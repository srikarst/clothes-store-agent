package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolDefinition(
        String name,
        String description,
        JsonNode inputSchema
) {}


