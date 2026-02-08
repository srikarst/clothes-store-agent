package com.example.clothesstoreagent.chat;

import java.util.Map;

public record ToolCall(
        String name,
        Map<String, Object> args
) {}




