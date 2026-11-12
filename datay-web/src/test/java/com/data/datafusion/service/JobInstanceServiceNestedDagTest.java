package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.dag.FlowInstance;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobInstanceDTO;
import com.data.datafusion.service.mapper.JobInstanceMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobInstanceServiceNestedDagTest {

    @Test
    void buildFlowInstanceExpandsNestedDagContext() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        JobInstanceMapper mapper = mock(JobInstanceMapper.class);
        JobInstanceService service = new JobInstanceService(jobInstanceRepository, jobRepository, mapper);

        Job nestedDag = job("dag-b", TaskConstants.TASK_TYPE_DAG);
        nestedDag.setJobContext("{\"jobs\":[{\"id\":\"shell-c\"}],\"jobDepends\":[]}");
        Job shell = job("shell-c", TaskConstants.TASK_TYPE_SHELL);
        shell.setJobContext("{}");
        when(jobRepository.findById("dag-b")).thenReturn(Optional.of(nestedDag));
        when(jobRepository.findById("shell-c")).thenReturn(Optional.of(shell));
        when(jobInstanceRepository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobInstance parent = dagInstance("dag-a", "dag-a-instance");
        parent.setJobContext("{\"jobs\":[{\"id\":\"dag-b\"}],\"jobDepends\":[]}");

        FlowInstance flow = service.buildFlowInstance(parent);

        assertThat(flow.getJobInstances()).hasSize(1);
        JobInstance nestedInstance = flow.getJobInstances().iterator().next();
        assertThat(nestedInstance.getType()).isEqualTo(TaskConstants.TASK_TYPE_DAG);
        assertThat(nestedInstance.getParentInstanceCode()).isEqualTo(parent.getInstanceCode());

        FlowInstance nestedFlow = JSONObject.parseObject(nestedInstance.getJobContext(), FlowInstance.class);
        assertThat(nestedFlow.getJobInstances()).hasSize(1);
        JobInstance grandChild = nestedFlow.getJobInstances().iterator().next();
        assertThat(grandChild.getJobCode()).isEqualTo("shell-c");
        assertThat(grandChild.getParentInstanceCode()).isEqualTo(nestedInstance.getInstanceCode());
    }

    @Test
    void buildFlowInstanceCreatesUniqueCodesForReusedNestedDag() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        JobInstanceMapper mapper = mock(JobInstanceMapper.class);
        JobInstanceService service = new JobInstanceService(jobInstanceRepository, jobRepository, mapper);

        Job nestedDag = job("dag-b", TaskConstants.TASK_TYPE_DAG);
        nestedDag.setJobContext("{\"jobs\":[],\"jobDepends\":[]}");
        when(jobRepository.findById("dag-b")).thenReturn(Optional.of(nestedDag));
        when(jobInstanceRepository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobInstance parentOne = dagInstance("dag-parent-1", "dag-parent-instance-1");
        parentOne.setJobContext("{\"jobs\":[{\"id\":\"dag-b\"}],\"jobDepends\":[]}");
        JobInstance parentTwo = dagInstance("dag-parent-2", "dag-parent-instance-2");
        parentTwo.setJobContext("{\"jobs\":[{\"id\":\"dag-b\"}],\"jobDepends\":[]}");

        JobInstance first = service.buildFlowInstance(parentOne).getJobInstances().iterator().next();
        JobInstance second = service.buildFlowInstance(parentTwo).getJobInstances().iterator().next();

        assertThat(first.getInstanceCode()).isNotEqualTo(second.getInstanceCode());
    }

    @Test
    void findSubInstancesReturnsAllDescendantsWhenRecursive() {
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        JobInstanceMapper mapper = mock(JobInstanceMapper.class);
        JobInstanceService service = new JobInstanceService(jobInstanceRepository, mock(JobRepository.class), mapper);

        JobInstance nestedDag = instance("nested-dag-instance", "top-instance");
        JobInstance directShell = instance("direct-shell-instance", "top-instance");
        JobInstance grandChild = instance("grand-child-instance", "nested-dag-instance");

        when(jobInstanceRepository.findByParentInstanceCodeOrderByIdAsc("top-instance"))
            .thenReturn(Arrays.asList(nestedDag, directShell));
        when(jobInstanceRepository.findByParentInstanceCodeOrderByIdAsc("nested-dag-instance")).thenReturn(List.of(grandChild));
        when(jobInstanceRepository.findByParentInstanceCodeOrderByIdAsc("direct-shell-instance")).thenReturn(Collections.emptyList());
        when(jobInstanceRepository.findByParentInstanceCodeOrderByIdAsc("grand-child-instance")).thenReturn(Collections.emptyList());
        when(mapper.toDto(any(JobInstance.class))).thenReturn(new JobInstanceDTO());

        assertThat(service.findSubInstances("top-instance", true)).hasSize(3);
        assertThat(service.findSubInstances("top-instance", false)).hasSize(2);
    }

    private static Job job(String id, String type) {
        Job job = new Job();
        job.setId(id);
        job.setJobName(id);
        job.setType(type);
        job.setPriority(TaskConstants.DEFAULT_PRIORITY);
        return job;
    }

    private static JobInstance dagInstance(String jobCode, String instanceCode) {
        JobInstance instance = instance(instanceCode, null);
        instance.setJobCode(jobCode);
        instance.setJobName(jobCode);
        instance.setType(TaskConstants.TASK_TYPE_DAG);
        instance.setPriority(TaskConstants.DEFAULT_PRIORITY);
        return instance;
    }

    private static JobInstance instance(String instanceCode, String parentInstanceCode) {
        JobInstance instance = new JobInstance();
        instance.setInstanceCode(instanceCode);
        instance.setParentInstanceCode(parentInstanceCode);
        instance.setStatus(TaskConstants.TASK_STATUS_APPENDING);
        return instance;
    }
}
