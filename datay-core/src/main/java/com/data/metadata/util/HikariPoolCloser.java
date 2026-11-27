package com.data.metadata.util;

import com.zaxxer.hikari.HikariDataSource;

/**
 * HikariCP 连接池关闭工具。
 *
 * <p>超时或手动停止任务时，任务执行线程可能带有中断标记，甚至会在清理过程中再次被中断。
 * HikariCP 的关闭流程会等待内部线程池结束，等待期间一旦收到中断就会立即抛出
 * {@link InterruptedException} 并提前返回（日志表现为 “Interrupted during closing”），
 * 导致 housekeeper 线程残留，且连接池已标记为关闭，无法再次调用 {@code close()} 补救。
 *
 * <p>因此关闭动作放到独立的守护线程中执行，当前线程只负责等待该线程结束；等待期间
 * 忽略中断，待关闭线程真正结束后再恢复当前线程的中断标记。这样既能保证清理完整执行，
 * 也不会吞掉任务取消/超时的中断语义。
 */
public final class HikariPoolCloser {

    private HikariPoolCloser() {}

    /**
     * 关闭连接池。清理过程不会被当前线程的中断标记打断。
     */
    public static void closeQuietly(HikariDataSource dataSource) {
        if (dataSource == null || dataSource.isClosed()) {
            return;
        }

        String poolName = dataSource.getPoolName();
        boolean interrupted = Thread.interrupted();
        Thread closer = new Thread(() -> {
            try {
                dataSource.close();
            } catch (Exception e) {
                System.err.println("Failed to close connection pool " + poolName + ": " + e.getMessage());
            }
        }, "hikari-pool-close");
        closer.setDaemon(true);
        closer.start();

        while (closer.isAlive()) {
            try {
                closer.join();
            } catch (InterruptedException e) {
                // 清理必须完成，忽略等待期间的中断，结束后统一恢复中断标记。
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
