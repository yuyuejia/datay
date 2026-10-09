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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端回归：门店销售 SQL 聚合流程。
 *
 * <p>覆盖两个曾导致「门店销售SQL聚合」任务失败的缺陷：
 * <ol>
 *   <li>DuckDB 类型名 {@code DECIMAL(18,2)} 未剥离精度，临时表被建成 {@code VARCHAR}，{@code SUM} 报
 *       {@code sum(VARCHAR)}；</li>
 *   <li>转换后精度丢失导致目标列建成裸 {@code DECIMAL}（DuckDB 默认 {@code DECIMAL(18,3)}），
 *       写入 scale=2 的金额时报 appender 错误。</li>
 * </ol>
 *
 * <p>链路：{@code StreamJdbcInput(DuckDB DECIMAL) → StreamSqlUnit(GROUP BY 聚合) → StreamJdbcOutput}。
 */
public class StreamSqlUnitJdbcFlowTest {

    @Test
    @Timeout(180000)
    public void testAggregateDecimalFromJdbcAndWrite() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");

        File src = new File("target/test-sqlunit-src.duckdb");
        File dw = new File("target/test-sqlunit-dw.duckdb");
        deleteIfExists(src);
        deleteIfExists(dw);

        String srcUrl = "jdbc:duckdb:" + src.getAbsolutePath();
        String dwUrl = "jdbc:duckdb:" + dw.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(srcUrl); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE ods_order (order_id VARCHAR, store_key VARCHAR, order_amount DECIMAL(18,2))");
            st.execute("INSERT INTO ods_order VALUES ('SO1','S001',100.50),('SO2','S001',200.00),('SO3','S002',50.25)");
        }

        // 刻意不对聚合列做内层 CAST，以便暴露临时表列类型被误判为 VARCHAR 的问题
        String sql = "SELECT store_key, CAST(COUNT(*) AS BIGINT) AS order_cnt, " +
            "CAST(SUM(order_amount) AS DECIMAL(18,2)) AS sales_amount FROM ods_order GROUP BY store_key";

        String job = "{\n" +
            "  \"units\": [\n" +
            "    {\".id\":\"n1\",\".name\":\"StreamJdbcInput\",\"schema\":\"main\",\"table\":\"ods_order\"," +
            "     \"sourceId\":{\"url\":\"" + srcUrl + "\",\"driver\":\"org.duckdb.DuckDBDriver\"," +
            "\"username\":\"\",\"password\":\"\",\"dbschema\":\"main\"}},\n" +
            "    {\".id\":\"n2\",\".name\":\"StreamSqlUnit\",\"sql\":\"" + sql + "\"},\n" +
            "    {\".id\":\"n3\",\".name\":\"StreamJdbcOutput\"," +
            "     \"sourceId\":{\"url\":\"" + dwUrl + "\",\"driver\":\"org.duckdb.DuckDBDriver\"," +
            "\"username\":\"\",\"password\":\"\",\"dbschema\":\"main\"}," +
            "     \"schema\":\"main\",\"table\":\"dws_store_sales\",\"model\":\"overwrite\"}\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\"sourceId\":\"n1\",\"targetId\":\"n2\",\"sourcePort\":0},\n" +
            "    {\"sourceId\":\"n2\",\"targetId\":\"n3\",\"sourcePort\":0}\n" +
            "  ]\n" +
            "}";

        assertDoesNotThrow(() -> new ETLFlowTask().runJob(job));

        // 复用与写入相同的连接池配置读取结果，避免 DuckDB「同文件不同配置」的连接限制
        try (Connection conn = DBUtils.getConnection(dwUrl, "", "")) {
            try (ResultSet rs = conn.getMetaData().getColumns(null, "main", "dws_store_sales", null)) {
                boolean sawDecimal = false;
                while (rs.next()) {
                    if ("sales_amount".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        String typeName = rs.getString("TYPE_NAME").toUpperCase();
                        assertTrue(typeName.startsWith("DECIMAL"), "聚合金额列应为 DECIMAL，实际: " + typeName);
                        sawDecimal = true;
                    }
                }
                assertTrue(sawDecimal, "未找到 sales_amount 列");
            }

            Map<String, BigDecimal> actual = new LinkedHashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT store_key, sales_amount FROM dws_store_sales")) {
                while (rs.next()) {
                    actual.put(rs.getString(1), rs.getBigDecimal(2));
                }
            }
            assertEquals(2, actual.size());
            assertEquals(0, actual.get("S001").compareTo(new BigDecimal("300.50")));
            assertEquals(0, actual.get("S002").compareTo(new BigDecimal("50.25")));
        }
    }

    private void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("无法删除测试文件: " + file.getAbsolutePath());
        }
    }
}
