package com.edgar.search.model;

import java.time.LocalDate;
import java.util.Map;

public record RetrievedChunk(
        long chunkId,
        String content,
        String accessionNumber,
        int chunkIndex,
        String ticker,
        String companyName,
        String form,
        LocalDate filingDate,
        String documentUrl,
        String section,
        Map<String, Object> metadata
) {
}
