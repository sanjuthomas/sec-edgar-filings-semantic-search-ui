package com.sanjuthomas.search.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaModelServiceTest {

    private MockWebServer mockWebServer;
    private OllamaModelService service;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        service = new OllamaModelService(
                mockWebServer.url("/").toString().replaceAll("/$", ""),
                "qwen3:14b"
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void defaultChatModelReturnsConfiguredValue() {
        assertThat(service.defaultChatModel()).isEqualTo("qwen3:14b");
    }

    @Test
    void listChatModelsReturnsCompletionModelsSorted() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "models": [
                            {"name": "bge-m3", "capabilities": ["embedding"]},
                            {"name": "qwen3:14b", "capabilities": ["completion"]},
                            {"name": "llama3:8b", "capabilities": ["completion"]}
                          ]
                        }
                        """));

        assertThat(service.listChatModels()).containsExactly("llama3:8b", "qwen3:14b");
    }

    @Test
    void listChatModelsIncludesModelsWithoutCapabilities() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "models": [
                            {"name": "legacy-model"}
                          ]
                        }
                        """));

        assertThat(service.listChatModels()).containsExactly("legacy-model");
    }

    @Test
    void listChatModelsFallsBackToDefaultWhenResponseEmpty() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"models\":[]}"));

        assertThat(service.listChatModels()).containsExactly("qwen3:14b");
    }

    @Test
    void listChatModelsFallsBackToDefaultWhenRequestFails() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThat(service.listChatModels()).containsExactly("qwen3:14b");
    }

    @Test
    void isKnownChatModelChecksListedModels() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"models\":[{\"name\":\"qwen3:14b\",\"capabilities\":[\"completion\"]}]}"));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"models\":[{\"name\":\"qwen3:14b\",\"capabilities\":[\"completion\"]}]}"));

        assertThat(service.isKnownChatModel("qwen3:14b")).isTrue();
        assertThat(service.isKnownChatModel("missing-model")).isFalse();
    }
}
