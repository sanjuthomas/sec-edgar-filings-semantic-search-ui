package com.sanjuthomas.search.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FilingMetadataRepository {

    private final JdbcTemplate jdbcTemplate;

    public FilingMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findDistinctTickers() {
        return jdbcTemplate.queryForList(
                "SELECT ticker FROM filings GROUP BY ticker ORDER BY LENGTH(ticker) DESC, ticker",
                String.class
        );
    }

    public List<CompanyRecord> findDistinctCompanies() {
        return jdbcTemplate.query(
                """
                        SELECT DISTINCT ticker, company_name
                        FROM filings
                        WHERE company_name IS NOT NULL AND company_name <> ''
                        ORDER BY ticker
                        """,
                (rs, rowNum) -> new CompanyRecord(
                        rs.getString("ticker"),
                        rs.getString("company_name")
                )
        );
    }

    public record CompanyRecord(String ticker, String companyName) {
    }
}
