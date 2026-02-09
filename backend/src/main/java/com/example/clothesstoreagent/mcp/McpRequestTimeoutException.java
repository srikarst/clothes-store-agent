package com.example.clothesstoreagent.mcp;

/**
 * Request timed out waiting for MCP server response.
 */
public class McpRequestTimeoutException extends McpTransportException {
    public McpRequestTimeoutException(String message) {
        super(message);
    }
}


