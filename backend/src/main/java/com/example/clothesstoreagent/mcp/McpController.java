package com.example.clothesstoreagent.mcp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpProps props;
    private final McpServerManager manager;

    public McpController(McpProps props, McpServerManager manager) {
        this.props = props;
        this.manager = manager;
    }

    @GetMapping("/servers")
    public ResponseEntity<Map<String, Object>> servers() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", props != null && props.isEnabled());
        out.put("servers", manager.listServerStatuses());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/servers/{id}/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_ID",
                    "message", "id is required"
            ));
        }
        manager.refresh(id.trim());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", id.trim()
        ));
    }
}


