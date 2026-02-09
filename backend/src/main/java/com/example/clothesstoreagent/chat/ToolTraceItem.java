package com.example.clothesstoreagent.chat;

import java.util.Map;

public record ToolTraceItem(
        int step,
        String tool,
        String provider,
        Map<String, Object> args,
        boolean ok,
        String resultPreview
) {}




