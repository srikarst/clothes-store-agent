package com.example.clothesstoreagent.mcp;

import java.util.Map;

public record McpToolCallResult(
        boolean ok,
        String text,
        Map<String, Object> raw
) {}


