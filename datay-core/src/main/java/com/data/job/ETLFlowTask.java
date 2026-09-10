package com.data.job;

import com.data.status.StatusStorageStrategy;
import com.data.status.StatusStorageStrategyFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// 主程序执行流程
public class ETLFlowTask {

    private static final Integer QUEUE_SIZE = 20;

    private ExecutorService executor; // 将executor提升为类变量
    private List<Component> order = new ArrayList<>();
    private final List<Future<?>> futures = new ArrayList<>();
    private volatile boolean cancelled = false; // 添加取消标志

    private TaskLogger taskLogger;

    private ExecutionContext context;

    private TaskInstance taskInstance;

    public ETLFlowTask(TaskInstance taskInstance, TaskLogger taskLogger) {
        this.taskInstance = taskInstance;
        this.taskLogger = taskLogger;
        initContext();
    }

    public ETLFlowTask() {
        initContext();
    }

    public void initContext() {
        context = new ExecutionContext();
        context.setJobInstanceCode(getInstanceCode());
        context.setJobCode(getJobCode());
        initLogFile();
        // 默认使用本地文件策略
        StatusStorageStrategy statusStorageStrategy = StatusStorageStrategyFactory.getStrategy("local");
        context.setStatusStorageStrategy(statusStorageStrategy);
    }

    public void runJob(String jobContext) throws Exception {
        DAGParser parser = new DAGParser();
        try {
            parser.parse(jobContext);
        } catch (IllegalArgumentException e) {
            log(e.getMessage());
            throw e;
        }

        order = parser.topologicalSort();
        
        // 计算总线程数（考虑并发数）
        int totalThreads = order.stream()
            .filter(comp -> comp != null)
            .mapToInt(Component::getParallelism)
            .sum();
        executor = Executors.newFixedThreadPool(Math.max(totalThreads, 1));

        for (Map.Entry<String, List<String>> entry : parser.getEdges().entrySet()) {
            for (String targetId : entry.getValue()) {
                getContext().getConnections().put(entry.getKey() + "_" + targetId, new LinkedBlockingQueue<>(QUEUE_SIZE));
            }
        }
        // 使用AtomicBoolean确保线程安全
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        try {
            // 在任务执行前尝试恢复状态
            getContext().loadStatus();
            for (Component comp : order) {
                if (comp == null) {
                    continue;
                }
                if (hasFailure.get()) {
                    break; // 如果已经有失败，停止提交新任务
                }

                // 根据并发数提交多个任务
                int parallelism = comp.getParallelism();
                comp.setActiveThreads(new AtomicInteger(parallelism));
                for (int i = 0; i < parallelism; i++) {
                    final int threadIndex = i;
                    futures.add(
                        executor.submit(() -> {
                            try {
                                comp.setContext(getContext());
                                comp.setTaskLogger(getTaskLogger());
                                // 设置线程索引，用于区分不同并发线程
                                comp.execute();
                            } catch (Exception e) {
                                // 检查是否是中断异常
                                log("组件执行失败：" + comp.getId() + "，异常信息：" + e.getMessage());
                                // 设置失败标志并取消所有其他任务
                                hasFailure.set(true);
                                cancel();
                                throw e; // 重新抛出异常
                            }
                        })
                    );
                }
            }

            // 循环检查所有线程状态
            while (!futures.isEmpty() && !hasFailure.get() && !cancelled) {
                if (Thread.currentThread().isInterrupted()) {
                    // 任务执行异常，设置失败标志并取消所有任务
                    hasFailure.set(true);
                    cancel();
                    throw new InterruptedException("任务被中断");
                }
                Iterator<Future<?>> iterator = futures.iterator();
                while (iterator.hasNext()) {
                    Future<?> future = iterator.next();
                    if (future.isDone()) {
                        try {
                            future.get(); // 检查是否有异常
                            iterator.remove(); // 任务完成且无异常，从列表中移除
                        } catch (ExecutionException e) {
                            // 任务执行异常，设置失败标志并取消所有任务
                            hasFailure.set(true);
                            cancel();
                            throw new RuntimeException("任务执行失败", e);
                        } catch (InterruptedException e) {
                            // 线程被中断，设置取消标志
                            cancelled = true;
                            cancel();
                            Thread.currentThread().interrupt(); // 恢复中断状态
                            throw e;
                        }
                    }
                }
                // 短暂休眠避免CPU过度占用
                if (!futures.isEmpty() && !hasFailure.get()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        cancelled = true;
                        cancel();
                        Thread.currentThread().interrupt(); // 恢复中断状态
                        throw new InterruptedException("任务已被取消");
                    }
                }
            }

            if (hasFailure.get()) {
                throw new RuntimeException("任务执行失败");
            }
            if (cancelled) {
                throw new InterruptedException("任务已被取消");
            }
            getContext().saveStatus();
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            DuckDBEngine.deleteDBFile(getContext().getJobInstanceCode());
        }
    }

    public void cancel() {
        cancelled = true; // 设置取消标志
        boolean allCancelled = true;

        for (Component comp : order) {
            if (comp == null) {
                continue;
            }
            comp.stop();
        }

        if (executor != null) {
            // 立即关闭线程池，不再接受新任务
            executor.shutdown();

            // 中断所有正在执行的任务
            for (Future<?> future : futures) {
                if (!future.isDone()) {
                    boolean cancelled = future.cancel(true); // 强制中断
                    allCancelled &= cancelled;
                    if (!cancelled) {
                        log("无法取消任务: " + future);
                    }
                }
            }

            // 尝试强制关闭
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    List<Runnable> unfinishedTasks = executor.shutdownNow();
                    if (!unfinishedTasks.isEmpty()) {
                        log("仍有未完成的任务: " + unfinishedTasks.size());
                        allCancelled = false;
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

    }

    public void initLogFile() {
        if (taskLogger != null) {
            return;
        }
        // 单元测试时，JobInstance 为 null，需要初始化一个测试用的 logger
        if (getTaskInstance() == null) {
            taskLogger = new TaskLogger("test", String.valueOf(System.currentTimeMillis()));
            return;
        }
        if (getTaskInstance().getParentInstanceCode() != null) {
            taskLogger = new TaskLogger(getTaskInstance().getJobCode(), getTaskInstance().getParentInstanceCode());
        } else {
            taskLogger = new TaskLogger(getTaskInstance().getJobCode(), getTaskInstance().getInstanceCode());
        }
    }

    public ExecutionContext getContext() {
        return context;
    }

    public void setContext(ExecutionContext context) {
        this.context = context;
    }

    public TaskInstance getTaskInstance() {
        return taskInstance;
    }

    public TaskLogger getTaskLogger() {
        return taskLogger;
    }

    public void log(String logMessage) {
        taskLogger.writeLog(logMessage);
    }

    public String getInstanceCode() {
        if (taskInstance == null) {
            return null;
        }
        return taskInstance.getInstanceCode();
    }

    public String getJobCode() {
        if (taskInstance == null) {
            return null;
        }
        return taskInstance.getJobCode();
    }
}