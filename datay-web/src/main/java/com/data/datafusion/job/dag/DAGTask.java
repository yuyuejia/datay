package com.data.datafusion.job.dag;

import static com.data.datafusion.service.jobevent.StartJobEventHandler.EXECNODE;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.datafusion.job.ITask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.TaskFactory;
import com.data.datafusion.service.jobevent.EventServiceFactory;
import com.data.datafusion.service.jobevent.JobStatusEvent;
import com.google.common.collect.Queues;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DAGTask extends AbstractTask {

    private final Logger log = LoggerFactory.getLogger(DAGTask.class);

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile ITask currentTask;

    public DAGTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        // 重试由 TaskExecutionSupport 整体触发，每次尝试都会重新执行全部子任务。
        // 这里不查询子任务历史状态，避免重试过程中依赖数据库状态。
        FlowInstance flowInstance = JSONObject.parseObject(getJobInstance().getJobContext(), FlowInstance.class);
        List<JobInstance> jobInstances = getJobInstanceByOrder(flowInstance);
        //TODO 根据深度，可以调整成并行执行
        int index = 0;
        boolean currentChildFinished = false;
        try {
            for (; index < jobInstances.size(); index++) {
                currentChildFinished = false;
                if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("DAG task was cancelled");
                }
                JobInstance jobInstance = jobInstances.get(index);
                // 执行任务前，变更任务为运行中状态
                JobStatusEvent beforeEvent = new JobStatusEvent();
                jobInstance.setExecNode(EXECNODE);
                jobInstance.setStatus(TaskConstants.TASK_STATUS_RUNNING);
                jobInstance.setStartTime(String.valueOf(System.currentTimeMillis()));
                beforeEvent.setJobInstance(jobInstance);
                beforeEvent.setStatus(TaskConstants.TASK_STATUS_RUNNING);
                beforeEvent.setStartTime(System.currentTimeMillis());
                EventServiceFactory.getEventService().pushJobStatusEvent(beforeEvent);

                ITask task = TaskFactory.getTaskProcessor(jobInstance);
                currentTask = task;
                if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                    task.cancel();
                    throw new InterruptedException("DAG task was cancelled");
                }
                String result;
                try {
                    result = task.execute();
                } finally {
                    currentTask = null;
                }

                // 超时/停止时执行线程会被中断。此时当前子任务已被取消，状态应落为中断，
                // 而不是 FAILED；同时先清理中断标记，保证子任务终态事件能够写入队列。
                if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                    Thread.interrupted();
                    if (!TaskConstants.TASK_STATUS_SUCCESSFUL.equals(result)) {
                        result = TaskConstants.TASK_STATUS_INTERRUPTED;
                    }
                }

                // 任务执行后，变更任务为完成状态
                JobStatusEvent afterEvent = new JobStatusEvent();
                jobInstance.setStatus(result);
                jobInstance.setEndTime(String.valueOf(System.currentTimeMillis()));
                afterEvent.setStatus(result);
                afterEvent.setJobInstance(jobInstance);
                afterEvent.setEndTime(System.currentTimeMillis());
                EventServiceFactory.getEventService().pushJobStatusEvent(afterEvent);
                currentChildFinished = true;
                if (TaskConstants.TASK_STATUS_INTERRUPTED.equals(result) && cancelled.get()) {
                    throw new InterruptedException("DAG task was cancelled");
                }
                if (!"SUCCESSFUL".equals(result)) {
                    throw new RuntimeException(
                        "DAG子任务执行失败: 子任务ID:" +
                        jobInstance.getJobCode() +
                        ",子任务类型:" +
                        jobInstance.getType() +
                        ",失败原因:" +
                        result
                    );
                }
                log.info("DAG子任务执行完成: 子任务ID:{},子任务类型:{}", jobInstance.getJobCode(), jobInstance.getType());
            }
            return "SUCCESSFUL";
        } catch (Exception e) {
            boolean cancelledTask = cancelled.get() || Thread.currentThread().isInterrupted() || e instanceof InterruptedException;
            if (cancelledTask) {
                log.warn("DAG任务已取消，正在中断当前及剩余子任务");
                // 清理中断标记，确保当前及剩余子任务的中断状态能够正常写入队列。
                Thread.interrupted();
            } else {
                log.error("DAG子任务执行异常 ：", e);
            }
            // 任务失败/取消时，将当前未完成及尚未执行的子任务状态更新为中断。
            // currentChildFinished 为 true 表示当前子任务终态事件已提交，只需处理后续子任务。
            int interruptFromIndex = currentChildFinished ? index + 1 : index;
            try {
                interruptRemainingTasks(jobInstances, interruptFromIndex);
            } catch (Exception interruptError) {
                log.error("更新剩余子任务状态为中断失败 ：", interruptError);
            }
            if (cancelledTask) {
                throw new InterruptedException("DAG task was cancelled");
            }
            throw e;
        }
    }

    @Override
    public boolean cancel() {
        cancelled.set(true);
        ITask task = this.currentTask;
        if (task != null) {
            task.cancel();
        }
        return true;
    }

    /**
     * 将尚未执行的后续子任务状态更新为中断
     *
     * @param jobInstances 按执行顺序排列的子任务实例
     * @param fromIndex    从该下标开始(含)的子任务均尚未执行
     */
    private void interruptRemainingTasks(List<JobInstance> jobInstances, int fromIndex) {
        for (int i = fromIndex; i < jobInstances.size(); i++) {
            JobInstance jobInstance = jobInstances.get(i);
            jobInstance.setStatus(TaskConstants.TASK_STATUS_INTERRUPTED);
            jobInstance.setEndTime(String.valueOf(System.currentTimeMillis()));

            JobStatusEvent interruptedEvent = new JobStatusEvent();
            interruptedEvent.setJobInstance(jobInstance);
            interruptedEvent.setStatus(TaskConstants.TASK_STATUS_INTERRUPTED);
            interruptedEvent.setEndTime(System.currentTimeMillis());
            EventServiceFactory.getEventService().pushJobStatusEvent(interruptedEvent);
        }
    }

    /**
     * 获取DAG的节点执行顺序
     * @param flowInstance
     * @return
     */
    public List<JobInstance> getJobInstanceByOrder(FlowInstance flowInstance) {
        MutableGraph<JobInstance> graph = GraphBuilder.directed().allowsSelfLoops(false).build();

        Map<String, JobInstance> jobInstanceMap = new HashMap<>();
        for (JobInstance jobInstance : flowInstance.getJobInstances()) {
            graph.addNode(jobInstance);
            jobInstanceMap.put(jobInstance.getJobCode(), jobInstance);
        }

        for (JobDepend jobDepend : flowInstance.getJobDepends()) {
            graph.putEdge(jobInstanceMap.get(jobDepend.getParentJobCode()), jobInstanceMap.get(jobDepend.getChildJobCode()));
        }

        Map<String, Integer> nodeInDegreeMap = new HashMap<>();
        Queue<JobInstance> queue = Queues.newArrayDeque();
        List<JobInstance> topologicalSortList = new ArrayList<>(); //拓扑排序列表维护

        // 获取所有入度为0的节点
        for (JobInstance jobInstance : graph.nodes()) {
            int indegree = graph.inDegree(jobInstance);
            nodeInDegreeMap.put(jobInstance.getJobCode(), indegree);
            if (indegree == 0) {
                queue.add(jobInstance);
                topologicalSortList.add(jobInstance);
            }
        }

        while (!queue.isEmpty()) {
            JobInstance preNode = queue.poll(); //获取并删除

            for (JobInstance successorNode : graph.successors(preNode)) {
                int indegree = nodeInDegreeMap.get(successorNode.getJobCode());
                if (--indegree == 0) { //-1：等效删除父节点以及相应的边
                    queue.offer(successorNode); //insert
                    topologicalSortList.add(successorNode);
                }
                nodeInDegreeMap.put(successorNode.getJobCode(), indegree);
            }
        }

        if (topologicalSortList.size() != graph.nodes().size()) {
            System.out.println("不是dag图");
        }
        return topologicalSortList;
    }
}
