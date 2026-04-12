# Chat Usage

This app is a simple chat demo.

## Start the app

1. Start backend:
   - `cd backend`
   - `./gradlew.bat bootRun`
2. Start frontend:
   - `cd frontend`
   - `npm install`
   - `npm start`

Open `http://localhost:3000`.

## Use the chat

1. Type your message in the chat box.
2. Press `Enter` or click `Send`.
3. Read the agent response.

Try messages like:
- `Can I return this hoodie after 12 days if it is unused and has tags?`
- `How fast can this ship internationally with express delivery?`
- `Which shipping option should I use if I need it in 2 days?`

## RAG Indexing (Phase 2)

The app now supports file-based ingestion and Qdrant indexing:

1. Put source docs in `backend/rag-docs/` (or set `APP_RAG_SOURCE_DIR`).
2. Configure environment variables:
   - `APP_AZURE_ENDPOINT`
   - `APP_AZURE_API_KEY`
   - `APP_RAG_EMBEDDING_DEPLOYMENT`
   - `APP_RAG_QDRANT_URL`
   - `APP_RAG_QDRANT_COLLECTION`
3. Optional indexing controls:
   - `APP_RAG_CHUNK_SIZE_TOKENS` (default `400`)
   - `APP_RAG_CHUNK_OVERLAP_TOKENS` (default `60`)
   - `APP_RAG_INGEST_BATCH_SIZE` (default `32`)
   - `APP_RAG_DELETE_SOURCE_BEFORE_UPSERT` (default `true`)
   - `APP_RAG_INGEST_ON_STARTUP=true` to auto-index on boot
4. Manual reindex endpoint:
   - `POST /api/rag/reindex`
   - Optional auth header if configured: `X-RAG-ADMIN-TOKEN`

## Hybrid Retrieval + Reranking

Query-time retrieval now supports vector + BM25 hybrid search with semantic reranking.

Optional controls:

- `APP_RAG_HYBRID_ENABLED` (default `true`)
- `APP_RAG_RERANK_ENABLED` (default `true`)
- `APP_RAG_VECTOR_CANDIDATES` (default `12`)
- `APP_RAG_BM25_CANDIDATES` (default `12`)
- `APP_RAG_RERANK_CANDIDATES` (default `8`)
- `APP_RAG_RERANK_WEIGHT_VECTOR` (default `0.45`)
- `APP_RAG_RERANK_WEIGHT_BM25` (default `0.25`)
- `APP_RAG_RERANK_WEIGHT_SEMANTIC` (default `0.30`)
