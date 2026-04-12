# API Behavior

The backend exposes these endpoints:

- `POST /api/chat`
- `POST /api/rag/reindex`

## Request

Send JSON:

```json
{
  "message": "your text here"
}
```

- `message` is required and must be non-empty.

If missing/blank, the API returns `400 Bad Request`.

## Processing flow

For each message, the backend does this in order:

1. Detects a deterministic skill from routing rules.
   - `post_purchase_support` for returns/refunds.
   - `order_fulfillment_support` for shipping/delivery ETA.
   - `general_help` when no rule matches.
2. Retrieves top matching context chunks using hybrid retrieval (Qdrant vector search + BM25 + reranking).
3. Runs local extraction tools before MCP:
   - `extract_return_context`
   - `extract_delivery_context`
4. Runs one MCP tool call on one of two MCP servers:
   - `policy-mcp-server` tools:
     - `check_return_eligibility`
     - `estimate_refund_timeline`
   - `fulfillment-mcp-server` tools:
     - `estimate_delivery_eta`
     - `recommend_shipping_option`
5. Builds one assistant reply string and returns metadata with route/tool traces.

## Response

On success (`200 OK`):

```json
{
  "assistantMessage": "Skill: ...",
  "skill": "post_purchase_support | order_fulfillment_support | general_help",
  "route": "policy-mcp-server/check_return_eligibility | fulfillment-mcp-server/estimate_delivery_eta | none",
  "ragContext": ["..."],
  "localTools": [
    {
      "toolName": "extract_return_context | extract_delivery_context",
      "input": "original message",
      "output": "parsed facts summary",
      "ok": true
    }
  ],
  "mcpCalls": [
    {
      "serverId": "policy-mcp-server | fulfillment-mcp-server",
      "toolName": "check_return_eligibility | estimate_refund_timeline | estimate_delivery_eta | recommend_shipping_option",
      "input": "original message",
      "output": "tool output text",
      "ok": true,
      "errorCode": null,
      "latencyMs": 1
    }
  ]
}
```

Notes:

- `localTools` and `mcpCalls` are empty arrays when no tool is used.
- `ragContext` can be empty if no relevant snippet matches.
- `assistantMessage` is always a plain text summary of what happened.

## RAG Reindex Endpoint

`POST /api/rag/reindex` runs ingestion and indexing:

1. Reads docs from `APP_RAG_SOURCE_DIR`.
2. Chunks text with configured size and overlap.
3. Creates embeddings for each chunk.
4. Upserts chunks into Qdrant.

If `APP_RAG_ADMIN_TOKEN` is set, include header:

- `X-RAG-ADMIN-TOKEN: <token>`
