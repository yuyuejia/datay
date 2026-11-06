package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySQL CDC → 达梦（DM）常用字段类型同步验证。
 *
 * <p>覆盖整数（含有符号/无符号）、浮点、定点、字符、文本、二进制、日期时间、
 * 布尔、BIT、JSON、枚举与集合，逐列对比源表与目标表的取值及目标字段类型。
 */
public class MysqlCdcDataTypesToDmTest {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String DM_URL = "jdbc:dm://127.0.0.1:5236";
    private static final String DM_USER = "SYSDBA";
    private static final String DM_PASSWORD = "Dameng123";
    private static final String DM_SCHEMA = "SYSDBA";
    private static final String TABLE = "cdc_dm_verify";

    /** 列: 名称, MySQL 读取表达式, DM 读取表达式 */
    private static final String[][] COLUMNS = {
        { "id", "cast(id as char)", "cast(id as varchar(40))" },
        { "c_tinyint", "cast(c_tinyint as char)", "cast(c_tinyint as varchar(40))" },
        { "c_smallint", "cast(c_smallint as char)", "cast(c_smallint as varchar(40))" },
        { "c_mediumint", "cast(c_mediumint as char)", "cast(c_mediumint as varchar(40))" },
        { "c_int", "cast(c_int as char)", "cast(c_int as varchar(40))" },
        { "c_bigint", "cast(c_bigint as char)", "cast(c_bigint as varchar(40))" },
        { "c_utinyint", "cast(c_utinyint as char)", "cast(c_utinyint as varchar(40))" },
        { "c_usmallint", "cast(c_usmallint as char)", "cast(c_usmallint as varchar(40))" },
        { "c_umediumint", "cast(c_umediumint as char)", "cast(c_umediumint as varchar(40))" },
        { "c_uint", "cast(c_uint as char)", "cast(c_uint as varchar(40))" },
        { "c_ubigint", "cast(c_ubigint as char)", "cast(c_ubigint as varchar(40))" },
        { "c_float", "cast(c_float as char)", "cast(c_float as varchar(40))" },
        { "c_double", "cast(c_double as char)", "cast(c_double as varchar(40))" },
        { "c_decimal", "cast(c_decimal as char)", "cast(c_decimal as varchar(40))" },
        { "c_char", "rtrim(c_char)", "rtrim(c_char)" },
        { "c_varchar", "c_varchar", "c_varchar" },
        { "c_text", "c_text", "c_text" },
        { "c_binary", "c_binary", "c_binary" },
        { "c_varbinary", "c_varbinary", "c_varbinary" },
        { "c_blob", "c_blob", "c_blob" },
        { "c_date", "cast(c_date as char)", "to_char(c_date, 'YYYY-MM-DD')" },
        { "c_time", "cast(c_time as char)", "to_char(c_time, 'HH24:MI:SS.FF6')" },
        { "c_datetime", "cast(c_datetime as char)", "to_char(c_datetime, 'YYYY-MM-DD HH24:MI:SS.FF6')" },
        { "c_timestamp", "cast(c_timestamp as char)", "to_char(c_timestamp, 'YYYY-MM-DD HH24:MI:SS.FF6')" },
        { "c_year", "cast(c_year as char)", "cast(c_year as varchar(40))" },
        { "c_bool", "cast(c_bool as char)", "case when c_bool then '1' else '0' end" },
        { "c_bit1", "cast(c_bit1+0 as char)", "case when c_bit1 then '1' else '0' end" },
        { "c_bit8", "cast(c_bit8+0 as char)", "cast(c_bit8 as varchar(40))" },
        { "c_json", "cast(c_json as char)", "c_json" },
        { "c_enum", "c_enum", "c_enum" },
        { "c_set", "c_set", "c_set" },
    };

    @Test
    public void testMysqlCdcDataTypesToDm() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        assumeTrue(dmReachable(), "达梦数据库不可用，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/mysqlCdcTypesToDm.json")));

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
                "1, -5, 1234, 8388607, 123456, 9000000000, 200, 60000, 16000000, 4000000000, 18000000000000000000, " +
                "1.5, 3.1415926535, 12345.678901, " +
                "'abc', 'hello世界', 'text-value', UNHEX('DEADBEEF00000000'), UNHEX('CAFEBABE1234'), UNHEX('CAFEBABE00FF'), " +
                "'2024-03-15', '12:34:56.789', '2024-03-15 12:34:56.789', '2024-03-15 12:34:56.789', 2024, 1, b'1', b'10101010', " +
                "'{\"a\":1,\"b\":\"中文\"}', 'b', 'x,z')"
            );
            awaitTrue(() -> targetRowCount() == 1, 45000, "等待 INSERT 同步到达梦超时");
            assertRowEquals("INSERT");
            assertTargetTypes();

            executeMysql(
                "UPDATE test." + TABLE + " SET " +
                "c_tinyint=-6, c_smallint=2345, c_mediumint=654321, c_int=654321, c_bigint=8000000000, " +
                "c_utinyint=255, c_usmallint=65535, c_umediumint=16777215, c_uint=4294967295, " +
                "c_ubigint=18446744073709551615, " +
                "c_float=2.25, c_double=2.7182818284, c_decimal=99999.123456, " +
                "c_char='xyz', c_varchar='updated值', c_text='text-updated', " +
                "c_binary=UNHEX('0102030405060708'), c_varbinary=UNHEX('0102'), c_blob=UNHEX('AABBCC'), " +
                "c_date='2025-01-02', c_time='01:02:03.004', c_datetime='2025-01-02 01:02:03.004', " +
                "c_timestamp='2025-01-02 01:02:03.004', c_year=2025, c_bool=0, c_bit1=b'0', c_bit8=b'00001111', " +
                "c_json='{\"a\":2,\"b\":\"更新\"}', c_enum='c', c_set='y' WHERE id=1"
            );
            awaitTrue(() -> "updated值".equals(targetColumnString("c_varchar")), 30000, "等待 UPDATE 同步到达梦超时");
            assertRowEquals("UPDATE");
        } finally {
            runner.cancel();
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
            DBUtils.closePool(DM_URL, null);
            DBUtils.closePool(MYSQL_URL, null);
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("CDC 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void assertRowEquals(String phase) throws Exception {
        Map<String, String> source = readRow(openMysql(), true);
        Map<String, String> target = readRow(dmConnection(), false);
        for (String[] column : COLUMNS) {
            if ("c_json".equals(column[0])) {
                assertEquals(
                    com.alibaba.fastjson2.JSON.parseObject(source.get(column[0])),
                    com.alibaba.fastjson2.JSON.parseObject(target.get(column[0])),
                    phase + " 列 [" + column[0] + "] 同步不一致"
                );
                continue;
            }
            assertEquals(source.get(column[0]), target.get(column[0]), phase + " 列 [" + column[0] + "] 同步不一致");
        }
    }

    private Map<String, String> readRow(Connection conn, boolean mysql) throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(mysql ? COLUMNS[i][1] : COLUMNS[i][2]).append(" AS c").append(i);
        }
        sql.append(" FROM ").append(mysql ? "test." + TABLE : DM_SCHEMA + "." + TABLE).append(" WHERE id = 1");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql.toString())) {
            if (rs.next()) {
                for (int i = 0; i < COLUMNS.length; i++) {
                    String name = COLUMNS[i][0];
                    if ("c_binary".equals(name) || "c_varbinary".equals(name) || "c_blob".equals(name)) {
                        byte[] bytes = rs.getBytes("c" + i);
                        row.put(name, bytes == null ? null : hex(bytes));
                    } else {
                        row.put(name, normalize(rs.getString("c" + i)));
                    }
                }
            }
        }
        return row;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("-?\\d+")) {
            return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?[eE][+-]?\\d+")) {
            return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
        }
        if (trimmed.matches("\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            return trimFraction(trimmed);
        }
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            return trimFraction(trimmed);
        }
        return trimmed;
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private String trimFraction(String value) {
        int dot = value.indexOf('.');
        if (dot < 0) {
            return value;
        }
        String base = value.substring(0, dot);
        String fraction = value.substring(dot + 1).replaceAll("0+$", "");
        return fraction.isEmpty() ? base : base + "." + fraction;
    }

    private int targetRowCount() {
        try (Connection conn = dmConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from " + DM_SCHEMA + "." + TABLE)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private String targetColumnString(String column) {
        try (Connection conn = dmConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select " + column + " from " + DM_SCHEMA + "." + TABLE + " where id = 1")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void assertTargetTypes() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ID", "BIGINT");
        expected.put("C_TINYINT", "TINYINT");
        expected.put("C_SMALLINT", "SMALLINT");
        expected.put("C_MEDIUMINT", "INT");
        expected.put("C_INT", "INT");
        expected.put("C_BIGINT", "BIGINT");
        expected.put("C_UTINYINT", "SMALLINT");
        expected.put("C_USMALLINT", "INT");
        expected.put("C_UMEDIUMINT", "INT");
        expected.put("C_UINT", "BIGINT");
        expected.put("C_UBIGINT", "NUMBER");
        expected.put("C_FLOAT", "FLOAT");
        expected.put("C_DOUBLE", "DOUBLE");
        expected.put("C_DECIMAL", "NUMBER");
        expected.put("C_CHAR", "CHAR");
        expected.put("C_VARCHAR", "VARCHAR");
        expected.put("C_TEXT", "CLOB");
        expected.put("C_BINARY", "VARBINARY");
        expected.put("C_VARBINARY", "VARBINARY");
        expected.put("C_BLOB", "BLOB");
        expected.put("C_DATE", "DATE");
        expected.put("C_TIME", "TIME");
        expected.put("C_DATETIME", "TIMESTAMP");
        expected.put("C_TIMESTAMP", "TIMESTAMP");
        expected.put("C_YEAR", "INT");
        expected.put("C_BOOL", "TINYINT");
        expected.put("C_BIT1", "BIT");
        expected.put("C_BIT8", "SMALLINT");
        expected.put("C_JSON", "CLOB");
        expected.put("C_ENUM", "VARCHAR");
        expected.put("C_SET", "VARCHAR");
        try (Connection conn = dmConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select COLUMN_NAME, DATA_TYPE from ALL_TAB_COLUMNS where OWNER = '" + DM_SCHEMA +
                 "' and TABLE_NAME = '" + TABLE.toUpperCase() + "'")) {
            Map<String, String> actual = new LinkedHashMap<>();
            while (rs.next()) {
                actual.put(rs.getString(1).toUpperCase(), rs.getString(2).toUpperCase());
            }
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                String actualType = actual.get(entry.getKey());
                assertTrue(actualType != null && actualType.startsWith(entry.getValue()),
                    "目标列 " + entry.getKey() + " 类型应为 " + entry.getValue() + "，实际为 " + actualType);
            }
        }
    }

    private void resetEnvironment() throws Exception {
        DBUtils.closePool(DM_URL, null);
        DBUtils.closePool(MYSQL_URL, null);
        java.io.File statusFile = new java.io.File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + TABLE);
            st.execute(
                "CREATE TABLE test." + TABLE + " (" +
                "id BIGINT PRIMARY KEY, c_tinyint TINYINT, c_smallint SMALLINT, c_mediumint MEDIUMINT, " +
                "c_int INT, c_bigint BIGINT, c_utinyint TINYINT UNSIGNED, c_usmallint SMALLINT UNSIGNED, " +
                "c_umediumint MEDIUMINT UNSIGNED, c_uint INT UNSIGNED, c_ubigint BIGINT UNSIGNED, " +
                "c_float FLOAT, c_double DOUBLE, c_decimal DECIMAL(20,6), " +
                "c_char CHAR(10), c_varchar VARCHAR(100), c_text TEXT, " +
                "c_binary BINARY(8), c_varbinary VARBINARY(32), c_blob BLOB, " +
                "c_date DATE, c_time TIME(3), c_datetime DATETIME(3), c_timestamp TIMESTAMP(3) NULL, " +
                "c_year YEAR, c_bool TINYINT(1), c_bit1 BIT(1), c_bit8 BIT(8), " +
                "c_json JSON, c_enum ENUM('a','b','c'), c_set SET('x','y','z'))"
            );
        }
        try (Connection conn = dmConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + DM_SCHEMA + "." + TABLE);
        }
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

    private boolean dmReachable() {
        try (Connection conn = dmConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
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

    private Connection dmConnection() throws Exception {
        DatasourceInfo dm = new DatasourceInfo();
        dm.setType("dm");
        dm.setUrl(DM_URL);
        dm.setUsername(DM_USER);
        dm.setPassword(DM_PASSWORD);
        dm.setDbschema(DM_SCHEMA);
        return DBUtils.getConnection(dm);
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
}
