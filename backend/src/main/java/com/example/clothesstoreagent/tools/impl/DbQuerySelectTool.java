package com.example.clothesstoreagent.tools.impl;

import com.example.clothesstoreagent.service.QueryService;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolExecutionResult;
import com.example.clothesstoreagent.tools.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class DbQuerySelectTool implements Tool {

    private final QueryService queryService;
    private final ObjectMapper om;

    public DbQuerySelectTool(QueryService queryService, ObjectMapper om) {
        this.queryService = queryService;
        this.om = om;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "db.query_select",
                "Execute a SELECT-only SQL query (read-only). Returns columns and rows.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "sql", Map.of("type", "string", "description", "SELECT-only SQL query text."),
                                "params", Map.of("type", "object", "description", "Named SQL parameters object."),
                                "maxRows", Map.of("type", "integer", "description", "Max rows to return (capped by server)."),
                                "timeoutSeconds", Map.of("type", "integer", "description", "Query timeout in seconds.")
                        ),
                        "required", new String[]{"sql"},
                        "additionalProperties", false
                )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) {
        if (args == null || args.get("sql") == null) {
            return ToolExecutionResult.fail("Missing required arg: sql", Map.of());
        }
        String sql = String.valueOf(args.get("sql"));
        Map<String, Object> params = Map.of();
        if (args.get("params") instanceof Map<?, ?> m) {
            Map<String, Object> p = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) continue;
                p.put(String.valueOf(e.getKey()), e.getValue());
            }
            params = p;
        }

        Integer maxRows = asInt(args.get("maxRows"));
        Integer timeoutSeconds = asInt(args.get("timeoutSeconds"));

        Map<String, Object> result = queryService.execute(sql, params, maxRows, timeoutSeconds);
        boolean ok = !result.containsKey("error");
        String content = toJsonSafe(result);
        if (ok) {
            return ToolExecutionResult.ok(content, result);
        }
        String message = String.valueOf(result.getOrDefault("message", "Query failed"));
        return ToolExecutionResult.fail(message, result);
    }

    private Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String toJsonSafe(Object v) {
        try {
            return om.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }
}




