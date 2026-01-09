package com.example.clothesstoreagent.nlq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of HistoryStore with TTL and max turns per conversation.
 */
public class InMemoryHistoryStore implements HistoryStore {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryHistoryStore.class);
    
    private final ConcurrentHashMap<String, Deque<ChatTurn>> conversations = new ConcurrentHashMap<>();
    private final int ttlMinutes;
    private final int maxTurns;
    
    public InMemoryHistoryStore(int ttlMinutes, int maxTurns) {
        this.ttlMinutes = ttlMinutes;
        this.maxTurns = maxTurns;
        log.info("InMemoryHistoryStore initialized: ttlMinutes={}, maxTurns={}", ttlMinutes, maxTurns);
    }
    
    @Override
    public void addTurn(String conversationId, ChatTurn turn) {
        if (conversationId == null || turn == null) {
            return;
        }
        
        conversations.compute(conversationId, (id, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            deque.addLast(turn);
            
            // Enforce max turns limit
            while (deque.size() > maxTurns) {
                deque.removeFirst();
            }
            
            return deque;
        });
        
        log.debug("Added {} turn to conversation '{}', total turns: {}", 
                 turn.role(), conversationId, conversations.get(conversationId).size());
    }
    
    @Override
    public List<ChatTurn> getHistory(String conversationId, int maxTurns) {
        if (conversationId == null) {
            return List.of();
        }
        
        Deque<ChatTurn> deque = conversations.get(conversationId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        
        // Return the last N turns in chronological order
        List<ChatTurn> turns = new ArrayList<>(deque);
        int startIdx = Math.max(0, turns.size() - maxTurns);
        List<ChatTurn> result = turns.subList(startIdx, turns.size());
        
        log.debug("Retrieved {} turns from conversation '{}' (requested up to {})", 
                 result.size(), conversationId, maxTurns);
        
        return result;
    }
    
    @Override
    public void clearConversation(String conversationId) {
        if (conversationId == null) {
            return;
        }
        
        Deque<ChatTurn> removed = conversations.remove(conversationId);
        if (removed != null) {
            log.info("Cleared conversation '{}' with {} turns", conversationId, removed.size());
        } else {
            log.debug("Conversation '{}' not found for clearing", conversationId);
        }
    }
    
    @Override
    public void evictExpired() {
        if (ttlMinutes <= 0) {
            return; // TTL disabled
        }
        
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(ttlMinutes));
        int initialSize = conversations.size();
        
        conversations.entrySet().removeIf(entry -> {
            Deque<ChatTurn> turns = entry.getValue();
            if (turns.isEmpty()) {
                return true;
            }
            
            // Check if the most recent turn is older than TTL
            ChatTurn lastTurn = turns.peekLast();
            return lastTurn != null && lastTurn.timestamp().isBefore(cutoff);
        });
        
        int evicted = initialSize - conversations.size();
        if (evicted > 0) {
            log.info("Evicted {} expired conversations (TTL: {} minutes)", evicted, ttlMinutes);
        }
    }
    
    /**
     * Get total number of active conversations (for monitoring/testing).
     */
    public int getActiveConversationCount() {
        return conversations.size();
    }
}
