package com.sanjuthomas.search.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingDimensionValidator {

    public EmbeddingDimensionValidator(EmbeddingModel embeddingModel, SearchProperties searchProperties) {
        float[] probe = embeddingModel.embed("dimension probe");
        if (probe.length != searchProperties.embeddingDimensions()) {
            throw new IllegalStateException(
                    "Embedding model produced "
                            + probe.length
                            + " dimensions, expected "
                            + searchProperties.embeddingDimensions()
            );
        }
    }
}
