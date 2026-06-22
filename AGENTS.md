# AGENTS.md

Guidance for AI coding agents working in **sec-edgar-filings-semantic-search-ui**.

## Project summary

Spring Boot RAG web app for SEC EDGAR filings. Users ask a natural-language question in a Thymeleaf UI; the server embeds the query, retrieves similar chunks from **pgvector** or **Qdrant**, and generates a cited answer with a local **Ollama** LLM.

This repo is the **search + answer UI only**. Filing download and vector indexing live in sibling projects:

- [sec-edgar-filings-to-pgvector](https://github.com/sanjuthomas/sec-edgar-filings-to-pgvector)
- [sec-edgar-filings-to-qdrant](https://github.com/sanjuthomas/sec-edgar-filings-to-qdrant)
- [sec-edgar-filings-rag-demo](https://github.com/sanjuthomas/sec-edgar-filings-rag-demo) — full Docker Compose stack

Stack: Java **21**, Maven, Spring Boot **3.4**, Spring AI **1.0**, Thymeleaf, JDBC (pgvector), Qdrant REST, Ollama (chat + query embeddings).

---

## Spring Boot & Spring AI version policy (required)

**Authoritative versions:** `spring-boot-starter-parent` and `spring-ai-bom` in `pom.xml` (currently Boot **3.4.5**, Spring AI **1.0.0**).

Agents **must**:

1. Keep the project on **Spring Boot 3.4.x** with **Spring AI 1.0.x**. Do not upgrade to Boot 4 or mix incompatible Spring AI versions without explicit maintainer approval.
2. Change Boot / Spring AI versions **only** via `pom.xml` parent and BOM — do not pin unrelated Spring artifacts separately unless docs require it.
3. Use existing starters: `spring-boot-starter-web`, `spring-ai-starter-model-ollama`.
4. After dependency bumps, run `mvn verify` and fix breakage before finishing.

**Compatible versions** (defined in `pom.xml` — keep in sync when upgrading):

| Property | Current | Notes |
|----------|---------|--------|
| `java.version` | 21 | CI uses JDK 21; Java 25 may break Mockito tests |
| Parent Boot | 3.4.5 | Single source of truth |
| `spring-ai.version` | 1.0.0 | BOM import for AI starters |

---

## Embedding model policy (required)

Query embeddings **must** match the ingest pipeline.

| Setting | Value |
|---------|--------|
| Model | `bge-m3` (Ollama) |
| Dimensions | **1024** |
| Runtime | Spring AI `OllamaEmbeddingModel` via `spring.ai.ollama.embedding.model` |
| Chat | Separate Ollama model (`spring.ai.ollama.chat.options.model`) |

Requires `ollama pull bge-m3` (or equivalent) before search. Query embeddings **must** match the ingest pipeline (`BAAI/bge-m3`, 1024-dim).

Agents **must not** switch to `bge-small-en-v1.5` (384-dim) or other models without re-indexing both pgvector and Qdrant collections.

---

## Vector store architecture

Retrieval is routed by `ChunkSearchRouter` to repository implementations:

| Store | Class | Access |
|-------|-------|--------|
| `pgvector` | `PgVectorChunkRepository` | JDBC cosine search on `filing_chunks` + `filings` |
| `qdrant` | `QdrantChunkRepository` | REST query to `/collections/{collection}/points/query` |

- Config prefix: `app.vectorstores` (not `spring.datasource` — that is pgvector JDBC only).
- Default vector store: `app.vectorstores.default-vector-store` (currently **qdrant**).
- Qdrant URL default: `http://localhost:16333` (host); Compose demo uses `http://qdrant:6333` internally.
- Schema is owned by ingest projects — this app does **not** create tables or Qdrant collections.

---

## RAG pipeline (server-side)

All orchestration happens in `RagSearchService` — chunks are **not** sent to the browser until the LLM finishes.

1. `SearchController` validates `SearchForm` and calls `RagSearchService.answer`.
2. Optional `TickerResolver` infers ticker from the question when no ticker filter is set.
3. `EmbeddingModel` embeds the question via Ollama `bge-m3` (1024-dim).
4. `ChunkSearchRouter` retrieves top-K chunks from the selected vector store.
5. `ChatClient` + Ollama synthesize an answer with inline `[1]`, `[2]` citations.
6. Thymeleaf renders `index.html` with answer, sources, and metadata.

**UI form fields** (`SearchForm`):

| Field | Required | Notes |
|-------|----------|-------|
| `question` | Yes | Max 2000 chars |
| `chatModel` | Yes | Populated from Ollama `/api/tags`; validated server-side |
| `vectorStore` | Yes | `pgvector` or `qdrant` |
| `chunkCount` | Yes | 1–500; presets 10/25/50/100 in UI; default from `app.search.top-k` |
| `ticker` | No | Uppercased filter |
| `form` | No | e.g. `10-K` |

---

## Testing conventions

- Run `mvn verify` after code changes (same as CI).
- Unit tests live under `src/test/java`; no integration tests against live pgvector/Qdrant/Ollama today.
- Prefer testing pure logic (`TickerResolver`, `VectorStoreType`, form normalization) without spinning up external services.
- Use **Java 21** for tests if Mockito fails on newer JDKs (see README troubleshooting).

Agents **should** add tests when changing validation, routing, or ticker-resolution logic.

---

## Code conventions

- Package root: `com.sanjuthomas.search`
- DTOs: Java `record`s with Jakarta validation on `SearchForm`.
- Config: `@ConfigurationProperties` records (`SearchProperties`, `VectorStoresProperties`) enabled in `AppConfig`.
- Repositories: `ChunkSearchRepository` interface; one implementation per `VectorStoreType`.
- Controllers: `SearchController` — GET `/` and POST `/search`; Thymeleaf only (no REST API).
- Match existing style; avoid unrelated refactors.
- Keep `spring.http.client.factory: simple` in `application.yml` — avoids JDK HttpClient issues with long Ollama calls on Boot 3.4.

---

## Commands

```bash
mvn spring-boot:run              # run on :8095 (requires Ollama with bge-m3)
mvn verify                       # build + tests (CI)
mvn test                         # unit tests only
docker compose up --build        # local image; needs pgvector + Ollama on host
```

UI: http://localhost:8095/

**Local prerequisites** (not started by this repo):

- PostgreSQL + pgvector on `localhost:5433`, database `edgar`
- Qdrant on `localhost:16333` (if using Qdrant)
- Ollama on `localhost:11434` with chat models (default config: `qwen3:30b`)

---

## Do not

- Commit secrets, `.env`, or credentials.
- Change embedding model or dimensions without coordinating with ingest repos.
- Use Spring AI `PgVectorStore` schema — this app queries ingest-project tables directly.
- Confuse `spring.datasource` (pgvector JDBC) with `app.vectorstores` (retrieval routing + Qdrant URL).
- Remove user-selectable vector store or Ollama model dropdowns without maintainer approval.
- Edit unrelated files or expand scope beyond the task.
- Commit unless explicitly requested by the user.

---

## References

- [README.md](README.md) — features, configuration, troubleshooting, sequence diagram
- [sec-edgar-filings-rag-demo](https://github.com/sanjuthomas/sec-edgar-filings-rag-demo) — full-stack Compose architecture
- [LICENSE](LICENSE) — MIT
