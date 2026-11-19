package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.Job;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DatasetPersistenceService} 配置校验、状态判定和任务节点生成的单元测试。
 */
class DatasetPersistenceServiceTest {

    private AnalysisDatasetRepository datasetRepository;
    private ETLTaskRepository etlTaskRepository;
    private JobRepository jobRepository;
    private ETLTaskService etlTaskService;
    private JobService jobService;
    private DataSourceService dataSourceService;
    private MetricQueryService metricQueryService;
    private DatasetPersistenceService service;

    @BeforeEach
    void setUp() {
        datasetRepository = mock(AnalysisDatasetRepository.class);
        etlTaskRepository = mock(ETLTaskRepository.class);
        jobRepository = mock(JobRepository.class);
        etlTaskService = mock(ETLTaskService.class);
        jobService = mock(JobService.class);
        dataSourceService = mock(DataSourceService.class);
        metricQueryService = mock(MetricQueryService.class);
        service = new DatasetPersistenceService(
            datasetRepository,
            etlTaskRepository,
            jobRepository,
            etlTaskService,
            jobService,
            dataSourceService,
            metricQueryService,
            new ObjectMapper()
        );
        when(dataSourceService.findOne("target-1")).thenReturn(Optional.of(dataSource("target-1")));
        when(dataSourceService.findOne("source-1")).thenReturn(Optional.of(dataSource("source-1")));
        when(jobService.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId("job-1");
            return job;
        });
        when(etlTaskService.saveWithoutJob(any(ETLTaskDTO.class))).thenAnswer(invocation -> {
            ETLTaskDTO task = invocation.getArgument(0);
            task.setId("task-1");
            return task;
        });
        when(datasetRepository.save(any(AnalysisDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldRejectSqlDatasetWithParameterPlaceholder() {
        AnalysisDatasetDTO dto = persistenceDto();
        dto.setDefinition("{\"sql\":\"SELECT * FROM orders WHERE id = ${orderId}\"}");

        assertThatThrownBy(() -> service.validateConfig(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("${param}");
    }

    @Test
    void shouldRejectUnsafeTargetTable() {
        AnalysisDatasetDTO dto = persistenceDto();
        dto.setPersistTargetTable("bad table;drop");

        assertThatThrownBy(() -> service.validateConfig(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("目标表");
    }

    @Test
    void shouldGenerateBatchTaskAndOutputNodeForSqlDataset() {
        AnalysisDataset entity = sqlDataset();

        AnalysisDataset result = service.sync(entity);

        assertThat(result.getPersistStatus()).isEqualTo(AnalysisDataset.PERSIST_STATUS_PENDING);
        assertThat(result.getPersistJobId()).isEqualTo("job-1");
        assertThat(result.getPersistEtlTaskId()).isEqualTo("task-1");
        org.mockito.ArgumentCaptor<ETLTaskDTO> captor = org.mockito.ArgumentCaptor.forClass(ETLTaskDTO.class);
        verify(etlTaskService).saveWithoutJob(captor.capture());
        ETLTaskDTO task = captor.getValue();
        assertThat(task.getType()).isEqualTo(TaskConstants.ETL_TASK_KIND_BATCH);
        assertThat(task.getNodes()).hasSize(2);
        ETLNodeDTO input = task.getNodes().get(0);
        ETLNodeDTO output = task.getNodes().get(1);
        assertThat(input.getType()).isEqualTo("SqlInput");
        assertThat(input.getConfig()).contains("SELECT region FROM orders");
        assertThat(output.getType()).isEqualTo("StreamJdbcOutput");
        assertThat(output.getConfig()).contains("\"schema\":\"main\"");
        assertThat(output.getConfig()).contains("\"model\":\"overwrite\"");
        assertThat(output.getConfig()).contains("\"dropIfTableExists\":true");
        verify(jobService).offline(any(Job.class));
    }

    @Test
    void shouldGenerateAppendOutputWithoutDroppingTable() {
        AnalysisDataset entity = sqlDataset();
        entity.setPersistWriteMode(AnalysisDataset.PERSIST_MODE_APPEND);

        service.sync(entity);

        org.mockito.ArgumentCaptor<ETLTaskDTO> captor = org.mockito.ArgumentCaptor.forClass(ETLTaskDTO.class);
        verify(etlTaskService).saveWithoutJob(captor.capture());
        ETLNodeDTO output = captor.getValue().getNodes().get(1);
        assertThat(output.getConfig()).contains("\"model\":\"append\"");
        assertThat(output.getConfig()).doesNotContain("dropIfTableExists");
    }

    @Test
    void shouldOnlyUsePersistedResultWhenStatusAndHashMatch() {
        AnalysisDataset entity = sqlDataset();
        entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_READY);
        entity.setPersistLastSuccessTime(ZonedDateTime.now());
        entity.setPersistDefinitionHash(service.definitionHash(entity));
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(entity));

        assertThat(service.isPersistedUsable("ds-1")).isTrue();

        entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_STALE);
        assertThat(service.isPersistedUsable("ds-1")).isFalse();
    }

    @Test
    void shouldDetectStaleResultWhenDefinitionChanges() {
        AnalysisDataset entity = sqlDataset();
        entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_READY);
        entity.setPersistLastSuccessTime(ZonedDateTime.now());
        entity.setPersistDefinitionHash(service.definitionHash(entity));
        entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_READY);
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(entity));

        entity.setDefinition("{\"sql\":\"SELECT region, amount FROM orders\"}");

        assertThat(service.isPersistedUsable("ds-1")).isFalse();
    }

    private AnalysisDatasetDTO persistenceDto() {
        AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
        dto.setType(AnalysisDataset.TYPE_SQL);
        dto.setDefinition("{\"sql\":\"SELECT region FROM orders\"}");
        dto.setPersistEnabled(true);
        dto.setPersistTargetDataSourceId("target-1");
        dto.setPersistTargetTable("dataset_result");
        dto.setPersistWriteMode(AnalysisDataset.PERSIST_MODE_REPLACE);
        return dto;
    }

    private AnalysisDataset sqlDataset() {
        AnalysisDataset entity = new AnalysisDataset();
        entity.setId("ds-1");
        entity.setTenantId("0");
        entity.setName("测试数据集");
        entity.setCode("test_dataset");
        entity.setType(AnalysisDataset.TYPE_SQL);
        entity.setDataSourceId("source-1");
        entity.setDefinition("{\"sql\":\"SELECT region FROM orders\"}");
        entity.setPersistEnabled(Boolean.TRUE);
        entity.setPersistTargetDataSourceId("target-1");
        entity.setPersistTargetTable("dataset_result");
        entity.setPersistWriteMode(AnalysisDataset.PERSIST_MODE_REPLACE);
        entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_DISABLED);
        return entity;
    }

    private DataSourceDTO dataSource(String id) {
        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId(id);
        dataSource.setName("目标库");
        dataSource.setType("DUCKDB");
        dataSource.setSchemaName("main");
        return dataSource;
    }
}
