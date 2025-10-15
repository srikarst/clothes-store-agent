package com.example.clothesstoreagent.service;

import com.example.clothesstoreagent.config.AppProps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaService {

    private final JdbcTemplate jdbc;
    private final AppProps props;

    public SchemaService(JdbcTemplate jdbc, AppProps props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public Map<String, Object> getSchema() {
        Map<String, Object> out = new LinkedHashMap<>();

        List<Map<String, Object>> tables = jdbc.queryForList("""
            SELECT TABLE_SCHEMA, TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_SCHEMA, TABLE_NAME
        """);

        Set<String> allow = props.getAllowTables().stream()
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, Object>> filteredTables = (allow.isEmpty())
                ? tables
                : tables.stream().filter(t -> allow.contains(
                    (t.get("TABLE_SCHEMA") + "." + t.get("TABLE_NAME")).toLowerCase()))
                    .collect(Collectors.toList());
        out.put("tables", filteredTables);

        List<Map<String, Object>> cols = jdbc.queryForList("""
            SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
            FROM INFORMATION_SCHEMA.COLUMNS
            ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION
        """);

        Map<String, List<Map<String, Object>>> columnsByTable = new LinkedHashMap<>();
        for (Map<String, Object> c : cols) {
            String key = c.get("TABLE_SCHEMA") + "." + c.get("TABLE_NAME");
            if (!allow.isEmpty() && !allow.contains(key.toLowerCase())) continue;
            columnsByTable.computeIfAbsent(key, k -> new ArrayList<>()).add(Map.of(
                "COLUMN_NAME", c.get("COLUMN_NAME"),
                "DATA_TYPE", c.get("DATA_TYPE"),
                "IS_NULLABLE", c.get("IS_NULLABLE")
            ));
        }
        out.put("columnsByTable", columnsByTable);

        List<Map<String, Object>> fks = jdbc.queryForList("""
            SELECT
              fk.name AS constraint_name,
              schP.name AS from_schema, tP.name AS from_table, cP.name AS from_column,
              schR.name AS to_schema,   tR.name AS to_table,   cR.name AS to_column
            FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
            JOIN sys.tables tP ON fkc.parent_object_id = tP.object_id
            JOIN sys.schemas schP ON tP.schema_id = schP.schema_id
            JOIN sys.columns cP ON cP.object_id = tP.object_id AND cP.column_id = fkc.parent_column_id
            JOIN sys.tables tR ON fkc.referenced_object_id = tR.object_id
            JOIN sys.schemas schR ON tR.schema_id = schR.schema_id
            JOIN sys.columns cR ON cR.object_id = tR.object_id AND cR.column_id = fkc.referenced_column_id
            ORDER BY from_schema, from_table, constraint_name, fkc.constraint_column_id
        """);

        if (!allow.isEmpty()) {
            fks = fks.stream().filter(m ->
                allow.contains((m.get("from_schema")+"."+m.get("from_table")).toString().toLowerCase()) &&
                allow.contains((m.get("to_schema")+"."+m.get("to_table")).toString().toLowerCase())
            ).collect(Collectors.toList());
        }
        out.put("fks", fks);

        int perCol = Math.max(0, props.getSchemaSamplesPerColumn());
        Map<String, List<String>> samplesByColumn = new LinkedHashMap<>();

        if (perCol > 0) {
            for (String tbl : columnsByTable.keySet()) {
                String[] parts = tbl.split("\\.", 2);
                String schema = parts[0], table = parts[1];

                for (Map<String, Object> c : columnsByTable.get(tbl)) {
                    String col = c.get("COLUMN_NAME").toString();
                    String dataType = String.valueOf(c.get("DATA_TYPE")).toLowerCase(Locale.ROOT);

                    if (dataType.contains("varbinary") || dataType.contains("image") || dataType.contains("xml"))
                        continue;

                    String sql = "SELECT TOP " + perCol + " DISTINCT " +
                            "CAST(" + quote(schema) + "." + quote(table) + "." + quote(col) + " AS NVARCHAR(200)) AS v " +
                            "FROM " + quote(schema) + "." + quote(table) + " " +
                            "WHERE " + quote(col) + " IS NOT NULL";

                    try {
                        List<String> vals = jdbc.query(sql, (rs, n) -> rs.getString("v"));
                        if (!vals.isEmpty()) {
                            samplesByColumn.put(schema + "." + table + "." + col, vals);
                        }
                    } catch (Exception ignore) { }
                }
            }
        }
        out.put("samplesByColumn", samplesByColumn);

        return out;
    }

    private static String quote(String ident) {
        return "[" + ident.replace("]", "]]" ) + "]";
    }
}
