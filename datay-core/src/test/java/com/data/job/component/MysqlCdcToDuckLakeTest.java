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
 * MySQL CDC 实时同步到 DuckLake 的端到端测试。
 *
 * <p>前置条件（本地 MySQL）：开启 binlog ROW 且账号具备复制权限；存在 schema {@code test}。
 * DuckLake 使用本地文件元数据 + 本地数据目录，无需 S3。
 */
public class MysqlCdcToDuckLakeTest {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";

    private static final String TABLE = "cdc_demo";
    private static final String DUCKLAKE_META = "./target/cdc-mysql.ducklake";
    private static final String DUCKLAKE_DATA_DIR = "./target/cdc-mysql-ducklake-data";

    @Test
    public void testMysqlCdcToDuckLake() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/mysqlCdcToDuckLake.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时");

            executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00')");
            executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (2, 'bob', 20.00, '2024-01-02 11:00:00')");
            awaitTrue(() -> queryCount() == 2, 30000, "等待 INSERT 同步到 DuckLake 超时");
            assertEquals("alice", queryString("select name from ducklake.main." + TABLE + " where id = 1"));
            assertEquals("10.50", queryString("select amount from ducklake.main." + TABLE + " where id = 1"));
            assertEquals(
                "2024-01-01 10:00:00",
                queryString("select cast(updated_at as varchar) from ducklake.main." + TABLE + " where id = 1")
            );

            executeMysql("UPDATE test." + TABLE + " SET name = 'alice2', amount = 11.11 WHERE id = 1");
            awaitTrue(() -> "alice2".equals(queryString("select name from ducklake.main." + TABLE + " where id = 1")), 30000, "等待 UPDATE 同步到 DuckLake 超时");
            assertEquals("11.11", queryString("select amount from ducklake.main." + TABLE + " where id = 1"));

            executeMysql("DELETE FROM test." + TABLE + " WHERE id = 2");
            awaitTrue(() -> queryCount() == 1, 30000, "等待 DELETE 同步到 DuckLake 超时");
            assertEquals("alice2", queryString("select name from ducklake.main." + TABLE + " where id = 1"));
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
            st.execute("CREATE TABLE test." + TABLE + " (id bigint primary key, name varchar(100), amount decimal(10,2), updated_at datetime)");
        }
    }

    private void deleteDuckLakeFiles() throws Exception {
        detachDuckLake();
        new File("./target/cdc-mysql.ducklake.wal").delete();
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

    private int queryCount() {
        Integer count = queryInt("select count(*) from ducklake.main." + TABLE);
        return count == null ? -1 : count;
    }

    private Integer queryInt(String sql) {
        try (Connection conn = DuckDBEngine.getInstance().getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String queryString(String sql) {
        try (Connection conn = DuckDBEngine.getInstance().getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
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
