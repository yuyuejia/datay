package com.data.datafusion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataSource;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.apppackage.AppPackageJson;
import com.data.datafusion.service.apppackage.model.AppPackageEtlTask;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.dto.ETLTaskImportResultDTO;
import com.data.datafusion.service.dto.ETLTaskTransferDTO;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link ETLTaskTransferService} 导出 / 导入的单元测试。
 */
class ETLTaskTransferServiceTest {

    private ETLTaskService eTLTaskService;
    private ETLTaskRepository eTLTaskRepository;
    private DataSourceRepository dataSourceRepository;
    private DataModelRepository dataModelRepository;
    private JobRepository jobRepository;
    private ETLTaskTransferService service;

    @BeforeEach
    void setUp() {
        eTLTaskService = mock(ETLTaskService.class);
        eTLTaskRepository = mock(ETLTaskRepository.class);
        dataSourceRepository = mock(DataSourceRepository.class);
        dataModelRepository = mock(DataModelRepository.class);
        jobRepository = mock(JobRepository.class);
        service = new ETLTaskTransferService(
            eTLTaskService,
            eTLTaskRepository,
            dataSourceRepository,
            dataModelRepository,
            jobRepository
        );
    }

    @Test
    void exportTaskCollectsReferences() {
        ETLTaskDTO dto = new ETLTaskDTO();
        dto.setId("task-1");
        dto.setTaskName("订单同步");
        dto.setTaskCode("order_sync");
        dto.setCron("0 0 * * * ?");
        dto.setNodes(List.of(node("n1", "SqlInput", "{\"sourceId\":\"ds-1\"}")));
        dto.setEdges(List.of());
        when(eTLTaskService.findOne("task-1")).thenReturn(Optional.of(dto));

        DataSource source = new DataSource();
        source.setId("ds-1");
        source.setName("生产库");
        source.setType("MYSQL");
        source.setUrl("jdbc:mysql://localhost:3306/db");
        when(dataSourceRepository.findById("ds-1")).thenReturn(Optional.of(source));

        ETLTaskTransferDTO transfer = service.exportTask("task-1");

        assertThat(transfer.task.taskCode).isEqualTo("order_sync");
        assertThat(transfer.task.cron).isEqualTo("0 0 * * * ?");
        assertThat(transfer.references.dataSources).hasSize(1);
        assertThat(transfer.references.dataSources.get(0).name).isEqualTo("生产库");
        assertThat(transfer.references.dataSources.get(0).url).isEqualTo("jdbc:mysql://localhost:3306/db");
    }

    @Test
    void importTaskReusesAndRemapsReferencesAndRenamesCode() throws Exception {
        ETLTaskTransferDTO transfer = new ETLTaskTransferDTO();
        AppPackageEtlTask task = new AppPackageEtlTask();
        task.taskName = "订单同步";
        task.taskCode = "order_sync";
        task.cron = "0 0 * * * ?";
        task.nodes = List.of(transferNode("n1", "SqlInput", "{\"sourceId\":\"ds-1\",\"modelId\":\"m-1\"}"));
        task.edges = List.of();
        transfer.task = task;

        ETLTaskTransferDTO.DataSourceRef dsRef = new ETLTaskTransferDTO.DataSourceRef();
        dsRef.oldId = "ds-1";
        dsRef.name = "生产库";
        dsRef.type = "MYSQL";
        dsRef.url = "jdbc:mysql://localhost:3306/db";
        transfer.references.dataSources = List.of(dsRef);

        ETLTaskTransferDTO.ModelRef modelRef = new ETLTaskTransferDTO.ModelRef();
        modelRef.oldId = "m-1";
        modelRef.code = "fact_order";
        modelRef.name = "订单事实表";
        transfer.references.models = List.of(modelRef);

        // 编码冲突：已有同名任务编码
        com.data.datafusion.domain.ETLTask existing = new com.data.datafusion.domain.ETLTask();
        existing.setId("existing");
        existing.setTaskCode("order_sync");
        when(eTLTaskRepository.findFirstByTaskCode("order_sync")).thenReturn(Optional.of(existing));
        when(eTLTaskRepository.findFirstByTaskCode("order_sync_copy")).thenReturn(Optional.empty());
        when(jobRepository.findFirstByJobName(any())).thenReturn(Optional.empty());

        DataSource source = new DataSource();
        source.setId("ds-new");
        when(dataSourceRepository.findFirstByNameAndTypeAndUrl("生产库", "MYSQL", "jdbc:mysql://localhost:3306/db"))
            .thenReturn(Optional.of(source));

        DataModel model = new DataModel();
        model.setId("m-new");
        when(dataModelRepository.findFirstByCode("fact_order")).thenReturn(Optional.of(model));

        when(eTLTaskService.save(any())).thenAnswer(invocation -> {
            ETLTaskDTO saved = invocation.getArgument(0);
            saved.setId("task-new");
            return saved;
        });

        ETLTaskImportResultDTO result = service.importTask(transfer);

        assertThat(result.taskId).isEqualTo("task-new");
        assertThat(result.taskCode).isEqualTo("order_sync_copy");
        assertThat(result.renamed).isTrue();
        assertThat(result.dataSources).hasSize(1);
        assertThat(result.dataSources.get(0).matched).isTrue();
        assertThat(result.dataSources.get(0).newId).isEqualTo("ds-new");
        assertThat(result.models.get(0).matched).isTrue();
        assertThat(result.models.get(0).newId).isEqualTo("m-new");

        ArgumentCaptor<ETLTaskDTO> captor = ArgumentCaptor.forClass(ETLTaskDTO.class);
        org.mockito.Mockito.verify(eTLTaskService).save(captor.capture());
        ETLTaskDTO saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_OFFLINE);
        assertThat(saved.getCron()).isEqualTo("0 0 * * * ?");
        JsonNode config = AppPackageJson.mapper().readTree(saved.getNodes().get(0).getConfig());
        assertThat(config.get("sourceId").asText()).isEqualTo("ds-new");
        assertThat(config.get("modelId").asText()).isEqualTo("m-new");
    }

    private ETLNodeDTO node(String code, String type, String config) {
        ETLNodeDTO node = new ETLNodeDTO();
        node.setCode(code);
        node.setType(type);
        node.setConfig(config);
        return node;
    }

    private AppPackageEtlTask.AppPackageEtlNode transferNode(String code, String type, String config) throws Exception {
        AppPackageEtlTask.AppPackageEtlNode node = new AppPackageEtlTask.AppPackageEtlNode();
        node.code = code;
        node.type = type;
        node.config = AppPackageJson.mapper().readTree(config);
        return node;
    }
}
