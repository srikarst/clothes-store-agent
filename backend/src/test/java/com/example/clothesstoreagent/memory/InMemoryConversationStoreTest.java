package com.example.clothesstoreagent.memory;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryConversationStoreTest {

    @Test
    void appendsAndReturnsInChronologicalOrder() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-02T00:00:00Z"), ZoneOffset.UTC);
        InMemoryConversationStore store = new InMemoryConversationStore(1440, 50, clock);

        store.append("c1", new ChatMessage(ChatRole.USER, "u1", Instant.parse("2026-02-02T00:00:01Z")));
        store.append("c1", new ChatMessage(ChatRole.ASSISTANT, "a1", Instant.parse("2026-02-02T00:00:02Z")));
        store.append("c1", new ChatMessage(ChatRole.USER, "u2", Instant.parse("2026-02-02T00:00:03Z")));

        List<ChatMessage> recent = store.getRecent("c1", 12);
        assertThat(recent).extracting(ChatMessage::content).containsExactly("u1", "a1", "u2");
    }

    @Test
    void enforcesMaxTurns() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-02T00:00:00Z"), ZoneOffset.UTC);
        InMemoryConversationStore store = new InMemoryConversationStore(1440, 2, clock);

        store.append("c1", new ChatMessage(ChatRole.USER, "u1", Instant.parse("2026-02-02T00:00:01Z")));
        store.append("c1", new ChatMessage(ChatRole.ASSISTANT, "a1", Instant.parse("2026-02-02T00:00:02Z")));
        store.append("c1", new ChatMessage(ChatRole.USER, "u2", Instant.parse("2026-02-02T00:00:03Z")));

        List<ChatMessage> recent = store.getRecent("c1", 12);
        assertThat(recent).extracting(ChatMessage::content).containsExactly("a1", "u2");
    }
}



