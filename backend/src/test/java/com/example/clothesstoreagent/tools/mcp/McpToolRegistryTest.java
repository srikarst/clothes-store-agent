package com.example.clothesstoreagent.tools.mcp;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.domains.Domain;
import com.example.clothesstoreagent.mcp.McpProps;
import com.example.clothesstoreagent.mcp.McpServerManager;
import com.example.clothesstoreagent.mcp.McpToolDefinition;
import com.example.clothesstoreagent.mcp.McpTransport;
import com.example.clothesstoreagent.mcp.McpTransportProvider;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolExecutionResult;
import com.example.clothesstoreagent.tools.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolRegistryTest {

    static class FakeTransport implements McpTransport {
        final ObjectMapper om = new ObjectMapper();

        @Override public void start() {}
        @Override public void stop() {}

        @Override
        public JsonNode request(String method, JsonNode params, Duration timeout) {
            if ("initialize".equals(method)) {
                return om.createObjectNode();
            }
            if ("tools/list".equals(method)) {
                ObjectNode echo = tool("echo", "Echo", om.createObjectNode().put("type", "object"));
                ObjectNode add = tool("math_add", "Add", om.createObjectNode().put("type", "object"));
                ObjectNode res = om.createObjectNode();
                res.set("tools", om.createArrayNode().add(echo).add(add));
                return res;
            }
            if ("tools/call".equals(method)) {
                String name = params != null && params.has("name") ? params.get("name").asText() : "";
                JsonNode args = params != null ? params.get("arguments") : null;
                ObjectNode res = om.createObjectNode();
                if ("math_add".equals(name)) {
                    double a = args != null && args.has("a") ? args.get("a").asDouble() : 0;
                    double b = args != null && args.has("b") ? args.get("b").asDouble() : 0;
                    res.put("isError", false);
                    res.set("content", om.createArrayNode().add(
                            om.createObjectNode().put("type", "text").put("text", String.valueOf(a + b))
                    ));
                    return res;
                }
                if ("echo".equals(name)) {
                    String text = args != null && args.has("text") ? args.get("text").asText("") : "";
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
            // ignore
        }

        private ObjectNode tool(String name, String desc, JsonNode schema) {
            ObjectNode t = om.createObjectNode();
            t.put("name", name);
            t.put("description", desc);
            t.set("inputSchema", schema);
            return t;
        }
    }

    static class FakeProvider implements McpTransportProvider {
        final FakeTransport transport = new FakeTransport();

        @Override public String transportId() { return "fake"; }

        @Override
        public McpTransport create(String serverId, McpProps.Server server, ObjectMapper om) {
            return transport;
        }
    }

    @Test
    void listsToolsOnlyForMcpDomainAndExecutesViaMcpClient() {
        McpProps props = new McpProps();
        props.setEnabled(true);
        props.setProtocolVersion("2024-11-05");
        McpProps.Server s = new McpProps.Server();
        s.setId("demo");
        s.setEnabled(true);
        s.setTransport("fake");
        s.setTimeoutSeconds(5);
        props.setServers(List.of(s));

        ObjectMapper om = new ObjectMapper();
        McpServerManager mgr = new McpServerManager(props, om, List.of(new FakeProvider()));

        AppProps appProps = new AppProps();
        appProps.setToolResultMaxChars(100);

        McpToolRegistry reg = new McpToolRegistry(mgr, appProps, om);

        assertThat(reg.listTools(Domain.ANALYTICS_SQL, new ToolContext("c", Domain.ANALYTICS_SQL, null, null, null))).isEmpty();

        List<ToolSpec> specs = reg.listTools(Domain.MCP_TOOLS, new ToolContext("c", Domain.MCP_TOOLS, null, null, null));
        assertThat(specs).extracting(ToolSpec::name)
                .contains("mcp.demo.echo", "mcp.demo.math_add");

        Tool t = reg.getToolByName("mcp.demo.math_add");
        assertThat(t).isNotNull();

        ToolExecutionResult r = t.execute(Map.of("a", 2, "b", 3), new ToolContext("c", Domain.MCP_TOOLS, null, null, null));
        assertThat(r.ok()).isTrue();
        assertThat(r.content()).contains("5");
    }
}


