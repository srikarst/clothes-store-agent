package com.example.quickshop.service;

import com.example.quickshop.config.AppProps;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class QueryService {

    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcTemplate jdbc;
    private final AppProps props;

    // Allow either "SELECT ..." or "WITH ... SELECT ..."
    private static final Pattern LEADING_SELECT =
            Pattern.compile("^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CTE_THEN_SELECT =
            Pattern.compile("^\\s*WITH\\b[\\s\\S]+?\\bSELECT\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public QueryService(NamedParameterJdbcTemplate namedJdbc, JdbcTemplate jdbc, AppProps props) {
        this.namedJdbc = namedJdbc;
        this.jdbc = jdbc;
        this.props = props;
    }

    public Map<String, Object> execute(String sql,
                                       Map<String, Object> params,
                                       Integer maxRows,
                                       Integer timeoutSeconds) {

        validate(sql);

        int max = Math.min(
                maxRows != null ? maxRows : props.getDefaultMaxRows(),
                props.getDefaultMaxRows()
        );
        int timeout = timeoutSeconds != null ? timeoutSeconds : props.getDefaultQueryTimeoutSeconds();

        int prevMaxRows = jdbc.getMaxRows();
        int prevTimeout = jdbc.getQueryTimeout();
        try {
            jdbc.setMaxRows(max);
            jdbc.setQueryTimeout(timeout);

            SqlParameterSource psrc = new MapSqlParameterSource(params != null ? params : Map.of());
            List<Map<String, Object>> rows = namedJdbc.queryForList(sql, psrc);

            List<String> columns = new ArrayList<>();
            if (!rows.isEmpty()) {
                columns.addAll(rows.get(0).keySet());
            }

            boolean truncated = rows.size() >= max; // heuristic
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("columns", columns);
            resp.put("rowCount", rows.size());
            resp.put("truncated", truncated);
            resp.put("rows", rows);
            return resp;

        } catch (DataAccessException ex) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "QUERY_FAILED");
            err.put("message", ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage());
            return err;

        } finally {
            jdbc.setMaxRows(prevMaxRows);
            jdbc.setQueryTimeout(prevTimeout);
        }
    }

    private void validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL is required");
        }

        // Trim and allow a single trailing ';'
        String s = sql.trim();
        s = s.replaceAll("[\\s;]+$", ""); // strip trailing semicolons/whitespace

        // Must be SELECT or WITH ... SELECT
        boolean looksSelect = LEADING_SELECT.matcher(s).find() || CTE_THEN_SELECT.matcher(s).find();
        if (!looksSelect) {
            throw new IllegalArgumentException("Only SELECT statements are allowed in this endpoint.");
        }

        // Block multiple statements: any remaining ';' inside body means more than one
        if (s.contains(";")) {
            throw new IllegalArgumentException("Multiple statements not allowed.");
        }

        // Basic keyword blocklist (defense in depth)
        String upper = s.toUpperCase(Locale.ROOT);
        for (String bad : props.getDisallowSqlKeywords()) {
            // simple contains check is ok for our sandbox
            if (upper.contains(" " + bad + " ") || upper.startsWith(bad + " ") || upper.contains("\n" + bad + " ")) {
                throw new IllegalArgumentException("Disallowed keyword detected: " + bad);
            }
        }

        // Optional allowlist enforcement
        List<String> allow = props.getAllowTables();
        if (allow != null && !allow.isEmpty()) {
            boolean any = false;
            String lowered = s.toLowerCase(Locale.ROOT);
            for (String t : allow) {
                if (lowered.contains(t.toLowerCase(Locale.ROOT))) { any = true; break; }
            }
            if (!any) {
                throw new IllegalArgumentException(
                        "SQL references tables outside the allowlist. Adjust 'app.allowTables' or the query.");
            }
        }
    }
}
