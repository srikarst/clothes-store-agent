# API Behavior

The backend exposes one endpoint:

- `POST /api/chat`

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

1. Detects a simple intent from keywords.
   - Example: `"add 12 and 8"` -> intent `mcp_math`.
2. Retrieves top matching context snippets from an in-memory RAG store.
   - Example: `"what is your return policy"` -> retrieves the returns-policy snippet in `ragContext`.
3. If intent is math-related, runs a simple MCP-style math tool.
   - Example: `"multiply 6 and 7"` -> calls MCP-style tool `multiply` and gets output `42`.
4. Builds one assistant reply string and returns metadata.
   - Example: response includes `assistantMessage` plus `intent`, `ragContext`, and `mcp`.

## Response

On success (`200 OK`):

```json
{
  "assistantMessage": "Intent: ...",
  "intent": "mcp_math | returns_policy | product_help | general_help",
  "ragContext": ["..."],
  "mcp": {
    "serverId": "demo-mcp-server",
    "toolName": "add | multiply | math_tool",
    "input": "original message",
    "output": "tool output text",
    "ok": true
  }
}
```

Notes:

- `mcp` is `null` when no tool is used.
- `ragContext` can be empty if no relevant snippet matches.
- `assistantMessage` is always a plain text summary of what happened.
