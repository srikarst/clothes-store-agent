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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AzureOpenAiEmbeddingClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String deployment;
    private final String apiVersion;
    private final String apiKey;
    private final long timeoutMs;

    public AzureOpenAiEmbeddingClient(
            ObjectMapper objectMapper,
            @Value("${APP_RAG_EMBEDDING_ENDPOINT:${APP_AZURE_ENDPOINT:}}") String endpoint,
            @Value("${APP_RAG_EMBEDDING_DEPLOYMENT:}") String deployment,
            @Value("${APP_RAG_EMBEDDING_API_VERSION:2024-02-15-preview}") String apiVersion,
            @Value("${APP_RAG_EMBEDDING_API_KEY:${APP_AZURE_API_KEY:}}") String apiKey,
            @Value("${APP_RAG_EMBEDDING_TIMEOUT_MS:5000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.endpoint = endpoint;
        this.deployment = deployment;
        this.apiVersion = apiVersion;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
    }

    public List<Double> embed(String input) {
        if (input == null || input.isBlank() || !isConfigured()) {
            return List.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", input);

        String url = endpoint.replaceAll("/+$", "") +
                "/openai/deployments/" + deployment +
                "/embeddings?api-version=" + apiVersion;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray()) {
                return List.of();
            }

            List<Double> embedding = new ArrayList<>(embeddingNode.size());
            for (JsonNode value : embeddingNode) {
                embedding.add(value.asDouble());
            }
            return embedding;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public boolean isConfigured() {
        return notBlank(endpoint) && notBlank(deployment) && notBlank(apiVersion) && notBlank(apiKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
