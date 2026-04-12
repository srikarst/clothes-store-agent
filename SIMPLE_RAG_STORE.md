# Qdrant RAG Notes

This project now uses:

- `QdrantRagStore` for query-time retrieval
- `QdrantRagIndexer` for ingestion-time chunking and indexing

## Query-time retrieval

File: `backend/src/main/java/com/example/clothesstoreagent/simple/QdrantRagStore.java`

Flow:

1. Embed user query with `AzureOpenAiEmbeddingClient`.
2. Vector retrieval from Qdrant `points/search`.
3. BM25 lexical retrieval from source docs (`APP_RAG_SOURCE_DIR`).
4. Merge vector and BM25 candidates.
5. Semantic rerank pass (embedding cosine on top candidates).
6. Return top `ragContext` strings.

## Ingestion-time indexing (Phase 2)

File: `backend/src/main/java/com/example/clothesstoreagent/simple/QdrantRagIndexer.java`

Flow:

1. Load docs from `APP_RAG_SOURCE_DIR` (default `backend/rag-docs` when running from repo root, `rag-docs` when running from backend dir).
2. Split docs into chunks using token-window chunking:
   - `APP_RAG_CHUNK_SIZE_TOKENS` (default `400`)
   - `APP_RAG_CHUNK_OVERLAP_TOKENS` (default `60`)
3. Delete existing chunks for each source file.
4. Embed each chunk.
5. Upsert vectors + payload metadata to Qdrant `points`.

Payload includes:

- configured text key (`APP_RAG_TEXT_PAYLOAD_KEY`, default `text`)
- configured source key (`APP_RAG_SOURCE_PAYLOAD_KEY`, default `source`)
- `doc_id`, `chunk_index`, `chunk_count`, `updated_at`, `indexed_at`

## Trigger indexing

- Startup indexing:
  - `APP_RAG_INGEST_ON_STARTUP=true`
  - Optional strict startup: `APP_RAG_INGEST_FAIL_STARTUP_ON_ERROR=true`
- Manual endpoint:
  - `POST /api/rag/reindex`
  - Optional header when enabled: `X-RAG-ADMIN-TOKEN`
