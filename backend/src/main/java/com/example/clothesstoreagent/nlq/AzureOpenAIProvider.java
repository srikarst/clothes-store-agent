package com.example.clothesstoreagent.nlq;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.service.SchemaService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
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
    public Plan compile(String prompt) {
        requireConfigured();

        Map<String, Object> ctx = schema.getSchema();

        String system = """
                You are a Text-to-SQL assistant for **Microsoft SQL Server**.
                Return **ONE** SELECT statement only. Output **strict JSON**: {"sql":"...","params":{}}.
                Constraints:
                - Use only the provided schema (tables/columns/FKs). Do not invent names.
                - Always filter completed orders: o.status = 'completed' when aggregating orders.
                - Revenue = SUM(oi.qty * oi.unit_price * (1 - oi.discount)).
                - Use UTC time functions: SYSUTCDATETIME(). For "last month" use a closed-open window
                  [start_of_last_month_utc, start_of_this_month_utc):
                  o.created_at >= DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-2))
                  AND o.created_at <  DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-1))
                - SQL Server syntax only (DATEADD, EOMONTH, brackets ok).
                """;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", "Example: top 3 products by revenue last month"),
                Map.of("role", "assistant", "content",
                        "{\"sql\":\"SELECT TOP 3 p.name, SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue " +
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
            String sql = String.valueOf(out.get("sql"));

            Map<String, Object> params = java.util.Collections.emptyMap();
            Object paramsRaw = out.get("params");
            if (paramsRaw instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) paramsRaw;
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                params = copy;
            }

            if (sql == null || !sql.trim().toLowerCase().startsWith("select")) {
                throw new IllegalStateException("Model did not return a SELECT.");
            }
            return new Plan("ai_azure", sql, params);

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
}
