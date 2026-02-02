package com.example.clothesstoreagent.chat;

import java.util.UUID;

/**
 * Conversation ID resolution logic:
 * 1) X-Conversation-Id header
 * 2) conversationId query param
 * 3) conversationId in body
 * 4) generate a new id
 */
public class ConversationIdResolver {

    public record Result(String conversationId, boolean generated) {}

    public Result resolve(String header, String queryParam, String body) {
        String fromHeader = normalize(header);
        if (fromHeader != null) {
            return new Result(fromHeader, false);
        }
        String fromQuery = normalize(queryParam);
        if (fromQuery != null) {
            return new Result(fromQuery, false);
        }
        String fromBody = normalize(body);
        if (fromBody != null) {
            return new Result(fromBody, false);
        }
        return new Result(UUID.randomUUID().toString(), true);
    }

    private static String normalize(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isBlank() ? null : t;
    }
}



