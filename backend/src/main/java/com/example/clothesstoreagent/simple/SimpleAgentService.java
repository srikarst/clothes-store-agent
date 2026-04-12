package com.example.clothesstoreagent.simple;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SimpleAgentService {

    private final QdrantRagStore ragStore;
    private final SimpleMcpClient mcpClient;
    private final SimpleModelClient modelClient;

    public SimpleAgentService(QdrantRagStore ragStore, SimpleMcpClient mcpClient, SimpleModelClient modelClient) {
        this.ragStore = ragStore;
        this.mcpClient = mcpClient;
        this.modelClient = modelClient;
    }

    public ChatResponse chat(String message) {
        String cleanMessage = message == null ? "" : message.trim();
        String skill = detectSkill(cleanMessage);
        List<String> ragContext = ragStore.retrieve(cleanMessage, 2);
        SimpleMcpClient.ExecutionResult execution = mcpClient.executeSkill(skill, cleanMessage);

        String assistantMessage = modelClient.generateAssistantMessage(
                cleanMessage,
                skill,
                ragContext,
                execution.localTools(),
                execution.mcpCalls()
        );
        if (assistantMessage == null || assistantMessage.isBlank()) {
            assistantMessage = buildAssistantMessage(skill, cleanMessage, ragContext, execution);
        }
        return new ChatResponse(
                assistantMessage,
                skill,
                execution.route(),
                ragContext,
                execution.localTools(),
                execution.mcpCalls()
        );
    }

    private String detectSkill(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        for (SkillRule rule : SKILL_RULES) {
            if (rule.matches(text)) {
                return rule.skill();
            }
        }
        return "general_help";
    }

    private String buildAssistantMessage(String skill,
                                         String userMessage,
                                         List<String> ragContext,
                                         SimpleMcpClient.ExecutionResult execution) {
        StringBuilder reply = new StringBuilder();
        reply.append("Skill: ").append(skill).append(". ");
        reply.append("Route: ").append(execution.route()).append(". ");

        if (!execution.localTools().isEmpty()) {
            SimpleMcpClient.LocalToolResult local = execution.localTools().get(0);
            reply.append("Local tool ").append(local.toolName())
                    .append(" extracted: ").append(local.output()).append(". ");
        }

        if (!execution.mcpCalls().isEmpty()) {
            SimpleMcpClient.McpResult mcp = execution.mcpCalls().get(0);
            if (mcp.ok()) {
                reply.append("MCP ").append(mcp.serverId()).append("/").append(mcp.toolName())
                        .append(" returned: ").append(mcp.output()).append(". ");
            } else {
                reply.append("MCP ").append(mcp.serverId()).append("/").append(mcp.toolName())
                        .append(" failed");
                if (mcp.errorCode() != null && !mcp.errorCode().isBlank()) {
                    reply.append(" (").append(mcp.errorCode()).append(")");
                }
                reply.append(": ").append(mcp.output()).append(". ");
                reply.append("Please provide missing details so I can retry. ");
            }
        }

        if (!ragContext.isEmpty()) {
            reply.append("Relevant store context: ").append(ragContext.get(0));
        } else if ("general_help".equals(skill)) {
            reply.append("Ask about returns/refunds or delivery ETA/shipping options.");
        } else {
            reply.append("I could not find a matching store context for: ").append(userMessage);
        }

        return reply.toString().trim();
    }

    private static final List<SkillRule> SKILL_RULES = List.of(
            new SkillRule(
                    "post_purchase_support",
                    Set.of("return", "refund", "exchange", "replace", "damaged", "defect")
            ),
            new SkillRule(
                    "order_fulfillment_support",
                    Set.of("shipping", "delivery", "arrive", "eta", "track", "courier", "dispatch")
            )
    );

    private record SkillRule(String skill, Set<String> keywords) {
        boolean matches(String text) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record ChatResponse(
            String assistantMessage,
            String skill,
            String route,
            List<String> ragContext,
            List<SimpleMcpClient.LocalToolResult> localTools,
            List<SimpleMcpClient.McpResult> mcpCalls
    ) {
    }
}
