package com.example.clothesstoreagent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads configured MCP servers and manages connections + cached tool definitions.
 */
@Component
public class McpServerManager {

    private static final Logger log = LoggerFactory.getLogger(McpServerManager.class);

    public enum ConnectionStatus {
        DISABLED,
        NOT_CONNECTED,
        CONNECTED,
        FAILED
    }

    public record ServerStatus(
            String id,
            boolean enabled,
            String transport,
            List<String> domains,
            ConnectionStatus status,
            int toolCount,
            String lastError
    ) {}

    private static class Runtime {
        volatile McpClient client;
        volatile List<McpToolDefinition> tools = List.of();
        volatile ConnectionStatus status = ConnectionStatus.NOT_CONNECTED;
        volatile String lastError;

        Runtime() {}
    }

    private final McpProps props;
    private final ObjectMapper om;
    private final Map<String, McpTransportProvider> providersById;
    private final ConcurrentHashMap<String, Runtime> runtimes = new ConcurrentHashMap<>();

    public McpServerManager(McpProps props, ObjectMapper om, List<McpTransportProvider> providers) {
        this.props = props;
        this.om = om != null ? om : new ObjectMapper();
        Map<String, McpTransportProvider> byId = new LinkedHashMap<>();
        if (providers != null) {
            for (McpTransportProvider p : providers) {
                if (p == null || p.transportId() == null || p.transportId().isBlank()) continue;
                byId.put(p.transportId().trim().toLowerCase(Locale.ROOT), p);
            }
        }
        this.providersById = Map.copyOf(byId);
    }

    public Map<String, List<McpToolDefinition>> getTools() {
        Map<String, List<McpToolDefinition>> out = new LinkedHashMap<>();
        List<McpProps.Server> servers = props != null && props.getServers() != null ? props.getServers() : List.of();
        for (McpProps.Server s : servers) {
            if (s == null || s.getId() == null || s.getId().isBlank()) continue;
            String id = s.getId().trim();
            Runtime rt = runtimes.computeIfAbsent(id, k -> new Runtime());
            if (shouldConnect(s) && (rt.tools == null || rt.tools.isEmpty()) && rt.status != ConnectionStatus.CONNECTED) {
                refresh(id);
            }
            out.put(id, rt.tools != null ? rt.tools : List.of());
        }
        return out;
    }

    public McpClient getClient(String serverId) {
        if (serverId == null || serverId.isBlank()) return null;
        if (!isMcpEnabled()) return null;

        McpProps.Server s = findServer(serverId);
        if (s == null || !s.isEnabled()) return null;

        Runtime rt = runtimes.computeIfAbsent(serverId.trim(), k -> new Runtime());
        McpClient c = rt.client;
        if (c != null) return c;

        synchronized (rt) {
            if (rt.client != null) return rt.client;
            rt.client = createClient(serverId.trim(), s);
            return rt.client;
        }
    }

    public void refreshAll() {
        List<McpProps.Server> servers = props != null && props.getServers() != null ? props.getServers() : List.of();
        for (McpProps.Server s : servers) {
            if (s == null || s.getId() == null || s.getId().isBlank()) continue;
            refresh(s.getId().trim());
        }
    }

    public void refresh(String serverId) {
        if (serverId == null || serverId.isBlank()) return;
        McpProps.Server s = findServer(serverId);
        if (s == null) return;

        Runtime rt = runtimes.computeIfAbsent(serverId.trim(), k -> new Runtime());

        if (!shouldConnect(s)) {
            rt.status = ConnectionStatus.DISABLED;
            rt.tools = List.of();
            rt.lastError = null;
            return;
        }

        try {
            McpClient c = getClient(serverId);
            if (c == null) {
                rt.status = ConnectionStatus.DISABLED;
                rt.tools = List.of();
                rt.lastError = null;
                return;
            }
            List<McpToolDefinition> tools = c.listTools();
            rt.tools = tools != null ? List.copyOf(tools) : List.of();
            rt.status = ConnectionStatus.CONNECTED;
            rt.lastError = null;
            log.info("MCP refreshed serverId={} toolCount={}", serverId, rt.tools.size());
        } catch (Exception e) {
            rt.status = ConnectionStatus.FAILED;
            rt.lastError = safeError(e);
            log.warn("MCP refresh failed serverId={} error={}", serverId, rt.lastError);
        }
    }

    public List<ServerStatus> listServerStatuses() {
        List<McpProps.Server> servers = props != null && props.getServers() != null ? props.getServers() : List.of();
        java.util.ArrayList<ServerStatus> out = new java.util.ArrayList<>();
        for (McpProps.Server s : servers) {
            if (s == null || s.getId() == null || s.getId().isBlank()) continue;
            String id = s.getId().trim();
            Runtime rt = runtimes.computeIfAbsent(id, k -> new Runtime());

            ConnectionStatus st;
            if (!shouldConnect(s)) {
                st = ConnectionStatus.DISABLED;
            } else if (rt.status == null) {
                st = ConnectionStatus.NOT_CONNECTED;
            } else {
                st = rt.status;
            }

            List<McpToolDefinition> tools = rt.tools != null ? rt.tools : List.of();
            out.add(new ServerStatus(
                    id,
                    isMcpEnabled() && s.isEnabled(),
                    s.getTransport(),
                    safeDomains(s.getDomains()),
                    st,
                    tools.size(),
                    rt.lastError
            ));
        }
        return out;
    }

    private McpProps.Server findServer(String id) {
        if (props == null || props.getServers() == null) return null;
        for (McpProps.Server s : props.getServers()) {
            if (s == null || s.getId() == null) continue;
            if (s.getId().trim().equalsIgnoreCase(id.trim())) {
                return s;
            }
        }
        return null;
    }

    private McpClient createClient(String serverId, McpProps.Server s) {
        String transportId = s.getTransport() != null ? s.getTransport().trim().toLowerCase(Locale.ROOT) : "stdio";
        McpTransportProvider provider = providersById.get(transportId);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported MCP transport: " + transportId);
        }
        McpTransport t = provider.create(serverId, s, om);
        Duration timeout = Duration.ofSeconds(Math.max(1, s.getTimeoutSeconds()));
        String pv = props != null ? props.getProtocolVersion() : "2024-11-05";
        return new McpClient(serverId, pv, timeout, t, om);
    }

    private boolean isMcpEnabled() {
        return props != null && props.isEnabled();
    }

    private boolean shouldConnect(McpProps.Server s) {
        return isMcpEnabled() && s != null && s.isEnabled();
    }

    private static String safeError(Exception e) {
        if (e == null) return null;
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) msg = e.toString();
        // Avoid returning huge stack traces / secrets.
        msg = msg.replaceAll("\\s+", " ").trim();
        if (msg.length() > 500) {
            msg = msg.substring(0, 499) + "…";
        }
        return msg;
    }

    private static List<String> safeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) return List.of("mcp_tools");
        return domains.stream()
                .filter(d -> d != null && !d.isBlank())
                .map(String::trim)
                .toList();
    }
}


