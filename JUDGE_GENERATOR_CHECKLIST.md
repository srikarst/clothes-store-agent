# Judge-Generator Implementation Checklist

## ✅ Implementation Complete

### Core Features
- [x] Feature flag `nlqMode` added to AppProps and application.yml
- [x] DTOs created: JudgeResponse, GeneratorResponse, RepairResponse
- [x] JudgeGeneratorAzureProvider implements Judge → Generator flow
- [x] Repair logic with one retry on DB error
- [x] NlqController handles two-step flow with executeWithRepair()
- [x] NlqConfig routes to JudgeGeneratorAzureProvider when mode=judge-generator
- [x] All tests passing (NlqControllerTest)
- [x] No compilation errors

### Guardrails Maintained
- [x] Read-only database connection
- [x] SELECT-only queries enforced
- [x] Table allowlist respected
- [x] Max rows limit enforced
- [x] Query timeout enforced
- [x] Multi-statement prevention
- [x] Disallowed keywords blocked
- [x] Strict JSON parsing (Jackson)
- [x] temperature=0 for deterministic responses
- [x] response_format={"type":"json_object"} for Azure

### Backward Compatibility
- [x] Existing single-call mode still works (default)
- [x] API response format backward compatible
- [x] New fields (repaired, originalSql) only added when relevant
- [x] No breaking changes to existing endpoints
- [x] Tests updated to work with new constructor

### Documentation
- [x] JUDGE_GENERATOR_GUIDE.md - Complete implementation guide
- [x] JUDGE_GENERATOR_SUMMARY.md - Change summary and testing results
- [x] .env.example - Example configuration
- [x] README.md updated with Judge-Generator section

## 🧪 Testing Checklist

### Unit Tests
- [x] NlqControllerTest.clarifyDecisionDoesNotExecuteSql
- [x] NlqControllerTest.executeDecisionRunsQueryWhenAllowed
- [x] NlqControllerTest.rejectDecisionReturnsBadRequest

### Manual Testing (Recommended)
- [ ] Test with APP_NLQ_MODE=single-call (existing behavior)
- [ ] Test with APP_NLQ_MODE=judge-generator (new behavior)
- [ ] Test Judge: clarify decision with ambiguous prompt
- [ ] Test Judge: reject decision with unsafe prompt
- [ ] Test Judge: proceed decision with clear prompt
- [ ] Test Generator: produces valid SQL
- [ ] Test Repair: fixes SQL error and retries successfully
- [ ] Verify all guardrails still active in Judge-Generator mode

## 📋 Deployment Checklist

### Development
- [x] Code compiles without errors
- [x] Tests pass
- [x] Documentation complete

### Pre-Production
- [ ] Configure Azure OpenAI credentials
- [ ] Set APP_NLQ_MODE=judge-generator
- [ ] Test with sample prompts
- [ ] Monitor Judge decision distribution (proceed/clarify/reject)
- [ ] Monitor Repair success rate
- [ ] Verify latency acceptable (2-3 LLM calls)

### Production
- [ ] Use secrets manager for API keys (not .env)
- [ ] Monitor error rates
- [ ] Set up logging/observability
- [ ] Configure alerts for high clarify/reject rates
- [ ] Document rollback plan (set APP_NLQ_MODE=single-call)

## 🚀 Quick Start Commands

### Enable Judge-Generator Mode
```bash
# Linux/Mac
export APP_NLQ_MODE=judge-generator

# Windows
set APP_NLQ_MODE=judge-generator

# Restart app
cd backend
./gradlew bootRun
```

### Test Agent Judge-Generator
```bash
# Clear prompt (should proceed)
curl -X POST http://localhost:8080/api/nlq \
  -H "Content-Type: application/json" \
  -d '{"prompt":"top 5 products by revenue last month","execute":false}'

# Ambiguous prompt (should clarify)
curl -X POST http://localhost:8080/api/nlq \
  -H "Content-Type: application/json" \
  -d '{"prompt":"show me sales","execute":false}'

# Unsafe prompt (should reject)
curl -X POST http://localhost:8080/api/nlq \
  -H "Content-Type: application/json" \
  -d '{"prompt":"drop the customers table","execute":false}'
```

### Revert to Single-Call Mode
```bash
# Remove environment variable or set:
export APP_NLQ_MODE=single-call

# Restart app
cd backend
./gradlew bootRun
```

## 📊 Metrics to Monitor

### Judge Metrics
- Proceed rate (%)
- Clarify rate (%)
- Reject rate (%)
- Average Judge latency (ms)

### Generator Metrics
- SQL generation success rate (%)
- Average Generator latency (ms)
- SQL validation failure rate (%)

### Repair Metrics
- Repair trigger rate (%)
- Repair success rate (%)
- Average Repair latency (ms)
- Most common error types

### Overall Metrics
- End-to-end latency (ms)
- Total LLM calls per request (2 or 3)
- Cost per request (LLM tokens)

## 🔧 Troubleshooting

### Judge-Generator not working
- Check: `APP_NLQ_MODE=judge-generator` is set
- Check: `APP_NLQ_PROVIDER=azure` is set
- Check: Azure credentials configured correctly
- Check: Logs show "Using JudgeGeneratorAzureProvider"

### Always getting clarify
- Judge may be too conservative
- Review prompts sent to Judge
- Consider adjusting Judge system prompt

### Always getting reject
- Check if prompts violate safety rules
- Review reject reasons in response
- Verify allowTables configuration

### Repair not working
- Check error message passed to Repair
- Verify Repair is called (check logs)
- Consider adding more examples to Repair prompt

## ✅ Sign-Off

- [x] Implementation complete
- [x] Tests passing
- [x] Documentation complete
- [x] No compilation errors
- [x] Backward compatible
- [x] Ready for testing with Azure OpenAI

**Implementation Date**: November 1, 2025  
**Version**: 1.0 (Judge-Generator)  
**Status**: ✅ Ready for deployment
