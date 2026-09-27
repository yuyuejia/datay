package com.data.job.component;

import com.data.job.DuckDBEngine;
import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * MySQL CDC → DuckLake 全字段类型同步验证。
 */
public class MysqlCdcDataTypesToDuckLakeTest {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String TABLE = "cdc_types";
    private static final String DUCKLAKE_META = "./target/cdc-mysql-types.ducklake";
    private static final String DUCKLAKE_DATA_DIR = "./target/cdc-mysql-types-ducklake-data";

    private static final String[][] COLUMNS = {
        { "id", "id", "id" },
        { "c_tinyint", "cast(c_tinyint as char)", "cast(c_tinyint as varchar)" },
        { "c_smallint", "cast(c_smallint as char)", "cast(c_smallint as varchar)" },
        { "c_int", "cast(c_int as char)", "cast(c_int as varchar)" },
        { "c_bigint", "cast(c_bigint as char)", "cast(c_bigint as varchar)" },
        { "c_float", "cast(c_float as char)", "cast(c_float as varchar)" },
        { "c_double", "cast(c_double as char)", "cast(c_double as varchar)" },
        { "c_decimal", "cast(c_decimal as char)", "cast(c_decimal as varchar)" },
        { "c_char", "c_char", "c_char" },
        { "c_varchar", "c_varchar", "c_varchar" },
        { "c_text", "c_text", "c_text" },
        { "c_binary", "hex(c_binary)", "hex(c_binary)" },
        { "c_blob", "hex(c_blob)", "hex(c_blob)" },
        { "c_date", "cast(c_date as char)", "cast(c_date as varchar)" },
        { "c_time", "cast(c_time as char)", "cast(c_time as varchar)" },
        { "c_datetime", "cast(c_datetime as char)", "cast(c_datetime as varchar)" },
        { "c_timestamp", "cast(c_timestamp as char)", "cast(c_timestamp as varchar)" },
        { "c_bool", "cast(c_bool as char)", "cast(c_bool as varchar)" },
        { "c_bit", "cast(c_bit+0 as char)", "cast(c_bit as varchar)" },
    };

    @Test
    public void testMysqlCdcDataTypesToDuckLake() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/mysqlCdcTypesToDuckLake.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时");

            executeMysql(
                "INSERT INTO test." + TABLE + " VALUES (" +
                "1, -5, 1234, 123456, 9000000000, 1.5, 3.1415926535, 12345.678901, " +
                "'abc', 'hello世界', 'text-value', UNHEX('DEADBEEF'), UNHEX('CAFEBABE1234'), " +
                "'2024-03-15', '12:34:56', '2024-03-15 12:34:56', '2024-03-15 12:34:56', 1, b'1')"
            );
            awaitTrue(() -> targetRowCount() == 1, 30000, "等待 INSERT 同步到 DuckLake 超时");
            assertRowEquals("INSERT");
            assertTargetTypes();

            executeMysql(
                "UPDATE test." + TABLE + " SET " +
                "c_tinyint=-6, c_smallint=2345, c_int=654321, c_bigint=8000000000, " +
                "c_float=2.25, c_double=2.7182818284, c_decimal=99999.123456, " +
                "c_char='xyz', c_varchar='updated值', c_text='text-updated', " +
                "c_binary=UNHEX('0102'), c_blob=UNHEX('AABBCC'), " +
                "c_date='2025-01-02', c_time='01:02:03', c_datetime='2025-01-02 01:02:03', " +
                "c_timestamp='2025-01-02 01:02:03', c_bool=0, c_bit=b'0' WHERE id=1"
            );
            awaitTrue(() -> "654321".equals(targetColumnString("c_int")), 30000, "等待 UPDATE 同步到 DuckLake 超时");
            assertRowEquals("UPDATE");
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
            throw new AssertionError("CDC 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void assertRowEquals(String phase) throws Exception {
        Map<String, String> source = readRow(openMysql(), TABLE, true);
        Map<String, String> target = readRow(duckConnection(), TABLE, false);
        for (String[] column : COLUMNS) {
            assertEquals(source.get(column[0]), target.get(column[0]), phase + " 列 [" + column[0] + "] 同步不一致");
        }
    }

    private Map<String, String> readRow(Connection conn, String table, boolean mysql) throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(mysql ? COLUMNS[i][1] : COLUMNS[i][2]).append(" AS c").append(i);
        }
        sql.append(" FROM ").append(mysql ? "test." + table : "ducklake.main." + table).append(" WHERE id = 1");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql.toString())) {
            if (rs.next()) {
                for (int i = 0; i < COLUMNS.length; i++) {
                    row.put(COLUMNS[i][0], rs.getString("c" + i));
                }
            }
        }
        return row;
    }

    private int targetRowCount() {
        return queryInt("select count(*) from ducklake.main." + TABLE);
    }

    private void assertTargetTypes() throws Exception {
        assertTargetType("c_tinyint", "TINYINT");
        assertTargetType("c_int", "INTEGER");
        assertTargetType("c_bigint", "BIGINT");
        assertTargetType("c_double", "DOUBLE");
        assertTargetType("c_decimal", "DECIMAL");
        assertTargetType("c_binary", "BLOB");
        assertTargetType("c_date", "DATE");
        assertTargetType("c_time", "TIME");
        assertTargetType("c_datetime", "TIMESTAMP");
        assertTargetType("c_bool", "TINYINT");
    }

    private void assertTargetType(String column, String expectedPrefix) throws Exception {
        String actual;
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
            "select data_type from information_schema.columns where table_schema = 'main' and table_name = '" + TABLE + "' and lower(column_name) = lower('" + column + "')"
        )) {
            actual = rs.next() ? rs.getString(1) : null;
        }
        assertTrue(actual != null && actual.toUpperCase().startsWith(expectedPrefix), "目标列 " + column + " 类型应为 " + expectedPrefix + "，实际为 " + actual);
    }

    private String targetColumnString(String column) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select " + column + " from ducklake.main." + TABLE + " where id = 1")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int queryInt(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private Connection duckConnection() throws Exception {
        return DuckDBEngine.getInstance().getConnection();
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
        deleteDuckLakeFiles();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + TABLE);
            st.execute(
                "CREATE TABLE test." + TABLE + " (" +
                "id BIGINT PRIMARY KEY, c_tinyint TINYINT, c_smallint SMALLINT, c_int INT, c_bigint BIGINT, " +
                "c_float FLOAT, c_double DOUBLE, c_decimal DECIMAL(20,6), " +
                "c_char CHAR(10), c_varchar VARCHAR(100), c_text TEXT, " +
                "c_binary VARBINARY(32), c_blob BLOB, " +
                "c_date DATE, c_time TIME, c_datetime DATETIME, c_timestamp TIMESTAMP NULL, " +
                "c_bool TINYINT(1), c_bit BIT(1))"
            );
        }
    }

    private void deleteDuckLakeFiles() throws Exception {
        detachDuckLake();
        new File("./target/cdc-mysql-types.ducklake.wal").delete();
        new File(DUCKLAKE_META).delete();
        Path dataDir = java.nio.file.Paths.get(DUCKLAKE_DATA_DIR);
        if (Files.exists(dataDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(dataDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectories(dataDir);
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

    private void executeMysql(String sql) throws Exception {
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private Connection openMysql() throws Exception {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
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
    private void detachDuckLake() {
        try (Connection conn = DuckDBEngine.getInstance().getConnection(); Statement st = conn.createStatement()) {
            st.execute("DETACH ducklake");
        } catch (Exception ignored) {
        }
    }
}
