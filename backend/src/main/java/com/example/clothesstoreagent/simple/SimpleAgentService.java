package com.example.clothesstoreagent.simple;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SimpleAgentService {

    private final SimpleRagStore ragStore;
    private final SimpleMcpClient mcpClient;
    private final SimpleModelClient modelClient;

    public SimpleAgentService(SimpleRagStore ragStore, SimpleMcpClient mcpClient, SimpleModelClient modelClient) {
        this.ragStore = ragStore;
        this.mcpClient = mcpClient;
        this.modelClient = modelClient;
    }

    public ChatResponse chat(String message) {
        String cleanMessage = message == null ? "" : message.trim();
        String intent = detectIntent(cleanMessage);
        List<String> ragContext = ragStore.retrieve(cleanMessage, 2);
        SimpleMcpClient.McpResult mcp = null;

        if ("mcp_math".equals(intent)) {
            mcp = mcpClient.runMathTool(cleanMessage);
        }

        String assistantMessage = modelClient.generateAssistantMessage(cleanMessage, intent, ragContext, mcp);
        if (assistantMessage == null || assistantMessage.isBlank()) {
            assistantMessage = buildAssistantMessage(intent, cleanMessage, ragContext, mcp);
        }
        return new ChatResponse(assistantMessage, intent, ragContext, mcp);
    }

    private String detectIntent(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.matches(".*\\d+.*") && (text.contains("add") || text.contains("sum")
                || text.contains("plus") || text.contains("calculate")
                || text.contains("multiply") || text.contains("times")
                || text.contains("product"))) {
            return "mcp_math";
        }
        if (containsAny(text, "return", "refund", "exchange", "replace")) {
            return "returns_policy";
        }
        if (containsAny(text, "shirt", "jean", "dress", "product", "catalog", "stock", "size", "price")) {
            return "product_help";
        }
        return "general_help";
    }

    private String buildAssistantMessage(String intent,
                                         String userMessage,
                                         List<String> ragContext,
                                         SimpleMcpClient.McpResult mcpResult) {
        StringBuilder reply = new StringBuilder();
        reply.append("Intent: ").append(intent).append(". ");

        if (mcpResult != null) {
            if (mcpResult.ok()) {
                reply.append("MCP tool ").append(mcpResult.toolName())
                        .append(" returned: ").append(mcpResult.output()).append(". ");
            } else {
                reply.append("MCP tool call failed: ").append(mcpResult.output()).append(". ");
            }
        }

        if (!ragContext.isEmpty()) {
            reply.append("Relevant store context: ").append(ragContext.get(0));
        } else if ("general_help".equals(intent)) {
            reply.append("Ask about products, returns, or simple math calculations.");
        } else {
            reply.append("I could not find a matching store context for: ").append(userMessage);
        }

        return reply.toString().trim();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public record ChatResponse(
            String assistantMessage,
            String intent,
            List<String> ragContext,
            SimpleMcpClient.McpResult mcp
    ) {
    }
}
