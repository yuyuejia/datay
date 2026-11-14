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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Oracle CDC（LogMiner）实时同步到 MySQL 的端到端测试。
 *
 * <p>重点验证：
 * <ul>
 *   <li>Oracle 大写表名/字段名写入 MySQL 时自动落到小写表名；</li>
 *   <li>NUMBER、VARCHAR2、CHAR、NVARCHAR2、BINARY_FLOAT、BINARY_DOUBLE、DATE、TIMESTAMP、RAW 等类型；</li>
 *   <li>快照、INSERT、UPDATE、NULL、DELETE。</li>
 * </ul>
 */
public class OracleCdcToMysqlTest {

    private static final String ORACLE_URL = System.getProperty("oracle.url", "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1");
    private static final String ORACLE_USER = System.getProperty("oracle.user", "datay");
    private static final String ORACLE_PASSWORD = System.getProperty("oracle.password", "datay123");
    private static final String ORACLE_SCHEMA = System.getProperty("oracle.schema", "DATAY");

    private static final String MYSQL_HOST = System.getProperty("mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("mysql.port", "3307");
    private static final String MYSQL_USER = System.getProperty("mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "password");
    private static final String MYSQL_DB = System.getProperty("mysql.database", "datay_oracle_cdc");

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

    private static final String TABLE = "CDC_ORACLE_MYSQL";
    private static final String MYSQL_TABLE = "cdc_oracle_mysql";

    @Test
    public void testOracleCdcToMysql() throws Exception {
        assumeTrue(supportsLogMiner(), "本地 Oracle 未开启 ARCHIVELOG/补充日志或 datay 账号权限不足，跳过测试");
        assumeTrue(mysqlReachable(), "目标 MySQL 不可用，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/oracleCdcToMysql.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            // 快照同步启动前已存在的基线行
            awaitTrue(() -> mysqlTableExists() && queryMysqlCount() == 1, 120000, "等待 Oracle CDC 快照创建 MySQL 目标表超时");
            assertEquals("baseline", queryMysqlString("select C_VARCHAR2 from " + MYSQL_TABLE + " where ID = 1"));

            // INSERT：覆盖常用 Oracle 类型
            executeOracle(
                "INSERT INTO " +
                ORACLE_SCHEMA +
                "." +
                TABLE +
                " (ID, C_NUMBER, C_INT, C_BIG, C_VARCHAR2, C_CHAR, C_NVARCHAR2, C_FLOAT, C_DOUBLE, C_DATE, C_TIMESTAMP, C_RAW) VALUES (" +
                "2, 123.4567, 12345, 123456789012345, 'hello', 'abc', 'chenjie', 1.5, 2.25, " +
                "DATE '2024-01-01', TIMESTAMP '2024-01-01 10:00:00', HEXTORAW('0102030405'))"
            );
            awaitTrue(() -> queryMysqlCount() == 2, 90000, "等待 Oracle INSERT 同步到 MySQL 超时");
            assertEquals("123.4567", queryMysqlString("select C_NUMBER from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("12345", queryMysqlString("select C_INT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("123456789012345", queryMysqlString("select C_BIG from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("hello", queryMysqlString("select C_VARCHAR2 from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("abc", queryMysqlString("select trim(C_CHAR) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("chenjie", queryMysqlString("select C_NVARCHAR2 from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(1.5d, Double.parseDouble(queryMysqlString("select C_FLOAT from " + MYSQL_TABLE + " where ID = 2")), 0.001d);
            assertEquals(2.25d, Double.parseDouble(queryMysqlString("select C_DOUBLE from " + MYSQL_TABLE + " where ID = 2")), 0.001d);
            assertEquals("2024-01-01", queryMysqlString("select cast(C_DATE as char) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(
                "2024-01-01 10:00:00",
                queryMysqlString("select date_format(C_TIMESTAMP, '%Y-%m-%d %H:%i:%s') from " + MYSQL_TABLE + " where ID = 2")
            );
            assertEquals("0102030405", queryMysqlString("select C_RAW from " + MYSQL_TABLE + " where ID = 2"));

            // UPDATE：数值、字符、日期时间
            executeOracle(
                "UPDATE " +
                ORACLE_SCHEMA +
                "." +
                TABLE +
                " SET C_NUMBER = 999.9999, C_INT = 54321, C_BIG = 800000000000000000, " +
                "C_VARCHAR2 = 'world', C_CHAR = 'xyz', C_NVARCHAR2 = '更新', C_FLOAT = 2.5, C_DOUBLE = 3.5, " +
                "C_DATE = DATE '2025-01-02', C_TIMESTAMP = TIMESTAMP '2025-01-02 01:02:03' WHERE ID = 2"
            );
            awaitTrue(
                () -> "world".equals(queryMysqlString("select C_VARCHAR2 from " + MYSQL_TABLE + " where ID = 2")),
                90000,
                "等待 Oracle UPDATE 同步到 MySQL 超时"
            );
            assertEquals("999.9999", queryMysqlString("select C_NUMBER from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("54321", queryMysqlString("select C_INT from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("800000000000000000", queryMysqlString("select C_BIG from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("xyz", queryMysqlString("select trim(C_CHAR) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals("更新", queryMysqlString("select C_NVARCHAR2 from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(2.5d, Double.parseDouble(queryMysqlString("select C_FLOAT from " + MYSQL_TABLE + " where ID = 2")), 0.001d);
            assertEquals(3.5d, Double.parseDouble(queryMysqlString("select C_DOUBLE from " + MYSQL_TABLE + " where ID = 2")), 0.001d);
            assertEquals("2025-01-02", queryMysqlString("select cast(C_DATE as char) from " + MYSQL_TABLE + " where ID = 2"));
            assertEquals(
                "2025-01-02 01:02:03",
                queryMysqlString("select date_format(C_TIMESTAMP, '%Y-%m-%d %H:%i:%s') from " + MYSQL_TABLE + " where ID = 2")
            );

            // NULL 行
            executeOracle("INSERT INTO " + ORACLE_SCHEMA + "." + TABLE + " (ID, C_VARCHAR2) VALUES (3, 'only-string')");
            awaitTrue(() -> queryMysqlCount() == 3, 90000, "等待 Oracle NULL 行同步到 MySQL 超时");
            assertNull(queryMysqlString("select C_NUMBER from " + MYSQL_TABLE + " where ID = 3"));
            assertNull(queryMysqlString("select C_DATE from " + MYSQL_TABLE + " where ID = 3"));
            assertEquals("only-string", queryMysqlString("select C_VARCHAR2 from " + MYSQL_TABLE + " where ID = 3"));

            // DELETE
            executeOracle("DELETE FROM " + ORACLE_SCHEMA + "." + TABLE + " WHERE ID = 1");
            awaitTrue(() -> queryMysqlCount() == 2, 90000, "等待 Oracle DELETE 同步到 MySQL 超时");

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
            throw new AssertionError("Oracle CDC 到 MySQL 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void printTargetTypes() throws Exception {
        try (
            Connection conn = mysqlConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "select COLUMN_NAME, DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE from information_schema.columns " +
                "where TABLE_SCHEMA = '" +
                MYSQL_DB +
                "' and TABLE_NAME = '" +
                MYSQL_TABLE +
                "' order by ORDINAL_POSITION"
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
            System.out.println("[ORACLE-MYSQL-TYPES] " + types);
        }
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
            st.execute("DROP TABLE IF EXISTS " + TABLE);
        }

        try (Connection conn = openOracle(); Statement st = conn.createStatement()) {
            try {
                st.execute("DROP TABLE " + ORACLE_SCHEMA + "." + TABLE);
            } catch (Exception ignored) {
            }
            st.execute(
                "CREATE TABLE " +
                ORACLE_SCHEMA +
                "." +
                TABLE +
                " (" +
                "ID NUMBER(10) PRIMARY KEY, " +
                "C_NUMBER NUMBER(12,4), " +
                "C_INT NUMBER(5), " +
                "C_BIG NUMBER(19), " +
                "C_VARCHAR2 VARCHAR2(100), " +
                "C_CHAR CHAR(10), " +
                "C_NVARCHAR2 NVARCHAR2(50), " +
                "C_FLOAT BINARY_FLOAT, " +
                "C_DOUBLE BINARY_DOUBLE, " +
                "C_DATE DATE, " +
                "C_TIMESTAMP TIMESTAMP, " +
                "C_RAW RAW(16))"
            );
            st.execute("INSERT INTO " + ORACLE_SCHEMA + "." + TABLE + " (ID, C_VARCHAR2) VALUES (1, 'baseline')");
        }
    }

    private void executeOracle(String sql) throws Exception {
        try (Connection conn = openOracle(); Statement st = conn.createStatement()) {
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

    private Connection openOracle() throws Exception {
        return DriverManager.getConnection(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD);
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
