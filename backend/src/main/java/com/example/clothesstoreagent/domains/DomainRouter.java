package com.example.clothesstoreagent.domains;

import java.util.Locale;

/**
 * Phase 1: heuristic-first domain selection.
 * Extensible for future model-based routing, but model routing is out of scope for Phase 1.
 */
public class DomainRouter {

    public Domain route(DomainHint hint, String userMessage) {
        if (hint == DomainHint.GENERAL) return Domain.GENERAL;
        if (hint == DomainHint.ANALYTICS_SQL) return Domain.ANALYTICS_SQL;
        if (hint == DomainHint.MCP_TOOLS) return Domain.MCP_TOOLS;

        // AUTO (default)
        String msg = userMessage != null ? userMessage : "";
        String m = msg.toLowerCase(Locale.ROOT);

        // Light heuristic: analytics / data / SQL / schema / DB signals.
        if (containsAny(m,
                "revenue", "sales", "orders", "customers", "top products",
                "sql", "database", "db", "schema", "table", "column", "query", "select", "join",
                "group by", "count(", "sum(", "avg(", "trend", "last month", "last week")) {
            return Domain.ANALYTICS_SQL;
        }

        // MCP tools experimentation domain signals.
        if (containsAny(m,
                "mcp", "mcp_tools",
                "use mcp", "mcp tool", "mcp tools",
                "call tool", "tool call",
                "server", "tool server", "mcp server",
                "tools/list", "tools/call",
                "tool")) {
            return Domain.MCP_TOOLS;
        }

        return Domain.GENERAL;
    }

    private static boolean containsAny(String s, String... needles) {
        if (s == null || s.isBlank()) return false;
        for (String n : needles) {
            if (n != null && !n.isBlank() && s.contains(n)) return true;
        }
        return false;
    }
}




