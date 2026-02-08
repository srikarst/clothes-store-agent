package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.domains.DomainHint;
import com.example.clothesstoreagent.domains.DomainRouter;
import com.example.clothesstoreagent.memory.ConversationStore;
import com.example.clothesstoreagent.memory.InMemoryConversationStore;
import com.example.clothesstoreagent.tools.DefaultToolRegistry;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolExecutionResult;
import com.example.clothesstoreagent.tools.ToolRegistry;
import com.example.clothesstoreagent.tools.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChatOrchestratorToolLoopTest {

    @Test
    void toolCallThenFinalWorks() {
        AppProps props = new AppProps();
        props.setHistoryEnabled(true);
        props.setChatMaxSteps(4);
        props.setToolResultMaxChars(500);

        ConversationStore store = new InMemoryConversationStore(1440, 50);

        Tool schemaTool = new Tool() {
            @Override public ToolSpec spec() {
                return new ToolSpec("db.schema_compact", "schema", Map.of("type", "object"));
            }
            @Override public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) {
                return ToolExecutionResult.ok("Schema:\n- dbo.orders:\n  - id (int)\n", Map.of());
            }
        };
        ToolRegistry registry = new DefaultToolRegistry(List.of(schemaTool));

        AtomicInteger calls = new AtomicInteger(0);
        ChatModel model = new ChatModel() {
            @Override
            public ChatCompletion complete(List<com.example.clothesstoreagent.memory.ChatMessage> messages, ChatModelOptions options) {
                int n = calls.incrementAndGet();
                if (n == 1) {
                    return new ChatCompletion("{\"type\":\"tool_call\",\"assistantMessage\":\"\",\"tool\":{\"name\":\"db.schema_compact\",\"args\":{}}}");
                }
                return new ChatCompletion("{\"type\":\"final\",\"assistantMessage\":\"Done\"}");
            }

            @Override
            public ChatModelInfo info() {
                return new ChatModelInfo("test", "local");
            }
        };

        ChatOrchestrator orch = new ChatOrchestrator(props, store, model, new DomainRouter(), registry, new ObjectMapper());
        ChatOrchestrator.Result out = orch.chatV1("c1", "show revenue", DomainHint.ANALYTICS_SQL, true, 4, 10, 0.1, true);

        assertThat(out.assistantMessage()).isEqualTo("Done");
        assertThat(out.ranTools()).isTrue();
        assertThat(out.domain()).isEqualTo("analytics_sql");
        assertThat(out.toolTrace()).hasSize(1);
        assertThat(out.toolTrace().get(0).tool()).isEqualTo("db.schema_compact");
    }

    @Test
    void executeToolsFalseBlocksExecution() {
        AppProps props = new AppProps();
        props.setHistoryEnabled(false);

        ConversationStore store = new InMemoryConversationStore(1440, 50);

        ToolRegistry registry = new DefaultToolRegistry(List.of());
        ChatModel model = new ChatModel() {
            @Override
            public ChatCompletion complete(List<com.example.clothesstoreagent.memory.ChatMessage> messages, ChatModelOptions options) {
                return new ChatCompletion("{\"type\":\"tool_call\",\"assistantMessage\":\"\",\"tool\":{\"name\":\"db.schema_compact\",\"args\":{}}}");
            }

            @Override
            public ChatModelInfo info() {
                return new ChatModelInfo("test", "local");
            }
        };

        ChatOrchestrator orch = new ChatOrchestrator(props, store, model, new DomainRouter(), registry, new ObjectMapper());
        ChatOrchestrator.Result out = orch.chatV1("c1", "show schema", DomainHint.ANALYTICS_SQL, false, 4, 10, 0.1, false);

        assertThat(out.ranTools()).isFalse();
        assertThat(out.assistantMessage()).contains("Tools are disabled");
    }

    @Test
    void stopsAtMaxSteps() {
        AppProps props = new AppProps();
        props.setHistoryEnabled(false);

        ConversationStore store = new InMemoryConversationStore(1440, 50);

        Tool noop = new Tool() {
            @Override public ToolSpec spec() { return new ToolSpec("db.schema_compact", "d", Map.of()); }
            @Override public ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx) { return ToolExecutionResult.ok("x", Map.of()); }
        };
        ToolRegistry registry = new DefaultToolRegistry(List.of(noop));

        // Always tool_call => should hit maxSteps and return fallback message (non-empty).
        ChatModel model = new ChatModel() {
            @Override
            public ChatCompletion complete(List<com.example.clothesstoreagent.memory.ChatMessage> messages, ChatModelOptions options) {
                return new ChatCompletion("{\"type\":\"tool_call\",\"assistantMessage\":\"\",\"tool\":{\"name\":\"db.schema_compact\",\"args\":{}}}");
            }

            @Override
            public ChatModelInfo info() {
                return new ChatModelInfo("test", "local");
            }
        };

        ChatOrchestrator orch = new ChatOrchestrator(props, store, model, new DomainRouter(), registry, new ObjectMapper());
        ChatOrchestrator.Result out = orch.chatV1("c1", "x", DomainHint.ANALYTICS_SQL, true, 2, 10, 0.1, false);

        assertThat(out.assistantMessage()).isNotBlank();
        assertThat(out.ranTools()).isTrue();
    }
}


