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
 * PostgreSQL CDC 实时同步到 DuckLake 的端到端测试。
 *
 * <p>前置条件（本地 PostgreSQL）：{@code wal_level=logical}、账号具备 REPLICATION 权限；
 * 源表设置 {@code REPLICA IDENTITY FULL} 以提供完整变更前镜像（DuckLake 无主键，UPDATE/DELETE 依赖前镜像）。
 * DuckLake 使用本地文件元数据 + 本地数据目录，无需 S3。
 */
public class PostgresCdcToDuckLakeTest {

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = "admin";
    private static final String PG_PASSWORD = "123456";

    private static final String TABLE = "cdc_demo";
    private static final String PUBLICATION = "datay_pub_cdc_demo_lake";
    private static final String SLOT = "datay_slot_cdc_demo_lake";

    private static final String DUCKLAKE_META = "./target/cdc-pg.ducklake";
    private static final String DUCKLAKE_DATA_DIR = "./target/cdc-pg-ducklake-data";

    @Test
    public void testPostgresCdcToDuckLake() throws Exception {
        assumeTrue(supportsLogicalReplication(), "本地 PostgreSQL 未开启 wal_level=logical 或 admin 无 REPLICATION 权限，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/postgresCdcToDuckLake.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            awaitTrue(() -> replicationSlotActive() && publicationExists(), 30000, "等待 PostgreSQL 逻辑复制就绪超时");

            executePg("INSERT INTO public." + TABLE + " (id, name, amount, updated_at) VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00')");
            executePg("INSERT INTO public." + TABLE + " (id, name, amount, updated_at) VALUES (2, 'bob', 20.00, '2024-01-02 11:00:00')");
            awaitTrue(() -> queryCount() == 2, 30000, "等待 INSERT 同步到 DuckLake 超时");
            assertEquals("alice", queryString("select name from ducklake.main." + TABLE + " where id = 1"));
            assertEquals("10.50", queryString("select amount from ducklake.main." + TABLE + " where id = 1"));
            assertEquals(
                "2024-01-01 10:00:00",
                queryString("select cast(updated_at as varchar) from ducklake.main." + TABLE + " where id = 1")
            );

            executePg("UPDATE public." + TABLE + " SET name = 'alice2', amount = 11.11 WHERE id = 1");
            awaitTrue(() -> "alice2".equals(queryString("select name from ducklake.main." + TABLE + " where id = 1")), 30000, "等待 UPDATE 同步到 DuckLake 超时");
            assertEquals("11.11", queryString("select amount from ducklake.main." + TABLE + " where id = 1"));

            executePg("DELETE FROM public." + TABLE + " WHERE id = 2");
            awaitTrue(() -> queryCount() == 1, 30000, "等待 DELETE 同步到 DuckLake 超时");
            assertEquals("alice2", queryString("select name from ducklake.main." + TABLE + " where id = 1"));
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
            throw new AssertionError("CDC 同步任务异常结束: " + error.getMessage(), error);
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
        deleteDuckLakeFiles();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        dropPublicationAndSlot();
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS public." + TABLE);
            st.execute("CREATE TABLE public." + TABLE + " (id bigint primary key, name varchar(100), amount numeric(10,2), updated_at timestamp)");
            // DuckLake 无主键，UPDATE/DELETE 依赖完整变更前镜像
            st.execute("ALTER TABLE public." + TABLE + " REPLICA IDENTITY FULL");
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

    private void deleteDuckLakeFiles() throws Exception {
        detachDuckLake();
        new File("./target/cdc-pg.ducklake.wal").delete();
        new File(DUCKLAKE_META).delete();
        Path dataDir = java.nio.file.Paths.get(DUCKLAKE_DATA_DIR);
        if (Files.exists(dataDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(dataDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectories(dataDir);
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
    private void detachDuckLake() {
        try (Connection conn = DuckDBEngine.getInstance().getConnection(); Statement st = conn.createStatement()) {
            st.execute("DETACH ducklake");
        } catch (Exception ignored) {
        }
    }
}
