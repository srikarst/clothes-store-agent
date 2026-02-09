package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;

/**
 * Transport plugin boundary for MCP JSON-RPC communication.
 *
 * Implementations must be thread-safe.
 */
public interface McpTransport {
    void start();

    void stop();

    /**
     * Sends a JSON-RPC request and returns the JSON-RPC "result" node.
     * Implementations should throw {@link McpRpcException} when the response contains "error".
     */
    JsonNode request(String method, JsonNode params, Duration timeout);

    /**
     * Sends a JSON-RPC notification (no response expected).
     */
    void notify(String method, JsonNode params);
}


