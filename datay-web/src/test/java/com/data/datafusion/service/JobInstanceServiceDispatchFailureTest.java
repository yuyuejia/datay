package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.mapper.JobInstanceMapper;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JobInstanceServiceDispatchFailureTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recordDispatchFailurePersistsFailedInstanceWhenInstanceWasNotBuilt() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        JobInstanceService service = new JobInstanceService(jobInstanceRepository, jobRepository, mock(JobInstanceMapper.class));

        Job job = new Job();
        job.setId("job-1");
        job.setJobName("测试任务");
        job.setType(TaskConstants.TASK_TYPE_SQL);
        job.setTenantId("9");
        job.setPriority(TaskConstants.PRIORITY_NORMAL);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobInstanceRepository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobInstance result = service.recordDispatchFailure(
            "job-1",
            null,
            new IllegalStateException("SQL 任务绑定的数据源不存在: ds-1")
        );

        assertThat(result.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
        assertThat(result.getJobCode()).isEqualTo("job-1");
        assertThat(result.getJobName()).isEqualTo("测试任务");
        assertThat(result.getType()).isEqualTo(TaskConstants.TASK_TYPE_SQL);
        assertThat(result.getTenantId()).isEqualTo("9");
        assertThat(result.getInstanceCode()).startsWith("job-1-");
        assertThat(result.getJobMessage()).contains("数据源不存在");
        assertThat(result.getCreateTime()).isNotNull();
        assertThat(result.getStartTime()).isNotBlank();
        assertThat(result.getEndTime()).isNotBlank();
    }

    @Test
    void recordDispatchFailureReusesAlreadyBuiltInstanceCode() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        JobInstanceService service = new JobInstanceService(jobInstanceRepository, jobRepository, mock(JobInstanceMapper.class));
        when(jobInstanceRepository.save(any(JobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobInstance instance = new JobInstance();
        instance.setJobCode("job-1");
        instance.setJobName("测试任务");
        instance.setInstanceCode("job-1-instance");

        JobInstance result = service.recordDispatchFailure("job-1", instance, new IllegalStateException("编排解析失败"));

        assertThat(result.getInstanceCode()).isEqualTo("job-1-instance");
        assertThat(result.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
        assertThat(result.getJobMessage()).contains("编排解析失败");
        assertThat(result.getEndTime()).isNotBlank();
    }
}
