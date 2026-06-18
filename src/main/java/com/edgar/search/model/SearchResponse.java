package com.edgar.search.model;

import java.util.List;

public record SearchResponse(
        String question,
        String answer,
        List<ChunkMatch> sources,
        String chatModel,
        String vectorStore,
        String appliedTicker,
        boolean tickerInferred,
        long retrievalMs,
        long generationMs
) {
}
