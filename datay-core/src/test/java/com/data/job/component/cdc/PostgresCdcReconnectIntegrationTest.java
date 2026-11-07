package com.data.job.component.cdc;

import com.data.job.DatasourceInfo;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PostgreSQL 逻辑复制连接中断后自动重连的端到端验证。
 */
class PostgresCdcReconnectIntegrationTest {

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = "admin";
    private static final String PG_PASSWORD = "123456";
    private static final String TABLE = "cdc_reconnect_it";
    private static final String PUBLICATION = "datay_pub_cdc_reconnect_it";
    private static final String SLOT = "datay_slot_cdc_reconnect_it";

    @Test
    void shouldReconnectAndContinueAfterReplicationConnectionTerminated() throws Exception {
        assumeTrue(supportsLogicalReplication(), "本地 PostgreSQL 未开启 wal_level=logical 或 admin 无 REPLICATION 权限，跳过测试");
        resetEnvironment();

        BlockingQueue<BinlogEvent> events = new LinkedBlockingQueue<>();
        PostgresWalCollector collector = new PostgresWalCollector(
            datasource(),
            "public",
            PUBLICATION,
            SLOT,
            TABLE,
            null,
            handler(events)
        );
        collector.setReconnectMaxAttempts(5);
        collector.setReconnectIntervalMs(300);

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                collector.start();
            } catch (Throwable e) {
                error.set(e);
            }
        });

        try {
            worker.start();
            awaitTrue(this::replicationSlotActive, 30000, "等待 PostgreSQL 逻辑复制就绪超时");

            executePg("INSERT INTO public." + TABLE + " (id, name) VALUES (1, 'before')");
            assertNotNull(awaitEvent(events, 1L, 15000), "未收到断线前的 INSERT 事件");

            terminateReplicationBackend();
            executePg("INSERT INTO public." + TABLE + " (id, name) VALUES (2, 'after')");

            BinlogEvent reconnectedEvent = awaitEvent(events, 2L, 30000);
            assertNotNull(reconnectedEvent, "连接中断重连后未继续收到 INSERT 事件");
            assertTrue(collector.isRunning(), "重连后采集器应保持运行");
            assertTrue(error.get() == null, "重连过程不应以异常终止: " + error.get());
        } finally {
            collector.stop();
            worker.join(5000);
            cleanup();
        }
    }

    private BinlogEvent awaitEvent(BlockingQueue<BinlogEvent> events, long id, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            BinlogEvent event = events.poll(200, TimeUnit.MILLISECONDS);
            if (event == null || event.getAfterData() == null) {
                continue;
            }
            Object eventId = event.getAfterData().get("id");
            if (eventId != null && String.valueOf(id).equals(String.valueOf(eventId))) {
                return event;
            }
        }
        return null;
    }

    private void terminateReplicationBackend() throws Exception {
        try (Connection conn = openPg()) {
            Long pid = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT active_pid FROM pg_replication_slots WHERE slot_name = ?")) {
                ps.setString(1, SLOT);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pid = rs.getLong(1);
                    }
                }
            }
            assertNotNull(pid, "逻辑复制槽没有活跃的 walsender 进程");
            try (PreparedStatement ps = conn.prepareStatement("SELECT pg_terminate_backend(?)")) {
                ps.setInt(1, pid.intValue());
                ps.execute();
            }
        }
    }

    private boolean replicationSlotActive() {
        try (Connection conn = openPg(); PreparedStatement ps = conn.prepareStatement(
            "SELECT active FROM pg_replication_slots WHERE slot_name = ?"
        )) {
            ps.setString(1, SLOT);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean supportsLogicalReplication() {
        try (Connection conn = openPg()) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("show wal_level")) {
                if (!rs.next() || !"logical".equalsIgnoreCase(rs.getString(1))) {
                    return false;
                }
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                "select rolreplication from pg_roles where rolname = '" + PG_USER + "'"
            )) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void resetEnvironment() throws Exception {
        cleanup();
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS public." + TABLE);
            st.execute("CREATE TABLE public." + TABLE + " (id bigint primary key, name varchar(100))");
            st.execute("ALTER TABLE public." + TABLE + " REPLICA IDENTITY FULL");
        }
    }

    private void cleanup() {
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute("DROP PUBLICATION IF EXISTS " + PUBLICATION);
            try (ResultSet rs = st.executeQuery("select 1 from pg_replication_slots where slot_name = '" + SLOT + "'")) {
                if (rs.next()) {
                    st.execute("select pg_drop_replication_slot('" + SLOT + "')");
                }
            }
            st.execute("DROP TABLE IF EXISTS public." + TABLE);
        } catch (Exception ignored) {
        }
    }

    private void executePg(String sql) throws Exception {
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private Connection openPg() throws Exception {
        return DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
    }

    private DatasourceInfo datasource() {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl(PG_URL);
        datasource.setUsername(PG_USER);
        datasource.setPassword(PG_PASSWORD);
        datasource.setDbschema("public");
        return datasource;
    }

    private BinlogEventHandler handler(BlockingQueue<BinlogEvent> events) {
        return new BinlogEventHandler() {
            @Override
            public void handleEvent(BinlogEvent event) {
                events.add(event);
            }

            @Override
            public void handleError(BinlogEvent event, Exception exception) {}
        };
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        fail(message);
    }
}
