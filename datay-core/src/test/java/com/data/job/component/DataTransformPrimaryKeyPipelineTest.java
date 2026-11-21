package com.data.job.component;

import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：读取（StreamJdbcInput）→ 转换（DataTransform）→ 写入（StreamJdbcOutput）时，
 * 自动创建的目标表能够保留上游主键。
 */
public class DataTransformPrimaryKeyPipelineTest {

    @Test
    @Timeout(180000)
    public void testAutoCreatedTargetKeepsPrimaryKey() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");

        File src = new File("target/test-dt-pk-src.duckdb");
        File dst = new File("target/test-dt-pk-dst.duckdb");
        deleteIfExists(src);
        deleteIfExists(dst);

        String srcUrl = "jdbc:duckdb:" + src.getAbsolutePath();
        String dstUrl = "jdbc:duckdb:" + dst.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(srcUrl); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE src_orders (id INTEGER PRIMARY KEY, name VARCHAR, amount DECIMAL(10,2))");
            st.execute("INSERT INTO src_orders VALUES (1, 'alice', 10.50), (2, 'bob', 20.00)");
        }

        String job = "{\n" +
            "  \"units\": [\n" +
            "    {\".id\":\"n1\",\".name\":\"StreamJdbcInput\",\"schema\":\"main\",\"table\":\"src_orders\"," +
            "     \"sourceId\":{\"url\":\"" + srcUrl + "\",\"driver\":\"org.duckdb.DuckDBDriver\"," +
            "\"username\":\"\",\"password\":\"\",\"dbschema\":\"main\"}},\n" +
            "    {\".id\":\"n2\",\".name\":\"DataTransform\",\"outputTable\":\"dwd_orders\"," +
            "     \"rules\":[{\"type\":\"upper\",\"column\":\"name\"}]},\n" +
            "    {\".id\":\"n3\",\".name\":\"StreamJdbcOutput\"," +
            "     \"sourceId\":{\"url\":\"" + dstUrl + "\",\"driver\":\"org.duckdb.DuckDBDriver\"," +
            "\"username\":\"\",\"password\":\"\",\"dbschema\":\"main\"}," +
            "     \"schema\":\"main\",\"table\":\"dwd_orders\",\"model\":\"append\"}\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\"sourceId\":\"n1\",\"targetId\":\"n2\",\"sourcePort\":0},\n" +
            "    {\"sourceId\":\"n2\",\"targetId\":\"n3\",\"sourcePort\":0}\n" +
            "  ]\n" +
            "}";

        try {
            assertDoesNotThrow(() -> new ETLFlowTask().runJob(job));

            // 复用与写入相同的连接池配置读取结果，避免 DuckDB「同文件不同配置」的连接限制
            try (Connection conn = DBUtils.getConnection(dstUrl, "", "")) {
                // 数据已完成转换并写入自动创建的目标表
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, name, amount FROM dwd_orders ORDER BY id")) {
                    assertTrue(rs.next(), "目标表未写入数据");
                    assertEquals(1, rs.getInt("id"));
                    assertEquals("ALICE", rs.getString("name"));
                    assertEquals(0, rs.getBigDecimal("amount").compareTo(new BigDecimal("10.50")));
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt("id"));
                    assertEquals("BOB", rs.getString("name"));
                    assertEquals(0, rs.getBigDecimal("amount").compareTo(new BigDecimal("20.00")));
                    assertTrue(!rs.next(), "目标表数据条数不符");
                }

                // 自动创建的目标表保留主键
                Set<String> primaryKeys = new LinkedHashSet<>();
                try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, "main", "dwd_orders")) {
                    while (rs.next()) {
                        primaryKeys.add(rs.getString("COLUMN_NAME"));
                    }
                }
                assertEquals(Set.of("id"), primaryKeys, "自动创建的目标表未保留主键");
            }
        } finally {
            deleteIfExists(src);
            deleteIfExists(dst);
        }
    }

    private void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("无法删除测试文件: " + file.getAbsolutePath());
        }
    }
}
