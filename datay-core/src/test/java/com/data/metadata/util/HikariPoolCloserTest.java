package com.data.metadata.util;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HikariPoolCloserTest {

    @Test
    @Timeout(10)
    void closeCompletesWhenThreadIsInterruptedDuringClosing() throws Exception {
        InterruptSensitiveDataSource dataSource = new InterruptSensitiveDataSource();
        Thread currentThread = Thread.currentThread();
        ExecutorService interrupter = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hikari-pool-closer-test-interrupter");
            thread.setDaemon(true);
            return thread;
        });

        Future<?> interruptFuture = interrupter.submit(() -> {
            if (dataSource.closingStarted.await(5, TimeUnit.SECONDS)) {
                Thread.sleep(50);
                currentThread.interrupt();
            }
            return null;
        });

        try {
            HikariPoolCloser.closeQuietly(dataSource);

            assertTrue(dataSource.closeCompleted.get(), "关闭过程中收到中断时也应完整关闭连接池");
            assertTrue(Thread.currentThread().isInterrupted(), "关闭完成后应恢复当前线程的中断标记");
        } finally {
            Thread.interrupted();
            interruptFuture.cancel(true);
            interrupter.shutdownNow();
        }
    }

    /**
     * 模拟 HikariCP 在关闭等待期间被中断就提前返回的行为。
     */
    private static class InterruptSensitiveDataSource extends HikariDataSource {

        private final CountDownLatch closingStarted = new CountDownLatch(1);

        private final AtomicBoolean closeCompleted = new AtomicBoolean(false);

        @Override
        public String getPoolName() {
            return "interrupt-sensitive";
        }

        @Override
        public boolean isClosed() {
            return closeCompleted.get();
        }

        @Override
        public void close() {
            closingStarted.countDown();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                return;
            }
            closeCompleted.set(true);
        }
    }
}
