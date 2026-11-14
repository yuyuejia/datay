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
 * 达梦 CDC（DBMS_LOGMNR）实时同步到 MySQL 的端到端测试。
 *
 * <p>覆盖快照、INSERT、UPDATE、DELETE、NULL 行以及达梦常用字段类型写入 MySQL。
 * 不满足达梦/MySQL 前置条件时自动跳过。
 */
public class DmCdcToMysqlTest {

    private static final String DM_URL = System.getProperty("dm.url", "jdbc:dm://127.0.0.1:5236");
    private static final String DM_USER = System.getProperty("dm.user", "DATAY");
    private static final String DM_PASSWORD = System.getProperty("dm.password", "Datay123456");
    private static final String DM_SCHEMA = System.getProperty("dm.schema", "DATAY");

    private static final String MYSQL_HOST = System.getProperty("mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("mysql.port", "3307");
    private static final String MYSQL_USER = System.getProperty("mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "password");
    private static final String MYSQL_DB = System.getProperty("mysql.database", "datay_dm_cdc");

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

    private static final String TABLE = "CDC_DM_MYSQL";
    private static final String MYSQL_TABLE = "cdc_dm_mysql";

    @Test
    public void testDmCdcToMysql() throws Exception {
        assumeTrue(supportsLogMiner(), "本地达梦未开启归档/逻辑日志或账号权限不足，跳过测试");
        assumeTrue(mysqlReachable(), "目标 MySQL 不可用，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/dmCdcToMysql.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            // 快照同步启动前已存在的基线行
            awaitTrue(() -> mysqlTableExists() && queryMysqlCount() == 1, 120000, "等待达梦 CDC 快照创建 MySQL 目标表超时");
            assertEquals("baseline", queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = 1"));

            // INSERT：覆盖常用字段类型
            executeDm(
                "INSERT INTO " +
                DM_SCHEMA +
                "." +
                TABLE +
                " (ID, C_TINYINT, C_SMALLINT, C_INT, C_BIGINT, C_DECIMAL, C_NUMBER, C_FLOAT, C_DOUBLE, " +
                "C_CHAR, C_VARCHAR, C_VARCHAR2, C_NVARCHAR2, C_DATE, C_TIME, C_DATETIME, C_TIMESTAMP, C_BIT) VALUES (" +
                "2, -5, 1234, 123456, 9000000000, 12345.6789, 98765.4321, 1.5, 2.25, " +
                "'abc', 'hello世界', 'varchar2', '中文', DATE '2024-01-01', TIME '12:34:56', " +
                "TIMESTAMP '2024-01-01 10:00:00', TIMESTAMP '2024-01-01 10:00:00.123456', 1)"
            );
            awaitTrue(() -> queryMysqlCount() == 2, 90000, "等待达梦 INSERT 同步到 MySQL 超时");
            assertMysqlValues(2, "-5", "hello世界");

            // UPDATE：数值、字符、日期时间、BIT
            executeDm(
                "UPDATE " +
                DM_SCHEMA +
                "." +
                TABLE +
                " SET C_TINYINT = -6, C_SMALLINT = 2345, C_INT = 654321, C_BIGINT = 8000000000, " +
                "C_DECIMAL = 99999.1234, C_NUMBER = 11111.2222, C_FLOAT = 2.25, C_DOUBLE = 3.5, " +
                "C_CHAR = 'xyz', C_VARCHAR = 'updated值', C_VARCHAR2 = 'v2', C_NVARCHAR2 = '更新', " +
                "C_DATE = DATE '2025-01-02', C_TIME = TIME '01:02:03', " +
                "C_DATETIME = TIMESTAMP '2025-01-02 01:02:03', C_TIMESTAMP = TIMESTAMP '2025-01-02 01:02:03.456789', " +
                "C_BIT = 0 WHERE ID = 2"
            );
            awaitTrue(
                () -> "updated值".equals(queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = 2")),
                90000,
                "等待达梦 UPDATE 同步到 MySQL 超时"
            );
            assertEquals("-6", queryMysqlString("select C_TINYINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("2345", queryMysqlString("select C_SMALLINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("654321", queryMysqlString("select C_INT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("8000000000", queryMysqlString("select C_BIGINT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("99999.1234", queryMysqlString("select C_DECIMAL from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("11111.2222", queryMysqlString("select C_NUMBER from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(2.25d, Double.parseDouble(queryMysqlString("select C_FLOAT from " + MYSQL_TABLE + " where ID = 2")), 0.0001d);
            assertEquals(3.5d, Double.parseDouble(queryMysqlString("select C_DOUBLE from " + MYSQL_TABLE + " where ID = 2")), 0.0001d);
            assertEquals("xyz", queryMysqlString("select trim(C_CHAR) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("v2", queryMysqlString("select C_VARCHAR2 from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("更新", queryMysqlString("select C_NVARCHAR2 from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("2025-01-02", queryMysqlString("select cast(C_DATE as char) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("01:02:03", queryMysqlString("select date_format(C_TIME, '%H:%i:%s') from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("2025-01-02 01:02:03", queryMysqlString("select date_format(C_DATETIME, '%Y-%m-%d %H:%i:%s') from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("0", queryMysqlString("select C_BIT from " + MYSQL_TABLE + " where ID = 2"));

            // NULL 行
            executeDm("INSERT INTO " + DM_SCHEMA + "." + TABLE + " (ID, C_VARCHAR) VALUES (3, 'only-string')");
            awaitTrue(() -> queryMysqlCount() == 3, 90000, "等待达梦 NULL 行同步到 MySQL 超时");
            assertNull(queryMysqlString("select C_DECIMAL from " + MYSQL_TABLE + " where ID = 3"));
            assertNull(queryMysqlString("select C_DATE from " + MYSQL_TABLE + " where ID = 3"));
            assertEquals("only-string", queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = 3"));

            // DELETE
            executeDm("DELETE FROM " + DM_SCHEMA + "." + TABLE + " WHERE ID = 1");
            awaitTrue(() -> queryMysqlCount() == 2, 90000, "等待达梦 DELETE 同步到 MySQL 超时");

            printTargetTypes();
        } finally {
            runner.cancel();
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("达梦 CDC 到 MySQL 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void assertMysqlValues(int id, String tinyint, String varchar) {
        assertEquals(tinyint, queryMysqlString("select C_TINYINT from " + MYSQL_TABLE + " where ID = " + id));
        assertEquals(varchar, queryMysqlString("select C_VARCHAR from " + MYSQL_TABLE + " where ID = " + id));
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
            System.out.println("[DM-MYSQL-TYPES] " + types);
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

    private boolean mysqlReachable() {
        try (Connection conn = mysqlServerConnection()) {
            return conn != null && !conn.isClosed();
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
                " (" +
                "ID INT PRIMARY KEY, " +
                "C_TINYINT TINYINT, " +
                "C_SMALLINT SMALLINT, " +
                "C_INT INT, " +
                "C_BIGINT BIGINT, " +
                "C_DECIMAL DECIMAL(12,4), " +
                "C_NUMBER NUMBER(12,4), " +
                "C_FLOAT FLOAT, " +
                "C_DOUBLE DOUBLE, " +
                "C_CHAR CHAR(10), " +
                "C_VARCHAR VARCHAR(100), " +
                "C_VARCHAR2 VARCHAR2(100), " +
                "C_NVARCHAR2 NVARCHAR2(50), " +
                "C_DATE DATE, " +
                "C_TIME TIME, " +
                "C_DATETIME DATETIME, " +
                "C_TIMESTAMP TIMESTAMP, " +
                "C_BIT BIT)"
            );
            st.execute("INSERT INTO " + DM_SCHEMA + "." + TABLE + " (ID, C_TINYINT, C_VARCHAR) VALUES (1, 1, 'baseline')");
        }
    }

    private void executeDm(String sql) throws Exception {
        try (Connection conn = dmConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
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

    private Connection dmConnection() throws Exception {
        DatasourceInfo dm = new DatasourceInfo();
        dm.setType("dm");
        dm.setUrl(DM_URL);
        dm.setUsername(DM_USER);
        dm.setPassword(DM_PASSWORD);
        dm.setDbschema(DM_SCHEMA);
        return DBUtils.getConnection(dm);
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
