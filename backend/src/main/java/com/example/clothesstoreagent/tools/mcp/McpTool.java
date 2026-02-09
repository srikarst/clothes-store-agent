package com.example.clothesstoreagent.tools.mcp;

import com.example.clothesstoreagent.mcp.McpClient;
import com.example.clothesstoreagent.mcp.McpServerManager;
import com.example.clothesstoreagent.mcp.McpToolCallResult;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolExecutionResult;
import com.example.clothesstoreagent.tools.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class McpTool implements Tool {

    private final String serverId;
    private final String mcpToolName;
    private final ToolSpec spec;
    private final McpServerManager manager;
    private final ObjectMapper om;
    private final int maxChars;

    public McpTool(String serverId,
                   String mcpToolName,
                   ToolSpec spec,
                   McpServerManager manager,
                   ObjectMapper om,
                   int maxChars) {
        this.serverId = serverId;
        this.mcpToolName = mcpToolName;
        this.spec = spec;
        this.manager = manager;
        this.om = om != null ? om : new ObjectMapper();
        this.maxChars = Math.max(0, maxChars);
    }

    public String serverId() {
        return serverId;
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) {
        if (manager == null) {
            return ToolExecutionResult.fail("MCP is not available", Map.of("serverId", serverId));
        }
        McpClient client = manager.getClient(serverId);
        if (client == null) {
            return ToolExecutionResult.fail("MCP server not available: " + serverId, Map.of("serverId", serverId));
        }

        JsonNode argNode = om.valueToTree(args != null ? args : Map.of());
        McpToolCallResult r = client.callTool(mcpToolName, argNode);

        String content = truncate(r != null ? r.text() : "", maxChars);
        Map<String, Object> data = r != null && r.raw() != null ? r.raw() : Map.of();

        if (r != null && r.ok()) {
            return ToolExecutionResult.ok(content, data);
        }
        return ToolExecutionResult.fail(content.isBlank() ? "Tool failed" : content, data);
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return "";
        int max = Math.max(0, maxChars);
        if (max == 0) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }
}


