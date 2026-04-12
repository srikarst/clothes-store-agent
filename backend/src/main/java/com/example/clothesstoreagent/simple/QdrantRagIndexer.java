package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class QdrantRagIndexer {

    private final ObjectMapper objectMapper;
    private final AzureOpenAiEmbeddingClient embeddingClient;
    private final HttpClient httpClient;
    private final String qdrantUrl;
    private final String collection;
    private final String apiKey;
    private final long timeoutMs;
    private final String sourceDirectory;
    private final int chunkSizeTokens;
    private final int chunkOverlapTokens;
    private final int ingestBatchSize;
    private final Set<String> allowedExtensions;
    private final String textPayloadKey;
    private final String sourcePayloadKey;
    private final boolean waitForQdrant;
    private final boolean deleteSourceBeforeUpsert;

    public QdrantRagIndexer(
            ObjectMapper objectMapper,
            AzureOpenAiEmbeddingClient embeddingClient,
            @Value("${APP_RAG_QDRANT_URL:}") String qdrantUrl,
            @Value("${APP_RAG_QDRANT_COLLECTION:}") String collection,
            @Value("${APP_RAG_QDRANT_API_KEY:}") String apiKey,
            @Value("${APP_RAG_INGEST_TIMEOUT_MS:10000}") long timeoutMs,
            @Value("${APP_RAG_SOURCE_DIR:rag-docs}") String sourceDirectory,
            @Value("${APP_RAG_CHUNK_SIZE_TOKENS:400}") int chunkSizeTokens,
            @Value("${APP_RAG_CHUNK_OVERLAP_TOKENS:60}") int chunkOverlapTokens,
            @Value("${APP_RAG_INGEST_BATCH_SIZE:32}") int ingestBatchSize,
            @Value("${APP_RAG_SOURCE_EXTENSIONS:md,txt}") String sourceExtensions,
            @Value("${APP_RAG_TEXT_PAYLOAD_KEY:text}") String textPayloadKey,
            @Value("${APP_RAG_SOURCE_PAYLOAD_KEY:source}") String sourcePayloadKey,
            @Value("${APP_RAG_INGEST_WAIT_FOR_QDRANT:true}") boolean waitForQdrant,
            @Value("${APP_RAG_DELETE_SOURCE_BEFORE_UPSERT:true}") boolean deleteSourceBeforeUpsert
    ) {
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.httpClient = HttpClient.newHttpClient();
        this.qdrantUrl = qdrantUrl;
        this.collection = collection;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.sourceDirectory = sourceDirectory;
        this.chunkSizeTokens = Math.max(chunkSizeTokens, 1);
        this.chunkOverlapTokens = Math.max(Math.min(chunkOverlapTokens, this.chunkSizeTokens - 1), 0);
        this.ingestBatchSize = Math.max(ingestBatchSize, 1);
        this.allowedExtensions = parseExtensions(sourceExtensions);
        this.textPayloadKey = textPayloadKey;
        this.sourcePayloadKey = sourcePayloadKey;
        this.waitForQdrant = waitForQdrant;
        this.deleteSourceBeforeUpsert = deleteSourceBeforeUpsert;
    }

    public IndexingReport indexFromConfiguredSource() {
        long startNanos = System.nanoTime();
        if (!isConfigured()) {
            return IndexingReport.failed(
                    "RAG indexer is not configured. Set Qdrant and embedding environment variables.",
                    elapsedMillis(startNanos)
            );
        }

        Path sourceRoot = resolveSourceDirectory(sourceDirectory);
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            return IndexingReport.failed(
                    "RAG source directory not found: " + sourceDirectory,
                    elapsedMillis(startNanos)
            );
        }

        try {
            List<Path> sourceFiles = listSourceFiles(sourceRoot);
            if (sourceFiles.isEmpty()) {
                return IndexingReport.success(
                        "No source files found in: " + sourceRoot,
                        sourceRoot.toString(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        elapsedMillis(startNanos)
                );
            }

            int docsIndexed = 0;
            int chunksCreated = 0;
            int chunksIndexed = 0;
            int embeddingFailures = 0;
            Instant indexedAt = Instant.now();
            List<Map<String, Object>> batch = new ArrayList<>();

            for (Path file : sourceFiles) {
                String sourcePath = normalizePath(sourceRoot.relativize(file));
                if (deleteSourceBeforeUpsert) {
                    deleteSourcePoints(sourcePath);
                }

                String content = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (content.isBlank()) {
                    continue;
                }

                List<String> chunks = chunkText(content, chunkSizeTokens, chunkOverlapTokens);
                if (chunks.isEmpty()) {
                    continue;
                }

                docsIndexed++;
                chunksCreated += chunks.size();

                String docId = stripExtension(sourcePath);
                String updatedAt = Files.getLastModifiedTime(file).toInstant().toString();

                for (int i = 0; i < chunks.size(); i++) {
                    String chunkText = chunks.get(i);
                    List<Double> vector = embeddingClient.embed(chunkText);
                    if (vector.isEmpty()) {
                        embeddingFailures++;
                        continue;
                    }

                    batch.add(buildPoint(
                            sourcePath,
                            docId,
                            chunkText,
                            i,
                            chunks.size(),
                            updatedAt,
                            indexedAt,
                            vector
                    ));
                    chunksIndexed++;

                    if (batch.size() >= ingestBatchSize) {
                        upsertBatch(batch);
                        batch.clear();
                    }
                }
            }

            if (!batch.isEmpty()) {
                upsertBatch(batch);
            }

            String message = "Indexed " + chunksIndexed + " chunks from " + docsIndexed + " documents.";
            return IndexingReport.success(
                    message,
                    sourceRoot.toString(),
                    sourceFiles.size(),
                    docsIndexed,
                    chunksCreated,
                    chunksIndexed,
                    embeddingFailures,
                    elapsedMillis(startNanos)
            );
        } catch (Exception e) {
            return IndexingReport.failed("RAG indexing failed: " + e.getMessage(), elapsedMillis(startNanos));
        }
    }

    public boolean isConfigured() {
        return embeddingClient.isConfigured() && notBlank(qdrantUrl) && notBlank(collection);
    }

    static List<String> chunkText(String text, int chunkSizeTokens, int chunkOverlapTokens) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] tokens = text.replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
                .split("\\s+");

        if (tokens.length == 0) {
            return List.of();
        }

        int safeChunkSize = Math.max(chunkSizeTokens, 1);
        int safeOverlap = Math.max(Math.min(chunkOverlapTokens, safeChunkSize - 1), 0);
        int stride = Math.max(safeChunkSize - safeOverlap, 1);

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < tokens.length) {
            int end = Math.min(start + safeChunkSize, tokens.length);
            StringBuilder builder = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(tokens[i]);
            }
            String chunk = builder.toString().trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= tokens.length) {
                break;
            }
            start += stride;
        }
        return chunks;
    }

    private List<Path> listSourceFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> allowedExtensions.contains(fileExtension(path)))
                    .sorted()
                    .toList();
        }
    }

    private Map<String, Object> buildPoint(
            String sourcePath,
            String docId,
            String chunkText,
            int chunkIndex,
            int chunkCount,
            String updatedAt,
            Instant indexedAt,
            List<Double> vector
    ) {
        String pointId = deterministicId(sourcePath, chunkIndex, chunkText);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(textPayloadKey, chunkText);
        payload.put(sourcePayloadKey, sourcePath);
        payload.put("doc_id", docId);
        payload.put("chunk_index", chunkIndex);
        payload.put("chunk_count", chunkCount);
        payload.put("updated_at", updatedAt);
        payload.put("indexed_at", indexedAt.toString());

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", pointId);
        point.put("vector", vector);
        point.put("payload", payload);
        return point;
    }

    private void upsertBatch(List<Map<String, Object>> points) throws IOException, InterruptedException {
        if (points == null || points.isEmpty()) {
            return;
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("points", points);

        String url = qdrantUrl.replaceAll("/+$", "") + "/collections/" + collection + "/points";
        if (waitForQdrant) {
            url += "?wait=true";
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));

        if (notBlank(apiKey)) {
            requestBuilder.header("api-key", apiKey);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Qdrant upsert failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    private void deleteSourcePoints(String sourcePath) throws IOException, InterruptedException {
        if (sourcePath == null || sourcePath.isBlank()) {
            return;
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("value", sourcePath);
        Map<String, Object> must = new LinkedHashMap<>();
        must.put("key", sourcePayloadKey);
        must.put("match", match);
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", List.of(must));
        requestBody.put("filter", filter);

        String url = qdrantUrl.replaceAll("/+$", "") + "/collections/" + collection + "/points/delete";
        if (waitForQdrant) {
            url += "?wait=true";
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));

        if (notBlank(apiKey)) {
            requestBuilder.header("api-key", apiKey);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Qdrant source delete failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    private static Set<String> parseExtensions(String value) {
        if (value == null || value.isBlank()) {
            return Set.of("md", "txt");
        }
        String[] raw = value.split(",");
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String entry : raw) {
            String normalized = entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith(".")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        if (out.isEmpty()) {
            return Set.of("md", "txt");
        }
        return Set.copyOf(out);
    }

    private static Path resolveSourceDirectory(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path configured = Paths.get(configuredPath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Path fromCwd = Paths.get("").toAbsolutePath().resolve(configured).normalize();
        if (Files.exists(fromCwd)) {
            return fromCwd;
        }
        Path fromParent = Paths.get("").toAbsolutePath().resolve("..").resolve(configured).normalize();
        if (Files.exists(fromParent)) {
            return fromParent;
        }
        return fromCwd;
    }

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String stripExtension(String value) {
        int idx = value.lastIndexOf('.');
        if (idx <= 0) {
            return value;
        }
        return value.substring(0, idx);
    }

    private static String deterministicId(String sourcePath, int chunkIndex, String chunkText) {
        String seed = sourcePath + "#" + chunkIndex + "#" + chunkText;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String fileExtension(Path path) {
        String name = path.getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public record IndexingReport(
            boolean ok,
            String message,
            String sourceDirectory,
            int docsDiscovered,
            int docsIndexed,
            int chunksCreated,
            int chunksIndexed,
            int embeddingFailures,
            long latencyMs
    ) {
        static IndexingReport success(
                String message,
                String sourceDirectory,
                int docsDiscovered,
                int docsIndexed,
                int chunksCreated,
                int chunksIndexed,
                int embeddingFailures,
                long latencyMs
        ) {
            return new IndexingReport(
                    true,
                    message,
                    sourceDirectory,
                    docsDiscovered,
                    docsIndexed,
                    chunksCreated,
                    chunksIndexed,
                    embeddingFailures,
                    latencyMs
            );
        }

        static IndexingReport failed(String message, long latencyMs) {
            return new IndexingReport(
                    false,
                    message,
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    latencyMs
            );
        }
    }
}
