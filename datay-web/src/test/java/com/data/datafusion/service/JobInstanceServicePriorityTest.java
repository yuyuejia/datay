package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.dag.FlowInstance;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.mapper.JobInstanceMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobInstanceServicePriorityTest {

    @Test
    void buildJobInstanceSnapshotsJobPriority() {
        JobRepository jobRepository = mock(JobRepository.class);
        Job job = new Job();
        job.setId("job-1");
        job.setJobName("priority-job");
        job.setType("SHELL");
        job.setPriority(3);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        JobInstanceService service = new JobInstanceService(mock(JobInstanceRepository.class), jobRepository, mock(JobInstanceMapper.class));

        JobInstance instance = service.buildJobInstance("job-1");

        assertThat(instance.getPriority()).isEqualTo(3);
    }

    @Test
    void buildJobInstanceFallsBackToNormalPriorityForMissingOrInvalidValues() {
        JobRepository jobRepository = mock(JobRepository.class);
        Job job = new Job();
        job.setId("job-1");
        job.setJobName("priority-job");
        job.setType("SHELL");
        job.setPriority(9);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        JobInstanceService service = new JobInstanceService(mock(JobInstanceRepository.class), jobRepository, mock(JobInstanceMapper.class));

        assertThat(service.buildJobInstance("job-1").getPriority()).isEqualTo(2);

        job.setPriority(null);
        assertThat(service.buildJobInstance("job-1").getPriority()).isEqualTo(2);
    }

    @Test
    void dagChildInstancesInheritParentPriority() {
        JobRepository jobRepository = mock(JobRepository.class);
        Job child = new Job();
        child.setId("child-1");
        child.setJobName("child");
        child.setType("SHELL");
        child.setPriority(1);
        when(jobRepository.findById("child-1")).thenReturn(Optional.of(child));

        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        when(jobInstanceRepository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobInstanceService service = new JobInstanceService(jobInstanceRepository, jobRepository, mock(JobInstanceMapper.class));
        JobInstance parent = new JobInstance();
        parent.setInstanceCode("dag-1");
        parent.setPriority(3);
        parent.setJobContext("{\"jobs\":[{\"id\":\"child-1\"}],\"jobDepends\":[]}");

        FlowInstance flowInstance = service.buildFlowInstance(parent);

        assertThat(flowInstance.getJobInstances()).extracting(JobInstance::getPriority).containsOnly(3);
    }

    @Test
    void dagChildInstancesDefaultWhenParentPriorityMissing() {
        JobRepository jobRepository = mock(JobRepository.class);
        Job child = new Job();
        child.setId("child-1");
        child.setJobName("child");
        child.setType("SHELL");
        child.setPriority(3);
        when(jobRepository.findById("child-1")).thenReturn(Optional.of(child));

        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        when(jobInstanceRepository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobInstanceService service = new JobInstanceService(jobInstanceRepository, jobRepository, mock(JobInstanceMapper.class));
        JobInstance parent = new JobInstance();
        parent.setInstanceCode("dag-1");
        parent.setPriority(null);
        parent.setJobContext("{\"jobs\":[{\"id\":\"child-1\"}],\"jobDepends\":[]}");

        FlowInstance flowInstance = service.buildFlowInstance(parent);

        assertThat(flowInstance.getJobInstances()).extracting(JobInstance::getPriority).containsOnly(2);
    }
}
