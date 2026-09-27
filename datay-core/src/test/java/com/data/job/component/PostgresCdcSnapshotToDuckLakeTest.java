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
 * PostgreSQL CDC 首次全量快照 → DuckLake 增量同步验证。
 */
public class PostgresCdcSnapshotToDuckLakeTest {

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = "admin";
    private static final String PG_PASSWORD = "123456";
    private static final String TABLE = "cdc_demo";
    private static final String PUBLICATION = "datay_pub_cdc_demo_snap_lake";
    private static final String SLOT = "datay_slot_cdc_demo_snap_lake";
    private static final String DUCKLAKE_META = "./target/cdc-pg-snapshot.ducklake";
    private static final String DUCKLAKE_DATA_DIR = "./target/cdc-pg-snapshot-ducklake-data";

    @Test
    public void testPostgresCdcSnapshotToDuckLake() throws Exception {
        assumeTrue(supportsLogicalReplication(), "本地 PostgreSQL 未开启 wal_level=logical 或 admin 无 REPLICATION 权限，跳过测试");
        resetEnvironment();

        executePg("INSERT INTO public." + TABLE + " (id, name, amount, updated_at) VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00')");
        executePg("INSERT INTO public." + TABLE + " (id, name, amount, updated_at) VALUES (2, 'bob', 20.00, '2024-01-02 11:00:00')");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/postgresCdcSnapshotToDuckLake.json")));
        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            awaitTrue(() -> replicationSlotActive() && publicationExists(), 30000, "等待 PostgreSQL 逻辑复制就绪超时");
            awaitTrue(() -> targetCount() == 2, 30000, "等待历史全量快照同步到 DuckLake 超时");
            assertEquals("alice", targetString("select name from ducklake.main." + TABLE + " where id = 1"));
            assertEquals("bob", targetString("select name from ducklake.main." + TABLE + " where id = 2"));

            executePg("INSERT INTO public." + TABLE + " (id, name, amount, updated_at) VALUES (3, 'carol', 30.00, '2024-01-03 12:00:00')");
            executePg("UPDATE public." + TABLE + " SET name = 'alice2' WHERE id = 1");
            executePg("DELETE FROM public." + TABLE + " WHERE id = 2");
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
            dropPublicationAndSlot();
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("CDC 快照任务异常结束: " + error.getMessage(), error);
        }
    }

    private boolean supportsLogicalReplication() {
        try (Connection conn = openPg()) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("show wal_level")) {
                if (!rs.next() || !"logical".equalsIgnoreCase(rs.getString(1))) {
                    return false;
                }
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select rolreplication from pg_roles where rolname = '" + PG_USER + "'")) {
                return rs.next() && rs.getBoolean(1);
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
        dropPublicationAndSlot();
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS public." + TABLE);
            st.execute("CREATE TABLE public." + TABLE + " (id bigint primary key, name varchar(100), amount numeric(10,2), updated_at timestamp)");
            st.execute("ALTER TABLE public." + TABLE + " REPLICA IDENTITY FULL");
        }
    }

    private void deleteDuckLakeFiles() throws Exception {
        new File("./target/cdc-pg-snapshot.ducklake.wal").delete();
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

    private void dropPublicationAndSlot() {
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute("DROP PUBLICATION IF EXISTS " + PUBLICATION);
            try (ResultSet rs = st.executeQuery("select 1 from pg_replication_slots where slot_name = '" + SLOT + "'")) {
                if (rs.next()) {
                    st.execute("select pg_drop_replication_slot('" + SLOT + "')");
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean publicationExists() {
        try (Connection conn = openPg(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select 1 from pg_publication where pubname = '" + PUBLICATION + "'")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean replicationSlotActive() {
        try (Connection conn = openPg(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select active from pg_replication_slots where slot_name = '" + SLOT + "'")) {
            return rs.next() && rs.getBoolean(1);
        } catch (Exception e) {
            return false;
        }
    }

    private void executePg(String sql) throws Exception {
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
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

    private Connection openPg() throws Exception {
        return DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
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
