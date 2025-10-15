package com.example.quickshop.nlq;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedProvider implements NlqProvider {

    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,3})\\b");
    private static final Pattern INTENT_TOP_PRODUCTS_LAST_MONTH =
        Pattern.compile("(?i)\\b(top|best)\\b.*\\bproducts?\\b.*\\b(last|previous)\\s+month\\b");
    private static final Pattern INTENT_REVENUE_BY_PRODUCT =
        Pattern.compile("(?i)\\brevenue\\b.*\\bby\\b.*\\bproducts?\\b|\\bproducts?\\b.*\\brevenue\\b");
    private static final Pattern INTENT_DAILY_REVENUE_7D =
        Pattern.compile("(?i)\\b(daily|per\\s*day)\\b.*\\b(last|past)\\s*(7|seven)\\s*days\\b");
    private static final Pattern INTENT_NEW_VS_RETURNING_BETWEEN =
        Pattern.compile("(?i)\\bnew\\b.*\\breturning\\b.*\\bbetween\\b\\s*(\\d{4}-\\d{2}-\\d{2})\\s*\\b(and|to)\\b\\s*(\\d{4}-\\d{2}-\\d{2})");

    @Override
    public Plan compile(String prompt) {
        String p = prompt == null ? "" : prompt.trim();
        if (p.isEmpty()) throw unrecognized();

        // 1) top N products by revenue last/previous month
        if (INTENT_TOP_PRODUCTS_LAST_MONTH.matcher(p).find()) {
            int topN = extractTopN(p, 5);
            String sql = """
                    SELECT TOP %d
                      p.name,
                      SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue
                    FROM dbo.orders o
                    JOIN dbo.order_items oi ON oi.order_id = o.id
                    JOIN dbo.products p     ON p.id = oi.product_id
                    WHERE o.status = 'completed'
                      AND o.created_at >= DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-2)) -- start of last month (UTC)
                      AND o.created_at <  DATEADD(DAY,1,EOMONTH(SYSUTCDATETIME(),-1)) -- start of this month
                    GROUP BY p.name
                    ORDER BY revenue DESC
                    """.formatted(topN);
            return new Plan("top_products_last_month", sql, Map.of());
        }

        // 2) revenue by product (no date filter)
        if (INTENT_REVENUE_BY_PRODUCT.matcher(p).find()) {
            String sql = """
                    SELECT
                      p.name,
                      SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue
                    FROM dbo.orders o
                    JOIN dbo.order_items oi ON oi.order_id = o.id
                    JOIN dbo.products p     ON p.id = oi.product_id
                    WHERE o.status = 'completed'
                    GROUP BY p.name
                    ORDER BY revenue DESC
                    """;
            return new Plan("revenue_by_product", sql, Map.of());
        }

        // 3) daily revenue last 7 days
        if (INTENT_DAILY_REVENUE_7D.matcher(p).find()) {
            String sql = """
                    SELECT
                      CAST(o.created_at AS date) AS [day],
                      SUM(oi.qty * oi.unit_price * (1 - oi.discount)) AS revenue
                    FROM dbo.orders o
                    JOIN dbo.order_items oi ON oi.order_id = o.id
                    WHERE o.status = 'completed'
                      AND o.created_at >= DATEADD(DAY,-7,CAST(SYSUTCDATETIME() AS date))
                    GROUP BY CAST(o.created_at AS date)
                    ORDER BY [day]
                    """;
            return new Plan("daily_revenue_7d", sql, Map.of());
        }

        // 4) new vs returning customers between <start> and <end>
        Matcher m = INTENT_NEW_VS_RETURNING_BETWEEN.matcher(p);
        if (m.find()) {
            String start = m.group(1);
            String end   = m.group(3);
            String sql = """
                    WITH first_order AS (
                      SELECT customer_id, MIN(created_at) AS first_order_at
                      FROM dbo.orders
                      WHERE status = 'completed'
                      GROUP BY customer_id
                    )
                    SELECT
                      SUM(CASE WHEN f.first_order_at >= :start_week AND f.first_order_at < :end_week THEN 1 ELSE 0 END) AS new_customers,
                      SUM(CASE WHEN f.first_order_at <  :start_week THEN 1 ELSE 0 END)                                 AS returning_customers
                    FROM first_order f
                    JOIN dbo.orders o ON o.customer_id = f.customer_id
                    WHERE o.status = 'completed'
                      AND o.created_at >= :start_week AND o.created_at < :end_week
                    """;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("start_week", start);
            params.put("end_week", end);
            return new Plan("new_vs_returning_between", sql, params);
        }

        throw unrecognized();
    }

    @Override
    public List<String> suggestions() {
        return NlqProvider.super.suggestions();
    }

    private static int extractTopN(String text, int fallback) {
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 1000) return n;
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static IllegalArgumentException unrecognized() {
        return new IllegalArgumentException("UNRECOGNIZED_NLQ");
    }
}
