package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.ETLTaskDirectoryRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobDTO;
import com.data.datafusion.service.mapper.JobMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobServiceTest {

    @Test
    void updateKeepsExistingSchedulingWhenFieldsAreMissing() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobService jobService = new JobService(jobRepository, mock(ETLTaskDirectoryRepository.class), jobMapper, jobInstanceService);

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
        JobService jobService = new JobService(jobRepository, mock(ETLTaskDirectoryRepository.class), jobMapper, jobInstanceService);

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
    void updateNormalizesInvalidPriorityToDefault() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobService jobService = new JobService(jobRepository, mock(ETLTaskDirectoryRepository.class), jobMapper, mock(JobInstanceService.class));

        Job existing = new Job();
        existing.setId("job-1");
        existing.setPriority(3);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(existing));

        JobDTO update = new JobDTO();
        update.setId("job-1");
        update.setPriority(99);
        when(jobMapper.toEntity(any(JobDTO.class))).thenAnswer(invocation -> {
            JobDTO dto = invocation.getArgument(0);
            Job entity = new Job();
            entity.setId(dto.getId());
            entity.setPriority(dto.getPriority());
            return entity;
        });
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toDto(any(Job.class))).thenReturn(new JobDTO());

        jobService.update(update);

        assertThat(update.getPriority()).isEqualTo(TaskConstants.DEFAULT_PRIORITY);
    }

    @Test
    void saveNormalizesMissingPriorityToDefault() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobService jobService = new JobService(jobRepository, mock(ETLTaskDirectoryRepository.class), jobMapper, mock(JobInstanceService.class));

        JobDTO create = new JobDTO();
        create.setJobName("new-job");
        when(jobMapper.toEntity(any(JobDTO.class))).thenAnswer(invocation -> {
            JobDTO dto = invocation.getArgument(0);
            Job entity = new Job();
            entity.setId("job-1");
            entity.setPriority(dto.getPriority());
            return entity;
        });
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toDto(any(Job.class))).thenReturn(new JobDTO());

        jobService.save(create);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        assertThat(create.getPriority()).isEqualTo(TaskConstants.DEFAULT_PRIORITY);
        assertThat(captor.getValue().getPriority()).isEqualTo(TaskConstants.DEFAULT_PRIORITY);
    }

    @Test
    void executeOnceSnapshotsPriorityToJobInstance() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobService jobService = new JobService(jobRepository, mock(ETLTaskDirectoryRepository.class), jobMapper, jobInstanceService);

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

    @Test
    void updateRejectsDagNestedInsideItself() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobService jobService = new JobService(
            jobRepository,
            mock(ETLTaskDirectoryRepository.class),
            mock(JobMapper.class),
            mock(JobInstanceService.class)
        );

        Job existing = new Job();
        existing.setId("dag-a");
        existing.setType(TaskConstants.TASK_TYPE_DAG);
        when(jobRepository.findById("dag-a")).thenReturn(Optional.of(existing));

        JobDTO update = new JobDTO();
        update.setId("dag-a");
        update.setType(TaskConstants.TASK_TYPE_DAG);
        update.setJobContext("{\"jobs\":[{\"id\":\"dag-a\"}],\"jobDepends\":[]}");

        assertThatThrownBy(() -> jobService.update(update))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("循环嵌套")
            .hasMessageContaining("dag-a -> dag-a");
    }

    @Test
    void updateRejectsIndirectDagNestingCycle() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobService jobService = new JobService(
            jobRepository,
            mock(ETLTaskDirectoryRepository.class),
            mock(JobMapper.class),
            mock(JobInstanceService.class)
        );

        Job dagA = new Job();
        dagA.setId("dag-a");
        dagA.setType(TaskConstants.TASK_TYPE_DAG);
        Job dagB = new Job();
        dagB.setId("dag-b");
        dagB.setType(TaskConstants.TASK_TYPE_DAG);
        dagB.setJobContext("{\"jobs\":[{\"id\":\"dag-a\"}],\"jobDepends\":[]}");

        when(jobRepository.findById("dag-a")).thenReturn(Optional.of(dagA));
        when(jobRepository.findById("dag-b")).thenReturn(Optional.of(dagB));

        JobDTO update = new JobDTO();
        update.setId("dag-a");
        update.setType(TaskConstants.TASK_TYPE_DAG);
        update.setJobContext("{\"jobs\":[{\"id\":\"dag-b\"}],\"jobDepends\":[]}");

        assertThatThrownBy(() -> jobService.update(update))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("循环嵌套")
            .hasMessageContaining("dag-a -> dag-b -> dag-a");
    }

    @Test
    void updateAcceptsValidNestedDag() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobMapper jobMapper = mock(JobMapper.class);
        JobService jobService = new JobService(
            jobRepository,
            mock(ETLTaskDirectoryRepository.class),
            jobMapper,
            mock(JobInstanceService.class)
        );

        Job dagA = new Job();
        dagA.setId("dag-a");
        dagA.setType(TaskConstants.TASK_TYPE_DAG);
        dagA.setPriority(2);
        Job dagB = new Job();
        dagB.setId("dag-b");
        dagB.setType(TaskConstants.TASK_TYPE_DAG);
        dagB.setJobContext("{\"jobs\":[{\"id\":\"sql-1\"}],\"jobDepends\":[]}");
        Job sqlJob = new Job();
        sqlJob.setId("sql-1");
        sqlJob.setType(TaskConstants.TASK_TYPE_SQL);
        when(jobRepository.findById("dag-a")).thenReturn(Optional.of(dagA));
        when(jobRepository.findById("dag-b")).thenReturn(Optional.of(dagB));
        when(jobRepository.findById("sql-1")).thenReturn(Optional.of(sqlJob));
        when(jobMapper.toEntity(any(JobDTO.class))).thenAnswer(invocation -> {
            JobDTO dto = invocation.getArgument(0);
            Job entity = new Job();
            entity.setId(dto.getId());
            entity.setType(dto.getType());
            return entity;
        });
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toDto(any(Job.class))).thenReturn(new JobDTO());

        JobDTO update = new JobDTO();
        update.setId("dag-a");
        update.setType(TaskConstants.TASK_TYPE_DAG);
        update.setJobContext("{\"jobs\":[{\"id\":\"dag-b\"}],\"jobDepends\":[]}");

        assertThat(jobService.update(update)).isNotNull();
        verify(jobRepository).save(any(Job.class));
    }

    @Test
    void updateRejectsNestingDeeperThanLimit() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobService jobService = new JobService(
            jobRepository,
            mock(ETLTaskDirectoryRepository.class),
            mock(JobMapper.class),
            mock(JobInstanceService.class)
        );

        Map<String, Job> jobs = new HashMap<>();
        int total = TaskConstants.MAX_DAG_NESTING_DEPTH + 2;
        for (int i = 0; i < total; i++) {
            Job job = new Job();
            job.setId("dag-" + i);
            job.setType(TaskConstants.TASK_TYPE_DAG);
            job.setJobContext("{\"jobs\":[{\"id\":\"dag-" + (i + 1) + "\"}],\"jobDepends\":[]}");
            jobs.put(job.getId(), job);
        }
        when(jobRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(jobs.get(invocation.getArgument(0))));

        JobDTO update = new JobDTO();
        update.setId("dag-0");
        update.setType(TaskConstants.TASK_TYPE_DAG);
        update.setJobContext("{\"jobs\":[{\"id\":\"dag-1\"}],\"jobDepends\":[]}");

        assertThatThrownBy(() -> jobService.update(update))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("嵌套层级");
    }

    @Test
    void updateRejectsReferenceToMissingJob() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobService jobService = new JobService(
            jobRepository,
            mock(ETLTaskDirectoryRepository.class),
            mock(JobMapper.class),
            mock(JobInstanceService.class)
        );

        Job existing = new Job();
        existing.setId("dag-a");
        existing.setType(TaskConstants.TASK_TYPE_DAG);
        when(jobRepository.findById("dag-a")).thenReturn(Optional.of(existing));
        when(jobRepository.findById("missing-job")).thenReturn(Optional.empty());

        JobDTO update = new JobDTO();
        update.setId("dag-a");
        update.setType(TaskConstants.TASK_TYPE_DAG);
        update.setJobContext("{\"jobs\":[{\"id\":\"missing-job\"}],\"jobDepends\":[]}");

        assertThatThrownBy(() -> jobService.update(update))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("引用的任务不存在");
    }
}
