package com.example.clothesstoreagent.tools;

import com.example.clothesstoreagent.domains.Domain;

public record ToolContext(
        String conversationId,
        Domain domain,
        Integer maxRows,
        Integer timeoutSeconds,
        String userId
) {}




