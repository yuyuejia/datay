package com.data.metadata.impl;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.util.DBUtils;
import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归验证：PG 的 BIGINT 在 JDBC 元数据中返回 {@code int8}，不能被 ClickHouse 方言
 * 误判为 8 位 {@code Int8}，否则经 JDBCLookup / StreamSqlUnit 后只剩最低字节。
 */
public class PostgresqlInt8TypeTest {

    private static final long BIG_VALUE = 1000000000000000083L;

    @Test
    public void pgInternalTypeNamesShouldNormalize() {
        assertEquals("BIGINT", PostgresqlConverter.normalizeSourceTypeName("int8"));
        assertEquals("INTEGER", PostgresqlConverter.normalizeSourceTypeName("int4"));
        assertEquals("SMALLINT", PostgresqlConverter.normalizeSourceTypeName("int2"));
        assertEquals("BIGINT", PostgresqlConverter.normalizeSourceTypeName("bigserial"));
        assertEquals("BOOLEAN", PostgresqlConverter.normalizeSourceTypeName("bool"));
        // 非内部类型保持原样
        assertEquals("BIGINT", PostgresqlConverter.normalizeSourceTypeName("BIGINT"));
        assertEquals("TIMESTAMPTZ", PostgresqlConverter.normalizeSourceTypeName("TIMESTAMPTZ"));
        assertEquals("timestamptz", PostgresqlConverter.normalizeSourceTypeName("timestamptz"));
    }

    @Test
    public void pgInt8ShouldNormalizeToBigintForAllTargets() {
        ColumnMeta pgColumn = new ColumnMeta("big_value", "int8");

        assertEquals("BIGINT", DatabaseConverter.convert("postgresql", "duckdb", pgColumn).getType());
        assertEquals("BIGINT", DatabaseConverter.convert("postgresql", "postgresql", pgColumn).getType());
        assertEquals("BIGINT", DatabaseConverter.convert("postgresql", "mysql", pgColumn).getType());
        // ClickHouse 的 Int8 是 8 位整数，PG 的 int8 必须落成 Int64
        assertEquals("Int64", DatabaseConverter.convert("postgresql", "clickhouse", pgColumn).getType());
    }

    @Test
    public void pgInt8ValueShouldSurviveDuckDbTemporaryTable() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");

        ColumnMeta duckDbColumn = DatabaseConverter.convert("postgresql", "duckdb", new ColumnMeta("big_value", "int8"));
        assertEquals("BIGINT", duckDbColumn.getType());

        try (DuckDBConnection conn = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE tmp (big_value BIGINT)");
            }
            try (var appender = conn.createAppender("main", "tmp")) {
                appender.beginRow();
                DBUtils.appendValue(appender, duckDbColumn, BIG_VALUE);
                appender.endRow();
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT big_value FROM tmp")) {
                assertTrue(rs.next());
                assertEquals(BIG_VALUE, rs.getLong(1), "BIGINT 值不应被截断");
            }
        }
    }
}