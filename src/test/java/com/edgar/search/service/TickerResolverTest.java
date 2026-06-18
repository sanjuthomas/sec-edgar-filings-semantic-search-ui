package com.edgar.search.service;

import com.edgar.search.repository.FilingMetadataRepository.CompanyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TickerResolverTest {

    private TickerResolver tickerResolver;

    @BeforeEach
    void setUp() {
        tickerResolver = TickerResolver.forTesting(
                List.of("A", "ADBE", "GS", "IT", "SO"),
                List.of(
                        new CompanyRecord("GS", "GOLDMAN SACHS GROUP INC"),
                        new CompanyRecord("ADBE", "ADOBE INC.")
                )
        );
    }

    @Test
    void prefersExplicitTicker() {
        var resolved = tickerResolver.resolve("buyback question", "ADBE");

        assertThat(resolved.ticker()).isEqualTo("ADBE");
        assertThat(resolved.inferred()).isFalse();
    }

    @Test
    void infersTickerFromQuestion() {
        var resolved = tickerResolver.resolve(
                "Was there a share buyback program announced by ADBE?",
                null
        );

        assertThat(resolved.ticker()).isEqualTo("ADBE");
        assertThat(resolved.inferred()).isTrue();
    }

    @Test
    void infersTickerFromCompanyName() {
        var resolved = tickerResolver.resolve(
                "Who are the elected directors in Goldman Sachs?",
                null
        );

        assertThat(resolved.ticker()).isEqualTo("GS");
        assertThat(resolved.inferred()).isTrue();
    }

    @Test
    void infersTickerFromCompanyNameAdobe() {
        var resolved = tickerResolver.resolve(
                "Do you know if the Adobe board approved a buyback program? If so, how much was it for?",
                null
        );

        assertThat(resolved.ticker()).isEqualTo("ADBE");
        assertThat(resolved.inferred()).isTrue();
    }

    @Test
    void doesNotTreatCommonWordsAsTickers() {
        var resolved = tickerResolver.resolve(
                "How much was it for if so?",
                null
        );

        assertThat(resolved.ticker()).isNull();
    }

    @Test
    void ignoresAmbiguousSingleLetterTickers() {
        var resolved = tickerResolver.resolve(
                "Was there a share buyback program announced?",
                null
        );

        assertThat(resolved.ticker()).isNull();
    }
}
