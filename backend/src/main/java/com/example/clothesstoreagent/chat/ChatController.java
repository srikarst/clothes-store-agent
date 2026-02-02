package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.memory.ConversationStore;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatOrchestrator orchestrator;
    private final ConversationStore store;
    private final ConversationIdResolver conversationIdResolver = new ConversationIdResolver();

    public ChatController(ChatOrchestrator orchestrator, ConversationStore store) {
        this.orchestrator = orchestrator;
        this.store = store;
    }

    public static class ChatRequest {
        @NotBlank public String message;
        public String conversationId;
        public Integer maxTurns;
        public Double temperature;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody ChatRequest req,
            @RequestHeader(value = "X-Conversation-Id", required = false) String conversationIdHeader,
            @RequestParam(value = "conversationId", required = false) String conversationIdParam
    ) {
        if (req == null || req.message == null || req.message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "message is required"
            ));
        }
        ConversationIdResolver.Result resolved = conversationIdResolver.resolve(
                conversationIdHeader,
                conversationIdParam,
                req.conversationId
        );
        String conversationId = resolved.conversationId();

        int msgLen = req.message.length();
        log.info("Chat request conversationId='{}' messageLength={} maxTurns={} temperature={}",
                conversationId, msgLen, req != null ? req.maxTurns : null, req != null ? req.temperature : null);

        ChatOrchestrator.Result r = orchestrator.chat(
                conversationId,
                req.message,
                req.maxTurns,
                req.temperature
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", conversationId);
        out.put("assistantMessage", r.assistantMessage());
        out.put("historyUsedTurns", r.historyUsedTurns());
        out.put("model", Map.of(
                "provider", r.modelInfo().provider(),
                "deployment", r.modelInfo().deployment()
        ));
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/conversation/{id}")
    public ResponseEntity<Map<String, Object>> clearConversation(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_ID",
                    "message", "Conversation ID cannot be empty"
            ));
        }
        store.clear(id);
        log.info("Cleared chat conversation '{}'", id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "conversationId", id,
                "message", "Conversation history cleared"
        ));
    }
}


