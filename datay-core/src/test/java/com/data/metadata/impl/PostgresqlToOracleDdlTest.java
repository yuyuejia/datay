package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL → Oracle 建表模拟验证。
 *
 * <p>Oracle 的 {@code VARCHAR2(n)/CHAR(n)} 默认按字节计量，而 PostgreSQL 的
 * {@code varchar(n)/char(n)} 按字符计量。本测试校验中文按 3 倍换算、超过 Oracle
 * 长度上限降级为 CLOB，以及无长度 varchar 的处理。</p>
 */
class PostgresqlToOracleDdlTest {

    private final OracleConverter converter = new OracleConverter();

    @Test
    void varcharLengthIsScaledByThreeForUtf8() {
        ColumnMeta target = toOracleColumn("VARCHAR", 100, false);

        assertEquals("VARCHAR2", target.getType());
        assertEquals(300, target.getLength());
    }

    @Test
    void charLengthIsScaledAndDegradedAboveOracleCharLimit() {
        ColumnMeta small = toOracleColumn("CHAR", 10, false);
        assertEquals("CHAR", small.getType());
        assertEquals(30, small.getLength());

        // 3 * 667 = 2001，超过 CHAR 的 2000 字节上限，降级为 VARCHAR2
        ColumnMeta medium = toOracleColumn("BPCHAR", 667, false);
        assertEquals("VARCHAR2", medium.getType());
        assertEquals(2001, medium.getLength());
    }

    @Test
    void oversizedCharacterColumnsBecomeClob() {
        ColumnMeta varchar = toOracleColumn("VARCHAR", 2000, false);
        assertEquals("CLOB", varchar.getType());

        ColumnMeta chineseChar = toOracleColumn("CHAR", 1400, false);
        assertEquals("CLOB", chineseChar.getType());
    }

    @Test
    void unboundedVarcharBecomesClob() {
        assertEquals("CLOB", toOracleColumn("VARCHAR", Integer.MAX_VALUE, false).getType());
        assertEquals("CLOB", toOracleColumn("VARCHAR", 0, false).getType());
    }

    @Test
    void unboundedVarcharPrimaryKeyFallsBackToMaxVarchar2() {
        // CLOB 不能作为 Oracle 主键，主键列必须保留有长度上限的 VARCHAR2
        ColumnMeta target = toOracleColumn("VARCHAR", Integer.MAX_VALUE, true);

        assertEquals("VARCHAR2", target.getType());
        assertEquals(4000, target.getLength());
    }

    @Test
    void oversizedVarcharPrimaryKeyFallsBackToMaxVarchar2() {
        ColumnMeta target = toOracleColumn("VARCHAR", 30000, true);

        assertEquals("VARCHAR2", target.getType());
        assertEquals(4000, target.getLength());
    }

    @Test
    void textAndBinaryTypesAreNotScaled() {
        assertEquals("CLOB", toOracleColumn("TEXT", 100, false).getType());

        ColumnMeta binary = toOracleColumn("VARBINARY", 16, false);
        assertEquals("RAW", binary.getType());
        assertEquals(16, binary.getLength());
    }

    @Test
    void oracleCharacterSemanticTypesAreNotScaled() {
        // Oracle NVARCHAR2/NCHAR 的长度单位是字符，不需要按 UTF-8 字节放大
        ColumnMeta nvarchar = toOracleColumn("NVARCHAR", 100, false);
        assertEquals("NVARCHAR2", nvarchar.getType());
        assertEquals(100, nvarchar.getLength());

        ColumnMeta nchar = toOracleColumn("NCHAR", 100, false);
        assertEquals("NCHAR", nchar.getType());
        assertEquals(100, nchar.getLength());
    }

    @Test
    void postgresAliasesUseTheSameRule() {
        ColumnMeta target = DatabaseConverter.convert(
            "gaussdb", "oracle", column("name", "VARCHAR", 50, false));
        assertEquals("VARCHAR2", target.getType());
        assertEquals(150, target.getLength());
    }

    @Test
    void unboundedVarcharFromOtherSourcesDoesNotGenerateIllegalOracleLength() {
        // 经 DuckDB 等中转后源库类型上下文可能丢失，但 VARCHAR 的 Integer.MAX_VALUE
        // 仍不能直接落成 Oracle VARCHAR2(2147483647)
        ColumnMeta clob = DatabaseConverter.convert(
            "duckdb", "oracle", column("v", "VARCHAR", Integer.MAX_VALUE, false));
        assertEquals("CLOB", clob.getType());

        ColumnMeta key = DatabaseConverter.convert(
            "duckdb", "oracle", column("k", "VARCHAR", Integer.MAX_VALUE, true));
        assertEquals("VARCHAR2", key.getType());
        assertEquals(4000, key.getLength());
    }

    @Test
    void generatedDdlUsesWidenedLengthsAndClobForOversizedColumns() {
        TableMeta source = new TableMeta("t_pg");
        source.addColumn(column("id", "BIGINT", 19, true));
        source.addColumn(column("name", "VARCHAR", 100, false));
        source.addColumn(column("remark", "TEXT", Integer.MAX_VALUE, false));
        source.addColumn(column("extra", "VARCHAR", Integer.MAX_VALUE, false));
        source.setDbType("postgresql");

        TableMeta target = DatabaseConverter.convert("postgresql", "oracle", source);
        target.setSchema("test");
        target.setDbType("oracle");
        target.setTable("t_pg");

        String ddl = converter.generateTableDDL(target);

        assertTrue(ddl.contains("\"NAME\" VARCHAR2(300)"), ddl);
        assertTrue(ddl.contains("\"REMARK\" CLOB"), ddl);
        assertTrue(ddl.contains("\"EXTRA\" CLOB"), ddl);
        assertTrue(ddl.contains("PRIMARY KEY (id)"), ddl);
        assertFalse(ddl.contains("2147483647"), ddl);
    }

    private ColumnMeta toOracleColumn(String type, int length, boolean primaryKey) {
        return DatabaseConverter.convert("postgresql", "oracle", column("c", type, length, primaryKey));
    }

    private ColumnMeta column(String name, String type, int length, boolean primaryKey) {
        ColumnMeta column = new ColumnMeta(name, type, length, -1, -1);
        column.setPrimaryKey(primaryKey);
        return column;
    }
}
