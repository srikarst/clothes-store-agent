package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

@Component
public class SimpleMcpClient {

    private static final String POLICY_MCP_SERVER = "policy-mcp-server";
    private static final String FULFILLMENT_MCP_SERVER = "fulfillment-mcp-server";
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d{1,3})\\s*(?:day|days)\\b");
    private static final Pattern URGENCY_PATTERN = Pattern.compile("(?:in|within)\\s*(\\d{1,2})\\s*(?:day|days)\\b");
    private static final int INITIALIZE_REQUEST_ID = 1;
    private static final int TOOL_CALL_REQUEST_ID = 2;

    private final ObjectMapper objectMapper;

    @Value("${APP_MCP_NODE_COMMAND:node}")
    private String nodeCommand = "node";

    @Value("${APP_MCP_POLICY_SERVER_SCRIPT:mcp-servers/policy/server.js}")
    private String policyServerScript = "mcp-servers/policy/server.js";

    @Value("${APP_MCP_FULFILLMENT_SERVER_SCRIPT:mcp-servers/fulfillment/server.js}")
    private String fulfillmentServerScript = "mcp-servers/fulfillment/server.js";

    @Value("${APP_MCP_TIMEOUT_MS:7000}")
    private long mcpTimeoutMs = 7000;

    public SimpleMcpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExecutionResult executeSkill(String skill, String userMessage) {
        String normalizedSkill = skill == null ? "" : skill.trim();
        String input = userMessage == null ? "" : userMessage.trim();

        if ("post_purchase_support".equals(normalizedSkill)) {
            return runPostPurchaseSupport(input);
        }
        if ("order_fulfillment_support".equals(normalizedSkill)) {
            return runOrderFulfillmentSupport(input);
        }
        return ExecutionResult.empty();
    }

    private ExecutionResult runPostPurchaseSupport(String userMessage) {
        ReturnFacts facts = extractReturnFacts(userMessage);
        LocalToolResult localTool = new LocalToolResult(
                "extract_return_context",
                userMessage,
                facts.toSummary(),
                true
        );

        boolean timelineQuery = containsAny(
                userMessage,
                "refund timeline",
                "refund take",
                "how long refund",
                "credit back",
                "money back"
        );
        String toolName = timelineQuery ? "estimate_refund_timeline" : "check_return_eligibility";
        McpResult mcpCall = timelineQuery
                ? estimateRefundTimeline(userMessage, facts)
                : checkReturnEligibility(userMessage, facts);

        return new ExecutionResult(
                POLICY_MCP_SERVER + "/" + toolName,
                List.of(localTool),
                List.of(mcpCall)
        );
    }

    private ExecutionResult runOrderFulfillmentSupport(String userMessage) {
        DeliveryFacts facts = extractDeliveryFacts(userMessage);
        LocalToolResult localTool = new LocalToolResult(
                "extract_delivery_context",
                userMessage,
                facts.toSummary(),
                true
        );

        boolean recommendationQuery = containsAny(
                userMessage,
                "recommend",
                "which shipping option",
                "best shipping",
                "cheapest",
                "budget",
                "fastest",
                "option"
        );
        String toolName = recommendationQuery ? "recommend_shipping_option" : "estimate_delivery_eta";
        McpResult mcpCall = recommendationQuery
                ? recommendShippingOption(userMessage, facts)
                : estimateDeliveryEta(userMessage, facts);

        return new ExecutionResult(
                FULFILLMENT_MCP_SERVER + "/" + toolName,
                List.of(localTool),
                List.of(mcpCall)
        );
    }

    private ReturnFacts extractReturnFacts(String userMessage) {
        String normalized = normalize(userMessage);
        Integer days = extractInt(DAYS_PATTERN, normalized);
        boolean used = containsAnyWholeWord(normalized, "used", "worn");
        boolean unused = containsAny(normalized, "unused", "new");
        Boolean hasTags = null;
        if (containsAny(normalized, "without tags", "no tags", "tag removed", "tags removed")) {
            hasTags = false;
        } else if (containsAny(normalized, "with tags", "tags attached", "has tags")) {
            hasTags = true;
        }
        return new ReturnFacts(days, used, unused, hasTags);
    }

    private DeliveryFacts extractDeliveryFacts(String userMessage) {
        String normalized = normalize(userMessage);
        String shippingSpeed = containsAny(normalized, "express", "priority", "fast shipping") ? "express" : "standard";
        String destination = containsAny(normalized, "international", "overseas", "abroad") ? "international" : "domestic";
        Integer urgencyDays = extractInt(URGENCY_PATTERN, normalized);
        if (urgencyDays == null && containsAny(normalized, "tomorrow", "next day")) {
            urgencyDays = 1;
        }
        boolean budgetPriority = containsAny(normalized, "budget", "cheapest", "low cost", "economy");
        return new DeliveryFacts(destination, shippingSpeed, urgencyDays, budgetPriority);
    }

    private McpResult checkReturnEligibility(String userMessage, ReturnFacts facts) {
        String toolName = "check_return_eligibility";
        long start = System.nanoTime();
        if (facts.daysSinceDelivery() == null) {
            return failedResult(
                    POLICY_MCP_SERVER,
                    toolName,
                    userMessage,
                    "Missing required input: include days since delivery (for example, \"12 days ago\").",
                    "VALIDATION_ERROR",
                    start
            );
        }

        String condition = facts.used() ? "used" : facts.unused() ? "unused" : "unknown";
        boolean hasTags = facts.hasTags() == null || facts.hasTags();
        // If tags are unknown, force a conservative review path instead of a false "eligible=true".
        if ("unused".equals(condition) && facts.hasTags() == null) {
            condition = "unknown";
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("daysSinceDelivery", facts.daysSinceDelivery());
        arguments.put("itemCondition", condition);
        arguments.put("hasTags", hasTags);
        return invokeTool(POLICY_MCP_SERVER, policyServerScript, toolName, userMessage, arguments, start);
    }

    private McpResult estimateRefundTimeline(String userMessage, ReturnFacts facts) {
        String toolName = "estimate_refund_timeline";
        long start = System.nanoTime();

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("daysSinceDelivery", facts.daysSinceDelivery() == null ? 0 : facts.daysSinceDelivery());
        if (containsAny(userMessage, "wallet")) {
            arguments.put("paymentMethod", "wallet");
        } else if (containsAny(userMessage, "card", "credit", "debit")) {
            arguments.put("paymentMethod", "card");
        }
        return invokeTool(POLICY_MCP_SERVER, policyServerScript, toolName, userMessage, arguments, start);
    }

    private McpResult estimateDeliveryEta(String userMessage, DeliveryFacts facts) {
        String toolName = "estimate_delivery_eta";
        long start = System.nanoTime();

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("destinationRegion", facts.destinationRegion());
        arguments.put("shippingSpeed", facts.shippingSpeed());
        return invokeTool(FULFILLMENT_MCP_SERVER, fulfillmentServerScript, toolName, userMessage, arguments, start);
    }

    private McpResult recommendShippingOption(String userMessage, DeliveryFacts facts) {
        String toolName = "recommend_shipping_option";
        long start = System.nanoTime();

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("destinationRegion", facts.destinationRegion());
        if (facts.urgencyDays() != null) {
            arguments.put("urgencyDays", facts.urgencyDays());
        }
        arguments.put("budgetPriority", facts.budgetPriority());
        return invokeTool(FULFILLMENT_MCP_SERVER, fulfillmentServerScript, toolName, userMessage, arguments, start);
    }

    private McpResult invokeTool(String serverId,
                                 String scriptPath,
                                 String toolName,
                                 String input,
                                 Map<String, Object> arguments,
                                 long startNanos) {
        Path resolvedScript = resolveScriptPath(scriptPath);
        if (resolvedScript == null) {
            return failedResult(
                    serverId,
                    toolName,
                    input,
                    "MCP server script not found for " + serverId + ": " + scriptPath,
                    "MCP_SERVER_SCRIPT_NOT_FOUND",
                    startNanos
            );
        }

        ProcessBuilder processBuilder = new ProcessBuilder(nodeCommand, resolvedScript.toString());
        processBuilder.redirectErrorStream(true);

        Process process = null;
        try {
            process = processBuilder.start();
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
            ) {
                sendJson(writer, buildInitializeRequest());
                sendJson(writer, buildToolCallRequest(toolName, arguments));
                writer.flush();
                writer.close();

                JsonNode toolCallResult = null;
                JsonNode toolCallError = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode response;
                    try {
                        response = objectMapper.readTree(line);
                    } catch (Exception ignored) {
                        continue;
                    }

                    int responseId = response.path("id").asInt(-1);
                    if (responseId != TOOL_CALL_REQUEST_ID) {
                        continue;
                    }

                    if (response.has("error")) {
                        toolCallError = response.path("error");
                        break;
                    }
                    toolCallResult = response.path("result");
                    break;
                }

                boolean completed = process.waitFor(mcpTimeoutMs, TimeUnit.MILLISECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    return failedResult(
                            serverId,
                            toolName,
                            input,
                            "MCP tool call timed out after " + mcpTimeoutMs + " ms.",
                            "MCP_TIMEOUT",
                            startNanos
                    );
                }

                if (toolCallError != null) {
                    String errorMessage = toolCallError.path("message").asText("MCP tool call failed.");
                    return failedResult(
                            serverId,
                            toolName,
                            input,
                            errorMessage,
                            "MCP_RPC_ERROR",
                            startNanos
                    );
                }
                if (toolCallResult == null || toolCallResult.isMissingNode()) {
                    return failedResult(
                            serverId,
                            toolName,
                            input,
                            "No tools/call result received from MCP server.",
                            "MCP_NO_RESPONSE",
                            startNanos
                    );
                }

                boolean isError = toolCallResult.path("isError").asBoolean(false);
                String output = extractMcpContent(toolCallResult.path("content"));
                if (isError) {
                    return failedResult(
                            serverId,
                            toolName,
                            input,
                            output.isBlank() ? "MCP tool returned an error result." : output,
                            "MCP_TOOL_ERROR",
                            startNanos
                    );
                }
                return successResult(
                        serverId,
                        toolName,
                        input,
                        output.isBlank() ? "ok=true" : output,
                        startNanos
                );
            }
        } catch (IOException e) {
            return failedResult(
                    serverId,
                    toolName,
                    input,
                    "Failed to start MCP server process: " + e.getMessage(),
                    "MCP_PROCESS_ERROR",
                    startNanos
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failedResult(
                    serverId,
                    toolName,
                    input,
                    "MCP call interrupted.",
                    "MCP_INTERRUPTED",
                    startNanos
            );
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Map<String, Object> buildInitializeRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", INITIALIZE_REQUEST_ID);
        request.put("method", "initialize");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", "2024-11-05");
        request.put("params", params);
        return request;
    }

    private Map<String, Object> buildToolCallRequest(String toolName, Map<String, Object> arguments) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", TOOL_CALL_REQUEST_ID);
        request.put("method", "tools/call");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);
        request.put("params", params);
        return request;
    }

    private void sendJson(BufferedWriter writer, Map<String, Object> payload) throws IOException {
        writer.write(objectMapper.writeValueAsString(payload));
        writer.newLine();
    }

    private String extractMcpContent(JsonNode contentNode) {
        if (contentNode == null || !contentNode.isArray()) {
            return "";
        }
        List<String> chunks = new ArrayList<>();
        for (JsonNode entry : contentNode) {
            if (entry != null && "text".equals(entry.path("type").asText())) {
                String text = entry.path("text").asText("");
                if (!text.isBlank()) {
                    chunks.add(text);
                }
            }
        }
        return String.join(" ", chunks).trim();
    }

    private Path resolveScriptPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path configured = Paths.get(configuredPath);
        if (configured.isAbsolute()) {
            return Files.exists(configured) ? configured : null;
        }

        Path fromCwd = Paths.get("").toAbsolutePath().resolve(configured).normalize();
        if (Files.exists(fromCwd)) {
            return fromCwd;
        }

        Path fromParent = Paths.get("").toAbsolutePath().resolve("..").resolve(configured).normalize();
        if (Files.exists(fromParent)) {
            return fromParent;
        }
        return null;
    }

    private McpResult successResult(String serverId, String toolName, String input, String output, long startNanos) {
        return new McpResult(serverId, toolName, input, output, true, null, elapsedMillis(startNanos));
    }

    private McpResult failedResult(
            String serverId,
            String toolName,
            String input,
            String output,
            String errorCode,
            long startNanos
    ) {
        return new McpResult(serverId, toolName, input, output, false, errorCode, elapsedMillis(startNanos));
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static Integer extractInt(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... keywords) {
        String normalized = normalize(text);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyWholeWord(String text, String... keywords) {
        String normalized = normalize(text);
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword.toLowerCase(Locale.ROOT)) + "\\b");
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private record ReturnFacts(Integer daysSinceDelivery, boolean used, boolean unused, Boolean hasTags) {
        String toSummary() {
            return "daysSinceDelivery=" + (daysSinceDelivery == null ? "unknown" : daysSinceDelivery)
                    + ", condition=" + conditionLabel()
                    + ", hasTags=" + (hasTags == null ? "unknown" : hasTags);
        }

        private String conditionLabel() {
            if (used) {
                return "used";
            }
            if (unused) {
                return "unused";
            }
            return "unknown";
        }
    }

    private record DeliveryFacts(String destinationRegion, String shippingSpeed, Integer urgencyDays, boolean budgetPriority) {
        String toSummary() {
            return "destination=" + destinationRegion
                    + ", shippingSpeed=" + shippingSpeed
                    + ", urgencyDays=" + (urgencyDays == null ? "none" : urgencyDays)
                    + ", budgetPriority=" + budgetPriority;
        }
    }

    public record ExecutionResult(
            String route,
            List<LocalToolResult> localTools,
            List<McpResult> mcpCalls
    ) {
        public static ExecutionResult empty() {
            return new ExecutionResult("none", List.of(), List.of());
        }
    }

    public record LocalToolResult(
            String toolName,
            String input,
            String output,
            boolean ok
    ) {
    }

    public record McpResult(
            String serverId,
            String toolName,
            String input,
            String output,
            boolean ok,
            String errorCode,
            long latencyMs
    ) {
    }
}
