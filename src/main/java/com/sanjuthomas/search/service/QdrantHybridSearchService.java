package com.sanjuthomas.search.service;

import com.sanjuthomas.search.config.QdrantSearchProperties;
import com.sanjuthomas.search.config.SearchProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.repository.QdrantHybridChunkLookup;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QdrantHybridSearchService {

    private final QdrantSearchProperties qdrantSearchProperties;
    private final SearchProperties searchProperties;
    private final QdrantHybridChunkLookup qdrantHybridChunkRepository;

    public QdrantHybridSearchService(
            QdrantSearchProperties qdrantSearchProperties,
            SearchProperties searchProperties,
            QdrantHybridChunkLookup qdrantHybridChunkRepository
    ) {
        this.qdrantSearchProperties = qdrantSearchProperties;
        this.searchProperties = searchProperties;
        this.qdrantHybridChunkRepository = qdrantHybridChunkRepository;
    }

    public boolean isEnabled() {
        return qdrantSearchProperties.enabled();
    }

    public List<ChunkMatch> search(
            String question,
            float[] queryEmbedding,
            int topN,
            String ticker,
            String form
    ) {
        int retrievalTopK = searchProperties.hybridRetrievalTopK();

        List<com.sanjuthomas.search.model.RetrievedChunk> vectorChunks = qdrantHybridChunkRepository.findDenseChunks(
                queryEmbedding,
                retrievalTopK,
                ticker,
                form
        );
        List<com.sanjuthomas.search.model.RetrievedChunk> bm25Chunks = qdrantHybridChunkRepository.findBm25Chunks(
                question,
                retrievalTopK,
                ticker,
                form
        );

        return HybridChunkReranker.rerank(vectorChunks, bm25Chunks, topN);
    }
}
