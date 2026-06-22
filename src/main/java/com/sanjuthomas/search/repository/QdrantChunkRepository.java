package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.config.VectorStoresProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.VectorStoreType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class QdrantChunkRepository implements ChunkSearchRepository {

    private static final List<String> RESERVED_PAYLOAD_KEYS = List.of(
            "content",
            "accession_number",
            "chunk_index",
            "ticker",
            "company_name",
            "form",
            "filing_date",
            "document_url",
            "section"
    );

    private final RestClient restClient;
    private final VectorStoresProperties.Qdrant qdrant;

    public QdrantChunkRepository(VectorStoresProperties vectorStoresProperties) {
        this.qdrant = vectorStoresProperties.qdrant();
        this.restClient = RestClient.builder().baseUrl(qdrant.url()).build();
    }

    @Override
    public VectorStoreType vectorStoreType() {
        return VectorStoreType.QDRANT;
    }

    @Override
    public List<ChunkMatch> findSimilarChunks(
            float[] queryEmbedding,
            int topK,
            String ticker,
            String form
    ) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", toDoubleList(queryEmbedding));
        requestBody.put("using", "dense");
        requestBody.put("limit", topK);
        requestBody.put("with_payload", true);

        Map<String, Object> filter = buildFilter(ticker, form);
        if (filter != null) {
            requestBody.put("filter", filter);
        }

        QdrantQueryResponse response = restClient.post()
                .uri("/collections/{collection}/points/query", qdrant.collection())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(QdrantQueryResponse.class);

        if (response == null || response.result() == null || response.result().points() == null) {
            return List.of();
        }

        List<ChunkMatch> matches = new ArrayList<>();
        int index = 1;
        for (ScoredPoint point : response.result().points()) {
            Map<String, Object> payload = point.payload() != null ? point.payload() : Map.of();
            matches.add(toChunkMatch(index++, point.score(), payload));
        }
        return matches;
    }

    private Map<String, Object> buildFilter(String ticker, String form) {
        List<Map<String, Object>> must = new ArrayList<>();
        if (ticker != null) {
            must.add(keywordMatch("ticker", ticker));
        }
        if (form != null) {
            must.add(keywordMatch("form", form));
        }
        if (must.isEmpty()) {
            return null;
        }
        return Map.of("must", must);
    }

    private Map<String, Object> keywordMatch(String key, String value) {
        return Map.of(
                "key", key,
                "match", Map.of("value", value)
        );
    }

    private List<Double> toDoubleList(float[] embedding) {
        List<Double> values = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            values.add((double) value);
        }
        return values;
    }

    private ChunkMatch toChunkMatch(int citationNumber, Double score, Map<String, Object> payload) {
        double distance = score != null ? 1.0d - score : 1.0d;
        String section = stringValue(payload.get("section"));
        LocalDate filingDate = parseFilingDate(payload.get("filing_date"));

        return new ChunkMatch(
                citationNumber,
                stringValue(payload.get("content")),
                distance,
                stringValue(payload.get("accession_number")),
                intValue(payload.get("chunk_index")),
                stringValue(payload.get("ticker")),
                stringValue(payload.get("company_name")),
                stringValue(payload.get("form")),
                filingDate,
                stringValue(payload.get("document_url")),
                section,
                extractMetadata(payload)
        );
    }

    private Map<String, Object> extractMetadata(Map<String, Object> payload) {
        Map<String, Object> metadata = new HashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!RESERVED_PAYLOAD_KEYS.contains(entry.getKey())) {
                metadata.put(entry.getKey(), entry.getValue());
            }
        }
        return metadata;
    }

    private LocalDate parseFilingDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text.substring(0, Math.min(10, text.length())));
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QdrantQueryResponse(QdrantQueryResult result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QdrantQueryResult(List<ScoredPoint> points) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScoredPoint(Double score, Map<String, Object> payload) {
    }
}
