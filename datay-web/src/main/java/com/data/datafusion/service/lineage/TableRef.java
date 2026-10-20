package com.data.datafusion.service.lineage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL 中引用的表：catalog / schema / table 任一层级都可能为空。
 */
public record TableRef(String catalog, String schema, String table) {
    public static TableRef parse(String value) {
        if (value == null || value.isBlank()) {
            return new TableRef(null, null, "");
        }
        String cleaned = value.trim().replace("\"", "").replace("`", "").replace("[", "").replace("]", "");
        String[] parts = cleaned.split("\\.");
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            String name = part == null ? "" : part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return new TableRef(null, null, "");
        }
        if (names.size() == 1) {
            return new TableRef(null, null, names.get(0));
        }
        if (names.size() == 2) {
            return new TableRef(null, names.get(0), names.get(1));
        }
        return new TableRef(names.get(names.size() - 3), names.get(names.size() - 2), names.get(names.size() - 1));
    }

    public static TableRef of(String catalog, String schema, String table) {
        return new TableRef(blankToNull(catalog), blankToNull(schema), blankToNull(table));
    }

    /**
     * 判断当前表引用是否命中模型绑定的物理表。
     *
     * <p>SQL 中可能只写表名而不带 schema；此时只要表名一致即认为命中。若 SQL 明确写了
     * schema，则 schema 也必须一致，避免跨 schema 同名表误判。</p>
     */
    public boolean matches(String expectedSchema, String expectedTable) {
        if (expectedTable == null || expectedTable.isBlank() || table == null || table.isBlank()) {
            return false;
        }
        if (!same(table, expectedTable)) {
            return false;
        }
        return schema == null || schema.isBlank() || expectedSchema == null || expectedSchema.isBlank() || same(schema, expectedSchema);
    }

    public String displayName() {
        List<String> parts = new ArrayList<>();
        if (catalog != null && !catalog.isBlank()) {
            parts.add(catalog);
        }
        if (schema != null && !schema.isBlank()) {
            parts.add(schema);
        }
        if (table != null && !table.isBlank()) {
            parts.add(table);
        }
        return String.join(".", parts);
    }

    public static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
