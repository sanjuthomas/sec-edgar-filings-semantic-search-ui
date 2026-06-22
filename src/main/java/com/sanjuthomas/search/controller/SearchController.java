package com.sanjuthomas.search.controller;

import com.sanjuthomas.search.config.SearchProperties;
import com.sanjuthomas.search.config.VectorStoresProperties;
import com.sanjuthomas.search.model.SearchForm;
import com.sanjuthomas.search.model.SearchResponse;
import com.sanjuthomas.search.model.VectorStoreType;
import com.sanjuthomas.search.service.OllamaModelService;
import com.sanjuthomas.search.service.RagSearchService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;
import java.util.List;

@Controller
public class SearchController {

    private static final List<Integer> CHUNK_COUNT_CHOICES = List.of(10, 25, 50, 100);

    private final RagSearchService ragSearchService;
    private final OllamaModelService ollamaModelService;
    private final VectorStoresProperties vectorStoresProperties;
    private final SearchProperties searchProperties;

    public SearchController(
            RagSearchService ragSearchService,
            OllamaModelService ollamaModelService,
            VectorStoresProperties vectorStoresProperties,
            SearchProperties searchProperties
    ) {
        this.ragSearchService = ragSearchService;
        this.ollamaModelService = ollamaModelService;
        this.vectorStoresProperties = vectorStoresProperties;
        this.searchProperties = searchProperties;
    }

    @GetMapping("/")
    public String index(Model model) {
        prepareSearchPage(model, newSearchForm());
        return "index";
    }

    @PostMapping("/search")
    public String search(
            @Valid @ModelAttribute("searchForm") SearchForm searchForm,
            BindingResult bindingResult,
            Model model
    ) {
        if (!bindingResult.hasErrors() && !ollamaModelService.isKnownChatModel(searchForm.chatModel())) {
            bindingResult.rejectValue("chatModel", "invalid", "Select a valid Ollama model.");
        }

        if (!bindingResult.hasErrors()) {
            try {
                VectorStoreType.fromValue(searchForm.vectorStore());
            } catch (IllegalArgumentException ex) {
                bindingResult.rejectValue("vectorStore", "invalid", "Select a valid vector store.");
            }
        }

        if (bindingResult.hasErrors()) {
            prepareSearchPage(model, searchForm);
            return "index";
        }

        SearchResponse response = ragSearchService.answer(searchForm);
        prepareSearchPage(model, searchForm);
        model.addAttribute("result", response);
        return "index";
    }

    private SearchForm newSearchForm() {
        return new SearchForm(
                "",
                ollamaModelService.defaultChatModel(),
                vectorStoresProperties.defaultVectorStore(),
                searchProperties.topK(),
                "",
                ""
        );
    }

    private void prepareSearchPage(Model model, SearchForm searchForm) {
        model.addAttribute("chatModels", ollamaModelService.listChatModels());
        model.addAttribute(
                "vectorStores",
                Arrays.stream(VectorStoreType.values()).map(VectorStoreType::value).toList()
        );
        model.addAttribute("chunkCountChoices", CHUNK_COUNT_CHOICES);
        model.addAttribute("searchForm", searchForm);
    }
}
