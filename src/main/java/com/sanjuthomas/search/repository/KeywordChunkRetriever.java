package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.model.RetrievedChunk;

import java.util.List;

public interface KeywordChunkRetriever {

    List<RetrievedChunk> findKeywordChunks(
            String query,
            int topK,
            String ticker,
            String form
    );
}
