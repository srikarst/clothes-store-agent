package com.example.clothesstoreagent.memory;

import java.util.List;

public interface ConversationStore {
    void append(String conversationId, ChatMessage message);

    /**
     * @return messages in chronological order (oldest first)
     */
    List<ChatMessage> getRecent(String conversationId, int maxTurns);

    void clear(String conversationId);

    void evictExpired();
}



