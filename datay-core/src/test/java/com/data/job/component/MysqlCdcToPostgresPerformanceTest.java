package com.data.job.component;

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
import java.util.List;
import java.util.Locale;
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
 * MySQL CDC → PostgreSQL 实时同步性能验证。
 *
 * <p>覆盖：全量快照吞吐、增量 INSERT 吞吐、增量 UPDATE / DELETE 吞吐、单行事务端到端延迟。
 * 所有规模均可通过 {@code -Dperf.*} 覆盖，便于在不同机器/不同 PG 配置下复测。</p>
 *
 * <p>前置条件（本机验证环境）：
 * <ul>
 *   <li>MySQL 127.0.0.1:3306/test，root/1234qwer，{@code log_bin=ON}、{@code binlog_format=ROW}；</li>
 *   <li>PostgreSQL 127.0.0.1:15432/datay_test，postgres/Datay@12345，schema {@code test} 可创建。</li>
 * </ul>
 * 任一环境不可用时自动跳过。
 */
public class MysqlCdcToPostgresPerformanceTest {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:15432/datay_test";
    private static final String PG_USER = "postgres";
    private static final String PG_PASSWORD = "Datay@12345";
    private static final String PG_SCHEMA = "test";
    /** 可选：覆盖任务配置中的 PG URL，用于对比 reWriteBatchedInserts 等驱动参数。 */
    private static final String PG_URL_OVERRIDE = System.getProperty("perf.pgUrl");

    private static final String TABLE = MysqlPerfTableSupport.tableName("cdc_pg_perf", "cdc_pg_perf_wide");

    private static final int SNAPSHOT_ROWS = Integer.getInteger("perf.snapshotRows", 100_000);
    private static final int SNAPSHOT_FETCH_SIZE = Integer.getInteger("perf.snapshotFetchSize", 10_000);
    private static final int BULK_ROWS = Integer.getInteger("perf.bulkRows", 50_000);
    private static final int BULK_BATCH = Integer.getInteger("perf.bulkBatch", 1_000);
    private static final int UPDATE_ROWS = Math.min(Integer.getInteger("perf.updateRows", 10_000), BULK_ROWS);
    private static final int DELETE_ROWS = Math.min(Integer.getInteger("perf.deleteRows", 10_000), BULK_ROWS);
    private static final int LATENCY_ROWS = Integer.getInteger("perf.latencyRows", 200);

    private static final long BULK_BASE = 10_000_000L;
    private static final long LATENCY_BASE = 20_000_000L;
    private static final long LATENCY_POLL_INTERVAL_MS = 2L;

    private Connection pgReadConnection;

    @Test
    public void testMysqlCdcToPostgresPerformance() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        assumeTrue(postgresReachable(), "PostgreSQL 不可用，跳过测试");
        resetEnvironment();

        StringBuilder report = new StringBuilder();
        report.append("\n===== MySQL CDC -> PostgreSQL 实时同步性能验证 =====\n");
        report.append("环境: MySQL binlog ROW -> PostgreSQL 18 (localhost)\n");
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
        String reportText;

        try {
            String jobTemplate = Files.readString(Paths.get(System.getProperty("user.dir"), "src/test/mysqlCdcPerfToPostgres.json"));
            if (hasPgUrlOverride()) {
                System.out.println("已使用 PG URL 覆盖: " + PG_URL_OVERRIDE);
            }
            String jobText = hasPgUrlOverride() ? jobTemplate.replace(PG_URL, PG_URL_OVERRIDE) : jobTemplate;
            if (!"cdc_pg_perf".equals(TABLE)) {
                jobText = jobText.replace("cdc_pg_perf", TABLE);
            }
            final String job = jobText;
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
                () -> pgCount() == SNAPSHOT_ROWS,
                Math.max(600_000, SNAPSHOT_ROWS / 20),
                "等待全量快照完成超时"
            );
            long snapshotMillis = elapsedMillis(jobStart);
            assertEquals(SNAPSHOT_ROWS, pgCount(), "快照完成后目标表行数不一致");
            report.append("\n[全量快照]\n");
            report.append(String.format(Locale.ROOT, "  数据量: %d 行\n", SNAPSHOT_ROWS));
            report.append(String.format(Locale.ROOT, "  耗时(任务启动→全部可见): %.2f s\n", snapshotMillis / 1000.0));
            report.append(String.format(Locale.ROOT, "  吞吐: %.0f 行/s\n", SNAPSHOT_ROWS / seconds(snapshotMillis)));

            // ------------------------------------------------------------ 2. CDC 就绪预热
            awaitTrue(this::binlogDumpActive, 60_000, "等待 MySQL Binlog 采集器就绪超时");
            executeMysql(MysqlPerfTableSupport.warmupInsertSql("test", TABLE));
            awaitTrue(() -> pgExists(-1), 60_000, "等待 CDC 预热消息同步到 PostgreSQL 超时");
            executeMysql("DELETE FROM test." + TABLE + " WHERE id = -1");
            awaitTrue(() -> !pgExists(-1), 60_000, "等待 CDC 预热消息删除同步到 PostgreSQL 超时");
            assertEquals(SNAPSHOT_ROWS, pgCount(), "CDC 预热后行数不一致");

            // ------------------------------------------------------------ 3. 增量 INSERT
            long t0 = System.nanoTime();
            batchInsert(BULK_BASE, BULK_ROWS, BULK_BATCH, "bulk");
            long tSource = System.nanoTime();
            awaitTrue(
                () -> pgCount() == SNAPSHOT_ROWS + BULK_ROWS,
                Math.max(600_000, BULK_ROWS / 20),
                "等待增量 INSERT 同步到 PostgreSQL 超时"
            );
            long tVisible = System.nanoTime();
            long insertSourceMillis = nanosToMillis(tSource - t0);
            long insertE2eMillis = nanosToMillis(tVisible - t0);
            long insertLagMillis = nanosToMillis(tVisible - tSource);
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
                BULK_ROWS / seconds(insertSourceMillis)
            ));
            report.append(String.format(
                Locale.ROOT,
                "  端到端总耗时: %.2f s (%.0f 行/s)\n",
                insertE2eMillis / 1000.0,
                BULK_ROWS / seconds(insertE2eMillis)
            ));
            report.append(String.format(
                Locale.ROOT,
                "  最后提交→全部可见: %.2f s%s\n",
                insertLagMillis / 1000.0,
                insertLagMillis > 0
                    ? String.format(Locale.ROOT, " (%.0f 行/s 追平)", BULK_ROWS / seconds(insertLagMillis))
                    : " (写入期间已被实时消费)"
            ));

            // ------------------------------------------------------------ 4. 增量 UPDATE
            long u0 = System.nanoTime();
            batchUpdate(BULK_BASE, UPDATE_ROWS, BULK_BATCH);
            long uSource = System.nanoTime();
            awaitTrue(
                () -> pgCountByPrefix("upd_") == UPDATE_ROWS,
                Math.max(300_000, UPDATE_ROWS / 10),
                "等待增量 UPDATE 同步到 PostgreSQL 超时"
            );
            long uVisible = System.nanoTime();
            long updateSourceMillis = nanosToMillis(uSource - u0);
            long updateE2eMillis = nanosToMillis(uVisible - u0);
            long updateLagMillis = nanosToMillis(uVisible - uSource);
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
                UPDATE_ROWS / seconds(updateSourceMillis)
            ));
            report.append(String.format(
                Locale.ROOT,
                "  端到端总耗时: %.2f s (%.0f 行/s)\n",
                updateE2eMillis / 1000.0,
                UPDATE_ROWS / seconds(updateE2eMillis)
            ));
            report.append(String.format(Locale.ROOT, "  最后提交→全部可见: %.2f s\n", updateLagMillis / 1000.0));

            // ------------------------------------------------------------ 5. 增量 DELETE
            long baselineBeforeDelete = pgCount();
            long d0 = System.nanoTime();
            batchDelete(BULK_BASE, DELETE_ROWS, BULK_BATCH);
            long dSource = System.nanoTime();
            long expectedAfterDelete = baselineBeforeDelete - DELETE_ROWS;
            awaitTrue(
                () -> pgCount() == expectedAfterDelete,
                Math.max(300_000, DELETE_ROWS / 10),
                "等待增量 DELETE 同步到 PostgreSQL 超时"
            );
            long dVisible = System.nanoTime();
            long deleteSourceMillis = nanosToMillis(dSource - d0);
            long deleteE2eMillis = nanosToMillis(dVisible - d0);
            long deleteLagMillis = nanosToMillis(dVisible - dSource);
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
                DELETE_ROWS / seconds(deleteSourceMillis)
            ));
            report.append(String.format(
                Locale.ROOT,
                "  端到端总耗时: %.2f s (%.0f 行/s)\n",
                deleteE2eMillis / 1000.0,
                DELETE_ROWS / seconds(deleteE2eMillis)
            ));
            report.append(String.format(Locale.ROOT, "  最后提交→全部可见: %.2f s\n", deleteLagMillis / 1000.0));

            // ------------------------------------------------------------ 6. 单行事务端到端延迟
            LatencyResult latency = measureLatency();
            report.append(String.format(
                Locale.ROOT,
                "\n[单行事务端到端延迟]（%d 次单行 INSERT，可见性轮询间隔 %d ms）\n",
                LATENCY_ROWS,
                LATENCY_POLL_INTERVAL_MS
            ));
            report.append("  端到端(MySQL 提交前→PostgreSQL 可见)\n");
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
            report.append(String.format(Locale.ROOT, "  最大轮询间隔: %.2f ms\n", latency.maxPollGap));
            report.append(String.format(
                Locale.ROOT,
                "  其中 MySQL 提交后→PostgreSQL 可见: avg=%.2f ms, p95=%.2f ms, max=%.2f ms\n",
                latency.syncAvg,
                latency.syncP95,
                latency.syncMax
            ));

            // ------------------------------------------------------------ 7. 结果校验
            long expectedFinal = SNAPSHOT_ROWS + BULK_ROWS - DELETE_ROWS + LATENCY_ROWS;
            awaitTrue(() -> pgCount() == expectedFinal, 120_000, "等待最终数据追平超时");
            assertEquals(expectedFinal, pgCount(), "最终目标表行数与源端不一致");
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
        String reportName = MysqlPerfTableSupport.WIDE ? "mysql-cdc-postgres-perf-wide-report.txt" : "mysql-cdc-postgres-perf-report.txt";
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

        try (Connection writeConn = openMysql(); PreparedStatement insert = writeConn.prepareStatement(
            MysqlPerfTableSupport.latencyInsertSql("test", TABLE)
        )) {
            for (int i = 0; i < LATENCY_ROWS; i++) {
                long id = LATENCY_BASE + i;
                MysqlPerfTableSupport.bindLatencyRow(insert, id);

                long before = System.nanoTime();
                insert.executeUpdate();
                long committed = System.nanoTime();

                long deadline = System.currentTimeMillis() + 60_000;
                boolean found = false;
                double maxPollGap = 0;
                long lastPoll = System.nanoTime();
                while (System.currentTimeMillis() < deadline) {
                    if (pgExists(id)) {
                        found = true;
                        break;
                    }
                    long now = System.nanoTime();
                    maxPollGap = Math.max(maxPollGap, nanosToMillisDouble(now - lastPoll));
                    lastPoll = now;
                    Thread.sleep(LATENCY_POLL_INTERVAL_MS);
                }
                long visible = System.nanoTime();
                assertTrue(found, "单行 CDC 消息 60s 内未同步到 PostgreSQL，id=" + id);

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

        LatencyResult result = new LatencyResult();
        result.avg = average(e2eSamples);
        result.p50 = percentile(e2eSamples, 50);
        result.p95 = percentile(e2eSamples, 95);
        result.p99 = percentile(e2eSamples, 99);
        result.max = maxValue;
        result.maxIndex = maxIndex;
        result.maxId = maxId;
        result.maxPollGap = pollGaps.stream().mapToDouble(Double::doubleValue).max().orElse(0);
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
        private double syncAvg;
        private double syncP95;
        private double syncMax;
    }

    // ------------------------------------------------------------------ 数据准备

    private void resetEnvironment() throws Exception {
        DBUtils.closePool(PG_URL, PG_USER);
        if (hasPgUrlOverride()) {
            DBUtils.closePool(PG_URL_OVERRIDE, PG_USER);
        }
        DBUtils.closePool(MYSQL_URL, MYSQL_USER);
        deleteStatusFile();
        executePg("CREATE SCHEMA IF NOT EXISTS " + PG_SCHEMA);
        executePg("DROP TABLE IF EXISTS " + PG_SCHEMA + "." + TABLE);
        pgReadConnection = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
        pgReadConnection.setAutoCommit(true);

        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + TABLE);
            st.execute(MysqlPerfTableSupport.createTableSql("test", TABLE));
        }
    }

    private void batchInsert(long startId, int count, int batchSize, String prefix) throws SQLException {
        try (Connection conn = openMysql()) {
            conn.setAutoCommit(true);
            for (int offset = 0; offset < count; offset += batchSize) {
                int rows = Math.min(batchSize, count - offset);
                StringBuilder sql = new StringBuilder(
                    "INSERT INTO test." + TABLE + " (" + MysqlPerfTableSupport.insertColumns() + ") VALUES "
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
                        "UPDATE test." + TABLE + " SET name = CONCAT('upd_', id) WHERE id >= " + from + " AND id < " + to
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
                    st.executeUpdate("DELETE FROM test." + TABLE + " WHERE id >= " + from + " AND id < " + to);
                }
            }
        }
    }

    // ------------------------------------------------------------------ PostgreSQL 查询

    private int pgCount() {
        try (Statement st = pgReadConnection.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from " + PG_SCHEMA + "." + TABLE)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private int pgCountByPrefix(String prefix) {
        try (Statement st = pgReadConnection.createStatement();
             ResultSet rs = st.executeQuery(
                 "select count(*) from " + PG_SCHEMA + "." + TABLE + " where name like '" + prefix + "%'"
             )) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean pgExists(long id) {
        try (PreparedStatement ps = pgReadConnection.prepareStatement(
            "select 1 from " + PG_SCHEMA + "." + TABLE + " where id = ?"
        )) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ MySQL / PG 辅助

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

    private boolean postgresReachable() {
        try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD)) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasPgUrlOverride() {
        return PG_URL_OVERRIDE != null && !PG_URL_OVERRIDE.isBlank();
    }

    private Connection openMysql() throws SQLException {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }

    private void executeMysql(String sql) throws Exception {
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private void executePg(String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
             Statement st = conn.createStatement()) {
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
                jobFuture.get(20, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        executor.shutdownNow();
        if (pgReadConnection != null) {
            try {
                pgReadConnection.close();
            } catch (Exception ignored) {
            }
        }
        try {
            DBUtils.closePool(PG_URL, PG_USER);
        } catch (Exception ignored) {
        }
        if (hasPgUrlOverride()) {
            try {
                DBUtils.closePool(PG_URL_OVERRIDE, PG_USER);
            } catch (Exception ignored) {
            }
        }
        try {
            DBUtils.closePool(MYSQL_URL, MYSQL_USER);
        } catch (Exception ignored) {
        }
        try {
            executePg("DROP TABLE IF EXISTS " + PG_SCHEMA + "." + TABLE);
        } catch (Exception ignored) {
        }
        try {
            executeMysql("DROP TABLE IF EXISTS test." + TABLE);
        } catch (Exception ignored) {
        }
        deleteStatusFile();
    }

    private void deleteStatusFile() {
        File file = new File("./log", "etl_status.json");
        if (file.exists()) {
            file.delete();
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
