package com.data.metadata.util;

import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DuckDB 结果集 VARCHAR 长度补全测试。
 *
 * <p>DuckDB 的 VARCHAR 结果集元数据中 precision 为 {@link Integer#MAX_VALUE}，
 * 处理组件重新读取元数据后会丢失原始长度。这里验证 {@link DBUtils#getTableMetaFromResultSet}
 * 能按字段名从上游元数据补回 VARCHAR 长度。
 */
public class DuckDBVarcharLengthFillTest {

    @Test
    public void testResultMetaFillsVarcharLengthFromSourceMeta() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb::memory:")) {
            conn.createStatement().execute("CREATE TABLE src (id INTEGER, name VARCHAR, remark VARCHAR)");
            conn.createStatement().execute("INSERT INTO src VALUES (1, 'alice', 'hello')");

            TableMeta source = new TableMeta("src");
            source.addColumn(new ColumnMeta("id", "INTEGER"));
            source.addColumn(varchar("name", 50));
            // remark 上游没有可用长度，应保持 DuckDB 返回的无界长度
            source.addColumn(new ColumnMeta("remark", "VARCHAR"));

            try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, name, remark FROM src")) {
                TableMeta result = DBUtils.getTableMetaFromResultSet(rs.getMetaData(), source);

                ColumnMeta name = findColumn(result, "name");
                assertNotNull(name);
                assertEquals(50, name.getLength());
                assertEquals(50, name.getPrecision());

                ColumnMeta remark = findColumn(result, "remark");
                assertNotNull(remark);
                assertEquals(Integer.MAX_VALUE, remark.getLength());
            }
        }
    }

    @Test
    public void testFillCharacterLengthsFromMultipleSources() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb::memory:")) {
            conn.createStatement().execute("CREATE TABLE left_t (id INTEGER, city VARCHAR)");
            conn.createStatement().execute("INSERT INTO left_t VALUES (1, '上海')");

            TableMeta left = new TableMeta("left_t");
            left.addColumn(new ColumnMeta("id", "INTEGER"));
            left.addColumn(varchar("city", 32));

            TableMeta right = new TableMeta("right_t");
            right.addColumn(varchar("store_name", 64));

            try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, city FROM left_t")) {
                ResultSetMetaData metaData = rs.getMetaData();
                TableMeta result = DBUtils.getTableMetaFromResultSet(metaData);
                DBUtils.fillCharacterColumnLengths(result, left, right);

                assertEquals(32, findColumn(result, "city").getLength());
            }
        }
    }

    private ColumnMeta varchar(String name, int length) {
        ColumnMeta column = new ColumnMeta(name, "VARCHAR");
        column.setLength(length);
        column.setPrecision(length);
        return column;
    }

    private ColumnMeta findColumn(TableMeta meta, String name) {
        for (ColumnMeta column : meta.columns()) {
            if (column.getName() != null && column.getName().equalsIgnoreCase(name)) {
                return column;
            }
        }
        return null;
    }
}
