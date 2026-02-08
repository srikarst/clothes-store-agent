package com.example.clothesstoreagent.tools.impl;

import com.example.clothesstoreagent.nlq.NlqProvider;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolExecutionResult;
import com.example.clothesstoreagent.tools.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps NLQ compile into a plan tool (does not execute).
 */
public class NlqText2SqlPlanTool implements Tool {

    private final NlqProvider nlq;
    private final ObjectMapper om;

    public NlqText2SqlPlanTool(NlqProvider nlq, ObjectMapper om) {
        this.nlq = nlq;
        this.om = om;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "nlq.text2sql_plan",
                "Convert a natural-language analytics question into a SQL+params plan. Does not execute.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "prompt", Map.of("type", "string", "description", "The natural-language question to convert to SQL.")
                        ),
                        "required", new String[]{"prompt"},
                        "additionalProperties", false
                )
        );
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) {
        if (args == null || args.get("prompt") == null) {
            return ToolExecutionResult.fail("Missing required arg: prompt", Map.of());
        }
        String prompt = String.valueOf(args.get("prompt")).trim();
        if (prompt.isBlank()) {
            return ToolExecutionResult.fail("prompt cannot be empty", Map.of());
        }

        NlqProvider.Decision d = nlq.compile(prompt);

        Map<String, Object> data = new LinkedHashMap<>();
        if (d.intent != null) data.put("intent", d.intent);
        data.put("decision", d.decisionKey());

        if (d.decision == NlqProvider.DecisionType.EXECUTE) {
            data.put("sql", d.sql);
            data.put("params", d.params != null ? d.params : Map.of());
            String content = toJsonSafe(Map.of(
                    "decision", d.decisionKey(),
                    "intent", d.intent,
                    "sql", d.sql,
                    "params", d.params != null ? d.params : Map.of()
            ));
            return ToolExecutionResult.ok(content, data);
        }

        // CLARIFY or REJECT
        if (d.question != null && !d.question.isBlank()) {
            data.put("message", d.question);
        }
        if (d.missing != null && !d.missing.isEmpty()) {
            data.put("missing", List.copyOf(d.missing));
        }

        String msg = d.question != null && !d.question.isBlank()
                ? d.question
                : (d.decision == NlqProvider.DecisionType.CLARIFY ? "Need clarification" : "Rejected");
        return ToolExecutionResult.fail(msg, data);
    }

    private String toJsonSafe(Object v) {
        try {
            return om.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }
}




