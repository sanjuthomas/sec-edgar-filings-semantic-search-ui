package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.model.RetrievedChunk;
import com.sanjuthomas.search.support.StubJdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PgBm25ChunkRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void findKeywordChunksMapsRowAndAppliesFilters() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(Map.of(
                "id", 99L,
                "content", "bm25 keyword excerpt",
                "accession_number", "0000000000-00-000005",
                "chunk_index", 4,
                "metadata", "{\"section\":\"Directors\"}",
                "ticker", "GS",
                "company_name", "Example Corp",
                "form", "10-K",
                "filing_date", LocalDate.of(2025, 3, 15),
                "document_url", "https://example.com/gs"
        ));
        PgBm25ChunkRepository repository = new PgBm25ChunkRepository(jdbcTemplate, objectMapper);

        List<RetrievedChunk> chunks = repository.findKeywordChunks("board of directors", 15, "GS", "10-K");

        assertThat(chunks).hasSize(1);
        RetrievedChunk chunk = chunks.getFirst();
        assertThat(chunk.mergeKey()).isEqualTo("99");
        assertThat(chunk.content()).isEqualTo("bm25 keyword excerpt");
        assertThat(chunk.section()).isEqualTo("Directors");
        assertThat(jdbcTemplate.lastSql())
                .contains("c.content ||| ?")
                .contains("ORDER BY rank DESC LIMIT ?");
        assertThat(jdbcTemplate.lastArgs()[0]).isEqualTo("board of directors");
        assertThat(jdbcTemplate.lastArgs()[1]).isEqualTo("GS");
        assertThat(jdbcTemplate.lastArgs()[2]).isEqualTo("10-K");
        assertThat(jdbcTemplate.lastArgs()[3]).isEqualTo(15);
    }

    @Test
    void findKeywordChunksHandlesInvalidMetadata() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(retrievedChunkRow(
                1L,
                "plain",
                "0000000000-00-000006",
                0,
                "{bad",
                "AAPL",
                "Apple Inc",
                "10-Q",
                null,
                ""
        ));
        PgBm25ChunkRepository repository = new PgBm25ChunkRepository(jdbcTemplate, objectMapper);

        List<RetrievedChunk> chunks = repository.findKeywordChunks("revenue", 5, null, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().metadata()).isEmpty();
        assertThat(jdbcTemplate.lastSql()).doesNotContain("f.ticker = ?");
    }

    private static Map<String, Object> retrievedChunkRow(
            long id,
            String content,
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
        row.put("id", id);
        row.put("content", content);
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
