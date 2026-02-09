package com.example.clothesstoreagent.domains;

/**
 * Phase 1 domains (simple multi-domain routing).
 */
public enum Domain {
    GENERAL("general"),
    ANALYTICS_SQL("analytics_sql"),
    MCP_TOOLS("mcp_tools");

    private final String id;

    Domain(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Domain fromId(String id) {
        if (id == null) return null;
        String v = id.trim().toLowerCase();
        return switch (v) {
            case "general" -> GENERAL;
            case "analytics_sql", "analytics", "sql" -> ANALYTICS_SQL;
            case "mcp_tools", "mcp" -> MCP_TOOLS;
            default -> null;
        };
    }
}




