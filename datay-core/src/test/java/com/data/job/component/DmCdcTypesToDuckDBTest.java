package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 达梦 CDC（DBMS_LOGMNR）常用字段类型同步到 DuckDB 的端到端测试。
 *
 * <p>覆盖整数、定点/浮点、字符、日期时间、BIT 与 NULL 的 INSERT/UPDATE/DELETE 同步。
 * 不满足达梦前置条件时自动跳过。
 */
public class DmCdcTypesToDuckDBTest {

    private static final String DM_URL = System.getProperty("dm.url", "jdbc:dm://127.0.0.1:5236");
    private static final String DM_USER = System.getProperty("dm.user", "DATAY");
    private static final String DM_PASSWORD = System.getProperty("dm.password", "Datay123456");
    private static final String DM_SCHEMA = System.getProperty("dm.schema", "DATAY");
    private static final String DUCK_URL = "jdbc:duckdb:./target/dm-cdc-types-duckdb-test.db";

    private static final String TABLE = "CDC_TYPES";

    @Test
    public void testDmCdcTypesToDuckDB() throws Exception {
        assumeTrue(supportsLogMiner(), "本地达梦未开启归档/逻辑日志或账号权限不足，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/dmCdcTypesToDuckDB.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            // 快照：启动前已存在的基线行
            awaitTrue(() -> duckTableExists() && queryDuckCount() == 1, 120000, "等待达梦 CDC 快照创建目标表超时");
            assertValues(1, "1", "baseline");

            // INSERT：覆盖所有常用类型
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
            awaitTrue(() -> queryDuckCount() == 2, 90000, "等待达梦 INSERT 多类型数据同步到 DuckDB 超时");
            assertTargetTypes();
            assertValues(2, "-5", "hello世界");
            assertEquals("1234", queryDuckString("select C_SMALLINT from main." + TABLE + " where ID = 2"));
            assertEquals("123456", queryDuckString("select C_INT from main." + TABLE + " where ID = 2"));
            assertEquals("9000000000", queryDuckString("select C_BIGINT from main." + TABLE + " where ID = 2"));
            assertEquals("12345.6789", queryDuckString("select C_DECIMAL from main." + TABLE + " where ID = 2"));
            assertEquals("98765.4321", queryDuckString("select C_NUMBER from main." + TABLE + " where ID = 2"));
            assertEquals(1.5d, Double.parseDouble(queryDuckString("select C_FLOAT from main." + TABLE + " where ID = 2")), 0.0001d);
            assertEquals(2.25d, Double.parseDouble(queryDuckString("select C_DOUBLE from main." + TABLE + " where ID = 2")), 0.0001d);
            assertEquals("abc", queryDuckString("select trim(C_CHAR) from main." + TABLE + " where ID = 2"));
            assertEquals("hello世界", queryDuckString("select C_VARCHAR from main." + TABLE + " where ID = 2"));
            assertEquals("varchar2", queryDuckString("select C_VARCHAR2 from main." + TABLE + " where ID = 2"));
            assertEquals("中文", queryDuckString("select C_NVARCHAR2 from main." + TABLE + " where ID = 2"));
            assertEquals("2024-01-01", queryDuckString("select cast(C_DATE as varchar) from main." + TABLE + " where ID = 2"));
            assertEquals("12:34:56", queryDuckString("select cast(C_TIME as varchar) from main." + TABLE + " where ID = 2"));
            assertEquals(
                "2024-01-01 10:00:00",
                queryDuckString("select strftime(C_DATETIME, '%Y-%m-%d %H:%M:%S') from main." + TABLE + " where ID = 2")
            );
            assertEquals(
                "2024-01-01 10:00:00.123456",
                queryDuckString("select cast(C_TIMESTAMP as varchar) from main." + TABLE + " where ID = 2")
            );
            assertEquals(true, queryDuckBoolean("select C_BIT from main." + TABLE + " where ID = 2"));

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
                () -> "updated值".equals(queryDuckString("select C_VARCHAR from main." + TABLE + " where ID = 2")),
                90000,
                "等待达梦 UPDATE 多类型数据同步到 DuckDB 超时"
            );
            assertEquals("-6", queryDuckString("select C_TINYINT from main." + TABLE + " where ID = 2"));
            assertEquals("2345", queryDuckString("select C_SMALLINT from main." + TABLE + " where ID = 2"));
            assertEquals("654321", queryDuckString("select C_INT from main." + TABLE + " where ID = 2"));
            assertEquals("8000000000", queryDuckString("select C_BIGINT from main." + TABLE + " where ID = 2"));
            assertEquals("99999.1234", queryDuckString("select C_DECIMAL from main." + TABLE + " where ID = 2"));
            assertEquals("11111.2222", queryDuckString("select C_NUMBER from main." + TABLE + " where ID = 2"));
            assertEquals(2.25d, Double.parseDouble(queryDuckString("select C_FLOAT from main." + TABLE + " where ID = 2")), 0.0001d);
            assertEquals(3.5d, Double.parseDouble(queryDuckString("select C_DOUBLE from main." + TABLE + " where ID = 2")), 0.0001d);
            assertEquals("xyz", queryDuckString("select trim(C_CHAR) from main." + TABLE + " where ID = 2"));
            assertEquals("v2", queryDuckString("select C_VARCHAR2 from main." + TABLE + " where ID = 2"));
            assertEquals("更新", queryDuckString("select C_NVARCHAR2 from main." + TABLE + " where ID = 2"));
            assertEquals("2025-01-02", queryDuckString("select cast(C_DATE as varchar) from main." + TABLE + " where ID = 2"));
            assertEquals("01:02:03", queryDuckString("select cast(C_TIME as varchar) from main." + TABLE + " where ID = 2"));
            assertEquals(
                "2025-01-02 01:02:03",
                queryDuckString("select strftime(C_DATETIME, '%Y-%m-%d %H:%M:%S') from main." + TABLE + " where ID = 2")
            );
            assertEquals(
                "2025-01-02 01:02:03.456789",
                queryDuckString("select cast(C_TIMESTAMP as varchar) from main." + TABLE + " where ID = 2")
            );
            assertEquals(false, queryDuckBoolean("select C_BIT from main." + TABLE + " where ID = 2"));

            // NULL：只插入非空列
            executeDm("INSERT INTO " + DM_SCHEMA + "." + TABLE + " (ID, C_VARCHAR) VALUES (3, 'only-string')");
            awaitTrue(() -> queryDuckCount() == 3, 90000, "等待达梦 NULL 行同步到 DuckDB 超时");
            assertNull(queryDuckString("select C_DECIMAL from main." + TABLE + " where ID = 3"));
            assertNull(queryDuckString("select C_DATE from main." + TABLE + " where ID = 3"));
            assertEquals("only-string", queryDuckString("select C_VARCHAR from main." + TABLE + " where ID = 3"));

            // DELETE
            executeDm("DELETE FROM " + DM_SCHEMA + "." + TABLE + " WHERE ID = 1");
            awaitTrue(() -> queryDuckCount() == 2, 90000, "等待达梦 DELETE 同步到 DuckDB 超时");
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
            throw new AssertionError("达梦 CDC 多类型同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void assertValues(int id, String tinyint, String varchar) {
        assertEquals(tinyint, queryDuckString("select C_TINYINT from main." + TABLE + " where ID = " + id));
        assertEquals(varchar, queryDuckString("select C_VARCHAR from main." + TABLE + " where ID = " + id));
    }

    private void assertTargetTypes() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ID", "INTEGER");
        expected.put("C_TINYINT", "TINYINT");
        expected.put("C_SMALLINT", "INTEGER");
        expected.put("C_INT", "INTEGER");
        expected.put("C_BIGINT", "BIGINT");
        expected.put("C_DECIMAL", "DECIMAL(12,4)");
        expected.put("C_NUMBER", "DECIMAL(12,4)");
        expected.put("C_FLOAT", "DOUBLE");
        expected.put("C_DOUBLE", "DOUBLE");
        expected.put("C_CHAR", "VARCHAR");
        expected.put("C_VARCHAR", "VARCHAR");
        expected.put("C_VARCHAR2", "VARCHAR");
        expected.put("C_NVARCHAR2", "VARCHAR");
        expected.put("C_DATE", "DATE");
        expected.put("C_TIME", "TIME");
        expected.put("C_DATETIME", "TIMESTAMP");
        expected.put("C_TIMESTAMP", "TIMESTAMP");
        expected.put("C_BIT", "BOOLEAN");

        try (
            Connection conn = duckConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "select column_name, data_type, numeric_precision, numeric_scale from information_schema.columns " +
                "where lower(table_name) = lower('" + TABLE + "') order by ordinal_position"
            )
        ) {
            Map<String, String> types = new LinkedHashMap<>();
            while (rs.next()) {
                types.put(rs.getString(1).toUpperCase(), rs.getString(2) + "(" + rs.getString(3) + "," + rs.getString(4) + ")");
            }
            System.out.println("[DM-TYPES] target types: " + types);
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                String actual = types.get(entry.getKey());
                assertTrue(
                    actual != null && actual.startsWith(entry.getValue() + "("),
                    "目标列 " + entry.getKey() + " 类型应为 " + entry.getValue() + "，实际为 " + actual
                );
            }
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
            st.execute(
                "INSERT INTO " +
                DM_SCHEMA +
                "." +
                TABLE +
                " (ID, C_TINYINT, C_VARCHAR) VALUES (1, 1, 'baseline')"
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

    private Boolean queryDuckBoolean(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getBoolean(1) : null;
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
            File file = new File("./target/dm-cdc-types-duckdb-test.db" + suffix);
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
