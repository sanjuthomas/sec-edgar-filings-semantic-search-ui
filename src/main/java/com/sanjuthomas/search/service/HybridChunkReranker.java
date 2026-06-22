package com.sanjuthomas.search.service;

import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.RetrievedChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HybridChunkReranker {

    private static final int RRF_K = 60;

    private HybridChunkReranker() {
    }

    static List<ChunkMatch> rerank(
            List<RetrievedChunk> vectorChunks,
            List<RetrievedChunk> bm25Chunks,
            int topN
    ) {
        Map<String, RetrievedChunk> chunksByKey = new HashMap<>();
        Map<String, Double> fusedScores = new HashMap<>();

        accumulateRrfScores(vectorChunks, fusedScores, chunksByKey);
        accumulateRrfScores(bm25Chunks, fusedScores, chunksByKey);

        List<Map.Entry<String, Double>> ranked = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .toList();

        List<ChunkMatch> matches = new ArrayList<>(ranked.size());
        int citationNumber = 1;
        for (Map.Entry<String, Double> entry : ranked) {
            RetrievedChunk chunk = chunksByKey.get(entry.getKey());
            matches.add(toChunkMatch(citationNumber++, chunk, entry.getValue()));
        }
        return matches;
    }

    private static void accumulateRrfScores(
            List<RetrievedChunk> rankedChunks,
            Map<String, Double> fusedScores,
            Map<String, RetrievedChunk> chunksByKey
    ) {
        for (int rank = 0; rank < rankedChunks.size(); rank++) {
            RetrievedChunk chunk = rankedChunks.get(rank);
            chunksByKey.putIfAbsent(chunk.mergeKey(), chunk);
            fusedScores.merge(
                    chunk.mergeKey(),
                    1.0 / (RRF_K + rank + 1),
                    Double::sum
            );
        }
    }

    private static ChunkMatch toChunkMatch(int citationNumber, RetrievedChunk chunk, double fusedScore) {
        return new ChunkMatch(
                citationNumber,
                chunk.content(),
                fusedScore,
                chunk.accessionNumber(),
                chunk.chunkIndex(),
                chunk.ticker(),
                chunk.companyName(),
                chunk.form(),
                chunk.filingDate(),
                chunk.documentUrl(),
                chunk.section(),
                chunk.metadata()
        );
    }
}
