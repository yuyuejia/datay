package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL → 达梦 DM 建表模拟验证。
 *
 * <p>达梦默认 {@code LENGTH_IN_CHAR=0}，即 {@code VARCHAR(n)/CHAR(n)} 按字节计量；
 * PostgreSQL 的 {@code varchar(n)/char(n)} 按字符计量。本测试校验中文按 3 倍换算、
 * 超过 32767 字节降级为 CLOB，以及无长度 varchar 的处理。</p>
 */
class PostgresqlToDmDdlTest {

    private final DmConverter converter = new DmConverter();

    @Test
    void varcharLengthIsScaledByThreeForUtf8() {
        ColumnMeta target = toDmColumn("VARCHAR", 100, false);

        assertEquals("VARCHAR", target.getType());
        assertEquals(300, target.getLength());
    }

    @Test
    void charLengthIsScaledByThreeForUtf8() {
        ColumnMeta target = toDmColumn("CHAR", 100, false);

        assertEquals("CHAR", target.getType());
        assertEquals(300, target.getLength());
    }

    @Test
    void varcharNearLimitIsScaledAndOverflowBecomesClob() {
        ColumnMeta nearLimit = toDmColumn("VARCHAR", 10922, false);
        assertEquals("VARCHAR", nearLimit.getType());
        assertEquals(32766, nearLimit.getLength());

        ColumnMeta overflow = toDmColumn("VARCHAR", 10923, false);
        assertEquals("CLOB", overflow.getType());
    }

    @Test
    void charOverflowBecomesClob() {
        ColumnMeta nearLimit = toDmColumn("CHAR", 10922, false);
        assertEquals("CHAR", nearLimit.getType());
        assertEquals(32766, nearLimit.getLength());

        ColumnMeta overflow = toDmColumn("CHAR", 10923, false);
        assertEquals("CLOB", overflow.getType());
    }

    @Test
    void unboundedVarcharBecomesClob() {
        assertEquals("CLOB", toDmColumn("VARCHAR", Integer.MAX_VALUE, false).getType());
        assertEquals("CLOB", toDmColumn("VARCHAR", 0, false).getType());
    }

    @Test
    void unboundedVarcharPrimaryKeyFallsBackToMaxVarchar() {
        // CLOB 不能作为主键，主键列必须保留有长度上限的 VARCHAR
        ColumnMeta target = toDmColumn("VARCHAR", Integer.MAX_VALUE, true);

        assertEquals("VARCHAR", target.getType());
        assertEquals(32767, target.getLength());
    }

    @Test
    void oversizedVarcharPrimaryKeyFallsBackToMaxVarchar() {
        ColumnMeta target = toDmColumn("VARCHAR", 30000, true);

        assertEquals("VARCHAR", target.getType());
        assertEquals(32767, target.getLength());
    }

    @Test
    void textAndBinaryTypesAreNotScaled() {
        assertEquals("CLOB", toDmColumn("TEXT", 100, false).getType());

        ColumnMeta binary = toDmColumn("VARBINARY", 16, false);
        assertEquals("VARBINARY", binary.getType());
        assertEquals(16, binary.getLength());
    }

    @Test
    void dmCharacterSemanticTypesAreNotScaled() {
        // 达梦 NVARCHAR2/NCHAR 按字符计量（NVARCHAR_LENGTH_IN_CHAR=1），不需要按字节放大
        ColumnMeta nvarchar = toDmColumn("NVARCHAR", 100, false);
        assertEquals("NVARCHAR2", nvarchar.getType());
        assertEquals(100, nvarchar.getLength());

        ColumnMeta nchar = toDmColumn("NCHAR", 100, false);
        assertEquals("NCHAR", nchar.getType());
        assertEquals(100, nchar.getLength());
    }

    @Test
    void postgresAliasesUseTheSameRule() {
        ColumnMeta target = DatabaseConverter.convert(
            "gaussdb", "dm", column("name", "VARCHAR", 50, false));
        assertEquals("VARCHAR", target.getType());
        assertEquals(150, target.getLength());
    }

    @Test
    void unboundedVarcharFromOtherSourcesDoesNotGenerateIllegalDmLength() {
        // 经 DuckDB 等中转后源库类型上下文可能丢失，但 VARCHAR 的 Integer.MAX_VALUE
        // 仍不能直接落成达梦 VARCHAR(2147483647)
        ColumnMeta clob = DatabaseConverter.convert(
            "duckdb", "dm", column("v", "VARCHAR", Integer.MAX_VALUE, false));
        assertEquals("CLOB", clob.getType());

        ColumnMeta key = DatabaseConverter.convert(
            "duckdb", "dm", column("k", "VARCHAR", Integer.MAX_VALUE, true));
        assertEquals("VARCHAR", key.getType());
        assertEquals(32767, key.getLength());
    }

    @Test
    void generatedDdlUsesWidenedLengthsAndClobForOversizedColumns() {
        TableMeta source = new TableMeta("t_pg");
        source.addColumn(column("id", "BIGINT", 19, true));
        source.addColumn(column("name", "VARCHAR", 100, false));
        source.addColumn(column("remark", "TEXT", Integer.MAX_VALUE, false));
        source.addColumn(column("extra", "VARCHAR", Integer.MAX_VALUE, false));
        source.setDbType("postgresql");

        TableMeta target = DatabaseConverter.convert("postgresql", "dm", source);
        target.setSchema("test");
        target.setDbType("dm");
        target.setTable("t_pg");

        String ddl = converter.generateTableDDL(target);

        assertTrue(ddl.contains("\"NAME\" VARCHAR(300)"), ddl);
        assertTrue(ddl.contains("\"REMARK\" CLOB"), ddl);
        assertTrue(ddl.contains("\"EXTRA\" CLOB"), ddl);
        assertTrue(ddl.contains("PRIMARY KEY (\"ID\")"), ddl);
        assertFalse(ddl.contains("2147483647"), ddl);
    }

    private ColumnMeta toDmColumn(String type, int length, boolean primaryKey) {
        return DatabaseConverter.convert("postgresql", "dm", column("c", type, length, primaryKey));
    }

    private ColumnMeta column(String name, String type, int length, boolean primaryKey) {
        ColumnMeta column = new ColumnMeta(name, type, length, -1, -1);
        column.setPrimaryKey(primaryKey);
        return column;
    }
}
