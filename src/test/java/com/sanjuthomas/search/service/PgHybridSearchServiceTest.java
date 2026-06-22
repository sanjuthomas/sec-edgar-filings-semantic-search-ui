package com.sanjuthomas.search.service;

import com.sanjuthomas.search.config.PgSearchProperties;
import com.sanjuthomas.search.config.SearchProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.RetrievedChunk;
import com.sanjuthomas.search.repository.KeywordChunkRetriever;
import com.sanjuthomas.search.repository.VectorChunkRetriever;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PgHybridSearchServiceTest {

    private static final SearchProperties SEARCH_PROPERTIES =
            new SearchProperties(25, 1024, 50, 25);

    @Test
    void isEnabledWhenPgSearchPropertyIsTrue() {
        PgHybridSearchService enabled = new PgHybridSearchService(
                new PgSearchProperties(true, "jdbc:postgresql://localhost:5433/edgar", "postgres", "postgres"),
                SEARCH_PROPERTIES,
                stubVectorRetriever(List.of()),
                stubKeywordRetriever(List.of())
        );

        assertThat(enabled.isEnabled()).isTrue();
    }

    @Test
    void isDisabledWhenPgSearchPropertyIsFalse() {
        PgHybridSearchService disabled = new PgHybridSearchService(
                new PgSearchProperties(false, "", "", ""),
                SEARCH_PROPERTIES,
                stubVectorRetriever(List.of()),
                stubKeywordRetriever(List.of())
        );

        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void searchQueriesVectorAndBm25RepositoriesThenReranks() {
        RetrievedChunk shared = chunk("42", "shared excerpt");
        RetrievedChunk vectorOnly = chunk("7", "vector excerpt");
        RetrievedChunk bm25Only = chunk("9", "bm25 excerpt");
        float[] embedding = {0.1f, 0.2f};

        RecordingVectorRetriever vectorRetriever = new RecordingVectorRetriever(
                List.of(shared, vectorOnly)
        );
        RecordingKeywordRetriever keywordRetriever = new RecordingKeywordRetriever(
                List.of(shared, bm25Only)
        );

        PgHybridSearchService service = new PgHybridSearchService(
                new PgSearchProperties(true, "", "", ""),
                SEARCH_PROPERTIES,
                vectorRetriever,
                keywordRetriever
        );

        List<ChunkMatch> results = service.search("revenue growth", embedding, 2, "GS", "10-K");

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().content()).isEqualTo("shared excerpt");
        assertThat(vectorRetriever.lastTopK).isEqualTo(50);
        assertThat(vectorRetriever.lastTicker).isEqualTo("GS");
        assertThat(keywordRetriever.lastQuery).isEqualTo("revenue growth");
        assertThat(keywordRetriever.lastTopK).isEqualTo(50);
    }

    private static VectorChunkRetriever stubVectorRetriever(List<RetrievedChunk> chunks) {
        return (embedding, topK, ticker, form) -> chunks;
    }

    private static KeywordChunkRetriever stubKeywordRetriever(List<RetrievedChunk> chunks) {
        return (query, topK, ticker, form) -> chunks;
    }

    private static RetrievedChunk chunk(String mergeKey, String content) {
        return new RetrievedChunk(
                mergeKey,
                content,
                "0000000000-00-000000",
                0,
                "GS",
                "Example Corp",
                "10-K",
                null,
                null,
                null,
                Map.of()
        );
    }

    private static final class RecordingVectorRetriever implements VectorChunkRetriever {
        private final List<RetrievedChunk> chunks;
        private int lastTopK;
        private String lastTicker;

        private RecordingVectorRetriever(List<RetrievedChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> findSimilarChunks(
                float[] queryEmbedding,
                int topK,
                String ticker,
                String form
        ) {
            lastTopK = topK;
            lastTicker = ticker;
            return chunks;
        }
    }

    private static final class RecordingKeywordRetriever implements KeywordChunkRetriever {
        private final List<RetrievedChunk> chunks;
        private String lastQuery;
        private int lastTopK;

        private RecordingKeywordRetriever(List<RetrievedChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> findKeywordChunks(
                String query,
                int topK,
                String ticker,
                String form
        ) {
            lastQuery = query;
            lastTopK = topK;
            return chunks;
        }
    }
}
