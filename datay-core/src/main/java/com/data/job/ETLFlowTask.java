package com.data.job;

import com.data.status.StatusStorageStrategy;
import com.data.status.StatusStorageStrategyFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private volatile String failureDetail = "任务执行失败"; // 记录首个失败的组件及根因

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
        // 使用当前生效的状态存储策略：单机默认 local，集群由上层注入 minio
        context.setStatusStorageStrategy(StatusStorageStrategyFactory.getActiveStrategy());
    }

    /**
     * 覆盖状态存储策略（供上层根据配置注入 minio/local）。
     *
     * @param statusStorageStrategy 状态存储策略
     */
    public void setStatusStorageStrategy(StatusStorageStrategy statusStorageStrategy) {
        if (statusStorageStrategy != null) {
            context.setStatusStorageStrategy(statusStorageStrategy);
        }
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

        // 调试模式：如果指定了目标节点，则只执行该节点及其所有上游依赖
        Set<String> runNodeIds = resolveRunNodeIds(order);
        if (getContext().isDebugMode() && getContext().getDebugTargetNodeId() != null) {
            List<Component> filtered = new ArrayList<>();
            for (Component comp : order) {
                if (comp != null && runNodeIds.contains(comp.getId())) {
                    filtered.add(comp);
                }
            }
            order = filtered;
            log("调试模式：仅运行目标节点及其上游，共 " + order.size() + " 个组件");
        }

        // 计算总线程数（考虑并发数）
        int totalThreads = order.stream()
            .filter(comp -> comp != null)
            .mapToInt(Component::getParallelism)
            .sum();
        executor = Executors.newFixedThreadPool(Math.max(totalThreads, 1));

        for (Map.Entry<String, List<String>> entry : parser.getEdges().entrySet()) {
            for (String targetId : entry.getValue()) {
                // 调试模式下只创建实际运行节点之间的队列，避免下游未运行导致队列阻塞
                if (!runNodeIds.contains(entry.getKey()) || !runNodeIds.contains(targetId)) {
                    continue;
                }
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
                                String detail = ExceptionUtils.describe(e);
                                failureDetail =
                                    "组件[" + comp.getName() + "-" + comp.getId() + "]执行失败：" + detail;
                                log("组件执行失败：" + comp.getName() + "-" + comp.getId() + "，异常信息：" + detail);
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
                            String message = failureDetail != null ? failureDetail : ExceptionUtils.describe(e.getCause());
                            throw new RuntimeException(message, e);
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
                throw new RuntimeException(failureDetail != null ? failureDetail : "任务执行失败");
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

    /**
     * 计算本次运行实际需要执行的节点集合。
     * 非调试模式或未指定目标节点时返回全部节点；
     * 调试模式指定目标节点时，返回目标节点及其所有上游节点。
     */
    private Set<String> resolveRunNodeIds(List<Component> order) {
        Set<String> allNodeIds = new HashSet<>();
        for (Component comp : order) {
            if (comp != null) {
                allNodeIds.add(comp.getId());
            }
        }
        if (!getContext().isDebugMode() || getContext().getDebugTargetNodeId() == null) {
            return allNodeIds;
        }
        String targetNodeId = getContext().getDebugTargetNodeId();
        if (!allNodeIds.contains(targetNodeId)) {
            log("调试模式：未找到目标节点 " + targetNodeId + "，改为整图运行");
            return allNodeIds;
        }
        // 以目标节点为起点，逆向收集所有上游依赖
        Set<String> result = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(targetNodeId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!result.add(current)) {
                continue;
            }
            for (Component comp : order) {
                if (comp == null) {
                    continue;
                }
                for (Connection connection : comp.getOutput()) {
                    if (current.equals(connection.getTargetId())) {
                        stack.push(comp.getId());
                    }
                }
            }
        }
        return result;
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