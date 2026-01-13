package com.example.clothesstoreagent.service;

import com.example.clothesstoreagent.config.AppProps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaService {

    private final JdbcTemplate jdbc;
    private final AppProps props;
    private final DataSource dataSource;

    public SchemaService(JdbcTemplate jdbc, AppProps props) {
        this.jdbc = jdbc;
        this.props = props;
        this.dataSource = Objects.requireNonNull(jdbc.getDataSource(), "DataSource is required");
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

        List<Map<String, Object>> fks = readForeignKeys(columnsByTable.keySet(), allow);
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
                            "CAST(" + q(schema) + "." + q(table) + "." + q(col) + " AS VARCHAR(200)) AS v " +
                            "FROM " + q(schema) + "." + q(table) + " " +
                            "WHERE " + q(col) + " IS NOT NULL";

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


    private List<Map<String, Object>> readForeignKeys(Set<String> schemaDotTables, Set<String> allowLower) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<Map<String, Object>> fks = new ArrayList<>();

            for (String tbl : schemaDotTables) {
                String[] parts = tbl.split("\\.", 2);
                if (parts.length != 2) continue;
                String schema = parts[0];
                String table = parts[1];

                try (ResultSet rs = meta.getImportedKeys(conn.getCatalog(), schema, table)) {
                    while (rs.next()) {
                        String fromSchema = rs.getString("FKTABLE_SCHEM");
                        String fromTable = rs.getString("FKTABLE_NAME");
                        String fromColumn = rs.getString("FKCOLUMN_NAME");
                        String toSchema = rs.getString("PKTABLE_SCHEM");
                        String toTable = rs.getString("PKTABLE_NAME");
                        String toColumn = rs.getString("PKCOLUMN_NAME");
                        String fkName = rs.getString("FK_NAME");

                        if (!allowLower.isEmpty()) {
                            String fromKey = (fromSchema + "." + fromTable).toLowerCase(Locale.ROOT);
                            String toKey = (toSchema + "." + toTable).toLowerCase(Locale.ROOT);
                            if (!allowLower.contains(fromKey) || !allowLower.contains(toKey)) continue;
                        }

                        fks.add(new LinkedHashMap<>(Map.of(
                                "constraint_name", fkName,
                                "from_schema", fromSchema,
                                "from_table", fromTable,
                                "from_column", fromColumn,
                                "to_schema", toSchema,
                                "to_table", toTable,
                                "to_column", toColumn
                        )));
                    }
                }
            }

            fks.sort(Comparator
                    .comparing((Map<String, Object> m) -> String.valueOf(m.get("from_schema")))
                    .thenComparing(m -> String.valueOf(m.get("from_table")))
                    .thenComparing(m -> String.valueOf(m.get("constraint_name")))
                    .thenComparing(m -> String.valueOf(m.get("from_column"))));

            return fks;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Returns a compact schema digest with limited sample values per column.
     * Useful for conversation history to reduce token usage.
     * @param samplesPerColumn maximum sample values per column
     */
    public String compactDigest(int samplesPerColumn) {
        Map<String, Object> schema = getSchema();
        
        StringBuilder sb = new StringBuilder();
        sb.append("Schema:\n");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> columnsByTable = 
            (Map<String, List<Map<String, Object>>>) schema.get("columnsByTable");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> samplesByColumn = 
            (Map<String, List<String>>) schema.get("samplesByColumn");
        
        for (Map<String, Object> table : tables) {
            String tableKey = table.get("TABLE_SCHEMA") + "." + table.get("TABLE_NAME");
            sb.append("- ").append(tableKey).append(":\n");
            
            List<Map<String, Object>> columns = columnsByTable.getOrDefault(tableKey, List.of());
            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("COLUMN_NAME");
                String dataType = (String) col.get("DATA_TYPE");
                sb.append("  - ").append(colName).append(" (").append(dataType).append(")");
                
                String sampleKey = tableKey + "." + colName;
                List<String> samples = samplesByColumn.get(sampleKey);
                if (samples != null && !samples.isEmpty()) {
                    List<String> limited = samples.stream()
                        .limit(samplesPerColumn)
                        .collect(Collectors.toList());
                    sb.append(" samples: ").append(limited);
                }
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
    
    private String q(String ident) {
        String dbProduct = "";
        try (Connection conn = dataSource.getConnection()) {
            dbProduct = String.valueOf(conn.getMetaData().getDatabaseProductName());
        } catch (Exception ignore) { }

        if (dbProduct.toLowerCase(Locale.ROOT).contains("microsoft sql server")) {
            return "[" + ident.replace("]", "]]" ) + "]";
        }
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
