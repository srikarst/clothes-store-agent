package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.memory.ChatMessage;
import com.example.clothesstoreagent.memory.ChatRole;
import com.example.clothesstoreagent.memory.ConversationStore;
import com.example.clothesstoreagent.memory.InMemoryConversationStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatOrchestratorTest {

    @Test
    void loadsHistoryCallsModelAndPersistsTurns() {
        AppProps props = new AppProps();
        props.setHistoryEnabled(true);
        props.setHistoryMaxTurns(6);
        props.setChatSystemPrompt("sys");
        props.setChatDefaultTemperature(0.2);

        ConversationStore store = new InMemoryConversationStore(1440, 50);
        store.append("c1", new ChatMessage(ChatRole.USER, "hello", Instant.now()));
        store.append("c1", new ChatMessage(ChatRole.ASSISTANT, "hi", Instant.now()));

        ChatModel model = mock(ChatModel.class);
        when(model.complete(any(), any())).thenReturn(new ChatCompletion("assistant reply"));
        when(model.info()).thenReturn(new ChatModelInfo("azure", "dep"));

        ChatOrchestrator orch = new ChatOrchestrator(props, store, model);
        ChatOrchestrator.Result out = orch.chat("c1", "next", 12, 0.1);

        assertThat(out.assistantMessage()).isEqualTo("assistant reply");
        assertThat(out.historyUsedTurns()).isEqualTo(2);

        List<ChatMessage> after = store.getRecent("c1", 50);
        assertThat(after).extracting(ChatMessage::content)
                .endsWith("next", "assistant reply");
    }
}



