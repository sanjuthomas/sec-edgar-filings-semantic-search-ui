package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.support.StubJdbcTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FilingMetadataRepositoryTest {

    @Test
    void findDistinctTickersReturnsSortedTickers() {
        FilingMetadataRepository repository = new FilingMetadataRepository(new StubJdbcTemplate(Map.of()));

        List<String> tickers = repository.findDistinctTickers();

        assertThat(tickers).containsExactly("GS");
    }

    @Test
    void findDistinctCompaniesMapsRows() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(Map.of(
                "ticker", "MSFT",
                "company_name", "Microsoft Corp"
        ));
        FilingMetadataRepository repository = new FilingMetadataRepository(jdbcTemplate);

        List<FilingMetadataRepository.CompanyRecord> companies = repository.findDistinctCompanies();

        assertThat(companies).hasSize(1);
        assertThat(companies.getFirst().ticker()).isEqualTo("MSFT");
        assertThat(companies.getFirst().companyName()).isEqualTo("Microsoft Corp");
        assertThat(jdbcTemplate.lastSql()).contains("SELECT DISTINCT ticker, company_name");
    }
}
