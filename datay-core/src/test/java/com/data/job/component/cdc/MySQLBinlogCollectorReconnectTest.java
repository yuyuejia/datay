package com.data.job.component.cdc;

import com.data.job.DatasourceInfo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Binlog 连接异常时的重连与失败语义。
 */
class MySQLBinlogCollectorReconnectTest {

    @Test
    void shouldFailAfterExceedingMaxReconnectAttempts() {
        MySQLBinlogCollector collector = newCollector();
        collector.setReconnectMaxAttempts(2);
        collector.setReconnectIntervalMs(20);

        RuntimeException error = assertThrows(RuntimeException.class, collector::start);

        assertTrue(error.getMessage().contains("最大重连次数 2"), error.getMessage());
        assertFalse(collector.isRunning(), "重试耗尽后采集器不应保持运行状态");
    }

    @Test
    void stopDuringReconnectWaitShouldNotThrow() throws Exception {
        MySQLBinlogCollector collector = newCollector();
        collector.setReconnectMaxAttempts(100);
        collector.setReconnectIntervalMs(5000);

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                collector.start();
            } catch (Throwable e) {
                error.set(e);
            }
        });
        thread.start();

        Thread.sleep(300);
        collector.stop();
        thread.join(5000);

        assertFalse(thread.isAlive(), "停止后重连循环应尽快退出");
        assertTrue(error.get() == null, "手动停止不应以失败异常结束: " + error.get());
        assertFalse(collector.isRunning());
    }

    private MySQLBinlogCollector newCollector() {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl("jdbc:mysql://127.0.0.1:1/test");
        datasource.setUsername("root");
        datasource.setPassword("");
        return new MySQLBinlogCollector(
            datasource,
            new BinlogEventHandler() {
                @Override
                public void handleEvent(BinlogEvent event) {}

                @Override
                public void handleError(BinlogEvent event, Exception exception) {}
            }
        );
    }
}
