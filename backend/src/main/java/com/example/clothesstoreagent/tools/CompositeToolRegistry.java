package com.example.clothesstoreagent.tools;

import com.example.clothesstoreagent.domains.Domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tool registry that composes multiple registries.
 */
public class CompositeToolRegistry implements ToolRegistry {

    private final List<ToolRegistry> registries;

    public CompositeToolRegistry(List<ToolRegistry> registries) {
        this.registries = registries != null ? List.copyOf(registries) : List.of();
    }

    @Override
    public List<ToolSpec> listTools(Domain domain, ToolContext ctx) {
        if (registries.isEmpty()) return List.of();

        Map<String, ToolSpec> out = new LinkedHashMap<>();
        for (ToolRegistry r : registries) {
            if (r == null) continue;
            List<ToolSpec> specs = r.listTools(domain, ctx);
            if (specs == null || specs.isEmpty()) continue;
            for (ToolSpec s : specs) {
                if (s == null || s.name() == null) continue;
                out.putIfAbsent(normalize(s.name()), s);
            }
        }
        return new ArrayList<>(out.values());
    }

    @Override
    public Tool getToolByName(String name) {
        if (name == null || registries.isEmpty()) return null;
        for (ToolRegistry r : registries) {
            if (r == null) continue;
            Tool t = r.getToolByName(name);
            if (t != null) return t;
        }
        return null;
    }

    private static String normalize(String n) {
        return n.trim().toLowerCase(Locale.ROOT);
    }
}


