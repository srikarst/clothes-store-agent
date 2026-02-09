package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC error returned by an MCP server.
 */
public class McpRpcException extends RuntimeException {
    private final String method;
    private final Integer code;
    private final String rpcMessage;
    private final JsonNode data;

    public McpRpcException(String method, Integer code, String rpcMessage, JsonNode data) {
        super(buildMessage(method, code, rpcMessage));
        this.method = method;
        this.code = code;
        this.rpcMessage = rpcMessage;
        this.data = data;
    }

    public String method() { return method; }
    public Integer code() { return code; }
    public String rpcMessage() { return rpcMessage; }
    public JsonNode data() { return data; }

    private static String buildMessage(String method, Integer code, String rpcMessage) {
        String m = rpcMessage != null ? rpcMessage : "JSON-RPC error";
        String c = code != null ? String.valueOf(code) : "?";
        return "MCP JSON-RPC error method=" + method + " code=" + c + " message=" + m;
    }
}


