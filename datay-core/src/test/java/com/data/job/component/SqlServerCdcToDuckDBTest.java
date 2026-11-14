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
 * SQL Server CDC 实时同步到 DuckDB 的端到端测试。
 *
 * <p>覆盖快照、INSERT、UPDATE、DELETE。不满足 SQL Server CDC 前置条件时自动跳过。
 */
public class SqlServerCdcToDuckDBTest {

    private static final String SQLSERVER_URL = System.getProperty(
        "sqlserver.url",
        "jdbc:sqlserver://127.0.0.1:14333;databaseName=datay_cdc;encrypt=true;trustServerCertificate=true;loginTimeout=5"
    );
    private static final String SQLSERVER_USER = System.getProperty("sqlserver.user", "datay");
    private static final String SQLSERVER_PASSWORD = System.getProperty("sqlserver.password", "Datay123456!");
    private static final String SQLSERVER_SCHEMA = System.getProperty("sqlserver.schema", "dbo");
    private static final String DUCK_URL = "jdbc:duckdb:./target/sqlserver-cdc-duckdb-test.db";

    private static final String TABLE = "CDC_DEMO";

    @Test
    public void testSqlServerCdcToDuckDB() throws Exception {
        assumeTrue(supportsCdc(), "本地 SQL Server 未开启 CDC 或账号权限不足，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/sqlServerCdcToDuckDB.json")
            )
        );

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            // 快照同步启动前已存在的基线行
            awaitTrue(() -> duckTableExists() && queryDuckCount() == 1, 120000, "等待 SQL Server CDC 快照创建目标表超时");
            assertEquals("alice", queryDuckString("select NAME from main." + TABLE + " where ID = 1"));

            // INSERT
            executeSqlServer(
                "INSERT INTO " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " (ID, NAME, AMOUNT, UPDATED_AT) VALUES (2, N'bob', 20.00, '2024-01-02T11:00:00')"
            );
            awaitTrue(() -> queryDuckCount() == 2, 90000, "等待 SQL Server INSERT 同步到 DuckDB 超时");

            // UPDATE
            executeSqlServer(
                "UPDATE " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " SET NAME = N'alice2', AMOUNT = 11.11 WHERE ID = 1"
            );
            awaitTrue(
                () -> "alice2".equals(queryDuckString("select NAME from main." + TABLE + " where ID = 1")),
                90000,
                "等待 SQL Server UPDATE 同步到 DuckDB 超时"
            );
            assertEquals("11.11", queryDuckString("select AMOUNT from main." + TABLE + " where ID = 1"));

            // DELETE
            executeSqlServer("DELETE FROM " + SQLSERVER_SCHEMA + "." + TABLE + " WHERE ID = 2");
            awaitTrue(() -> queryDuckCount() == 1, 90000, "等待 SQL Server DELETE 同步到 DuckDB 超时");
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
            throw new AssertionError("SQL Server CDC 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private boolean supportsCdc() {
        try (Connection conn = sqlServerConnection()) {
            ensureDatabaseCdc(conn);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void resetEnvironment() throws Exception {
        closeDuck();
        deleteDuckFile();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        try (Connection conn = sqlServerConnection(); Statement st = conn.createStatement()) {
            ensureDatabaseCdc(conn);
            if (isTableTracked(conn, TABLE)) {
                st.execute(
                    "EXEC sys.sp_cdc_disable_table @source_schema=N'" +
                    SQLSERVER_SCHEMA +
                    "', @source_name=N'" +
                    TABLE +
                    "', @capture_instance=N'all'"
                );
            }
            st.execute("IF OBJECT_ID(N'" + SQLSERVER_SCHEMA + "." + TABLE + "', N'U') IS NOT NULL DROP TABLE " + SQLSERVER_SCHEMA + "." + TABLE);
            st.execute(
                "CREATE TABLE " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " (ID INT PRIMARY KEY, NAME NVARCHAR(100), AMOUNT DECIMAL(10,2), UPDATED_AT DATETIME2(3))"
            );
            st.execute(
                "INSERT INTO " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " (ID, NAME, AMOUNT, UPDATED_AT) VALUES (1, N'alice', 10.50, '2024-01-01T10:00:00')"
            );
            ensureTableCdc(conn, TABLE);
        }
    }

    private void ensureDatabaseCdc(Connection conn) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
            "SELECT is_cdc_enabled FROM sys.databases WHERE database_id = DB_ID()"
        )) {
            if (rs.next() && rs.getBoolean(1)) {
                return;
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("EXEC sys.sp_cdc_enable_db");
        }
    }

    private void ensureTableCdc(Connection conn, String table) throws Exception {
        if (isTableTracked(conn, table)) {
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.execute(
                "EXEC sys.sp_cdc_enable_table @source_schema=N'" +
                SQLSERVER_SCHEMA +
                "', @source_name=N'" +
                table +
                "', @role_name=NULL, @supports_net_changes=0"
            );
        }
    }

    private boolean isTableTracked(Connection conn, String table) throws Exception {
        try (
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "SELECT t.is_tracked_by_cdc FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id " +
                "WHERE s.name = N'" +
                SQLSERVER_SCHEMA +
                "' AND t.name = N'" +
                table +
                "'"
            )
        ) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private void executeSqlServer(String sql) throws Exception {
        try (Connection conn = sqlServerConnection(); Statement st = conn.createStatement()) {
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

    private Connection sqlServerConnection() throws Exception {
        DatasourceInfo sqlServer = new DatasourceInfo();
        sqlServer.setType("sqlserver");
        sqlServer.setUrl(SQLSERVER_URL);
        sqlServer.setUsername(SQLSERVER_USER);
        sqlServer.setPassword(SQLSERVER_PASSWORD);
        sqlServer.setDbschema(SQLSERVER_SCHEMA);
        return DBUtils.getConnection(sqlServer);
    }

    private void closeDuck() {
        try {
            DBUtils.closePool(DUCK_URL, null);
        } catch (Exception ignored) {
        }
    }

    private void deleteDuckFile() {
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File("./target/sqlserver-cdc-duckdb-test.db" + suffix);
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
