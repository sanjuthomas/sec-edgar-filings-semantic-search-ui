package com.edgar.search.service;

import com.edgar.search.config.SearchProperties;
import com.edgar.search.model.ChunkMatch;
import com.edgar.search.model.SearchForm;
import com.edgar.search.model.SearchResponse;
import com.edgar.search.repository.FilingChunkRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagSearchService {

    private static final String SYSTEM_PROMPT = """
            You are a financial research assistant answering questions about SEC EDGAR filings.
            Use ONLY the provided source excerpts. If the excerpts do not contain enough information,
            say you cannot determine the answer from the available filings.

            Rules:
            - Answer in clear, concise prose.
            - Cite sources inline using bracketed numbers like [1], [2] that refer to the excerpt labels.
            - Do not invent facts, names, or dates that are not supported by the excerpts.
            - Do not include chain-of-thought or reasoning sections; respond with the final answer only.
            """;

    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final FilingChunkRepository filingChunkRepository;
    private final TickerResolver tickerResolver;
    private final SearchProperties searchProperties;

    public RagSearchService(
            EmbeddingModel embeddingModel,
            ChatClient chatClient,
            FilingChunkRepository filingChunkRepository,
            TickerResolver tickerResolver,
            SearchProperties searchProperties
    ) {
        this.embeddingModel = embeddingModel;
        this.chatClient = chatClient;
        this.filingChunkRepository = filingChunkRepository;
        this.tickerResolver = tickerResolver;
        this.searchProperties = searchProperties;
    }

    public SearchResponse answer(SearchForm form) {
        long retrievalStart = System.currentTimeMillis();

        TickerResolver.ResolvedTicker resolvedTicker = tickerResolver.resolve(
                form.question(),
                form.normalizedTicker()
        );

        float[] queryVector = embeddingModel.embed(form.question());
        List<ChunkMatch> sources = filingChunkRepository.findSimilarChunks(
                queryVector,
                searchProperties.topK(),
                resolvedTicker.ticker(),
                form.normalizedForm()
        );

        long retrievalMs = System.currentTimeMillis() - retrievalStart;

        if (sources.isEmpty()) {
            return new SearchResponse(
                    form.question(),
                    "No matching filing excerpts were found in pgvector for this question.",
                    List.of(),
                    resolvedTicker.ticker(),
                    resolvedTicker.inferred(),
                    retrievalMs,
                    0L
            );
        }

        String context = buildContext(sources);
        long generationStart = System.currentTimeMillis();

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(form.question(), context))
                .call()
                .content();

        long generationMs = System.currentTimeMillis() - generationStart;

        return new SearchResponse(
                form.question(),
                answer,
                sources,
                resolvedTicker.ticker(),
                resolvedTicker.inferred(),
                retrievalMs,
                generationMs
        );
    }

    private String buildContext(List<ChunkMatch> sources) {
        return sources.stream()
                .map(this::formatSource)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatSource(ChunkMatch source) {
        StringBuilder builder = new StringBuilder();
        builder.append("[")
                .append(source.citationNumber())
                .append("] ")
                .append(source.ticker())
                .append(" ")
                .append(source.form());

        if (source.filingDate() != null) {
            builder.append(" (filed ").append(source.filingDate()).append(")");
        }

        builder.append(" | accession ").append(source.accessionNumber());
        builder.append(" | chunk ").append(source.chunkIndex());

        if (source.section() != null && !source.section().isBlank()) {
            builder.append(" | section ").append(source.section());
        }

        builder.append("\n").append(source.content().strip());
        return builder.toString();
    }

    private String userPrompt(String question, String context) {
        return """
                Question:
                %s

                Source excerpts:
                %s

                Answer the question using only the excerpts above. Include inline citations like [1].
                """.formatted(question, context);
    }
}
