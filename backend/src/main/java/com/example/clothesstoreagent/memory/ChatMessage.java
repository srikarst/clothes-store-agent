package com.example.clothesstoreagent.memory;

import java.time.Instant;

/**
 * Generic chat message for conversation history.
 */
public record ChatMessage(
        ChatRole role,
        String content,
        Instant timestamp
) {
    public static ChatMessage system(String content) {
        return new ChatMessage(ChatRole.SYSTEM, content, Instant.now());
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content, Instant.now());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content, Instant.now());
    }
}



