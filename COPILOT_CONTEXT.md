App: Clothes Store Agent (Spring Boot + SQL Server).
Key rules for model outputs:
- ONE SELECT only, SQL Server dialect.
- Revenue = SUM(oi.qty * oi.unit_price * (1 - oi.discount))
- Orders must be completed: o.status='completed'
- Use UTC windows: SYSUTCDATETIME(); last month = closed-open window via EOMONTH.
- Return STRICT JSON: {"sql":"...", "params":{}} (or decision JSON).
Endpoints:
- POST /api/nlq -> uses NlqProvider (currently Azure provider or rule-based).
- POST /api/query -> executes SQL with guardrails.
- GET /api/schema -> tables/columns/FKs/samples; allowTables in config.
Flags to keep:
APP_NLQ_GUARDS, APP_NLQ_DECISION (single|two-step), APP_NLQ_JUDGE,
APP_NLQ_HISTORY, APP_NLQ_HISTORY_MAX_TURNS, APP_NLQ_ROUTER,
APP_AZURE_DEPLOYMENT_SMALL, _LARGE, _JUDGE.
Coding style: small classes, pure functions, no global state, feature flags, unit tests for parsing/guards.

Config comes from `backend/.env` (auto-loaded). Core keys:
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `APP_NLQ_PROVIDER` (rule default, set to `azure` to call Azure OpenAI)
- `APP_AZURE_ENDPOINT`, `APP_AZURE_API_KEY`, `APP_AZURE_DEPLOYMENT`, `APP_AZURE_API_VERSION`
