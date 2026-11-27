package com.data.datafusion.job.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.ITask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.TaskExecutionSupport;
import com.data.job.DuckDBEngine;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 验证 datay-web 的超时/手动停止路径下，ETL 任务仍会释放 DuckDB 实例连接池。
 */
class EtlTaskDuckDBPoolCleanupTest {

    private static final String LONG_RUNNING_JOB = """
        {
          "units": [
            {".id": "gen", ".name": "GenerateFlowFile", "dataFormat": "JSON_ARRAY",
             "inputData": "[{\\"id\\":1,\\"name\\":\\"alice\\"}]", "loopCount": "1000", "waitTime": "200"},
            {".id": "sink", ".name": "DuckDBWrite", "table": "t", "model": "append"}
          ],
          "connections": [
            {"sourceId": "gen", "targetId": "sink", "sourcePort": 0}
          ]
        }
        """;

    @Test
    @Timeout(60000)
    void timeoutClosesDuckDBPool() throws Exception {
        String instanceCode = "web-duckdb-timeout-" + System.nanoTime();
        String dbFile = DuckDBEngine.getDBFile(instanceCode);
        JobInstance jobInstance = jobInstance(instanceCode);
        jobInstance.setTimeoutSeconds(1);
        jobInstance.setRetryCount(0);

        try {
            String status = TaskExecutionSupport.execute(
                jobInstance,
                () -> new ETLTask(jobInstance),
                null,
                () -> false
            );

            assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_TIMEOUT);
            assertPoolClosed(dbFile);
        } finally {
            Thread.interrupted();
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    @Timeout(60000)
    void manualStopClosesDuckDBPool() throws Exception {
        String instanceCode = "web-duckdb-manual-stop-" + System.nanoTime();
        String dbFile = DuckDBEngine.getDBFile(instanceCode);
        JobInstance jobInstance = jobInstance(instanceCode);
        jobInstance.setRetryCount(0);

        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ITask> runningTask = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "web-duckdb-manual-stop-worker");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> future = executor.submit(() -> TaskExecutionSupport.execute(
            jobInstance,
            () -> new ETLTask(jobInstance),
            runningTask::set,
            stopRequested::get
        ));

        try {
            assertTrue(awaitPoolCreated(dbFile, 10, TimeUnit.SECONDS), "任务运行后应创建实例连接池");

            // 与 StartJobEventHandler.stopTask 一致：先通知任务取消，再中断执行线程。
            stopRequested.set(true);
            ITask task = runningTask.get();
            if (task != null) {
                task.cancel();
            }
            future.cancel(true);
            executor.shutdown();

            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "手动停止后任务线程应结束");
            assertPoolClosed(dbFile);
        } finally {
            stopRequested.set(true);
            future.cancel(true);
            executor.shutdownNow();
            Thread.interrupted();
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    private JobInstance jobInstance(String instanceCode) {
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobCode("web-duckdb-cleanup-job");
        jobInstance.setInstanceCode(instanceCode);
        jobInstance.setType(TaskConstants.TASK_TYPE_ETL);
        jobInstance.setJobContext(LONG_RUNNING_JOB);
        return jobInstance;
    }

    private boolean awaitPoolCreated(String dbFile, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        do {
            if (DuckDBEngine.getInstance().getAllPoolStatus().containsKey(dbFile)) {
                return true;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private void assertPoolClosed(String dbFile) throws InterruptedException {
        assertFalse(Files.exists(Paths.get(dbFile)), "任务结束后实例数据库文件应被删除");
        assertFalse(
            DuckDBEngine.getInstance().getAllPoolStatus().containsKey(dbFile),
            "任务结束后实例连接池应被关闭并移除"
        );

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        String expectedThreadName = "DuckDB-Pool-" + dbFile + " housekeeper";
        do {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.isAlive() && expectedThreadName.equals(thread.getName()));
            if (!alive) {
                return;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertTrue(false, "任务结束后不应残留实例连接池的 housekeeper 线程");
    }
}
