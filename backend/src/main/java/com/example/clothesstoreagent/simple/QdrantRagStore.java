package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class QdrantRagStore {

    private static final List<String> DEFAULT_TEXT_KEYS = List.of("text", "chunk", "content", "page_content", "body");
    private static final List<String> DEFAULT_SOURCE_KEYS = List.of("source", "source_id", "doc_id", "document_id", "url", "path");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from",
            "your", "you", "are", "into", "about", "what", "how",
            "when", "where", "after", "before", "would", "could",
            "should", "have", "has", "had", "was", "were", "they",
            "them", "their", "our", "ours", "its", "it's", "can"
    );

    private final ObjectMapper objectMapper;
    private final AzureOpenAiEmbeddingClient embeddingClient;
    private final HttpClient httpClient;
    private final String qdrantUrl;
    private final String collection;
    private final String apiKey;
    private final long timeoutMs;
    private final int defaultTopK;
    private final double scoreThreshold;
    private final String textPayloadKey;
    private final String sourcePayloadKey;
    private final boolean hybridEnabled;
    private final boolean rerankEnabled;
    private final int vectorCandidates;
    private final int bm25Candidates;
    private final int rerankCandidates;
    private final double rerankWeightVector;
    private final double rerankWeightBm25;
    private final double rerankWeightSemantic;
    private final String sourceDirectory;
    private final Set<String> sourceExtensions;
    private final int chunkSizeTokens;
    private final int chunkOverlapTokens;
    private final long bm25RefreshMs;
    private final double bm25K1;
    private final double bm25B;

    private volatile LexicalSnapshot lexicalSnapshot = LexicalSnapshot.empty();
    private volatile long lexicalSnapshotLoadedAtMs = 0;

    public QdrantRagStore(
            ObjectMapper objectMapper,
            AzureOpenAiEmbeddingClient embeddingClient,
            @Value("${APP_RAG_QDRANT_URL:}") String qdrantUrl,
            @Value("${APP_RAG_QDRANT_COLLECTION:}") String collection,
            @Value("${APP_RAG_QDRANT_API_KEY:}") String apiKey,
            @Value("${APP_RAG_TIMEOUT_MS:5000}") long timeoutMs,
            @Value("${APP_RAG_TOP_K:3}") int defaultTopK,
            @Value("${APP_RAG_SCORE_THRESHOLD:0.25}") double scoreThreshold,
            @Value("${APP_RAG_TEXT_PAYLOAD_KEY:text}") String textPayloadKey,
            @Value("${APP_RAG_SOURCE_PAYLOAD_KEY:source}") String sourcePayloadKey,
            @Value("${APP_RAG_HYBRID_ENABLED:true}") boolean hybridEnabled,
            @Value("${APP_RAG_RERANK_ENABLED:true}") boolean rerankEnabled,
            @Value("${APP_RAG_VECTOR_CANDIDATES:12}") int vectorCandidates,
            @Value("${APP_RAG_BM25_CANDIDATES:12}") int bm25Candidates,
            @Value("${APP_RAG_RERANK_CANDIDATES:8}") int rerankCandidates,
            @Value("${APP_RAG_RERANK_WEIGHT_VECTOR:0.45}") double rerankWeightVector,
            @Value("${APP_RAG_RERANK_WEIGHT_BM25:0.25}") double rerankWeightBm25,
            @Value("${APP_RAG_RERANK_WEIGHT_SEMANTIC:0.30}") double rerankWeightSemantic,
            @Value("${APP_RAG_SOURCE_DIR:rag-docs}") String sourceDirectory,
            @Value("${APP_RAG_SOURCE_EXTENSIONS:md,txt}") String sourceExtensions,
            @Value("${APP_RAG_CHUNK_SIZE_TOKENS:400}") int chunkSizeTokens,
            @Value("${APP_RAG_CHUNK_OVERLAP_TOKENS:60}") int chunkOverlapTokens,
            @Value("${APP_RAG_BM25_REFRESH_MS:60000}") long bm25RefreshMs,
            @Value("${APP_RAG_BM25_K1:1.5}") double bm25K1,
            @Value("${APP_RAG_BM25_B:0.75}") double bm25B
    ) {
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.httpClient = HttpClient.newHttpClient();
        this.qdrantUrl = qdrantUrl;
        this.collection = collection;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.defaultTopK = Math.max(defaultTopK, 1);
        this.scoreThreshold = scoreThreshold;
        this.textPayloadKey = textPayloadKey;
        this.sourcePayloadKey = sourcePayloadKey;
        this.hybridEnabled = hybridEnabled;
        this.rerankEnabled = rerankEnabled;
        this.vectorCandidates = Math.max(vectorCandidates, 1);
        this.bm25Candidates = Math.max(bm25Candidates, 1);
        this.rerankCandidates = Math.max(rerankCandidates, 1);
        this.rerankWeightVector = Math.max(rerankWeightVector, 0.0);
        this.rerankWeightBm25 = Math.max(rerankWeightBm25, 0.0);
        this.rerankWeightSemantic = Math.max(rerankWeightSemantic, 0.0);
        this.sourceDirectory = sourceDirectory;
        this.sourceExtensions = parseExtensions(sourceExtensions);
        this.chunkSizeTokens = Math.max(chunkSizeTokens, 1);
        this.chunkOverlapTokens = Math.max(Math.min(chunkOverlapTokens, this.chunkSizeTokens - 1), 0);
        this.bm25RefreshMs = Math.max(bm25RefreshMs, 0);
        this.bm25K1 = Math.max(bm25K1, 0.01);
        this.bm25B = Math.max(Math.min(bm25B, 1.0), 0.0);
    }

    public List<String> retrieve(String query, int topK) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isBlank()) {
            return List.of();
        }

        int effectiveTopK = topK > 0 ? topK : defaultTopK;
        int vectorLimit = Math.max(effectiveTopK, vectorCandidates);
        int bm25Limit = Math.max(effectiveTopK, bm25Candidates);

        List<Double> queryVector = embeddingClient.embed(cleanQuery);
        List<ScoredChunk> vectorMatches = searchQdrant(queryVector, vectorLimit);
        List<ScoredChunk> bm25Matches = hybridEnabled
                ? searchBm25(cleanQuery, bm25Limit)
                : List.of();

        List<ScoredChunk> rankedChunks = mergeAndRank(queryVector, vectorMatches, bm25Matches);
        if (rankedChunks.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueChunks = new LinkedHashSet<>();
        for (ScoredChunk chunk : rankedChunks) {
            uniqueChunks.add(formatChunk(chunk));
            if (uniqueChunks.size() >= effectiveTopK) {
                break;
            }
        }
        return new ArrayList<>(uniqueChunks);
    }

    public boolean isConfigured() {
        return embeddingClient.isConfigured() && notBlank(qdrantUrl) && notBlank(collection);
    }

    private List<ScoredChunk> mergeAndRank(
            List<Double> queryVector,
            List<ScoredChunk> vectorMatches,
            List<ScoredChunk> bm25Matches
    ) {
        Map<String, ScoredChunk> merged = new LinkedHashMap<>();

        for (ScoredChunk chunk : vectorMatches) {
            merged.put(candidateKey(chunk.source(), chunk.text()), chunk);
        }

        for (ScoredChunk bm25 : bm25Matches) {
            String key = candidateKey(bm25.source(), bm25.text());
            ScoredChunk existing = merged.get(key);
            if (existing == null) {
                merged.put(key, bm25);
            } else {
                merged.put(key, existing.withBm25Score(Math.max(existing.bm25Score(), bm25.bm25Score())));
            }
        }

        if (merged.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> blended = applyWeightedRanking(new ArrayList<>(merged.values()), false);
        if (!rerankEnabled || queryVector == null || queryVector.isEmpty()) {
            return blended;
        }

        Map<String, Double> semanticScores = computeSemanticScores(queryVector, blended);
        if (semanticScores.isEmpty()) {
            return blended;
        }

        List<ScoredChunk> withSemantic = new ArrayList<>(blended.size());
        for (ScoredChunk chunk : blended) {
            String key = candidateKey(chunk.source(), chunk.text());
            double semanticScore = semanticScores.getOrDefault(key, 0.0);
            withSemantic.add(chunk.withSemanticScore(semanticScore));
        }
        return applyWeightedRanking(withSemantic, true);
    }

    private Map<String, Double> computeSemanticScores(List<Double> queryVector, List<ScoredChunk> rankedCandidates) {
        int limit = Math.min(rerankCandidates, rankedCandidates.size());
        if (limit <= 0) {
            return Map.of();
        }

        Map<String, Double> semanticScores = new LinkedHashMap<>();
        for (int i = 0; i < limit; i++) {
            ScoredChunk chunk = rankedCandidates.get(i);
            List<Double> chunkVector = embeddingClient.embed(chunk.text());
            if (chunkVector.isEmpty()) {
                continue;
            }
            semanticScores.put(candidateKey(chunk.source(), chunk.text()), cosineSimilarity(queryVector, chunkVector));
        }
        return semanticScores;
    }

    private List<ScoredChunk> applyWeightedRanking(List<ScoredChunk> candidates, boolean includeSemantic) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        double minVector = Double.MAX_VALUE;
        double maxVector = -Double.MAX_VALUE;
        double minBm25 = Double.MAX_VALUE;
        double maxBm25 = -Double.MAX_VALUE;
        double minSemantic = Double.MAX_VALUE;
        double maxSemantic = -Double.MAX_VALUE;

        for (ScoredChunk chunk : candidates) {
            minVector = Math.min(minVector, chunk.vectorScore());
            maxVector = Math.max(maxVector, chunk.vectorScore());
            minBm25 = Math.min(minBm25, chunk.bm25Score());
            maxBm25 = Math.max(maxBm25, chunk.bm25Score());
            minSemantic = Math.min(minSemantic, chunk.semanticScore());
            maxSemantic = Math.max(maxSemantic, chunk.semanticScore());
        }

        double semanticWeight = includeSemantic ? rerankWeightSemantic : 0.0;
        double totalWeight = rerankWeightVector + rerankWeightBm25 + semanticWeight;
        if (totalWeight <= 0) {
            totalWeight = 1.0;
        }

        List<ScoredChunk> ranked = new ArrayList<>(candidates.size());
        for (ScoredChunk chunk : candidates) {
            double normalizedVector = normalizeScore(chunk.vectorScore(), minVector, maxVector);
            double normalizedBm25 = normalizeScore(chunk.bm25Score(), minBm25, maxBm25);
            double normalizedSemantic = includeSemantic
                    ? normalizeScore(chunk.semanticScore(), minSemantic, maxSemantic)
                    : 0.0;
            double finalScore = (
                    rerankWeightVector * normalizedVector
                            + rerankWeightBm25 * normalizedBm25
                            + semanticWeight * normalizedSemantic
            ) / totalWeight;
            ranked.add(chunk.withFinalScore(finalScore));
        }

        ranked.sort(Comparator
                .comparingDouble(ScoredChunk::finalScore).reversed()
                .thenComparingDouble(ScoredChunk::vectorScore).reversed()
                .thenComparingDouble(ScoredChunk::bm25Score).reversed());
        return ranked;
    }

    private List<ScoredChunk> searchQdrant(List<Double> vector, int topK) {
        if (vector == null || vector.isEmpty() || !notBlank(qdrantUrl) || !notBlank(collection)) {
            return List.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vector", vector);
        payload.put("limit", topK);
        payload.put("with_payload", true);
        payload.put("with_vector", false);
        if (scoreThreshold > 0) {
            payload.put("score_threshold", scoreThreshold);
        }

        String url = qdrantUrl.replaceAll("/+$", "")
                + "/collections/" + collection + "/points/search";

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

            if (notBlank(apiKey)) {
                requestBuilder.header("api-key", apiKey);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            return extractChunks(root.path("result"));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<ScoredChunk> searchBm25(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        LexicalSnapshot snapshot = getLexicalSnapshot();
        if (snapshot.chunks().isEmpty()) {
            return List.of();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueQueryTerms = new LinkedHashSet<>(queryTokens);
        int docCount = snapshot.chunks().size();
        double avgLength = Math.max(snapshot.averageDocLength(), 1.0);

        List<ScoredChunk> scored = new ArrayList<>();
        for (LexicalChunk chunk : snapshot.chunks()) {
            double score = 0.0;
            for (String term : uniqueQueryTerms) {
                int tf = chunk.termFrequency().getOrDefault(term, 0);
                if (tf <= 0) {
                    continue;
                }
                int df = snapshot.documentFrequency().getOrDefault(term, 0);
                if (df <= 0) {
                    continue;
                }
                double idf = Math.log(1.0 + ((docCount - df + 0.5) / (df + 0.5)));
                double lengthNorm = 1.0 - bm25B + bm25B * (chunk.length() / avgLength);
                double numerator = tf * (bm25K1 + 1.0);
                double denominator = tf + bm25K1 * lengthNorm;
                score += idf * (numerator / denominator);
            }
            if (score > 0) {
                scored.add(new ScoredChunk(chunk.text(), chunk.source(), 0.0, score, 0.0, 0.0));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::bm25Score).reversed());
        return scored.size() <= topK ? scored : new ArrayList<>(scored.subList(0, topK));
    }

    private synchronized LexicalSnapshot getLexicalSnapshot() {
        long now = System.currentTimeMillis();
        if (lexicalSnapshot != null
                && !lexicalSnapshot.chunks().isEmpty()
                && now - lexicalSnapshotLoadedAtMs < bm25RefreshMs) {
            return lexicalSnapshot;
        }
        lexicalSnapshot = buildLexicalSnapshot();
        lexicalSnapshotLoadedAtMs = now;
        return lexicalSnapshot;
    }

    private LexicalSnapshot buildLexicalSnapshot() {
        Path sourceRoot = resolveSourceDirectory(sourceDirectory);
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            return LexicalSnapshot.empty();
        }

        List<LexicalChunk> chunks = new ArrayList<>();
        Map<String, Integer> documentFrequency = new LinkedHashMap<>();

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> sourceExtensions.contains(fileExtension(path)))
                    .sorted()
                    .toList();

            for (Path file : files) {
                String content = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (content.isBlank()) {
                    continue;
                }

                String source = normalizePath(sourceRoot.relativize(file));
                List<String> docChunks = QdrantRagIndexer.chunkText(content, chunkSizeTokens, chunkOverlapTokens);
                for (String chunkText : docChunks) {
                    List<String> tokens = tokenize(chunkText);
                    if (tokens.isEmpty()) {
                        continue;
                    }

                    Map<String, Integer> termFrequency = termFrequency(tokens);
                    chunks.add(new LexicalChunk(chunkText, source, termFrequency, tokens.size()));

                    for (String term : termFrequency.keySet()) {
                        int current = documentFrequency.getOrDefault(term, 0);
                        documentFrequency.put(term, current + 1);
                    }
                }
            }
        } catch (IOException ignored) {
            return LexicalSnapshot.empty();
        }

        if (chunks.isEmpty()) {
            return LexicalSnapshot.empty();
        }

        double totalLength = 0.0;
        for (LexicalChunk chunk : chunks) {
            totalLength += chunk.length();
        }
        double averageLength = totalLength / chunks.size();
        return new LexicalSnapshot(chunks, documentFrequency, averageLength);
    }

    private List<ScoredChunk> extractChunks(JsonNode resultNode) {
        JsonNode hitsNode = resultNode;
        if (resultNode != null && resultNode.isObject() && resultNode.path("points").isArray()) {
            hitsNode = resultNode.path("points");
        }
        if (hitsNode == null || !hitsNode.isArray()) {
            return List.of();
        }

        List<ScoredChunk> chunks = new ArrayList<>();
        for (JsonNode hit : hitsNode) {
            JsonNode payloadNode = hit.path("payload");
            String text = selectPayloadField(payloadNode, textPayloadKey, DEFAULT_TEXT_KEYS);
            if (text.isBlank()) {
                continue;
            }
            String source = selectPayloadField(payloadNode, sourcePayloadKey, DEFAULT_SOURCE_KEYS);
            chunks.add(new ScoredChunk(text, source, hit.path("score").asDouble(0.0), 0.0, 0.0, 0.0));
        }
        return chunks;
    }

    private static String selectPayloadField(JsonNode payloadNode, String preferredKey, List<String> fallbackKeys) {
        if (payloadNode == null || !payloadNode.isObject()) {
            return "";
        }

        if (notBlank(preferredKey)) {
            String preferred = payloadAsText(payloadNode.get(preferredKey));
            if (!preferred.isBlank()) {
                return preferred;
            }
        }

        for (String key : fallbackKeys) {
            String value = payloadAsText(payloadNode.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String payloadAsText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("").trim();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText("").trim();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode entry : node) {
                String text = payloadAsText(entry);
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return String.join(", ", values).trim();
        }
        return node.toString().trim();
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String[] raw = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : raw) {
            String canonical = canonicalToken(token);
            if (canonical.length() >= 3 && !STOP_WORDS.contains(canonical)) {
                tokens.add(canonical);
            }
        }
        return tokens;
    }

    private static String canonicalToken(String token) {
        if (token == null) {
            return "";
        }
        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String token : tokens) {
            int current = frequencies.getOrDefault(token, 0);
            frequencies.put(token, current + 1);
        }
        return frequencies;
    }

    private static double normalizeScore(double value, double min, double max) {
        if (Double.compare(max, min) == 0) {
            return max > 0 ? 1.0 : 0.0;
        }
        return (value - min) / (max - min);
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int dimensions = Math.min(left.size(), right.size());
        if (dimensions == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < dimensions; i++) {
            double l = left.get(i);
            double r = right.get(i);
            dotProduct += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String formatChunk(ScoredChunk chunk) {
        if (chunk.source().isBlank()) {
            return chunk.text().trim();
        }
        return "[" + chunk.source().trim() + "] " + chunk.text().trim();
    }

    private static String candidateKey(String source, String text) {
        return (source == null ? "" : source) + "||" + (text == null ? "" : text);
    }

    private static Set<String> parseExtensions(String value) {
        if (value == null || value.isBlank()) {
            return Set.of("md", "txt");
        }
        String[] raw = value.split(",");
        Set<String> out = new LinkedHashSet<>();
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

    private record LexicalChunk(
            String text,
            String source,
            Map<String, Integer> termFrequency,
            int length
    ) {
    }

    private record LexicalSnapshot(
            List<LexicalChunk> chunks,
            Map<String, Integer> documentFrequency,
            double averageDocLength
    ) {
        static LexicalSnapshot empty() {
            return new LexicalSnapshot(List.of(), Map.of(), 0.0);
        }
    }

    private record ScoredChunk(
            String text,
            String source,
            double vectorScore,
            double bm25Score,
            double semanticScore,
            double finalScore
    ) {
        ScoredChunk withBm25Score(double score) {
            return new ScoredChunk(text, source, vectorScore, score, semanticScore, finalScore);
        }

        ScoredChunk withSemanticScore(double score) {
            return new ScoredChunk(text, source, vectorScore, bm25Score, score, finalScore);
        }

        ScoredChunk withFinalScore(double score) {
            return new ScoredChunk(text, source, vectorScore, bm25Score, semanticScore, score);
        }
    }
}
