package com.edgar.search.service;

import com.edgar.search.config.PgSearchProperties;
import com.edgar.search.config.SearchProperties;
import com.edgar.search.model.ChunkMatch;
import com.edgar.search.repository.PgBm25ChunkRepository;
import com.edgar.search.repository.PgVectorHybridChunkRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PgHybridSearchService {

    private final PgSearchProperties pgSearchProperties;
    private final SearchProperties searchProperties;
    private final PgVectorHybridChunkRepository vectorRepository;
    private final PgBm25ChunkRepository bm25Repository;

    public PgHybridSearchService(
            PgSearchProperties pgSearchProperties,
            SearchProperties searchProperties,
            PgVectorHybridChunkRepository vectorRepository,
            PgBm25ChunkRepository bm25Repository
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

        List<com.edgar.search.model.RetrievedChunk> vectorChunks = vectorRepository.findSimilarChunks(
                queryEmbedding,
                retrievalTopK,
                ticker,
                form
        );
        List<com.edgar.search.model.RetrievedChunk> bm25Chunks = bm25Repository.findKeywordChunks(
                question,
                retrievalTopK,
                ticker,
                form
        );

        return HybridChunkReranker.rerank(vectorChunks, bm25Chunks, topN);
    }
}
