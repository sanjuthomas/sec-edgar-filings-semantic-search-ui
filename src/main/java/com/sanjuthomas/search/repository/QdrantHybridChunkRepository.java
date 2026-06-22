package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.config.QdrantSearchProperties;
import com.sanjuthomas.search.model.RetrievedChunk;
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
public class QdrantHybridChunkRepository implements QdrantHybridChunkLookup {

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
    private final QdrantSearchProperties qdrantSearchProperties;

    public QdrantHybridChunkRepository(QdrantSearchProperties qdrantSearchProperties) {
        this.qdrantSearchProperties = qdrantSearchProperties;
        this.restClient = RestClient.builder().baseUrl(qdrantSearchProperties.url()).build();
    }

    public List<RetrievedChunk> findDenseChunks(
            float[] queryEmbedding,
            int topK,
            String ticker,
            String form
    ) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", toDoubleList(queryEmbedding));
        requestBody.put("using", qdrantSearchProperties.denseVector());
        requestBody.put("limit", topK);
        requestBody.put("with_payload", true);
        applyFilter(requestBody, ticker, form);

        return queryPoints(requestBody);
    }

    public List<RetrievedChunk> findBm25Chunks(
            String query,
            int topK,
            String ticker,
            String form
    ) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(
                "query",
                Map.of(
                        "text", query,
                        "model", qdrantSearchProperties.bm25Model()
                )
        );
        requestBody.put("using", qdrantSearchProperties.bm25Vector());
        requestBody.put("limit", topK);
        requestBody.put("with_payload", true);
        applyFilter(requestBody, ticker, form);

        return queryPoints(requestBody);
    }

    private List<RetrievedChunk> queryPoints(Map<String, Object> requestBody) {
        QdrantQueryResponse response = restClient.post()
                .uri("/collections/{collection}/points/query", qdrantSearchProperties.collection())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(QdrantQueryResponse.class);

        if (response == null || response.result() == null || response.result().points() == null) {
            return List.of();
        }

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (ScoredPoint point : response.result().points()) {
            chunks.add(toRetrievedChunk(point));
        }
        return chunks;
    }

    private void applyFilter(Map<String, Object> requestBody, String ticker, String form) {
        Map<String, Object> filter = buildFilter(ticker, form);
        if (filter != null) {
            requestBody.put("filter", filter);
        }
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

    private RetrievedChunk toRetrievedChunk(ScoredPoint point) {
        Map<String, Object> payload = point.payload() != null ? point.payload() : Map.of();
        String section = stringValue(payload.get("section"));
        if (section.isBlank()) {
            section = null;
        }

        return new RetrievedChunk(
                stringValue(point.id()),
                stringValue(payload.get("content")),
                stringValue(payload.get("accession_number")),
                intValue(payload.get("chunk_index")),
                stringValue(payload.get("ticker")),
                stringValue(payload.get("company_name")),
                stringValue(payload.get("form")),
                parseFilingDate(payload.get("filing_date")),
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
    private record ScoredPoint(Object id, Double score, Map<String, Object> payload) {
    }
}
