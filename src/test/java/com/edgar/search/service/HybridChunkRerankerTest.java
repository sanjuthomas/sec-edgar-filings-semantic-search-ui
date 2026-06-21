package com.edgar.search.service;

import com.edgar.search.model.ChunkMatch;
import com.edgar.search.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridChunkRerankerTest {

    @Test
    void mergesResultsByChunkIdAndReranksWithReciprocalRankFusion() {
        RetrievedChunk shared = chunk(1L, "shared chunk");
        RetrievedChunk vectorOnly = chunk(2L, "vector only");
        RetrievedChunk bm25Only = chunk(3L, "bm25 only");

        List<ChunkMatch> reranked = HybridChunkReranker.rerank(
                List.of(shared, vectorOnly),
                List.of(shared, bm25Only),
                3
        );

        assertThat(reranked).hasSize(3);
        assertThat(reranked.getFirst().accessionNumber()).isEqualTo("0000000000-00-000001");
        assertThat(reranked.getFirst().chunkIndex()).isZero();
        assertThat(reranked.getFirst().citationNumber()).isEqualTo(1);
        assertThat(reranked.getFirst().distance()).isGreaterThan(0.0);
    }

    @Test
    void limitsResultsToRequestedTopN() {
        List<RetrievedChunk> vectorChunks = List.of(
                chunk(1L, "one"),
                chunk(2L, "two"),
                chunk(3L, "three")
        );

        List<ChunkMatch> reranked = HybridChunkReranker.rerank(vectorChunks, List.of(), 2);

        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).citationNumber()).isEqualTo(1);
        assertThat(reranked.get(1).citationNumber()).isEqualTo(2);
    }

    private static RetrievedChunk chunk(long chunkId, String content) {
        return new RetrievedChunk(
                chunkId,
                content,
                "0000000000-00-" + String.format("%06d", chunkId),
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
}
