package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
 * 达梦 CDC（DBMS_LOGMNR）实时同步到 DuckDB 的端到端测试。
 *
 * <p>前置条件（可用 {@code src/test/docker/dm} 启动本地达梦容器）：
 * <ul>
 *   <li>数据库处于归档模式；</li>
 *   <li>{@code RLOG_APPEND_LOGIC=2}、{@code RLOG_IGNORE_TABLE_SET=1}；</li>
 *   <li>已创建 {@code DBMS_LOGMNR} 系统包；</li>
 *   <li>账号具备归档切换与日志挖掘权限。</li>
 * </ul>
 * 不满足时测试自动跳过。
 */
public class DmCdcToDuckDBTest {

    private static final String DM_URL = System.getProperty("dm.url", "jdbc:dm://127.0.0.1:5236");
    private static final String DM_USER = System.getProperty("dm.user", "DATAY");
    private static final String DM_PASSWORD = System.getProperty("dm.password", "Datay123456");
    private static final String DM_SCHEMA = System.getProperty("dm.schema", "DATAY");
    private static final String DUCK_URL = "jdbc:duckdb:./target/dm-cdc-duckdb-test.db";

    private static final String TABLE = "CDC_DEMO";

    @Test
    public void testDmCdcToDuckDB() throws Exception {
        assumeTrue(supportsLogMiner(), "本地达梦未开启归档/逻辑日志或账号权限不足，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/dmCdcToDuckDB.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            // 快照同步启动前已存在的数据
            awaitTrue(() -> duckTableExists() && queryDuckCount() == 1, 120000, "等待达梦 CDC 快照创建目标表超时");
            assertEquals("alice", queryDuckString("select NAME from main." + TABLE + " where ID = 1"));

            // INSERT
            executeDm("INSERT INTO " + DM_SCHEMA + "." + TABLE + " (ID, NAME, AMOUNT, UPDATED_AT) VALUES (2, 'bob', 20.00, TIMESTAMP '2024-01-02 11:00:00')");
            awaitTrue(() -> queryDuckCount() == 2, 90000, "等待 INSERT 同步到 DuckDB 超时");

            // UPDATE
            executeDm("UPDATE " + DM_SCHEMA + "." + TABLE + " SET NAME = 'alice2', AMOUNT = 11.11 WHERE ID = 1");
            awaitTrue(
                () -> "alice2".equals(queryDuckString("select NAME from main." + TABLE + " where ID = 1")),
                90000,
                "等待 UPDATE 同步到 DuckDB 超时"
            );
            assertEquals("11.11", queryDuckString("select AMOUNT from main." + TABLE + " where ID = 1"));

            // DELETE
            executeDm("DELETE FROM " + DM_SCHEMA + "." + TABLE + " WHERE ID = 2");
            awaitTrue(() -> queryDuckCount() == 1, 90000, "等待 DELETE 同步到 DuckDB 超时");
            assertEquals("alice2", queryDuckString("select NAME from main." + TABLE + " where ID = 1"));
        } finally {
            runner.cancel();
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
            closeDuck();
            deleteDuckFile();
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("达梦 CDC 同步任务异常结束: " + error.getMessage(), error);
        }
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
            if (paraInt(conn, "LOGMNR_DELAY_ANALYZE") == 1) {
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
            if (!rs.next()) {
                return -1;
            }
            String value = rs.getString(1);
            if (value == null) {
                return -1;
            }
            String trimmed = value.trim();
            int space = trimmed.indexOf(' ');
            if (space > 0) {
                trimmed = trimmed.substring(0, space);
            }
            return Integer.parseInt(trimmed);
        }
    }

    private void resetEnvironment() throws Exception {
        closeDuck();
        deleteDuckFile();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
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
                " (ID INT PRIMARY KEY, NAME VARCHAR(100), AMOUNT DECIMAL(10,2), UPDATED_AT TIMESTAMP)"
            );
            st.execute(
                "INSERT INTO " +
                DM_SCHEMA +
                "." +
                TABLE +
                " (ID, NAME, AMOUNT, UPDATED_AT) VALUES (1, 'alice', 10.50, TIMESTAMP '2024-01-01 10:00:00')"
            );
        }
    }

    private void executeDm(String sql) throws Exception {
        try (Connection conn = dmConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private boolean duckTableExists() {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = lower('" + TABLE + "')"
            )) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private int queryDuckCount() {
        Integer count = queryDuckInt("select count(*) from main." + TABLE);
        return count == null ? -1 : count;
    }

    private Integer queryDuckInt(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String queryDuckString(String sql) {
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

    private void closeDuck() {
        try {
            DBUtils.closePool(DUCK_URL, null);
        } catch (Exception ignored) {
        }
    }

    private void deleteDuckFile() {
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File("./target/dm-cdc-duckdb-test.db" + suffix);
            if (file.exists()) {
                file.delete();
            }
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
