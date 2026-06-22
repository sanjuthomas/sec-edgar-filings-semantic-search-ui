package com.sanjuthomas.search.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFormTest {

    @Test
    void normalizesTickerAndForm() {
        SearchForm form = new SearchForm(
                "question",
                "qwen3:30b",
                "qdrant",
                25,
                " gs ",
                " 10-k "
        );

        assertThat(form.normalizedTicker()).isEqualTo("GS");
        assertThat(form.normalizedForm()).isEqualTo("10-K");
        assertThat(form.vectorStoreType()).isEqualTo(VectorStoreType.QDRANT);
    }

    @Test
    void treatsBlankFiltersAsNull() {
        SearchForm form = new SearchForm(
                "question",
                "qwen3:14b",
                "pgvector",
                10,
                "  ",
                ""
        );

        assertThat(form.normalizedTicker()).isNull();
        assertThat(form.normalizedForm()).isNull();
        assertThat(form.vectorStoreType()).isEqualTo(VectorStoreType.PGVECTOR);
    }
}
