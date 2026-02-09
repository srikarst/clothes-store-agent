package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Transport plugin factory.
 */
public interface McpTransportProvider {
    /**
     * Transport id (e.g., "stdio", "http", "ws").
     */
    String transportId();

    McpTransport create(String serverId, McpProps.Server server, ObjectMapper om);
}


