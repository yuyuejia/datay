package com.data.job.component.cdc;

import com.data.job.DatasourceInfo;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 PostgreSQL / MongoDB / Oracle CDC 采集器的重连与停止语义。
 */
class CdcCollectorReconnectTest {

    @Test
    void postgresShouldFailAfterExceedingMaxReconnectAttempts() {
        PostgresWalCollector collector = newPostgresCollector();
        collector.setReconnectMaxAttempts(1);
        collector.setReconnectIntervalMs(10);

        CdcConnectionException error = assertThrows(CdcConnectionException.class, collector::start);

        assertTrue(error.getMessage().contains("最大重连次数 1"), error.getMessage());
        assertFalse(collector.isRunning(), "重试耗尽后采集器不应保持运行状态");
    }

    @Test
    void mongoShouldFailAfterExceedingMaxReconnectAttempts() {
        MongoChangeStreamCollector collector = newMongoCollector();
        collector.setReconnectMaxAttempts(1);
        collector.setReconnectIntervalMs(10);

        CdcConnectionException error = assertThrows(CdcConnectionException.class, collector::start);

        assertTrue(error.getMessage().contains("最大重连次数 1"), error.getMessage());
        assertFalse(collector.isRunning(), "重试耗尽后采集器不应保持运行状态");
    }

    @Test
    void oracleShouldFailAfterExceedingMaxReconnectAttempts() {
        OracleLogMinerCollector collector = newOracleCollector();
        collector.setReconnectMaxAttempts(1);
        collector.setReconnectIntervalMs(10);

        CdcConnectionException error = assertThrows(CdcConnectionException.class, collector::start);

        assertTrue(error.getMessage().contains("最大重连次数 1"), error.getMessage());
        assertFalse(collector.isRunning(), "重试耗尽后采集器不应保持运行状态");
    }

    @Test
    void postgresStopDuringReconnectWaitShouldNotThrow() throws Exception {
        PostgresWalCollector collector = newPostgresCollector();
        assertStopDuringReconnectWait(collector::start, collector::stop, collector::isRunning, collector::setReconnectMaxAttempts, collector::setReconnectIntervalMs);
    }

    @Test
    void mongoStopDuringReconnectWaitShouldNotThrow() throws Exception {
        MongoChangeStreamCollector collector = newMongoCollector();
        assertStopDuringReconnectWait(collector::start, collector::stop, collector::isRunning, collector::setReconnectMaxAttempts, collector::setReconnectIntervalMs);
    }

    @Test
    void oracleStopDuringReconnectWaitShouldNotThrow() throws Exception {
        OracleLogMinerCollector collector = newOracleCollector();
        assertStopDuringReconnectWait(collector::start, collector::stop, collector::isRunning, collector::setReconnectMaxAttempts, collector::setReconnectIntervalMs);
    }

    @Test
    void shouldClassifyConnectionFailures() {
        assertTrue(CdcConnectionErrors.isConnectionFailure(new SQLException("connection closed", "08006")));
        assertTrue(CdcConnectionErrors.isConnectionFailure(new SQLException("server shutdown", "57P01")));
        assertTrue(CdcConnectionErrors.isConnectionFailure(new SQLException("role does not exist", "28000")));
        assertTrue(CdcConnectionErrors.isConnectionFailure(new SQLException("database does not exist", "3D000")));
        assertTrue(CdcConnectionErrors.isConnectionFailure(new ConnectException("Connection refused")));
        assertTrue(CdcConnectionErrors.isConnectionFailure(new SQLException("invalid password", "28P01")));
        assertFalse(CdcConnectionErrors.isConnectionFailure(new SQLException("syntax error", "42601")));
        assertFalse(CdcConnectionErrors.isConnectionFailure(new IllegalStateException("未找到匹配的表")));
    }

    private void assertStopDuringReconnectWait(
        Runnable start,
        Runnable stop,
        BooleanSupplier isRunning,
        IntConsumer setReconnectMaxAttempts,
        LongConsumer setReconnectIntervalMs
    ) throws Exception {
        setReconnectMaxAttempts.accept(100);
        setReconnectIntervalMs.accept(5000);

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                start.run();
            } catch (Throwable e) {
                error.set(e);
            }
        });
        thread.start();

        Thread.sleep(500);
        stop.run();
        thread.join(5000);

        assertFalse(thread.isAlive(), "停止后重连循环应尽快退出");
        assertTrue(error.get() == null, "手动停止不应以失败异常结束: " + error.get());
        assertFalse(isRunning.getAsBoolean());
    }

    private PostgresWalCollector newPostgresCollector() {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl("jdbc:postgresql://127.0.0.1:1/test?connectTimeout=1&socketTimeout=1");
        datasource.setUsername("postgres");
        datasource.setPassword("");
        return new PostgresWalCollector(datasource, "public", "datay_pub_test", "datay_slot_test", null, null, new NoopHandler());
    }

    private MongoChangeStreamCollector newMongoCollector() {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl("mongodb://127.0.0.1:1/test?serverSelectionTimeoutMS=300&connectTimeoutMS=300&socketTimeoutMS=300");
        datasource.setDbschema("test");
        return new MongoChangeStreamCollector(datasource, "test", "cdc_demo", null, null, 1000, null, new NoopHandler());
    }

    private OracleLogMinerCollector newOracleCollector() {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl("jdbc:oracle:thin:@//127.0.0.1:1/FREEPDB1?oracle.net.CONNECT_TIMEOUT=1000&oracle.jdbc.ReadTimeout=1000");
        datasource.setUsername("datay");
        datasource.setPassword("datay123");
        return new OracleLogMinerCollector(datasource, "DATAY", null, null, 500, new NoopHandler());
    }

    /** 测试用事件处理器，不关心采集到的事件内容。 */
    private static class NoopHandler implements BinlogEventHandler {

        @Override
        public void handleEvent(BinlogEvent event) {}

        @Override
        public void handleError(BinlogEvent event, Exception exception) {}
    }
}
