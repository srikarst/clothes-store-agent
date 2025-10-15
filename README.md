# Clothes Store Agent — Text → SQL on SQL Server

A tiny Text→SQL starter you can extend with AI. It exposes:

- `GET /api/health` – health check  
- `GET /api/schema` – tables, columns, FKs, sample values (filtered by allowlist)  
- `POST /api/query` – **SELECT-only** executor (max rows + timeout caps)  
- `POST /api/nlq` – **English → SQL** (rule-based by default; Azure OpenAI optional)

A minimal HTML page at `/` lets you test without a separate frontend.

> Ships with a **rule-based NLQ** provider. Flip one config to use **Azure OpenAI** instead.

---

## Tech

- Java 17, Spring Boot 3.3 (Gradle **wrapper** included — no global Gradle required)
- SQL Server (Docker) + Microsoft JDBC Driver
- Minimal static UI, plus VS Code tasks

---

## Quick start

### 0) Prereqs
- JDK 17  
- Docker Desktop (for SQL Server)

### 1) Start SQL Server (Docker)

**Bash / WSL / macOS**
# If port 1433 is busy, change the left side (e.g., -p 11433:1433) and update DB_URL.
docker run -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=YourStrong!Passw0rd" \
  -p 1433:1433 --name clothes-store-agent-sql -d mcr.microsoft.com/mssql/server:2022-latest

**PowerShell**
docker run -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=YourStrong!Passw0rd" `
  -p 1433:1433 --name clothes-store-agent-sql -d mcr.microsoft.com/mssql/server:2022-latest

**(Optional) create DB `clothes_store_agent`:**

_Bash / WSL / macOS_
docker exec -it clothes-store-agent-sql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P 'YourStrong!Passw0rd' \
  -Q "IF DB_ID('clothes_store_agent') IS NULL CREATE DATABASE clothes_store_agent;"

_PowerShell_
docker exec -it clothes-store-agent-sql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "YourStrong!Passw0rd" `
  -Q "IF DB_ID('clothes_store_agent') IS NULL CREATE DATABASE clothes_store_agent;"

### 2) Configure environment (`backend/.env`)

The app reads a `.env` file automatically.

```bash
cd backend
cp .env.example .env   # use copy-item on PowerShell
# edit .env and set the values below
```

`.env` keys (adjust if you change ports or enable Azure):

```
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=clothes_store_agent;encrypt=true;trustServerCertificate=true
DB_USER=sa
DB_PASSWORD=YourStrong!Passw0rd

# NLQ provider: rule | azure
APP_NLQ_PROVIDER=rule

# Azure OpenAI (only required when APP_NLQ_PROVIDER=azure)
APP_AZURE_ENDPOINT=https://<your-aoai>.openai.azure.com
APP_AZURE_API_KEY=<your_key>
APP_AZURE_DEPLOYMENT=gpt-4o-mini
APP_AZURE_API_VERSION=2025-01-01-preview
```

> Prefer secrets managers or real env vars in production. Keep `.env` out of Git.

**(Recommended) allowlist** in `application.yml`:
app:
  allowTables:
    - dbo.customers
    - dbo.products
    - dbo.orders
    - dbo.order_items

### 3) Run (Gradle **wrapper**)
cd backend

# Windows
.\gradlew.bat bootRun

# macOS/Linux
./gradlew bootRun

Open: <http://localhost:8080>

> VS Code task `app:run (Windows, wrapper, 8081)` starts the app on port 8081.

---

## API

### `GET /api/health`
{"status":"ok"}

### `GET /api/schema`
Returns schema context for Text→SQL:
{
  "tables": [ { "TABLE_SCHEMA": "dbo", "TABLE_NAME": "orders" } ],
  "columnsByTable": {
    "dbo.orders": [ { "COLUMN_NAME":"id","DATA_TYPE":"int" } ]
  },
  "fks": [
    { "from_schema":"dbo","from_table":"order_items","from_column":"order_id",
      "to_schema":"dbo","to_table":"orders","to_column":"id" }
  ],
  "samplesByColumn": {
    "dbo.products.category": ["Apparel","Electronics"]
  }
}

### `POST /api/query` — **safe SELECT executor**
_Request_
{
  "sql": "SELECT TOP 10 name FROM sys.tables WHERE name LIKE :like",
  "params": { "like": "o%" },
  "maxRows": 100,
  "timeoutSeconds": 10
}
_Response_
{
  "columns": ["name"],
  "rowCount": 3,
  "truncated": false,
  "rows": [ { "name":"orders" }, { "name":"orders_archive" }, { "name":"order_items" } ]
}

### `POST /api/nlq` — **English → SQL**
Generates SQL from plain English. If `"execute": true`, it runs the SQL and returns results.

_Request_
{ "prompt":"top 5 products by revenue last month", "execute": false }
_Response (rule or Azure)_
{
  "recognizedIntent": "ai_azure",
  "sql": "SELECT TOP 5 p.name, SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue\nFROM dbo.orders o\nJOIN dbo.order_items oi ON oi.order_id = o.id\nJOIN dbo.products p ON p.id = oi.product_id\nWHERE o.status='completed'\n  AND o.created_at >= DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-2))\n  AND o.created_at <  DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-1))\nGROUP BY p.name\nORDER BY revenue DESC",
  "params": {},
  "ran": false
}

---

## Enabling Azure OpenAI (optional)

1) Create an **Azure OpenAI** resource and deploy a **Chat Completions** model (e.g., `gpt-4o-mini`).
2) Update `backend/.env`:

```
APP_NLQ_PROVIDER=azure
APP_AZURE_ENDPOINT=https://<your-aoai>.openai.azure.com
APP_AZURE_API_KEY=<your_key>
APP_AZURE_DEPLOYMENT=gpt-4o-mini
APP_AZURE_API_VERSION=2025-01-01-preview
```

3) Restart the app and test:
curl -s http://localhost:8081/api/nlq -H "Content-Type: application/json" \
  -d '{"prompt":"daily revenue last 7 days","execute":false}'

**Security tips**
- Never commit keys; use `.env` only for local dev.
- Rotate keys if ever exposed.

---

## Minimal Web UI

Open <http://localhost:8081> and use:
- **Load Schema** → shows tables/columns (+ FK & samples if available)
- **Run SELECT** → executes SQL safely
- **Ask in English** → calls `/api/nlq` (dry-run or execute)

---

## Safety & guardrails

- **Read-only**: only `SELECT` allowed; multi-statement blocked.  
- **Allowlist**: restricts which tables can be referenced (`app.allowTables`).  
- **Caps**: max rows & query timeout (configurable).  
- (Optional) **Extra guard**: block disallowed keywords; validate `schema.table` in joins.

> In production, keep these enabled and use a DB login with **read-only** permissions.

---

## Troubleshooting

- **Port 1433 busy**: stop other SQL Server instances or map `-p 11433:1433` and update `DB_URL`.  
- **SSL trust error**: keep `encrypt=true;trustServerCertificate=true` in `DB_URL` for local dev.  
- **`sqlcmd` path**: container uses `/opt/mssql-tools18/bin/sqlcmd`.  
- **Azure 401/404**: verify endpoint base (`https://<resource>.openai.azure.com`), deployment name, API version.

---

## Project structure
backend/
├─ build.gradle, settings.gradle
├─ src/main/java/com/example/clothesstoreagent/
│ ├─ ClothesStoreAgentApplication.java
│ ├─ api/HealthController.java
│ ├─ api/SchemaController.java
│ ├─ api/QueryController.java
│ ├─ api/NlqController.java
│ ├─ config/AppProps.java
│ ├─ config/NlqConfig.java
│ ├─ nlq/NlqProvider.java
│ ├─ nlq/RuleBasedProvider.java
│ ├─ nlq/AzureOpenAIProvider.java
│ ├─ nlq/AwsBedrockProvider.java
│ ├─ service/SchemaService.java
│ └─ service/QueryService.java
└─ src/main/resources/
  ├─ application.yml
  └─ static/index.html


---

## Roadmap snapshot (to multi-model)

- **Now**: Single-turn Text→SQL (rule or Azure), schema API, safe execution  
- **Next**: Eval harness + light gates → Clarify on ambiguity (decision JSON)  
- **Then**: Judge/Repair (toggle) → Short conversation context → Observability  
- **Goal**: **Multi-model** router (mini for easy, larger for hard) behind flags

---

**License**: MIT (or your choice)  
**Notes**: Contributions welcome; keep secrets out of commits.
