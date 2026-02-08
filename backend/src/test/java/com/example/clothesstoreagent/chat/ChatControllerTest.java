package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.memory.ConversationStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void returnsConversationIdAndAssistantMessage() {
        ConversationStore store = mock(ConversationStore.class);
        ChatOrchestrator orch = mock(ChatOrchestrator.class);
        when(orch.chatV1("c1", "hi",
                com.example.clothesstoreagent.domains.DomainHint.AUTO,
                true,
                null,
                null,
                null,
                false))
                .thenReturn(new ChatOrchestrator.Result(
                        "hello!",
                        0,
                        new ChatModelInfo("azure", "dep"),
                        "general",
                        false,
                        java.util.List.of()
                ));

        ChatController c = new ChatController(orch, store);
        ChatController.ChatRequest req = new ChatController.ChatRequest();
        req.message = "hi";

        ResponseEntity<Map<String, Object>> resp = c.chat(req, "c1", null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("conversationId", "c1");
        assertThat(resp.getBody()).containsEntry("assistantMessage", "hello!");
    }
}



