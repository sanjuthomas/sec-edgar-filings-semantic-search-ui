package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.model.RetrievedChunk;

import java.util.List;

public interface QdrantHybridChunkLookup {

    List<RetrievedChunk> findDenseChunks(
            float[] queryEmbedding,
            int topK,
            String ticker,
            String form
    );

    List<RetrievedChunk> findBm25Chunks(
            String query,
            int topK,
            String ticker,
            String form
    );
}
