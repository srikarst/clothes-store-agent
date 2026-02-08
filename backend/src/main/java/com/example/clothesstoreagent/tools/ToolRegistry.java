package com.example.clothesstoreagent.tools;

import com.example.clothesstoreagent.domains.Domain;

import java.util.List;

public interface ToolRegistry {
    List<ToolSpec> listTools(Domain domain, ToolContext ctx);

    Tool getToolByName(String name);
}




