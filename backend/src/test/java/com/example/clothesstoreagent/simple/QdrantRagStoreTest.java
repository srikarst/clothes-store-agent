package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantRagStoreTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void retrieve_returnsBm25Chunk_whenVectorSearchIsUnavailable() throws Exception {
        Path sourceDir = Files.createTempDirectory("bm25-source");
        Files.writeString(
                sourceDir.resolve("policy.md"),
                "Card refunds usually settle in 5 to 10 business days after approval.",
                StandardCharsets.UTF_8
        );

        QdrantRagStore store = newStore(sourceDir.toString(), "", "");
        List<String> results = store.retrieve("how long does card refund take", 2);

        assertFalse(results.isEmpty());
        assertTrue(results.get(0).contains("policy.md"));
    }

    @Test
    void retrieve_mergesVectorAndBm25Candidates() throws Exception {
        Path sourceDir = Files.createTempDirectory("hybrid-source");
        Files.writeString(
                sourceDir.resolve("shipping.md"),
                "Express shipping usually arrives in 1 to 2 business days for domestic orders.",
                StandardCharsets.UTF_8
        );

        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/collections/test_collection/points/search", exchange -> {
            handleSearchRequest(exchange, requestCount);
        });
        server.start();

        try {
            String qdrantUrl = "http://localhost:" + server.getAddress().getPort();
            QdrantRagStore store = newStore(sourceDir.toString(), qdrantUrl, "test_collection");

            List<String> results = store.retrieve("need express delivery timeline", 2);

            assertEquals(1, requestCount.get());
            assertEquals(2, results.size());
            assertTrue(results.stream().anyMatch(value -> value.contains("shipping.md")));
            assertTrue(results.stream().anyMatch(value -> value.contains("vector-doc")));
        } finally {
            server.stop(0);
        }
    }

    private static void handleSearchRequest(HttpExchange exchange, AtomicInteger requestCount) throws IOException {
        requestCount.incrementAndGet();
        byte[] body = """
                {
                  "result": [
                    {
                      "score": 0.91,
                      "payload": {
                        "text": "Customer support is available from 9am to 6pm on weekdays.",
                        "source": "vector-doc"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static QdrantRagStore newStore(String sourceDir, String qdrantUrl, String collection) {
        return new QdrantRagStore(
                OBJECT_MAPPER,
                new StubEmbeddingClient(),
                qdrantUrl,
                collection,
                "",
                5000,
                3,
                0.0,
                "text",
                "source",
                true,
                true,
                12,
                12,
                8,
                0.45,
                0.25,
                0.30,
                sourceDir,
                "md,txt",
                50,
                10,
                0,
                1.5,
                0.75
        );
    }

    private static class StubEmbeddingClient extends AzureOpenAiEmbeddingClient {

        StubEmbeddingClient() {
            super(OBJECT_MAPPER, "", "", "2024-02-15-preview", "", 1000);
        }

        @Override
        public List<Double> embed(String input) {
            if (input == null || input.isBlank()) {
                return List.of();
            }
            String normalized = input.toLowerCase(Locale.ROOT);
            if (normalized.contains("refund")) {
                return List.of(1.0, 0.0, 0.0);
            }
            if (normalized.contains("express") || normalized.contains("delivery")) {
                return List.of(0.0, 1.0, 0.0);
            }
            return List.of(0.0, 0.0, 1.0);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }
}
