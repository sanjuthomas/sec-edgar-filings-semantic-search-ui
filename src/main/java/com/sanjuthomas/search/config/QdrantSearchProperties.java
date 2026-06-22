package com.sanjuthomas.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.qdrantsearch")
public record QdrantSearchProperties(
        boolean enabled,
        String url,
        String collection,
        String denseVector,
        String bm25Vector,
        String bm25Model
) {
    public QdrantSearchProperties {
        if (url == null) {
            url = "";
        }
        if (collection == null) {
            collection = "filing_chunks";
        }
        if (denseVector == null) {
            denseVector = "dense";
        }
        if (bm25Vector == null) {
            bm25Vector = "content-bm25";
        }
        if (bm25Model == null) {
            bm25Model = "Qdrant/bm25";
        }
    }
}
