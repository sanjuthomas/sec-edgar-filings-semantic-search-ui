package com.edgar.search.repository;

import com.edgar.search.model.ChunkMatch;
import com.edgar.search.model.VectorStoreType;

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
