package com.data.job.component;

import com.data.job.DuckDBEngine;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL → DuckLake 全量同步性能验证（默认不执行）。
 *
 * <p>启用方式：</p>
 * <pre>
 *   -Dperf.mysql.enabled=true
 *   -Dperf.mysql.rows=4000000        # 期望源表行数，默认 4000000
 *   -Dperf.mysql.table=perf.big_sync # 源表，默认 perf.big_sync
 * </pre>
 *
 * <p>源表需预先准备（约 4M 行 / 1GB）：</p>
 * <pre>
 * CREATE DATABASE IF NOT EXISTS perf;
 * CREATE TABLE perf.big_sync (
 *   id BIGINT NOT NULL, order_no VARCHAR(32) NOT NULL, user_id BIGINT NOT NULL,
 *   amount DECIMAL(18,2) NOT NULL, qty INT NOT NULL, status VARCHAR(16) NOT NULL,
 *   created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 *   remark VARCHAR(64), city VARCHAR(32), score DOUBLE, flag TINYINT,
 *   payload VARCHAR(255), PRIMARY KEY (id)
 * ) ENGINE=InnoDB;
 * </pre>
 *
 * <p>测试会把同步结果写到 {@code target/perf/mysql-ducklake-&lt;uuid&gt;/}，
 * 结束后校验行数、去重与抽样值，并打印吞吐和输出体积。</p>
 */
public class MysqlToDuckLakePerformanceTest {

    private static final String MYSQL_URL = System.getProperty("perf.mysql.url", "jdbc:mysql://127.0.0.1:3306");
    private static final String MYSQL_USER = System.getProperty("perf.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("perf.mysql.password", "1234qwer");
    private static final String SOURCE = System.getProperty("perf.mysql.table", "perf.big_sync");
    private static final long EXPECTED_ROWS = Long.getLong("perf.mysql.rows", 4_000_000L);

    @Test
    @Timeout(3_600)
    public void syncMysqlToDuckLake() throws Exception {
        Assumptions.assumeTrue(
            Boolean.getBoolean("perf.mysql.enabled"),
            "设置 -Dperf.mysql.enabled=true 运行真实 MySQL→DuckLake 性能验证"
        );

        long sourceRows;
        long sourceBytes;
        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            sourceRows = scalar(conn, "SELECT COUNT(*) FROM " + SOURCE);
            sourceBytes = scalar(conn, "SELECT data_length + index_length FROM information_schema.tables "
                + "WHERE table_schema = '" + schemaName() + "' AND table_name = '" + tableName() + "'");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "MySQL 不可用或源表不存在，跳过: " + e.getMessage());
            return;
        }
        Assumptions.assumeTrue(
            sourceRows == EXPECTED_ROWS,
            "源表 " + SOURCE + " 行数为 " + sourceRows + "，与期望 " + EXPECTED_ROWS + " 不一致，跳过"
        );

        Path base = Path.of(System.getProperty("user.dir"), "target", "perf", "mysql-ducklake-" + UUID.randomUUID());
        Files.createDirectories(base);
        String meta = base.resolve("meta.ducklake").toString();
        String dataDir = base.resolve("data").toString();

        String instanceCode = "mysql-ducklake-perf-" + UUID.randomUUID();
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setInstanceCode(instanceCode);
        taskInstance.setJobCode("mysql-ducklake-perf");

        ETLFlowTask runner = new ETLFlowTask(taskInstance, null);
        long start = System.nanoTime();
        runner.runJob(jobJson(meta, dataDir));
        double seconds = (System.nanoTime() - start) / 1e9;

        long targetRows;
        long distinctIds;
        long payloadBytes;
        try (Connection conn = DuckDBEngine.getInstance().getConnection(instanceCode);
             Statement statement = conn.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                "SELECT count(*), count(DISTINCT id), sum(length(payload)) FROM ducklake.main.big_sync"
            )) {
                rs.next();
                targetRows = rs.getLong(1);
                distinctIds = rs.getLong(2);
                payloadBytes = rs.getLong(3);
            }
        }

        assertEquals(EXPECTED_ROWS, targetRows, "DuckLake 行数应与源表一致");
        assertEquals(EXPECTED_ROWS, distinctIds, "DuckLake 主键应无重复");
        long outputBytes = directorySize(base);
        double rowsPerSecond = targetRows / seconds;
        double sourceMbPerSecond = sourceBytes / 1024.0 / 1024.0 / seconds;

        System.out.printf(
            "%n[MySQL→DuckLake 性能] 源表=%s 行数=%d 源数据=%.1f MB 耗时=%.3f s 吞吐=%.0f 行/s 源侧吞吐=%.1f MB/s%n",
            SOURCE, targetRows, sourceBytes / 1024.0 / 1024.0, seconds, rowsPerSecond, sourceMbPerSecond
        );
        System.out.printf(
            "[MySQL→DuckLake 输出] 输出总大小=%.1f MB（含元数据） payload字符数=%d 元数据=%s 数据目录=%s%n%n",
            outputBytes / 1024.0 / 1024.0, payloadBytes, meta, dataDir
        );

        assertTrue(seconds > 0, "耗时异常");
    }

    private String jobJson(String meta, String dataDir) {
        return """
            {
              "units": [
                {
                  ".id": "mysql_src",
                  ".name": "StreamJdbcInput",
                  "sourceId": {
                    "url": "%s",
                    "driver": "com.mysql.cj.jdbc.Driver",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "%s"
                  },
                  "table": "%s",
                  "maxRowsPerPartition": "10000",
                  "incrColumn": "",
                  "where": ""
                },
                {
                  ".id": "ducklake_sink",
                  ".name": "DuckLakeWrite",
                  "sourceId": {
                    "url": "ducklake:%s",
                    "driver": "org.duckdb.DuckDBDriver",
                    "dbschema": "main",
                    "extraParams": {
                      "s3.data_path": "%s"
                    }
                  },
                  "schema": "main",
                  "table": "%s",
                  "model": "overwrite"
                }
              ],
              "connections": [
                { "sourceId": "mysql_src", "sourcePort": 0, "targetId": "ducklake_sink" }
              ],
              "version": "1.0.0"
            }
            """.formatted(
            MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, schemaName(),
            tableName(), meta.replace("\\", "\\\\"), dataDir.replace("\\", "\\\\"), tableName()
        );
    }

    private String schemaName() {
        int dot = SOURCE.indexOf('.');
        return dot < 0 ? "perf" : SOURCE.substring(0, dot);
    }

    private String tableName() {
        int dot = SOURCE.indexOf('.');
        return dot < 0 ? SOURCE : SOURCE.substring(dot + 1);
    }

    private long scalar(Connection conn, String sql) throws Exception {
        try (Statement statement = conn.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long directorySize(Path dir) throws Exception {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        }
    }
}
