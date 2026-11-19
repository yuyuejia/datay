package com.data.metadata.util;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DuckDB 整数聚合结果（HUGEINT）写入目标表的回归测试。
 *
 * <p>DuckDB 对 {@code SUM(BIGINT)} 等整数聚合返回 {@code HUGEINT}。如果类型转换表未登记该类型，
 * 目标表会被建成 {@code VARCHAR}，而 Appender 写入 {@code HUGEINT} 值时会报
 * "expected ... VARCHAR, actual ... HUGEINT"。</p>
 */
class DuckDBIntegerMetadataTest {

    @Test
    void shouldPersistHugeIntAggregateWithoutTypeMismatch() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb::memory:")) {
            TableMeta sourceMeta;
            String storeName;
            Object total;
            try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                    "SELECT 's1' AS store_name, SUM(quantity) AS total FROM (VALUES (1), (2)) t(quantity)"
                )
            ) {
                sourceMeta = DBUtils.getTableMetaFromResultSet(rs.getMetaData());
                assertTrue(rs.next());
                storeName = rs.getString(1);
                total = rs.getObject(2);
            }

            ColumnMeta totalColumn = sourceMeta
                .columns()
                .stream()
                .filter(column -> "total".equals(column.getName()))
                .findFirst()
                .orElseThrow();
            assertEquals("HUGEINT", totalColumn.getType());

            TableMeta targetMeta = DatabaseConverter.convert("duckdb", "duckdb", sourceMeta);
            targetMeta.setTable("agg_result");
            ColumnMeta targetTotal = targetMeta
                .columns()
                .stream()
                .filter(column -> "total".equals(column.getName()))
                .findFirst()
                .orElseThrow();
            assertEquals("HUGEINT", targetTotal.getType());

            String ddl = DatabaseConverter.generateTableDDL("duckdb", targetMeta);
            assertTrue(ddl.toUpperCase().contains("HUGEINT"), ddl);
            try (Statement st = conn.createStatement()) {
                st.execute(ddl);
            }

            try (DuckDBAppender appender = ((DuckDBConnection) conn).createAppender("main", "agg_result")) {
                appender.beginRow();
                DBUtils.appendValue(appender, targetMeta.columns().get(0), storeName);
                DBUtils.appendValue(appender, targetMeta.columns().get(1), total);
                appender.endRow();
            }

            try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT store_name, total FROM agg_result")
            ) {
                assertTrue(rs.next());
                assertEquals("s1", rs.getString(1));
                assertEquals(3L, rs.getLong(2));
            }
        }
    }
}
