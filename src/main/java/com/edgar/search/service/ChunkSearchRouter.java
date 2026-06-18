package com.edgar.search.service;

import com.edgar.search.model.ChunkMatch;
import com.edgar.search.model.VectorStoreType;
import com.edgar.search.repository.ChunkSearchRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ChunkSearchRouter {

    private final Map<VectorStoreType, ChunkSearchRepository> repositories;

    public ChunkSearchRouter(List<ChunkSearchRepository> repositories) {
        Map<VectorStoreType, ChunkSearchRepository> map = new EnumMap<>(VectorStoreType.class);
        for (ChunkSearchRepository repository : repositories) {
            map.put(repository.vectorStoreType(), repository);
        }
        this.repositories = Map.copyOf(map);
    }

    public List<ChunkMatch> findSimilarChunks(
            VectorStoreType vectorStore,
            float[] queryEmbedding,
            int topK,
            String ticker,
            String form
    ) {
        ChunkSearchRepository repository = repositories.get(vectorStore);
        if (repository == null) {
            throw new IllegalArgumentException("Unsupported vector store: " + vectorStore);
        }
        return repository.findSimilarChunks(queryEmbedding, topK, ticker, form);
    }
}
