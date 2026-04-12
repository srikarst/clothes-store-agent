package com.example.clothesstoreagent.simple;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "http://localhost:3000")
public class RagAdminController {

    private final QdrantRagIndexer ragIndexer;

    @Value("${APP_RAG_ADMIN_TOKEN:}")
    private String adminToken = "";

    public RagAdminController(QdrantRagIndexer ragIndexer) {
        this.ragIndexer = ragIndexer;
    }

    @PostMapping("/reindex")
    public ResponseEntity<?> reindex(
            @RequestHeader(value = "X-RAG-ADMIN-TOKEN", required = false) String incomingToken
    ) {
        if (isAuthRequired() && !adminToken.equals(incomingToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Missing or invalid X-RAG-ADMIN-TOKEN"
            ));
        }

        QdrantRagIndexer.IndexingReport report = ragIndexer.indexFromConfiguredSource();
        if (report.ok()) {
            return ResponseEntity.ok(report);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(report);
    }

    private boolean isAuthRequired() {
        return adminToken != null && !adminToken.isBlank();
    }
}
