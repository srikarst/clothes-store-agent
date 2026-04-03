package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SimpleModelClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String deployment;
    private final String apiVersion;
    private final String apiKey;
    private final double temperature;

    public SimpleModelClient(
            ObjectMapper objectMapper,
            @Value("${APP_AZURE_ENDPOINT:}") String endpoint,
            @Value("${APP_AZURE_DEPLOYMENT:}") String deployment,
            @Value("${APP_AZURE_API_VERSION:2024-02-15-preview}") String apiVersion,
            @Value("${APP_AZURE_API_KEY:}") String apiKey,
            @Value("${APP_CHAT_DEFAULT_TEMPERATURE:0.2}") double temperature
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.endpoint = endpoint;
        this.deployment = deployment;
        this.apiVersion = apiVersion;
        this.apiKey = apiKey;
        this.temperature = temperature;
    }

    public String generateAssistantMessage(String userMessage,
                                           String intent,
                                           List<String> ragContext,
                                           SimpleMcpClient.McpResult mcpResult) {
        if (!isConfigured()) {
            return null;
        }

        String mcpInfo = mcpResult == null
                ? "none"
                : "tool=" + mcpResult.toolName() + ", ok=" + mcpResult.ok() + ", output=" + mcpResult.output();
        String ragInfo = (ragContext == null || ragContext.isEmpty())
                ? "none"
                : String.join(" | ", ragContext);

        String prompt = """
                User message: %s
                Detected intent: %s
                Retrieved context: %s
                MCP result: %s

                Respond as a helpful clothes-store assistant in plain text.
                Keep it concise (max 3 short sentences).
                Use retrieved context when available.
                """.formatted(userMessage, intent, ragInfo, mcpInfo);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a concise clothes store support assistant."),
                    Map.of("role", "user", "content", prompt)
            ));
            payload.put("temperature", temperature);

            String url = endpoint.replaceAll("/+$", "") +
                    "/openai/deployments/" + deployment +
                    "/chat/completions?api-version=" + apiVersion;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode()) {
                return null;
            }
            String content = contentNode.asText("").trim();
            return content.isEmpty() ? null : content;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isConfigured() {
        return notBlank(endpoint) && notBlank(deployment) && notBlank(apiVersion) && notBlank(apiKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
