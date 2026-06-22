package com.sanjuthomas.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vectorstores")
public record VectorStoresProperties(
        String defaultVectorStore,
        Qdrant qdrant
) {
    public record Qdrant(String url, String collection) {
    }
}
