package com.sanjuthomas.search.service;

import com.sanjuthomas.search.config.PgSearchProperties;
import com.sanjuthomas.search.config.SearchProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.repository.KeywordChunkRetriever;
import com.sanjuthomas.search.repository.PgBm25ChunkRepository;
import com.sanjuthomas.search.repository.PgVectorHybridChunkRepository;
import com.sanjuthomas.search.repository.VectorChunkRetriever;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PgHybridSearchService {

    private final PgSearchProperties pgSearchProperties;
    private final SearchProperties searchProperties;
    private final VectorChunkRetriever vectorRepository;
    private final KeywordChunkRetriever bm25Repository;

    public PgHybridSearchService(
            PgSearchProperties pgSearchProperties,
            SearchProperties searchProperties,
            VectorChunkRetriever vectorRepository,
            KeywordChunkRetriever bm25Repository
    ) {
        this.pgSearchProperties = pgSearchProperties;
        this.searchProperties = searchProperties;
        this.vectorRepository = vectorRepository;
        this.bm25Repository = bm25Repository;
    }

    public boolean isEnabled() {
        return pgSearchProperties.enabled();
    }

    public List<ChunkMatch> search(
            String question,
            float[] queryEmbedding,
            int topN,
            String ticker,
            String form
    ) {
        int retrievalTopK = searchProperties.hybridRetrievalTopK();

        List<com.sanjuthomas.search.model.RetrievedChunk> vectorChunks = vectorRepository.findSimilarChunks(
                queryEmbedding,
                retrievalTopK,
                ticker,
                form
        );
        List<com.sanjuthomas.search.model.RetrievedChunk> bm25Chunks = bm25Repository.findKeywordChunks(
                question,
                retrievalTopK,
                ticker,
                form
        );

        return HybridChunkReranker.rerank(vectorChunks, bm25Chunks, topN);
    }
}
