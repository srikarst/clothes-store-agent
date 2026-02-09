# MCP (Model Context Protocol) — Phase 2 Setup

Phase 2 adds **MCP tools** via **local stdio transport** (a local process, JSON-RPC over stdin/stdout).

This repo includes a demo MCP server so you can test tool calling end-to-end immediately.

## Enable the demo MCP server

1) Ensure you have **Node.js** installed (`node` available on your PATH).

2) Update `backend/src/main/resources/application.yml`:

- Set `app.mcp.enabled: true`
- Set the demo server `enabled: true`

Example:

```yaml
app:
  mcp:
    enabled: true
    protocolVersion: "2024-11-05"
    servers:
      - id: "demo"
        enabled: true
        transport: "stdio"
        command: "node"
        args:
          - "mcp-servers/demo/server.js"
        timeoutSeconds: 20
        domains: ["mcp_tools"]
```

## Run the backend from repo root

The demo config uses a **repo-root-relative** script path (`mcp-servers/demo/server.js`).

Run from the repo root so the relative path resolves correctly:

```bash
./backend/gradlew bootRun
```

On Windows (PowerShell):

```powershell
.\backend\gradlew.bat bootRun
```

If you run from the `backend/` directory instead, update the demo args to:

```yaml
args:
  - "../mcp-servers/demo/server.js"
```

## Try it

In the UI, select **Domain → MCP Tools**, then ask:

- “add 2 and 3”
- “echo hello”

Enable **Show tool trace** to see `provider: mcp:demo`.

## Debug endpoints

- `GET /api/mcp/servers`: list configured servers + connection status + tool counts
- `POST /api/mcp/servers/{id}/refresh`: refresh tool list for a server


