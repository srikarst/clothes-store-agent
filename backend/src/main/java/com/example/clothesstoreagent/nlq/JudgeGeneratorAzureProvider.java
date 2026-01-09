package com.example.clothesstoreagent.nlq;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.nlq.judge.GeneratorResponse;
import com.example.clothesstoreagent.nlq.judge.JudgeResponse;
import com.example.clothesstoreagent.nlq.judge.RepairResponse;
import com.example.clothesstoreagent.service.SchemaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Judge-Generator Provider: Judge → Generator (+Repair)
 * 
 * Step 1: Judge decides proceed/clarify/reject
 * Step 2: If proceed, Generator emits SQL
 * Step 3: If DB error, Repair corrects SQL (one retry)
 */
public class JudgeGeneratorAzureProvider implements NlqProvider {

    private static final Logger log = LoggerFactory.getLogger(JudgeGeneratorAzureProvider.class);

    private final AppProps props;
    private final SchemaService schema;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    public JudgeGeneratorAzureProvider(AppProps props, SchemaService schema) {
        this.props = props;
        this.schema = schema;
    }

    @Override
    public Decision compile(String prompt) {
        return compileWithHistory(prompt, List.of());
    }

    @Override
    public Decision compileWithHistory(String prompt, List<ChatTurn> history) {
        requireConfigured();

        // Step 1: Judge
        JudgeResponse judgeResp = callJudgeWithHistory(prompt, history);
        
        if (judgeResp.getDecision() == null || judgeResp.getDecision().isBlank()) {
            throw new IllegalStateException("Judge returned empty decision");
        }
        
        String decision = judgeResp.getDecision().trim().toLowerCase(Locale.ROOT);
        
        switch (decision) {
            case "clarify" -> {
                String question = judgeResp.getQuestion() != null ? judgeResp.getQuestion().trim() : "";
                if (question.isBlank()) {
                    question = "Could you please provide more details?";
                }
                List<String> missing = judgeResp.getMissing() != null ? judgeResp.getMissing() : List.of();
                log.info("Judge: clarify - missing={} question='{}'", missing, question);
                return Decision.clarify("judge_clarify", question, missing);
            }
            case "reject" -> {
                String message = judgeResp.getQuestion() != null ? judgeResp.getQuestion().trim() : "";
                if (message.isBlank()) {
                    message = "Sorry, I can't help with that request.";
                }
                log.info("Judge: reject - message='{}'", message);
                return Decision.reject("judge_reject", message);
            }
            case "proceed" -> {
                log.info("Judge: proceed - calling Generator");
                // Step 2: Generator
                GeneratorResponse genResp = callGeneratorWithHistory(prompt, history);
                
                if (genResp.getSql() == null || genResp.getSql().isBlank()) {
                    throw new IllegalStateException("Generator returned empty SQL");
                }
                
                String sql = genResp.getSql().trim();
                Map<String, Object> params = genResp.getParams() != null ? genResp.getParams() : Map.of();
                
                validateSql(sql);
                
                log.info("Generator: produced SQL (length={}) with {} params", sql.length(), params.size());
                return Decision.execute("judge_generator", sql, params);
            }
            default -> throw new IllegalStateException("Unexpected Judge decision: " + decision);
        }
    }

    /**
     * Step 1: Judge with history support
     */
    private JudgeResponse callJudgeWithHistory(String prompt, List<ChatTurn> history) {
        boolean useCompact = !history.isEmpty();
        String schemaContent = useCompact ? 
            schema.compactDigest(6) : 
            "Schema JSON:\n" + safeJson(schema.getSchema());
        
        String system = """
                You are a Judge agent for a Text-to-SQL system (Microsoft SQL Server).
                Your job is to analyze the user's natural language request and decide whether it can proceed to SQL generation.
                
                Respond with **strict JSON** matching exactly this schema (no extra keys):
                {
                    "decision": "proceed" | "clarify" | "reject",
                    "missing": string[],
                    "reasons": string[],
                    "question": string
                }
                
                Rules:
                1. If the request is clear, has sufficient context, and refers to tables/columns in the schema → decision="proceed"
                2. If the request is ambiguous or lacks required filters (like date ranges) → decision="clarify"
                   - Fill "missing" with what's missing (e.g., ["date range", "product category"])
                   - Fill "question" with a helpful clarifying question
                   - Fill "reasons" with why clarification is needed
                3. If the request is out of scope, asks for mutations (INSERT/UPDATE/DELETE), or violates safety → decision="reject"
                   - Fill "question" with a polite rejection message
                   - Fill "reasons" with why it's rejected
                4. Only use tables/columns from the provided schema. Never invent names.
                5. For revenue queries, always require completed orders (o.status = 'completed').
                """;
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        
        // Add conversation history
        for (ChatTurn turn : history) {
            String role = turn.role() == ChatTurn.Role.USER ? "user" : "assistant";
            messages.add(Map.of("role", role, "content", turn.content()));
        }
        
        // Add current user request with schema
        messages.add(Map.of("role", "user", "content", 
            schemaContent + "\n\nUser request:\n" + prompt + "\n\nRespond with JSON only."));
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));
        
        try {
            String responseContent = callAzure(body);
            JudgeResponse resp = om.readValue(responseContent, JudgeResponse.class);
            log.debug("Judge response: decision={} missing={} question='{}'", 
                resp.getDecision(), resp.getMissing(), resp.getQuestion());
            return resp;
        } catch (Exception e) {
            throw new IllegalStateException("Judge call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Step 2: Generator with history support
     */
    private GeneratorResponse callGeneratorWithHistory(String prompt, List<ChatTurn> history) {
        boolean useCompact = !history.isEmpty();
        String schemaContent = useCompact ? 
            schema.compactDigest(6) : 
            "Schema JSON:\n" + safeJson(schema.getSchema());
        
        String system = """
                You are a SQL Generator agent for Microsoft SQL Server.
                Your job is to produce a safe, executable SELECT query based on the user's request.
                
                Respond with **strict JSON** matching exactly this schema (no extra keys):
                {
                    "sql": string,
                    "params": object
                }
                
                Rules:
                - Use only provided schema (tables/columns/FKs). Never invent names.
                - Generate **ONE** SELECT statement in "sql" with optional named parameters in "params" (object).
                - Always filter completed orders: o.status = 'completed' when aggregating orders.
                - Revenue = SUM(oi.qty * oi.unit_price * (1 - oi.discount)).
                - Use UTC time helpers: SYSUTCDATETIME(). For "last month" use closed-open window
                  [start_of_last_month_utc, start_of_this_month_utc):
                  o.created_at >= DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-2))
                  AND o.created_at <  DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-1)).
                - SQL Server syntax only (DATEADD, EOMONTH, brackets ok). No multi-statement batches.
                - Never use INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE, GRANT, REVOKE.
                """;
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        
        // Add example
        messages.add(Map.of("role", "user", "content",
            "Example: top 3 products by revenue last month"));
        messages.add(Map.of("role", "assistant", "content",
            "{\"sql\":\"SELECT TOP 3 p.name, SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue " +
            "FROM dbo.orders o JOIN dbo.order_items oi ON oi.order_id = o.id " +
            "JOIN dbo.products p ON p.id = oi.product_id " +
            "WHERE o.status = 'completed' " +
            "AND o.created_at >= DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-2)) " +
            "AND o.created_at <  DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-1)) " +
            "GROUP BY p.name ORDER BY revenue DESC\",\"params\":{}}"));
        
        // Add conversation history
        for (ChatTurn turn : history) {
            String role = turn.role() == ChatTurn.Role.USER ? "user" : "assistant";
            messages.add(Map.of("role", role, "content", turn.content()));
        }
        
        // Add current user request with schema
        messages.add(Map.of("role", "user", "content",
            schemaContent + "\n\nUser request:\n" + prompt + "\n\nRespond with JSON only."));
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));
        
        try {
            String responseContent = callAzure(body);
            GeneratorResponse resp = om.readValue(responseContent, GeneratorResponse.class);
            log.debug("Generator response: sql length={} params={}", 
                resp.getSql() != null ? resp.getSql().length() : 0, 
                resp.getParams() != null ? resp.getParams().keySet() : "none");
            return resp;
        } catch (Exception e) {
            throw new IllegalStateException("Generator call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Step 3 (on DB error): Repair produces corrected SQL
     */
    public RepairResponse callRepair(String originalPrompt, String failedSql, String errorMessage) {
        Map<String, Object> ctx = schema.getSchema();
        
        String system = """
                You are a SQL Repair agent for Microsoft SQL Server.
                Your job is to fix a SQL query that failed during execution.
                
                Respond with **strict JSON** matching exactly this schema (no extra keys):
                {
                    "sql": string,
                    "params": object
                }
                
                Rules:
                - Analyze the error message and the failed SQL.
                - Produce a corrected SELECT query that fixes the issue.
                - Use only provided schema (tables/columns/FKs). Never invent names.
                - Common fixes: wrong column names, missing JOINs, invalid syntax, wrong aggregate functions.
                - Always filter completed orders: o.status = 'completed' when aggregating orders.
                - Revenue = SUM(oi.qty * oi.unit_price * (1 - oi.discount)).
                - SQL Server syntax only (DATEADD, EOMONTH). No multi-statement batches.
                - Never use INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE, GRANT, REVOKE.
                """;
        
        String userContent = String.format(
            "Schema JSON:\n%s\n\nOriginal user request:\n%s\n\nFailed SQL:\n%s\n\nError message:\n%s\n\nRespond with corrected SQL in JSON only.",
            safeJson(ctx), originalPrompt, failedSql, errorMessage
        );
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));
        
        try {
            String responseContent = callAzure(body);
            RepairResponse resp = om.readValue(responseContent, RepairResponse.class);
            log.info("Repair response: sql length={} params={}", 
                resp.getSql() != null ? resp.getSql().length() : 0, 
                resp.getParams() != null ? resp.getParams().keySet() : "none");
            return resp;
        } catch (Exception e) {
            throw new IllegalStateException("Repair call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Common method to call Azure OpenAI API
     */
    private String callAzure(Map<String, Object> body) throws Exception {
        String url = props.getAzureOpenaiEndpoint().replaceAll("/+$", "") +
            "/openai/deployments/" + props.getAzureOpenaiDeployment() +
            "/chat/completions?api-version=" + props.getAzureOpenaiApiVersion();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("api-key", props.getAzureOpenaiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Azure call failed: HTTP " + resp.statusCode() + " - " + resp.body());
        }

        Map<?, ?> json = om.readValue(resp.body(), Map.class);
        List<?> choices = (List<?>) json.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices from Azure.");
        }
        Map<?, ?> choice0 = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice0.get("message");
        return String.valueOf(message.get("content"));
    }

    private void requireConfigured() {
        if (isBlank(props.getAzureOpenaiEndpoint()) ||
                isBlank(props.getAzureOpenaiApiKey()) ||
                isBlank(props.getAzureOpenaiDeployment())) {
            throw new IllegalStateException("Azure OpenAI not configured. Set app.azureOpenaiEndpoint, " +
                    "app.azureOpenaiApiKey, app.azureOpenaiDeployment (and apiVersion).");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safeJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void validateSql(String sql) {
        String lowerSql = sql.toLowerCase(Locale.ROOT).trim();
        if (!lowerSql.startsWith("select") && !lowerSql.startsWith("with")) {
            throw new IllegalStateException("Generator must return a SELECT statement.");
        }
    }
}
