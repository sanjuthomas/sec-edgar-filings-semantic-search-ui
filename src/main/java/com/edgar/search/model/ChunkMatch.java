package com.edgar.search.model;

import java.time.LocalDate;
import java.util.Map;

public record ChunkMatch(
        int citationNumber,
        String content,
        double distance,
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
