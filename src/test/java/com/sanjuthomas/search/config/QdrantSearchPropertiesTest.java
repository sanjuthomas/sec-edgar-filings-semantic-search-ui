package com.sanjuthomas.search.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantSearchPropertiesTest {

    @Test
    void appliesDefaultsForNullFields() {
        QdrantSearchProperties properties = new QdrantSearchProperties(true, null, null, null, null, null);

        assertThat(properties.url()).isEmpty();
        assertThat(properties.collection()).isEqualTo("filing_chunks");
        assertThat(properties.denseVector()).isEqualTo("dense");
        assertThat(properties.bm25Vector()).isEqualTo("content-bm25");
        assertThat(properties.bm25Model()).isEqualTo("Qdrant/bm25");
    }
}
