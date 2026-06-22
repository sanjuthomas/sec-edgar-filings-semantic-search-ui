package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.VectorStoreType;
import com.sanjuthomas.search.support.StubJdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorChunkRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void vectorStoreTypeIsPgvector() {
        PgVectorChunkRepository repository = new PgVectorChunkRepository(
                new StubJdbcTemplate(Map.of()),
                objectMapper
        );

        assertThat(repository.vectorStoreType()).isEqualTo(VectorStoreType.PGVECTOR);
    }

    @Test
    void findSimilarChunksMapsRowWithMetadataAndFilters() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(Map.of(
                "content", "pg excerpt",
                "distance", 0.12,
                "accession_number", "0000000000-00-000001",
                "chunk_index", 2,
                "metadata", "{\"section\":\"Risk Factors\",\"extra\":\"value\"}",
                "ticker", "GS",
                "company_name", "Example Corp",
                "form", "10-K",
                "filing_date", LocalDate.of(2024, 12, 31),
                "document_url", "https://example.com/doc"
        ));
        PgVectorChunkRepository repository = new PgVectorChunkRepository(jdbcTemplate, objectMapper);

        List<ChunkMatch> matches = repository.findSimilarChunks(
                new float[] {0.1f, 0.2f},
                10,
                "GS",
                "10-K"
        );

        assertThat(matches).hasSize(1);
        ChunkMatch match = matches.getFirst();
        assertThat(match.content()).isEqualTo("pg excerpt");
        assertThat(match.distance()).isEqualTo(0.12);
        assertThat(match.section()).isEqualTo("Risk Factors");
        assertThat(match.metadata()).containsEntry("extra", "value");
        assertThat(jdbcTemplate.lastSql())
                .contains("f.ticker = ?")
                .contains("f.form = ?")
                .contains("ORDER BY distance LIMIT ?");
        assertThat(jdbcTemplate.lastArgs()).hasSize(4);
        assertThat(jdbcTemplate.lastArgs()[0]).isInstanceOf(com.pgvector.PGvector.class);
        assertThat(jdbcTemplate.lastArgs()[1]).isEqualTo("GS");
        assertThat(jdbcTemplate.lastArgs()[2]).isEqualTo("10-K");
        assertThat(jdbcTemplate.lastArgs()[3]).isEqualTo(10);
    }

    @Test
    void findSimilarChunksHandlesBlankMetadata() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(chunkRow(
                "plain excerpt",
                0.5,
                "0000000000-00-000002",
                0,
                "not-json",
                "AAPL",
                "Apple Inc",
                "10-Q",
                null,
                ""
        ));
        PgVectorChunkRepository repository = new PgVectorChunkRepository(jdbcTemplate, objectMapper);

        List<ChunkMatch> matches = repository.findSimilarChunks(new float[] {0.3f}, 5, null, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().section()).isNull();
        assertThat(matches.getFirst().metadata()).isEmpty();
        assertThat(matches.getFirst().filingDate()).isNull();
        assertThat(jdbcTemplate.lastSql()).doesNotContain("f.ticker = ?");
    }

    private static Map<String, Object> chunkRow(
            String content,
            double distance,
            String accessionNumber,
            int chunkIndex,
            String metadata,
            String ticker,
            String companyName,
            String form,
            LocalDate filingDate,
            String documentUrl
    ) {
        Map<String, Object> row = new HashMap<>();
        row.put("content", content);
        row.put("distance", distance);
        row.put("accession_number", accessionNumber);
        row.put("chunk_index", chunkIndex);
        row.put("metadata", metadata);
        row.put("ticker", ticker);
        row.put("company_name", companyName);
        row.put("form", form);
        row.put("filing_date", filingDate);
        row.put("document_url", documentUrl);
        return row;
    }
}
