package com.example.quickshop.api;

import com.example.quickshop.nlq.NlqProvider;
import com.example.quickshop.service.QueryService;
import jakarta.validation.constraints.NotBlank;
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

    private final NlqProvider nlq;   // <-- interface
    private final QueryService query;

    public NlqController(NlqProvider nlq, QueryService query) {
        this.nlq = nlq;
        this.query = query;
    }

    public static class NlqRequest {
        @NotBlank public String prompt;
        public Boolean execute;         // default true
        public Integer maxRows;
        public Integer timeoutSeconds;
    }

    @PostMapping
    public Map<String, Object> handle(@RequestBody NlqRequest req) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            String preview = req.prompt != null && req.prompt.length() > 160
                    ? req.prompt.substring(0, 160) + "…"
                    : req.prompt;
            boolean doRunFlag = req.execute == null || Boolean.TRUE.equals(req.execute);
            log.info("NLQ request execute={} prompt='{}'", doRunFlag, preview);

            NlqProvider.Plan plan = nlq.compile(req.prompt);
            resp.put("recognizedIntent", plan.intent);
            resp.put("sql", plan.sql);
            resp.put("params", plan.params);

            boolean doRun = doRunFlag;
            resp.put("ran", doRun);

            if (doRun) {
                log.debug("Executing NLQ plan intent={} sql='{}' params={}",
                        plan.intent,
                        compact(plan.sql),
                        plan.params != null ? plan.params.keySet() : "none");
                Map<String, Object> result = query.execute(plan.sql, plan.params, req.maxRows, req.timeoutSeconds);
                resp.put("result", result);
                log.info("NLQ execution complete intent={} rows={}",
                        plan.intent,
                        result.getOrDefault("rowCount", "n/a"));
            } else {
                log.info("NLQ returning plan intent={} without execution", plan.intent);
            }
            return resp;

        } catch (IllegalArgumentException ex) {
            log.warn("NLQ prompt could not be mapped: {}", ex.getMessage());
            resp.put("error", "UNRECOGNIZED");
            resp.put("message", "I couldn't map that prompt to a known query template.");
            resp.put("try", nlq.suggestions());
            return resp;
        } catch (Exception ex) {
            log.error("NLQ provider failure", ex);
            resp.put("error", "NLQ_FAILED");
            resp.put("message", ex.getMessage());
            return resp;
        }
    }

    private String compact(String sql) {
        if (sql == null) { return ""; }
        String singleLine = sql.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 200 ? singleLine.substring(0, 200) + "…" : singleLine;
    }
}
