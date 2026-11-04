package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 验证首次全量快照完成后，增量起始位点与 {@code snapshotDone} 一起持久化。
 *
 * <p>回归场景：快照完成、状态已保存后任务停止；停机期间源库产生新变更；重启后应从
 * 快照起始位点继续消费，而不是从当前位置开始导致停机期间的变更丢失。</p>
 */
public class MysqlCdcSnapshotStateRecoveryTest {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String DUCK_URL = "jdbc:duckdb:./target/mysql-cdc-snapshot-state.duckdb";
    private static final String TABLE = "cdc_state_demo";
    private static final String JOB_CODE = "mysql-cdc-snapshot-state-test";
    private static final long SERVER_ID = 2245L;

    @Test
    public void snapshotStartPositionShouldBePersistedWithSnapshotDone() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        resetEnvironment();

        ETLFlowTask firstRun = new ETLFlowTask(taskInstance("run1"), null);
        ExecutorService firstExecutor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        Future<?> firstFuture = firstExecutor.submit(() -> {
            try {
                firstRun.runJob(job());
            } catch (Throwable e) {
                firstError.set(e);
            }
        });

        try {
            awaitTrue(
                () -> statusContainsSnapshotCheckpoint(),
                30000,
                "等待快照完成状态（snapshotDone + binlog 起始位点）持久化超时"
            );
            awaitTrue(() -> targetCount() == 2, 30000, "等待历史全量快照同步到 DuckDB 超时");

            Map<String, Object> status = readStatus();
            assertNotNull(status);
            assertEquals(Boolean.TRUE, status.get("MySQLBinlogInput.snapshotDone"), "snapshotDone 应已持久化");
            assertNotNull(status.get("MySQLBinlogInput.binlogFile"), "起始 binlog 文件应与 snapshotDone 一起持久化");
            assertNotNull(status.get("MySQLBinlogInput.binlogPosition"), "起始 binlog 位点应与 snapshotDone 一起持久化");
        } finally {
            stop(firstRun, firstFuture, firstExecutor);
            assertNoUnexpectedError(firstError.get());
        }

        // 模拟任务停止期间的源库变更
        executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (3, 'offline', 30.00, '2024-01-03 12:00:00')");

        ETLFlowTask secondRun = new ETLFlowTask(taskInstance("run2"), null);
        ExecutorService secondExecutor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> secondError = new AtomicReference<>();
        Future<?> secondFuture = secondExecutor.submit(() -> {
            try {
                secondRun.runJob(job());
            } catch (Throwable e) {
                secondError.set(e);
            }
        });

        try {
            awaitTrue(
                () ->
                    targetCount() == 3 &&
                    "offline".equals(targetString("select name from main." + TABLE + " where id = 3")),
                30000,
                "重启后未从快照起始位点补放停机期间的变更"
            );
        } finally {
            stop(secondRun, secondFuture, secondExecutor);
            assertNoUnexpectedError(secondError.get());
            cleanup();
        }
    }

    private String job() throws Exception {
        return new String(
            Files.readAllBytes(Path.of(System.getProperty("user.dir"), "src/test/mysqlCdcSnapshotStateRecovery.json")),
            StandardCharsets.UTF_8
        );
    }

    private TaskInstance taskInstance(String instanceSuffix) {
        TaskInstance instance = new TaskInstance();
        instance.setId(JOB_CODE + "-" + instanceSuffix);
        instance.setInstanceCode(JOB_CODE + "-" + instanceSuffix);
        instance.setJobCode(JOB_CODE);
        instance.setJobName(JOB_CODE);
        return instance;
    }

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

    private void resetEnvironment() throws Exception {
        cleanup();
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + TABLE);
            st.execute(
                "CREATE TABLE test." +
                TABLE +
                " (id bigint primary key, name varchar(100), amount decimal(10,2), updated_at datetime)"
            );
            st.execute(
                "INSERT INTO test." +
                TABLE +
                " (id, name, amount, updated_at) VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00')," +
                " (2, 'bob', 20.00, '2024-01-02 11:00:00')"
            );
        }
    }

    private void cleanup() throws Exception {
        DBUtils.closePool(DUCK_URL, null);
        new File("./target/mysql-cdc-snapshot-state.duckdb").delete();
        new File("./target/mysql-cdc-snapshot-state.duckdb.wal").delete();
        Files.deleteIfExists(statusPath());
    }

    private Path statusPath() {
        return Path.of(System.getProperty("user.dir"), "log", "status_" + JOB_CODE + ".json");
    }

    private Map<String, Object> readStatus() {
        try {
            if (!Files.exists(statusPath())) {
                return null;
            }
            return JSON.parseObject(Files.readString(statusPath()), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean statusContainsSnapshotCheckpoint() {
        Map<String, Object> status = readStatus();
        return (
            status != null &&
            Boolean.TRUE.equals(status.get("MySQLBinlogInput.snapshotDone")) &&
            status.get("MySQLBinlogInput.binlogFile") != null &&
            status.get("MySQLBinlogInput.binlogPosition") != null
        );
    }

    private int targetCount() {
        Integer count = targetInt("select count(*) from main." + TABLE);
        return count == null ? -1 : count;
    }

    private Integer targetInt(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String targetString(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Connection duckConnection() throws Exception {
        DatasourceInfo duck = new DatasourceInfo();
        duck.setUrl(DUCK_URL);
        return DBUtils.getConnection(duck);
    }

    private Connection openMysql() throws Exception {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }

    private void executeMysql(String sql) throws Exception {
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private void stop(ETLFlowTask runner, Future<?> future, ExecutorService executor) {
        runner.cancel();
        try {
            future.get(15, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
    }

    private void assertNoUnexpectedError(Throwable error) {
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("CDC 快照任务异常结束: " + error.getMessage(), error);
        }
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

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertTrue(false, message);
    }
}
