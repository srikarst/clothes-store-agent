package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local MCP transport using JSON-RPC over stdio (one JSON object per line).
 */
public class StdioMcpTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpTransport.class);

    private final String serverId;
    private final List<String> command;
    private final Map<String, String> env;
    private final ObjectMapper om;

    private final Object lifecycleLock = new Object();
    private final Object writeLock = new Object();

    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    private volatile Process process;
    private volatile BufferedWriter stdin;
    private volatile Thread stdoutThread;
    private volatile Thread stderrThread;
    private volatile boolean stopping;

    public StdioMcpTransport(String serverId,
                            String command,
                            List<String> args,
                            Map<String, String> env,
                            ObjectMapper om) {
        this.serverId = serverId != null ? serverId : "mcp";
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is required for MCP stdio transport");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        if (args != null) cmd.addAll(args.stream().filter(Objects::nonNull).toList());
        this.command = List.copyOf(cmd);
        this.env = env != null ? Map.copyOf(env) : Map.of();
        // Important: MCP stdio framing is newline-delimited JSON (one JSON-RPC object per line).
        // The app's global ObjectMapper may be configured with INDENT_OUTPUT=true, which would emit multi-line JSON
        // and break framing. Use a compact mapper for MCP transport IO.
        this.om = (om != null ? om.copy() : new ObjectMapper())
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (stopping) return;
            if (isAlive(process)) return;
            spawnProcess();
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            stopping = true;
            failAllPending("Transport stopped");
            destroyProcess();

            Thread out = stdoutThread;
            Thread err = stderrThread;
            stdoutThread = null;
            stderrThread = null;
            if (out != null) out.interrupt();
            if (err != null) err.interrupt();
        }
    }

    @Override
    public JsonNode request(String method, JsonNode params, Duration timeout) {
        return requestInternal(method, params, timeout, true);
    }

    @Override
    public void notify(String method, JsonNode params) {
        if (method == null || method.isBlank()) return;
        start();
        ensureAliveOrThrow(method);

        ObjectNode req = om.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        if (params != null && !params.isNull()) {
            req.set("params", params);
        }
        writeLine(req, method);
    }

    private JsonNode requestInternal(String method, JsonNode params, Duration timeout, boolean allowRetry) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method is required");
        }
        Duration t = timeout != null ? timeout : Duration.ofSeconds(20);
        start();

        Long id = null;
        try {
            ensureAliveOrThrow(method);
            id = nextId.getAndIncrement();
            CompletableFuture<JsonNode> fut = new CompletableFuture<>();
            pending.put(id, fut);

            ObjectNode req = om.createObjectNode();
            req.put("jsonrpc", "2.0");
            req.put("id", id);
            req.put("method", method);
            if (params != null && !params.isNull()) {
                req.set("params", params);
            }
            writeLine(req, method);

            JsonNode resp;
            try {
                resp = fut.get(Math.max(1, t.toMillis()), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                pending.remove(id);
                throw new McpRequestTimeoutException("MCP request timed out serverId=" + serverId + " method=" + method);
            }

            JsonNode error = resp != null ? resp.get("error") : null;
            if (error != null && !error.isNull()) {
                Integer code = error.has("code") && error.get("code").isNumber() ? error.get("code").intValue() : null;
                String msg = error.has("message") ? error.get("message").asText() : null;
                JsonNode data = error.get("data");
                throw new McpRpcException(method, code, msg, data);
            }
            JsonNode result = resp != null ? resp.get("result") : null;
            return result != null ? result : om.nullNode();
        } catch (McpRpcException e) {
            throw e;
        } catch (Exception e) {
            if (id != null) {
                pending.remove(id);
            }
            if (allowRetry && shouldRetryTransportError(e)) {
                log.warn("MCP stdio transport retry serverId={} method={} error={}", serverId, method, e.toString());
                restart();
                return requestInternal(method, params, timeout, false);
            }
            if (e instanceof McpTransportException mte) throw mte;
            throw new McpTransportException("MCP request failed serverId=" + serverId + " method=" + method, e);
        }
    }

    private void writeLine(ObjectNode req, String methodForLogs) {
        String json;
        try {
            json = om.writeValueAsString(req);
        } catch (Exception e) {
            throw new McpTransportException("Failed to serialize JSON-RPC request method=" + methodForLogs, e);
        }

        synchronized (writeLock) {
            BufferedWriter w = this.stdin;
            if (w == null) {
                throw new McpTransportException("MCP transport not started (stdin is null) serverId=" + serverId);
            }
            try {
                w.write(json);
                w.write("\n");
                w.flush();
            } catch (IOException ioe) {
                throw new McpTransportException("Failed writing to MCP server stdin serverId=" + serverId, ioe);
            }
        }
    }

    private void spawnProcess() {
        destroyProcess();

        ProcessBuilder pb = new ProcessBuilder(command);
        if (!env.isEmpty()) {
            pb.environment().putAll(env);
        }
        pb.redirectErrorStream(false);

        try {
            Process p = pb.start();
            this.process = p;
            this.stdin = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));

            startStdoutReader(p);
            startStderrReader(p);

            log.info("Started MCP stdio serverId={} cmd={}", serverId, String.join(" ", command));
        } catch (IOException e) {
            throw new McpTransportException("Failed to start MCP stdio process serverId=" + serverId, e);
        }
    }

    private void startStdoutReader(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!stopping && (line = br.readLine()) != null) {
                    handleStdoutLine(line);
                }
            } catch (Exception e) {
                if (!stopping) {
                    log.warn("MCP stdout reader crashed serverId={} error={}", serverId, e.toString());
                }
            } finally {
                if (!stopping) {
                    failAllPending("MCP server stdout closed serverId=" + serverId);
                }
            }
        }, "mcp-stdio-stdout-" + serverId);
        t.setDaemon(true);
        t.start();
        this.stdoutThread = t;
    }

    private void startStderrReader(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!stopping && (line = br.readLine()) != null) {
                    // Avoid logging secrets; assume demo servers log only non-sensitive info.
                    log.debug("MCP[{}] stderr: {}", serverId, line);
                }
            } catch (Exception e) {
                if (!stopping) {
                    log.debug("MCP stderr reader ended serverId={} error={}", serverId, e.toString());
                }
            }
        }, "mcp-stdio-stderr-" + serverId);
        t.setDaemon(true);
        t.start();
        this.stderrThread = t;
    }

    private void handleStdoutLine(String line) {
        if (line == null || line.isBlank()) return;

        JsonNode node;
        try {
            node = om.readTree(line);
        } catch (Exception e) {
            // Some servers may write logs to stdout; ignore non-JSON lines.
            log.debug("MCP[{}] non-json stdout line ignored: {}", serverId, line);
            return;
        }

        JsonNode idNode = node.get("id");
        if (idNode == null || idNode.isNull()) {
            // Notification or log-like message.
            return;
        }

        Long id = asLongId(idNode);
        if (id == null) {
            log.debug("MCP[{}] response with non-numeric id ignored: {}", serverId, idNode);
            return;
        }

        CompletableFuture<JsonNode> fut = pending.remove(id);
        if (fut != null) {
            fut.complete(node);
        }
    }

    private static Long asLongId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) return null;
        if (idNode.isIntegralNumber()) return idNode.longValue();
        if (idNode.isTextual()) {
            try {
                return Long.parseLong(idNode.asText().trim());
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private void failAllPending(String reason) {
        McpTransportException ex = new McpTransportException(reason);
        for (Map.Entry<Long, CompletableFuture<JsonNode>> e : pending.entrySet()) {
            CompletableFuture<JsonNode> fut = pending.remove(e.getKey());
            if (fut != null) {
                fut.completeExceptionally(ex);
            }
        }
    }

    private void destroyProcess() {
        Process p = this.process;
        this.process = null;
        this.stdin = null;

        if (p != null) {
            try {
                p.destroy();
                if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                }
            } catch (Exception ignore) {
                try {
                    p.destroyForcibly();
                } catch (Exception ignore2) {
                    // ignore
                }
            }
        }
    }

    private void restart() {
        synchronized (lifecycleLock) {
            if (stopping) return;
            spawnProcess();
        }
    }

    private void ensureAliveOrThrow(String method) {
        Process p = this.process;
        if (!isAlive(p)) {
            throw new McpTransportException("MCP process is not running serverId=" + serverId + " method=" + method);
        }
    }

    private static boolean isAlive(Process p) {
        return p != null && p.isAlive();
    }

    private static boolean shouldRetryTransportError(Exception e) {
        if (e instanceof McpRpcException) return false;
        if (e instanceof McpRequestTimeoutException) return false;
        return true;
    }
}


