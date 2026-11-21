package com.data.job.component;

import com.data.job.DuckDBEngine;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端回归：多表 JDBCLookup → Join → 目标库写入。
 *
 * <p>链路：
 * <pre>
 * orders  ──> JDBCLookup(customers + regions) ──┐
 *                                               ├──> Join ──> StreamJdbcOutput(dws_order_detail)
 * stores  ──────────────────────────────────────┘
 * </pre>
 *
 * <p>验证：
 * <ol>
 *   <li>同一个数据源下多张维表按顺序回填，第二张表可以引用第一张表回填的字段；</li>
 *   <li>回填后的数据可以继续参与 Join；</li>
 *   <li>Join 输出的数据与表元数据可以正确写入目标表，目标表字段名、类型和取值符合预期。</li>
 * </ol>
 */
public class JDBCLookupJoinFlowTest {

    @Test
    @Timeout(180000)
    public void testMultiTableLookupJoinAndWriteTarget() throws Exception {
        File srcDb = new File("target/test-lookup-join-src.db");
        File dimDb = new File("target/test-lookup-join-dim.db");
        File dstDb = new File("target/test-lookup-join-dst.db");
        deleteIfExists(srcDb);
        deleteIfExists(dimDb);
        deleteIfExists(dstDb);

        String srcUrl = "jdbc:sqlite:" + srcDb.getAbsolutePath();
        String dimUrl = "jdbc:sqlite:" + dimDb.getAbsolutePath();
        String dstUrl = "jdbc:sqlite:" + dstDb.getAbsolutePath();

        prepareSourceDb(srcUrl);
        prepareLookupDb(dimUrl);

        String instanceCode = "test-lookup-join-" + System.nanoTime();
        TaskInstance taskInstance = new TaskInstance()
            .instanceCode(instanceCode)
            .jobCode("test-lookup-join-job");
        ETLFlowTask runner = new ETLFlowTask(taskInstance, null);

        String job = "{\n" +
            "  \"units\": [\n" +
            "    {\".id\":\"orders_input\",\".name\":\"StreamJdbcInput\"," +
            "     \"sourceId\":{\"type\":\"sqlite\",\"url\":\"" + srcUrl + "\",\"dbschema\":\"main\"}," +
            "     \"schema\":\"main\",\"table\":\"orders\"},\n" +
            "    {\".id\":\"lookup\",\".name\":\"JDBCLookup\"," +
            "     \"sourceId\":{\"type\":\"sqlite\",\"url\":\"" + dimUrl + "\",\"dbschema\":\"main\"}," +
            "     \"schema\":\"main\",\"outputTable\":\"orders_enriched\",\"cache\":true," +
            "     \"tables\":[" +
            "       {\"table\":\"customers\",\"localField\":\"customer_id\",\"lookupField\":\"id\"," +
            "        \"selectColumns\":\"name, region_id\",\"outputPrefix\":\"customer_\",\"joinType\":\"LEFT\"}," +
            "       {\"table\":\"regions\",\"localField\":\"customer_region_id\",\"lookupField\":\"id\"," +
            "        \"selectColumns\":\"region_name\",\"outputPrefix\":\"\",\"joinType\":\"LEFT\"}" +
            "     ]},\n" +
            "    {\".id\":\"stores_input\",\".name\":\"StreamJdbcInput\"," +
            "     \"sourceId\":{\"type\":\"sqlite\",\"url\":\"" + srcUrl + "\",\"dbschema\":\"main\"}," +
            "     \"schema\":\"main\",\"table\":\"stores\"},\n" +
            "    {\".id\":\"join\",\".name\":\"Join\"," +
            "     \"outputTable\":\"dws_order_detail\",\"fromTable\":\"orders_enriched\"," +
            "     \"selectColumns\":\"orders_enriched.order_id AS order_id, " +
            "orders_enriched.customer_name AS customer_name, " +
            "orders_enriched.customer_region_id AS customer_region_id, " +
            "orders_enriched.region_name AS region_name, " +
            "orders_enriched.amount AS amount, stores.store_name AS store_name\"," +
            "     \"joins\":[{\"type\":\"LEFT\",\"table\":\"stores\"," +
            "       \"leftTable\":\"orders_enriched\",\"leftField\":\"store_id\",\"rightField\":\"store_id\"}]},\n" +
            "    {\".id\":\"target\",\".name\":\"StreamJdbcOutput\"," +
            "     \"sourceId\":{\"type\":\"sqlite\",\"url\":\"" + dstUrl + "\",\"dbschema\":\"main\"}," +
            "     \"schema\":\"main\",\"table\":\"dws_order_detail\",\"model\":\"overwrite\"}\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\"sourceId\":\"orders_input\",\"targetId\":\"lookup\",\"sourcePort\":0},\n" +
            "    {\"sourceId\":\"lookup\",\"targetId\":\"join\",\"sourcePort\":0},\n" +
            "    {\"sourceId\":\"stores_input\",\"targetId\":\"join\",\"sourcePort\":0},\n" +
            "    {\"sourceId\":\"join\",\"targetId\":\"target\",\"sourcePort\":0}\n" +
            "  ]\n" +
            "}";

        try {
            runner.runJob(job);
            assertEnrichedDuckDbTable(instanceCode);
            assertTargetData(dstUrl);
            assertTargetMetadata(dstUrl);
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
            DBUtils.closePool(srcUrl, null);
            DBUtils.closePool(dimUrl, null);
            DBUtils.closePool(dstUrl, null);
        }
    }

    private void prepareSourceDb(String url) throws Exception {
        try (Connection conn = DriverManager.getConnection(url); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, store_id INTEGER, amount INTEGER)");
            st.execute("INSERT INTO orders VALUES (1, 100, 10, 1000)");
            st.execute("INSERT INTO orders VALUES (2, 101, 20, 2000)");
            st.execute("INSERT INTO orders VALUES (3, 999, 10, 3000)");
            st.execute("CREATE TABLE stores (store_id INTEGER, store_name VARCHAR(50))");
            st.execute("INSERT INTO stores VALUES (10, '上海门店')");
            st.execute("INSERT INTO stores VALUES (20, '北京门店')");
        }
    }

    private void prepareLookupDb(String url) throws Exception {
        try (Connection conn = DriverManager.getConnection(url); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE customers (id INTEGER, name VARCHAR(50), region_id INTEGER)");
            st.execute("INSERT INTO customers VALUES (100, 'alice', 1)");
            st.execute("INSERT INTO customers VALUES (101, 'bob', 2)");
            st.execute("CREATE TABLE regions (id INTEGER, region_name VARCHAR(50))");
            st.execute("INSERT INTO regions VALUES (1, '华东')");
            st.execute("INSERT INTO regions VALUES (2, '华北')");
        }
    }

    /**
     * 验证 JDBCLookup 输出的多表回填结果在进入 Join 后，DuckDB 中间表的字段与数据正确。
     */
    private void assertEnrichedDuckDbTable(String instanceCode) throws Exception {
        try (
            Connection conn = DuckDBEngine.getInstance().getConnection(instanceCode);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM main.orders_enriched ORDER BY order_id")
        ) {
            ResultSetMetaData md = rs.getMetaData();
            assertEquals(7, md.getColumnCount());
            assertEquals("order_id", md.getColumnName(1));
            assertEquals("customer_id", md.getColumnName(2));
            assertEquals("store_id", md.getColumnName(3));
            assertEquals("amount", md.getColumnName(4));
            assertEquals("customer_name", md.getColumnName(5));
            assertEquals("customer_region_id", md.getColumnName(6));
            assertEquals("region_name", md.getColumnName(7));
            assertTrue(md.getColumnTypeName(5).toUpperCase().contains("VARCHAR"));
            assertTrue(md.getColumnTypeName(7).toUpperCase().contains("VARCHAR"));

            assertTrue(rs.next());
            assertEquals("alice", rs.getString("customer_name"));
            assertEquals(1, rs.getInt("customer_region_id"));
            assertEquals("华东", rs.getString("region_name"));

            assertTrue(rs.next());
            assertEquals("bob", rs.getString("customer_name"));
            assertEquals(2, rs.getInt("customer_region_id"));
            assertEquals("华北", rs.getString("region_name"));

            assertTrue(rs.next());
            assertNull(rs.getString("customer_name"));
            assertNull(rs.getObject("customer_region_id"));
            assertNull(rs.getString("region_name"));
            assertTrue(!rs.next());
        }
    }

    private void assertTargetData(String url) throws Exception {
        try (Connection conn = DriverManager.getConnection(url); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                "SELECT order_id, customer_name, customer_region_id, region_name, amount, store_name " +
                    "FROM dws_order_detail ORDER BY order_id")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("order_id"));
                assertEquals("alice", rs.getString("customer_name"));
                assertEquals(1, rs.getInt("customer_region_id"));
                assertEquals("华东", rs.getString("region_name"));
                assertEquals(1000, rs.getInt("amount"));
                assertEquals("上海门店", rs.getString("store_name"));

                assertTrue(rs.next());
                assertEquals(2, rs.getInt("order_id"));
                assertEquals("bob", rs.getString("customer_name"));
                assertEquals(2, rs.getInt("customer_region_id"));
                assertEquals("华北", rs.getString("region_name"));
                assertEquals(2000, rs.getInt("amount"));
                assertEquals("北京门店", rs.getString("store_name"));

                // 客户 999 不存在：多表 lookup 的 LEFT 关联补 null，Join 后仍保留订单
                assertTrue(rs.next());
                assertEquals(3, rs.getInt("order_id"));
                assertNull(rs.getString("customer_name"));
                assertNull(rs.getObject("customer_region_id"));
                assertNull(rs.getString("region_name"));
                assertEquals(3000, rs.getInt("amount"));
                assertEquals("上海门店", rs.getString("store_name"));

                assertTrue(!rs.next());
            }
        }
    }

    private void assertTargetMetadata(String url) throws Exception {
        try (Connection conn = DriverManager.getConnection(url)) {
            TableMeta meta = DBUtils.getTableMetaData(conn, "main", "dws_order_detail");
            assertNotNull(meta);
            assertEquals("dws_order_detail", meta.getTable());

            List<ColumnMeta> columns = meta.columns();
            assertEquals(6, columns.size());
            assertNumericColumn(columns.get(0), "order_id");
            assertStringColumn(columns.get(1), "customer_name");
            assertNumericColumn(columns.get(2), "customer_region_id");
            assertStringColumn(columns.get(3), "region_name");
            assertNumericColumn(columns.get(4), "amount");
            assertStringColumn(columns.get(5), "store_name");
        }
    }

    private void assertNumericColumn(ColumnMeta column, String name) {
        assertEquals(name, column.getName());
        assertNotNull(column.getType());
        assertTrue(
            column.getType().toUpperCase().startsWith("INT"),
            "字段 " + name + " 类型应为整数，实际为 " + column.getType()
        );
    }

    /**
     * DuckDB 的结果集不携带 VARCHAR 长度，处理组件会用上游元数据补回原始长度。
     * 本例上游字符串字段均为 VARCHAR(50)，目标表应保持 50，而不是退化成 LONGTEXT 或默认 255。
     */
    private void assertStringColumn(ColumnMeta column, String name) {
        assertEquals(name, column.getName());
        assertNotNull(column.getType());
        assertEquals("VARCHAR", column.getType().toUpperCase());
        assertEquals(50, column.getLength(), "字段 " + name + " 应保留上游 VARCHAR 长度");
    }

    private void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("无法删除测试文件: " + file.getAbsolutePath());
        }
    }
}
