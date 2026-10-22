package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobDTO;
import com.data.datafusion.service.mapper.JobMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobServiceTest {

    @Test
    void updateKeepsExistingSchedulingWhenFieldsAreMissing() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobService jobService = new JobService(jobRepository, jobMapper, jobInstanceService);

        Job existing = new Job();
        existing.setId("job-1");
        existing.setCron("0 0 1 * * ?");
        existing.setRetryCount(3);
        existing.setTimeoutSeconds(30);
        existing.setPriority(3);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(existing));

        JobDTO update = new JobDTO();
        update.setId("job-1");
        update.setJobName("renamed");
        when(jobMapper.toEntity(any(JobDTO.class))).thenAnswer(invocation -> {
            JobDTO dto = invocation.getArgument(0);
            Job entity = new Job();
            entity.setId(dto.getId());
            entity.setJobName(dto.getJobName());
            entity.setCron(dto.getCron());
            entity.setRetryCount(dto.getRetryCount());
            entity.setTimeoutSeconds(dto.getTimeoutSeconds());
            entity.setPriority(dto.getPriority());
            return entity;
        });
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toDto(any(Job.class))).thenReturn(new JobDTO());

        jobService.update(update);

        assertThat(update.getCron()).isEqualTo("0 0 1 * * ?");
        assertThat(update.getRetryCount()).isEqualTo(3);
        assertThat(update.getTimeoutSeconds()).isEqualTo(30);
        assertThat(update.getPriority()).isEqualTo(3);
    }

    @Test
    void updateAllowsExplicitlyClearingSchedulingValues() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobService jobService = new JobService(jobRepository, jobMapper, jobInstanceService);

        Job existing = new Job();
        existing.setId("job-1");
        existing.setCron("0 0 1 * * ?");
        existing.setRetryCount(3);
        existing.setTimeoutSeconds(30);
        existing.setPriority(3);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(existing));

        JobDTO update = new JobDTO();
        update.setId("job-1");
        update.setJobName("renamed");
        update.setCron("0 0 2 * * ?");
        update.setRetryCount(0);
        update.setTimeoutSeconds(0);
        update.setPriority(1);
        when(jobMapper.toEntity(any(JobDTO.class))).thenAnswer(invocation -> {
            JobDTO dto = invocation.getArgument(0);
            Job entity = new Job();
            entity.setId(dto.getId());
            entity.setCron(dto.getCron());
            entity.setRetryCount(dto.getRetryCount());
            entity.setTimeoutSeconds(dto.getTimeoutSeconds());
            entity.setPriority(dto.getPriority());
            return entity;
        });
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toDto(any(Job.class))).thenReturn(new JobDTO());

        jobService.update(update);

        assertThat(update.getCron()).isEqualTo("0 0 2 * * ?");
        assertThat(update.getRetryCount()).isZero();
        assertThat(update.getTimeoutSeconds()).isZero();
        assertThat(update.getPriority()).isEqualTo(1);
    }

    @Test
    void executeOnceSnapshotsPriorityToJobInstance() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobService jobService = new JobService(jobRepository, jobMapper, jobInstanceService);

        Job job = new Job();
        job.setId("job-1");
        job.setJobName("priority-job");
        job.setType("SHELL");
        job.setPriority(3);

        jobService.executeOnce(job);

        ArgumentCaptor<JobInstance> captor = ArgumentCaptor.forClass(JobInstance.class);
        verify(jobInstanceService).startInstance(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(3);
    }
}
