package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.config.VectorStoresProperties;
import com.sanjuthomas.search.model.ChunkMatch;
import com.sanjuthomas.search.model.VectorStoreType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantChunkRepositoryTest {

    private MockWebServer mockWebServer;
    private QdrantChunkRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        repository = new QdrantChunkRepository(new VectorStoresProperties(
                "qdrant",
                new VectorStoresProperties.Qdrant(
                        mockWebServer.url("/").toString().replaceAll("/$", ""),
                        "filing_chunks"
                )
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void vectorStoreTypeIsQdrant() {
        assertThat(repository.vectorStoreType()).isEqualTo(VectorStoreType.QDRANT);
    }

    @Test
    void findSimilarChunksMapsPayloadAndMetadata() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson()));

        List<ChunkMatch> matches = repository.findSimilarChunks(new float[] {0.1f, 0.2f}, 5, "GS", "10-K");

        assertThat(matches).hasSize(1);
        ChunkMatch match = matches.getFirst();
        assertThat(match.citationNumber()).isEqualTo(1);
        assertThat(match.content()).isEqualTo("vector excerpt");
        assertThat(match.distance()).isCloseTo(0.09, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(match.ticker()).isEqualTo("GS");
        assertThat(match.companyName()).isEqualTo("Example Corp");
        assertThat(match.form()).isEqualTo("10-K");
        assertThat(match.filingDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(match.section()).isEqualTo("Item 1");
        assertThat(match.metadata()).containsEntry("custom_field", "value");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/collections/filing_chunks/points/query");
        assertThat(request.getBody().readUtf8())
                .contains("\"using\":\"dense\"")
                .contains("\"limit\":5")
                .contains("\"ticker\"")
                .contains("\"GS\"")
                .contains("\"form\"")
                .contains("\"10-K\"");
    }

    @Test
    void findSimilarChunksWithoutFiltersOmitsFilterClause() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"result\":{\"points\":[]},\"status\":\"ok\"}"));

        List<ChunkMatch> matches = repository.findSimilarChunks(new float[] {0.5f}, 3, null, null);

        assertThat(matches).isEmpty();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getBody().readUtf8()).doesNotContain("\"filter\"");
    }

    @Test
    void returnsEmptyListWhenResponseHasNoPoints() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"result\":{\"points\":[]},\"status\":\"ok\"}"));

        List<ChunkMatch> matches = repository.findSimilarChunks(new float[] {0.5f}, 3, null, null);

        assertThat(matches).isEmpty();
    }

    @Test
    void handlesMissingScoreAndMalformedPayloadValues() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "points": [
                              {
                                "score": null,
                                "payload": {
                                  "content": null,
                                  "chunk_index": "7",
                                  "filing_date": ""
                                }
                              }
                            ]
                          }
                        }
                        """));

        List<ChunkMatch> matches = repository.findSimilarChunks(new float[] {0.5f}, 1, null, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().distance()).isEqualTo(1.0);
        assertThat(matches.getFirst().content()).isEmpty();
        assertThat(matches.getFirst().chunkIndex()).isEqualTo(7);
        assertThat(matches.getFirst().filingDate()).isNull();
    }

    private static String responseJson() {
        return """
                {
                  "result": {
                    "points": [
                      {
                        "score": 0.91,
                        "payload": {
                          "content": "vector excerpt",
                          "accession_number": "0000000000-00-000001",
                          "chunk_index": 0,
                          "ticker": "GS",
                          "company_name": "Example Corp",
                          "form": "10-K",
                          "filing_date": "2025-01-01",
                          "document_url": "https://example.com/filing",
                          "section": "Item 1",
                          "custom_field": "value"
                        }
                      }
                    ]
                  },
                  "status": "ok"
                }
                """;
    }
}
