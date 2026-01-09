package com.example.clothesstoreagent.api;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.nlq.ChatTurn;
import com.example.clothesstoreagent.nlq.HistoryStore;
import com.example.clothesstoreagent.nlq.JudgeGeneratorAzureProvider;
import com.example.clothesstoreagent.nlq.NlqProvider;
import com.example.clothesstoreagent.nlq.judge.RepairResponse;
import com.example.clothesstoreagent.service.QueryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nlq")
@Validated
public class NlqController {

    private static final Logger log = LoggerFactory.getLogger(NlqController.class);

    private final NlqProvider nlq;
    private final QueryService query;
    private final AppProps props;
    private final HistoryStore historyStore;

    public NlqController(NlqProvider nlq, QueryService query, AppProps props, HistoryStore historyStore) {
        this.nlq = nlq;
        this.query = query;
        this.props = props;
        this.historyStore = historyStore;
    }

    public static class NlqRequest {
        @NotBlank public String prompt;
        public Boolean execute;
        public Integer maxRows;
        public Integer timeoutSeconds;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(
            @RequestBody NlqRequest req,
            @RequestHeader(value = "X-Conversation-Id", required = false) String conversationIdHeader,
            @RequestParam(value = "conversationId", required = false) String conversationIdParam) {
        try {
            String preview = req.prompt != null && req.prompt.length() > 160
                    ? req.prompt.substring(0, 160) + "…"
                    : req.prompt;
            boolean doRunFlag = req.execute == null || Boolean.TRUE.equals(req.execute);
            
            // Determine conversation ID (header takes precedence over query param)
            String conversationId = conversationIdHeader != null ? conversationIdHeader : conversationIdParam;
            boolean useHistory = props.isHistoryEnabled() && conversationId != null && !conversationId.isBlank();
            
            log.info("NLQ request execute={} prompt='{}' conversationId='{}' historyEnabled={}",
                    doRunFlag, preview, conversationId, useHistory);

            // Load conversation history if enabled
            List<ChatTurn> history = List.of();
            if (useHistory) {
                history = historyStore.getHistory(conversationId, props.getHistoryMaxTurns());
                log.debug("Loaded {} turns from conversation '{}'", history.size(), conversationId);
            }
            
            // Compile with or without history
            NlqProvider.Decision decision = useHistory ? 
                nlq.compileWithHistory(req.prompt, history) : 
                nlq.compile(req.prompt);

            Map<String, Object> resp = new LinkedHashMap<>();
            if (decision.intent != null) {
                resp.put("recognizedIntent", decision.intent);
            }
            resp.put("decision", decision.decisionKey());
            if (decision.question != null && !decision.question.isBlank()) {
                resp.put("question", decision.question);
            }
            if (!decision.missing.isEmpty()) {
                resp.put("missing", decision.missing);
            }

            switch (decision.decision) {
                case CLARIFY -> {
                    resp.put("clarify", true);
                    resp.put("ran", false);
                    
                    // Save to history
                    if (useHistory) {
                        historyStore.addTurn(conversationId, ChatTurn.user(req.prompt));
                        String assistantMsg = "QUESTION: " + decision.question;
                        historyStore.addTurn(conversationId, ChatTurn.assistant(assistantMsg));
                        log.debug("Saved clarify turns to conversation '{}'", conversationId);
                    }
                    
                    log.info("NLQ clarify intent={} question='{}'", decision.intent, decision.question);
                    return ResponseEntity.ok(resp);
                }
                case EXECUTE -> {
                    if (decision.sql == null || decision.sql.isBlank()) {
                        throw new IllegalStateException("Decision=execute is missing SQL text");
                    }
                    resp.put("sql", decision.sql);
                    resp.put("params", decision.params);

                    boolean doRun = doRunFlag;
                    resp.put("ran", doRun);

                    if (doRun) {
                        log.debug("Executing NLQ plan intent={} sql='{}' params={}",
                                decision.intent,
                                compact(decision.sql),
                                decision.params.isEmpty() ? "none" : decision.params.keySet());
                        
                        Map<String, Object> result = executeWithRepair(
                            req.prompt, 
                            decision.sql, 
                            decision.params, 
                            req.maxRows, 
                            req.timeoutSeconds
                        );
                        
                        resp.put("result", result);
                        
                        // Check if repair was attempted
                        String finalSql = decision.sql;
                        if (result.containsKey("repaired") && Boolean.TRUE.equals(result.get("repaired"))) {
                            resp.put("repaired", true);
                            resp.put("originalSql", decision.sql);
                            resp.put("sql", result.get("repairedSql"));
                            finalSql = (String) result.get("repairedSql");
                        }
                        
                        // Save to history
                        if (useHistory) {
                            historyStore.addTurn(conversationId, ChatTurn.user(req.prompt));
                            String sqlToSave = finalSql.length() > 2000 ? 
                                finalSql.substring(0, 2000) + "..." : finalSql;
                            String assistantMsg = "SQL:\n" + sqlToSave;
                            historyStore.addTurn(conversationId, ChatTurn.assistant(assistantMsg));
                            log.debug("Saved execute turns to conversation '{}'", conversationId);
                        }
                        
                        log.info("NLQ execution complete intent={} rows={} repaired={}",
                                decision.intent,
                                result.getOrDefault("rowCount", "n/a"),
                                result.getOrDefault("repaired", false));
                    } else {
                        log.info("NLQ returning plan intent={} without execution", decision.intent);
                    }

                    return ResponseEntity.ok(resp);
                }
                case REJECT -> {
                    Map<String, Object> reject = new LinkedHashMap<>();
                    if (decision.intent != null) {
                        reject.put("recognizedIntent", decision.intent);
                    }
                    reject.put("decision", decision.decisionKey());
                    if (!decision.missing.isEmpty()) {
                        reject.put("missing", decision.missing);
                    }
                    String message = decision.question != null && !decision.question.isBlank()
                            ? decision.question
                            : "Sorry, I can't help with that request.";
                    reject.put("message", message);
                    reject.put("error", "NLQ_REJECTED");
                    reject.put("ran", false);
                    
                    // Save to history
                    if (useHistory) {
                        historyStore.addTurn(conversationId, ChatTurn.user(req.prompt));
                        String reasons = !decision.missing.isEmpty() ? 
                            String.join(", ", decision.missing) : "out of scope";
                        String assistantMsg = "REJECT: " + reasons;
                        historyStore.addTurn(conversationId, ChatTurn.assistant(assistantMsg));
                        log.debug("Saved reject turns to conversation '{}'", conversationId);
                    }
                    
                    log.info("NLQ reject intent={} message='{}'", decision.intent, message);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(reject);
                }
                default -> throw new IllegalStateException("Unhandled decision type: " + decision.decision);
            }

        } catch (IllegalArgumentException ex) {
            log.warn("NLQ prompt could not be mapped: {}", ex.getMessage());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "UNRECOGNIZED");
            resp.put("message", "I couldn't map that prompt to a known query template.");
            resp.put("try", nlq.suggestions());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception ex) {
            log.error("NLQ provider failure", ex);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "NLQ_FAILED");
            resp.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    private String compact(String sql) {
        if (sql == null) { return ""; }
        String singleLine = sql.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 200 ? singleLine.substring(0, 200) + "…" : singleLine;
    }

    /**
     * Execute SQL with repair logic for Judge-Generator mode.
     * If execution fails and the provider supports repair, attempt one repair.
     */
    private Map<String, Object> executeWithRepair(String originalPrompt, 
                                                   String sql, 
                                                   Map<String, Object> params,
                                                   Integer maxRows, 
                                                   Integer timeoutSeconds) {
        // First attempt
        Map<String, Object> result = query.execute(sql, params, maxRows, timeoutSeconds);
        
        // Check if it failed and if we can repair
        if (result.containsKey("error") && nlq instanceof JudgeGeneratorAzureProvider judgeGen) {
            String errorMsg = String.valueOf(result.get("message"));
            log.warn("SQL execution failed: {}. Attempting repair...", errorMsg);
            
            try {
                RepairResponse repairResp = judgeGen.callRepair(originalPrompt, sql, errorMsg);
                
                if (repairResp.getSql() == null || repairResp.getSql().isBlank()) {
                    log.error("Repair returned empty SQL, returning original error");
                    return result;
                }
                
                String repairedSql = repairResp.getSql().trim();
                Map<String, Object> repairedParams = repairResp.getParams() != null ? 
                    repairResp.getParams() : Map.of();
                
                log.info("Repair produced new SQL (length={}), retrying execution", repairedSql.length());
                
                // Second attempt with repaired SQL
                Map<String, Object> repairedResult = query.execute(repairedSql, repairedParams, maxRows, timeoutSeconds);
                
                if (!repairedResult.containsKey("error")) {
                    // Success! Mark as repaired
                    repairedResult.put("repaired", true);
                    repairedResult.put("repairedSql", repairedSql);
                    repairedResult.put("originalError", errorMsg);
                    log.info("Repair successful! Rows returned: {}", repairedResult.get("rowCount"));
                    return repairedResult;
                } else {
                    // Repair failed too
                    log.error("Repair attempt failed: {}", repairedResult.get("message"));
                    return result; // Return original error
                }
                
            } catch (Exception e) {
                log.error("Repair call failed: {}", e.getMessage(), e);
                return result; // Return original error
            }
        }
        
        return result;
    }

    @DeleteMapping("/conversation/{id}")
    public ResponseEntity<Map<String, Object>> clearConversation(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_ID",
                "message", "Conversation ID cannot be empty"
            ));
        }
        
        historyStore.clearConversation(id);
        log.info("Cleared conversation '{}'", id);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "conversationId", id,
            "message", "Conversation history cleared"
        ));
    }
}
