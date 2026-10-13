package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
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
 * Oracle CDC（LogMiner）实时同步到 DuckDB 的端到端测试。
 *
 * <p>前置条件（本地 Oracle，可用 {@code src/test/docker/oracle} 启动）：
 * <ul>
 *   <li>数据库处于 {@code ARCHIVELOG} 模式；</li>
 *   <li>开启补充日志：{@code ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;}；</li>
 *   <li>账号 {@code datay} 具备 {@code LOGMINING} 角色与 {@code SELECT ANY TRANSACTION} 权限。</li>
 * </ul>
 * 不满足时测试自动跳过。
 */
public class OracleCdcToDuckDBTest {

    private static final String ORACLE_URL = System.getProperty("oracle.url", "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1");
    private static final String ORACLE_USER = System.getProperty("oracle.user", "datay");
    private static final String ORACLE_PASSWORD = System.getProperty("oracle.password", "datay123");
    private static final String DUCK_URL = "jdbc:duckdb:./target/oracle-cdc-duckdb-test.db";

    private static final String SCHEMA = "DATAY";
    private static final String TABLE = "CDC_DEMO";

    @Test
    public void testOracleCdcToDuckDB() throws Exception {
        assumeTrue(supportsLogMiner(), "本地 Oracle 未开启 ARCHIVELOG/补充日志或 datay 账号权限不足，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/oracleCdcToDuckDB.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            // 等待快照完成并创建目标表
            awaitTrue(this::duckTableExists, 90000, "等待 Oracle CDC 快照创建目标表超时");

            // INSERT
            executeOracle("INSERT INTO " + SCHEMA + "." + TABLE + " (ID, NAME, AMOUNT, UPDATED_AT) VALUES (1, 'alice', 10.50, TIMESTAMP '2024-01-01 10:00:00')");
            executeOracle("INSERT INTO " + SCHEMA + "." + TABLE + " (ID, NAME, AMOUNT, UPDATED_AT) VALUES (2, 'bob', 20.00, TIMESTAMP '2024-01-02 11:00:00')");
            awaitTrue(() -> queryDuckCount() == 2, 60000, "等待 INSERT 同步到 DuckDB 超时");
            assertEquals("alice", queryDuckString("select NAME from main." + TABLE + " where ID = 1"));
            assertEquals("10.50", queryDuckString("select AMOUNT from main." + TABLE + " where ID = 1"));

            // UPDATE
            executeOracle("UPDATE " + SCHEMA + "." + TABLE + " SET NAME = 'alice2', AMOUNT = 11.11 WHERE ID = 1");
            awaitTrue(() -> "alice2".equals(queryDuckString("select NAME from main." + TABLE + " where ID = 1")), 60000, "等待 UPDATE 同步到 DuckDB 超时");
            assertEquals("11.11", queryDuckString("select AMOUNT from main." + TABLE + " where ID = 1"));

            // DELETE
            executeOracle("DELETE FROM " + SCHEMA + "." + TABLE + " WHERE ID = 2");
            awaitTrue(() -> queryDuckCount() == 1, 60000, "等待 DELETE 同步到 DuckDB 超时");
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
            throw new AssertionError("CDC 同步任务异常结束: " + error.getMessage(), error);
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

    private boolean supportsLogMiner() {
        try (Connection conn = openOracle()) {
            String logMode;
            String suppMin;
            try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT LOG_MODE, SUPPLEMENTAL_LOG_DATA_MIN FROM V$DATABASE")) {
                if (!rs.next()) {
                    return false;
                }
                logMode = rs.getString(1);
                suppMin = rs.getString(2);
            }
            if (!"ARCHIVELOG".equalsIgnoreCase(logMode)) {
                return false;
            }
            if (suppMin == null || "NO".equalsIgnoreCase(suppMin)) {
                return false;
            }
            boolean roleOk = queryInt(conn, "SELECT COUNT(*) FROM SESSION_ROLES WHERE ROLE = 'LOGMINING'") > 0;
            boolean privOk = queryInt(conn, "SELECT COUNT(*) FROM SESSION_PRIVS WHERE PRIVILEGE = 'SELECT ANY TRANSACTION'") > 0;
            return roleOk || privOk;
        } catch (Exception e) {
            return false;
        }
    }

    private int queryInt(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void resetEnvironment() throws Exception {
        closeDuck();
        deleteDuckFile();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        try (Connection conn = openOracle(); Statement st = conn.createStatement()) {
            try {
                st.execute("DROP TABLE " + SCHEMA + "." + TABLE);
            } catch (Exception ignored) {
            }
            st.execute(
                "CREATE TABLE " +
                SCHEMA +
                "." +
                TABLE +
                " (ID NUMBER(10) PRIMARY KEY, NAME VARCHAR2(100), AMOUNT NUMBER(10,2), UPDATED_AT TIMESTAMP)"
            );
        }
    }

    private void executeOracle(String sql) throws Exception {
        try (Connection conn = openOracle(); Statement st = conn.createStatement()) {
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

    private void closeDuck() {
        try {
            DBUtils.closePool(DUCK_URL, null);
        } catch (Exception ignored) {
        }
    }

    private void deleteDuckFile() {
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File("./target/oracle-cdc-duckdb-test.db" + suffix);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private Connection openOracle() throws Exception {
        return DriverManager.getConnection(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD);
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
