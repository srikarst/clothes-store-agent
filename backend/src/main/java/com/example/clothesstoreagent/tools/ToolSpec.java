package com.example.clothesstoreagent.tools;

import java.util.Map;

public record ToolSpec(
        String name,
        String description,
        Map<String, Object> jsonSchema
) {}




