package com.edgar.search.controller;

import com.edgar.search.model.SearchForm;
import com.edgar.search.model.SearchResponse;
import com.edgar.search.service.RagSearchService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class SearchController {

    private final RagSearchService ragSearchService;

    public SearchController(RagSearchService ragSearchService) {
        this.ragSearchService = ragSearchService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("searchForm", new SearchForm("", "", ""));
        return "index";
    }

    @PostMapping("/search")
    public String search(
            @Valid @ModelAttribute("searchForm") SearchForm searchForm,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "index";
        }

        SearchResponse response = ragSearchService.answer(searchForm);
        model.addAttribute("result", response);
        return "index";
    }
}
