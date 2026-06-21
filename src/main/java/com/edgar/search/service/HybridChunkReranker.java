package com.edgar.search.service;

import com.edgar.search.model.ChunkMatch;
import com.edgar.search.model.RetrievedChunk;

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
        Map<Long, RetrievedChunk> chunksById = new HashMap<>();
        Map<Long, Double> fusedScores = new HashMap<>();

        accumulateRrfScores(vectorChunks, fusedScores, chunksById);
        accumulateRrfScores(bm25Chunks, fusedScores, chunksById);

        List<Map.Entry<Long, Double>> ranked = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .toList();

        List<ChunkMatch> matches = new ArrayList<>(ranked.size());
        int citationNumber = 1;
        for (Map.Entry<Long, Double> entry : ranked) {
            RetrievedChunk chunk = chunksById.get(entry.getKey());
            matches.add(toChunkMatch(citationNumber++, chunk, entry.getValue()));
        }
        return matches;
    }

    private static void accumulateRrfScores(
            List<RetrievedChunk> rankedChunks,
            Map<Long, Double> fusedScores,
            Map<Long, RetrievedChunk> chunksById
    ) {
        for (int rank = 0; rank < rankedChunks.size(); rank++) {
            RetrievedChunk chunk = rankedChunks.get(rank);
            chunksById.putIfAbsent(chunk.chunkId(), chunk);
            fusedScores.merge(
                    chunk.chunkId(),
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
