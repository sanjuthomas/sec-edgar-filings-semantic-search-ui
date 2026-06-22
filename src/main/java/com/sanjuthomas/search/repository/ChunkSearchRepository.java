package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.VectorStoreType;

import java.util.List;

public interface ChunkSearchRepository {

    VectorStoreType vectorStoreType();

    List<ChunkMatch> findSimilarChunks(
            float[] queryEmbedding,
            int topK,
            String ticker,
            String form
    );
}
