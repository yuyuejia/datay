package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.etl.ETLNodeTranslatorRegistry;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ETLTaskServiceTest {

    @Test
    void updateKeepsExistingSchedulingWhenFieldsAreMissing() {
        ETLTaskRepository etlTaskRepository = mock(ETLTaskRepository.class);
        ETLNodeRepository etlNodeRepository = mock(ETLNodeRepository.class);
        ETLEdgeRepository etlEdgeRepository = mock(ETLEdgeRepository.class);
        ETLTaskMapper etlTaskMapper = mock(ETLTaskMapper.class);
        ETLNodeMapper etlNodeMapper = mock(ETLNodeMapper.class);
        ETLEdgeMapper etlEdgeMapper = mock(ETLEdgeMapper.class);
        JobService jobService = mock(JobService.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        ETLNodeTranslatorRegistry translatorRegistry = mock(ETLNodeTranslatorRegistry.class);

        ETLTaskService service = new ETLTaskService(
            etlTaskRepository,
            etlNodeRepository,
            etlEdgeRepository,
            etlTaskMapper,
            etlNodeMapper,
            etlEdgeMapper,
            jobService,
            dataSourceService,
            translatorRegistry
        );

        ETLTask existing = new ETLTask();
        existing.setId("etl-1");
        existing.setCron("0 0 2 * * ?");
        existing.setRetryCount(2);
        existing.setTimeoutSeconds(20);
        existing.setPriority(3);
        when(etlTaskRepository.findById("etl-1")).thenReturn(Optional.of(existing));

        ETLTaskDTO update = new ETLTaskDTO();
        update.setId("etl-1");
        update.setTaskName("renamed");
        update.setNodes(Collections.emptyList());
        update.setEdges(Collections.emptyList());
        when(etlTaskMapper.toEntity(any(ETLTaskDTO.class))).thenAnswer(invocation -> {
            ETLTaskDTO dto = invocation.getArgument(0);
            ETLTask entity = new ETLTask();
            entity.setId(dto.getId());
            entity.setTaskName(dto.getTaskName());
            entity.setCron(dto.getCron());
            entity.setRetryCount(dto.getRetryCount());
            entity.setTimeoutSeconds(dto.getTimeoutSeconds());
            entity.setPriority(dto.getPriority());
            return entity;
        });
        when(etlNodeMapper.toEntity(anyList())).thenReturn(Collections.emptyList());
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(Collections.emptyList());
        when(etlTaskRepository.save(any(ETLTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobService.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId("job-1");
            return job;
        });
        when(etlTaskMapper.toDto(any(ETLTask.class))).thenReturn(new ETLTaskDTO());

        service.update(update);

        assertThat(update.getCron()).isEqualTo("0 0 2 * * ?");
        assertThat(update.getRetryCount()).isEqualTo(2);
        assertThat(update.getTimeoutSeconds()).isEqualTo(20);
        assertThat(update.getPriority()).isEqualTo(3);

        org.mockito.ArgumentCaptor<Job> jobCaptor = org.mockito.ArgumentCaptor.forClass(Job.class);
        org.mockito.Mockito.verify(jobService).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getPriority()).isEqualTo(3);
    }

    @Test
    void onlineAppliesPriorityToTaskAndJob() {
        ETLTaskRepository etlTaskRepository = mock(ETLTaskRepository.class);
        ETLNodeRepository etlNodeRepository = mock(ETLNodeRepository.class);
        ETLEdgeRepository etlEdgeRepository = mock(ETLEdgeRepository.class);
        ETLTaskMapper etlTaskMapper = mock(ETLTaskMapper.class);
        ETLNodeMapper etlNodeMapper = mock(ETLNodeMapper.class);
        ETLEdgeMapper etlEdgeMapper = mock(ETLEdgeMapper.class);
        JobService jobService = mock(JobService.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        ETLNodeTranslatorRegistry translatorRegistry = mock(ETLNodeTranslatorRegistry.class);

        ETLTaskService service = new ETLTaskService(
            etlTaskRepository,
            etlNodeRepository,
            etlEdgeRepository,
            etlTaskMapper,
            etlNodeMapper,
            etlEdgeMapper,
            jobService,
            dataSourceService,
            translatorRegistry
        );

        ETLTask existing = new ETLTask();
        existing.setId("etl-1");
        existing.setTaskName("task");
        existing.setJobId("job-1");
        existing.setPriority(1);
        when(etlTaskRepository.findById("etl-1")).thenReturn(Optional.of(existing));

        Job job = new Job();
        job.setId("job-1");
        job.setPriority(1);
        when(jobService.findOneJob("job-1")).thenReturn(Optional.of(job));
        when(etlTaskRepository.save(any(ETLTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(etlTaskMapper.toDto(any(ETLTask.class))).thenReturn(new ETLTaskDTO());

        service.online("etl-1", "0 0 2 * * ?", 0, 0, 3);

        assertThat(existing.getPriority()).isEqualTo(3);
        assertThat(job.getPriority()).isEqualTo(3);
        org.mockito.Mockito.verify(jobService).online(job, false);
    }
}
