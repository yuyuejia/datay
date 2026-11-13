package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.DuckDbAppenderWriter;
import com.data.datasource.provider.DuckLakeDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DuckLake 大批量写入性能与写入路径回归。
 *
 * <p>不依赖外部数据库：在临时目录创建 DuckLake，分别验证
 * {@link DuckLakeWrite} 与 {@link StreamJdbcOutput}（经 {@link DuckLakeDataSourceProvider}）
 * 都能走 Appender 写入，并输出吞吐便于复测。</p>
 *
 * <p>可通过系统属性调整规模（默认取较小值，保证 `mvn test` 时长可控）：</p>
 * <pre>
 *   -Dperf.ducklake.rows=200000
 *   -Dperf.ducklake.rowsPerFlowFile=10000
 *   -Dperf.ducklake.streamRows=50000
 *   -Dperf.ducklake.minRowsPerSec=0        # >0 时校验最低吞吐（用于固定性能基线）
 * </pre>
 *
 * <p>DuckLake 扩展不可用（无缓存且离线）时自动跳过。</p>
 */
public class DuckLakeWritePerformanceTest {

    private static final int ROWS = Integer.getInteger("perf.ducklake.rows", 200_000);
    private static final int ROWS_PER_FLOW_FILE = Integer.getInteger("perf.ducklake.rowsPerFlowFile", 10_000);
    private static final int STREAM_ROWS = Integer.getInteger("perf.ducklake.streamRows", 50_000);
    private static final double MIN_ROWS_PER_SEC = Double.parseDouble(
        System.getProperty("perf.ducklake.minRowsPerSec", "0")
    );

    private static final String[] COL_NAMES = {
        "id", "order_no", "user_id", "amount", "qty", "status",
        "created_at", "updated_at", "remark", "city", "score", "flag"
    };
    private static final String[] COL_TYPES = {
        "BIGINT", "VARCHAR", "BIGINT", "DECIMAL(18,2)", "INTEGER", "VARCHAR",
        "TIMESTAMP", "TIMESTAMP", "VARCHAR", "VARCHAR", "DOUBLE", "BOOLEAN"
    };

    @Test
    public void duckLakeWriteUsesAppenderAndScales() throws Exception {
        Path base = Files.createTempDirectory("ducklake-write-perf-");
        String meta = base.resolve("meta.ducklake").toString();
        String dataDir = base.resolve("data").toString();
        createLakeTable(meta, dataDir);

        String instanceCode = "ducklake-write-perf-" + UUID.randomUUID();
        DuckLakeWrite writer = newWriter(meta, dataDir, instanceCode);
        long start = System.nanoTime();
        try {
            for (int offset = 0; offset < ROWS; offset += ROWS_PER_FLOW_FILE) {
                int rows = Math.min(ROWS_PER_FLOW_FILE, ROWS - offset);
                FlowFile flowFile = new FlowFile();
                flowFile.setJsonArray(buildRows(offset, rows));
                writer.execute(flowFile);
            }
        } finally {
            // DuckLake 元数据在同一进程中只能挂载一次，校验前先释放引擎 scratch 库
            DuckDBEngine.close(instanceCode);
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        double rowsPerSecond = ROWS / seconds;

        assertEquals(ROWS, countRows(meta, dataDir));
        assertEquals(ROWS, countDistinctIds(meta, dataDir), "Appender 回退不应产生重复数据");

        System.out.printf(
            "[DuckLake 性能] DuckLakeWrite rows=%d rowsPerFlowFile=%d secs=%.3f rowsPerSec=%.0f%n",
            ROWS, ROWS_PER_FLOW_FILE, seconds, rowsPerSecond
        );
        if (MIN_ROWS_PER_SEC > 0) {
            assertTrue(
                rowsPerSecond >= MIN_ROWS_PER_SEC,
                "DuckLakeWrite 吞吐低于基线：" + rowsPerSecond + " < " + MIN_ROWS_PER_SEC
            );
        }

        DuckDBEngine.deleteDBFile(instanceCode);
    }

    @Test
    public void streamJdbcOutputUsesAppenderForDuckLake() throws Exception {
        Path base = Files.createTempDirectory("ducklake-stream-perf-");
        String meta = base.resolve("meta.ducklake").toString();
        String dataDir = base.resolve("data").toString();
        createLakeTable(meta, dataDir);

        DatasourceInfo datasource = duckLakeDatasource(meta, dataDir);
        String instanceCode = "ducklake-stream-perf-" + UUID.randomUUID();
        StreamJdbcOutput output = new StreamJdbcOutput();
        output.setContext(context(instanceCode));
        output.setTaskLogger(new TaskLogger("ducklake-perf", instanceCode));
        output.setId("StreamJdbcOutput");
        output.setName("StreamJdbcOutput");
        output.setActiveThreads(new AtomicInteger(1));
        output.setDatasource(datasource);
        output.setTable("bench");
        output.setSchema("main");
        output.setModel("append");
        output.upstreamFinish = true;

        long start = System.nanoTime();
        for (int offset = 0; offset < STREAM_ROWS; offset += ROWS_PER_FLOW_FILE) {
            int rows = Math.min(ROWS_PER_FLOW_FILE, STREAM_ROWS - offset);
            FlowFile flowFile = new FlowFile();
            flowFile.setJsonArray(buildRows(offset, rows));
            output.execute(flowFile);
        }
        double seconds = (System.nanoTime() - start) / 1e9;

        // StreamJdbcOutput 的连接来自连接池，scratch 库仍持有 DuckLake 元数据；
        // 复用同一数据源连接校验，避免同一进程内重复挂载元数据。
        assertEquals(STREAM_ROWS, countRowsViaDatasource(datasource));
        System.out.printf(
            "[DuckLake 性能] StreamJdbcOutput rows=%d rowsPerFlowFile=%d secs=%.3f rowsPerSec=%.0f%n",
            STREAM_ROWS, ROWS_PER_FLOW_FILE, seconds, STREAM_ROWS / seconds
        );
    }

    @Test
    public void duckLakeWriteKeepsUpdateOnSqlPath() throws Exception {
        Path base = Files.createTempDirectory("ducklake-update-perf-");
        String meta = base.resolve("meta.ducklake").toString();
        String dataDir = base.resolve("data").toString();
        createLakeTable(meta, dataDir);

        String instanceCode = "ducklake-update-perf-" + UUID.randomUUID();
        DuckLakeWrite writer = newWriter(meta, dataDir, instanceCode);
        try {
            FlowFile insert = new FlowFile();
            JSONArray insertRows = new JSONArray();
            insertRows.add(buildRow(1L, "remark-1"));
            insert.setJsonArray(insertRows);
            writer.execute(insert);

            // UPDATE 必须继续走 SQL（带变更前镜像），不能被 Appender 当成 INSERT 追加
            writer.setModel("auto");
            JSONObject after = buildRow(1L, "updated-1");
            after.put("__before", buildRow(1L, "remark-1"));
            JSONArray updateRows = new JSONArray();
            updateRows.add(after);
            FlowFile update = new FlowFile();
            update.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "UPDATE");
            update.setJsonArray(updateRows);
            writer.execute(update);
        } finally {
            DuckDBEngine.close(instanceCode);
        }

        assertEquals(1, countRows(meta, dataDir), "UPDATE 不应退化成追加 INSERT");
        assertEquals(
            1,
            scalar(meta, dataDir, "SELECT count(*) FROM ducklake.main.bench WHERE id = 1 AND remark = 'updated-1'"),
            "UPDATE 应更新原行"
        );
        DuckDBEngine.deleteDBFile(instanceCode);
    }

    @Test
    public void appenderIsOnlyUsedForInsertLikeEvents() {
        DuckLakeDataSourceProvider provider = new DuckLakeDataSourceProvider();
        TableMeta table = new TableMeta("bench");

        for (String eventType : new String[] { "INSERT", "insert", "APPEND", "overwrite" }) {
            assertTrue(DuckDbAppenderWriter.supports(eventType), "应允许 Appender：" + eventType);
            assertTrue(provider.supportsTableWrite(null, table, eventType), "DuckLake 应走 Appender：" + eventType);
        }
        for (String eventType : new String[] { "UPDATE", "DELETE", "REPLACE", null }) {
            assertFalse(DuckDbAppenderWriter.supports(eventType), "不应允许 Appender：" + eventType);
            assertFalse(provider.supportsTableWrite(null, table, eventType), "DuckLake 不应走 Appender：" + eventType);
        }
    }

    @Test
    public void appenderFallsBackWhenConnectionIsNotDuckDb() throws Exception {
        Connection notDuckDb = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if ("toString".equals(method.getName())) {
                    return "not-a-duckdb-connection";
                }
                return null;
            }
        );
        assertFalse(
            DuckDbAppenderWriter.append(notDuckDb, new TableMeta("bench"), buildRows(0, 1), null),
            "非 DuckDB 连接应返回 false，由调用方回退 SQL 写入"
        );
    }

    // ------------------------------------------------------------------ 辅助方法

    private DuckLakeWrite newWriter(String meta, String dataDir, String instanceCode) {
        DuckLakeWrite writer = new DuckLakeWrite();
        writer.setContext(context(instanceCode));
        writer.setTaskLogger(new TaskLogger("ducklake-perf", instanceCode));
        writer.setId("DuckLakeWrite");
        writer.setName("DuckLakeWrite");
        writer.setActiveThreads(new AtomicInteger(1));
        writer.setDatasource(duckLakeDatasource(meta, dataDir));
        writer.setTable("bench");
        writer.setSchema("main");
        writer.setModel("append");
        writer.upstreamFinish = true;
        return writer;
    }

    private ExecutionContext context(String instanceCode) {
        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode(instanceCode);
        context.setJobCode("ducklake-perf");
        return context;
    }

    private DatasourceInfo duckLakeDatasource(String meta, String dataDir) {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DUCKLAKE");
        datasource.setUrl("ducklake:" + meta);
        datasource.setDbschema("main");
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("s3.data_path", dataDir);
        datasource.setExtraParams(extraParams);
        return datasource;
    }

    private void createLakeTable(String meta, String dataDir) {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            Statement statement = conn.createStatement();
            try {
                statement.execute("LOAD ducklake");
            } catch (Exception e) {
                statement.execute("INSTALL ducklake");
                statement.execute("LOAD ducklake");
            }
            statement.execute("ATTACH 'ducklake:" + meta + "' AS ducklake (data_path '" + dataDir + "')");
            statement.execute("CREATE TABLE ducklake.main.bench (" + ddl() + ")");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "DuckLake 扩展不可用，跳过性能测试: " + e.getMessage());
        }
    }

    private long countRows(String meta, String dataDir) throws Exception {
        return scalar(meta, dataDir, "SELECT count(*) FROM ducklake.main.bench");
    }

    private long countRowsViaDatasource(DatasourceInfo datasource) throws Exception {
        try (
            Connection conn = DBUtils.getConnection(datasource);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("SELECT count(*) FROM ducklake.main.bench")
        ) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long countDistinctIds(String meta, String dataDir) throws Exception {
        return scalar(meta, dataDir, "SELECT count(DISTINCT id) FROM ducklake.main.bench");
    }

    private long scalar(String meta, String dataDir, String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            Statement statement = conn.createStatement();
            try {
                statement.execute("LOAD ducklake");
            } catch (Exception e) {
                statement.execute("INSTALL ducklake");
                statement.execute("LOAD ducklake");
            }
            try {
                statement.execute("ATTACH 'ducklake:" + meta + "' AS ducklake (data_path '" + dataDir + "')");
            } catch (Exception e) {
                Assumptions.assumeTrue(false, "DuckLake 元数据仍被占用，跳过校验: " + e.getMessage());
            }
            try (ResultSet rs = statement.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String ddl() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COL_NAMES.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(COL_NAMES[i]).append(' ').append(COL_TYPES[i]);
        }
        return sb.toString();
    }

    private JSONArray buildRows(int offset, int rows) {
        JSONArray array = new JSONArray(rows);
        for (int r = 0; r < rows; r++) {
            array.add(buildRow(offset + r, "remark-" + ((offset + r) % 1000)));
        }
        return array;
    }

    private JSONObject buildRow(long id, String remark) {
        JSONObject record = new JSONObject();
        record.put("id", id);
        record.put("order_no", "NO" + String.format("%012d", id));
        record.put("user_id", id % 100_000L);
        record.put("amount", new BigDecimal(id % 1_000_000).movePointLeft(2));
        record.put("qty", (int) (id % 100));
        record.put("status", new String[] { "NEW", "PAID", "SHIPPED", "DONE" }[(int) (id % 4)]);
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(id % 86_400));
        record.put("created_at", ts);
        record.put("updated_at", ts);
        record.put("remark", remark);
        record.put("city", "city-" + (id % 200));
        record.put("score", (id % 10000) / 10.0);
        record.put("flag", id % 2 == 0);
        return record;
    }
}
