package com.sanjuthomas.search.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgSearchPropertiesTest {

    @Test
    void normalizesNullConnectionFields() {
        PgSearchProperties properties = new PgSearchProperties(true, null, null, null);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.url()).isEmpty();
        assertThat(properties.username()).isEmpty();
        assertThat(properties.password()).isEmpty();
    }
}
