package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * MySQL → Doris 建表模拟验证。
 *
 * <p>不依赖任何外部环境，直接构造 MySQL JDBC 元数据常见形态（列类型为 {@code TYPE_NAME} 原值），
 * 走 {@link DatabaseConverter} 转换后生成 Doris DDL，并校验 Doris 的建表约束：</p>
 * <ul>
 *   <li>UNIQUE KEY 必须是列定义的有序前缀（本次线上报错的根因）；</li>
 *   <li>分桶列必须是 KEY 列；</li>
 *   <li>列名统一小写、不携带默认值；</li>
 *   <li>常见 MySQL 类型映射正确，无符号整数与超范围 DECIMAL 不丢精度语义。</li>
 * </ul>
 */
class MysqlToDorisDdlTest {

    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("^`([^`]+)`");
    private static final Pattern UNIQUE_KEY_PATTERN = Pattern.compile("UNIQUE KEY\\s*\\(([^)]*)\\)");
    private static final Pattern DISTRIBUTION_PATTERN = Pattern.compile("DISTRIBUTED BY HASH\\(([^)]*)\\)");

    @Test
    void singlePrimaryKeyFirstColumn() {
        TableMeta source = mysqlTable("t_pk_first",
            column("id", "BIGINT", 20, -1, -1, false, true),
            column("name", "VARCHAR", 64, -1, -1, false, false),
            column("amount", "DECIMAL", 0, 12, 2, true, false),
            column("created_at", "DATETIME", 19, -1, -1, true, false),
            column("remark", "TEXT", 65535, -1, -1, true, false));

        String ddl = toDorisDdl(source, "test");

        assertEquals(List.of("id", "name", "amount", "created_at", "remark"), columnNames(ddl));
        assertEquals(List.of("id"), uniqueKeys(ddl));
        assertEquals("id", distributionColumn(ddl));
        assertTrue(ddl.contains("`amount` DECIMAL(12,2)"), ddl);
        assertTrue(ddl.contains("`remark` STRING"), ddl);
        assertValidDorisDdl(ddl);
    }

    @Test
    void scatteredCompositePrimaryKeyIsMovedToPrefix() {
        // 线上报错场景：主键 sType 位于普通列 didtype 之后
        TableMeta source = mysqlTable("t_scattered_pk",
            column("id", "BIGINT", 20, -1, -1, false, true),
            column("didtype", "VARCHAR", 50, -1, -1, true, false),
            column("sType", "VARCHAR", 50, -1, -1, false, true),
            column("tenant_id", "BIGINT", 20, -1, -1, false, true),
            column("amount", "DECIMAL", 0, 10, 2, true, false));

        String ddl = toDorisDdl(source, "test");

        assertEquals(List.of("id", "stype", "tenant_id", "didtype", "amount"), columnNames(ddl));
        assertEquals(List.of("id", "stype", "tenant_id"), uniqueKeys(ddl));
        assertEquals("id", distributionColumn(ddl));
        assertValidDorisDdl(ddl);
    }

    @Test
    void reversedCompositePrimaryKeyKeepsColumnOrder() {
        TableMeta source = mysqlTable("t_reverse_pk",
            column("b", "BIGINT", 20, -1, -1, false, true),
            column("a", "BIGINT", 20, -1, -1, false, true),
            column("c", "VARCHAR", 20, -1, -1, true, false));

        String ddl = toDorisDdl(source, "test");

        // readTableMeta 用集合收集主键，主键在 DDL 中按列顺序排列，仍满足前缀规则
        assertEquals(List.of("b", "a", "c"), columnNames(ddl));
        assertEquals(List.of("b", "a"), uniqueKeys(ddl));
        assertValidDorisDdl(ddl);
    }

    @Test
    void tableWithoutPrimaryKeyUsesFirstColumnAsDistributionKey() {
        TableMeta source = mysqlTable("t_no_pk",
            column("id", "BIGINT", 20, -1, -1, true, false),
            column("name", "VARCHAR", 64, -1, -1, true, false),
            column("amount", "DECIMAL", 0, 10, 2, true, false));

        String ddl = toDorisDdl(source, "test");

        assertEquals(List.of("id", "name", "amount"), columnNames(ddl));
        assertTrue(uniqueKeys(ddl).isEmpty(), ddl);
        assertEquals("id", distributionColumn(ddl));
        assertValidDorisDdl(ddl);
    }

    @Test
    void allColumnsArePrimaryKey() {
        TableMeta source = mysqlTable("t_all_pk",
            column("a", "BIGINT", 20, -1, -1, false, true),
            column("b", "VARCHAR", 20, -1, -1, false, true),
            column("c", "DATE", 10, -1, -1, false, true));

        String ddl = toDorisDdl(source, "test");

        assertEquals(List.of("a", "b", "c"), columnNames(ddl));
        assertEquals(List.of("a", "b", "c"), uniqueKeys(ddl));
        assertEquals("a", distributionColumn(ddl));
        assertValidDorisDdl(ddl);
    }

    @Test
    void mixedCaseColumnNamesAreLowercasedConsistently() {
        TableMeta source = mysqlTable("t_upper_case",
            column("ID", "BIGINT", 20, -1, -1, false, true),
            column("dIdType", "VARCHAR", 50, -1, -1, true, false),
            column("SType", "VARCHAR", 50, -1, -1, false, true));

        String ddl = toDorisDdl(source, "test");

        assertEquals(List.of("id", "stype", "didtype"), columnNames(ddl));
        assertEquals(List.of("id", "stype"), uniqueKeys(ddl));
        assertValidDorisDdl(ddl);
    }

    @Test
    void commonMysqlTypesAreMapped() {
        TableMeta source = mysqlTable("t_types",
            column("c_tinyint", "TINYINT", 3, -1, -1, true, false),
            column("c_smallint", "SMALLINT", 5, -1, -1, true, false),
            column("c_mediumint", "MEDIUMINT", 7, -1, -1, true, false),
            column("c_int", "INT", 10, -1, -1, true, false),
            column("c_bigint", "BIGINT", 19, -1, -1, true, false),
            column("c_decimal", "DECIMAL", 0, 18, 4, true, false),
            column("c_float", "FLOAT", 12, -1, -1, true, false),
            column("c_double", "DOUBLE", 22, -1, -1, true, false),
            column("c_char", "CHAR", 10, -1, -1, true, false),
            column("c_varchar", "VARCHAR", 100, -1, -1, true, false),
            column("c_tinytext", "TINYTEXT", 255, -1, -1, true, false),
            column("c_text", "TEXT", 65535, -1, -1, true, false),
            column("c_mediumtext", "MEDIUMTEXT", 16777215, -1, -1, true, false),
            column("c_longtext", "LONGTEXT", 2147483647, -1, -1, true, false),
            column("c_blob", "BLOB", 65535, -1, -1, true, false),
            column("c_date", "DATE", 10, -1, -1, true, false),
            column("c_datetime", "DATETIME", 19, -1, -1, true, false),
            column("c_timestamp", "TIMESTAMP", 19, -1, -1, true, false),
            column("c_time", "TIME", 10, -1, -1, true, false),
            column("c_year", "YEAR", 4, -1, -1, true, false),
            column("c_json", "JSON", 0, -1, -1, true, false),
            column("c_enum", "ENUM", 1, -1, -1, true, false),
            column("c_set", "SET", 3, -1, -1, true, false),
            column("c_bit", "BIT", 1, -1, -1, true, false),
            column("c_binary", "BINARY", 8, -1, -1, true, false),
            column("c_varbinary", "VARBINARY", 32, -1, -1, true, false),
            column("id", "BIGINT", 20, -1, -1, false, true));

        String ddl = toDorisDdl(source, "test");

        assertTrue(ddl.contains("`c_tinyint` TINYINT"), ddl);
        assertTrue(ddl.contains("`c_smallint` SMALLINT"), ddl);
        assertTrue(ddl.contains("`c_mediumint` INT"), ddl);
        assertTrue(ddl.contains("`c_int` INT"), ddl);
        assertTrue(ddl.contains("`c_bigint` BIGINT"), ddl);
        assertTrue(ddl.contains("`c_decimal` DECIMAL(18,4)"), ddl);
        assertTrue(ddl.contains("`c_float` FLOAT"), ddl);
        assertTrue(ddl.contains("`c_double` DOUBLE"), ddl);
        assertTrue(ddl.contains("`c_char` CHAR(10)"), ddl);
        assertTrue(ddl.contains("`c_varchar` VARCHAR(100)"), ddl);
        assertTrue(ddl.contains("`c_tinytext` STRING"), ddl);
        assertTrue(ddl.contains("`c_text` STRING"), ddl);
        assertTrue(ddl.contains("`c_mediumtext` STRING"), ddl);
        assertTrue(ddl.contains("`c_longtext` STRING"), ddl);
        assertTrue(ddl.contains("`c_blob` STRING"), ddl);
        assertTrue(ddl.contains("`c_date` DATE"), ddl);
        assertTrue(ddl.contains("`c_datetime` DATETIME"), ddl);
        assertTrue(ddl.contains("`c_timestamp` DATETIME"), ddl);
        assertTrue(ddl.contains("`c_time` VARCHAR(10)"), ddl);
        assertTrue(ddl.contains("`c_year` INT"), ddl);
        assertTrue(ddl.contains("`c_json` JSON"), ddl);
        assertTrue(ddl.contains("`c_enum` VARCHAR(1)"), ddl);
        assertTrue(ddl.contains("`c_set` VARCHAR(3)"), ddl);
        assertTrue(ddl.contains("`c_bit` VARCHAR(1)"), ddl);
        assertTrue(ddl.contains("`c_binary` VARCHAR(8)"), ddl);
        assertTrue(ddl.contains("`c_varbinary` VARCHAR(32)"), ddl);
        assertEquals(List.of("id", "c_tinyint", "c_smallint", "c_mediumint", "c_int", "c_bigint", "c_decimal",
            "c_float", "c_double", "c_char", "c_varchar", "c_tinytext", "c_text", "c_mediumtext", "c_longtext",
            "c_blob", "c_date", "c_datetime", "c_timestamp", "c_time", "c_year", "c_json", "c_enum", "c_set",
            "c_bit", "c_binary", "c_varbinary"), columnNames(ddl));
        assertValidDorisDdl(ddl);
    }

    @Test
    void unsignedIntegerTypesAreWidenedInsteadOfBecomingVarchar() {
        TableMeta source = mysqlTable("t_unsigned",
            column("id", "BIGINT UNSIGNED", 20, -1, -1, false, true),
            column("c_tiny", "TINYINT UNSIGNED", 3, -1, -1, true, false),
            column("c_small", "SMALLINT UNSIGNED", 5, -1, -1, true, false),
            column("c_medium", "MEDIUMINT UNSIGNED", 7, -1, -1, true, false),
            column("c_int", "INT UNSIGNED", 10, -1, -1, true, false),
            column("c_big", "BIGINT UNSIGNED", 20, -1, -1, true, false));

        String ddl = toDorisDdl(source, "test");

        assertTrue(ddl.contains("`id` LARGEINT"), ddl);
        assertTrue(ddl.contains("`c_tiny` SMALLINT"), ddl);
        assertTrue(ddl.contains("`c_small` INT"), ddl);
        assertTrue(ddl.contains("`c_medium` INT"), ddl);
        assertTrue(ddl.contains("`c_int` BIGINT"), ddl);
        assertTrue(ddl.contains("`c_big` LARGEINT"), ddl);
        assertFalse(ddl.contains("`c_big` VARCHAR"), ddl);
        assertValidDorisDdl(ddl);
    }

    @Test
    void wideDecimalIsClampedToDorisPrecisionLimit() {
        TableMeta source = mysqlTable("t_decimal_wide",
            column("id", "BIGINT", 20, -1, -1, false, true),
            column("c_dec", "DECIMAL", 0, 65, 30, true, false));

        String ddl = toDorisDdl(source, "test");

        assertTrue(ddl.contains("`c_dec` DECIMAL(38,30)"), ddl);
        assertValidDorisDdl(ddl);
    }

    @Test
    void columnCommentsAreEscaped() {
        TableMeta source = mysqlTable("t_comment",
            column("id", "BIGINT", 20, -1, -1, false, true),
            withComment(column("name", "VARCHAR", 50, -1, -1, true, false), "it's name, with comma"));

        String ddl = toDorisDdl(source, "test");

        // MySQL 单引号在 Doris 中成对转义
        assertTrue(ddl.contains("COMMENT 'it''s name, with comma'"), ddl);
        assertValidDorisDdl(ddl);
    }

    // ------------------------------------------------------------------ 构造与校验辅助

    static TableMeta mysqlTable(String name, ColumnMeta... columns) {
        TableMeta table = new TableMeta(name, new ArrayList<>(List.of(columns)));
        table.setSchema("src_db");
        table.setDbType("mysql");
        return table;
    }

    static ColumnMeta column(String name, String type, int length, int precision, int scale, boolean nullable, boolean primaryKey) {
        ColumnMeta column = new ColumnMeta(name, type, length, precision, scale);
        column.setNullable(nullable);
        column.setPrimaryKey(primaryKey);
        return column;
    }

    static ColumnMeta withComment(ColumnMeta column, String comment) {
        column.setComment(comment);
        return column;
    }

    static String toDorisDdl(TableMeta source, String targetSchema) {
        TableMeta target = DatabaseConverter.convert("mysql", "doris", source);
        target.setSchema(targetSchema);
        target.setTable(source.getTable().toLowerCase(Locale.ROOT));
        return DatabaseConverter.generateTableDDL("doris", target);
    }

    static List<String> columnNames(String ddl) {
        int start = ddl.indexOf("(\n");
        int end = ddl.indexOf("\n)\n", start);
        assertTrue(start >= 0 && end > start, "无法解析列定义:\n" + ddl);

        String body = ddl.substring(start + 2, end);
        List<String> names = new ArrayList<>();
        for (String rawLine : body.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = COLUMN_NAME_PATTERN.matcher(line);
            assertTrue(matcher.find(), "无法解析列定义行: " + rawLine + "\n" + ddl);
            names.add(matcher.group(1));
        }
        return names;
    }

    static List<String> uniqueKeys(String ddl) {
        Matcher matcher = UNIQUE_KEY_PATTERN.matcher(ddl);
        if (!matcher.find()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String part : matcher.group(1).split(",")) {
            String key = part.trim().replace("`", "");
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    static String distributionColumn(String ddl) {
        Matcher matcher = DISTRIBUTION_PATTERN.matcher(ddl);
        assertTrue(matcher.find(), "缺少 DISTRIBUTED BY HASH:\n" + ddl);
        return matcher.group(1).trim().replace("`", "");
    }

    /**
     * 校验 Doris 建表 DDL 的通用约束，任何 MySQL 来源的表都应满足。
     */
    static void assertValidDorisDdl(String ddl) {
        List<String> columns = columnNames(ddl);
        assertFalse(columns.isEmpty(), "表没有列:\n" + ddl);

        List<String> keys = uniqueKeys(ddl);
        assertTrue(keys.size() <= columns.size(), "KEY 列数量超过表列数:\n" + ddl);
        for (int i = 0; i < keys.size(); i++) {
            assertEquals(
                columns.get(i),
                keys.get(i),
                "UNIQUE KEY 必须是列定义的有序前缀，第 " + i + " 个不匹配:\n" + ddl
            );
        }

        // Doris 要求分桶列必须是 KEY 列；无主键时默认以第一列为 KEY
        String distribution = distributionColumn(ddl);
        List<String> effectiveKeys = keys.isEmpty() ? List.of(columns.get(0)) : keys;
        assertTrue(effectiveKeys.contains(distribution), "分桶列必须是 KEY 列:\n" + ddl);

        assertFalse(ddl.toUpperCase(Locale.ROOT).contains(" DEFAULT "), "Doris DDL 不应包含默认值:\n" + ddl);
        for (String column : columns) {
            assertEquals(column.toLowerCase(Locale.ROOT), column, "列名应统一小写:\n" + ddl);
        }
    }
}
