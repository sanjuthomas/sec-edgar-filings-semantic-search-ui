package com.sanjuthomas.search.service;

import com.sanjuthomas.search.config.QdrantSearchProperties;
import com.sanjuthomas.search.config.SearchProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.RetrievedChunk;
import com.sanjuthomas.search.repository.QdrantHybridChunkLookup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantHybridSearchServiceTest {

    private static final SearchProperties SEARCH_PROPERTIES =
            new SearchProperties(25, 1024, 50, 25);

    private static final QdrantSearchProperties QDRANT_SEARCH_PROPERTIES =
            new QdrantSearchProperties(
                    true,
                    "http://localhost:6333",
                    "filing_chunks",
                    "dense",
                    "content-bm25",
                    "Qdrant/bm25"
            );

    @Test
    void isEnabledWhenQdrantSearchPropertyIsTrue() {
        QdrantHybridSearchService service = new QdrantHybridSearchService(
                QDRANT_SEARCH_PROPERTIES,
                SEARCH_PROPERTIES,
                stubLookup(List.of(), List.of())
        );

        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void isDisabledWhenQdrantSearchPropertyIsFalse() {
        QdrantHybridSearchService service = new QdrantHybridSearchService(
                new QdrantSearchProperties(false, "", "filing_chunks", "dense", "content-bm25", "Qdrant/bm25"),
                SEARCH_PROPERTIES,
                stubLookup(List.of(), List.of())
        );

        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void searchQueriesDenseAndBm25RepositoriesThenReranks() {
        RetrievedChunk shared = chunk("point-shared", "shared excerpt");
        RetrievedChunk denseOnly = chunk("point-dense", "dense excerpt");
        RetrievedChunk bm25Only = chunk("point-bm25", "bm25 excerpt");
        float[] embedding = {0.3f, 0.4f};

        RecordingQdrantLookup lookup = new RecordingQdrantLookup(
                List.of(shared, denseOnly),
                List.of(shared, bm25Only)
        );

        QdrantHybridSearchService service = new QdrantHybridSearchService(
                QDRANT_SEARCH_PROPERTIES,
                SEARCH_PROPERTIES,
                lookup
        );

        List<ChunkMatch> results = service.search("directors", embedding, 2, null, null);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().content()).isEqualTo("shared excerpt");
        assertThat(lookup.denseTopK).isEqualTo(50);
        assertThat(lookup.bm25Query).isEqualTo("directors");
        assertThat(lookup.bm25TopK).isEqualTo(50);
    }

    private static QdrantHybridChunkLookup stubLookup(
            List<RetrievedChunk> denseChunks,
            List<RetrievedChunk> bm25Chunks
    ) {
        return new QdrantHybridChunkLookup() {
            @Override
            public List<RetrievedChunk> findDenseChunks(
                    float[] queryEmbedding,
                    int topK,
                    String ticker,
                    String form
            ) {
                return denseChunks;
            }

            @Override
            public List<RetrievedChunk> findBm25Chunks(
                    String query,
                    int topK,
                    String ticker,
                    String form
            ) {
                return bm25Chunks;
            }
        };
    }

    private static RetrievedChunk chunk(String mergeKey, String content) {
        return new RetrievedChunk(
                mergeKey,
                content,
                "0000000000-00-000001",
                1,
                "GS",
                "Example Corp",
                "10-K",
                null,
                null,
                null,
                Map.of()
        );
    }

    private static final class RecordingQdrantLookup implements QdrantHybridChunkLookup {
        private final List<RetrievedChunk> denseChunks;
        private final List<RetrievedChunk> bm25Chunks;
        private int denseTopK;
        private String bm25Query;
        private int bm25TopK;

        private RecordingQdrantLookup(List<RetrievedChunk> denseChunks, List<RetrievedChunk> bm25Chunks) {
            this.denseChunks = denseChunks;
            this.bm25Chunks = bm25Chunks;
        }

        @Override
        public List<RetrievedChunk> findDenseChunks(
                float[] queryEmbedding,
                int topK,
                String ticker,
                String form
        ) {
            denseTopK = topK;
            return denseChunks;
        }

        @Override
        public List<RetrievedChunk> findBm25Chunks(
                String query,
                int topK,
                String ticker,
                String form
        ) {
            bm25Query = query;
            bm25TopK = topK;
            return bm25Chunks;
        }
    }
}
