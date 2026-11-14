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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SQL Server CDC 实时同步到 MySQL 的端到端测试。
 *
 * <p>覆盖快照、INSERT、UPDATE、DELETE、常用字段类型与 NULL 行。
 * 不满足 SQL Server/MySQL 前置条件时自动跳过。
 */
public class SqlServerCdcToMysqlTest {

    private static final String SQLSERVER_URL = System.getProperty(
        "sqlserver.url",
        "jdbc:sqlserver://127.0.0.1:14333;databaseName=datay_cdc;encrypt=true;trustServerCertificate=true;loginTimeout=5"
    );
    private static final String SQLSERVER_USER = System.getProperty("sqlserver.user", "datay");
    private static final String SQLSERVER_PASSWORD = System.getProperty("sqlserver.password", "Datay123456!");
    private static final String SQLSERVER_SCHEMA = System.getProperty("sqlserver.schema", "dbo");

    private static final String MYSQL_HOST = System.getProperty("mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("mysql.port", "3307");
    private static final String MYSQL_USER = System.getProperty("mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "password");
    private static final String MYSQL_DB = System.getProperty("mysql.database", "datay_sqlserver_cdc");

    private static final String MYSQL_SERVER_URL =
        "jdbc:mysql://" +
        MYSQL_HOST +
        ":" +
        MYSQL_PORT +
        "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_URL =
        "jdbc:mysql://" +
        MYSQL_HOST +
        ":" +
        MYSQL_PORT +
        "/" +
        MYSQL_DB +
        "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=%2B08%3A00&forceConnectionTimeZoneToSession=true";

    private static final String TABLE = "CDC_SQLSERVER_MYSQL";
    private static final String MYSQL_TABLE = "cdc_sqlserver_mysql";

    @Test
    public void testSqlServerCdcToMysql() throws Exception {
        assumeTrue(supportsCdc(), "本地 SQL Server 未开启 CDC 或账号权限不足，跳过测试");
        assumeTrue(mysqlReachable(), "目标 MySQL 不可用，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/sqlServerCdcToMysql.json")
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
            awaitTrue(() -> mysqlTableExists() && queryMysqlCount() == 1, 120000, "等待 SQL Server CDC 快照创建 MySQL 目标表超时");
            assertEquals("baseline", queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = 1"));

            // INSERT：覆盖常用字段类型
            executeSqlServer(
                "INSERT INTO " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " (ID, C_TINYINT, C_SMALLINT, C_INT, C_BIGINT, C_DECIMAL, C_NUMERIC, C_FLOAT, C_REAL, " +
                "C_BIT, C_CHAR, C_VARCHAR, C_NVARCHAR, C_DATE, C_TIME, C_DATETIME2, C_DATETIMEOFFSET, " +
                "C_UUID, C_VARBINARY) VALUES (" +
                "2, 5, -1234, 123456, 9000000000, 12345.6789, 98765.4321, 1.5, 2.25, " +
                "1, 'abc', N'hello-world', N'中文值', '2024-01-01', '12:34:56', " +
                "'2024-01-01T10:00:00.123', '2024-01-01T10:00:00.123+08:00', " +
                "'11111111-2222-3333-4444-555555555555', 0x010203)"
            );
            awaitTrue(() -> queryMysqlCount() == 2, 90000, "等待 SQL Server INSERT 同步到 MySQL 超时");
            assertEquals("5", queryMysqlString("select C_TINYINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("-1234", queryMysqlString("select C_SMALLINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("123456", queryMysqlString("select C_INT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("9000000000", queryMysqlString("select C_BIGINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("12345.6789", queryMysqlString("select C_DECIMAL from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("98765.4321", queryMysqlString("select C_NUMERIC from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(1.5d, Double.parseDouble(queryMysqlString("select C_FLOAT from " + MYSQL_TABLE + " where ID = 2")), 0.0001d);
            assertEquals(2.25d, Double.parseDouble(queryMysqlString("select C_REAL from " + MYSQL_TABLE + " where ID = 2")), 0.0001d);
            assertEquals("1", queryMysqlString("select C_BIT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("abc", queryMysqlString("select trim(C_CHAR) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("hello-world", queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("中文值", queryMysqlString("select C_NVARCHAR from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("2024-01-01", queryMysqlString("select cast(C_DATE as char) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("12:34:56", queryMysqlString("select date_format(C_TIME, '%H:%i:%s') from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(
                "2024-01-01 10:00:00",
                queryMysqlString("select date_format(C_DATETIME2, '%Y-%m-%d %H:%i:%s') from " + MYSQL_TABLE + " where ID = 2")
            );
            String dateTimeOffset = queryMysqlString("select C_DATETIMEOFFSET from " + MYSQL_TABLE + " where ID = 2");
            assertTrue(
                dateTimeOffset != null && dateTimeOffset.startsWith("2024-01-01T10:00:00.123"),
                "DATETIMEOFFSET 同步结果不符合预期: " + dateTimeOffset
            );
            assertEquals(
                "11111111-2222-3333-4444-555555555555",
                queryMysqlString("select lower(cast(C_UUID as char)) from " + MYSQL_TABLE + " where ID = 2")
            );
            assertEquals("010203", queryMysqlString("select lower(hex(C_VARBINARY)) from " + MYSQL_TABLE + " where ID = 2"));

            // UPDATE：数值、字符、日期时间、BIT
            executeSqlServer(
                "UPDATE " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " SET C_TINYINT = 6, C_SMALLINT = -2345, C_INT = 654321, C_BIGINT = 8000000000, " +
                "C_DECIMAL = 99999.1234, C_NUMERIC = 11111.2222, C_FLOAT = 2.5, C_REAL = 3.25, " +
                "C_BIT = 0, C_CHAR = 'xyz', C_VARCHAR = N'updated-value', C_NVARCHAR = N'更新值', " +
                "C_DATE = '2025-01-02', C_TIME = '01:02:03', " +
                "C_DATETIME2 = '2025-01-02T01:02:03.456', " +
                "C_DATETIMEOFFSET = '2025-01-02T01:02:03.456+08:00', " +
                "C_UUID = 'AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE', C_VARBINARY = 0x040506 WHERE ID = 2"
            );
            awaitTrue(
                () -> "updated-value".equals(queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = 2")),
                90000,
                "等待 SQL Server UPDATE 同步到 MySQL 超时"
            );
            assertEquals("6", queryMysqlString("select C_TINYINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("-2345", queryMysqlString("select C_SMALLINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("654321", queryMysqlString("select C_INT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("8000000000", queryMysqlString("select C_BIGINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("99999.1234", queryMysqlString("select C_DECIMAL from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("11111.2222", queryMysqlString("select C_NUMERIC from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(2.5d, Double.parseDouble(queryMysqlString("select C_FLOAT from " + MYSQL_TABLE + " where ID = 2")), 0.0001d);
            assertEquals(3.25d, Double.parseDouble(queryMysqlString("select C_REAL from " + MYSQL_TABLE + " where ID = 2")), 0.0001d);
            assertEquals("0", queryMysqlString("select C_BIT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("xyz", queryMysqlString("select trim(C_CHAR) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("更新值", queryMysqlString("select C_NVARCHAR from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("2025-01-02", queryMysqlString("select cast(C_DATE as char) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("01:02:03", queryMysqlString("select date_format(C_TIME, '%H:%i:%s') from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(
                "2025-01-02 01:02:03",
                queryMysqlString("select date_format(C_DATETIME2, '%Y-%m-%d %H:%i:%s') from " + MYSQL_TABLE + " where ID = 2")
            );
            assertEquals(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                queryMysqlString("select lower(cast(C_UUID as char)) from " + MYSQL_TABLE + " where ID = 2")
            );
            assertEquals("040506", queryMysqlString("select lower(hex(C_VARBINARY)) from " + MYSQL_TABLE + " where ID = 2"));

            // NULL 行
            executeSqlServer(
                "INSERT INTO " + SQLSERVER_SCHEMA + "." + TABLE + " (ID, C_VARCHAR) VALUES (3, N'only-string')"
            );
            awaitTrue(() -> queryMysqlCount() == 3, 90000, "等待 SQL Server NULL 行同步到 MySQL 超时");
            assertNull(queryMysqlString("select C_DECIMAL from " + MYSQL_TABLE + " where ID = 3"));
            assertNull(queryMysqlString("select C_DATE from " + MYSQL_TABLE + " where ID = 3"));
            assertEquals("only-string", queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = 3"));

            // DELETE
            executeSqlServer("DELETE FROM " + SQLSERVER_SCHEMA + "." + TABLE + " WHERE ID = 1");
            awaitTrue(() -> queryMysqlCount() == 2, 90000, "等待 SQL Server DELETE 同步到 MySQL 超时");

            printTargetTypes();
        } finally {
            runner.cancel();
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
            closeMysqlPools();
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("SQL Server CDC 到 MySQL 同步任务异常结束: " + error.getMessage(), error);
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
        closeMysqlPools();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }

        try (Connection conn = mysqlServerConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + MYSQL_DB + " CHARACTER SET utf8mb4");
        }
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + MYSQL_TABLE);
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
            st.execute(
                "IF OBJECT_ID(N'" + SQLSERVER_SCHEMA + "." + TABLE + "', N'U') IS NOT NULL DROP TABLE " + SQLSERVER_SCHEMA + "." + TABLE
            );
            st.execute(
                "CREATE TABLE " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " (" +
                "ID INT PRIMARY KEY, " +
                "C_TINYINT TINYINT, C_SMALLINT SMALLINT, C_INT INT, C_BIGINT BIGINT, " +
                "C_DECIMAL DECIMAL(12,4), C_NUMERIC NUMERIC(12,4), C_FLOAT FLOAT, C_REAL REAL, C_BIT BIT, " +
                "C_CHAR CHAR(10), C_VARCHAR VARCHAR(100), C_NVARCHAR NVARCHAR(100), " +
                "C_DATE DATE, C_TIME TIME(0), C_DATETIME2 DATETIME2(3), C_DATETIMEOFFSET DATETIMEOFFSET(3), " +
                "C_UUID UNIQUEIDENTIFIER, C_VARBINARY VARBINARY(100))"
            );
            st.execute(
                "INSERT INTO " +
                SQLSERVER_SCHEMA +
                "." +
                TABLE +
                " (ID, C_VARCHAR) VALUES (1, N'baseline')"
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

    private boolean mysqlReachable() {
        try (Connection conn = mysqlServerConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean mysqlTableExists() {
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                "select count(*) from information_schema.tables where table_schema = '" +
                MYSQL_DB +
                "' and table_name = '" +
                MYSQL_TABLE +
                "'"
            )) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private int queryMysqlCount() {
        Integer count = queryMysqlInt("select count(*) from " + MYSQL_TABLE);
        return count == null ? -1 : count;
    }

    private Integer queryMysqlInt(String sql) {
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String queryMysqlString(String sql) {
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void printTargetTypes() throws Exception {
        try (
            Connection conn = mysqlConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "select COLUMN_NAME, DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE from information_schema.columns " +
                "where TABLE_SCHEMA = '" + MYSQL_DB + "' and TABLE_NAME = '" + MYSQL_TABLE + "' order by ORDINAL_POSITION"
            )
        ) {
            StringBuilder types = new StringBuilder();
            while (rs.next()) {
                if (types.length() > 0) {
                    types.append(", ");
                }
                types
                    .append(rs.getString(1))
                    .append("=")
                    .append(rs.getString(2))
                    .append("(")
                    .append(rs.getString(3))
                    .append(",")
                    .append(rs.getString(4))
                    .append(")");
            }
            System.out.println("[SQLSERVER-MYSQL-TYPES] " + types);
        }
    }

    private Connection mysqlServerConnection() throws Exception {
        DatasourceInfo mysql = new DatasourceInfo();
        mysql.setType("mysql");
        mysql.setUrl(MYSQL_SERVER_URL);
        mysql.setUsername(MYSQL_USER);
        mysql.setPassword(MYSQL_PASSWORD);
        return DBUtils.getConnection(mysql);
    }

    private Connection mysqlConnection() throws Exception {
        DatasourceInfo mysql = new DatasourceInfo();
        mysql.setType("mysql");
        mysql.setUrl(MYSQL_URL);
        mysql.setUsername(MYSQL_USER);
        mysql.setPassword(MYSQL_PASSWORD);
        mysql.setDbschema(MYSQL_DB);
        return DBUtils.getConnection(mysql);
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

    private void closeMysqlPools() {
        try {
            DBUtils.closePool(MYSQL_URL, null);
        } catch (Exception ignored) {
        }
        try {
            DBUtils.closePool(MYSQL_SERVER_URL, null);
        } catch (Exception ignored) {
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
