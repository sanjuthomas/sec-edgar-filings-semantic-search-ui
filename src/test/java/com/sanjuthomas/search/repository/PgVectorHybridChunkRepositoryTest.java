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

class PgVectorHybridChunkRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void findSimilarChunksMapsRowAndAppliesFilters() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(Map.of(
                "id", 42L,
                "content", "hybrid vector excerpt",
                "accession_number", "0000000000-00-000003",
                "chunk_index", 1,
                "metadata", "{\"section\":\"MD&A\"}",
                "ticker", "MSFT",
                "company_name", "Microsoft Corp",
                "form", "10-K",
                "filing_date", LocalDate.of(2025, 2, 1),
                "document_url", "https://example.com/msft"
        ));
        PgVectorHybridChunkRepository repository = new PgVectorHybridChunkRepository(jdbcTemplate, objectMapper);

        List<RetrievedChunk> chunks = repository.findSimilarChunks(
                new float[] {0.2f},
                25,
                "MSFT",
                "10-K"
        );

        assertThat(chunks).hasSize(1);
        RetrievedChunk chunk = chunks.getFirst();
        assertThat(chunk.mergeKey()).isEqualTo("42");
        assertThat(chunk.content()).isEqualTo("hybrid vector excerpt");
        assertThat(chunk.section()).isEqualTo("MD&A");
        assertThat(chunk.filingDate()).isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(jdbcTemplate.lastSql())
                .contains("c.embedding <=> ?::vector")
                .contains("f.ticker = ?")
                .contains("f.form = ?");
    }

    @Test
    void findSimilarChunksHandlesMissingMetadata() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(retrievedChunkRow(
                7L,
                "no metadata",
                "0000000000-00-000004",
                0,
                "",
                "GS",
                "Example Corp",
                "10-Q",
                null,
                ""
        ));
        PgVectorHybridChunkRepository repository = new PgVectorHybridChunkRepository(jdbcTemplate, objectMapper);

        List<RetrievedChunk> chunks = repository.findSimilarChunks(new float[] {0.1f}, 5, null, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().section()).isNull();
        assertThat(chunks.getFirst().metadata()).isEmpty();
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
