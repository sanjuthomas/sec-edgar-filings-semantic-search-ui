package com.sanjuthomas.search.service;

import com.sanjuthomas.search.repository.FilingMetadataRepository;
import com.sanjuthomas.search.repository.FilingMetadataRepository.CompanyRecord;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TickerResolver {

    private static final Pattern TICKER_TOKEN = Pattern.compile("\\b[A-Z]{2,5}\\b");
    private static final Pattern COMPANY_SUFFIX = Pattern.compile(
            "\\b(INC\\.?|CORP\\.?|LTD\\.?|LLC|CO\\.?|PLC|GROUP|HOLDINGS?|THE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final FilingMetadataRepository filingMetadataRepository;

    private List<String> knownTickers = List.of();
    private List<CompanyRecord> knownCompanies = List.of();

    public TickerResolver(FilingMetadataRepository filingMetadataRepository) {
        this.filingMetadataRepository = filingMetadataRepository;
    }

    static TickerResolver forTesting(List<String> tickers, List<CompanyRecord> companies) {
        TickerResolver resolver = new TickerResolver(null);
        resolver.knownTickers = tickers;
        resolver.knownCompanies = companies;
        return resolver;
    }

    @PostConstruct
    void loadMetadata() {
        knownTickers = filingMetadataRepository.findDistinctTickers();
        knownCompanies = filingMetadataRepository.findDistinctCompanies();
    }

    /**
     * Uses the explicit ticker when provided; otherwise infers one from the question.
     */
    public ResolvedTicker resolve(String question, String explicitTicker) {
        if (explicitTicker != null && !explicitTicker.isBlank()) {
            return new ResolvedTicker(explicitTicker.trim().toUpperCase(), false);
        }

        String fromCompany = detectCompanyName(question);
        if (fromCompany != null) {
            return new ResolvedTicker(fromCompany, true);
        }

        String fromTicker = detectTickerSymbol(question);
        if (fromTicker != null) {
            return new ResolvedTicker(fromTicker, true);
        }

        return new ResolvedTicker(null, false);
    }

    private String detectTickerSymbol(String question) {
        var tickerSet = knownTickers.stream()
                .map(ticker -> ticker.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        var matcher = TICKER_TOKEN.matcher(question);
        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            candidates.add(matcher.group());
        }

        return candidates.stream()
                .filter(tickerSet::contains)
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }

    private String detectCompanyName(String question) {
        String normalizedQuestion = normalizeText(question);

        return knownCompanies.stream()
                .filter(company -> companyMentioned(normalizedQuestion, company.companyName()))
                .max(Comparator.comparingInt(company -> company.companyName().length()))
                .map(CompanyRecord::ticker)
                .orElse(null);
    }

    private boolean companyMentioned(String normalizedQuestion, String companyName) {
        String normalizedCompany = normalizeCompanyName(companyName);
        if (normalizedCompany.length() < 4) {
            return false;
        }

        if (normalizedQuestion.contains(normalizedCompany)) {
            return true;
        }

        String[] words = normalizedCompany.split("\\s+");
        if (words.length >= 2) {
            String twoWordPhrase = words[0] + " " + words[1];
            return normalizedQuestion.contains(twoWordPhrase);
        }

        return words[0].length() >= 5 && normalizedQuestion.contains(words[0]);
    }

    private String normalizeCompanyName(String companyName) {
        return normalizeText(COMPANY_SUFFIX.matcher(companyName).replaceAll(" "));
    }

    private String normalizeText(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record ResolvedTicker(String ticker, boolean inferred) {
    }
}
