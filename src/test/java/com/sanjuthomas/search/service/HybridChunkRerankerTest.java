package com.sanjuthomas.search.service;

import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridChunkRerankerTest {

    @Test
    void mergesResultsByMergeKeyAndReranksWithReciprocalRankFusion() {
        RetrievedChunk shared = chunk("shared-key", "shared chunk");
        RetrievedChunk vectorOnly = chunk("vector-key", "vector only");
        RetrievedChunk bm25Only = chunk("bm25-key", "bm25 only");

        List<ChunkMatch> reranked = HybridChunkReranker.rerank(
                List.of(shared, vectorOnly),
                List.of(shared, bm25Only),
                3
        );

        assertThat(reranked).hasSize(3);
        assertThat(reranked.getFirst().content()).isEqualTo("shared chunk");
        assertThat(reranked.getFirst().citationNumber()).isEqualTo(1);
        assertThat(reranked.getFirst().distance()).isGreaterThan(0.0);
    }

    @Test
    void limitsResultsToRequestedTopN() {
        List<RetrievedChunk> vectorChunks = List.of(
                chunk("one", "one"),
                chunk("two", "two"),
                chunk("three", "three")
        );

        List<ChunkMatch> reranked = HybridChunkReranker.rerank(vectorChunks, List.of(), 2);

        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).citationNumber()).isEqualTo(1);
        assertThat(reranked.get(1).citationNumber()).isEqualTo(2);
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
}
