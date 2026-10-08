package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySQL CDC 实时同步到 DuckDB 的端到端测试。
 *
 * <p>前置条件（本地 MySQL）：
 * <ul>
 *   <li>{@code log_bin=ON}、{@code binlog_format=ROW}；</li>
 *   <li>账号具备 {@code REPLICATION SLAVE}/{@code REPLICATION CLIENT} 权限；</li>
 *   <li>存在 schema {@code test}。</li>
 * </ul>
 * 不满足时测试自动跳过。
 */
public class MysqlCdcToDuckDBTest {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String DUCK_URL = "jdbc:duckdb:./target/mysql-cdc-duckdb-test.db";

    private static final String TABLE = "cdc_demo";

    /** 与 mysqlCdcToDuckDB.json 中配置的 serverId 保持一致。 */
    private static final long SERVER_ID = 2233L;

    private static final Logger CONNECTOR_LOG = Logger.getLogger("com.github.shyiko.mysql.binlog.BinaryLogClient");
    private final List<String> connectorMessages = new CopyOnWriteArrayList<>();
    private final Handler connectorHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getMessage() != null) {
                connectorMessages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @Test
    public void testMysqlCdcToDuckDB() throws Exception {
        assumeTrue(supportsRowBinlog(), "本地 MySQL 未开启 binlog ROW 格式或缺少复制权限，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/mysqlCdcToDuckDB.json")));

        CONNECTOR_LOG.setLevel(Level.ALL);
        CONNECTOR_LOG.addHandler(connectorHandler);
        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            awaitTrue(this::binlogDumpActive, 30000, "等待 MySQL Binlog 采集器就绪超时");
            awaitTrue(
                () -> connectorMessages.stream().anyMatch(message -> message.contains("sid:" + SERVER_ID)),
                30000,
                "等待组件以配置的 server-id=" + SERVER_ID + " 连接 MySQL 超时"
            );

            // INSERT
            executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00')");
            executeMysql("INSERT INTO test." + TABLE + " (id, name, amount, updated_at) VALUES (2, 'bob', 20.00, '2024-01-02 11:00:00')");
            awaitTrue(() -> queryDuckCount() == 2, 30000, "等待 INSERT 同步到 DuckDB 超时");
            assertEquals("alice", queryDuckString("select name from main." + TABLE + " where id = 1"));
            assertEquals("10.50", queryDuckString("select amount from main." + TABLE + " where id = 1"));
            assertEquals(
                "2024-01-01 10:00:00",
                queryDuckString("select cast(updated_at as varchar) from main." + TABLE + " where id = 1")
            );

            // UPDATE
            executeMysql("UPDATE test." + TABLE + " SET name = 'alice2', amount = 11.11 WHERE id = 1");
            awaitTrue(() -> "alice2".equals(queryDuckString("select name from main." + TABLE + " where id = 1")), 30000, "等待 UPDATE 同步到 DuckDB 超时");
            assertEquals("11.11", queryDuckString("select amount from main." + TABLE + " where id = 1"));

            // DELETE
            executeMysql("DELETE FROM test." + TABLE + " WHERE id = 2");
            awaitTrue(() -> queryDuckCount() == 1, 30000, "等待 DELETE 同步到 DuckDB 超时");
            assertEquals("alice2", queryDuckString("select name from main." + TABLE + " where id = 1"));
        } finally {
            CONNECTOR_LOG.removeHandler(connectorHandler);
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
        closeDuck();
        deleteDuckFile();
        // 清除上一次运行保存的增量位点，确保从当前 Binlog 位置开始，避免重放历史 DDL
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        try (Connection conn = openMysql(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS test." + TABLE);
            st.execute("CREATE TABLE test." + TABLE + " (id bigint primary key, name varchar(100), amount decimal(10,2), updated_at datetime)");
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

    private Connection duckConnection() throws Exception {
        DatasourceInfo duck = new DatasourceInfo();
        duck.setUrl(DUCK_URL);
        return DBUtils.getConnection(duck);
    }

    private void closeDuck() {
        try {
            DBUtils.closePool(DUCK_URL, null);
        } catch (Exception ignored) {
        }
    }

    private void deleteDuckFile() {
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File("./target/mysql-cdc-duckdb-test.db" + suffix);
            if (file.exists()) {
                file.delete();
            }
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
}
