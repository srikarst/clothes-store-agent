package com.example.quickshop.api;

import com.example.quickshop.service.QueryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/query")
@Validated
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    public static class QueryRequest {
        @NotBlank
        public String sql;
        public Map<String, Object> params;
        public Integer maxRows;
        public Integer timeoutSeconds;
    }

    @PostMapping
    public Map<String, Object> run(@RequestBody QueryRequest req) {
        Map<String, Object> safeParams = req.params != null ? req.params : new LinkedHashMap<>();
        return queryService.execute(req.sql, safeParams, req.maxRows, req.timeoutSeconds);
    }
}
