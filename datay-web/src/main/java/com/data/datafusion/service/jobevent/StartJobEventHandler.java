package com.data.datafusion.service.jobevent;

import cn.hutool.core.net.NetUtil;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.datafusion.job.ITask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.TaskExecutionSupport;
import com.data.datafusion.job.TaskFactory;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.web.rest.WorkerResource;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartJobEventHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StartJobEventHandler.class);

    // 使用ThreadPoolExecutor以便获取线程池状态
    private final ThreadPoolExecutor jobExecutor;

    public static int WORK_THREAD_COUNT = 100;

    /** 待执行队列容量，避免一次性从事件队列取走过多任务。 */
    public static final int JOB_QUEUE_CAPACITY = 1000;

    /** 同优先级任务按提交顺序执行。 */
    private final AtomicLong submitSequence = new AtomicLong();

    // 任务跟踪映射：instanceCode -> 任务执行信息
    public static final Map<String, TaskExecutionInfo> runningTasks = new ConcurrentHashMap<>();

    // 获取当前执行节点的ip和端口信息
    public static final String EXECNODE = NetUtil.getLocalhostStr() + ":" + SpringUtil.getEnvironment().getProperty("server.port");

    public StartJobEventHandler() {
        // 创建可配置的线程池，支持任务停止和状态监控
        int corePoolSize = WORK_THREAD_COUNT;
        int maxPoolSize = WORK_THREAD_COUNT;
        int queueCapacity = JOB_QUEUE_CAPACITY;
        long keepAliveTime = 60L;

        this.jobExecutor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(queueCapacity, PrioritizedFutureTask::compare),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public void run() {
        while (true) {
            try {
                // 队列未满时继续接收任务，使待执行任务进入优先级队列排序。
                if (!hasAvailableCapacity()) {
                    log.debug(
                        "任务队列已满，等待空闲容量... 当前活跃线程: {}, 队列大小: {}",
                        jobExecutor.getActiveCount(),
                        jobExecutor.getQueue().size()
                    );
                    Thread.sleep(1000); // 等待1秒
                    continue;
                }

                // 获取任务
                JobStatusEvent event = EventServiceFactory.getEventService().takeStartJobEvent();
                event.getJobInstance().setExecNode(EXECNODE);

                // 提交任务到线程池
                String instanceCode = event.getJobInstance().getInstanceCode();
                // 记录任务执行信息。真正的任务执行器在每次重试时创建，便于超时/停止时操作当前尝试。
                TaskExecutionInfo taskInfo = new TaskExecutionInfo(event.getJobInstance());
                runningTasks.put(instanceCode, taskInfo);
                int priority = normalizePriority(event.getJobInstance().getPriority());
                PrioritizedFutureTask futureTask = new PrioritizedFutureTask(
                    new TaskCallable(event, taskInfo),
                    priority,
                    submitSequence.incrementAndGet()
                );
                jobExecutor.execute(futureTask);
                taskInfo.setFuture(futureTask);

                log.info(
                    "任务已提交执行: instanceCode={}, jobCode={}, priority={}, 当前活跃线程: {}, 队列大小: {}",
                    instanceCode,
                    event.getJobInstance().getJobCode(),
                    priority,
                    jobExecutor.getActiveCount(),
                    jobExecutor.getQueue().size()
                );
            } catch (Exception e) {
                log.error("处理任务事件时发生异常", e);
            }
        }
    }

    /**
     * 检查执行队列是否还有容量。
     * @return true表示可以继续接收新任务
     */
    private boolean hasAvailableCapacity() {
        return jobExecutor.getQueue().size() < JOB_QUEUE_CAPACITY;
    }

    private static int normalizePriority(Integer priority) {
        return TaskConstants.isValidPriority(priority) ? priority : TaskConstants.DEFAULT_PRIORITY;
    }

    /**
     * 停止指定任务
     * @param instanceCode 任务实例代码
     * @return 是否成功停止
     */
    public static boolean stopTask(String instanceCode) {
        TaskExecutionInfo taskInfo = runningTasks.get(instanceCode);
        if (taskInfo != null && !taskInfo.isCompleted()) {
            try {
                // 标记任务为停止状态
                taskInfo.setStopped(true);
                // 先通知任务自身清理（如销毁shell子进程）
                ITask task = taskInfo.getTask();
                if (task != null) {
                    task.cancel();
                }
                // 尝试取消任务执行（中断线程）
                Future<String> future = taskInfo.getFuture();
                boolean cancelled = future == null || future.cancel(true);

                if (cancelled) {
                    log.info("任务已成功停止: instanceCode={}", instanceCode);
                    JobStatusEvent endJobEvent = new JobStatusEvent();
                    endJobEvent.setStatus(TaskConstants.TASK_STATUS_INTERRUPTED);
                    taskInfo.getJobInstance().setStatus(TaskConstants.TASK_STATUS_INTERRUPTED);
                    endJobEvent.setJobInstance(taskInfo.getJobInstance());
                    endJobEvent.setEndTime(System.currentTimeMillis());
                    EventServiceFactory.getEventService().pushJobStatusEvent(endJobEvent);
                    runningTasks.remove(instanceCode);
                    return true;
                } else {
                    log.warn("任务停止失败: instanceCode={}", instanceCode);
                    return false;
                }
            } catch (Exception e) {
                log.error("停止任务时发生异常: instanceCode={}", instanceCode, e);
                return false;
            }
        }
        if (taskInfo == null) {
            log.warn("任务未找到: instanceCode={}", instanceCode);
            JobStatusEvent endJobEvent = new JobStatusEvent();
            endJobEvent.setStatus(TaskConstants.TASK_STATUS_INTERRUPTED);
            JobInstance instance = new JobInstance();
            instance.setInstanceCode(instanceCode);
            instance.setStatus(TaskConstants.TASK_STATUS_INTERRUPTED);
            endJobEvent.setJobInstance(instance);
            endJobEvent.setEndTime(System.currentTimeMillis());
            EventServiceFactory.getEventService().pushJobStatusEvent(endJobEvent);
        }
        return true;
    }

    /**
     * 任务执行Callable
     */
    private class TaskCallable implements Callable<String> {

        private final JobStatusEvent event;
        private final TaskExecutionInfo taskInfo;

        public TaskCallable(JobStatusEvent event, TaskExecutionInfo taskInfo) {
            this.event = event;
            this.taskInfo = taskInfo;
        }

        @Override
        public String call() throws Exception {
            String instanceCode = event.getJobInstance().getInstanceCode();
            // 工作线程池没有请求上下文，需按任务所属租户设置租户上下文，
            // 否则租户过滤器会以 dummy 租户拦截，导致读取不到该租户的状态存储等配置。
            Long tenantId = parseTenantId(event.getJobInstance().getTenantId());
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
            }

            try {
                // 检查任务是否已被停止
                if (taskInfo != null && taskInfo.isStopped()) {
                    log.info("任务已被标记为停止，不再执行: instanceCode={}", instanceCode);
                    return "TASK_STOPPED";
                }

                log.info(
                    "开始执行任务: Code:{}, Type:{}, instanceCode:{}",
                    event.getJobInstance().getJobCode(),
                    event.getJobInstance().getType(),
                    instanceCode
                );

                // 执行任务前，变更任务为运行中状态
                JobStatusEvent beforeEvent = new JobStatusEvent();
                event.getJobInstance().setStatus(TaskConstants.TASK_STATUS_RUNNING);
                event.getJobInstance().setStartTime(String.valueOf(System.currentTimeMillis()));
                beforeEvent.setJobInstance(event.getJobInstance());
                beforeEvent.setStatus(TaskConstants.TASK_STATUS_RUNNING);
                beforeEvent.setStartTime(System.currentTimeMillis());
                EventServiceFactory.getEventService().pushJobStatusEvent(beforeEvent);

                //正式执行任务。重试/超时由 TaskExecutionSupport 统一处理，每次尝试创建独立执行器。
                String status = TaskExecutionSupport.execute(
                    event.getJobInstance(),
                    () -> TaskFactory.getTaskProcessor(event.getJobInstance()),
                    taskInfo::setTask,
                    taskInfo::isStopped
                );

                // 失败/超时任务把错误日志写入实例消息，状态事件会把它带到告警中心。
                // 日志在任务执行节点读取，集群模式下 master 无需访问 worker 的本地日志文件。
                if (
                    TaskConstants.TASK_STATUS_FAILED.equals(status) || TaskConstants.TASK_STATUS_TIMEOUT.equals(status)
                ) {
                    try {
                        String failureLog = SpringUtil.getBean(WorkerResource.class).readFailureLog(event.getJobInstance());
                        event.getJobInstance().setJobMessage(failureLog);
                    } catch (Exception e) {
                        log.warn("读取任务失败日志失败: instanceCode={}", instanceCode, e);
                    }
                }

                // 任务执行后，变更任务为完成状态
                JobStatusEvent afterEvent = new JobStatusEvent();
                event.getJobInstance().setStatus(status);
                event.getJobInstance().setEndTime(String.valueOf(System.currentTimeMillis()));
                afterEvent.setStatus(status);
                afterEvent.setJobInstance(event.getJobInstance());
                afterEvent.setEndTime(System.currentTimeMillis());
                EventServiceFactory.getEventService().pushJobStatusEvent(afterEvent);

                // 标记任务完成
                if (taskInfo != null) {
                    taskInfo.setCompleted(true);
                }

                // 从运行任务列表中移除
                runningTasks.remove(instanceCode);

                log.info("任务执行完成: instanceCode={}, result={}", instanceCode, status);
                return status;
            } catch (Exception e) {
                // 标记任务完成（异常情况）
                if (taskInfo != null) {
                    taskInfo.setCompleted(true);
                }

                // 从运行任务列表中移除
                runningTasks.remove(instanceCode);

                log.error("任务执行异常: instanceCode={}", instanceCode, e);
                throw e;
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * 解析任务所属租户 id，解析失败或为空时返回 null。
     */
    private static Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(tenantId.trim());
        } catch (NumberFormatException e) {
            log.warn("任务租户 id 非法，忽略租户上下文: {}", tenantId);
            return null;
        }
    }

    /**
     * 任务执行信息类
     */
    public static class TaskExecutionInfo {

        private volatile Future<String> future;
        private final JobInstance jobInstance;
        private final AtomicBoolean stopped;
        private volatile boolean completed;
        private volatile ITask task; // 新增：保存ITask实例引用

        public TaskExecutionInfo(JobInstance jobInstance) {
            this.jobInstance = jobInstance;
            this.stopped = new AtomicBoolean(false);
            this.completed = false;
        }

        public Future<String> getFuture() {
            return future;
        }

        public JobInstance getJobInstance() {
            return jobInstance;
        }

        public boolean isStopped() {
            return stopped.get();
        }

        public void setStopped(boolean stopped) {
            this.stopped.set(stopped);
        }

        public boolean isCompleted() {
            return completed || (future != null && future.isDone());
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public String getInstanceCode() {
            return jobInstance.getInstanceCode();
        }

        public String getJobCode() {
            return jobInstance.getJobCode();
        }

        public String getStatus() {
            if (isStopped()) {
                return "STOPPED";
            } else if (isCompleted()) {
                return "COMPLETED";
            } else {
                return "RUNNING";
            }
        }

        /**
         * 获取任务执行进度信息
         * @return 进度信息，如果不可用则返回null
         */
        public String getTaskStatus() {
            if (task != null && task instanceof AbstractTask) {
                AbstractTask abstractTask = (AbstractTask) task;
                // 这里可以实现进度查询逻辑
                // 例如：通过ExecutionContext获取进度
                return "执行中...";
            }
            return null;
        }

        public ITask getTask() {
            return task;
        }

        public void setTask(ITask task) {
            this.task = task;
        }

        public void setFuture(Future<String> future) {
            this.future = future;
        }
    }

    /**
     * 关闭事件处理器
     */
    public void shutdown() {
        try {
            // 停止所有运行中的任务
            for (String instanceCode : runningTasks.keySet()) {
                stopTask(instanceCode);
            }

            // 关闭线程池
            jobExecutor.shutdown();
            if (!jobExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                jobExecutor.shutdownNow();
            }

            log.info("StartJobEventHandler已关闭");
        } catch (InterruptedException e) {
            jobExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
