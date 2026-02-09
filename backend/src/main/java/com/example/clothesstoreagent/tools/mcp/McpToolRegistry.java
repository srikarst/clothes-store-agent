package com.example.clothesstoreagent.tools.mcp;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.domains.Domain;
import com.example.clothesstoreagent.mcp.McpServerManager;
import com.example.clothesstoreagent.mcp.McpToolDefinition;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolRegistry;
import com.example.clothesstoreagent.tools.ToolSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool registry backed by MCP servers.
 */
public class McpToolRegistry implements ToolRegistry {

    private record Resolved(String fullName, String serverId, String mcpToolName, ToolSpec spec) {}

    private final McpServerManager manager;
    private final AppProps props;
    private final ObjectMapper om;

    private final ConcurrentHashMap<String, Resolved> resolvedByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Tool> toolsByName = new ConcurrentHashMap<>();

    public McpToolRegistry(McpServerManager manager, AppProps props, ObjectMapper om) {
        this.manager = manager;
        this.props = props;
        this.om = om != null ? om : new ObjectMapper();
    }

    @Override
    public List<ToolSpec> listTools(Domain domain, ToolContext ctx) {
        if (domain == null || domain != Domain.MCP_TOOLS) {
            return List.of();
        }
        if (manager == null) {
            return List.of();
        }

        Map<String, List<McpToolDefinition>> all = manager.getTools();
        if (all == null || all.isEmpty()) {
            return List.of();
        }

        Map<String, ToolSpec> specs = new LinkedHashMap<>();
        for (Map.Entry<String, List<McpToolDefinition>> e : all.entrySet()) {
            String serverId = e.getKey();
            if (serverId == null || serverId.isBlank()) continue;
            List<McpToolDefinition> defs = e.getValue();
            if (defs == null || defs.isEmpty()) continue;

            for (McpToolDefinition d : defs) {
                if (d == null || d.name() == null || d.name().isBlank()) continue;
                String fullName = "mcp." + serverId.trim() + "." + d.name().trim();
                ToolSpec s = new ToolSpec(
                        fullName,
                        buildDescription(serverId, d.description()),
                        asSchemaMap(d.inputSchema())
                );
                specs.putIfAbsent(normalize(fullName), s);
                resolvedByName.put(normalize(fullName), new Resolved(fullName, serverId.trim(), d.name().trim(), s));
            }
        }

        return new ArrayList<>(specs.values());
    }

    @Override
    public Tool getToolByName(String name) {
        if (name == null || name.isBlank()) return null;
        String norm = normalize(name);
        if (!norm.startsWith("mcp.")) return null;

        Tool existing = toolsByName.get(norm);
        if (existing != null) return existing;

        // Ensure we've seen tool definitions at least once.
        if (!resolvedByName.containsKey(norm)) {
            listTools(Domain.MCP_TOOLS, new ToolContext("n/a", Domain.MCP_TOOLS, null, null, null));
        }

        Resolved r = resolvedByName.get(norm);
        if (r == null) return null;

        Tool t = new McpTool(
                r.serverId(),
                r.mcpToolName(),
                r.spec(),
                manager,
                om,
                props != null ? props.getToolResultMaxChars() : 3000
        );
        toolsByName.putIfAbsent(norm, t);
        return toolsByName.get(norm);
    }

    private String buildDescription(String serverId, String desc) {
        String d = desc != null ? desc.trim() : "";
        if (d.isBlank()) d = "MCP tool";
        return "[mcp:" + serverId + "] " + d;
    }

    private Map<String, Object> asSchemaMap(JsonNode schema) {
        if (schema == null || schema.isNull()) return Map.of("type", "object");
        try {
            Map<String, Object> m = om.convertValue(schema, new TypeReference<Map<String, Object>>() {});
            return m != null ? m : Map.of("type", "object");
        } catch (Exception ignore) {
            return Map.of("type", "object");
        }
    }

    private static String normalize(String n) {
        return n.trim().toLowerCase(Locale.ROOT);
    }
}


