package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.domains.Domain;
import com.example.clothesstoreagent.domains.DomainHint;
import com.example.clothesstoreagent.domains.DomainRouter;
import com.example.clothesstoreagent.memory.ChatMessage;
import com.example.clothesstoreagent.memory.ConversationStore;
import com.example.clothesstoreagent.tools.Tool;
import com.example.clothesstoreagent.tools.ToolContext;
import com.example.clothesstoreagent.tools.ToolExecutionResult;
import com.example.clothesstoreagent.tools.ToolRegistry;
import com.example.clothesstoreagent.tools.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 1 orchestrator: domain routing + bounded local tool loop.
 */
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    public static final int MAX_TURNS_UPPER_CAP = 50;
    private static final int MAX_JSON_PARSE_ATTEMPTS = 2;

    private final AppProps props;
    private final ConversationStore store;
    private final ChatModel model;
    private final DomainRouter domainRouter;
    private final ToolRegistry tools;
    private final ObjectMapper om;

    public record Result(
            String assistantMessage,
            int historyUsedTurns,
            ChatModelInfo modelInfo,
            String domain,
            boolean ranTools,
            List<ToolTraceItem> toolTrace
    ) {}

    public ChatOrchestrator(AppProps props,
                            ConversationStore store,
                            ChatModel model,
                            DomainRouter domainRouter,
                            ToolRegistry tools,
                            ObjectMapper om) {
        this.props = props;
        this.store = store;
        this.model = model;
        this.domainRouter = domainRouter;
        this.tools = tools;
        this.om = om;
    }

    /**
     * Backward-compatible entrypoint (Phase 0 contract).
     */
    public Result chat(String conversationId, String userMessage, Integer maxTurnsOverride, Double temperatureOverride) {
        return chatV1(conversationId, userMessage, DomainHint.AUTO, true, props.getChatMaxSteps(),
                maxTurnsOverride, temperatureOverride, false);
    }

    public Result chatV1(String conversationId,
                         String userMessage,
                         DomainHint domainHint,
                         boolean executeTools,
                         Integer maxStepsOverride,
                         Integer maxTurnsOverride,
                         Double temperatureOverride,
                         boolean showToolTrace) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }

        int requested = maxTurnsOverride != null ? maxTurnsOverride : props.getHistoryMaxTurns();
        int maxTurns = Math.min(Math.max(0, requested), MAX_TURNS_UPPER_CAP);

        List<ChatMessage> history = props.isHistoryEnabled()
                ? store.getRecent(conversationId, maxTurns)
                : List.of();

        DomainHint hint = domainHint != null ? domainHint : DomainHint.AUTO;
        Domain domain = domainRouter.route(hint, userMessage);

        int maxStepsRequested = maxStepsOverride != null ? maxStepsOverride : props.getChatMaxSteps();
        int maxSteps = Math.min(Math.max(1, maxStepsRequested), Math.max(1, props.getChatMaxSteps()));

        // Persist user message once up-front.
        if (props.isHistoryEnabled()) {
            store.append(conversationId, ChatMessage.user(userMessage.trim()));
        }

        List<ToolTraceItem> trace = showToolTrace ? new ArrayList<>() : List.of();
        boolean ranTools = false;

        // Build common prompt scaffold.
        List<ChatMessage> basePrompt = new ArrayList<>(history.size() + 2);
        basePrompt.add(ChatMessage.system(buildSystemPrompt(domain, executeTools)));
        basePrompt.addAll(history);
        basePrompt.add(ChatMessage.user(userMessage.trim()));

        List<ChatMessage> prompt = new ArrayList<>(basePrompt);

        String assistantFinal = "";
        for (int step = 1; step <= maxSteps; step++) {
            AssistantOutput out = callModelForAssistantOutput(prompt, temperatureOverride);

            if (out == null) {
                assistantFinal = "Sorry — I had trouble generating a response. Please try again.";
                break;
            }

            String type = out.type != null ? out.type.trim().toLowerCase(Locale.ROOT) : "";
            if ("final".equals(type)) {
                assistantFinal = out.assistantMessage != null ? out.assistantMessage : "";
                break;
            }

            if (!"tool_call".equals(type)) {
                assistantFinal = out.assistantMessage != null && !out.assistantMessage.isBlank()
                        ? out.assistantMessage
                        : "Sorry — I couldn't interpret the model output. Please try again.";
                break;
            }

            if (domain == Domain.GENERAL) {
                assistantFinal = "I’m in the general domain and won’t run database tools. Please switch to Analytics (SQL) or ask a non-DB question.";
                break;
            }

            ToolCall tc = out.tool;
            if (tc == null || tc.name() == null || tc.name().isBlank()) {
                assistantFinal = "Sorry — the model requested a tool but didn't provide a valid tool name.";
                break;
            }

            String toolName = tc.name().trim();
            if (!executeTools) {
                assistantFinal = "Tools are disabled for this request. Intended tool call: " + toolName + ".";
                break;
            }

            Tool tool = tools.getToolByName(toolName);
            if (tool == null) {
                assistantFinal = "Sorry — unknown tool requested: " + toolName + ".";
                break;
            }

            // Ensure tool is available for this domain.
            List<String> allowed = tools.listTools(domain, new ToolContext(conversationId, domain, null, null, null))
                    .stream()
                    .map(ToolSpec::name)
                    .toList();
            if (!allowed.contains(tool.spec().name())) {
                assistantFinal = "Sorry — tool not available in this domain: " + toolName + ".";
                break;
            }

            Map<String, Object> args = tc.args() != null ? tc.args() : Map.of();
            ToolExecutionResult tr;
            try {
                tr = tool.execute(args, new ToolContext(conversationId, domain, null, null, null));
            } catch (Exception e) {
                tr = ToolExecutionResult.fail("Tool threw exception: " + e.getMessage(), Map.of());
            }
            ranTools = true;

            String preview = preview(tr.content(), 220);
            if (showToolTrace) {
                trace.add(new ToolTraceItem(step, tool.spec().name(), args, tr.ok(), preview));
            }

            log.info("Tool ran name={} ok={}", tool.spec().name(), tr.ok());

            // Append tool result message into prompt and (truncated) into conversation history.
            String toolResultMessage = formatToolResult(tool.spec().name(), tr);
            String stored = truncate(toolResultMessage, props.getToolResultMaxChars());

            prompt.add(ChatMessage.assistant(stored));
            if (props.isHistoryEnabled()) {
                store.append(conversationId, ChatMessage.assistant(stored));
            }

            // If tool failed, stop and let model synthesize a user-facing response in the next step,
            // unless we are at the last step (then return a safe failure message).
            if (!tr.ok() && step == maxSteps) {
                assistantFinal = "I couldn't complete that using the available tools. " +
                        "Try rephrasing your question or providing more details.";
                break;
            }
        }

        if (assistantFinal == null) assistantFinal = "";
        if (assistantFinal.isBlank()) {
            assistantFinal = ranTools
                    ? "I reached the maximum number of tool steps for this request before producing a final answer. " +
                      "Try asking a narrower question or increasing maxSteps."
                    : "Sorry — I couldn't produce a response. Please try again.";
        }

        if (props.isHistoryEnabled()) {
            store.append(conversationId, ChatMessage.assistant(assistantFinal));
        }

        return new Result(assistantFinal, history.size(), model.info(), domain.id(), ranTools, trace);
    }

    private AssistantOutput callModelForAssistantOutput(List<ChatMessage> prompt, Double temperatureOverride) {
        ChatModelOptions options = new ChatModelOptions(temperatureOverride, true);
        for (int attempt = 1; attempt <= MAX_JSON_PARSE_ATTEMPTS; attempt++) {
            ChatCompletion completion = model.complete(prompt, options);
            String raw = completion != null ? completion.assistantMessage() : null;
            if (raw == null) raw = "";

            try {
                AssistantOutput out = om.readValue(raw, AssistantOutput.class);
                if (out != null && out.type != null) {
                    return out;
                }
            } catch (Exception ignore) {
                // retry once with an extra corrective message
            }

            prompt = new ArrayList<>(prompt);
            prompt.add(ChatMessage.system(
                    "Your previous message was invalid. Reply with ONLY valid JSON matching the schema exactly."));
        }
        return null;
    }

    private String buildSystemPrompt(Domain domain, boolean executeTools) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful assistant for a clothes store application.\n\n");

        sb.append("You MUST reply with ONLY a JSON object (no markdown, no extra text) in this schema:\n");
        sb.append("{\"type\":\"final\"|\"tool_call\",\"assistantMessage\":\"string\",\"tool\":{\"name\":\"string\",\"args\":{}}}\n");
        sb.append("Rules:\n");
        sb.append("- If type=\"final\", provide assistantMessage and omit tool or set tool=null.\n");
        sb.append("- If type=\"tool_call\", provide tool.name and tool.args. assistantMessage can be brief.\n\n");

        if (domain == Domain.GENERAL) {
            sb.append("Domain: general. Do not call tools.\n");
            sb.append("Always respond with type=\"final\".\n");
            return sb.toString();
        }

        sb.append("Domain: analytics_sql. You can plan and query a SQL database using tools.\n");
        if (!executeTools) {
            sb.append("Tools are DISABLED for this request; you may still propose a tool_call, but it won't run.\n");
        }
        sb.append("Prefer using db.schema_compact first if you need schema context.\n\n");

        ToolContext ctx = new ToolContext("n/a", domain, null, null, null);
        List<ToolSpec> specs = tools.listTools(domain, ctx);
        sb.append("Available tools:\n");
        try {
            sb.append(om.writeValueAsString(specs));
        } catch (Exception e) {
            for (ToolSpec s : specs) {
                sb.append("- ").append(s.name()).append(": ").append(s.description()).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatToolResult(String name, ToolExecutionResult r) {
        String ok = r.ok() ? "true" : "false";
        String content = r.content() != null ? r.content() : "";
        return "TOOL_RESULT name=" + name + " ok=" + ok + ":\n" + content;
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return "";
        int max = Math.max(0, maxChars);
        if (max == 0) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String preview(String s, int maxChars) {
        String t = truncate(s, Math.max(0, maxChars));
        return t.replaceAll("\\s+", " ").trim();
    }
}



