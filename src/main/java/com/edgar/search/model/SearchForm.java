package com.edgar.search.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchForm(
        @NotBlank(message = "Please enter a question.")
        @Size(max = 2000, message = "Question is too long.")
        String question,

        @NotBlank(message = "Please select an Ollama model.")
        @Size(max = 100, message = "Model name is too long.")
        String chatModel,

        @NotBlank(message = "Please select a vector store.")
        @Size(max = 20, message = "Vector store is too long.")
        String vectorStore,

        @Size(max = 10, message = "Ticker is too long.")
        String ticker,

        @Size(max = 20, message = "Form type is too long.")
        String form
) {
    public String normalizedTicker() {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        return ticker.trim().toUpperCase();
    }

    public String normalizedForm() {
        if (form == null || form.isBlank()) {
            return null;
        }
        return form.trim().toUpperCase();
    }

    public VectorStoreType vectorStoreType() {
        return VectorStoreType.fromValue(vectorStore);
    }
}
