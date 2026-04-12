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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class QdrantRagStore {

    private static final List<String> DEFAULT_TEXT_KEYS = List.of("text", "chunk", "content", "page_content", "body");
    private static final List<String> DEFAULT_SOURCE_KEYS = List.of("source", "source_id", "doc_id", "document_id", "url", "path");

    private final ObjectMapper objectMapper;
    private final AzureOpenAiEmbeddingClient embeddingClient;
    private final HttpClient httpClient;
    private final String qdrantUrl;
    private final String collection;
    private final String apiKey;
    private final long timeoutMs;
    private final int defaultTopK;
    private final double scoreThreshold;
    private final String textPayloadKey;
    private final String sourcePayloadKey;

    public QdrantRagStore(
            ObjectMapper objectMapper,
            AzureOpenAiEmbeddingClient embeddingClient,
            @Value("${APP_RAG_QDRANT_URL:}") String qdrantUrl,
            @Value("${APP_RAG_QDRANT_COLLECTION:}") String collection,
            @Value("${APP_RAG_QDRANT_API_KEY:}") String apiKey,
            @Value("${APP_RAG_TIMEOUT_MS:5000}") long timeoutMs,
            @Value("${APP_RAG_TOP_K:3}") int defaultTopK,
            @Value("${APP_RAG_SCORE_THRESHOLD:0.25}") double scoreThreshold,
            @Value("${APP_RAG_TEXT_PAYLOAD_KEY:text}") String textPayloadKey,
            @Value("${APP_RAG_SOURCE_PAYLOAD_KEY:source}") String sourcePayloadKey
    ) {
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.httpClient = HttpClient.newHttpClient();
        this.qdrantUrl = qdrantUrl;
        this.collection = collection;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.defaultTopK = Math.max(defaultTopK, 1);
        this.scoreThreshold = scoreThreshold;
        this.textPayloadKey = textPayloadKey;
        this.sourcePayloadKey = sourcePayloadKey;
    }

    public List<String> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || !isConfigured()) {
            return List.of();
        }

        List<Double> vector = embeddingClient.embed(query);
        if (vector.isEmpty()) {
            return List.of();
        }

        int effectiveTopK = topK > 0 ? topK : defaultTopK;
        List<ScoredChunk> chunks = searchQdrant(vector, effectiveTopK);
        if (chunks.isEmpty()) {
            return List.of();
        }
        chunks.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        Set<String> uniqueChunks = new LinkedHashSet<>();
        for (ScoredChunk chunk : chunks) {
            uniqueChunks.add(formatChunk(chunk));
            if (uniqueChunks.size() >= effectiveTopK) {
                break;
            }
        }
        return new ArrayList<>(uniqueChunks);
    }

    public boolean isConfigured() {
        return embeddingClient.isConfigured() && notBlank(qdrantUrl) && notBlank(collection);
    }

    private List<ScoredChunk> searchQdrant(List<Double> vector, int topK) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vector", vector);
        payload.put("limit", topK);
        payload.put("with_payload", true);
        payload.put("with_vector", false);
        if (scoreThreshold > 0) {
            payload.put("score_threshold", scoreThreshold);
        }

        String url = qdrantUrl.replaceAll("/+$", "")
                + "/collections/" + collection + "/points/search";

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

            if (notBlank(apiKey)) {
                requestBuilder.header("api-key", apiKey);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            return extractChunks(root.path("result"));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<ScoredChunk> extractChunks(JsonNode resultNode) {
        JsonNode hitsNode = resultNode;
        if (resultNode != null && resultNode.isObject() && resultNode.path("points").isArray()) {
            hitsNode = resultNode.path("points");
        }
        if (hitsNode == null || !hitsNode.isArray()) {
            return List.of();
        }

        List<ScoredChunk> chunks = new ArrayList<>();
        for (JsonNode hit : hitsNode) {
            JsonNode payloadNode = hit.path("payload");
            String text = selectPayloadField(payloadNode, textPayloadKey, DEFAULT_TEXT_KEYS);
            if (text.isBlank()) {
                continue;
            }
            String source = selectPayloadField(payloadNode, sourcePayloadKey, DEFAULT_SOURCE_KEYS);
            chunks.add(new ScoredChunk(text, source, hit.path("score").asDouble(0.0)));
        }
        return chunks;
    }

    private static String selectPayloadField(JsonNode payloadNode, String preferredKey, List<String> fallbackKeys) {
        if (payloadNode == null || !payloadNode.isObject()) {
            return "";
        }

        if (notBlank(preferredKey)) {
            String preferred = payloadAsText(payloadNode.get(preferredKey));
            if (!preferred.isBlank()) {
                return preferred;
            }
        }

        for (String key : fallbackKeys) {
            String value = payloadAsText(payloadNode.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String payloadAsText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("").trim();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText("").trim();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode entry : node) {
                String text = payloadAsText(entry);
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return String.join(", ", values).trim();
        }
        return node.toString().trim();
    }

    private static String formatChunk(ScoredChunk chunk) {
        if (chunk.source().isBlank()) {
            return chunk.text().trim();
        }
        return "[" + chunk.source().trim() + "] " + chunk.text().trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record ScoredChunk(String text, String source, double score) {
    }
}
