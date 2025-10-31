package com.example.clothesstoreagent.nlq;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.service.SchemaService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AzureOpenAIProvider implements NlqProvider {

    private final AppProps props;
    private final SchemaService schema;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    public AzureOpenAIProvider(AppProps props, SchemaService schema) {
        this.props = props;
        this.schema = schema;
    }

    @Override
    public NlqProvider.Decision compile(String prompt) {
        requireConfigured();

        Map<String, Object> ctx = schema.getSchema();

                String system = """
                                You are a Text-to-SQL assistant for **Microsoft SQL Server**.
                                Respond with **strict JSON** matching exactly this schema (no extra keys):
                                {
                                    "decision": "execute" | "clarify" | "reject",
                                    "question": string,
                                    "missing": string[],
                                    "sql": string,
                                    "params": object
                                }
                                Rules:
                                - Use only provided schema (tables/columns/FKs). Never invent names.
                                - When unsure or the user request lacks required filters, set decision="clarify" and fill
                                    "question" with a clear follow-up. Leave "sql" empty and list missing concepts in "missing".
                                - When the request cannot be fulfilled (out of scope, unsafe, etc.), set decision="reject" and
                                    place the polite explanation in "question".
                                - Only when you have a safe, executable query set decision="execute" and produce **ONE** SELECT
                                    statement in "sql" with optional named parameters in "params" (object).
                                - Always filter completed orders: o.status = 'completed' when aggregating orders.
                                - Revenue = SUM(oi.qty * oi.unit_price * (1 - oi.discount)).
                                - Use UTC time helpers: SYSUTCDATETIME(). For "last month" use closed-open window
                                    [start_of_last_month_utc, start_of_this_month_utc):
                                    o.created_at >= DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-2))
                                    AND o.created_at <  DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-1)).
                                - SQL Server syntax only (DATEADD, EOMONTH, brackets ok). No multi-statement batches.
                                """;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", "Example: top 3 products by revenue last month"),
        Map.of("role", "assistant", "content",
            "{\"decision\":\"execute\",\"question\":\"\",\"missing\":[]," +
                "\"sql\":\"SELECT TOP 3 p.name, SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue " +
                "FROM dbo.orders o JOIN dbo.order_items oi ON oi.order_id = o.id " +
                "JOIN dbo.products p ON p.id = oi.product_id " +
                "WHERE o.status = 'completed' " +
                "AND o.created_at >= DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-2)) " +
                "AND o.created_at <  DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-1)) " +
                "GROUP BY p.name ORDER BY revenue DESC\",\"params\":{}}"),
                Map.of("role", "user", "content",
                        "Schema JSON:\n" + safeJson(ctx) + "\n\nUser request:\n" + prompt
                                + "\n\nReturn ONLY strict JSON.")));
        body.put("temperature", 0);
    body.put("response_format", Map.of("type", "json_object"));

    String url = props.getAzureOpenaiEndpoint().replaceAll("/+$", "") +
        "/openai/deployments/" + props.getAzureOpenaiDeployment() +
        "/chat/completions?api-version=" + props.getAzureOpenaiApiVersion();

        try {
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
            String content = String.valueOf(message.get("content"));

            Map<?, ?> out = om.readValue(content, Map.class);
            return parseDecision(out);

        } catch (Exception e) {
            throw new IllegalStateException("AzureOpenAIProvider error: " + e.getMessage(), e);
        }
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

    private NlqProvider.Decision parseDecision(Map<?, ?> raw) {
        if (raw == null) {
            throw new IllegalStateException("Model returned empty payload.");
        }

        Object decisionVal = raw.get("decision");
        if (!(decisionVal instanceof String decisionStr)) {
            throw new IllegalStateException("Missing decision key in model response.");
        }
        String normalized = decisionStr.trim().toLowerCase(Locale.ROOT);

        NlqProvider.DecisionType type = switch (normalized) {
            case "execute" -> NlqProvider.DecisionType.EXECUTE;
            case "clarify" -> NlqProvider.DecisionType.CLARIFY;
            case "reject" -> NlqProvider.DecisionType.REJECT;
            default -> throw new IllegalStateException("Unsupported decision: " + decisionStr);
        };

        String question = null;
        Object questionVal = raw.get("question");
        if (questionVal instanceof String q && !q.isBlank()) {
            question = q.trim();
        }

        List<String> missing = new ArrayList<>();
        Object missingVal = raw.get("missing");
        if (missingVal instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof String s && !s.isBlank()) {
                    missing.add(s.trim());
                }
            }
        }

        String sql = null;
        Object sqlVal = raw.get("sql");
        if (sqlVal instanceof String s && !s.isBlank()) {
            sql = s.trim();
        }

        Map<String, Object> params = Map.of();
        Object paramsVal = raw.get("params");
        if (paramsVal instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            params = copy;
        }

        if (type == NlqProvider.DecisionType.EXECUTE) {
            if (sql == null) {
                throw new IllegalStateException("Decision=execute requires sql.");
            }
            String lowerSql = sql.toLowerCase(Locale.ROOT).trim();
            if (!lowerSql.startsWith("select") && !lowerSql.startsWith("with")) {
                throw new IllegalStateException("Decision=execute must return a SELECT statement.");
            }
        }

        if (type == NlqProvider.DecisionType.CLARIFY && question == null) {
            throw new IllegalStateException("Decision=clarify requires a question.");
        }

        if (type == NlqProvider.DecisionType.REJECT && question == null) {
            throw new IllegalStateException("Decision=reject requires a question message.");
        }

        return new NlqProvider.Decision("ai_azure", type, question, missing, sql, params);
    }
}
