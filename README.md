# Clothes Store Agent (AI Agent Demo)

This repository is mainly an AI agent playground.  
The "clothes store" schema is just sample data used to exercise agent behaviors.

## What we built (agent-focused)

- Multi-turn chat orchestration with conversation IDs and history memory.
- Domain routing for `auto`, `general`, `analytics_sql`, and `mcp_tools`.
- Structured model contract for agent outputs (`final` or `tool_call` JSON).
- Bounded tool loop in chat (`maxSteps`) with optional tool trace output.
- Tool abstraction via registries, combining local tools and MCP-backed tools.
- NLQ pipeline (`clarify` / `execute` / `reject`) with optional execution and SQL repair path.
- MCP server manager + stdio JSON-RPC transport for external tool integration.

## Why this repo is useful

- Demonstrates practical agent patterns without heavy framework lock-in.
- Shows safe tool-use constraints (domain restrictions, guarded SQL execution).
- Includes a full loop: UI -> orchestration -> tools -> memory -> response.
- Good baseline for experimenting with routing, memory, tools, and observability.

## Project structure

- `backend`: Spring Boot APIs, chat orchestrator, NLQ providers, tool registries, MCP integration.
- `frontend`: React panels for chat, NLQ, schema inspection, and manual SQL runs.

## API overview

- `POST /api/chat` - main agent endpoint.
- `DELETE /api/chat/conversation/{id}` - clear chat memory.
- `POST /api/nlq` - prompt to SQL decision/plan (and optional execution).
- `DELETE /api/nlq/conversation/{id}` - clear NLQ memory.
- `POST /api/query` - guarded SQL execution endpoint (`SELECT`-only).
- `GET /api/schema` - schema metadata for prompts/tools.
- `GET /api/mcp/servers` - MCP status and discovered server info.
- `POST /api/mcp/servers/{id}/refresh` - refresh MCP tool inventory.
- `GET /api/health` - liveness check.

## Prerequisites

- Java 17
- Node.js 18+ and npm

## Run locally

1) Start backend (PowerShell):

```powershell
cd backend
$env:SERVER_PORT=8081
.\gradlew.bat bootRun
```

2) Start frontend (new terminal):

```powershell
cd frontend
npm install
npm start
```

Frontend runs on `http://localhost:3000` and proxies API calls to `http://localhost:8081`.

## Configuration notes

- Default profile: `h2` with seeded demo data.
- Optional profile: `sqlserver` via `application-sqlserver.yml`.
- App settings are under `backend/src/main/resources/application.yml`.
- Backend default port in config is `8080`; local run above uses `8081` to match frontend proxy.

## Sample domain data (for demos only)

The demo schema uses retail-like tables (`dbo.customers`, `dbo.products`, `dbo.orders`, `dbo.order_items`) plus small playground tables (`dbo.person`, `dbo.address`).

## Useful commands

- Backend tests: `cd backend; .\gradlew.bat test`
- Frontend tests: `cd frontend; npm test`
- Frontend build: `cd frontend; npm run build`
