package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantRagIndexerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void indexFromConfiguredSource_chunksEmbedsAndUpserts() throws Exception {
        Path sourceDir = Files.createTempDirectory("rag-source");
        Files.writeString(
                sourceDir.resolve("returns.md"),
                "one two three four five six seven eight nine",
                StandardCharsets.UTF_8
        );

        AtomicInteger requestCount = new AtomicInteger();
        AtomicInteger deleteCount = new AtomicInteger();
        AtomicReference<String> requestBodyRef = new AtomicReference<>("");
        AtomicReference<String> deleteBodyRef = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/collections/test_collection/points", exchange -> {
            handleUpsertRequest(exchange, requestCount, requestBodyRef);
        });
        server.createContext("/collections/test_collection/points/delete", exchange -> {
            handleDeleteRequest(exchange, requestCount, deleteCount, deleteBodyRef);
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            QdrantRagIndexer indexer = new QdrantRagIndexer(
                    OBJECT_MAPPER,
                    new StubEmbeddingClient(),
                    "http://localhost:" + port,
                    "test_collection",
                    "",
                    3000,
                    sourceDir.toString(),
                    4,
                    1,
                    32,
                    "md,txt",
                    "text",
                    "source",
                    true,
                    true
            );

            QdrantRagIndexer.IndexingReport report = indexer.indexFromConfiguredSource();

            assertTrue(report.ok());
            assertEquals(1, report.docsDiscovered());
            assertEquals(1, report.docsIndexed());
            assertEquals(3, report.chunksCreated());
            assertEquals(3, report.chunksIndexed());
            assertEquals(0, report.embeddingFailures());
            assertEquals(2, requestCount.get());
            assertEquals(1, deleteCount.get());

            JsonNode root = OBJECT_MAPPER.readTree(requestBodyRef.get());
            JsonNode points = root.path("points");
            assertTrue(points.isArray());
            assertEquals(3, points.size());
            assertTrue(points.get(0).path("payload").path("text").asText("").length() > 0);
            assertTrue(points.get(0).path("payload").path("source").asText("").endsWith("returns.md"));
            assertEquals(3, points.get(0).path("vector").size());

            JsonNode deleteRoot = OBJECT_MAPPER.readTree(deleteBodyRef.get());
            assertTrue(deleteRoot.path("filter").path("must").isArray());
            assertEquals("source", deleteRoot.path("filter").path("must").path(0).path("key").asText(""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void indexFromConfiguredSource_returnsFailedWhenNotConfigured() {
        QdrantRagIndexer indexer = new QdrantRagIndexer(
                OBJECT_MAPPER,
                new StubEmbeddingClient(),
                "",
                "",
                "",
                3000,
                "rag-docs",
                400,
                60,
                32,
                "md,txt",
                "text",
                "source",
                true,
                true
        );

        QdrantRagIndexer.IndexingReport report = indexer.indexFromConfiguredSource();
        assertFalse(report.ok());
        assertTrue(report.message().toLowerCase().contains("not configured"));
    }

    private static void handleUpsertRequest(
            HttpExchange exchange,
            AtomicInteger requestCount,
            AtomicReference<String> requestBodyRef
    ) throws IOException {
        requestCount.incrementAndGet();
        requestBodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void handleDeleteRequest(
            HttpExchange exchange,
            AtomicInteger requestCount,
            AtomicInteger deleteCount,
            AtomicReference<String> deleteBodyRef
    ) throws IOException {
        requestCount.incrementAndGet();
        deleteCount.incrementAndGet();
        deleteBodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
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
            return List.of(0.1, 0.2, 0.3);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }
}
