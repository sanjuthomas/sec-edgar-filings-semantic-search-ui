package com.sanjuthomas.search.controller;

import com.sanjuthomas.search.config.SearchProperties;
import com.sanjuthomas.search.config.VectorStoresProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.SearchForm;
import com.sanjuthomas.search.model.SearchResponse;
import com.sanjuthomas.search.model.VectorStoreType;
import com.sanjuthomas.search.service.OllamaModelService;
import com.sanjuthomas.search.service.RagSearchService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchControllerTest {

    private MockWebServer mockWebServer;
    private OllamaModelService ollamaModelService;
    private RecordingRagSearchService ragSearchService;
    private SearchController controller;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        ollamaModelService = new OllamaModelService(
                mockWebServer.url("/").toString().replaceAll("/$", ""),
                "qwen3:14b"
        );
        ragSearchService = new RecordingRagSearchService();
        controller = new SearchController(
                ragSearchService,
                ollamaModelService,
                new VectorStoresProperties("pgvector", new VectorStoresProperties.Qdrant("http://localhost:6333", "filing_chunks")),
                new SearchProperties(25, 1024, 50, 25)
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void indexPreparesSearchPage() {
        enqueueChatModels(1);

        Model model = new ConcurrentModel();
        String view = controller.index(model);

        assertThat(view).isEqualTo("index");
        assertThat(model.getAttribute("searchForm")).isInstanceOf(SearchForm.class);
        assertThat(model.getAttribute("vectorStores")).isEqualTo(List.of("pgvector", "qdrant"));
        assertThat(model.getAttribute("chunkCountChoices")).isEqualTo(List.of(10, 25, 50, 100));
        assertThat(((SearchForm) model.getAttribute("searchForm")).chatModel()).isEqualTo("qwen3:14b");
    }

    @Test
    void searchReturnsValidationErrorsForInvalidModel() {
        enqueueChatModels(2);

        SearchForm form = new SearchForm("Who are the directors?", "unknown-model", "pgvector", 25, "", "");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "searchForm");
        Model model = new ConcurrentModel();

        String view = controller.search(form, bindingResult, model);

        assertThat(view).isEqualTo("index");
        assertThat(bindingResult.getFieldError("chatModel")).isNotNull();
        assertThat(ragSearchService.calls).isZero();
        assertThat(model.containsAttribute("result")).isFalse();
    }

    @Test
    void searchReturnsValidationErrorsForInvalidVectorStore() {
        enqueueChatModels(2);

        SearchForm form = new SearchForm("Revenue growth?", "qwen3:14b", "invalid-store", 25, "", "");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "searchForm");
        Model model = new ConcurrentModel();

        String view = controller.search(form, bindingResult, model);

        assertThat(view).isEqualTo("index");
        assertThat(bindingResult.getFieldError("vectorStore")).isNotNull();
        assertThat(ragSearchService.calls).isZero();
    }

    @Test
    void searchInvokesRagServiceForValidForm() {
        enqueueChatModels(2);

        SearchForm form = new SearchForm("Revenue growth?", "qwen3:14b", "pgvector", 25, "GS", "10-K");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "searchForm");
        Model model = new ConcurrentModel();

        String view = controller.search(form, bindingResult, model);

        assertThat(view).isEqualTo("index");
        assertThat(bindingResult.hasErrors()).isFalse();
        assertThat(ragSearchService.calls).isEqualTo(1);
        assertThat(model.getAttribute("result")).isInstanceOf(SearchResponse.class);
        assertThat(((SearchResponse) model.getAttribute("result")).answer()).isEqualTo("stub answer");
    }

    private void enqueueChatModels(int count) {
        for (int i = 0; i < count; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"models\":[{\"name\":\"qwen3:14b\",\"capabilities\":[\"completion\"]}]}"));
        }
    }

    private static final class RecordingRagSearchService extends RagSearchService {
        private int calls;

        private RecordingRagSearchService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public SearchResponse answer(SearchForm form) {
            calls++;
            ChunkMatch source = new ChunkMatch(
                    1,
                    "excerpt",
                    0.1,
                    "0000000000-00-000001",
                    0,
                    "GS",
                    "Example Corp",
                    "10-K",
                    null,
                    null,
                    "Item 1",
                    Map.of()
            );
            return new SearchResponse(
                    form.question(),
                    "stub answer",
                    List.of(source),
                    form.chatModel(),
                    VectorStoreType.fromValue(form.vectorStore()).value(),
                    "GS",
                    false,
                    5L,
                    10L
            );
        }
    }
}
