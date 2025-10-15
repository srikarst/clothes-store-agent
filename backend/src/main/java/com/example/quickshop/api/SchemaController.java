package com.example.quickshop.api;

import com.example.quickshop.service.SchemaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SchemaController {
    private final SchemaService schema;

    public SchemaController(SchemaService schema) { this.schema = schema; }

    @GetMapping("/api/schema")
    public Map<String, Object> get() {
        return schema.getSchema();
    }
}
