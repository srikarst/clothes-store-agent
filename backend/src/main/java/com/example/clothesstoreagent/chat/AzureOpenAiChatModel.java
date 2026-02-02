package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.memory.ChatMessage;
import com.example.clothesstoreagent.memory.ChatRole;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Azure OpenAI Chat Completions implementation.
 *
 * Keeps Azure request/response mapping here so controllers/orchestrators stay provider-agnostic.
 */
public class AzureOpenAiChatModel implements ChatModel {

    private final AppProps props;
    private final HttpClient http;
    private final ObjectMapper om;
    private final Duration requestTimeout;

    public AzureOpenAiChatModel(AppProps props) {
        this(props, HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofSeconds(30));
    }

    public AzureOpenAiChatModel(AppProps props, HttpClient http, ObjectMapper om, Duration requestTimeout) {
        this.props = props;
        this.http = http;
        this.om = om;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public ChatCompletion complete(List<ChatMessage> messages, ChatModelOptions options) {
        requireConfigured();

        List<Map<String, Object>> azureMessages = messages.stream()
                .filter(m -> m != null && m.content() != null)
                .map(m -> Map.<String, Object>of(
                        "role", toAzureRole(m.role()),
                        "content", m.content()
                ))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", azureMessages);

        double temp = options != null && options.temperature() != null ? options.temperature() : props.getChatDefaultTemperature();
        body.put("temperature", temp);

        String url = props.getAzureOpenaiEndpoint().replaceAll("/+$", "") +
                "/openai/deployments/" + props.getAzureOpenaiDeployment() +
                "/chat/completions?api-version=" + props.getAzureOpenaiApiVersion();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("api-key", props.getAzureOpenaiApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("Azure call failed: HTTP " + resp.statusCode());
            }

            Map<?, ?> json = om.readValue(resp.body(), Map.class);
            List<?> choices = (List<?>) json.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("No choices from Azure.");
            }
            Map<?, ?> choice0 = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice0.get("message");
            String content = message != null ? String.valueOf(message.get("content")) : null;
            if (content == null) {
                throw new IllegalStateException("Azure response missing message.content");
            }
            return new ChatCompletion(content);
        } catch (Exception e) {
            throw new IllegalStateException("AzureOpenAiChatModel error: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatModelInfo info() {
        return new ChatModelInfo("azure", props.getAzureOpenaiDeployment());
    }

    private void requireConfigured() {
        if (isBlank(props.getAzureOpenaiEndpoint()) ||
                isBlank(props.getAzureOpenaiApiKey()) ||
                isBlank(props.getAzureOpenaiDeployment())) {
            throw new IllegalStateException("Azure OpenAI not configured. Set app.azureOpenaiEndpoint, " +
                    "app.azureOpenaiApiKey, app.azureOpenaiDeployment (and apiVersion).");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String toAzureRole(ChatRole role) {
        if (role == null) return "user";
        return switch (role) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case USER -> "user";
        };
    }
}



