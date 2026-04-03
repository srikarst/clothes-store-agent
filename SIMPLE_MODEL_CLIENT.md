# SimpleModelClient: `generateAssistantMessage(...)`

This note explains what happens inside:

- `modelClient.generateAssistantMessage(cleanMessage, intent, ragContext, mcp)`

in the simple chat flow.

## Where it runs

`SimpleAgentService.chat(...)` prepares:

- `cleanMessage` (trimmed user input)
- `intent` (rule-based intent label)
- `ragContext` (top retrieved context lines)
- `mcp` (optional MCP tool result)

Then it calls `SimpleModelClient.generateAssistantMessage(...)` to get an LLM-written reply.

## What the method does

1. **Configuration guard**
   - Returns `null` unless all Azure settings are non-blank:
     - `APP_AZURE_ENDPOINT`
     - `APP_AZURE_DEPLOYMENT`
     - `APP_AZURE_API_VERSION`
     - `APP_AZURE_API_KEY`

2. **Normalize context for prompt**
   - Builds `mcpInfo`:
     - `"none"` when MCP did not run
     - otherwise `"tool=<name>, ok=<true|false>, output=<text>"`
   - Builds `ragInfo`:
     - `"none"` when no context was retrieved
     - otherwise joins entries with `" | "`

3. **Build a single user prompt**
   - Injects:
     - user message
     - detected intent
     - retrieved context text
     - MCP result text
   - Adds behavior instructions:
     - be a helpful clothes-store assistant
     - plain text
     - max 3 short sentences
     - use retrieved context when available

4. **Create and send Azure Chat Completions request**
   - Payload includes:
     - system message: concise support assistant
     - user message: composed prompt
     - `temperature` from `APP_CHAT_DEFAULT_TEMPERATURE` (default `0.2`)
   - URL shape:
     - `<endpoint>/openai/deployments/<deployment>/chat/completions?api-version=<apiVersion>`
   - Headers:
     - `Content-Type: application/json`
     - `api-key: <APP_AZURE_API_KEY>`
   - Timeout: 30 seconds

5. **Parse response**
   - Expects `choices[0].message.content`
   - Returns trimmed content when present and non-empty
   - Returns `null` for:
     - non-2xx HTTP status
     - missing content
     - empty content
     - any exception during request or parsing

## Why `null` is acceptable here

`SimpleAgentService` treats `null` as "LLM unavailable/failed" and falls back to local deterministic text via `buildAssistantMessage(...)`.

This keeps chat responses working even when Azure configuration is missing or the model call fails.
