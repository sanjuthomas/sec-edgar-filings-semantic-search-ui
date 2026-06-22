package com.sanjuthomas.search.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreTypeTest {

    @Test
    void parsesKnownValues() {
        assertThat(VectorStoreType.fromValue("pgvector")).isEqualTo(VectorStoreType.PGVECTOR);
        assertThat(VectorStoreType.fromValue("QDRANT")).isEqualTo(VectorStoreType.QDRANT);
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> VectorStoreType.fromValue("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsUnknownValue() {
        assertThatThrownBy(() -> VectorStoreType.fromValue("pinecone"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
