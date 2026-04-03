package com.example.clothesstoreagent.simple;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Validated
@CrossOrigin(origins = "http://localhost:3000")
public class SimpleChatController {

    private final SimpleAgentService agentService;

    public SimpleChatController(SimpleAgentService agentService) {
        this.agentService = agentService;
    }

    public static class ChatRequest {
        @NotBlank
        public String message;
    }

    @PostMapping
    public ResponseEntity<?> chat(@Valid @RequestBody ChatRequest request) {
        if (request == null || request.message == null || request.message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "message is required"
            ));
        }
        return ResponseEntity.ok(agentService.chat(request.message));
    }
}
