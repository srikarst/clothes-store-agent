# Judge-Generator Implementation Guide

## Overview

The Spring Boot application now supports **Judge-Generator** mode alongside the existing single-call NLQ mode.

## Feature Flag

The feature is controlled by the `nlqMode` configuration property:

```yaml
app:
  nlqProvider: azure  # or rule, aws, bedrock
  nlqMode: judge-generator   # "single-call" (default) or "judge-generator"
```

Or via environment variable:
```bash
APP_NLQ_MODE=judge-generator
```

## How It Works

### Single-Call Mode (Default)
- **One LLM call** returns: `{decision, sql, params, question, missing}`
- Used by existing `AzureOpenAIProvider`

### Judge-Generator Mode
A **two-step flow** with automatic repair:

#### Step 1: Judge
- LLM call returns: `{decision: "proceed"|"clarify"|"reject", missing: [], reasons: [], question: ""}`
- If `"clarify"` → Returns to user with clarifying question
- If `"reject"` → Returns error with rejection message
- If `"proceed"` → Continues to Step 2

#### Step 2: Generator
- LLM call returns: `{sql: "SELECT ...", params: {...}}`
- SQL is executed against database

#### Step 3: Repair (on DB error)
- If execution fails, **one repair attempt** is made
- LLM receives original prompt + failed SQL + error message
- Returns: `{sql: "SELECT ...", params: {...}}`
- Repaired SQL is executed **once**

## API Response

The `/api/nlq` endpoint remains backward compatible. New fields in Judge-Generator mode:

### Clarify Response
```json
{
  "decision": "clarify",
  "clarify": true,
  "question": "Could you please specify the date range?",
  "missing": ["date_range"],
  "ran": false
}
```

### Reject Response (HTTP 400)
```json
{
  "decision": "reject",
  "error": "NLQ_REJECTED",
  "message": "Sorry, I can't perform destructive operations.",
  "ran": false
}
```

### Execute Response (with repair)
```json
{
  "decision": "execute",
  "sql": "SELECT TOP 10 ...",
  "params": {},
  "ran": true,
  "repaired": true,
  "originalSql": "SELECT TOP 10 ... (invalid)",
  "result": {
    "columns": ["name", "revenue"],
    "rowCount": 10,
    "truncated": false,
    "rows": [...]
  }
}
```

## Guardrails (Maintained)

All existing guardrails remain in place:
- ✅ Read-only database connection
- ✅ SELECT-only queries (no INSERT/UPDATE/DELETE/DROP)
- ✅ Table allowlist (`app.allowTables`)
- ✅ Max rows limit (`app.defaultMaxRows`)
- ✅ Query timeout (`app.defaultQueryTimeoutSeconds`)
- ✅ Strict JSON parsing with Jackson
- ✅ `temperature=0` for deterministic responses
- ✅ `response_format={"type":"json_object"}` for Azure

## Configuration Example

### Judge-Generator with Azure OpenAI
```yaml
app:
  nlqProvider: azure
  nlqMode: judge-generator
  azureOpenaiEndpoint: ${APP_AZURE_ENDPOINT}
  azureOpenaiDeployment: ${APP_AZURE_DEPLOYMENT}
  azureOpenaiApiVersion: ${APP_AZURE_API_VERSION}
  azureOpenaiApiKey: ${APP_AZURE_API_KEY}
```

### Fallback to Single-Call Mode
```yaml
app:
  nlqProvider: azure
  nlqMode: single-call  # or omit (defaults to single-call)
```

### Rule-Based Mode (No LLM)
```yaml
app:
  nlqProvider: rule
  # nlqMode is ignored for rule-based provider
```

## Implementation Details

### New Classes
- `JudgeGeneratorAzureProvider` - Implements two-step Judge → Generator flow
- `JudgeResponse` - DTO for Judge step
- `GeneratorResponse` - DTO for Generator step
- `RepairResponse` - DTO for Repair step

### Modified Classes
- `NlqController` - Added `executeWithRepair()` method
- `NlqConfig` - Routes to `JudgeGeneratorAzureProvider` when `nlqMode=judge-generator`
- `AppProps` - Added `nlqMode` property

### Logging
All steps are logged at INFO level:
```
[INFO] Judge: proceed - calling Generator
[INFO] Generator: produced SQL (length=245) with 0 params
[WARN] SQL execution failed: Invalid column name 'revenue'. Attempting repair...
[INFO] Repair produced new SQL (length=248), retrying execution
[INFO] Repair successful! Rows returned: 10
```

## Testing

Run tests to verify:
```bash
cd backend
gradlew.bat test --tests "com.example.clothesstoreagent.api.NlqControllerTest"
```

## Migration

Existing deployments continue to work with **no changes required**. To enable Judge-Generator:

1. Set `APP_NLQ_MODE=judge-generator` environment variable
2. Restart application
3. Verify `/api/nlq` responses include new fields

To revert: Remove the environment variable or set `APP_NLQ_MODE=single-call`.
