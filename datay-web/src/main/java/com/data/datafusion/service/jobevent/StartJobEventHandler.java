package com.data.datafusion.service.jobevent;

import cn.hutool.core.net.NetUtil;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.datafusion.job.ITask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.TaskFactory;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartJobEventHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StartJobEventHandler.class);

    // 使用ThreadPoolExecutor以便获取线程池状态
    private final ThreadPoolExecutor jobExecutor;

    public static int WORK_THREAD_COUNT = 100;

    // 任务跟踪映射：instanceCode -> 任务执行信息
    public static final Map<String, TaskExecutionInfo> runningTasks = new ConcurrentHashMap<>();

    // 获取当前执行节点的ip和端口信息
    public static final String EXECNODE = NetUtil.getLocalhostStr() + ":" + SpringUtil.getEnvironment().getProperty("server.port");

    public StartJobEventHandler() {
        // 创建可配置的线程池，支持任务停止和状态监控
        int corePoolSize = WORK_THREAD_COUNT;
        int maxPoolSize = WORK_THREAD_COUNT;
        int queueCapacity = 1000;
        long keepAliveTime = 60L;

        this.jobExecutor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public void run() {
        while (true) {
            try {
                // 检查线程池是否有空闲容量
                if (!hasAvailableCapacity()) {
                    // 线程池已满，等待一段时间再检查
                    log.debug(
                        "线程池已满，等待空闲容量... 当前活跃线程: {}, 队列大小: {}",
                        jobExecutor.getActiveCount(),
                        jobExecutor.getQueue().size()
                    );
                    Thread.sleep(1000); // 等待1秒
                    continue;
                }

                // 线程池有空闲，获取任务
                JobStatusEvent event = EventServiceFactory.getEventService().takeStartJobEvent();
                event.getJobInstance().setExecNode(EXECNODE);

                // 创建任务处理器
                ITask task = TaskFactory.getTaskProcessor(event.getJobInstance());

                // 提交任务到线程池
                String instanceCode = event.getJobInstance().getInstanceCode();
                // 记录任务执行信息
                TaskExecutionInfo taskInfo = new TaskExecutionInfo(event.getJobInstance(), task);
                runningTasks.put(instanceCode, taskInfo);
                Future<String> future = jobExecutor.submit(new TaskCallable(task, event));
                taskInfo.setFuture(future);

                log.info(
                    "任务已提交执行: instanceCode={}, jobCode={}, 当前活跃线程: {}, 队列大小: {}",
                    instanceCode,
                    event.getJobInstance().getJobCode(),
                    jobExecutor.getActiveCount(),
                    jobExecutor.getQueue().size()
                );
            } catch (Exception e) {
                log.error("处理任务事件时发生异常", e);
            }
        }
    }

    /**
     * 检查线程池是否有空闲容量
     * @return true表示有空闲容量，可以接受新任务
     */
    private boolean hasAvailableCapacity() {
        // 如果队列还有剩余容量，说明可以接受新任务
        int activeCount = jobExecutor.getActiveCount();
        return activeCount < WORK_THREAD_COUNT;
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
                boolean cancelled = taskInfo.getFuture().cancel(true);

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

        private final ITask task;
        private final JobStatusEvent event;

        public TaskCallable(ITask task, JobStatusEvent event) {
            this.task = task;
            this.event = event;
        }

        @Override
        public String call() throws Exception {
            String instanceCode = event.getJobInstance().getInstanceCode();
            TaskExecutionInfo taskInfo = runningTasks.get(instanceCode);

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

                //正式执行任务
                String status = task.execute();

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
            }
        }
    }

    /**
     * 任务执行信息类
     */
    public static class TaskExecutionInfo {

        private Future<String> future;
        private final JobInstance jobInstance;
        private final AtomicBoolean stopped;
        private volatile boolean completed;
        private ITask task; // 新增：保存ITask实例引用

        public TaskExecutionInfo(JobInstance jobInstance, ITask task) {
            this.jobInstance = jobInstance;
            this.stopped = new AtomicBoolean(false);
            this.completed = false;
            this.task = task; // 新增：保存ITask实例
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
            return completed || future.isDone();
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