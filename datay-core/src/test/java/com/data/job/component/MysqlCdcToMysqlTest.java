package com.data.job.component;

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
 * MySQL CDC → MySQL 端到端验证。
 *
 * <p>覆盖事件类型：INSERT（单条与批量合并）、UPDATE（含主键变更）、DELETE、
 * DDL（ADD COLUMN / TRUNCATE）、snapshot 全量、目标表已存在时重启。
 *
 * <p>覆盖字段类型：有/无符号整数、浮点、定点、字符、文本、二进制、日期时间、
 * BIT、JSON、ENUM、SET、YEAR。
 *
 * <p>前置条件：
 * <ul>
 *   <li>源 MySQL 127.0.0.1:3306/test，root/1234qwer，binlog ROW；</li>
 *   <li>目标 MySQL 127.0.0.1:13306，root/Datay@12345，可创建 {@code test} 库。</li>
 * </ul>
 */
public class MysqlCdcToMysqlTest {

    private static final String SRC_SERVER_URL =
        "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String SRC_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String SRC_USER = "root";
    private static final String SRC_PASSWORD = "1234qwer";

    private static final String DST_SERVER_URL =
        "jdbc:mysql://127.0.0.1:13306/?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=%2B08%3A00&forceConnectionTimeZoneToSession=true";
    private static final String DST_URL =
        "jdbc:mysql://127.0.0.1:13306/test?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=%2B08%3A00&forceConnectionTimeZoneToSession=true";
    private static final String DST_USER = "root";
    private static final String DST_PASSWORD = "Datay@12345";

    private static final String SRC_MAPPED_URL =
        "jdbc:mysql://127.0.0.1:3306/cdc_src_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DST_MAPPED_URL =
        "jdbc:mysql://127.0.0.1:13306/cdc_dst_db?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=%2B08%3A00&forceConnectionTimeZoneToSession=true";

    private static final String SCHEMA = "test";
    private static final String TABLE = "cdc_mysql_verify";

    /** {逻辑列名, 读取表达式}，源库与目标库使用同一表达式。 */
    private static final String[][] COLUMNS = {
        { "id", "cast(id as char)" },
        { "c_tinyint", "cast(c_tinyint as char)" },
        { "c_smallint", "cast(c_smallint as char)" },
        { "c_mediumint", "cast(c_mediumint as char)" },
        { "c_int", "cast(c_int as char)" },
        { "c_bigint", "cast(c_bigint as char)" },
        { "c_utinyint", "cast(c_utinyint as char)" },
        { "c_usmallint", "cast(c_usmallint as char)" },
        { "c_umediumint", "cast(c_umediumint as char)" },
        { "c_uint", "cast(c_uint as char)" },
        { "c_ubigint", "cast(c_ubigint as char)" },
        { "c_float", "cast(c_float as char)" },
        { "c_double", "cast(c_double as char)" },
        { "c_decimal", "cast(c_decimal as char)" },
        { "c_char", "c_char" },
        { "c_varchar", "c_varchar" },
        { "c_text", "c_text" },
        { "c_binary", "hex(c_binary)" },
        { "c_varbinary", "hex(c_varbinary)" },
        { "c_blob", "hex(c_blob)" },
        { "c_date", "cast(c_date as char)" },
        { "c_time", "cast(c_time as char)" },
        { "c_datetime", "cast(c_datetime as char)" },
        { "c_timestamp", "cast(c_timestamp as char)" },
        { "c_bool", "cast(c_bool as char)" },
        { "c_bit1", "cast(c_bit1+0 as char)" },
        { "c_bit8", "lpad(bin(c_bit8+0), 8, '0')" },
        { "c_json_a", "json_unquote(json_extract(c_json, '$.a'))" },
        { "c_json_b", "json_unquote(json_extract(c_json, '$.b'))" },
        { "c_enum", "c_enum" },
        { "c_set", "c_set" },
        { "c_year", "cast(c_year as char)" },
    };

    @Test
    public void testMysqlCdcToMysql() throws Exception {
        assumeTrue(supportsRowBinlog(), "源 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(targetReachable(), "目标 MySQL 不可用，跳过测试");
        resetEnvironment();

        JobHandle job = startJob("mysqlCdcToMysql.json");
        List<String> mismatches = new ArrayList<>();
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时", job.error);

            // ---------- INSERT：全字段类型 ----------
            executeSource(
                "INSERT INTO test." + TABLE + " VALUES (" +
                "1, -5, 1234, 8388607, 123456, 9000000000, 200, 60000, 16000000, 4000000000, 18000000000000000000, " +
                "1.5, 3.1415926535, 12345.678901, 'abc', 'hello世界', 'text-value', " +
                "UNHEX('DEADBEEF00000000'), UNHEX('CAFEBABE1234'), UNHEX('CAFEBABE00FF'), " +
                "'2024-03-15', '12:34:56.789', '2024-03-15 12:34:56.789', '2024-03-15 12:34:56.789', 1, b'1', b'10101010', " +
                "'{\"a\":1,\"b\":\"中文\"}', 'b', 'x,z', 2024)"
            );
            awaitTrue(() -> targetRowCount() == 1, 45000, "等待 INSERT 同步到目标 MySQL 超时", job.error);
            collectMismatches("INSERT", mismatches);
            checkTargetTypes(mismatches);

            // ---------- INSERT：批量合并 ----------
            executeSource("INSERT INTO test." + TABLE + " (id, c_varchar, c_int) VALUES (2, 'batch-2', 2), (3, 'batch-3', 3)");
            awaitTrue(() -> targetRowCount() == 3, 30000, "等待批量 INSERT 同步到目标 MySQL 超时", job.error);
            collectMismatches("BATCH INSERT", mismatches);

            // ---------- UPDATE ----------
            executeSource(
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
            awaitTrue(() -> "updated值".equals(targetColumnString("c_varchar", 1)), 30000, "等待 UPDATE 同步到目标 MySQL 超时", job.error);
            collectMismatches("UPDATE", mismatches);

            // ---------- DELETE ----------
            executeSource("DELETE FROM test." + TABLE + " WHERE id=3");
            awaitTrue(() -> targetRowCount() == 2, 30000, "等待 DELETE 同步到目标 MySQL 超时", job.error);
            collectMismatches("DELETE", mismatches);

            // ---------- DDL：ADD COLUMN ----------
            executeSource("ALTER TABLE test." + TABLE + " ADD COLUMN c_added VARCHAR(20) DEFAULT 'ddl-default'");
            awaitTrue(() -> targetHasColumn("c_added"), 30000, "等待 ADD COLUMN DDL 同步到目标 MySQL 超时", job.error);
            executeSource("UPDATE test." + TABLE + " SET c_added='row1-added' WHERE id=1");
            executeSource("INSERT INTO test." + TABLE + " (id, c_varchar, c_int, c_added) VALUES (4, 'ddl-4', 4, 'row4-added')");
            awaitTrue(() -> targetRowCount() == 3, 30000, "等待 DDL 后 INSERT 同步到目标 MySQL 超时", job.error);
            assertEquals("row4-added", targetColumnString("c_added", 4), "DDL 新增列的写入值未同步到目标 MySQL");
            assertEquals("row1-added", targetColumnString("c_added", 1), "DDL 新增列的 UPDATE 值未同步到目标 MySQL");
            collectMismatches("DDL + INSERT/UPDATE", mismatches);

            // ---------- UPDATE：主键变更 ----------
            executeSource("UPDATE test." + TABLE + " SET id=10 WHERE id=2");
            awaitTrue(
                () -> "batch-2".equals(targetColumnString("c_varchar", 10)) && targetColumnString("c_varchar", 2) == null,
                30000,
                "等待主键变更的 UPDATE 同步到目标 MySQL 超时",
                job.error
            );
            collectMismatches("UPDATE 主键变更", mismatches);

            // ---------- DDL：TRUNCATE ----------
            executeSource("TRUNCATE TABLE test." + TABLE);
            awaitTrue(() -> targetRowCount() == 0, 30000, "等待 TRUNCATE 同步到目标 MySQL 超时", job.error);

            executeSource("INSERT INTO test." + TABLE + " (id, c_varchar, c_int) VALUES (99, 'final-evidence', 99)");
            awaitTrue(() -> "final-evidence".equals(targetColumnString("c_varchar", 99)),
                30000, "等待 TRUNCATE 后的增量 INSERT 同步到目标 MySQL 超时", job.error);
            collectMismatches("TRUNCATE + INSERT", mismatches);

            // ---------- DDL：DROP + CREATE TABLE ----------
            executeSource("DROP TABLE test." + TABLE);
            awaitTrue(() -> targetRowCount() == -1, 30000, "等待 DROP TABLE 同步到目标 MySQL 超时", job.error);
            executeSource(
                "CREATE TABLE test." + TABLE +
                " (id BIGINT PRIMARY KEY, c_varchar VARCHAR(50)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            awaitTrue(() -> targetRowCount() == 0 && targetHasColumn("c_varchar"),
                30000, "等待 CREATE TABLE 同步到目标 MySQL 超时", job.error);
            executeSource("INSERT INTO test." + TABLE + " VALUES (1, 'after-create')");
            awaitTrue(() -> "after-create".equals(targetColumnString("c_varchar", 1)),
                30000, "等待 CREATE TABLE 后的 INSERT 同步到目标 MySQL 超时", job.error);
        } finally {
            stopJob(job);
        }

        assertJobSucceeded(job);
        assertTrue(mismatches.isEmpty(), "MySQL CDC → MySQL 校验失败:\n - " + String.join("\n - ", mismatches));
    }

    /**
     * 目标表已存在时重启任务（不经过自动建表，直接读取目标 MySQL 元数据绑定参数）。
     */
    @Test
    public void testRestartWithExistingTargetTable() throws Exception {
        assumeTrue(supportsRowBinlog(), "源 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(targetReachable(), "目标 MySQL 不可用，跳过测试");
        resetEnvironment();

        List<String> mismatches = new ArrayList<>();
        JobHandle first = startJob("mysqlCdcToMysql.json");
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时", first.error);
            executeSource(
                "INSERT INTO test." + TABLE + " VALUES (" +
                "1, -5, 1234, 8388607, 123456, 9000000000, 200, 60000, 16000000, 4000000000, 18000000000000000000, " +
                "1.5, 3.1415926535, 12345.678901, 'abc', 'hello世界', 'text-value', " +
                "UNHEX('DEADBEEF00000000'), UNHEX('CAFEBABE1234'), UNHEX('CAFEBABE00FF'), " +
                "'2024-03-15', '12:34:56.789', '2024-03-15 12:34:56.789', '2024-03-15 12:34:56.789', 1, b'1', b'10101010', " +
                "'{\"a\":1,\"b\":\"中文\"}', 'b', 'x,z', 2024)"
            );
            awaitTrue(() -> targetRowCount() == 1, 45000, "等待首次 INSERT 同步到目标 MySQL 超时", first.error);
            collectMismatches("首次 INSERT", mismatches);
        } finally {
            stopJobWithoutClosingPools(first);
        }

        JobHandle second = startJob("mysqlCdcToMysql.json");
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待重启后的 MySQL Binlog 采集器就绪超时", second.error);
            executeSource(
                "INSERT INTO test." + TABLE +
                " (id, c_varchar, c_int, c_bit1, c_bit8, c_json, c_enum, c_set, c_year, c_ubigint) VALUES " +
                "(2, 'restart-2', 2, b'1', b'00001111', '{\"a\":9,\"b\":\"重启\"}', 'a', 'y,z', 2026, 18446744073709551615)"
            );
            executeSource("UPDATE test." + TABLE + " SET c_varchar='restart-updated', c_char='zzz', c_bit1=b'0' WHERE id=1");
            awaitTrue(
                () -> targetRowCount() == 2 && "restart-updated".equals(targetColumnString("c_varchar", 1)),
                30000,
                "等待重启后的 INSERT/UPDATE 同步到目标 MySQL 超时",
                second.error
            );
            collectMismatches("重启后 INSERT/UPDATE", mismatches);
        } finally {
            stopJobWithoutClosingPools(second);
        }

        DBUtils.closePool(SRC_URL, null);
        DBUtils.closePool(DST_URL, null);
        assertJobSucceeded(first);
        assertJobSucceeded(second);
        assertTrue(mismatches.isEmpty(), "目标表已存在时同步校验失败:\n - " + String.join("\n - ", mismatches));
    }

    /**
     * snapshot=true：先全量导入存量数据（含 ENUM/SET/BIT/无符号类型），再继续消费增量。
     */
    @Test
    public void testSnapshotThenIncremental() throws Exception {
        assumeTrue(supportsRowBinlog(), "源 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(targetReachable(), "目标 MySQL 不可用，跳过测试");
        resetEnvironment();

        executeSource(
            "INSERT INTO test." + TABLE +
            " (id, c_varchar, c_int, c_decimal, c_date, c_bit1, c_bit8, c_binary, c_json, c_enum, c_set, c_year, c_ubigint) VALUES " +
            "(1, 'snapshot-1', 1, 10.500000, '2024-01-01', b'1', b'10101010', UNHEX('DEADBEEF00000000'), '{\"a\":1}', 'a', 'x', 2020, 18000000000000000000), " +
            "(2, 'snapshot-2', 2, 20.250000, '2024-01-02', b'0', b'00001111', UNHEX('0102030405060708'), '{\"a\":2}', 'b', 'y', 2021, 18446744073709551615)"
        );

        List<String> mismatches = new ArrayList<>();
        JobHandle job = startJob("mysqlCdcToMysqlSnapshot.json");
        try {
            awaitTrue(() -> targetRowCount() == 2, 45000, "等待 snapshot 全量数据导入目标 MySQL 超时", job.error);
            collectMismatches("SNAPSHOT", mismatches);

            executeSource("INSERT INTO test." + TABLE + " (id, c_varchar, c_int) VALUES (3, 'incremental-3', 3)");
            executeSource("UPDATE test." + TABLE + " SET c_varchar='snapshot-updated' WHERE id=1");
            awaitTrue(
                () -> targetRowCount() == 3 && "snapshot-updated".equals(targetColumnString("c_varchar", 1)),
                30000,
                "等待 snapshot 后的增量同步到目标 MySQL 超时",
                job.error
            );
            collectMismatches("SNAPSHOT + 增量", mismatches);
        } finally {
            stopJob(job);
        }

        assertJobSucceeded(job);
        assertTrue(mismatches.isEmpty(), "snapshot 同步校验失败:\n - " + String.join("\n - ", mismatches));
    }

    private void collectMismatches(String phase, List<String> mismatches) {
        collectMismatches(phase, TABLE, COLUMNS, mismatches);
    }

    private void collectMismatches(String phase, String table, String[][] columns, List<String> mismatches) {
        try {
            Map<Long, Map<String, String>> source = readRows(sourceConnection(), table, columns);
            Map<Long, Map<String, String>> target;
            try {
                target = readRows(targetConnection(), table, columns);
            } catch (Exception e) {
                mismatches.add(phase + " 读取目标表失败: " + e.getMessage());
                return;
            }
            compareRows(phase, table, source, target, columns, mismatches);
        } catch (Exception e) {
            mismatches.add(phase + " 校验异常: " + e.getMessage());
        }
    }

    private void compareRows(String phase, String table, Map<Long, Map<String, String>> source,
                             Map<Long, Map<String, String>> target, String[][] columns, List<String> mismatches) {
        if (!source.keySet().equals(target.keySet())) {
            mismatches.add(phase + " 表 " + table + " 主键集合不一致 source=" + source.keySet() + " target=" + target.keySet());
        }
        for (Map.Entry<Long, Map<String, String>> entry : source.entrySet()) {
            Map<String, String> expected = entry.getValue();
            Map<String, String> actual = target.get(entry.getKey());
            if (actual == null) {
                continue;
            }
            for (String[] column : columns) {
                String e = expected.get(column[0]);
                String a = actual.get(column[0]);
                if (e == null ? a != null : !e.equals(a)) {
                    mismatches.add(phase + " 表 " + table + " id=" + entry.getKey() + " 列 [" + column[0] + "] source=" + e + " target=" + a);
                }
            }
        }
    }

    private Map<Long, Map<String, String>> readRows(Connection conn) throws Exception {
        return readRows(conn, TABLE, COLUMNS);
    }

    private Map<Long, Map<String, String>> readRows(Connection conn, String table, String[][] columns) throws Exception {
        return readRows(conn, SCHEMA, table, columns);
    }

    private Map<Long, Map<String, String>> readRows(Connection conn, String schema, String table, String[][] columns)
        throws Exception {
        Map<Long, Map<String, String>> rows = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns[i][1]).append(" AS c").append(i);
        }
        sql.append(" FROM ").append(schema).append(".").append(table).append(" ORDER BY id");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql.toString())) {
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.length; i++) {
                    row.put(columns[i][0], rs.getString("c" + i));
                }
                rows.put(rs.getLong("c0"), row);
            }
        }
        return rows;
    }

    private void checkTargetTypes(List<String> mismatches) {
        Map<String, String> dataTypes = new LinkedHashMap<>();
        dataTypes.put("id", "bigint");
        dataTypes.put("c_tinyint", "tinyint");
        dataTypes.put("c_smallint", "smallint");
        dataTypes.put("c_mediumint", "mediumint");
        dataTypes.put("c_int", "int");
        dataTypes.put("c_bigint", "bigint");
        dataTypes.put("c_utinyint", "tinyint");
        dataTypes.put("c_usmallint", "smallint");
        dataTypes.put("c_umediumint", "mediumint");
        dataTypes.put("c_uint", "int");
        dataTypes.put("c_ubigint", "bigint");
        dataTypes.put("c_float", "float");
        dataTypes.put("c_double", "double");
        dataTypes.put("c_decimal", "decimal");
        dataTypes.put("c_char", "char");
        dataTypes.put("c_varchar", "varchar");
        dataTypes.put("c_text", "text");
        dataTypes.put("c_binary", "binary");
        dataTypes.put("c_varbinary", "varbinary");
        dataTypes.put("c_blob", "blob");
        dataTypes.put("c_date", "date");
        dataTypes.put("c_time", "time");
        dataTypes.put("c_datetime", "datetime");
        dataTypes.put("c_timestamp", "timestamp");
        dataTypes.put("c_bool", "tinyint");
        dataTypes.put("c_bit1", "bit");
        dataTypes.put("c_bit8", "bit");
        dataTypes.put("c_json", "json");
        dataTypes.put("c_enum", "enum");
        dataTypes.put("c_set", "set");
        dataTypes.put("c_year", "year");

        Map<String, String> columnTypeFragments = new LinkedHashMap<>();
        columnTypeFragments.put("c_utinyint", "tinyint unsigned");
        columnTypeFragments.put("c_usmallint", "smallint unsigned");
        columnTypeFragments.put("c_umediumint", "mediumint unsigned");
        columnTypeFragments.put("c_uint", "int unsigned");
        columnTypeFragments.put("c_ubigint", "bigint unsigned");
        columnTypeFragments.put("c_decimal", "decimal(20,6)");
        columnTypeFragments.put("c_char", "char(10)");
        columnTypeFragments.put("c_varchar", "varchar(200)");
        columnTypeFragments.put("c_binary", "binary(8)");
        columnTypeFragments.put("c_varbinary", "varbinary(32)");
        columnTypeFragments.put("c_time", "time(3)");
        columnTypeFragments.put("c_datetime", "datetime(3)");
        columnTypeFragments.put("c_timestamp", "timestamp(3)");
        columnTypeFragments.put("c_bit1", "bit(1)");
        columnTypeFragments.put("c_bit8", "bit(8)");
        columnTypeFragments.put("c_enum", "enum('a','b','c')");
        columnTypeFragments.put("c_set", "set('x','y','z')");

        try (Connection conn = targetConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select column_name, data_type, column_type from information_schema.columns " +
                 "where table_schema='" + SCHEMA + "' and table_name='" + TABLE + "'")) {
            Map<String, String> actualDataTypes = new LinkedHashMap<>();
            Map<String, String> actualColumnTypes = new LinkedHashMap<>();
            while (rs.next()) {
                actualDataTypes.put(rs.getString(1), rs.getString(2).toLowerCase());
                actualColumnTypes.put(rs.getString(1), rs.getString(3).toLowerCase());
            }
            for (Map.Entry<String, String> entry : dataTypes.entrySet()) {
                String actual = actualDataTypes.get(entry.getKey());
                if (!entry.getValue().equals(actual)) {
                    mismatches.add("类型映射 " + entry.getKey() + " 期望=" + entry.getValue() + " 实际=" + actual);
                }
            }
            for (Map.Entry<String, String> entry : columnTypeFragments.entrySet()) {
                String actual = actualColumnTypes.get(entry.getKey());
                if (actual == null || !actual.contains(entry.getValue())) {
                    mismatches.add("列定义 " + entry.getKey() + " 期望包含=" + entry.getValue() + " 实际=" + actual);
                }
            }
        } catch (Exception e) {
            mismatches.add("读取目标表类型失败: " + e.getMessage());
        }
    }

    private int targetRowCount() {
        return targetRowCount(TABLE);
    }

    private int targetRowCount(String table) {
        try (Connection conn = targetConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from " + SCHEMA + "." + table)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean targetHasColumn(String column) {
        return targetHasColumn(TABLE, column);
    }

    private boolean targetHasColumn(String table, String column) {
        try (Connection conn = targetConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select 1 from information_schema.columns where table_schema='" + SCHEMA +
                 "' and table_name='" + table + "' and column_name='" + column + "'")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private String targetColumnString(String column, long id) {
        return targetColumnString(TABLE, column, id);
    }

    private String targetColumnString(String table, String column, long id) {
        try (Connection conn = targetConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select cast(" + column + " as char) from " + SCHEMA + "." + table + " where id=" + id)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int rowCount(Connection conn, String schema, String table) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from " + schema + "." + table)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean hasColumn(Connection conn, String schema, String table, String column) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select 1 from information_schema.columns where table_schema='" + schema +
                 "' and table_name='" + table + "' and column_name='" + column + "'")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private String columnString(Connection conn, String schema, String table, String column, long id) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "select cast(" + column + " as char) from " + schema + "." + table + " where id=" + id)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean supportsRowBinlog() {
        try (Connection conn = openSource()) {
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

    private boolean targetReachable() {
        try (Connection ignored = DriverManager.getConnection(DST_SERVER_URL, DST_USER, DST_PASSWORD)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void resetEnvironment() throws Exception {
        DBUtils.closePool(SRC_URL, null);
        DBUtils.closePool(DST_URL, null);
        java.io.File statusFile = new java.io.File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }

        // 目标库准备：与源库同名 schema，保证 DDL 事件中的 test.xxx 能直接执行
        try (Connection conn = DriverManager.getConnection(DST_SERVER_URL, DST_USER, DST_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS test DEFAULT CHARACTER SET utf8mb4");
        }
        try (Connection conn = targetConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + SCHEMA + "." + TABLE);
        }

        try (Connection conn = openSource(); Statement st = conn.createStatement()) {
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
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    private boolean binlogDumpActive() {
        try (Connection conn = openSource(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("show processlist")) {
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

    private void executeSource(String sql) throws Exception {
        try (Connection conn = openSource(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private Connection openSource() throws Exception {
        return DriverManager.getConnection(SRC_URL, SRC_USER, SRC_PASSWORD);
    }

    private Connection sourceConnection() throws Exception {
        return DriverManager.getConnection(SRC_URL, SRC_USER, SRC_PASSWORD);
    }

    private Connection targetConnection() throws Exception {
        return DriverManager.getConnection(DST_URL, DST_USER, DST_PASSWORD);
    }

    private Connection mappedSource() throws Exception {
        return DriverManager.getConnection(SRC_MAPPED_URL, SRC_USER, SRC_PASSWORD);
    }

    private Connection mappedTarget() throws Exception {
        return DriverManager.getConnection(DST_MAPPED_URL, DST_USER, DST_PASSWORD);
    }

    private int mappedTargetCount() {
        try (Connection conn = mappedTarget()) {
            return rowCount(conn, "cdc_dst_db", "cdc_schema_verify");
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean mappedTargetHasColumn(String column) {
        try (Connection conn = mappedTarget()) {
            return hasColumn(conn, "cdc_dst_db", "cdc_schema_verify", column);
        } catch (Exception e) {
            return false;
        }
    }

    private String mappedTargetColumn(String column, long id) {
        try (Connection conn = mappedTarget()) {
            return columnString(conn, "cdc_dst_db", "cdc_schema_verify", column, id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 多表同步：验证不同表的事件合并、元数据缓存互不影响，以及 DDL 只作用于目标表。
     */
    @Test
    public void testMultiTableSync() throws Exception {
        assumeTrue(supportsRowBinlog(), "源 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(targetReachable(), "目标 MySQL 不可用，跳过测试");
        resetEnvironment();

        String tableA = "cdc_multi_a";
        String tableB = "cdc_multi_b";
        try (Connection conn = openSource(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + tableA);
            st.execute(
                "CREATE TABLE test." + tableA +
                " (id BIGINT PRIMARY KEY, name VARCHAR(50), amount DECIMAL(10,2), flag TINYINT) ENGINE=InnoDB"
            );
            st.execute("DROP TABLE IF EXISTS test." + tableB);
            st.execute(
                "CREATE TABLE test." + tableB +
                " (id BIGINT PRIMARY KEY, title VARCHAR(50), created_at DATETIME) ENGINE=InnoDB"
            );
        }
        try (Connection conn = targetConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + tableA);
            st.execute("DROP TABLE IF EXISTS test." + tableB);
        }

        String[][] columnsA = {
            { "id", "cast(id as char)" },
            { "name", "name" },
            { "amount", "cast(amount as char)" },
            { "flag", "cast(flag as char)" },
        };
        String[][] columnsB = {
            { "id", "cast(id as char)" },
            { "title", "title" },
            { "created_at", "cast(created_at as char)" },
        };
        String[][] columnsAWithNote = {
            { "id", "cast(id as char)" },
            { "name", "name" },
            { "amount", "cast(amount as char)" },
            { "flag", "cast(flag as char)" },
            { "note", "note" },
        };

        List<String> mismatches = new ArrayList<>();
        JobHandle job = startJob("mysqlCdcToMysqlMulti.json");
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时", job.error);

            executeSource(
                "INSERT INTO test." + tableA + " VALUES (1, 'a1', 10.50, 1), (2, 'a2', 20.25, 0)"
            );
            executeSource(
                "INSERT INTO test." + tableB + " VALUES (1, 'b1', '2024-01-01 10:00:00'), (2, 'b2', '2024-01-02 11:00:00')"
            );
            awaitTrue(
                () -> targetRowCount(tableA) == 2 && targetRowCount(tableB) == 2,
                30000,
                "等待多表 INSERT 同步到目标 MySQL 超时",
                job.error
            );
            collectMismatches("多表 INSERT A", tableA, columnsA, mismatches);
            collectMismatches("多表 INSERT B", tableB, columnsB, mismatches);

            executeSource("UPDATE test." + tableA + " SET name='a1-updated', amount=11.11 WHERE id=1");
            executeSource("DELETE FROM test." + tableB + " WHERE id=2");
            awaitTrue(
                () -> "a1-updated".equals(targetColumnString(tableA, "name", 1)) && targetRowCount(tableB) == 1,
                30000,
                "等待多表 UPDATE/DELETE 同步到目标 MySQL 超时",
                job.error
            );
            collectMismatches("多表 UPDATE/DELETE A", tableA, columnsA, mismatches);
            collectMismatches("多表 UPDATE/DELETE B", tableB, columnsB, mismatches);

            // DDL 只作用于 A 表，B 表结构不应受影响
            executeSource("ALTER TABLE test." + tableA + " ADD COLUMN note VARCHAR(20) DEFAULT 'n'");
            awaitTrue(() -> targetHasColumn(tableA, "note"), 30000, "等待 A 表 ADD COLUMN 同步到目标 MySQL 超时", job.error);
            assertTrue(!targetHasColumn(tableB, "note"), "B 表不应出现 A 表新增的 note 列");

            executeSource("UPDATE test." + tableA + " SET note='n1' WHERE id=1");
            executeSource("INSERT INTO test." + tableA + " (id, name, amount, flag, note) VALUES (3, 'a3', 30.30, 1, 'n3')");
            executeSource("INSERT INTO test." + tableB + " (id, title, created_at) VALUES (3, 'b3', '2024-01-03 12:00:00')");
            awaitTrue(
                () -> targetRowCount(tableA) == 3 && "n3".equals(targetColumnString(tableA, "note", 3)) && targetRowCount(tableB) == 2,
                30000,
                "等待多表 DDL 后的 INSERT 同步到目标 MySQL 超时",
                job.error
            );
            collectMismatches("多表 DDL 后 A", tableA, columnsAWithNote, mismatches);
            collectMismatches("多表 DDL 后 B", tableB, columnsB, mismatches);
        } finally {
            stopJob(job);
        }

        assertJobSucceeded(job);
        assertTrue(mismatches.isEmpty(), "多表同步校验失败:\n - " + String.join("\n - ", mismatches));
    }

    /**
     * 源库与目标库不同名：验证 DDL 的库名映射，以及数据写入落到目标库。
     */
    @Test
    public void testSchemaMappingAndDdlRewrite() throws Exception {
        assumeTrue(supportsRowBinlog(), "源 MySQL 未开启 binlog ROW 格式，跳过测试");
        assumeTrue(targetReachable(), "目标 MySQL 不可用，跳过测试");

        String table = "cdc_schema_verify";
        java.io.File statusFile = new java.io.File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        DBUtils.closePool(SRC_MAPPED_URL, null);
        DBUtils.closePool(DST_MAPPED_URL, null);

        try (Connection conn = DriverManager.getConnection(SRC_SERVER_URL, SRC_USER, SRC_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS cdc_src_db DEFAULT CHARACTER SET utf8mb4");
        }
        try (Connection conn = DriverManager.getConnection(DST_SERVER_URL, DST_USER, DST_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS cdc_dst_db DEFAULT CHARACTER SET utf8mb4");
        }
        try (Connection conn = mappedSource(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS cdc_src_db." + table);
            st.execute(
                "CREATE TABLE cdc_src_db." + table +
                " (id BIGINT PRIMARY KEY, name VARCHAR(50), amount DECIMAL(10,2), qty INT, created_at DATETIME, flag TINYINT) " +
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
        try (Connection conn = mappedTarget(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS cdc_dst_db." + table);
        }

        String[][] columnsWithNotes = {
            { "id", "cast(id as char)" },
            { "name", "name" },
            { "amount", "cast(amount as char)" },
            { "qty", "cast(qty as char)" },
            { "created_at", "cast(created_at as char)" },
            { "flag", "cast(flag as char)" },
            { "note", "note" },
            { "note2", "note2" },
        };

        List<String> mismatches = new ArrayList<>();
        JobHandle job = startJob("mysqlCdcToMysqlSchemaMapping.json");
        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时", job.error);

            executeSourceOn(SRC_MAPPED_URL, SRC_USER, SRC_PASSWORD,
                "INSERT INTO cdc_src_db." + table + " VALUES (1, 'mapped-1', 10.50, 5, '2024-01-01 10:00:00', 1)");
            awaitTrue(() -> mappedTargetCount() == 1, 30000, "等待跨库 INSERT 同步到目标库超时", job.error);

            // 带库名的 DDL：源 cdc_src_db → 目标 cdc_dst_db
            executeSourceOn(SRC_MAPPED_URL, SRC_USER, SRC_PASSWORD,
                "ALTER TABLE cdc_src_db." + table + " ADD COLUMN note VARCHAR(20) DEFAULT 'n'");
            awaitTrue(() -> mappedTargetHasColumn("note"), 30000, "等待带库名 DDL 同步到目标库超时", job.error);

            // 不带库名的 DDL：依赖目标连接默认库
            executeSourceOn(SRC_MAPPED_URL, SRC_USER, SRC_PASSWORD,
                "ALTER TABLE " + table + " ADD COLUMN note2 VARCHAR(20) DEFAULT 'm'");
            awaitTrue(() -> mappedTargetHasColumn("note2"), 30000, "等待不带库名 DDL 同步到目标库超时", job.error);

            executeSourceOn(SRC_MAPPED_URL, SRC_USER, SRC_PASSWORD,
                "UPDATE cdc_src_db." + table + " SET name='mapped-updated', note='n1', note2='m1' WHERE id=1");
            executeSourceOn(SRC_MAPPED_URL, SRC_USER, SRC_PASSWORD,
                "INSERT INTO cdc_src_db." + table + " (id, name, amount, qty, created_at, flag, note, note2) " +
                "VALUES (2, 'mapped-2', 20.25, 6, '2024-01-02 11:00:00', 0, 'n2', 'm2')");
            awaitTrue(
                () -> mappedTargetCount() == 2 && "mapped-updated".equals(mappedTargetColumn("name", 1)),
                30000,
                "等待跨库 UPDATE/INSERT 同步到目标库超时",
                job.error
            );

            try (
                Connection source = mappedSource();
                Connection target = mappedTarget()
            ) {
                compareRows(
                    "跨库同步",
                    table,
                    readRows(source, "cdc_src_db", table, columnsWithNotes),
                    readRows(target, "cdc_dst_db", table, columnsWithNotes),
                    columnsWithNotes,
                    mismatches
                );
            }

            // 不带库名的 TRUNCATE 也应落到目标库
            executeSourceOn(SRC_MAPPED_URL, SRC_USER, SRC_PASSWORD, "TRUNCATE TABLE " + table);
            awaitTrue(() -> mappedTargetCount() == 0, 30000, "等待跨库 TRUNCATE 同步到目标库超时", job.error);
        } finally {
            stopJobWithoutClosingPools(job);
            DBUtils.closePool(SRC_MAPPED_URL, null);
            DBUtils.closePool(DST_MAPPED_URL, null);
            DBUtils.closePool(SRC_URL, null);
            DBUtils.closePool(DST_URL, null);
        }

        assertJobSucceeded(job);
        assertTrue(mismatches.isEmpty(), "跨库 DDL/数据同步校验失败:\n - " + String.join("\n - ", mismatches));
    }

    private void executeSourceOn(String url, String user, String password, String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(url, user, password); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
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
        DBUtils.closePool(SRC_URL, null);
        DBUtils.closePool(DST_URL, null);
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
