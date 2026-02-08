package com.example.clothesstoreagent.tools.impl;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.service.SchemaService;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolExecutionResult;
import com.example.clothesstoreagent.tools.ToolSpec;

import java.util.Map;

public class DbSchemaCompactTool implements Tool {

    private final SchemaService schemaService;
    private final AppProps props;

    public DbSchemaCompactTool(SchemaService schemaService, AppProps props) {
        this.schemaService = schemaService;
        this.props = props;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "db.schema_compact",
                "Return a compact database schema digest (tables, columns, and a few sample values).",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false
                )
        );
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) {
        int samples = props.getSchemaSamplesPerColumn();
        if (samples <= 0) {
            samples = 6;
        }
        String digest = schemaService.compactDigest(samples);
        return ToolExecutionResult.ok(digest, Map.of("samplesPerColumn", samples));
    }
}




