package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class StdioMcpTransportProvider implements McpTransportProvider {
    @Override
    public String transportId() {
        return "stdio";
    }

    @Override
    public McpTransport create(String serverId, McpProps.Server server, ObjectMapper om) {
        return new StdioMcpTransport(
                serverId,
                server != null ? server.getCommand() : null,
                server != null ? server.getArgs() : null,
                server != null ? server.getEnv() : null,
                om
        );
    }
}


