package com.example.clothesstoreagent.nlq;

import java.time.Instant;

/**
 * Represents a single turn in a conversation history.
 */
public record ChatTurn(
    Role role,
    String content,
    Instant timestamp
) {
    public enum Role {
        USER,
        ASSISTANT
    }

    public static ChatTurn user(String content) {
        return new ChatTurn(Role.USER, content, Instant.now());
    }

    public static ChatTurn assistant(String content) {
        return new ChatTurn(Role.ASSISTANT, content, Instant.now());
    }
}
