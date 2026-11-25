package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.metadata.util.DBUtils;
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
 * 真实 MySQL → DuckDB 多并发读取 + 多并发写入性能验证（默认不执行）。
 *
 * <p>启用方式：</p>
 * <pre>
 *   -Dperf.mysql.enabled=true
 *   -Dperf.mysql.url=jdbc:mysql://127.0.0.1:3306
 *   -Dperf.mysql.user=root
 *   -Dperf.mysql.password=YOUR_PASSWORD
 *   -Dperf.mysql.table=perf.big_sync   # 默认 4,000,000 行
 *   -Dperf.mysql.rows=4000000
 *   -Dperf.read.parallelism=8          # 读并发
 *   -Dperf.write.parallelism=8         # 写并发
 *   -Dperf.queue.capacity=32           # 连线缓冲队列（批次数）
 * </pre>
 *
 * <p>源表按单列主键（id）自动分区，每个读线程只读取自己的区间；每个写线程从共享队列
 * 消费 FlowFile 并并发写入 DuckDB。测试结束后校验行数、去重和主键求和，并打印吞吐。</p>
 */
public class MysqlToDuckDBConcurrencyPerformanceTest {

    private static final String MYSQL_URL = System.getProperty("perf.mysql.url", "jdbc:mysql://127.0.0.1:3306");
    private static final String MYSQL_USER = System.getProperty("perf.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("perf.mysql.password", "");
    private static final String SOURCE = System.getProperty("perf.mysql.table", "perf.big_sync");
    private static final long EXPECTED_ROWS = Long.getLong("perf.mysql.rows", 4_000_000L);
    private static final int READ_PARALLELISM = Integer.getInteger("perf.read.parallelism", 8);
    private static final int WRITE_PARALLELISM = Integer.getInteger("perf.write.parallelism", 8);
    private static final int QUEUE_CAPACITY = Integer.getInteger("perf.queue.capacity", 32);

    @Test
    @Timeout(3_600)
    public void syncMysqlToDuckDbWithConcurrentReadWrite() throws Exception {
        Assumptions.assumeTrue(
            Boolean.getBoolean("perf.mysql.enabled"),
            "设置 -Dperf.mysql.enabled=true 运行真实 MySQL→DuckDB 多并发性能验证"
        );

        String mysqlUrl = mysqlJdbcUrl();
        long sourceRows;
        long sourceSumId;
        long sourceSumQty;
        try (Connection conn = DriverManager.getConnection(mysqlUrl, MYSQL_USER, MYSQL_PASSWORD)) {
            sourceRows = scalar(conn, "SELECT COUNT(*) FROM " + SOURCE);
            sourceSumId = scalar(conn, "SELECT COALESCE(SUM(id), 0) FROM " + SOURCE);
            sourceSumQty = scalar(conn, "SELECT COALESCE(SUM(qty), 0) FROM " + SOURCE);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "MySQL 不可用或源表不存在，跳过: " + e.getMessage());
            return;
        }
        Assumptions.assumeTrue(
            sourceRows == EXPECTED_ROWS,
            "源表 " + SOURCE + " 行数为 " + sourceRows + "，与期望 " + EXPECTED_ROWS + " 不一致，跳过"
        );

        Path base = Path.of(System.getProperty("user.dir"), "target", "perf",
            "mysql-duckdb-" + UUID.randomUUID());
        Files.createDirectories(base);
        Path duckdbFile = base.resolve("target.duckdb").toAbsolutePath();
        String targetUrl = "jdbc:duckdb:" + duckdbFile;
        String targetTable = tableName();

        String instanceCode = "mysql-duckdb-perf-" + UUID.randomUUID();
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setInstanceCode(instanceCode);
        taskInstance.setJobCode("mysql-duckdb-perf");

        ETLFlowTask runner = new ETLFlowTask(taskInstance, null);
        long start = System.nanoTime();
        runner.runJob(jobJson(targetUrl, targetTable));
        double seconds = (System.nanoTime() - start) / 1e9;

        long targetRows;
        long targetDistinctIds;
        long targetSumId;
        long targetSumQty;
        // 复用 StreamJdbcOutput 的 DuckDB 连接池配置，避免同一文件以不同配置重复打开。
        DatasourceInfo targetDs = new DatasourceInfo();
        targetDs.setUrl(targetUrl);
        targetDs.setDriver("org.duckdb.DuckDBDriver");
        targetDs.setDbschema("main");
        try (Connection conn = DBUtils.getConnection(targetDs)) {
            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery(
                     "SELECT count(*), count(DISTINCT id), COALESCE(sum(id), 0), COALESCE(sum(qty), 0) FROM main."
                         + targetTable)) {
                rs.next();
                targetRows = rs.getLong(1);
                targetDistinctIds = rs.getLong(2);
                targetSumId = rs.getLong(3);
                targetSumQty = rs.getLong(4);
            }
        }

        assertEquals(EXPECTED_ROWS, targetRows, "DuckDB 行数应与源表一致");
        assertEquals(EXPECTED_ROWS, targetDistinctIds, "DuckDB 主键应无重复");
        assertEquals(sourceSumId, targetSumId, "DuckDB 主键求和应与源表一致");
        assertEquals(sourceSumQty, targetSumQty, "DuckDB qty 求和应与源表一致");

        long outputBytes = fileSize(duckdbFile);
        double rowsPerSecond = targetRows / seconds;
        System.out.printf(
            "%n[MySQL→DuckDB 并发性能] 源表=%s 行数=%d 读并发=%d 写并发=%d 队列=%d 耗时=%.3f s 吞吐=%.0f 行/s%n",
            SOURCE, targetRows, READ_PARALLELISM, WRITE_PARALLELISM, QUEUE_CAPACITY, seconds, rowsPerSecond
        );
        System.out.printf(
            "[MySQL→DuckDB 输出] 文件=%.1f MB 去重=%d sum(id)=%d sum(qty)=%d 目标=%s%n%n",
            outputBytes / 1024.0 / 1024.0, targetDistinctIds, targetSumId, targetSumQty, duckdbFile
        );

        assertTrue(seconds > 0, "耗时异常");
    }

    private String jobJson(String targetUrl, String targetTable) {
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
                  "incrColumn": "",
                  "where": "",
                  "parallelism": "%d"
                },
                {
                  ".id": "duckdb_sink",
                  ".name": "StreamJdbcOutput",
                  "sourceId": {
                    "url": "%s",
                    "driver": "org.duckdb.DuckDBDriver",
                    "dbschema": "main"
                  },
                  "schema": "main",
                  "table": "%s",
                  "model": "append",
                  "parallelism": "%d"
                }
              ],
              "connections": [
                {
                  "sourceId": "mysql_src",
                  "sourcePort": 0,
                  "targetId": "duckdb_sink",
                  "queueCapacity": %d
                }
              ],
              "version": "1.0.0"
            }
            """.formatted(
            mysqlJdbcUrl(), MYSQL_USER, MYSQL_PASSWORD, schemaName(),
            tableName(), READ_PARALLELISM,
            targetUrl.replace("\\", "\\\\"), targetTable, WRITE_PARALLELISM,
            QUEUE_CAPACITY
        );
    }

    private String mysqlJdbcUrl() {
        if (MYSQL_URL.contains("?")) {
            return MYSQL_URL + "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        }
        return MYSQL_URL + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
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

    private long fileSize(Path file) throws Exception {
        try (Stream<Path> paths = Files.walk(file)) {
            return paths.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        }
    }
}
