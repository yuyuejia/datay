package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * StreamJdbcInput 并发同步的端到端流程测试（真实走 DAGParser + ETLFlowTask + StreamJdbcOutput）。
 *
 * <p>覆盖两类容易出问题的场景：</p>
 * <ol>
 *   <li>单表 + 多线程 + 自动分区：验证按增量字段 / 主键自动分区后，每个线程只读取自己的区间，
 *       目标表数据完整且不重复，增量水位在任务结束后为全局最大值，任务正常结束；</li>
 *   <li>多表 + 多线程 + 无可用分区字段：验证回退为「按表序号分发」后，每张表只被一个线程读取，
 *       数据不重复不遗漏，任务正常结束；</li>
 *   <li>单线程：验证 parallelism=1 时不走分区，数据完整、增量水位随批次保存、二次运行不重复同步，
 *       多表顺序读取也能正常结束。</li>
 * </ol>
 */
class StreamJdbcInputPartitionFlowTest {

    private static final String DRIVER = "org.duckdb.DuckDBDriver";

    @Test
    @Timeout(180000)
    void singleTableMultiThreadWithPartition() throws Exception {
        Class.forName(DRIVER);

        String srcUrl = duckDbUrl("test-partition-flow-src.duckdb");
        String dwUrl = duckDbUrl("test-partition-flow-dw.duckdb");
        deleteIfExists("target/test-partition-flow-src.duckdb");
        deleteIfExists("target/test-partition-flow-dw.duckdb");

        createTable(srcUrl, "orders", 1, 500, true);

        String job = jobJson(
            srcUrl,
            dwUrl,
            "orders",
            "4",
            "\"incrColumn\":\"id\",",
            "overwrite"
        );

        ETLFlowTask task = newTask(new InMemoryStatusStorage(), "test-partition-flow-" + System.nanoTime());
        assertDoesNotThrow(() -> task.runJob(job), "单表并发分区同步任务应正常结束");

        try (Connection conn = DBUtils.getConnection(dwUrl, "", "")) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*), COUNT(DISTINCT id), MIN(id), MAX(id), SUM(id), SUM(amount) FROM main.orders")) {
                assertTrue(rs.next());
                assertEquals(500, rs.getInt(1), "目标表行数应与源表一致");
                assertEquals(500, rs.getInt(2), "目标表主键不应重复");
                assertEquals(1, rs.getInt(3));
                assertEquals(500, rs.getInt(4));
                assertEquals(125250L, rs.getLong(5), "id 累计和应完整");
                assertEquals(0, rs.getBigDecimal(6).compareTo(new BigDecimal("187875.00")),
                    "amount 累计和应完整");
            }
        }

        // 任务结束且所有分区汇合后，全局增量水位应为 500
        Object watermark = task.getContext().getStatusMap().get("n1.lastIncrValue.orders");
        assertEquals("500", String.valueOf(watermark), "并发分区完成后应写入全局最大增量水位");
    }

    @Test
    @Timeout(180000)
    void multiTableMultiThreadWithoutPartitionColumn() throws Exception {
        Class.forName(DRIVER);

        String srcUrl = duckDbUrl("test-multitable-flow-src.duckdb");
        String dwUrl = duckDbUrl("test-multitable-flow-dw.duckdb");
        deleteIfExists("target/test-multitable-flow-src.duckdb");
        deleteIfExists("target/test-multitable-flow-dw.duckdb");

        // 三张表均无主键、无增量列，触发「按表序号分发」的回退逻辑
        createTableWithoutPrimaryKey(srcUrl, "t_a", 1, 200);
        createTableWithoutPrimaryKey(srcUrl, "t_b", 1001, 1200);
        createTableWithoutPrimaryKey(srcUrl, "t_c", 2001, 2200);

        String job = jobJson(srcUrl, dwUrl, "t_a,t_b,t_c", "4", "", "overwrite");

        ETLFlowTask task = newTask(new InMemoryStatusStorage(), "test-multitable-flow-" + System.nanoTime());
        assertDoesNotThrow(() -> task.runJob(job), "多表无分区字段同步任务应正常结束");

        try (Connection conn = DBUtils.getConnection(dwUrl, "", "")) {
            assertTable(conn, "t_a", 200, 1, 200, 20100L);
            assertTable(conn, "t_b", 200, 1001, 1200, 220100L);
            assertTable(conn, "t_c", 200, 2001, 2200, 420100L);

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM main.t_a) + (SELECT COUNT(*) FROM main.t_b) " +
                         "+ (SELECT COUNT(*) FROM main.t_c)")) {
                assertTrue(rs.next());
                assertEquals(600, rs.getInt(1), "三张目标表总行数应等于源表总和");
            }
        }
    }

    @Test
    @Timeout(180000)
    void singleTableSingleThreadSync() throws Exception {
        Class.forName(DRIVER);

        String srcUrl = duckDbUrl("test-single-thread-src.duckdb");
        String dwUrl = duckDbUrl("test-single-thread-dw.duckdb");
        deleteIfExists("target/test-single-thread-src.duckdb");
        deleteIfExists("target/test-single-thread-dw.duckdb");

        createTable(srcUrl, "orders", 1, 500, true);

        // parallelism=1：不启用分区，整表顺序读取；append 便于二次运行校验增量水位
        String job = jobJson(srcUrl, dwUrl, "orders", "1", "\"incrColumn\":\"id\",", "append");

        InMemoryStatusStorage statusStorage = new InMemoryStatusStorage();
        String jobCode = "test-single-thread-" + System.nanoTime();

        ETLFlowTask firstRun = newTask(statusStorage, jobCode);
        assertDoesNotThrow(() -> firstRun.runJob(job), "单线程首次同步任务应正常结束");

        try (Connection conn = DBUtils.getConnection(dwUrl, "", "")) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*), COUNT(DISTINCT id), MIN(id), MAX(id), SUM(id), SUM(amount) FROM main.orders")) {
                assertTrue(rs.next());
                assertEquals(500, rs.getInt(1), "单线程目标表行数应与源表一致");
                assertEquals(500, rs.getInt(2), "单线程目标表主键不应重复");
                assertEquals(1, rs.getInt(3));
                assertEquals(500, rs.getInt(4));
                assertEquals(125250L, rs.getLong(5), "单线程 id 累计和应完整");
                assertEquals(0, rs.getBigDecimal(6).compareTo(new BigDecimal("187875.00")),
                    "单线程 amount 累计和应完整");
            }
        }
        assertEquals("500",
            String.valueOf(firstRun.getContext().getStatusMap().get("n1.lastIncrValue.orders")),
            "单线程首次同步后应保存增量水位 500");

        ETLFlowTask secondRun = newTask(statusStorage, jobCode);
        assertDoesNotThrow(() -> secondRun.runJob(job), "单线程二次增量同步任务应正常结束");
        assertEquals(500, countRows(dwUrl, "main.orders"),
            "单线程二次运行应读取 0 行，目标表仍为 500 行");
        assertEquals("500",
            String.valueOf(secondRun.getContext().getStatusMap().get("n1.lastIncrValue.orders")),
            "单线程二次运行后水位应保持 500");
    }

    @Test
    @Timeout(180000)
    void multiTableSingleThreadSync() throws Exception {
        Class.forName(DRIVER);

        String srcUrl = duckDbUrl("test-multitable-single-thread-src.duckdb");
        String dwUrl = duckDbUrl("test-multitable-single-thread-dw.duckdb");
        deleteIfExists("target/test-multitable-single-thread-src.duckdb");
        deleteIfExists("target/test-multitable-single-thread-dw.duckdb");

        createTableWithoutPrimaryKey(srcUrl, "t_a", 1, 200);
        createTableWithoutPrimaryKey(srcUrl, "t_b", 1001, 1200);
        createTableWithoutPrimaryKey(srcUrl, "t_c", 2001, 2200);

        String job = jobJson(srcUrl, dwUrl, "t_a,t_b,t_c", "1", "", "overwrite");

        ETLFlowTask task = newTask(new InMemoryStatusStorage(), "test-multitable-single-thread-" + System.nanoTime());
        assertDoesNotThrow(() -> task.runJob(job), "多表单线程同步任务应正常结束");

        try (Connection conn = DBUtils.getConnection(dwUrl, "", "")) {
            assertTable(conn, "t_a", 200, 1, 200, 20100L);
            assertTable(conn, "t_b", 200, 1001, 1200, 220100L);
            assertTable(conn, "t_c", 200, 2001, 2200, 420100L);

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM main.t_a) + (SELECT COUNT(*) FROM main.t_b) " +
                         "+ (SELECT COUNT(*) FROM main.t_c)")) {
                assertTrue(rs.next());
                assertEquals(600, rs.getInt(1), "单线程三张目标表总行数应等于源表总和");
            }
        }
    }

    @Test
    @Timeout(180000)
    void incrementalSecondRunDoesNotResync() throws Exception {
        Class.forName(DRIVER);

        String srcUrl = duckDbUrl("test-partition-incr-src.duckdb");
        String dwUrl = duckDbUrl("test-partition-incr-dw.duckdb");
        deleteIfExists("target/test-partition-incr-src.duckdb");
        deleteIfExists("target/test-partition-incr-dw.duckdb");

        createTable(srcUrl, "orders", 1, 500, true);

        // append 模式：第二次运行不会清空目标表，若增量过滤失效会变成 1000 行
        String job = jobJson(
            srcUrl,
            dwUrl,
            "orders",
            "4",
            "\"incrColumn\":\"id\",",
            "append"
        );

        InMemoryStatusStorage statusStorage = new InMemoryStatusStorage();
        String jobCode = "test-partition-incr-" + System.nanoTime();

        ETLFlowTask firstRun = newTask(statusStorage, jobCode);
        assertDoesNotThrow(() -> firstRun.runJob(job), "首次增量同步任务应正常结束");
        assertEquals(500, countRows(dwUrl, "main.orders"), "首次运行应写入全部 500 行");

        ETLFlowTask secondRun = newTask(statusStorage, jobCode);
        assertDoesNotThrow(() -> secondRun.runJob(job), "二次增量同步任务应正常结束");

        assertEquals(500, countRows(dwUrl, "main.orders"),
            "二次运行应读取 0 行，目标表仍为 500 行（增量水位生效）");
        Object watermark = secondRun.getContext().getStatusMap().get("n1.lastIncrValue.orders");
        assertEquals("500", String.valueOf(watermark), "二次运行后水位应保持 500");
    }

    @Test
    @Timeout(180000)
    void timestampIncrColumnMultiThreadIncremental() throws Exception {
        Class.forName(DRIVER);

        String srcUrl = duckDbUrl("test-time-incr-src.duckdb");
        String dwUrl = duckDbUrl("test-time-incr-dw.duckdb");
        deleteIfExists("target/test-time-incr-src.duckdb");
        deleteIfExists("target/test-time-incr-dw.duckdb");

        createTableWithTimestamp(srcUrl, "orders_time", 1, 500, LocalDateTime.of(2026, 1, 1, 0, 0, 0));

        // 时间字段作为增量字段，多线程下应回退到单列主键分区，增量条件仍按时间字段过滤
        String job = jobJson(srcUrl, dwUrl, "orders_time", "4", "\"incrColumn\":\"update_time\",", "append");

        InMemoryStatusStorage statusStorage = new InMemoryStatusStorage();
        String jobCode = "test-time-incr-" + System.nanoTime();

        ETLFlowTask firstRun = newTask(statusStorage, jobCode);
        assertDoesNotThrow(() -> firstRun.runJob(job), "时间字段首次多线程同步应正常结束");
        assertEquals(500, countRows(dwUrl, "main.orders_time"), "首次运行应写入全部 500 行");
        assertTimestampWatermark(firstRun.getContext().getStatusMap().get("n1.lastIncrValue.orders_time"),
            "2026-01-01T00:08:20");

        appendRowsWithTimestamp(srcUrl, "orders_time", 501, 510, LocalDateTime.of(2026, 1, 1, 0, 10, 0));

        ETLFlowTask secondRun = newTask(statusStorage, jobCode);
        assertDoesNotThrow(() -> secondRun.runJob(job), "时间字段二次多线程增量同步应正常结束");
        assertEquals(510, countRows(dwUrl, "main.orders_time"), "二次运行只应追加 10 行新数据");
        assertTimestampWatermark(secondRun.getContext().getStatusMap().get("n1.lastIncrValue.orders_time"),
            "2026-01-01T00:10:10");
    }

    private ETLFlowTask newTask(InMemoryStatusStorage statusStorage, String jobCode) {
        ETLFlowTask task = new ETLFlowTask();
        task.getContext().setJobCode(jobCode);
        task.setStatusStorageStrategy(statusStorage);
        return task;
    }

    private int countRows(String url, String qualifiedTable) throws SQLException {
        try (Connection conn = DBUtils.getConnection(url, "", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + qualifiedTable)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static class InMemoryStatusStorage implements com.data.status.StatusStorageStrategy {

        private final java.util.Map<String, java.util.Map<String, Object>> store = new java.util.HashMap<>();

        @Override
        public synchronized boolean saveStatus(java.util.Map<String, Object> status, String jobCode) {
            store.put(jobCode, new java.util.HashMap<>(status));
            return true;
        }

        @Override
        public synchronized java.util.Map<String, Object> loadStatus(String jobCode) {
            java.util.Map<String, Object> status = store.get(jobCode);
            return status == null ? null : new java.util.HashMap<>(status);
        }

        @Override
        public synchronized boolean deleteStatus(String jobCode) {
            store.remove(jobCode);
            return true;
        }

        @Override
        public synchronized java.util.List<String> listJobCodes() {
            return new java.util.ArrayList<>(store.keySet());
        }

        @Override
        public String getStrategyName() {
            return "InMemory";
        }
    }

    private String jobJson(String srcUrl, String dwUrl, String tables, String parallelism, String extraSourceConfig,
                           String sinkModel) {
        return "{\n" +
            "  \"units\": [\n" +
            "    {\".id\":\"n1\",\".name\":\"StreamJdbcInput\",\"schema\":\"main\",\"table\":\"" + tables + "\"," +
            "     \"parallelism\":\"" + parallelism + "\"," + extraSourceConfig +
            "     \"sourceId\":{\"url\":\"" + srcUrl + "\",\"driver\":\"" + DRIVER + "\"," +
            "\"username\":\"\",\"password\":\"\",\"dbschema\":\"main\"}},\n" +
            "    {\".id\":\"n2\",\".name\":\"StreamJdbcOutput\",\"schema\":\"main\",\"model\":\"" + sinkModel + "\"," +
            "     \"sourceId\":{\"url\":\"" + dwUrl + "\",\"driver\":\"" + DRIVER + "\"," +
            "\"username\":\"\",\"password\":\"\",\"dbschema\":\"main\"}}\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\"sourceId\":\"n1\",\"targetId\":\"n2\",\"sourcePort\":0}\n" +
            "  ]\n" +
            "}";
    }

    private void createTable(String url, String table, int startId, int endId, boolean withPrimaryKey)
            throws SQLException {
        try (Connection conn = DriverManager.getConnection(url); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE main." + table + " (id INT" + (withPrimaryKey ? " PRIMARY KEY" : "") +
                ", name VARCHAR, amount DECIMAL(10,2))");
        }
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO main." + table + " (id, name, amount) VALUES (?, ?, ?)")) {
            for (int i = startId; i <= endId; i++) {
                ps.setInt(1, i);
                ps.setString(2, "row-" + i);
                ps.setBigDecimal(3, BigDecimal.valueOf(i).multiply(new BigDecimal("1.50")));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void createTableWithTimestamp(String url, String table, int startId, int endId, LocalDateTime base)
            throws SQLException {
        try (Connection conn = DBUtils.getConnection(url, "", ""); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE main." + table + " (id INT PRIMARY KEY, name VARCHAR, amount DECIMAL(10,2),"
                + " update_time TIMESTAMP)");
        }
        appendRowsWithTimestamp(url, table, startId, endId, base);
    }

    private void appendRowsWithTimestamp(String url, String table, int startId, int endId, LocalDateTime base)
            throws SQLException {
        try (Connection conn = DBUtils.getConnection(url, "", "");
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO main." + table + " (id, name, amount, update_time) VALUES (?, ?, ?, ?)")) {
            for (int i = startId; i <= endId; i++) {
                ps.setInt(1, i);
                ps.setString(2, "row-" + i);
                ps.setBigDecimal(3, BigDecimal.valueOf(i).multiply(new BigDecimal("1.50")));
                ps.setTimestamp(4, Timestamp.valueOf(base.plusSeconds(i - startId + 1)));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void assertTimestampWatermark(Object watermark, String expected) {
        assertTrue(watermark != null, "应保存时间字段增量水位");
        String text = String.valueOf(watermark).replace(' ', 'T').replaceAll("\\.0+$", "");
        assertEquals(expected, text, "时间字段增量水位应为全局最大值");
    }

    private void createTableWithoutPrimaryKey(String url, String table, int startId, int endId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE main." + table + " (id INT, name VARCHAR)");
        }
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO main." + table + " (id, name) VALUES (?, ?)")) {
            for (int i = startId; i <= endId; i++) {
                ps.setInt(1, i);
                ps.setString(2, "row-" + i);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void assertTable(Connection conn, String table, int expectedRows, int expectedMin, int expectedMax,
                             long expectedIdSum) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*), COUNT(DISTINCT id), MIN(id), MAX(id), SUM(id) FROM main." + table)) {
            assertTrue(rs.next(), "目标表 " + table + " 不存在");
            assertEquals(expectedRows, rs.getInt(1), table + " 行数不正确");
            assertEquals(expectedRows, rs.getInt(2), table + " 主键重复");
            assertEquals(expectedMin, rs.getInt(3), table + " 最小值不正确");
            assertEquals(expectedMax, rs.getInt(4), table + " 最大值不正确");
            assertEquals(expectedIdSum, rs.getLong(5), table + " 数据不完整");
        }
    }

    private String duckDbUrl(String fileName) {
        return "jdbc:duckdb:" + new File("target/" + fileName).getAbsolutePath();
    }

    private void deleteIfExists(String path) {
        File file = new File(path);
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("无法删除测试文件: " + file.getAbsolutePath());
        }
    }
}
