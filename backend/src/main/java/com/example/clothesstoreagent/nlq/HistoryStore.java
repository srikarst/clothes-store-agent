package com.example.clothesstoreagent.nlq;

import java.util.List;

/**
 * Interface for storing and retrieving conversation history.
 */
public interface HistoryStore {
    
    /**
     * Add a turn to the conversation history.
     * @param conversationId the conversation identifier
     * @param turn the chat turn to add
     */
    void addTurn(String conversationId, ChatTurn turn);
    
    /**
     * Retrieve the most recent N turns for a conversation.
     * @param conversationId the conversation identifier
     * @param maxTurns maximum number of turns to retrieve
     * @return list of chat turns in chronological order (oldest first)
     */
    List<ChatTurn> getHistory(String conversationId, int maxTurns);
    
    /**
     * Clear all history for a conversation.
     * @param conversationId the conversation identifier
     */
    void clearConversation(String conversationId);
    
    /**
     * Remove expired conversations based on TTL.
     */
    void evictExpired();
}
