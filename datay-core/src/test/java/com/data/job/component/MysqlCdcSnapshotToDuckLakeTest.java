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
 * MySQL CDC 首次全量快照 → DuckLake 增量同步验证。
 */
public class MysqlCdcSnapshotToDuckLakeTest {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String TABLE = "cdc_demo";
    private static final String DUCKLAKE_META = "./target/cdc-mysql-snapshot.ducklake";
    private static final String DUCKLAKE_DATA_DIR = "./target/cdc-mysql-snapshot-ducklake-data";

    @Test
    public void testMysqlCdcSnapshotToDuckLake() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        resetEnvironment();

        executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00')");
        executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (2, 'bob', 20.00, '2024-01-02 11:00:00')");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/mysqlCdcSnapshotToDuckLake.json")));
        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时");
            awaitTrue(() -> targetCount() == 2, 30000, "等待历史全量快照同步到 DuckLake 超时");
            assertEquals("alice", targetString("select name from ducklake.main." + TABLE + " where id = 1"));
            assertEquals("bob", targetString("select name from ducklake.main." + TABLE + " where id = 2"));

            executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (3, 'carol', 30.00, '2024-01-03 12:00:00')");
            executeMysql("UPDATE test." + TABLE + " SET name = 'alice2' WHERE id = 1");
            executeMysql("DELETE FROM test." + TABLE + " WHERE id = 2");
            awaitTrue(
                () ->
                    targetCount() == 2 &&
                    "alice2".equals(targetString("select name from ducklake.main." + TABLE + " where id = 1")) &&
                    "carol".equals(targetString("select name from ducklake.main." + TABLE + " where id = 3")),
                30000,
                "等待增量同步到 DuckLake 超时"
            );
            assertEquals("alice2", targetString("select name from ducklake.main." + TABLE + " where id = 1"));
            assertEquals("carol", targetString("select name from ducklake.main." + TABLE + " where id = 3"));
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
            throw new AssertionError("CDC 快照任务异常结束: " + error.getMessage(), error);
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
        detachDuckLake();
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
        new File("./target/cdc-mysql-snapshot.ducklake.wal").delete();
        new File(DUCKLAKE_META).delete();
        Path dataDir = java.nio.file.Paths.get(DUCKLAKE_DATA_DIR);
        if (Files.exists(dataDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(dataDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectories(dataDir);
    }

    private void detachDuckLake() {
        try (Connection conn = DuckDBEngine.getInstance().getConnection(); Statement st = conn.createStatement()) {
            st.execute("DETACH ducklake");
        } catch (Exception ignored) {
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

    private int targetCount() {
        return targetInt("select count(*) from ducklake.main." + TABLE);
    }

    private int targetInt(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private String targetString(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Connection duckConnection() throws Exception {
        return DuckDBEngine.getInstance().getConnection();
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
}
