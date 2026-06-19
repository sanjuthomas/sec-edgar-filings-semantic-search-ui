package com.edgar.search.service;

import com.edgar.search.model.ChunkMatch;
import com.edgar.search.model.VectorStoreType;
import com.edgar.search.repository.ChunkSearchRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkSearchRouterTest {

    @Test
    void routesToSelectedVectorStore() {
        ChunkMatch pgMatch = chunkMatch("PG");
        ChunkMatch qdrantMatch = chunkMatch("QD");

        ChunkSearchRouter router = new ChunkSearchRouter(List.of(
                stubRepository(VectorStoreType.PGVECTOR, pgMatch),
                stubRepository(VectorStoreType.QDRANT, qdrantMatch)
        ));

        List<ChunkMatch> fromPg = router.findSimilarChunks(
                VectorStoreType.PGVECTOR,
                new float[] {0.1f},
                10,
                "GS",
                "10-K"
        );
        List<ChunkMatch> fromQdrant = router.findSimilarChunks(
                VectorStoreType.QDRANT,
                new float[] {0.2f},
                25,
                null,
                null
        );

        assertThat(fromPg).containsExactly(pgMatch);
        assertThat(fromQdrant).containsExactly(qdrantMatch);
    }

    @Test
    void rejectsUnsupportedVectorStore() {
        ChunkSearchRouter router = new ChunkSearchRouter(List.of(
                stubRepository(VectorStoreType.PGVECTOR, chunkMatch("PG"))
        ));

        assertThatThrownBy(() -> router.findSimilarChunks(
                VectorStoreType.QDRANT,
                new float[] {0.1f},
                10,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported vector store");
    }

    private static ChunkSearchRepository stubRepository(VectorStoreType type, ChunkMatch match) {
        return new ChunkSearchRepository() {
            @Override
            public VectorStoreType vectorStoreType() {
                return type;
            }

            @Override
            public List<ChunkMatch> findSimilarChunks(
                    float[] queryEmbedding,
                    int topK,
                    String ticker,
                    String form
            ) {
                return List.of(match);
            }
        };
    }

    private static ChunkMatch chunkMatch(String ticker) {
        return new ChunkMatch(
                1,
                "excerpt",
                0.12,
                "0000000000-00-000000",
                0,
                ticker,
                "Example Corp",
                "10-K",
                null,
                null,
                null,
                null
        );
    }
}
