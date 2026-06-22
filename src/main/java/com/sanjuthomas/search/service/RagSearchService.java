package com.sanjuthomas.search.service;

import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.SearchForm;
import com.sanjuthomas.search.model.SearchResponse;
import com.sanjuthomas.search.model.VectorStoreType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
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
    private final ChunkSearchRouter chunkSearchRouter;
    private final PgHybridSearchService pgHybridSearchService;
    private final QdrantHybridSearchService qdrantHybridSearchService;
    private final TickerResolver tickerResolver;

    public RagSearchService(
            EmbeddingModel embeddingModel,
            ChatClient chatClient,
            ChunkSearchRouter chunkSearchRouter,
            PgHybridSearchService pgHybridSearchService,
            QdrantHybridSearchService qdrantHybridSearchService,
            TickerResolver tickerResolver
    ) {
        this.embeddingModel = embeddingModel;
        this.chatClient = chatClient;
        this.chunkSearchRouter = chunkSearchRouter;
        this.pgHybridSearchService = pgHybridSearchService;
        this.qdrantHybridSearchService = qdrantHybridSearchService;
        this.tickerResolver = tickerResolver;
    }

    public SearchResponse answer(SearchForm form) {
        VectorStoreType vectorStore = form.vectorStoreType();
        long retrievalStart = System.currentTimeMillis();

        TickerResolver.ResolvedTicker resolvedTicker = tickerResolver.resolve(
                form.question(),
                form.normalizedTicker()
        );

        float[] queryVector = embeddingModel.embed(form.question());
        List<ChunkMatch> sources;
        if (vectorStore == VectorStoreType.PGVECTOR && pgHybridSearchService.isEnabled()) {
            sources = pgHybridSearchService.search(
                    form.question(),
                    queryVector,
                    form.chunkCount(),
                    resolvedTicker.ticker(),
                    form.normalizedForm()
            );
        } else if (vectorStore == VectorStoreType.QDRANT && qdrantHybridSearchService.isEnabled()) {
            sources = qdrantHybridSearchService.search(
                    form.question(),
                    queryVector,
                    form.chunkCount(),
                    resolvedTicker.ticker(),
                    form.normalizedForm()
            );
        } else {
            sources = chunkSearchRouter.findSimilarChunks(
                    vectorStore,
                    queryVector,
                    form.chunkCount(),
                    resolvedTicker.ticker(),
                    form.normalizedForm()
            );
        }

        long retrievalMs = System.currentTimeMillis() - retrievalStart;

        if (sources.isEmpty()) {
            return new SearchResponse(
                    form.question(),
                    "No matching filing excerpts were found in "
                            + vectorStore.value()
                            + " for this question.",
                    List.of(),
                    form.chatModel(),
                    vectorStore.value(),
                    resolvedTicker.ticker(),
                    resolvedTicker.inferred(),
                    retrievalMs,
                    0L
            );
        }

        String context = buildContext(sources);
        long generationStart = System.currentTimeMillis();

        String answer = chatClient.prompt()
                .options(OllamaOptions.builder().model(form.chatModel()).build())
                .system(SYSTEM_PROMPT)
                .user(userPrompt(form.question(), context))
                .call()
                .content();

        long generationMs = System.currentTimeMillis() - generationStart;

        return new SearchResponse(
                form.question(),
                answer,
                sources,
                form.chatModel(),
                vectorStore.value(),
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
