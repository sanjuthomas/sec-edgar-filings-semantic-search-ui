package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.model.RetrievedChunk;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class PgVectorHybridChunkRepository implements VectorChunkRetriever {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PgVectorHybridChunkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RetrievedChunk> findSimilarChunks(
            float[] queryEmbedding,
            int topK,
            String ticker,
            String form
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    c.id,
                    c.content,
                    c.accession_number,
                    c.chunk_index,
                    c.metadata,
                    f.ticker,
                    f.company_name,
                    f.form,
                    f.filing_date,
                    f.document_url
                FROM filing_chunks c
                JOIN filings f ON f.accession_number = c.accession_number
                WHERE TRUE
                """);

        List<Object> params = new ArrayList<>();

        if (ticker != null) {
            sql.append(" AND f.ticker = ?");
            params.add(ticker);
        }
        if (form != null) {
            sql.append(" AND f.form = ?");
            params.add(form);
        }

        sql.append(" ORDER BY c.embedding <=> ?::vector LIMIT ?");
        params.add(new PGvector(queryEmbedding));
        params.add(topK);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> mapRow(rs),
                params.toArray()
        );
    }

    private RetrievedChunk mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
        String section = metadata.get("section") instanceof String s ? s : null;
        Date filingDate = rs.getDate("filing_date");
        LocalDate localFilingDate = filingDate != null ? filingDate.toLocalDate() : null;

        return new RetrievedChunk(
                String.valueOf(rs.getLong("id")),
                rs.getString("content"),
                rs.getString("accession_number"),
                rs.getInt("chunk_index"),
                rs.getString("ticker"),
                rs.getString("company_name"),
                rs.getString("form"),
                localFilingDate,
                rs.getString("document_url"),
                section,
                metadata
        );
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
