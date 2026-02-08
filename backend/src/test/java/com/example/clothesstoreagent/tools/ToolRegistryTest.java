package com.example.clothesstoreagent.tools;

import com.example.clothesstoreagent.domains.Domain;
import com.example.clothesstoreagent.tools.impl.DbSchemaCompactTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    void returnsNoToolsForGeneral() {
        Tool dummy = new Tool() {
            @Override public ToolSpec spec() { return new ToolSpec("x", "d", Map.of()); }
            @Override public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) { return ToolExecutionResult.ok("ok", Map.of()); }
        };
        ToolRegistry reg = new DefaultToolRegistry(List.of(dummy));
        assertThat(reg.listTools(Domain.GENERAL, new ToolContext("c", Domain.GENERAL, null, null, null))).isEmpty();
    }

    @Test
    void canLookupToolByNameCaseInsensitively() {
        Tool dummy = new Tool() {
            @Override public ToolSpec spec() { return new ToolSpec("db.schema_compact", "d", Map.of()); }
            @Override public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) { return ToolExecutionResult.ok("ok", Map.of()); }
        };
        ToolRegistry reg = new DefaultToolRegistry(List.of(dummy));
        assertThat(reg.getToolByName("DB.SCHEMA_COMPACT")).isNotNull();
    }
}



