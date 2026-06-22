package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.model.RetrievedChunk;

import java.util.List;

public interface VectorChunkRetriever {

    List<RetrievedChunk> findSimilarChunks(
            float[] queryEmbedding,
            int topK,
            String ticker,
            String form
    );
}
