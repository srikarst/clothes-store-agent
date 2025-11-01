package com.example.clothesstoreagent.nlq.judge;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Strict JSON response from the Repair agent.
 * Expected schema:
 * {
 *   "sql": "SELECT ...",
 *   "params": {"param1": "value1", ...}
 * }
 */
public class RepairResponse {
    
    @JsonProperty("sql")
    private String sql;
    
    @JsonProperty("params")
    private Map<String, Object> params;

    public RepairResponse() {
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
