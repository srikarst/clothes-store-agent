package com.example.clothesstoreagent.nlq.judge;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Strict JSON response from the Judge agent.
 * Expected schema:
 * {
 *   "decision": "proceed" | "clarify" | "reject",
 *   "missing": ["field1", "field2"],
 *   "reasons": ["reason1", "reason2"],
 *   "question": "clarification or rejection message"
 * }
 */
public class JudgeResponse {
    
    @JsonProperty("decision")
    private String decision;
    
    @JsonProperty("missing")
    private List<String> missing;
    
    @JsonProperty("reasons")
    private List<String> reasons;
    
    @JsonProperty("question")
    private String question;

    public JudgeResponse() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public List<String> getMissing() {
        return missing;
    }

    public void setMissing(List<String> missing) {
        this.missing = missing;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
