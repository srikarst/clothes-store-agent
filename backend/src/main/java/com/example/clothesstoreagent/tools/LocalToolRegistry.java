package com.example.clothesstoreagent.tools;

import com.example.clothesstoreagent.domains.Domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-process (local) tool registry.
 */
public class LocalToolRegistry implements ToolRegistry {

    private final Map<String, Tool> toolsByName;

    public LocalToolRegistry(List<Tool> tools) {
        Map<String, Tool> byName = new LinkedHashMap<>();
        if (tools != null) {
            for (Tool t : tools) {
                if (t == null || t.spec() == null || t.spec().name() == null) continue;
                byName.put(normalize(t.spec().name()), t);
            }
        }
        this.toolsByName = Map.copyOf(byName);
    }

    @Override
    public List<ToolSpec> listTools(Domain domain, ToolContext ctx) {
        if (domain == null || domain != Domain.ANALYTICS_SQL) {
            return List.of();
        }
        return toolsByName.values().stream().map(Tool::spec).toList();
    }

    @Override
    public Tool getToolByName(String name) {
        if (name == null) return null;
        return toolsByName.get(normalize(name));
    }

    private static String normalize(String n) {
        return n.trim().toLowerCase(Locale.ROOT);
    }
}


