package com.sanjuthomas.search.repository;

import com.sanjuthomas.search.config.QdrantSearchProperties;
import com.sanjuthomas.search.model.RetrievedChunk;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantHybridChunkRepositoryTest {

    private MockWebServer mockWebServer;
    private QdrantHybridChunkRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        repository = new QdrantHybridChunkRepository(new QdrantSearchProperties(
                true,
                mockWebServer.url("/").toString().replaceAll("/$", ""),
                "filing_chunks",
                "dense",
                "content-bm25",
                "Qdrant/bm25"
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void findDenseChunksPostsToQueryEndpointWithDenseVector() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(denseResponseJson()));

        List<RetrievedChunk> chunks = repository.findDenseChunks(new float[] {0.1f, 0.2f}, 5, "GS", "10-K");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().mergeKey()).isEqualTo("point-1");
        assertThat(chunks.getFirst().ticker()).isEqualTo("GS");
        assertThat(chunks.getFirst().content()).isEqualTo("dense excerpt");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/collections/filing_chunks/points/query");
        assertThat(request.getBody().readUtf8())
                .contains("\"using\":\"dense\"")
                .contains("\"limit\":5")
                .contains("\"ticker\"")
                .contains("\"GS\"");
    }

    @Test
    void findBm25ChunksPostsDocumentQueryWithSparseVector() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(bm25ResponseJson()));

        List<RetrievedChunk> chunks = repository.findBm25Chunks("revenue growth", 3, null, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().mergeKey()).isEqualTo("point-2");
        assertThat(chunks.getFirst().content()).isEqualTo("bm25 excerpt");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getBody().readUtf8())
                .contains("\"using\":\"content-bm25\"")
                .contains("\"text\":\"revenue growth\"")
                .contains("\"model\":\"Qdrant/bm25\"");
    }

    @Test
    void returnsEmptyListWhenQdrantResponseHasNoPoints() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"result\":{\"points\":[]},\"status\":\"ok\"}"));

        List<RetrievedChunk> chunks = repository.findBm25Chunks("missing", 3, null, null);

        assertThat(chunks).isEmpty();
    }

    @Test
    void mapsCustomMetadataAndBlankSection() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "points": [
                              {
                                "id": "point-3",
                                "score": 0.5,
                                "payload": {
                                  "content": "metadata excerpt",
                                  "accession_number": "0000000000-00-000003",
                                  "chunk_index": 1,
                                  "ticker": "MSFT",
                                  "company_name": "Microsoft Corp",
                                  "form": "10-K",
                                  "filing_date": "2025-02-01",
                                  "section": "",
                                  "custom_field": "value"
                                }
                              }
                            ]
                          }
                        }
                        """));

        List<RetrievedChunk> chunks = repository.findDenseChunks(new float[] {0.1f}, 1, null, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().section()).isNull();
        assertThat(chunks.getFirst().metadata()).containsEntry("custom_field", "value");
        assertThat(chunks.getFirst().filingDate()).isEqualTo(java.time.LocalDate.of(2025, 2, 1));
    }

    private static String denseResponseJson() {
        return """
                {
                  "result": {
                    "points": [
                      {
                        "id": "point-1",
                        "score": 0.91,
                        "payload": {
                          "content": "dense excerpt",
                          "accession_number": "0000000000-00-000001",
                          "chunk_index": 0,
                          "ticker": "GS",
                          "company_name": "Example Corp",
                          "form": "10-K",
                          "filing_date": "2025-01-01",
                          "document_url": "https://example.com/filing"
                        }
                      }
                    ]
                  },
                  "status": "ok"
                }
                """;
    }

    private static String bm25ResponseJson() {
        return """
                {
                  "result": {
                    "points": [
                      {
                        "id": "point-2",
                        "score": 4.2,
                        "payload": {
                          "content": "bm25 excerpt",
                          "accession_number": "0000000000-00-000002",
                          "chunk_index": 2,
                          "ticker": "AAPL",
                          "company_name": "Apple Inc",
                          "form": "10-Q"
                        }
                      }
                    ]
                  },
                  "status": "ok"
                }
                """;
    }
}
