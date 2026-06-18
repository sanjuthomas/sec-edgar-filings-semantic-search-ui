package com.edgar.search.model;

import java.util.Arrays;
import java.util.Locale;

public enum VectorStoreType {
    PGVECTOR("pgvector"),
    QDRANT("qdrant");

    private final String value;

    VectorStoreType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static VectorStoreType fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Vector store is required.");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown vector store: " + raw));
    }
}
