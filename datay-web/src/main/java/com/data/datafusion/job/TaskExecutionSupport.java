package com.data.datafusion.job;

import com.data.datafusion.domain.JobInstance;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 任务执行公共支撑：按任务配置执行重试和超时控制。
 *
 * <p>重试次数表示首次执行之外的最大重试次数，0 表示不重试；超时时间单位为秒，
 * 每次尝试单独计时，0 表示不限制。仅 {@code FAILED}、{@code TIMEOUT} 会自动重试，
 * 手动停止（{@code INTERRUPTED}）和成功不会重试。
 */
public final class TaskExecutionSupport {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionSupport.class);

    private static final long DEFAULT_RETRY_INTERVAL_MILLIS = 10_000L;

    private static final ScheduledExecutorService TIMEOUT_WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "task-timeout-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 重试间隔，默认 10 秒。测试场景可调小以缩短用例执行时间。
     */
    static long retryIntervalMillis = DEFAULT_RETRY_INTERVAL_MILLIS;

    private TaskExecutionSupport() {}

    /**
     * 按任务实例上的重试/超时配置执行任务。
     *
     * @param jobInstance   任务实例（重试、超时配置来源）
     * @param taskFactory   每次尝试创建全新的任务执行器
     * @param onTaskCreated 每次尝试创建任务后回调，便于外部停止当前尝试
     * @param stopRequested 外部是否已请求停止
     * @return 最终任务状态
     */
    public static String execute(
        JobInstance jobInstance,
        Supplier<ITask> taskFactory,
        Consumer<ITask> onTaskCreated,
        BooleanSupplier stopRequested
    ) {
        int maxRetryCount = normalizeRetryCount(jobInstance);
        int timeoutSeconds = normalizeTimeoutSeconds(jobInstance);
        BooleanSupplier stopped = stopRequested == null ? () -> false : stopRequested;

        String lastStatus = TaskConstants.TASK_STATUS_FAILED;
        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            if (stopped.getAsBoolean()) {
                Thread.interrupted();
                return TaskConstants.TASK_STATUS_INTERRUPTED;
            }

            ITask task = taskFactory.get();
            if (onTaskCreated != null) {
                onTaskCreated.accept(task);
            }
            // 创建任务后再次检查停止标记，避免停止请求恰好发生在尝试切换期间。
            if (stopped.getAsBoolean()) {
                try {
                    task.cancel();
                } catch (Exception e) {
                    log.warn("停止任务时发生异常", e);
                }
                Thread.interrupted();
                return TaskConstants.TASK_STATUS_INTERRUPTED;
            }

            int attemptNumber = attempt + 1;
            int totalAttempts = maxRetryCount + 1;
            log.info(
                "任务开始执行: instanceCode={}, attempt={}/{}, timeoutSeconds={}",
                jobInstance == null ? null : jobInstance.getInstanceCode(),
                attemptNumber,
                totalAttempts,
                timeoutSeconds
            );
            logTask(task, "开始执行（第 " + attemptNumber + "/" + totalAttempts + " 次尝试）");

            lastStatus = executeAttempt(task, timeoutSeconds);

            if (TaskConstants.TASK_STATUS_SUCCESSFUL.equals(lastStatus)) {
                return lastStatus;
            }
            if (TaskConstants.TASK_STATUS_INTERRUPTED.equals(lastStatus)) {
                log.info(
                    "任务已停止，不再重试: instanceCode={}, attempt={}",
                    jobInstance == null ? null : jobInstance.getInstanceCode(),
                    attemptNumber
                );
                // 清理停止时可能残留的中断标记，避免影响线程池中的后续任务。
                Thread.interrupted();
                return lastStatus;
            }

            if (attempt >= maxRetryCount) {
                break;
            }

            log.warn(
                "任务执行失败，将在 {} 毫秒后重试: instanceCode={}, attempt={}/{}, status={}",
                retryIntervalMillis,
                jobInstance == null ? null : jobInstance.getInstanceCode(),
                attemptNumber,
                totalAttempts,
                lastStatus
            );
            logTask(
                task,
                "执行失败（状态：" + lastStatus + "），" + (retryIntervalMillis / 1000) + " 秒后进行第 " + (attemptNumber + 1) + " 次尝试"
            );

            if (!sleepBeforeRetry(stopped)) {
                Thread.interrupted();
                return TaskConstants.TASK_STATUS_INTERRUPTED;
            }
        }

        return lastStatus;
    }

    private static String executeAttempt(ITask task, int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return safeExecute(task);
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Thread worker = Thread.currentThread();

        ScheduledFuture<?> watchdog = TIMEOUT_WATCHDOG.schedule(
            () -> {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                timedOut.set(true);
                log.warn("任务执行超时（{} 秒），正在停止当前尝试", timeoutSeconds);
                try {
                    task.cancel();
                } catch (Exception e) {
                    log.warn("超时停止任务时发生异常", e);
                }
                worker.interrupt();
            },
            timeoutSeconds,
            TimeUnit.SECONDS
        );

        try {
            String status = safeExecute(task);
            finished.set(true);
            if (timedOut.get()) {
                Thread.interrupted();
                return TaskConstants.TASK_STATUS_TIMEOUT;
            }
            return status;
        } finally {
            watchdog.cancel(false);
            if (timedOut.get()) {
                Thread.interrupted();
            }
        }
    }

    private static String safeExecute(ITask task) {
        try {
            return task.execute();
        } catch (Exception e) {
            log.error("任务执行异常", e);
            return TaskConstants.TASK_STATUS_FAILED;
        }
    }

    /**
     * 等待重试间隔。等待期间收到停止请求或线程被中断时返回 false。
     */
    private static boolean sleepBeforeRetry(BooleanSupplier stopRequested) {
        long remaining = retryIntervalMillis;
        try {
            while (remaining > 0) {
                if (stopRequested.getAsBoolean()) {
                    return false;
                }
                long sleepMillis = Math.min(remaining, 1000L);
                Thread.sleep(sleepMillis);
                remaining -= sleepMillis;
            }
            return !stopRequested.getAsBoolean();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int normalizeRetryCount(JobInstance jobInstance) {
        Integer configured = jobInstance == null ? null : jobInstance.getRetryCount();
        if (configured == null || configured < 0) {
            return 0;
        }
        return Math.min(configured, TaskConstants.MAX_RETRY_COUNT);
    }

    private static int normalizeTimeoutSeconds(JobInstance jobInstance) {
        Integer configured = jobInstance == null ? null : jobInstance.getTimeoutSeconds();
        if (configured == null || configured < 0) {
            return 0;
        }
        return Math.min(configured, TaskConstants.MAX_TIMEOUT_SECONDS);
    }

    private static void logTask(ITask task, String message) {
        if (task instanceof AbstractTask abstractTask) {
            try {
                abstractTask.log(message);
            } catch (Exception e) {
                log.debug("写入任务日志失败: {}", e.getMessage());
            }
        }
    }
}
