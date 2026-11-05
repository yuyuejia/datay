package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * MySQL CDC → PostgreSQL 端到端验证。
 *
 * <p>覆盖事件类型：INSERT（单条与批量合并）、UPDATE、DELETE、DDL（ADD COLUMN），
 * 以及全字段类型同步（整数/浮点/定点/字符/文本/二进制/日期时间/布尔/位/JSON/枚举/集合/年份）。
 *
 * <p>前置条件（本机验证环境）：
 * <ul>
 *   <li>MySQL 127.0.0.1:3306，root/1234qwer，{@code log_bin=ON}、{@code binlog_format=ROW}；</li>
 *   <li>PostgreSQL 127.0.0.1:15432/datay_test，postgres/Datay@12345，schema {@code test} 可创建。</li>
 * </ul>
 * 任一环境不可用时自动跳过。
 */
public class MysqlCdcToPostgresTest {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:15432/datay_test";
    private static final String PG_USER = "postgres";
    private static final String PG_PASSWORD = "Datay@12345";
    private static final String PG_SCHEMA = "test";
    private static final String TABLE = "cdc_pg_verify";

    /**
     * 列定义：{逻辑列名, MySQL 读取表达式, PostgreSQL 读取表达式}。
     * 两侧表达式都归一化为可直接比较的字符串。
     */
    private static final String[][] COLUMNS = {
        { "id", "cast(id as char)", "id::text" },
        { "c_tinyint", "cast(c_tinyint as char)", "c_tinyint::text" },
        { "c_smallint", "cast(c_smallint as char)", "c_smallint::text" },
        { "c_mediumint", "cast(c_mediumint as char)", "c_mediumint::text" },
        { "c_int", "cast(c_int as char)", "c_int::text" },
        { "c_bigint", "cast(c_bigint as char)", "c_bigint::text" },
        { "c_utinyint", "cast(c_utinyint as char)", "c_utinyint::text" },
        { "c_usmallint", "cast(c_usmallint as char)", "c_usmallint::text" },
        { "c_umediumint", "cast(c_umediumint as char)", "c_umediumint::text" },
        { "c_uint", "cast(c_uint as char)", "c_uint::text" },
        { "c_ubigint", "cast(c_ubigint as char)", "c_ubigint::text" },
        { "c_float", "cast(c_float as char)", "c_float::text" },
        { "c_double", "cast(c_double as char)", "c_double::text" },
        { "c_decimal", "cast(c_decimal as char)", "c_decimal::text" },
        { "c_char", "c_char", "c_char::text" },
        { "c_varchar", "c_varchar", "c_varchar::text" },
        { "c_text", "c_text", "c_text::text" },
        { "c_binary", "hex(c_binary)", "upper(encode(c_binary, 'hex'))" },
        { "c_varbinary", "hex(c_varbinary)", "upper(encode(c_varbinary, 'hex'))" },
        { "c_blob", "hex(c_blob)", "upper(encode(c_blob, 'hex'))" },
        { "c_date", "cast(c_date as char)", "c_date::text" },
        { "c_time", "cast(c_time as char)", "c_time::text" },
        { "c_datetime", "cast(c_datetime as char)", "c_datetime::text" },
        { "c_timestamp", "cast(c_timestamp as char)", "c_timestamp::text" },
        { "c_bool", "cast(c_bool as char)", "c_bool::text" },
        { "c_bit1", "cast(c_bit1+0 as char)", "case when c_bit1 is null then null when c_bit1::text in ('1','true','t') then '1' else '0' end" },
        { "c_bit8", "lpad(bin(c_bit8+0), 8, '0')", "c_bit8::text" },
        { "c_json_a", "json_unquote(json_extract(c_json, '$.a'))", "c_json->>'a'" },
        { "c_json_b", "json_unquote(json_extract(c_json, '$.b'))", "c_json->>'b'" },
        { "c_enum", "c_enum", "c_enum::text" },
        { "c_set", "c_set", "c_set::text" },
        { "c_year", "cast(c_year as char)", "c_year::text" },
    };

    @Test
    public void testMysqlCdcToPostgres() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(postgresReachable(), "PostgreSQL 不可用，跳过测试");
        resetEnvironment();

        JobHandle job = startJob("mysqlCdcToPostgres.json");
        AtomicReference<Throwable> jobError = job.error;

        List<String> mismatches = new ArrayList<>();
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时", jobError);

            // ---------- INSERT：全字段类型 ----------
            executeMysql(
                "INSERT INTO test." + TABLE + " VALUES (" +
                "1, -5, 1234, 8388607, 123456, 9000000000, 200, 60000, 16000000, 4000000000, 18000000000000000000, " +
                "1.5, 3.1415926535, 12345.678901, " +
                "'abc', 'hello世界', 'text-value', UNHEX('DEADBEEF00000000'), UNHEX('CAFEBABE1234'), UNHEX('CAFEBABE00FF'), " +
                "'2024-03-15', '12:34:56.789', '2024-03-15 12:34:56.789', '2024-03-15 12:34:56.789', 1, b'1', b'10101010', " +
                "'{\"a\":1,\"b\":\"中文\"}', 'b', 'x,z', 2024)"
            );
            awaitTrue(() -> targetRowCount() == 1, 45000, "等待 INSERT 同步到 PostgreSQL 超时", jobError);
            collectMismatches("INSERT", mismatches);
            checkTargetTypes(mismatches);

            // ---------- INSERT：批量合并 ----------
            executeMysql("INSERT INTO test." + TABLE + " (id, c_varchar, c_int) VALUES (2, 'batch-2', 2), (3, 'batch-3', 3)");
            awaitTrue(() -> targetRowCount() == 3, 30000, "等待批量 INSERT 同步到 PostgreSQL 超时", jobError);
            collectMismatches("BATCH INSERT", mismatches);

            // ---------- UPDATE ----------
            executeMysql(
                "UPDATE test." + TABLE + " SET " +
                "c_tinyint=-6, c_smallint=2345, c_mediumint=654321, c_int=654321, c_bigint=8000000000, " +
                "c_utinyint=255, c_usmallint=65535, c_umediumint=16777215, c_uint=4294967295, " +
                "c_ubigint=18446744073709551615, " +
                "c_float=2.25, c_double=2.7182818284, c_decimal=99999.123456, " +
                "c_char='xyz', c_varchar='updated值', c_text='text-updated', " +
                "c_binary=UNHEX('0102030405060708'), c_varbinary=UNHEX('0102'), c_blob=UNHEX('AABBCC'), " +
                "c_date='2025-01-02', c_time='01:02:03.004', c_datetime='2025-01-02 01:02:03.004', " +
                "c_timestamp='2025-01-02 01:02:03.004', c_bool=0, c_bit1=b'0', c_bit8=b'00001111', " +
                "c_json='{\"a\":2,\"b\":\"更新\"}', c_enum='c', c_set='y', c_year=2025 " +
                "WHERE id=1"
            );
            awaitTrue(() -> "updated值".equals(targetColumnString("c_varchar", 1)), 30000, "等待 UPDATE 同步到 PostgreSQL 超时", jobError);
            collectMismatches("UPDATE", mismatches);

            // ---------- DELETE ----------
            executeMysql("DELETE FROM test." + TABLE + " WHERE id=3");
            awaitTrue(() -> targetRowCount() == 2, 30000, "等待 DELETE 同步到 PostgreSQL 超时", jobError);
            collectMismatches("DELETE", mismatches);

            // ---------- DDL：ADD COLUMN ----------
            executeMysql("ALTER TABLE test." + TABLE + " ADD COLUMN c_added VARCHAR(20) DEFAULT 'ddl-default'");
            awaitTrue(() -> targetHasColumn("c_added"), 30000, "等待 ADD COLUMN DDL 同步到 PostgreSQL 超时", jobError);
            executeMysql("UPDATE test." + TABLE + " SET c_added='row1-added' WHERE id=1");
            executeMysql("INSERT INTO test." + TABLE + " (id, c_varchar, c_int, c_added) VALUES (4, 'ddl-4', 4, 'row4-added')");
            awaitTrue(() -> targetRowCount() == 3, 30000, "等待 DDL 后 INSERT 同步到 PostgreSQL 超时", jobError);
            assertEquals("row4-added", targetColumnString("c_added", 4), "DDL 新增列的写入值未同步到 PostgreSQL");
            assertEquals("row1-added", targetColumnString("c_added", 1), "DDL 新增列的 UPDATE 值未同步到 PostgreSQL");
            collectMismatches("DDL + INSERT/UPDATE", mismatches);

            // ---------- UPDATE：主键变更 ----------
            executeMysql("UPDATE test." + TABLE + " SET id=10 WHERE id=2");
            awaitTrue(
                () -> "batch-2".equals(targetColumnString("c_varchar", 10)) && targetColumnString("c_varchar", 2) == null,
                30000,
                "等待主键变更的 UPDATE 同步到 PostgreSQL 超时",
                jobError
            );
            collectMismatches("UPDATE 主键变更", mismatches);

            // ---------- DDL：TRUNCATE ----------
            executeMysql("TRUNCATE TABLE test." + TABLE);
            awaitTrue(() -> targetRowCount() == 0, 30000, "等待 TRUNCATE 同步到 PostgreSQL 超时", jobError);

            // 留一条数据，便于测试结束后直接查询目标表核对
            executeMysql("INSERT INTO test." + TABLE + " (id, c_varchar, c_int) VALUES (99, 'final-evidence', 99)");
            awaitTrue(() -> "final-evidence".equals(targetColumnString("c_varchar", 99)),
                30000, "等待 TRUNCATE 后的增量 INSERT 同步到 PostgreSQL 超时", jobError);
            collectMismatches("TRUNCATE + INSERT", mismatches);
        } finally {
            stopJob(job);
        }

        assertJobSucceeded(job);
        assertTrue(mismatches.isEmpty(), "MySQL CDC → PostgreSQL 校验失败:\n - " + String.join("\n - ", mismatches));
    }

    /**
     * 目标表已存在时重启任务（不经过自动建表，直接读取 PostgreSQL 元数据绑定参数）。
     */
    @Test
    public void testRestartWithExistingTargetTable() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(postgresReachable(), "PostgreSQL 不可用，跳过测试");
        resetEnvironment();

        List<String> mismatches = new ArrayList<>();
        JobHandle first = startJob("mysqlCdcToPostgres.json");
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时", first.error);
            executeMysql(
                "INSERT INTO test." + TABLE + " VALUES (" +
                "1, -5, 1234, 8388607, 123456, 9000000000, 200, 60000, 16000000, 4000000000, 18000000000000000000, " +
                "1.5, 3.1415926535, 12345.678901, " +
                "'abc', 'hello世界', 'text-value', UNHEX('DEADBEEF00000000'), UNHEX('CAFEBABE1234'), UNHEX('CAFEBABE00FF'), " +
                "'2024-03-15', '12:34:56.789', '2024-03-15 12:34:56.789', '2024-03-15 12:34:56.789', 1, b'1', b'10101010', " +
                "'{\"a\":1,\"b\":\"中文\"}', 'b', 'x,z', 2024)"
            );
            awaitTrue(() -> targetRowCount() == 1, 45000, "等待首次 INSERT 同步到 PostgreSQL 超时", first.error);
            collectMismatches("首次 INSERT", mismatches);
        } finally {
            stopJobWithoutClosingPools(first);
        }

        // 重启：目标表已存在，后续写入走「读取目标元数据」路径
        JobHandle second = startJob("mysqlCdcToPostgres.json");
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待重启后的 MySQL Binlog 采集器就绪超时", second.error);
            executeMysql("INSERT INTO test." + TABLE + " (id, c_varchar, c_int, c_bit1, c_json) VALUES (2, 'restart-2', 2, b'1', '{\"a\":9,\"b\":\"重启\"}')");
            executeMysql("UPDATE test." + TABLE + " SET c_varchar='restart-updated', c_char='zzz', c_bit1=b'0' WHERE id=1");
            awaitTrue(() -> targetRowCount() == 2 && "restart-updated".equals(targetColumnString("c_varchar", 1)),
                30000, "等待重启后的 INSERT/UPDATE 同步到 PostgreSQL 超时", second.error);
            collectMismatches("重启后 INSERT/UPDATE", mismatches);
        } finally {
            stopJobWithoutClosingPools(second);
        }

        DBUtils.closePool(PG_URL, null);
        DBUtils.closePool(MYSQL_URL, null);
        assertJobSucceeded(first);
        assertJobSucceeded(second);
        assertTrue(mismatches.isEmpty(), "目标表已存在时同步校验失败:\n - " + String.join("\n - ", mismatches));
    }

    /**
     * snapshot=true：先全量导入存量数据，再继续消费增量。
     */
    @Test
    public void testSnapshotThenIncremental() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(postgresReachable(), "PostgreSQL 不可用，跳过测试");
        resetEnvironment();

        // 启动前先写入存量数据，验证 snapshot 全量读取
        executeMysql(
            "INSERT INTO test." + TABLE +
            " (id, c_varchar, c_int, c_decimal, c_date, c_bit1, c_bit8, c_binary, c_json, c_enum, c_set, c_year) VALUES " +
            "(1, 'snapshot-1', 1, 10.500000, '2024-01-01', b'1', b'10101010', UNHEX('DEADBEEF00000000'), '{\"a\":1}', 'a', 'x', 2020), " +
            "(2, 'snapshot-2', 2, 20.250000, '2024-01-02', b'0', b'00001111', UNHEX('0102030405060708'), '{\"a\":2}', 'b', 'y', 2021)"
        );

        List<String> mismatches = new ArrayList<>();
        JobHandle job = startJob("mysqlCdcToPostgresSnapshot.json");
        try {
            awaitTrue(() -> targetRowCount() == 2, 45000, "等待 snapshot 全量数据导入 PostgreSQL 超时", job.error);
            collectMismatches("SNAPSHOT", mismatches);

            // snapshot 完成后继续验证增量
            executeMysql("INSERT INTO test." + TABLE + " (id, c_varchar, c_int) VALUES (3, 'incremental-3', 3)");
            executeMysql("UPDATE test." + TABLE + " SET c_varchar='snapshot-updated' WHERE id=1");
            awaitTrue(() -> targetRowCount() == 3 && "snapshot-updated".equals(targetColumnString("c_varchar", 1)),
                30000, "等待 snapshot 后的增量同步到 PostgreSQL 超时", job.error);
            collectMismatches("SNAPSHOT + 增量", mismatches);
        } finally {
            stopJob(job);
        }

        assertJobSucceeded(job);
        assertTrue(mismatches.isEmpty(), "snapshot 同步校验失败:\n - " + String.join("\n - ", mismatches));
    }

    private static class JobHandle {

        private final ETLFlowTask runner = new ETLFlowTask();
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private Future<?> future;
    }

    private JobHandle startJob(String configFile) throws Exception {
        JobHandle handle = new JobHandle();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test", configFile)));
        handle.future = handle.executor.submit(() -> {
            try {
                handle.runner.runJob(job);
            } catch (Throwable e) {
                handle.error.set(e);
            }
        });
        return handle;
    }

    private void stopJob(JobHandle handle) {
        stopJobWithoutClosingPools(handle);
        DBUtils.closePool(PG_URL, null);
        DBUtils.closePool(MYSQL_URL, null);
    }

    private void stopJobWithoutClosingPools(JobHandle handle) {
        handle.runner.cancel();
        try {
            if (handle.future != null) {
                handle.future.get(15, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
        }
        handle.executor.shutdownNow();
    }

    private void assertJobSucceeded(JobHandle handle) {
        Throwable error = handle.error.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("CDC 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void collectMismatches(String phase, List<String> mismatches) {
        try {
            Map<Long, Map<String, String>> source = readRows(mysqlConnection(), true);
            Map<Long, Map<String, String>> target;
            try {
                target = readRows(pgConnection(), false);
            } catch (Exception e) {
                mismatches.add(phase + " 读取目标表失败: " + e.getMessage());
                return;
            }
            if (!source.keySet().equals(target.keySet())) {
                mismatches.add(phase + " 主键集合不一致 source=" + source.keySet() + " target=" + target.keySet());
            }
            for (Map.Entry<Long, Map<String, String>> entry : source.entrySet()) {
                Map<String, String> expected = entry.getValue();
                Map<String, String> actual = target.get(entry.getKey());
                if (actual == null) {
                    continue;
                }
                for (String[] column : COLUMNS) {
                    String e = expected.get(column[0]);
                    String a = actual.get(column[0]);
                    if (e == null ? a != null : !e.equals(a)) {
                        mismatches.add(phase + " id=" + entry.getKey() + " 列 [" + column[0] + "] source=" + e + " target=" + a);
                    }
                }
            }
        } catch (Exception e) {
            mismatches.add(phase + " 校验异常: " + e.getMessage());
        }
    }

    private Map<Long, Map<String, String>> readRows(Connection conn, boolean mysql) throws Exception {
        Map<Long, Map<String, String>> rows = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(mysql ? COLUMNS[i][1] : COLUMNS[i][2]).append(" AS c").append(i);
        }
        sql.append(" FROM ").append(mysql ? "test." + TABLE : PG_SCHEMA + "." + TABLE).append(" ORDER BY id");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql.toString())) {
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < COLUMNS.length; i++) {
                    row.put(COLUMNS[i][0], rs.getString("c" + i));
                }
                rows.put(rs.getLong("c0"), row);
            }
        }
        return rows;
    }

    private void checkTargetTypes(List<String> mismatches) {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("id", "bigint");
        expected.put("c_tinyint", "smallint");
        expected.put("c_smallint", "smallint");
        expected.put("c_mediumint", "integer");
        expected.put("c_int", "integer");
        expected.put("c_bigint", "bigint");
        expected.put("c_utinyint", "smallint");
        expected.put("c_usmallint", "integer");
        expected.put("c_umediumint", "integer");
        expected.put("c_uint", "bigint");
        expected.put("c_ubigint", "numeric");
        expected.put("c_float", "real");
        expected.put("c_double", "double precision");
        expected.put("c_decimal", "numeric");
        expected.put("c_char", "character");
        expected.put("c_varchar", "character varying");
        expected.put("c_text", "text");
        expected.put("c_binary", "bytea");
        expected.put("c_varbinary", "bytea");
        expected.put("c_blob", "bytea");
        expected.put("c_date", "date");
        expected.put("c_time", "time without time zone");
        expected.put("c_datetime", "timestamp without time zone");
        expected.put("c_timestamp", "timestamp without time zone");
        expected.put("c_bool", "smallint");
        expected.put("c_bit1", "boolean");
        expected.put("c_bit8", "bit");
        expected.put("c_json", "jsonb");
        expected.put("c_enum", "character varying");
        expected.put("c_set", "character varying");
        expected.put("c_year", "smallint");

        try (Connection conn = pgConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select column_name, data_type from information_schema.columns " +
                 "where table_schema='" + PG_SCHEMA + "' and table_name='" + TABLE + "'")) {
            Map<String, String> actual = new LinkedHashMap<>();
            while (rs.next()) {
                actual.put(rs.getString(1), rs.getString(2));
            }
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                String actualType = actual.get(entry.getKey());
                if (!entry.getValue().equals(actualType)) {
                    mismatches.add("类型映射 " + entry.getKey() + " 期望=" + entry.getValue() + " 实际=" + actualType);
                }
            }
        } catch (Exception e) {
            mismatches.add("读取目标表类型失败: " + e.getMessage());
        }
    }

    private int targetRowCount() {
        try (Connection conn = pgConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from " + PG_SCHEMA + "." + TABLE)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean targetHasColumn(String column) {
        try (Connection conn = pgConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select 1 from information_schema.columns where table_schema='" + PG_SCHEMA +
                 "' and table_name='" + TABLE + "' and column_name='" + column + "'")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private String targetColumnString(String column, long id) {
        try (Connection conn = pgConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select " + column + "::text from " + PG_SCHEMA + "." + TABLE + " where id=" + id)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
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

    private boolean postgresReachable() {
        try (Connection ignored = pgConnection()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void resetEnvironment() throws Exception {
        DBUtils.closePool(PG_URL, null);
        DBUtils.closePool(MYSQL_URL, null);
        java.io.File statusFile = new java.io.File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }

        try (Connection conn = pgConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + PG_SCHEMA + "." + TABLE);
            st.execute("CREATE SCHEMA IF NOT EXISTS " + PG_SCHEMA);
        }

        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + TABLE);
            st.execute(
                "CREATE TABLE test." + TABLE + " (" +
                "id BIGINT PRIMARY KEY, c_tinyint TINYINT, c_smallint SMALLINT, c_mediumint MEDIUMINT, " +
                "c_int INT, c_bigint BIGINT, " +
                "c_utinyint TINYINT UNSIGNED, c_usmallint SMALLINT UNSIGNED, c_umediumint MEDIUMINT UNSIGNED, " +
                "c_uint INT UNSIGNED, c_ubigint BIGINT UNSIGNED, " +
                "c_float FLOAT, c_double DOUBLE, c_decimal DECIMAL(20,6), " +
                "c_char CHAR(10), c_varchar VARCHAR(200), c_text TEXT, " +
                "c_binary BINARY(8), c_varbinary VARBINARY(32), c_blob BLOB, " +
                "c_date DATE, c_time TIME(3), c_datetime DATETIME(3), c_timestamp TIMESTAMP(3) NULL, " +
                "c_bool TINYINT(1), c_bit1 BIT(1), c_bit8 BIT(8), " +
                "c_json JSON, c_enum ENUM('a','b','c'), c_set SET('x','y','z'), c_year YEAR" +
                ")"
            );
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

    private Connection mysqlConnection() throws Exception {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }

    private Connection pgConnection() throws Exception {
        return DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message, AtomicReference<Throwable> jobError)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Throwable error = jobError.get();
            if (error != null && !isCancellation(error)) {
                throw new AssertionError(message + "；任务已异常结束: " + error.getMessage(), error);
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
