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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DAGTask extends AbstractTask {

    private final Logger log = LoggerFactory.getLogger(DAGTask.class);

    public DAGTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        FlowInstance flowInstance = JSONObject.parseObject(getJobInstance().getJobContext(), FlowInstance.class);
        List<JobInstance> jobInstances = getJobInstanceByOrder(flowInstance);
        //TODO 根据深度，可以调整成并行执行
        int index = 0;
        try {
            for (; index < jobInstances.size(); index++) {
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
                String result = task.execute();

                // 任务执行后，变更任务为完成状态
                JobStatusEvent afterEvent = new JobStatusEvent();
                jobInstance.setStatus(result);
                jobInstance.setEndTime(String.valueOf(System.currentTimeMillis()));
                afterEvent.setStatus(result);
                afterEvent.setJobInstance(jobInstance);
                afterEvent.setEndTime(System.currentTimeMillis());
                EventServiceFactory.getEventService().pushJobStatusEvent(afterEvent);
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
            log.error("DAG子任务执行异常 ：", e);
            // 任务失败时，将尚未执行的后续子任务状态更新为中断
            try {
                interruptRemainingTasks(jobInstances, index + 1);
            } catch (Exception interruptError) {
                log.error("更新剩余子任务状态为中断失败 ：", interruptError);
            }
            throw e;
        }
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
