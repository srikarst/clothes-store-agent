package com.example.clothesstoreagent.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.mcp")
public class McpProps {
    private boolean enabled = false;
    private String protocolVersion = "2024-11-05";
    private List<Server> servers = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }

    public List<Server> getServers() { return servers; }
    public void setServers(List<Server> servers) {
        this.servers = servers;
    }

    public static class Server {
        private String id;
        private boolean enabled = true;
        private String transport = "stdio";
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private List<String> domains = new ArrayList<>(List.of("mcp_tools"));
        private int timeoutSeconds = 20;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }

        public List<String> getArgs() { return args; }
        public void setArgs(List<String> args) { this.args = args; }

        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env; }

        public List<String> getDomains() { return domains; }
        public void setDomains(List<String> domains) { this.domains = domains; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}


