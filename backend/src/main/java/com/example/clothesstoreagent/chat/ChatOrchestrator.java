package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.memory.ChatMessage;
import com.example.clothesstoreagent.memory.ConversationStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin orchestration: load history -> call model -> persist turns.
 */
public class ChatOrchestrator {

    public static final int MAX_TURNS_UPPER_CAP = 50;

    private final AppProps props;
    private final ConversationStore store;
    private final ChatModel model;

    public record Result(
            String assistantMessage,
            int historyUsedTurns,
            ChatModelInfo modelInfo
    ) {}

    public ChatOrchestrator(AppProps props, ConversationStore store, ChatModel model) {
        this.props = props;
        this.store = store;
        this.model = model;
    }

    public Result chat(String conversationId, String userMessage, Integer maxTurnsOverride, Double temperatureOverride) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }

        int requested = maxTurnsOverride != null ? maxTurnsOverride : props.getHistoryMaxTurns();
        int maxTurns = Math.min(Math.max(0, requested), MAX_TURNS_UPPER_CAP);

        List<ChatMessage> history = props.isHistoryEnabled()
                ? store.getRecent(conversationId, maxTurns)
                : List.of();

        List<ChatMessage> prompt = new ArrayList<>(history.size() + 2);
        String system = props.getChatSystemPrompt();
        if (system != null && !system.isBlank()) {
            prompt.add(ChatMessage.system(system));
        }
        prompt.addAll(history);
        prompt.add(ChatMessage.user(userMessage.trim()));

        ChatModelOptions options = new ChatModelOptions(temperatureOverride);
        ChatCompletion completion = model.complete(prompt, options);

        String assistant = completion != null ? completion.assistantMessage() : null;
        if (assistant == null) {
            assistant = "";
        }

        if (props.isHistoryEnabled()) {
            store.append(conversationId, ChatMessage.user(userMessage.trim()));
            store.append(conversationId, ChatMessage.assistant(assistant));
        }

        return new Result(assistant, history.size(), model.info());
    }
}



