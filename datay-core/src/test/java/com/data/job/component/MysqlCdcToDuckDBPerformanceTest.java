package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySQL CDC → DuckDB 实时同步性能验证。
 *
 * <p>覆盖四类指标：
 * <ol>
 *   <li>首次全量快照吞吐（MySQL 历史数据 → DuckDB）；</li>
 *   <li>增量 INSERT 吞吐（多行事务批量提交）；</li>
 *   <li>增量 UPDATE / DELETE 吞吐；</li>
 *   <li>单行事务端到端延迟（p50 / p95 / p99 / max）。</li>
 * </ol>
 *
 * <p>所有规模均可通过系统属性覆盖，便于在不同机器上复测：
 * <pre>
 *   -Dperf.snapshotRows=100000
 *   -Dperf.snapshotFetchSize=10000
 *   -Dperf.bulkRows=50000
 *   -Dperf.bulkBatch=1000
 *   -Dperf.updateRows=10000
 *   -Dperf.deleteRows=10000
 *   -Dperf.latencyRows=200
 * </pre>
 *
 * <p>前置条件与 {@link MysqlCdcToDuckDBTest} 一致：本地 MySQL 开启 ROW binlog 且账号有复制权限，
 * 存在 {@code test} schema；不满足时自动跳过。
 */
public class MysqlCdcToDuckDBPerformanceTest {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";

    private static final String DUCK_URL = "jdbc:duckdb:./target/mysql-cdc-perf.duckdb";
    private static final String DUCK_FILE = "./target/mysql-cdc-perf.duckdb";

    private static final String SCHEMA = "test";
    private static final String TABLE = MysqlPerfTableSupport.tableName("cdc_perf", "cdc_perf_wide");

    private static final int SNAPSHOT_ROWS = Integer.getInteger("perf.snapshotRows", 100_000);
    private static final int SNAPSHOT_FETCH_SIZE = Integer.getInteger("perf.snapshotFetchSize", 10_000);
    private static final int BULK_ROWS = Integer.getInteger("perf.bulkRows", 50_000);
    private static final int BULK_BATCH = Integer.getInteger("perf.bulkBatch", 1_000);
    private static final int UPDATE_ROWS = Math.min(Integer.getInteger("perf.updateRows", 10_000), BULK_ROWS);
    private static final int DELETE_ROWS = Math.min(Integer.getInteger("perf.deleteRows", 10_000), BULK_ROWS);
    private static final int LATENCY_ROWS = Integer.getInteger("perf.latencyRows", 200);
    /** 可选：调整 DuckDB checkpoint 阈值，用于验证 WAL checkpoint 对尾延迟的影响。 */
    private static final String DUCKDB_CHECKPOINT_THRESHOLD = System.getProperty("perf.duckdbCheckpointThreshold");

    /** 增量数据主键起始值，避免与快照数据（1..SNAPSHOT_ROWS）重叠。 */
    private static final long BULK_BASE = 10_000_000L;
    /** 延迟测试数据主键起始值。 */
    private static final long LATENCY_BASE = 20_000_000L;
    private static final long LATENCY_POLL_INTERVAL_MS = 2L;

    @Test
    public void testMysqlCdcToDuckDBPerformance() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        resetEnvironment();

        StringBuilder report = new StringBuilder();
        report.append("\n===== MySQL CDC -> DuckDB 实时同步性能验证 =====\n");
        report.append("环境: MySQL binlog ROW -> DuckDB embedded JDBC\n");
        report.append(String.format(
            Locale.ROOT,
            "参数: table=%s, wide=%s, snapshotRows=%d, snapshotFetchSize=%d, bulkRows=%d, bulkBatch=%d, updateRows=%d, deleteRows=%d, latencyRows=%d\n",
            TABLE,
            MysqlPerfTableSupport.WIDE,
            SNAPSHOT_ROWS,
            SNAPSHOT_FETCH_SIZE,
            BULK_ROWS,
            BULK_BATCH,
            UPDATE_ROWS,
            DELETE_ROWS,
            LATENCY_ROWS
        ));

        // ---------------------------------------------------------------- 准备全量数据
        long prepareStart = System.nanoTime();
        batchInsert(1, SNAPSHOT_ROWS, BULK_BATCH, "row");
        long prepareMillis = elapsedMillis(prepareStart);
        report.append(String.format(
            Locale.ROOT,
            "[准备] 向 MySQL 写入 %d 行全量数据，耗时 %.2f s（该时间不计入同步吞吐）\n",
            SNAPSHOT_ROWS,
            prepareMillis / 1000.0
        ));

        ETLFlowTask runner = new ETLFlowTask();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        Future<?> jobFuture = null;

        Map<String, Double> metrics = new LinkedHashMap<>();
        String reportText;
        try {
            String jobTemplate = Files.readString(Paths.get(System.getProperty("user.dir"), "src/test/mysqlCdcPerfToDuckDB.json"));
            String job = "cdc_perf".equals(TABLE) ? jobTemplate : jobTemplate.replace("cdc_perf", TABLE);
            applyDuckDbSettings();
            long jobStart = System.nanoTime();
            jobFuture = executor.submit(() -> {
                try {
                    runner.runJob(job);
                } catch (Throwable e) {
                    jobError.set(e);
                }
            });

            // ------------------------------------------------------------ 1. 全量快照
            awaitTrue(
                () -> duckCount() == SNAPSHOT_ROWS,
                Math.max(120_000, SNAPSHOT_ROWS / 100),
                "等待全量快照完成超时"
            );
            long snapshotMillis = elapsedMillis(jobStart);
            assertEquals(SNAPSHOT_ROWS, duckCount(), "快照完成后目标表行数不一致");
            metrics.put("snapshotMillis", (double) snapshotMillis);
            metrics.put("snapshotRowsPerSec", SNAPSHOT_ROWS / seconds(snapshotMillis));
            report.append("\n[全量快照]\n");
            report.append(String.format(Locale.ROOT, "  数据量: %d 行\n", SNAPSHOT_ROWS));
            report.append(String.format(Locale.ROOT, "  耗时(任务启动→全部可见): %.2f s\n", snapshotMillis / 1000.0));
            report.append(String.format(Locale.ROOT, "  吞吐: %.0f 行/s\n", metrics.get("snapshotRowsPerSec")));

            // ------------------------------------------------------------ 2. CDC 就绪预热
            awaitTrue(this::binlogDumpActive, 60_000, "等待 MySQL Binlog 采集器就绪超时");
            executeMysql(MysqlPerfTableSupport.warmupInsertSql(SCHEMA, TABLE));
            awaitTrue(() -> duckExists(-1), 30_000, "等待 CDC 预热消息同步到 DuckDB 超时");
            executeMysql("DELETE FROM " + SCHEMA + "." + TABLE + " WHERE id = -1");
            awaitTrue(() -> !duckExists(-1), 30_000, "等待 CDC 预热消息删除同步到 DuckDB 超时");
            assertEquals(SNAPSHOT_ROWS, duckCount(), "CDC 预热后行数不一致");

            // ------------------------------------------------------------ 3. 增量 INSERT
            long t0 = System.nanoTime();
            batchInsert(BULK_BASE, BULK_ROWS, BULK_BATCH, "bulk");
            long tSource = System.nanoTime();
            awaitTrue(
                () -> duckCount() == SNAPSHOT_ROWS + BULK_ROWS,
                Math.max(120_000, BULK_ROWS / 100),
                "等待增量 INSERT 同步到 DuckDB 超时"
            );
            long tVisible = System.nanoTime();
            long insertSourceMillis = nanosToMillis(tSource - t0);
            long insertE2eMillis = nanosToMillis(tVisible - t0);
            long insertLagMillis = nanosToMillis(tVisible - tSource);
            metrics.put("insertSourceMillis", (double) insertSourceMillis);
            metrics.put("insertE2eMillis", (double) insertE2eMillis);
            metrics.put("insertLagMillis", (double) insertLagMillis);
            metrics.put("insertSourceRowsPerSec", BULK_ROWS / seconds(insertSourceMillis));
            metrics.put("insertE2eRowsPerSec", BULK_ROWS / seconds(insertE2eMillis));
            metrics.put("insertLagRowsPerSec", insertLagMillis > 0 ? BULK_ROWS / seconds(insertLagMillis) : 0.0);
            report.append("\n[增量 INSERT]\n");
            report.append(String.format(
                Locale.ROOT,
                "  数据量: %d 行 / %d 个事务（%d 行/事务）\n",
                BULK_ROWS,
                ceilDiv(BULK_ROWS, BULK_BATCH),
                BULK_BATCH
            ));
            report.append(String.format(
                Locale.ROOT,
                "  MySQL 写入耗时: %.2f s (%.0f 行/s)\n",
                insertSourceMillis / 1000.0,
                metrics.get("insertSourceRowsPerSec")
            ));
            report.append(String.format(
                Locale.ROOT,
                "  端到端总耗时: %.2f s (%.0f 行/s)\n",
                insertE2eMillis / 1000.0,
                metrics.get("insertE2eRowsPerSec")
            ));
            report.append(String.format(
                Locale.ROOT,
                "  最后提交→全部可见: %.2f s%s\n",
                insertLagMillis / 1000.0,
                insertLagMillis > 0
                    ? String.format(Locale.ROOT, " (%.0f 行/s 追平)", metrics.get("insertLagRowsPerSec"))
                    : " (写入期间已被实时消费)"
            ));

            // ------------------------------------------------------------ 4. 增量 UPDATE
            long u0 = System.nanoTime();
            batchUpdate(BULK_BASE, UPDATE_ROWS, BULK_BATCH);
            long uSource = System.nanoTime();
            awaitTrue(
                () -> duckCountByPrefix("upd_") == UPDATE_ROWS,
                Math.max(120_000, UPDATE_ROWS / 50),
                "等待增量 UPDATE 同步到 DuckDB 超时"
            );
            long uVisible = System.nanoTime();
            long updateSourceMillis = nanosToMillis(uSource - u0);
            long updateE2eMillis = nanosToMillis(uVisible - u0);
            long updateLagMillis = nanosToMillis(uVisible - uSource);
            metrics.put("updateSourceMillis", (double) updateSourceMillis);
            metrics.put("updateE2eMillis", (double) updateE2eMillis);
            metrics.put("updateLagMillis", (double) updateLagMillis);
            metrics.put("updateSourceRowsPerSec", UPDATE_ROWS / seconds(updateSourceMillis));
            metrics.put("updateE2eRowsPerSec", UPDATE_ROWS / seconds(updateE2eMillis));
            report.append("\n[增量 UPDATE]\n");
            report.append(String.format(
                Locale.ROOT,
                "  数据量: %d 行 / %d 个事务（%d 行/事务）\n",
                UPDATE_ROWS,
                ceilDiv(UPDATE_ROWS, BULK_BATCH),
                BULK_BATCH
            ));
            report.append(String.format(
                Locale.ROOT,
                "  MySQL 写入耗时: %.2f s (%.0f 行/s)\n",
                updateSourceMillis / 1000.0,
                metrics.get("updateSourceRowsPerSec")
            ));
            report.append(String.format(
                Locale.ROOT,
                "  端到端总耗时: %.2f s (%.0f 行/s)\n",
                updateE2eMillis / 1000.0,
                metrics.get("updateE2eRowsPerSec")
            ));
            report.append(String.format(Locale.ROOT, "  最后提交→全部可见: %.2f s\n", updateLagMillis / 1000.0));

            // ------------------------------------------------------------ 5. 增量 DELETE
            long baselineBeforeDelete = duckCount();
            long d0 = System.nanoTime();
            batchDelete(BULK_BASE, DELETE_ROWS, BULK_BATCH);
            long dSource = System.nanoTime();
            long expectedAfterDelete = baselineBeforeDelete - DELETE_ROWS;
            awaitTrue(
                () -> duckCount() == expectedAfterDelete,
                Math.max(120_000, DELETE_ROWS / 50),
                "等待增量 DELETE 同步到 DuckDB 超时"
            );
            long dVisible = System.nanoTime();
            long deleteSourceMillis = nanosToMillis(dSource - d0);
            long deleteE2eMillis = nanosToMillis(dVisible - d0);
            long deleteLagMillis = nanosToMillis(dVisible - dSource);
            metrics.put("deleteSourceMillis", (double) deleteSourceMillis);
            metrics.put("deleteE2eMillis", (double) deleteE2eMillis);
            metrics.put("deleteLagMillis", (double) deleteLagMillis);
            metrics.put("deleteSourceRowsPerSec", DELETE_ROWS / seconds(deleteSourceMillis));
            metrics.put("deleteE2eRowsPerSec", DELETE_ROWS / seconds(deleteE2eMillis));
            report.append("\n[增量 DELETE]\n");
            report.append(String.format(
                Locale.ROOT,
                "  数据量: %d 行 / %d 个事务（%d 行/事务）\n",
                DELETE_ROWS,
                ceilDiv(DELETE_ROWS, BULK_BATCH),
                BULK_BATCH
            ));
            report.append(String.format(
                Locale.ROOT,
                "  MySQL 写入耗时: %.2f s (%.0f 行/s)\n",
                deleteSourceMillis / 1000.0,
                metrics.get("deleteSourceRowsPerSec")
            ));
            report.append(String.format(
                Locale.ROOT,
                "  端到端总耗时: %.2f s (%.0f 行/s)\n",
                deleteE2eMillis / 1000.0,
                metrics.get("deleteE2eRowsPerSec")
            ));
            report.append(String.format(Locale.ROOT, "  最后提交→全部可见: %.2f s\n", deleteLagMillis / 1000.0));

            // ------------------------------------------------------------ 6. 单行事务端到端延迟
            LatencyResult latency = measureLatency();
            metrics.put("latencyAvgMillis", latency.avg);
            metrics.put("latencyP50Millis", latency.p50);
            metrics.put("latencyP95Millis", latency.p95);
            metrics.put("latencyP99Millis", latency.p99);
            metrics.put("latencyMaxMillis", latency.max);
            report.append(String.format(
                Locale.ROOT,
                "\n[单行事务端到端延迟]（%d 次单行 INSERT，可见性轮询间隔 %d ms）\n",
                LATENCY_ROWS,
                LATENCY_POLL_INTERVAL_MS
            ));
            report.append(String.format(Locale.ROOT, "  端到端(MySQL 提交前→DuckDB 可见)\n"));
            report.append(String.format(Locale.ROOT, "    avg: %.2f ms\n", latency.avg));
            report.append(String.format(Locale.ROOT, "    p50: %.2f ms\n", latency.p50));
            report.append(String.format(Locale.ROOT, "    p95: %.2f ms\n", latency.p95));
            report.append(String.format(Locale.ROOT, "    p99: %.2f ms\n", latency.p99));
            report.append(String.format(Locale.ROOT, "    max: %.2f ms\n", latency.max));
            report.append(String.format(
                Locale.ROOT,
                "    max 出现在第 %d 次采样（id=%d）\n",
                latency.maxIndex + 1,
                latency.maxId
            ));
            report.append(String.format(
                Locale.ROOT,
                "  最大轮询间隔: %.2f ms\n",
                latency.maxPollGap
            ));
            if (latency.noisySamples > 0) {
                report.append(String.format(
                    Locale.ROOT,
                    "  剔除观测抖动后（%d/%d 个样本，轮询间隔 > 30ms 的样本已排除）\n",
                    latency.cleanCount,
                    LATENCY_ROWS
                ));
                report.append(String.format(
                    Locale.ROOT,
                    "    avg: %.2f ms, p95: %.2f ms, p99: %.2f ms, max: %.2f ms\n",
                    latency.cleanAvg,
                    latency.cleanP95,
                    latency.cleanP99,
                    latency.cleanMax
                ));
            }
            report.append(String.format(
                Locale.ROOT,
                "  其中 MySQL 提交后→DuckDB 可见: avg=%.2f ms, p95=%.2f ms, max=%.2f ms\n",
                latency.syncAvg,
                latency.syncP95,
                latency.syncMax
            ));

            // ------------------------------------------------------------ 7. 结果校验
            long expectedFinal = SNAPSHOT_ROWS + BULK_ROWS - DELETE_ROWS + LATENCY_ROWS;
            awaitTrue(() -> duckCount() == expectedFinal, 60_000, "等待最终数据追平超时");
            assertEquals(expectedFinal, duckCount(), "最终目标表行数与源端不一致");
            report.append("\n[结果校验]\n");
            report.append(String.format(Locale.ROOT, "  目标表最终行数: %d，与源端预期一致\n", expectedFinal));

            Throwable error = jobError.get();
            assertTrue(error == null || isCancellation(error), "CDC 同步任务异常结束: " + (error == null ? "" : error.getMessage()));
            report.append("  同步任务运行期间无异常\n");
            reportText = report.toString();
        } finally {
            cleanup(runner, executor, jobFuture);
        }

        System.out.println(reportText);
        String reportName = MysqlPerfTableSupport.WIDE ? "mysql-cdc-perf-wide-report.txt" : "mysql-cdc-perf-report.txt";
        Path reportPath = Paths.get("target", reportName);
        Files.writeString(reportPath, reportText);
        System.out.println("性能报告已写入: " + reportPath.toAbsolutePath());
    }

    // ------------------------------------------------------------------ 延迟测量

    private LatencyResult measureLatency() throws Exception {
        List<Double> e2eSamples = new ArrayList<>(LATENCY_ROWS);
        List<Double> syncSamples = new ArrayList<>(LATENCY_ROWS);
        List<Double> pollGaps = new ArrayList<>(LATENCY_ROWS);
        int maxIndex = -1;
        double maxValue = -1;
        long maxId = -1;

        try (Connection readConn = duckConnection(); Connection writeConn = openMysql()) {
            readConn.setAutoCommit(true);
            try (PreparedStatement insert = writeConn.prepareStatement(
                MysqlPerfTableSupport.latencyInsertSql(SCHEMA, TABLE)
            )) {
                for (int i = 0; i < LATENCY_ROWS; i++) {
                    long id = LATENCY_BASE + i;
                    MysqlPerfTableSupport.bindLatencyRow(insert, id);

                    long before = System.nanoTime();
                    insert.executeUpdate();
                    long committed = System.nanoTime();

                    long deadline = System.currentTimeMillis() + 30_000;
                    boolean found = false;
                    double maxPollGap = 0;
                    long lastPoll = System.nanoTime();
                    while (System.currentTimeMillis() < deadline) {
                        if (duckExists(readConn, id)) {
                            found = true;
                            break;
                        }
                        long now = System.nanoTime();
                        maxPollGap = Math.max(maxPollGap, nanosToMillisDouble(now - lastPoll));
                        lastPoll = now;
                        Thread.sleep(LATENCY_POLL_INTERVAL_MS);
                    }
                    long visible = System.nanoTime();
                    assertTrue(found, "单行 CDC 消息 30s 内未同步到 DuckDB，id=" + id);

                    double e2eMillis = nanosToMillisDouble(visible - before);
                    e2eSamples.add(e2eMillis);
                    syncSamples.add(nanosToMillisDouble(visible - committed));
                    pollGaps.add(maxPollGap);
                    if (e2eMillis > maxValue) {
                        maxValue = e2eMillis;
                        maxIndex = i;
                        maxId = id;
                    }
                }
            }
        }

        LatencyResult result = new LatencyResult();
        result.avg = average(e2eSamples);
        result.p50 = percentile(e2eSamples, 50);
        result.p95 = percentile(e2eSamples, 95);
        result.p99 = percentile(e2eSamples, 99);
        result.max = maxValue;
        result.maxIndex = maxIndex;
        result.maxId = maxId;
        result.maxPollGap = pollGaps.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        // 轮询线程被 I/O 或调度挤占时，sleep/查询间隔会被拉长，这种样本无法反映真实可见延迟。
        // 单独给出剔除「轮询间隔 > 30ms」样本后的统计，方便判断尾延迟是同步抖动还是观测抖动。
        List<Double> clean = new ArrayList<>(e2eSamples.size());
        for (int i = 0; i < e2eSamples.size(); i++) {
            if (pollGaps.get(i) <= 30) {
                clean.add(e2eSamples.get(i));
            }
        }
        result.noisySamples = e2eSamples.size() - clean.size();
        result.cleanCount = clean.size();
        if (!clean.isEmpty()) {
            result.cleanAvg = average(clean);
            result.cleanP95 = percentile(clean, 95);
            result.cleanP99 = percentile(clean, 99);
            result.cleanMax = clean.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        }

        result.syncAvg = average(syncSamples);
        result.syncP95 = percentile(syncSamples, 95);
        result.syncMax = syncSamples.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return result;
    }

    private static final class LatencyResult {
        private double avg;
        private double p50;
        private double p95;
        private double p99;
        private double max;
        private int maxIndex;
        private long maxId;
        private double maxPollGap;
        private int noisySamples;
        private int cleanCount;
        private double cleanAvg;
        private double cleanP95;
        private double cleanP99;
        private double cleanMax;
        private double syncAvg;
        private double syncP95;
        private double syncMax;
    }

    // ------------------------------------------------------------------ 数据准备

    private void resetEnvironment() throws Exception {
        DBUtils.closePool(DUCK_URL, null);
        deleteDuckFiles();
        deleteStatusFile();
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + SCHEMA + "." + TABLE);
            st.execute(MysqlPerfTableSupport.createTableSql(SCHEMA, TABLE));
        }
    }

    private void batchInsert(long startId, int count, int batchSize, String prefix) throws SQLException {
        try (Connection conn = openMysql()) {
            conn.setAutoCommit(true);
            for (int offset = 0; offset < count; offset += batchSize) {
                int rows = Math.min(batchSize, count - offset);
                StringBuilder sql = new StringBuilder(
                    "INSERT INTO " + SCHEMA + "." + TABLE + " (" + MysqlPerfTableSupport.insertColumns() + ") VALUES "
                );
                for (int i = 0; i < rows; i++) {
                    if (i > 0) {
                        sql.append(',');
                    }
                    MysqlPerfTableSupport.appendInsertValues(sql, startId + offset + i, prefix);
                }
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(sql.toString());
                }
            }
        }
    }

    private void batchUpdate(long startId, int count, int batchSize) throws SQLException {
        try (Connection conn = openMysql()) {
            conn.setAutoCommit(true);
            for (int offset = 0; offset < count; offset += batchSize) {
                int rows = Math.min(batchSize, count - offset);
                long from = startId + offset;
                long to = from + rows;
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(
                        "UPDATE " + SCHEMA + "." + TABLE + " SET name = CONCAT('upd_', id) WHERE id >= " + from + " AND id < " + to
                    );
                }
            }
        }
    }

    private void batchDelete(long startId, int count, int batchSize) throws SQLException {
        try (Connection conn = openMysql()) {
            conn.setAutoCommit(true);
            for (int offset = 0; offset < count; offset += batchSize) {
                int rows = Math.min(batchSize, count - offset);
                long from = startId + offset;
                long to = from + rows;
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("DELETE FROM " + SCHEMA + "." + TABLE + " WHERE id >= " + from + " AND id < " + to);
                }
            }
        }
    }

    // ------------------------------------------------------------------ DuckDB 查询

    private int duckCount() {
        Integer count = queryDuckInt("select count(*) from main." + TABLE);
        return count == null ? -1 : count;
    }

    private int duckCountByPrefix(String prefix) {
        Integer count = queryDuckInt("select count(*) from main." + TABLE + " where name like '" + prefix + "%'");
        return count == null ? -1 : count;
    }

    private boolean duckExists(long id) {
        try (Connection conn = duckConnection()) {
            return duckExists(conn, id);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean duckExists(Connection conn, long id) {
        try (PreparedStatement ps = conn.prepareStatement("select 1 from main." + TABLE + " where id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private Integer queryDuckInt(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Connection duckConnection() throws Exception {
        DatasourceInfo duck = new DatasourceInfo();
        duck.setUrl(DUCK_URL);
        return DBUtils.getConnection(duck);
    }

    /**
     * 可选的 DuckDB 连接级诊断参数。checkpoint_threshold 是 GLOBAL 设置，
     * 设置一次后连接池里的其它连接同样生效，可用于验证 WAL checkpoint 对尾延迟的影响。
     */
    private void applyDuckDbSettings() throws Exception {
        if (DUCKDB_CHECKPOINT_THRESHOLD != null && !DUCKDB_CHECKPOINT_THRESHOLD.isBlank()) {
            try (Connection conn = duckConnection(); Statement st = conn.createStatement()) {
                st.execute("SET checkpoint_threshold='" + DUCKDB_CHECKPOINT_THRESHOLD + "'");
            }
            System.out.println("已设置 DuckDB checkpoint_threshold=" + DUCKDB_CHECKPOINT_THRESHOLD);
        }
    }

    // ------------------------------------------------------------------ MySQL 辅助

    private boolean supportsRowBinlog() {
        try (Connection conn = openMysql()) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("show variables like 'binlog_format'")) {
                if (!rs.next() || !"ROW".equalsIgnoreCase(rs.getString(2))) {
                    return false;
                }
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("show variables like 'log_bin'")) {
                return rs.next() && "ON".equalsIgnoreCase(rs.getString(2));
            }
        } catch (Exception e) {
            return false;
        }
    }

    private Connection openMysql() throws SQLException {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }

    private void executeMysql(String sql) throws Exception {
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private boolean binlogDumpActive() {
        try (Connection conn = openMysql(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("show processlist")) {
            while (rs.next()) {
                String command = rs.getString("Command");
                if (command != null && command.toLowerCase().startsWith("binlog dump")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 清理

    private void cleanup(ETLFlowTask runner, ExecutorService executor, Future<?> jobFuture) {
        try {
            runner.cancel();
        } catch (Exception ignored) {
        }
        if (jobFuture != null) {
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        executor.shutdownNow();
        try {
            DBUtils.closePool(DUCK_URL, null);
        } catch (Exception ignored) {
        }
        deleteDuckFiles();
        deleteStatusFile();
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + SCHEMA + "." + TABLE);
        } catch (Exception ignored) {
        }
    }

    private void deleteDuckFiles() {
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File(DUCK_FILE + suffix);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private void deleteStatusFile() {
        for (String name : new String[] { "etl_status.json" }) {
            File file = new File("./log", name);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    // ------------------------------------------------------------------ 工具方法

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertTrue(condition.getAsBoolean(), message + "（超时 " + timeoutMillis + " ms）");
    }

    private boolean isCancellation(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof InterruptedException) {
                return true;
            }
            String message = throwable.getMessage();
            if (message != null && message.contains("InterruptedException")) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private static long elapsedMillis(long startNanos) {
        return nanosToMillis(System.nanoTime() - startNanos);
    }

    private static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private static double nanosToMillisDouble(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double seconds(long millis) {
        return millis <= 0 ? 0.001 : millis / 1000.0;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }
}
