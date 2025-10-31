package com.example.clothesstoreagent.api;

import com.example.clothesstoreagent.nlq.NlqProvider;
import com.example.clothesstoreagent.service.QueryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/nlq")
@Validated
public class NlqController {

    private static final Logger log = LoggerFactory.getLogger(NlqController.class);

    private final NlqProvider nlq;
    private final QueryService query;

    public NlqController(NlqProvider nlq, QueryService query) {
        this.nlq = nlq;
        this.query = query;
    }

    public static class NlqRequest {
        @NotBlank public String prompt;
        public Boolean execute;
        public Integer maxRows;
        public Integer timeoutSeconds;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(@RequestBody NlqRequest req) {
        try {
            String preview = req.prompt != null && req.prompt.length() > 160
                    ? req.prompt.substring(0, 160) + "…"
                    : req.prompt;
            boolean doRunFlag = req.execute == null || Boolean.TRUE.equals(req.execute);
            log.info("NLQ reques execute={} prompt='{}'", doRunFlag, preview);

            NlqProvider.Decision decision = nlq.compile(req.prompt);

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
                        Map<String, Object> result = query.execute(decision.sql, decision.params, req.maxRows, req.timeoutSeconds);
                        resp.put("result", result);
                        log.info("NLQ execution complete intent={} rows={}",
                                decision.intent,
                                result.getOrDefault("rowCount", "n/a"));
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
}
