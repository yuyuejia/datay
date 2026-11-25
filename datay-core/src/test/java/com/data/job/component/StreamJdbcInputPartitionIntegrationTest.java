package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * StreamJdbcInput 并发分区读取的端到端验证（使用本地 DuckDB）。
 *
 * <p>多个线程共享同一个组件实例、分别携带不同的 workerIndex 执行，验证：
 * <ol>
 *   <li>每个线程读取不同的主键区间，全部数据恰好被读取一次；</li>
 *   <li>并发读取完成后增量水位是所有分区中的全局最大值；</li>
 *   <li>没有可用分区字段时单表只由一个线程读取，不会重复读取。</li>
 * </ol>
 */
class StreamJdbcInputPartitionIntegrationTest extends ComponentTestBase {

    private static final String URL = "jdbc:duckdb:./target/stream-jdbc-partition-it.duckdb";
    private static final String SCHEMA = "main";
    private static final int TOTAL_ROWS = 100;

    private DatasourceInfo datasource;

    @BeforeEach
    void setUpDatasource() {
        datasource = new DatasourceInfo();
        datasource.setType("duckdb");
        datasource.setUrl(URL);
        datasource.setDbschema(SCHEMA);
    }

    @Test
    void partitionWorkersReadDistinctRows() throws Exception {
        String table = "partition_orders";
        prepareTable(table);

        CapturingStreamJdbcInput input = new CapturingStreamJdbcInput();
        initComponent(input, "partition-input", "StreamJdbcInput");
        input.setDatasource(datasource);
        input.setSchema(SCHEMA);
        input.setTable(table);
        input.setIncrColumn("id");
        input.setParallelism(4);

        runWorkers(input, 4);

        assertEquals(TOTAL_ROWS, input.ids.size(), "4 个线程读取行数之和应等于总行数");
        assertEquals(TOTAL_ROWS, new HashSet<>(input.ids).size(), "主键不应重复");
        assertEquals(4, input.queries.size(), "每个线程应各执行一次查询");
        input.queries.forEach(sql -> assertTrue(sql.contains("id >="), "分区 SQL 应包含区间条件: " + sql));

        Object lastIncrValue = input.getLastIncrValue(table);
        assertTrue(lastIncrValue instanceof Number, "应保存增量水位");
        assertEquals(TOTAL_ROWS, ((Number) lastIncrValue).intValue(), "增量水位应为全局最大值");
    }

    @Test
    void primaryKeyIsUsedWhenNoIncrColumn() throws Exception {
        String table = "partition_orders_pk";
        prepareTable(table);

        CapturingStreamJdbcInput input = new CapturingStreamJdbcInput();
        initComponent(input, "partition-pk-input", "StreamJdbcInput");
        input.setDatasource(datasource);
        input.setSchema(SCHEMA);
        input.setTable(table);
        input.setParallelism(4);

        runWorkers(input, 4);

        assertEquals(TOTAL_ROWS, input.ids.size(), "4 个线程读取行数之和应等于总行数");
        assertEquals(TOTAL_ROWS, new HashSet<>(input.ids).size(), "主键不应重复");
        assertEquals(4, input.queries.size(), "未配置增量字段时应自动使用单列主键分区");
        input.queries.forEach(sql -> assertTrue(sql.contains("id >="), "分区 SQL 应包含区间条件: " + sql));
    }

    @Test
    void timestampIncrColumnFallsBackToPrimaryKey() throws Exception {
        String table = "partition_orders_time";
        prepareTimestampTable(table);

        CapturingStreamJdbcInput input = new CapturingStreamJdbcInput();
        initComponent(input, "partition-time-input", "StreamJdbcInput");
        input.setDatasource(datasource);
        input.setSchema(SCHEMA);
        input.setTable(table);
        input.setIncrColumn("update_time");
        input.setParallelism(4);

        runWorkers(input, 4);

        assertEquals(TOTAL_ROWS, input.ids.size(), "4 个线程读取行数之和应等于总行数");
        assertEquals(TOTAL_ROWS, new HashSet<>(input.ids).size(), "主键不应重复");
        assertEquals(4, input.queries.size(), "时间字段不可直接分区时，应回退单列主键分区");
        input.queries.forEach(sql -> {
            assertTrue(sql.contains("id >="), "应使用主键区间: " + sql);
            assertTrue(!sql.contains("update_time >="), "不应把时间字段当分区键: " + sql);
        });

        Object lastIncrValue = input.getLastIncrValue(table);
        String watermark = String.valueOf(lastIncrValue).replace(' ', 'T').replaceAll("\\.0+$", "");
        assertEquals("2026-01-01T00:01:40", watermark,
            "时间字段的增量水位应为所有分区中的全局最大值");
    }

    @Test
    void tableWithoutPartitionColumnUsesSingleWorker() throws Exception {
        String table = "partition_orders_plain";
        prepareTable(table, false);

        CapturingStreamJdbcInput input = new CapturingStreamJdbcInput();
        initComponent(input, "partition-plain-input", "StreamJdbcInput");
        input.setDatasource(datasource);
        input.setSchema(SCHEMA);
        input.setTable(table);
        input.setParallelism(4);

        runWorkers(input, 4);

        assertEquals(TOTAL_ROWS, input.ids.size());
        assertEquals(TOTAL_ROWS, new HashSet<>(input.ids).size());
        assertEquals(1, input.queries.size(), "没有可用分区字段时单表只应由一个线程读取");
    }

    private void runWorkers(CapturingStreamJdbcInput input, int workers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                final int workerIndex = i;
                futures.add(pool.submit(() -> {
                    input.setWorkerIndex(workerIndex);
                    ready.countDown();
                    try {
                        start.await();
                        input.execute(new FlowFile());
                    } finally {
                        input.clearWorkerIndex();
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未全部就绪");
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void prepareTable(String table) throws SQLException {
        prepareTable(table, true);
    }

    private void prepareTable(String table, boolean withPrimaryKey) throws SQLException {
        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + SCHEMA + "." + table);
            String primaryKey = withPrimaryKey ? " PRIMARY KEY" : "";
            statement.execute("CREATE TABLE " + SCHEMA + "." + table + " (id INT" + primaryKey + ", name VARCHAR(64))");
        }
        try (Connection conn = DBUtils.getConnection(datasource);
             PreparedStatement statement = conn.prepareStatement(
                 "INSERT INTO " + SCHEMA + "." + table + " (id, name) VALUES (?, ?)")) {
            for (int i = 1; i <= TOTAL_ROWS; i++) {
                statement.setInt(1, i);
                statement.setString(2, "row-" + i);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void prepareTimestampTable(String table) throws SQLException {
        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + SCHEMA + "." + table);
            statement.execute("CREATE TABLE " + SCHEMA + "." + table
                + " (id INT PRIMARY KEY, name VARCHAR(64), update_time TIMESTAMP)");
        }
        try (Connection conn = DBUtils.getConnection(datasource);
             PreparedStatement statement = conn.prepareStatement(
                 "INSERT INTO " + SCHEMA + "." + table + " (id, name, update_time) VALUES (?, ?, ?)")) {
            LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
            for (int i = 1; i <= TOTAL_ROWS; i++) {
                statement.setInt(1, i);
                statement.setString(2, "row-" + i);
                statement.setTimestamp(3, Timestamp.valueOf(base.plusSeconds(i)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * 捕获组件实际执行的 SQL 与读取到的主键，不向下游发送数据。
     */
    private static class CapturingStreamJdbcInput extends StreamJdbcInput {

        private final List<String> queries = new ArrayList<>();
        private final List<Integer> ids = new ArrayList<>();

        @Override
        protected String buildQuery(TableMeta srcTable, String tableIncrColumn, FlowFile flowFile) {
            String sql = super.buildQuery(srcTable, tableIncrColumn, flowFile);
            addQuery(sql);
            return sql;
        }

        @Override
        protected String buildQuery(TableMeta srcTable, String tableIncrColumn, FlowFile flowFile,
                                    String partitionPredicate) {
            String sql = super.buildQuery(srcTable, tableIncrColumn, flowFile, partitionPredicate);
            addQuery(sql);
            return sql;
        }

        private synchronized void addQuery(String sql) {
            queries.add(sql);
        }

        @Override
        protected void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) {
            synchronized (ids) {
                for (JSONObject record : batchRecords) {
                    ids.add(((Number) record.get("id")).intValue());
                }
            }
        }
    }
}
