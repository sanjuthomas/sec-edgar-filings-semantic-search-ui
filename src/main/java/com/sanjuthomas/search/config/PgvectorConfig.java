package com.sanjuthomas.search.config;

import com.pgvector.PGvector;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class PgvectorConfig {

    private final DataSource dataSource;

    public PgvectorConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void registerVectorType() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            PGvector.addVectorType(connection);
        }
    }
}
