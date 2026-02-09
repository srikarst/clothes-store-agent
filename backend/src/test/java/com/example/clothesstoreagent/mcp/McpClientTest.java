package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpClientTest {

    static class FakeMcpTransport implements McpTransport {
        final ObjectMapper om = new ObjectMapper();
        final List<String> requests = new ArrayList<>();
        final List<String> notifications = new ArrayList<>();
        boolean started = false;

        @Override public void start() { started = true; }
        @Override public void stop() { started = false; }

        @Override
        public JsonNode request(String method, JsonNode params, Duration timeout) {
            requests.add(method);
            if ("initialize".equals(method)) {
                return om.createObjectNode();
            }
            if ("tools/list".equals(method)) {
                ObjectNode tool = om.createObjectNode();
                tool.put("name", "echo");
                tool.put("description", "Echo text");
                tool.set("inputSchema", om.createObjectNode().put("type", "object"));

                ObjectNode res = om.createObjectNode();
                res.set("tools", om.createArrayNode().add(tool));
                return res;
            }
            if ("tools/call".equals(method)) {
                String name = params != null && params.has("name") ? params.get("name").asText() : "";
                ObjectNode res = om.createObjectNode();
                if ("echo".equals(name)) {
                    String text = "";
                    if (params != null && params.has("arguments") && params.get("arguments").has("text")) {
                        text = params.get("arguments").get("text").asText("");
                    }
                    res.put("isError", false);
                    res.set("content", om.createArrayNode().add(
                            om.createObjectNode().put("type", "text").put("text", text)
                    ));
                    return res;
                }
                res.put("isError", true);
                res.set("content", om.createArrayNode().add(
                        om.createObjectNode().put("type", "text").put("text", "Unknown tool")
                ));
                return res;
            }
            throw new IllegalArgumentException("Unexpected method: " + method);
        }

        @Override
        public void notify(String method, JsonNode params) {
            notifications.add(method);
        }
    }

    @Test
    void initializeListAndCallToolWorks() {
        FakeMcpTransport t = new FakeMcpTransport();
        McpClient c = new McpClient("demo", "2024-11-05", Duration.ofSeconds(2), t, new ObjectMapper());

        List<McpToolDefinition> tools = c.listTools();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("echo");

        McpToolCallResult r = c.callTool("echo", new ObjectMapper().createObjectNode().put("text", "hi"));
        assertThat(r.ok()).isTrue();
        assertThat(r.text()).isEqualTo("hi");

        assertThat(t.requests).contains("initialize", "tools/list", "tools/call");
        assertThat(t.notifications).anyMatch(m -> m.contains("initialized"));
    }
}


