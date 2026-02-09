package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal MCP protocol client for one server.
 */
public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    private final String serverId;
    private final String protocolVersion;
    private final Duration timeout;
    private final McpTransport transport;
    private final ObjectMapper om;

    private final Object initLock = new Object();
    private volatile boolean initialized;

    public McpClient(String serverId,
                     String protocolVersion,
                     Duration timeout,
                     McpTransport transport,
                     ObjectMapper om) {
        this.serverId = serverId != null ? serverId : "mcp";
        this.protocolVersion = (protocolVersion == null || protocolVersion.isBlank()) ? "2024-11-05" : protocolVersion.trim();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(20);
        this.transport = transport;
        this.om = om != null ? om : new ObjectMapper();
    }

    public String serverId() {
        return serverId;
    }

    public void initialize() {
        if (initialized) return;
        synchronized (initLock) {
            if (initialized) return;
            try {
                if (transport == null) {
                    throw new McpClientException("No transport configured");
                }
                transport.start();

                ObjectNode params = om.createObjectNode();
                params.put("protocolVersion", protocolVersion);
                params.set("capabilities", om.createObjectNode());
                ObjectNode clientInfo = om.createObjectNode();
                clientInfo.put("name", "clothes-store-agent");
                clientInfo.put("version", "phase2");
                params.set("clientInfo", clientInfo);

                transport.request("initialize", params, timeout);

                // Notification required by many servers after initialize.
                try {
                    transport.notify("notifications/initialized", om.createObjectNode());
                    transport.notify("initialized", om.createObjectNode());
                } catch (Exception ignore) {
                    // Best-effort notification.
                }

                initialized = true;
            } catch (Exception e) {
                initialized = false;
                throw new McpClientException("Failed to initialize MCP serverId=" + serverId + ": " + e.getMessage(), e);
            }
        }
    }

    public List<McpToolDefinition> listTools() {
        ensureInitialized();
        JsonNode result = transport.request("tools/list", om.createObjectNode(), timeout);

        JsonNode arr = result != null ? result.get("tools") : null;
        if (arr == null || !arr.isArray()) {
            return List.of();
        }

        List<McpToolDefinition> out = new ArrayList<>();
        for (JsonNode t : arr) {
            if (t == null || t.isNull()) continue;
            String name = t.has("name") ? t.get("name").asText() : null;
            if (name == null || name.isBlank()) continue;
            String desc = t.has("description") ? t.get("description").asText() : "";
            JsonNode schema = t.get("inputSchema");
            out.add(new McpToolDefinition(name, desc, schema != null ? schema : om.nullNode()));
        }
        return out;
    }

    public McpToolCallResult callTool(String toolName, JsonNode arguments) {
        try {
            ensureInitialized();

            ObjectNode params = om.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments != null && !arguments.isNull() ? arguments : om.createObjectNode());

            JsonNode result = transport.request("tools/call", params, timeout);

            boolean isError = result != null && result.has("isError") && result.get("isError").asBoolean(false);
            String text = extractText(result);
            Map<String, Object> raw = asMapSafe(result);

            return new McpToolCallResult(!isError, text, raw);
        } catch (McpRpcException re) {
            return new McpToolCallResult(false, re.getMessage(), Map.of(
                    "type", "jsonrpc_error",
                    "method", re.method(),
                    "code", re.code(),
                    "message", re.rpcMessage()
            ));
        } catch (Exception e) {
            initialized = false; // allow re-init on next call after transport issues
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            return new McpToolCallResult(false, msg, Map.of(
                    "type", "exception",
                    "message", msg
            ));
        }
    }

    private void ensureInitialized() {
        try {
            initialize();
        } catch (McpClientException e) {
            // Bubble up with a clean message, but don't crash the app (caller should handle).
            log.warn("MCP initialize failed serverId={} error={}", serverId, e.getMessage());
            throw e;
        }
    }

    private String extractText(JsonNode result) {
        if (result == null || result.isNull()) return "";

        StringBuilder sb = new StringBuilder();

        JsonNode content = result.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode b : content) {
                if (b == null || b.isNull()) continue;
                String type = b.has("type") ? b.get("type").asText() : "";
                if ("text".equalsIgnoreCase(type) && b.has("text")) {
                    String t = b.get("text").asText();
                    if (t == null || t.isBlank()) continue;
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(t);
                }
            }
        } else if (content != null && content.isTextual()) {
            sb.append(content.asText());
        }

        if (sb.isEmpty() && result.has("text")) {
            sb.append(result.get("text").asText(""));
        }

        return sb.toString().trim();
    }

    private Map<String, Object> asMapSafe(JsonNode v) {
        if (v == null || v.isNull()) return Map.of();
        try {
            return om.convertValue(v, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", v.toString());
        }
    }
}


