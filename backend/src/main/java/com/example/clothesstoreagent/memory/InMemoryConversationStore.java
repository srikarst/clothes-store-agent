package com.example.clothesstoreagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation with TTL and max turns per conversation.
 */
public class InMemoryConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConversationStore.class);

    private final ConcurrentHashMap<String, Deque<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final int ttlMinutes;
    private final int maxTurns;
    private final Clock clock;

    public InMemoryConversationStore(int ttlMinutes, int maxTurns) {
        this(ttlMinutes, maxTurns, Clock.systemUTC());
    }

    public InMemoryConversationStore(int ttlMinutes, int maxTurns, Clock clock) {
        this.ttlMinutes = ttlMinutes;
        this.maxTurns = maxTurns;
        this.clock = clock;
        log.info("InMemoryConversationStore initialized: ttlMinutes={}, maxTurns={}", ttlMinutes, maxTurns);
    }

    @Override
    public void append(String conversationId, ChatMessage message) {
        if (conversationId == null || conversationId.isBlank() || message == null) {
            return;
        }
        evictExpired();

        conversations.compute(conversationId, (id, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            Instant ts = message.timestamp() != null ? message.timestamp() : Instant.now(clock);
            ChatMessage toStore = new ChatMessage(message.role(), message.content(), ts);
            deque.addLast(toStore);

            while (deque.size() > maxTurns) {
                deque.removeFirst();
            }
            return deque;
        });
    }

    @Override
    public List<ChatMessage> getRecent(String conversationId, int maxTurns) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        evictExpired();

        Deque<ChatMessage> deque = conversations.get(conversationId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> turns = new ArrayList<>(deque);
        int startIdx = Math.max(0, turns.size() - Math.max(0, maxTurns));
        return turns.subList(startIdx, turns.size());
    }

    @Override
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        conversations.remove(conversationId);
    }

    @Override
    public void evictExpired() {
        if (ttlMinutes <= 0) {
            return;
        }
        Instant cutoff = Instant.now(clock).minus(Duration.ofMinutes(ttlMinutes));
        conversations.entrySet().removeIf(entry -> {
            Deque<ChatMessage> turns = entry.getValue();
            if (turns == null || turns.isEmpty()) {
                return true;
            }
            ChatMessage last = turns.peekLast();
            return last != null && last.timestamp() != null && last.timestamp().isBefore(cutoff);
        });
    }
}



