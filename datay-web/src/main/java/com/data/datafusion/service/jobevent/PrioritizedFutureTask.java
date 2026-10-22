package com.data.datafusion.service.jobevent;

import com.data.datafusion.job.TaskConstants;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * 带优先级的任务包装，供执行线程池的工作队列排序。
 *
 * <p>排序规则：优先级高的先执行；优先级相同时按提交顺序 FIFO。</p>
 */
final class PrioritizedFutureTask extends FutureTask<String> {

    private final int priority;
    private final long sequence;

    PrioritizedFutureTask(Callable<String> callable, int priority, long sequence) {
        super(callable);
        this.priority = priority;
        this.sequence = sequence;
    }

    int getPriority() {
        return priority;
    }

    long getSequence() {
        return sequence;
    }

    static int compare(Runnable left, Runnable right) {
        int leftPriority = left instanceof PrioritizedFutureTask
            ? ((PrioritizedFutureTask) left).getPriority()
            : TaskConstants.DEFAULT_PRIORITY;
        int rightPriority = right instanceof PrioritizedFutureTask
            ? ((PrioritizedFutureTask) right).getPriority()
            : TaskConstants.DEFAULT_PRIORITY;
        int priorityCompare = Integer.compare(rightPriority, leftPriority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        long leftSequence = left instanceof PrioritizedFutureTask ? ((PrioritizedFutureTask) left).getSequence() : Long.MAX_VALUE;
        long rightSequence = right instanceof PrioritizedFutureTask ? ((PrioritizedFutureTask) right).getSequence() : Long.MAX_VALUE;
        return Long.compare(leftSequence, rightSequence);
    }
}
