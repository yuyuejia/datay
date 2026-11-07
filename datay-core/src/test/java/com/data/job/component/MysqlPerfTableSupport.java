package com.data.job.component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * MySQL CDC 性能测试的表结构与数据生成工具。
 *
 * <p>默认使用 4 字段窄表；通过 {@code -Dperf.wide=true} 切换到 20 字段宽表，
 * 用于验证行宽对实时同步吞吐和延迟的影响。</p>
 */
final class MysqlPerfTableSupport {

    static final boolean WIDE = Boolean.getBoolean("perf.wide");

    private static final String NARROW_COLUMNS = "id, name, amount, created_at";

    private static final String WIDE_COLUMNS =
        "id, c_tinyint, c_smallint, c_int, c_bigint, c_decimal, c_float, c_double, " +
        "name, c_char, c_text, c_date, c_time, c_datetime, c_timestamp, c_bool, " +
        "c_binary, c_blob, c_amount2, c_remark";

    private MysqlPerfTableSupport() {}

    static String tableName(String narrowName, String wideName) {
        return System.getProperty("perf.table", WIDE ? wideName : narrowName);
    }

    static String createTableSql(String schema, String table) {
        if (!WIDE) {
            return "CREATE TABLE " + schema + "." + table + " (" +
                "id bigint primary key, " +
                "name varchar(128), " +
                "amount decimal(12,2), " +
                "created_at datetime)";
        }
        return "CREATE TABLE " + schema + "." + table + " (" +
            "id bigint primary key, " +
            "c_tinyint tinyint, " +
            "c_smallint smallint, " +
            "c_int int, " +
            "c_bigint bigint, " +
            "c_decimal decimal(18,6), " +
            "c_float float, " +
            "c_double double, " +
            "name varchar(128), " +
            "c_char char(16), " +
            "c_text text, " +
            "c_date date, " +
            "c_time time, " +
            "c_datetime datetime, " +
            "c_timestamp timestamp, " +
            "c_bool tinyint(1), " +
            "c_binary varbinary(16), " +
            "c_blob blob, " +
            "c_amount2 decimal(12,2), " +
            "c_remark varchar(255))";
    }

    static String insertColumns() {
        return WIDE ? WIDE_COLUMNS : NARROW_COLUMNS;
    }

    static void appendInsertValues(StringBuilder sql, long id, String prefix) {
        if (!WIDE) {
            sql.append('(')
                .append(id)
                .append(",'")
                .append(prefix)
                .append('_')
                .append(id)
                .append("',")
                .append(id % 1000)
                .append(".25,'2026-01-01 00:00:00')");
            return;
        }
        sql.append('(')
            .append(id).append(',')
            .append(id % 100).append(',')
            .append(id % 1000).append(',')
            .append(id % 100000).append(',')
            .append(id).append(',')
            .append(id % 100000).append(".123456,")
            .append(id % 1000).append(".5,")
            .append(id % 1000).append(".25,'")
            .append(prefix).append('_').append(id).append("',")
            .append("'char16','text_").append(id).append("',")
            .append("'2026-01-01','12:34:56','2026-01-01 12:34:56','2026-01-01 12:34:56',")
            .append(id % 2).append(",0x0102,0xDEADBEEF,")
            .append(id % 1000).append(".25,'remark_").append(id).append("')");
    }

    static String latencyInsertSql(String schema, String table) {
        return "INSERT INTO " + schema + "." + table + " (" + insertColumns() + ") VALUES (" +
            placeholders(WIDE ? 20 : 4) + ")";
    }

    static String warmupInsertSql(String schema, String table) {
        if (WIDE) {
            return "INSERT INTO " + schema + "." + table + " (id, name) VALUES (-1, 'warmup')";
        }
        return "INSERT INTO " + schema + "." + table +
            " (id, name, amount, created_at) VALUES (-1, 'warmup', 0.00, '2026-01-01 00:00:00')";
    }

    static void bindLatencyRow(PreparedStatement ps, long id) throws SQLException {
        if (!WIDE) {
            ps.setLong(1, id);
            ps.setString(2, "lat_" + id);
            ps.setBigDecimal(3, BigDecimal.valueOf(id % 1000).add(new BigDecimal("0.25")));
            ps.setTimestamp(4, Timestamp.valueOf("2026-01-01 00:00:00"));
            return;
        }
        ps.setLong(1, id);
        ps.setInt(2, (int) (id % 100));
        ps.setInt(3, (int) (id % 1000));
        ps.setInt(4, (int) (id % 100000));
        ps.setLong(5, id);
        ps.setBigDecimal(6, BigDecimal.valueOf(id % 100000).add(new BigDecimal("0.123456")));
        ps.setFloat(7, (id % 1000) + 0.5f);
        ps.setDouble(8, (id % 1000) + 0.25d);
        ps.setString(9, "lat_" + id);
        ps.setString(10, "char16");
        ps.setString(11, "text_" + id);
        ps.setDate(12, Date.valueOf("2026-01-01"));
        ps.setTime(13, Time.valueOf("12:34:56"));
        ps.setTimestamp(14, Timestamp.valueOf("2026-01-01 12:34:56"));
        ps.setTimestamp(15, Timestamp.valueOf("2026-01-01 12:34:56"));
        ps.setInt(16, (int) (id % 2));
        ps.setBytes(17, new byte[] { 1, 2 });
        ps.setBytes(18, new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF });
        ps.setBigDecimal(19, BigDecimal.valueOf(id % 1000).add(new BigDecimal("0.25")));
        ps.setString(20, "remark_" + id);
    }

    private static String placeholders(int count) {
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        return sql.toString();
    }
}
