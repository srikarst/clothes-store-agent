package com.example.clothesstoreagent.domains;

/**
 * Request-time hint for selecting a domain.
 */
public enum DomainHint {
    AUTO("auto"),
    GENERAL("general"),
    ANALYTICS_SQL("analytics_sql");

    private final String id;

    DomainHint(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DomainHint fromId(String id) {
        if (id == null) return null;
        String v = id.trim().toLowerCase();
        return switch (v) {
            case "auto" -> AUTO;
            case "general" -> GENERAL;
            case "analytics_sql", "analytics", "sql" -> ANALYTICS_SQL;
            default -> null;
        };
    }
}




