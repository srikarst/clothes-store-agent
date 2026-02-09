package com.example.clothesstoreagent.mcp;

/**
 * Transport-level failure (process IO, timeout, protocol framing).
 */
public class McpTransportException extends RuntimeException {
    public McpTransportException(String message) { super(message); }
    public McpTransportException(String message, Throwable cause) { super(message, cause); }
}


