package com.example.quickshop.nlq;

import java.util.List;
import java.util.Map;

public interface NlqProvider {
    final class Plan {
        public final String intent;           // e.g., "top_products_last_month"
        public final String sql;              // compiled SELECT
        public final Map<String, Object> params;

        public Plan(String intent, String sql, Map<String, Object> params) {
            this.intent = intent;
            this.sql = sql;
            this.params = params != null ? params : Map.of();
        }
    }

    Plan compile(String prompt);

    default List<String> suggestions() {
        return List.of(
            "top 5 products by revenue last month",
            "revenue by product",
            "daily revenue last 7 days",
            "new vs returning customers between 2025-10-02 and 2025-10-09"
        );
    }
}
