package com.sanjuthomas.search.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingDimensionValidatorTest {

    @Test
    void acceptsMatchingEmbeddingDimensions() {
        new EmbeddingDimensionValidator(stubEmbeddingModel(1024), new SearchProperties(25, 1024, 50, 25));
    }

    @Test
    void rejectsMismatchedEmbeddingDimensions() {
        assertThatThrownBy(() ->
                new EmbeddingDimensionValidator(stubEmbeddingModel(768), new SearchProperties(25, 1024, 50, 25))
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("768")
                .hasMessageContaining("1024");
    }

    private static EmbeddingModel stubEmbeddingModel(int dimensions) {
        return new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                return new float[dimensions];
            }

            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                return new float[dimensions];
            }

            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(
                    org.springframework.ai.embedding.EmbeddingRequest request
            ) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
