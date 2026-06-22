package com.sanjuthomas.search.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@Service
public class OllamaModelService {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelService.class);

    private final RestClient restClient;
    private final String defaultChatModel;

    public OllamaModelService(
            @Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl,
            @Value("${spring.ai.ollama.chat.options.model}") String defaultChatModel
    ) {
        this.restClient = RestClient.builder().baseUrl(ollamaBaseUrl).build();
        this.defaultChatModel = defaultChatModel;
    }

    public String defaultChatModel() {
        return defaultChatModel;
    }

    public List<String> listChatModels() {
        try {
            OllamaTagsResponse response = restClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(OllamaTagsResponse.class);

            if (response == null || response.models() == null || response.models().isEmpty()) {
                return List.of(defaultChatModel);
            }

            List<String> models = response.models().stream()
                    .filter(this::isChatModel)
                    .map(OllamaModel::name)
                    .sorted(Comparator.naturalOrder())
                    .toList();

            return models.isEmpty() ? List.of(defaultChatModel) : models;
        } catch (Exception ex) {
            log.warn("Failed to load models from Ollama, using default only: {}", ex.getMessage());
            return List.of(defaultChatModel);
        }
    }

    public boolean isKnownChatModel(String model) {
        return listChatModels().contains(model);
    }

    private boolean isChatModel(OllamaModel model) {
        if (model.capabilities() == null || model.capabilities().isEmpty()) {
            return true;
        }
        return model.capabilities().contains("completion");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaTagsResponse(List<OllamaModel> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaModel(
            String name,
            List<String> capabilities
    ) {
    }
}
