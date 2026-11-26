package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL → Doris 建表模拟验证。
 *
 * <p>PostgreSQL 的 {@code varchar(n)/char(n)} 中 n 是字符数，而 Doris 的
 * {@code VARCHAR(M)/CHAR(M)} 中 M 是 UTF-8 字节长度（中文占 3 字节）。本测试校验转换器
 * 按 3 倍扩大长度，并正确处理无长度 varchar、超出 Doris 上限以及不能作为 KEY 列的 STRING。</p>
 */
class PostgresqlToDorisDdlTest {

    private final DorisConverter converter = new DorisConverter();

    @Test
    void varcharLengthIsScaledByThreeForUtf8() {
        ColumnMeta target = toDorisColumn("VARCHAR", 100, false);

        assertEquals("VARCHAR", target.getType());
        assertEquals(300, target.getLength());
    }

    @Test
    void charLengthIsScaledAndDegradedToVarcharAboveDorisCharLimit() {
        ColumnMeta small = toDorisColumn("CHAR", 10, false);
        assertEquals("CHAR", small.getType());
        assertEquals(30, small.getLength());

        ColumnMeta medium = toDorisColumn("BPCHAR", 100, false);
        assertEquals("VARCHAR", medium.getType());
        assertEquals(300, medium.getLength());
    }

    @Test
    void unboundedVarcharBecomesString() {
        ColumnMeta target = toDorisColumn("VARCHAR", Integer.MAX_VALUE, false);
        assertEquals("STRING", target.getType());

        ColumnMeta unknownLength = toDorisColumn("VARCHAR", 0, false);
        assertEquals("STRING", unknownLength.getType());
    }

    @Test
    void unboundedVarcharPrimaryKeyFallsBackToMaxVarchar() {
        // Doris 的 STRING 不能作为 KEY/分桶列，主键列必须保留有长度上限的 VARCHAR
        ColumnMeta target = toDorisColumn("VARCHAR", Integer.MAX_VALUE, true);

        assertEquals("VARCHAR", target.getType());
        assertEquals(65533, target.getLength());
    }

    @Test
    void varcharLengthIsCappedAtDorisLimit() {
        ColumnMeta varchar = toDorisColumn("VARCHAR", 30000, false);
        assertEquals("VARCHAR", varchar.getType());
        assertEquals(65533, varchar.getLength());

        ColumnMeta chineseChar = toDorisColumn("CHAR", 30000, false);
        assertEquals("VARCHAR", chineseChar.getType());
        assertEquals(65533, chineseChar.getLength());
    }

    @Test
    void textAndNonCharacterTypesAreNotScaled() {
        ColumnMeta text = toDorisColumn("TEXT", 100, false);
        assertEquals("STRING", text.getType());

        // UUID 虽然映射为 Doris VARCHAR，但长度是固定 36 字节，不能乘 3
        ColumnMeta uuid = toDorisColumn("UUID", 36, false);
        assertEquals("VARCHAR", uuid.getType());
        assertEquals(36, uuid.getLength());
    }

    @Test
    void unboundedVarcharFromOtherSourcesDoesNotGenerateIllegalDorisLength() {
        // 经 DuckDB 等中转后源库类型上下文可能丢失，但 VARCHAR 的 Integer.MAX_VALUE
        // 仍不能直接落成 Doris VARCHAR(2147483647)
        ColumnMeta text = DatabaseConverter.convert(
            "duckdb", "doris", column("v", "VARCHAR", Integer.MAX_VALUE, false));
        assertEquals("STRING", text.getType());

        ColumnMeta key = DatabaseConverter.convert(
            "duckdb", "doris", column("k", "VARCHAR", Integer.MAX_VALUE, true));
        assertEquals("VARCHAR", key.getType());
        assertEquals(65533, key.getLength());
    }

    @Test
    void postgresAliasesUseTheSameRule() {
        ColumnMeta target = DatabaseConverter.convert(
            "kingbase", "doris", column("name", "VARCHAR", 50, false));
        assertEquals("VARCHAR", target.getType());
        assertEquals(150, target.getLength());
    }

    @Test
    void generatedDdlUsesWidenedLengthsAndStringForUnboundedColumns() {
        TableMeta source = new TableMeta("t_pg");
        ColumnMeta id = column("id", "BIGINT", 19, true);
        ColumnMeta name = column("name", "VARCHAR", 100, false);
        ColumnMeta remark = column("remark", "TEXT", Integer.MAX_VALUE, false);
        ColumnMeta extra = column("extra", "VARCHAR", Integer.MAX_VALUE, false);
        source.addColumn(id);
        source.addColumn(name);
        source.addColumn(remark);
        source.addColumn(extra);
        source.setDbType("postgresql");

        TableMeta target = DatabaseConverter.convert("postgresql", "doris", source);
        target.setSchema("test");
        target.setDbType("doris");
        target.setTable("t_pg");

        String ddl = converter.generateTableDDL(target);

        assertTrue(ddl.contains("`name` VARCHAR(300)"), ddl);
        assertTrue(ddl.contains("`remark` STRING"), ddl);
        assertTrue(ddl.contains("`extra` STRING"), ddl);
        assertTrue(ddl.contains("UNIQUE KEY (`id`)"), ddl);
        assertFalse(ddl.contains("2147483647"), ddl);
    }

    private ColumnMeta toDorisColumn(String type, int length, boolean primaryKey) {
        return DatabaseConverter.convert("postgresql", "doris", column("c", type, length, primaryKey));
    }

    private ColumnMeta column(String name, String type, int length, boolean primaryKey) {
        ColumnMeta column = new ColumnMeta(name, type, length, -1, -1);
        column.setPrimaryKey(primaryKey);
        return column;
    }
}
