# Judge-Generator Implementation Summary

## Changes Made

### ✅ 1. Configuration
**Files Modified:**
- `backend/src/main/java/com/example/clothesstoreagent/config/AppProps.java`
  - Added `nlqMode` property (defaults to "single-call")
  - Added getter/setter methods
  
- `backend/src/main/resources/application.yml`
  - Added `nlqMode: ${APP_NLQ_MODE:single-call}` configuration

### ✅ 2. DTOs for Judge-Generator Flow
**Files Created:**
- `backend/src/main/java/com/example/clothesstoreagent/nlq/judge/JudgeResponse.java`
  - Fields: `decision`, `missing`, `reasons`, `question`
  - Strict Jackson annotations
  
- `backend/src/main/java/com/example/clothesstoreagent/nlq/judge/GeneratorResponse.java`
  - Fields: `sql`, `params`
  - Strict Jackson annotations
  
- `backend/src/main/java/com/example/clothesstoreagent/nlq/judge/RepairResponse.java`
  - Fields: `sql`, `params`
  - Strict Jackson annotations

### ✅ 3. Judge-Generator Provider
**Files Created:**
- `backend/src/main/java/com/example/clothesstoreagent/nlq/JudgeGeneratorAzureProvider.java`
  - Implements `NlqProvider` interface
  - Three main methods:
    - `callJudge()` - Step 1: Decide proceed/clarify/reject
    - `callGenerator()` - Step 2: Generate SQL
    - `callRepair()` - Step 3: Repair SQL on DB error (public for controller access)
  - Uses `temperature=0` and `response_format={"type":"json_object"}`
  - Strict JSON parsing with Jackson
  - Comprehensive error handling

### ✅ 4. Controller Updates
**Files Modified:**
- `backend/src/main/java/com/example/clothesstoreagent/api/NlqController.java`
  - Added `AppProps` dependency (for backward compatibility)
  - Modified `handle()` method to call `executeWithRepair()`
  - Added `executeWithRepair()` method:
    - Executes SQL
    - Catches DB errors
    - Calls `JudgeGeneratorAzureProvider.callRepair()` if provider supports it
    - Retries once with repaired SQL
    - Returns result with `repaired`, `repairedSql`, `originalError` fields

### ✅ 5. Provider Configuration
**Files Modified:**
- `backend/src/main/java/com/example/clothesstoreagent/config/NlqConfig.java`
  - Reads `nlqMode` from AppProps
  - When `nlqProvider=azure` and `nlqMode=judge-generator`:
    - Returns `JudgeGeneratorAzureProvider`
  - Otherwise:
    - Returns existing providers (`AzureOpenAIProvider`, `RuleBasedProvider`, etc.)
  - Added logging for provider initialization

### ✅ 6. Test Updates
**Files Modified:**
- `backend/src/test/java/com/example/clothesstoreagent/api/NlqControllerTest.java`
  - Updated all test methods to mock `AppProps`
  - Updated constructor calls to include `AppProps` parameter
  - All 3 tests pass successfully

## API Backward Compatibility

The `/api/nlq` endpoint remains **100% backward compatible**:

### Existing Fields (Unchanged)
- `decision` - "execute", "clarify", or "reject"
- `sql` - Generated SQL (when decision=execute)
- `params` - SQL parameters
- `question` - Clarification or rejection message
- `missing` - Missing information list
- `ran` - Whether query was executed
- `result` - Query results

### New Fields (Judge-Generator Only)
- `repaired` - Boolean, true if SQL was repaired
- `originalSql` - The original SQL before repair
- `repairedSql` - The corrected SQL (also in `sql` field)
- `originalError` - The DB error that triggered repair

## Feature Flag Behavior

| nlqProvider | nlqMode           | Provider Used                | Behavior                    |
|-------------|-------------------|------------------------------|------------------------------|
| `azure`     | `judge-generator` | JudgeGeneratorAzureProvider  | Judge → Generator + Repair   |
| `azure`     | `single-call`     | AzureOpenAIProvider          | Single LLM call              |
| `azure`     | (not set)         | AzureOpenAIProvider          | Single LLM call (default)    |
| `rule`      | (any)        | RuleBasedProvider       | Rule-based (no LLM)          |
| `aws`       | (any)        | AwsBedrockProvider      | AWS Bedrock                  |

## Guardrails Maintained

All existing security guardrails remain active in Judge-Generator mode:

✅ **Read-only database access** (`app.readOnly=true`)  
✅ **SELECT-only enforcement** (rejects INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/REVOKE)  
✅ **Table allowlist** (`app.allowTables` - empty allows all)  
✅ **Max rows limit** (`app.defaultMaxRows=200`)  
✅ **Query timeout** (`app.defaultQueryTimeoutSeconds=20`)  
✅ **Multi-statement prevention** (rejects queries with semicolons)  
✅ **Strict JSON parsing** (Jackson ObjectMapper)  
✅ **Deterministic responses** (`temperature=0`)  
✅ **JSON-only output** (`response_format={"type":"json_object"}` for Azure)

## LLM Prompts

All three agent prompts follow best practices:

### Judge Prompt
- Analyzes user request against schema
- Decides: proceed (clear), clarify (ambiguous), reject (unsafe/out-of-scope)
- Returns structured JSON with reasons

### Generator Prompt
- Produces safe SELECT queries only
- Uses schema context and examples
- Applies business logic (e.g., completed orders filter)
- Supports named parameters

### Repair Prompt
- Receives failed SQL + error message + original prompt
- Analyzes common errors (wrong columns, missing JOINs, syntax)
- Returns corrected SQL

All prompts include:
- SQL Server syntax requirements
- Schema constraints
- Safety rules
- Revenue calculation formula
- UTC date handling

## Testing Results

```bash
✅ NlqControllerTest.clarifyDecisionDoesNotExecuteSql - PASSED
✅ NlqControllerTest.executeDecisionRunsQueryWhenAllowed - PASSED
✅ NlqControllerTest.rejectDecisionReturnsBadRequest - PASSED

BUILD SUCCESSFUL in 12s
```

## Files Changed

### Created (7 files)
1. `backend/src/main/java/com/example/clothesstoreagent/nlq/JudgeGeneratorAzureProvider.java`
2. `backend/src/main/java/com/example/clothesstoreagent/nlq/judge/JudgeResponse.java`
3. `backend/src/main/java/com/example/clothesstoreagent/nlq/judge/GeneratorResponse.java`
4. `backend/src/main/java/com/example/clothesstoreagent/nlq/judge/RepairResponse.java`
5. `JUDGE_GENERATOR_GUIDE.md`
6. `JUDGE_GENERATOR_SUMMARY.md` (this file)

### Modified (5 files)
1. `backend/src/main/java/com/example/clothesstoreagent/config/AppProps.java`
2. `backend/src/main/resources/application.yml`
3. `backend/src/main/java/com/example/clothesstoreagent/api/NlqController.java`
4. `backend/src/main/java/com/example/clothesstoreagent/config/NlqConfig.java`
5. `backend/src/test/java/com/example/clothesstoreagent/api/NlqControllerTest.java`

## Deployment

### Enable Judge-Generator Mode
```bash
# Set environment variable
export APP_NLQ_MODE=judge-generator  # Linux/Mac
set APP_NLQ_MODE=judge-generator     # Windows

# Or update application.yml
app:
  nlqMode: judge-generator
```

### Revert to Single-Call Mode
```bash
# Remove environment variable or set:
export APP_NLQ_MODE=single-call
```

### No Code Changes Required
Existing deployments continue to work without any changes. The feature is opt-in via configuration only.

## Next Steps

1. **Test with real Azure OpenAI**
   - Configure Azure credentials
   - Test Judge decisions (proceed/clarify/reject)
   - Test Generator SQL quality
   - Test Repair on intentional SQL errors

2. **Monitor Performance**
   - Judge call latency
   - Generator call latency
   - Repair call latency (when triggered)
   - Total request latency (2-3 LLM calls)

3. **Collect Metrics**
   - Clarify rate
   - Reject rate
   - Proceed rate
   - Repair success rate

4. **Fine-tune Prompts**
   - Adjust Judge criteria based on clarify/reject patterns
   - Improve Generator examples
   - Enhance Repair logic for common errors
