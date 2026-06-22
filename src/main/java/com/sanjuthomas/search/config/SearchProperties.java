package com.sanjuthomas.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        int topK,
        int embeddingDimensions,
        int hybridRetrievalTopK,
        int rerankTopN
) {
}
