package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.memory.ChatMessage;

import java.util.List;

/**
 * Provider-agnostic interface for chat completions.
 */
public interface ChatModel {
    ChatCompletion complete(List<ChatMessage> messages, ChatModelOptions options);

    ChatModelInfo info();
}



