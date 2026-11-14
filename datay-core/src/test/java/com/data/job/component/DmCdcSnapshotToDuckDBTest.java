package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
 * 达梦 CDC 历史全量快照验证。
 *
 * <p>验证多批次快照（{@code snapshotFetchSize=500}，共 20000 行）、快照完成位点持久化，
 * 以及重启后从快照位点继续消费停机期间的增量变更。
 */
public class DmCdcSnapshotToDuckDBTest {

    private static final String DM_URL = System.getProperty("dm.url", "jdbc:dm://127.0.0.1:5236");
    private static final String DM_USER = System.getProperty("dm.user", "DATAY");
    private static final String DM_PASSWORD = System.getProperty("dm.password", "Datay123456");
    private static final String DM_SCHEMA = System.getProperty("dm.schema", "DATAY");
    private static final String DUCK_URL = "jdbc:duckdb:./target/dm-cdc-snapshot.duckdb";
    private static final String TABLE = "CDC_SNAPSHOT";
    private static final String JOB_CODE = "dm-cdc-snapshot-test";

    private static final int SNAPSHOT_ROWS = 20_000;
    private static final BigDecimal EXPECTED_SUM = new BigDecimal("250012500.00");

    @Test
    public void snapshotShouldReadAllHistoryAndResumeFromCheckpoint() throws Exception {
        assumeTrue(supportsLogMiner(), "本地达梦未开启归档/逻辑日志或账号权限不足，跳过测试");
        resetEnvironment();

        // ---------- 第一次运行：读取 20000 行历史全量数据 ----------
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
                180000,
                "等待达梦快照完成状态（snapshotDone + binlogPosition）持久化超时"
            );
            awaitTrue(() -> targetCount() == SNAPSHOT_ROWS, 180000, "等待达梦历史全量快照同步到 DuckDB 超时");

            assertEquals(SNAPSHOT_ROWS, targetInt("select count(distinct ID) from main." + TABLE));
            assertEquals("1", targetString("select min(ID) from main." + TABLE));
            assertEquals(String.valueOf(SNAPSHOT_ROWS), targetString("select max(ID) from main." + TABLE));
            assertEquals("name-12345", targetString("select NAME from main." + TABLE + " where ID = 12345"));
            assertEquals(EXPECTED_SUM, new BigDecimal(targetString("select sum(AMOUNT) from main." + TABLE)));

            Map<String, Object> status = readStatus();
            assertNotNull(status);
            assertEquals(Boolean.TRUE, status.get("DMCDCInput.snapshotDone"), "snapshotDone 应已持久化");
            assertNotNull(status.get("DMCDCInput.binlogPosition"), "快照起始 SCN 应与 snapshotDone 一起持久化");
        } finally {
            stop(firstRun, firstFuture, firstExecutor);
            assertNoUnexpectedError(firstError.get());
        }

        // 模拟任务停止期间产生的增量变更
        executeDm(
            "INSERT INTO " +
            DM_SCHEMA +
            "." +
            TABLE +
            " (ID, NAME, AMOUNT, CREATED_AT) VALUES (" +
            (SNAPSHOT_ROWS + 1) +
            ", 'offline', 1.00, TIMESTAMP '2024-01-02 00:00:00')"
        );

        // ---------- 第二次运行：有断点，不应重复全量，应补放停机期间变更 ----------
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
                    targetCount() == SNAPSHOT_ROWS + 1 &&
                    "offline".equals(targetString("select NAME from main." + TABLE + " where ID = " + (SNAPSHOT_ROWS + 1))),
                120000,
                "重启后未从快照位点补放停机期间的增量变更"
            );
        } finally {
            stop(secondRun, secondFuture, secondExecutor);
            assertNoUnexpectedError(secondError.get());
            cleanup();
        }
    }

    private String job() throws Exception {
        return new String(
            Files.readAllBytes(Path.of(System.getProperty("user.dir"), "src/test/dmCdcSnapshotToDuckDB.json")),
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

    private boolean supportsLogMiner() {
        try (Connection conn = dmConnection()) {
            String archMode;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT ARCH_MODE FROM V$DATABASE")) {
                if (!rs.next()) {
                    return false;
                }
                archMode = rs.getString(1);
            }
            if (!("Y".equalsIgnoreCase(archMode) || "1".equals(archMode))) {
                return false;
            }
            if (paraInt(conn, "RLOG_APPEND_LOGIC") < 2) {
                return false;
            }
            if (paraInt(conn, "RLOG_IGNORE_TABLE_SET") != 1) {
                return false;
            }
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER SYSTEM ARCHIVE LOG CURRENT");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int paraInt(Connection conn, String name) throws Exception {
        try (
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "SELECT PARA_VALUE FROM V$DM_INI WHERE PARA_NAME = '" + name.replace("'", "''") + "'"
            )
        ) {
            if (!rs.next() || rs.getString(1) == null) {
                return -1;
            }
            String trimmed = rs.getString(1).trim();
            int space = trimmed.indexOf(' ');
            if (space > 0) {
                trimmed = trimmed.substring(0, space);
            }
            return Integer.parseInt(trimmed);
        }
    }

    private void resetEnvironment() throws Exception {
        cleanup();
        try (Connection conn = dmConnection(); Statement st = conn.createStatement()) {
            try {
                st.execute("DROP TABLE " + DM_SCHEMA + "." + TABLE);
            } catch (Exception ignored) {
            }
            st.execute(
                "CREATE TABLE " +
                DM_SCHEMA +
                "." +
                TABLE +
                " (ID INT PRIMARY KEY, NAME VARCHAR(100), AMOUNT DECIMAL(10,2), CREATED_AT TIMESTAMP)"
            );
            st.execute(
                "INSERT INTO " +
                DM_SCHEMA +
                "." +
                TABLE +
                " (ID, NAME, AMOUNT, CREATED_AT) SELECT LEVEL, 'name-' || LEVEL, LEVEL * 1.25, " +
                "TIMESTAMP '2024-01-01 00:00:00' FROM DUAL CONNECT BY LEVEL <= " +
                SNAPSHOT_ROWS
            );
        }
    }

    private void executeDm(String sql) throws Exception {
        try (Connection conn = dmConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private void cleanup() throws Exception {
        DBUtils.closePool(DUCK_URL, null);
        new File("./target/dm-cdc-snapshot.duckdb").delete();
        new File("./target/dm-cdc-snapshot.duckdb.wal").delete();
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
            Boolean.TRUE.equals(status.get("DMCDCInput.snapshotDone")) &&
            status.get("DMCDCInput.binlogPosition") != null
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

    private Connection dmConnection() throws Exception {
        DatasourceInfo dm = new DatasourceInfo();
        dm.setType("dm");
        dm.setUrl(DM_URL);
        dm.setUsername(DM_USER);
        dm.setPassword(DM_PASSWORD);
        dm.setDbschema(DM_SCHEMA);
        return DBUtils.getConnection(dm);
    }

    private void stop(ETLFlowTask runner, Future<?> future, ExecutorService executor) {
        runner.cancel();
        try {
            future.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
    }

    private void assertNoUnexpectedError(Throwable error) {
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("达梦 CDC 快照任务异常结束: " + error.getMessage(), error);
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
            Thread.sleep(500);
        }
        assertTrue(false, message);
    }
}
