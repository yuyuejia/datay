package com.data.metadata.util;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨库同步到 DuckDB 时 DECIMAL 精度/scale 的回归测试。
 */
class DuckDBDecimalMetadataTest {

    @Test
    void shouldNormalizeDecimalScaleWhenAppending() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb::memory:")) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE t (id DECIMAL(10,2), amount DECIMAL(10,2))");
            }

            ColumnMeta id = decimalColumn("id", 10, 2);
            ColumnMeta amount = decimalColumn("amount", 10, 2);
            try (DuckDBAppender appender = ((DuckDBConnection) conn).createAppender("main", "t")) {
                appender.beginRow();
                // Oracle/PostgreSQL 对整数金额常返回 scale=0 的 BigDecimal
                DBUtils.appendValue(appender, id, new BigDecimal("1"));
                DBUtils.appendValue(appender, amount, new BigDecimal("60"));
                appender.endRow();
            }

            try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, amount FROM t")
            ) {
                assertTrue(rs.next());
                assertEquals(new BigDecimal("1.00"), rs.getBigDecimal(1));
                assertEquals(new BigDecimal("60.00"), rs.getBigDecimal(2));
            }
        }
    }

    @Test
    void shouldKeepDecimalPrecisionFromResultSetMeta() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb::memory:")) {
            try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT CAST(1.23 AS DECIMAL(18,2)) AS amount")
            ) {
                TableMeta duckdbTable = DBUtils.getTableMetaFromResultSet(rs.getMetaData());
                duckdbTable.setTable("t");
                ColumnMeta amount = duckdbTable.columns().get(0);
                assertEquals(18, amount.getPrecision());
                assertEquals(2, amount.getScale());

                TableMeta mysqlTable = DatabaseConverter.convert("duckdb", "mysql", duckdbTable);
                String ddl = DatabaseConverter.generateTableDDL("mysql", mysqlTable);
                assertTrue(ddl.toUpperCase().contains("DECIMAL(18,2)"), ddl);
            }
        }
    }

    @Test
    void shouldMapOracleNumberWithoutPrecisionToDecimalWithScale() {
        ColumnMeta source = new ColumnMeta("ID", "NUMBER");
        source.setPrecision(0);
        source.setScale(-127);

        ColumnMeta target = DatabaseConverter.convert("oracle", "duckdb", source);
        assertEquals("DECIMAL", target.getType());
        assertEquals(38, target.getPrecision());
        assertEquals(10, target.getScale());
    }

    private ColumnMeta decimalColumn(String name, int precision, int scale) {
        ColumnMeta column = new ColumnMeta(name, "DECIMAL");
        column.setPrecision(precision);
        column.setScale(scale);
        return column;
    }
}
