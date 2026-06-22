package com.sanjuthomas.search.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SearchProperties.class,
        VectorStoresProperties.class,
        PgSearchProperties.class,
        QdrantSearchProperties.class
})
public class AppConfig {
}
