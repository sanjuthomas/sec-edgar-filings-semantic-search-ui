package com.sanjuthomas.search.service;

import com.sanjuthomas.search.config.PgSearchProperties;
import com.sanjuthomas.search.config.QdrantSearchProperties;
import com.sanjuthomas.search.config.SearchProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.SearchForm;
import com.sanjuthomas.search.model.SearchResponse;
import com.sanjuthomas.search.model.VectorStoreType;
import com.sanjuthomas.search.repository.ChunkSearchRepository;
import com.sanjuthomas.search.repository.QdrantHybridChunkLookup;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagSearchServiceTest {

    private static final SearchProperties SEARCH_PROPERTIES =
            new SearchProperties(25, 1024, 50, 25);

    @Test
    void usesPgHybridSearchWhenPgvectorSelectedAndHybridEnabled() {
        RagSearchService service = ragSearchService(
                pgHybridEnabled(true),
                qdrantHybridEnabled(false),
                chunkSearchRouterReturning(List.of())
        );

        SearchResponse response = service.answer(searchForm("pgvector", "Who are the directors?"));

        assertThat(response.answer()).contains("No matching filing excerpts were found in pgvector");
        assertThat(response.vectorStore()).isEqualTo("pgvector");
    }

    @Test
    void usesQdrantHybridSearchWhenQdrantSelectedAndHybridEnabled() {
        RagSearchService service = ragSearchService(
                pgHybridEnabled(false),
                qdrantHybridEnabled(true),
                chunkSearchRouterReturning(List.of())
        );

        SearchResponse response = service.answer(searchForm("qdrant", "revenue growth"));

        assertThat(response.answer()).contains("No matching filing excerpts were found in qdrant");
        assertThat(response.vectorStore()).isEqualTo("qdrant");
    }

    @Test
    void fallsBackToChunkSearchRouterWhenHybridDisabled() {
        RagSearchService service = ragSearchService(
                pgHybridEnabled(false),
                qdrantHybridEnabled(false),
                chunkSearchRouterReturning(List.of())
        );

        SearchResponse response = service.answer(searchForm("pgvector", "business overview"));

        assertThat(response.answer()).contains("No matching filing excerpts were found in pgvector");
    }

    @Test
    void generatesAnswerFromRetrievedSources() {
        ChunkMatch source = new ChunkMatch(
                1,
                "  Revenue increased 5% year over year.  ",
                0.2,
                "0000000000-00-000001",
                3,
                "GS",
                "Example Corp",
                "10-K",
                LocalDate.of(2024, 12, 31),
                "https://example.com/filing",
                "Item 7",
                Map.of("extra", "value")
        );
        ChatModel chatModel = prompt -> new ChatResponse(List.of(
                new Generation(new AssistantMessage("Revenue increased year over year [1]."))
        ));
        RagSearchService service = new RagSearchService(
                stubEmbeddingModel(),
                ChatClient.builder(chatModel).build(),
                chunkSearchRouterReturning(List.of(source)),
                pgHybridEnabled(false),
                qdrantHybridEnabled(false),
                TickerResolver.forTesting(List.of("GS"), List.of())
        );

        SearchResponse response = service.answer(searchForm("pgvector", "How did revenue change?"));

        assertThat(response.answer()).isEqualTo("Revenue increased year over year [1].");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().section()).isEqualTo("Item 7");
        assertThat(response.generationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(response.retrievalMs()).isGreaterThanOrEqualTo(0L);
    }

    private static RagSearchService ragSearchService(
            PgHybridSearchService pgHybridSearchService,
            QdrantHybridSearchService qdrantHybridSearchService,
            ChunkSearchRouter chunkSearchRouter
    ) {
        EmbeddingModel embeddingModel = stubEmbeddingModel();
        TickerResolver tickerResolver = TickerResolver.forTesting(List.of("GS"), List.of());
        return new RagSearchService(
                embeddingModel,
                null,
                chunkSearchRouter,
                pgHybridSearchService,
                qdrantHybridSearchService,
                tickerResolver
        );
    }

    private static PgHybridSearchService pgHybridEnabled(boolean enabled) {
        return new PgHybridSearchService(
                new PgSearchProperties(enabled, "", "", ""),
                SEARCH_PROPERTIES,
                (embedding, topK, ticker, form) -> List.of(),
                (query, topK, ticker, form) -> List.of()
        );
    }

    private static QdrantHybridSearchService qdrantHybridEnabled(boolean enabled) {
        return new QdrantHybridSearchService(
                new QdrantSearchProperties(enabled, "", "filing_chunks", "dense", "content-bm25", "Qdrant/bm25"),
                SEARCH_PROPERTIES,
                new QdrantHybridChunkLookup() {
                    @Override
                    public List<com.sanjuthomas.search.model.RetrievedChunk> findDenseChunks(
                            float[] queryEmbedding,
                            int topK,
                            String ticker,
                            String form
                    ) {
                        return List.of();
                    }

                    @Override
                    public List<com.sanjuthomas.search.model.RetrievedChunk> findBm25Chunks(
                            String query,
                            int topK,
                            String ticker,
                            String form
                    ) {
                        return List.of();
                    }
                }
        );
    }

    private static ChunkSearchRouter chunkSearchRouterReturning(List<ChunkMatch> matches) {
        return new ChunkSearchRouter(List.of(
                new ChunkSearchRepository() {
                    @Override
                    public VectorStoreType vectorStoreType() {
                        return VectorStoreType.PGVECTOR;
                    }

                    @Override
                    public List<ChunkMatch> findSimilarChunks(
                            float[] queryEmbedding,
                            int topK,
                            String ticker,
                            String form
                    ) {
                        return matches;
                    }
                },
                new ChunkSearchRepository() {
                    @Override
                    public VectorStoreType vectorStoreType() {
                        return VectorStoreType.QDRANT;
                    }

                    @Override
                    public List<ChunkMatch> findSimilarChunks(
                            float[] queryEmbedding,
                            int topK,
                            String ticker,
                            String form
                    ) {
                        return List.of();
                    }
                }
        ));
    }

    private static EmbeddingModel stubEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                return new float[] {0.5f};
            }

            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                return new float[] {0.5f};
            }

            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(
                    org.springframework.ai.embedding.EmbeddingRequest request
            ) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static SearchForm searchForm(String vectorStore, String question) {
        return new SearchForm(question, "qwen3:14b", vectorStore, 25, "", "");
    }
}
