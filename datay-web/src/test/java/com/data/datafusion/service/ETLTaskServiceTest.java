package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.etl.ETLNodeTranslatorRegistry;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Test
    void onlineKeepsExistingPriorityWhenNotProvided() {
        ETLTaskRepository etlTaskRepository = mock(ETLTaskRepository.class);
        ETLTaskMapper etlTaskMapper = mock(ETLTaskMapper.class);
        JobService jobService = mock(JobService.class);
        ETLTaskService service = new ETLTaskService(
            etlTaskRepository,
            mock(ETLNodeRepository.class),
            mock(ETLEdgeRepository.class),
            etlTaskMapper,
            mock(ETLNodeMapper.class),
            mock(ETLEdgeMapper.class),
            jobService,
            mock(DataSourceService.class),
            mock(ETLNodeTranslatorRegistry.class)
        );

        ETLTask existing = new ETLTask();
        existing.setId("etl-1");
        existing.setJobId("job-1");
        existing.setPriority(1);
        when(etlTaskRepository.findById("etl-1")).thenReturn(Optional.of(existing));

        Job job = new Job();
        job.setId("job-1");
        job.setPriority(1);
        when(jobService.findOneJob("job-1")).thenReturn(Optional.of(job));
        when(etlTaskRepository.save(any(ETLTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(etlTaskMapper.toDto(any(ETLTask.class))).thenReturn(new ETLTaskDTO());

        service.online("etl-1", null, null, null, null);

        assertThat(existing.getPriority()).isEqualTo(1);
        assertThat(job.getPriority()).isEqualTo(1);
    }

    @Test
    void onlineDefaultsToNormalWhenCurrentPriorityIsMissing() {
        ETLTaskRepository etlTaskRepository = mock(ETLTaskRepository.class);
        ETLTaskMapper etlTaskMapper = mock(ETLTaskMapper.class);
        JobService jobService = mock(JobService.class);
        ETLTaskService service = new ETLTaskService(
            etlTaskRepository,
            mock(ETLNodeRepository.class),
            mock(ETLEdgeRepository.class),
            etlTaskMapper,
            mock(ETLNodeMapper.class),
            mock(ETLEdgeMapper.class),
            jobService,
            mock(DataSourceService.class),
            mock(ETLNodeTranslatorRegistry.class)
        );

        ETLTask existing = new ETLTask();
        existing.setId("etl-1");
        existing.setJobId("job-1");
        existing.setPriority(null);
        when(etlTaskRepository.findById("etl-1")).thenReturn(Optional.of(existing));

        Job job = new Job();
        job.setId("job-1");
        job.setPriority(null);
        when(jobService.findOneJob("job-1")).thenReturn(Optional.of(job));
        when(etlTaskRepository.save(any(ETLTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(etlTaskMapper.toDto(any(ETLTask.class))).thenReturn(new ETLTaskDTO());

        service.online("etl-1", null, null, null, null);

        assertThat(existing.getPriority()).isEqualTo(2);
        assertThat(job.getPriority()).isEqualTo(2);
    }

    @Test
    void generateJobJsonFillsCompleteDatasourceInfoForExtendedComponent() {
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

        String config = """
            {
              "sourceId": "ds-1",
              "schema": "demo",
              "table": "demo_table",
              "columns": ["id", "name"],
              "message": "hello plugin",
              "parallelism": "2"
            }
            """;

        ETLNodeDTO nodeDTO = new ETLNodeDTO();
        nodeDTO.setCode("node-1");
        nodeDTO.setType("ExampleHelloSource");
        nodeDTO.setConfig(config);

        ETLNode node = new ETLNode();
        node.setCode("node-1");
        node.setType("ExampleHelloSource");
        node.setConfig(config);

        ETLTaskDTO task = new ETLTaskDTO();
        task.setNodes(List.of(nodeDTO));
        task.setEdges(Collections.emptyList());

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId("ds-1");
        dataSource.setType("MYSQL");
        dataSource.setUrl("jdbc:mysql://127.0.0.1:3306/demo?useSSL=false");
        dataSource.setUsername("root");
        dataSource.setPassword("secret");
        dataSource.setHostname("127.0.0.1");
        dataSource.setPort("3306");
        dataSource.setExtraParams(Map.of("serverTimezone", "Asia/Shanghai"));

        when(etlNodeMapper.toEntity(anyList())).thenReturn(List.of(node));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(Collections.emptyList());
        when(dataSourceService.findOne("ds-1")).thenReturn(Optional.of(dataSource));

        JSONObject jobJson = JSONUtil.parseObj(service.generateETLJobJson(task));
        JSONObject unit = jobJson.getJSONArray("units").getJSONObject(0);
        JSONObject sourceId = unit.getJSONObject("sourceId");

        assertThat(unit.getStr(".name")).isEqualTo("ExampleHelloSource");
        assertThat(unit.getStr("parallelism")).isEqualTo("2");
        assertThat(unit.getStr("schema")).isEqualTo("demo");
        assertThat(unit.getStr("table")).isEqualTo("demo_table");
        assertThat(unit.getJSONArray("columns").toList(String.class)).containsExactly("id", "name");

        assertThat(sourceId != null).isTrue();
        assertThat(sourceId.getStr("type")).isEqualTo("MYSQL");
        assertThat(sourceId.getStr("url")).isEqualTo("jdbc:mysql://127.0.0.1:3306/demo?useSSL=false");
        assertThat(sourceId.getStr("driver")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(sourceId.getStr("username")).isEqualTo("root");
        assertThat(sourceId.getStr("password")).isEqualTo("secret");
        assertThat(sourceId.getStr("hostname")).isEqualTo("127.0.0.1");
        assertThat(sourceId.getStr("port")).isEqualTo("3306");
        assertThat(sourceId.getStr("dbschema")).isEqualTo("demo");
        assertThat(sourceId.getJSONObject("extraParams").getStr("serverTimezone")).isEqualTo("Asia/Shanghai");
    }
}
