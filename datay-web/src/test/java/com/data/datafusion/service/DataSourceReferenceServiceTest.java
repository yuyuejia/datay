package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.domain.DataSync;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.MetadataCollectTask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.repository.DataCompareTaskRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.repository.MetadataCollectTaskRepository;
import com.data.datafusion.service.dto.DataSourceReferenceDTO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataSourceReferenceServiceTest {

    private static final String DS_ID = "ds-1";

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final ETLNodeRepository etlNodeRepository = mock(ETLNodeRepository.class);
    private final ETLTaskRepository etlTaskRepository = mock(ETLTaskRepository.class);
    private final DataSyncRepository dataSyncRepository = mock(DataSyncRepository.class);
    private final DataSyncTableConfigRepository dataSyncTableConfigRepository = mock(DataSyncTableConfigRepository.class);
    private final DataCompareTaskRepository dataCompareTaskRepository = mock(DataCompareTaskRepository.class);
    private final DataQualityTaskRepository dataQualityTaskRepository = mock(DataQualityTaskRepository.class);
    private final MetadataCollectTaskRepository metadataCollectTaskRepository = mock(MetadataCollectTaskRepository.class);
    private final DataApiRepository dataApiRepository = mock(DataApiRepository.class);
    private final AnalysisDatasetRepository analysisDatasetRepository = mock(AnalysisDatasetRepository.class);
    private final AnalysisDashboardRepository analysisDashboardRepository = mock(AnalysisDashboardRepository.class);
    private final DataModelRepository dataModelRepository = mock(DataModelRepository.class);

    private final DataSourceReferenceService service = new DataSourceReferenceService(
        jobRepository,
        etlNodeRepository,
        etlTaskRepository,
        dataSyncRepository,
        dataSyncTableConfigRepository,
        dataCompareTaskRepository,
        dataQualityTaskRepository,
        metadataCollectTaskRepository,
        dataApiRepository,
        analysisDatasetRepository,
        analysisDashboardRepository,
        dataModelRepository
    );

    @Test
    void returnsAllDirectReferencesAndMarksCascadeDeletes() {
        Job sqlJob = new Job();
        sqlJob.setId("job-1");
        sqlJob.setJobName("订单同步 SQL");
        when(jobRepository.findByTypeAndJobContextContaining(TaskConstants.TASK_TYPE_SQL, DS_ID)).thenReturn(List.of(sqlJob));

        ETLNode firstNode = node("etl-1");
        ETLNode secondNode = node("etl-1");
        when(etlNodeRepository.findByConfigContaining(DS_ID)).thenReturn(List.of(firstNode, secondNode));
        ETLTask etlTask = new ETLTask();
        etlTask.setId("etl-1");
        etlTask.setTaskName("订单 ETL");
        when(etlTaskRepository.findByJobContextContaining(DS_ID)).thenReturn(List.of());
        when(etlTaskRepository.findById("etl-1")).thenReturn(Optional.of(etlTask));

        DataSync dataSync = new DataSync();
        dataSync.setId("sync-1");
        dataSync.setJobName("订单同步");
        when(dataSyncRepository.findBySourceOrTarget(DS_ID, DS_ID)).thenReturn(List.of(dataSync));
        when(dataSyncRepository.findById("sync-1")).thenReturn(Optional.of(dataSync));

        DataCompareTask compareTask = new DataCompareTask();
        compareTask.setId("compare-1");
        compareTask.setTaskName("订单比对");
        when(dataCompareTaskRepository.findByLeftDataSourceIdOrRightDataSourceId(DS_ID, DS_ID)).thenReturn(List.of(compareTask));

        DataQualityTask qualityTask = new DataQualityTask();
        qualityTask.setId("quality-1");
        qualityTask.setTaskName("订单质检");
        when(dataQualityTaskRepository.findByDataSourceId(DS_ID)).thenReturn(List.of(qualityTask));

        MetadataCollectTask metadataTask = new MetadataCollectTask();
        metadataTask.setId("metadata-1");
        metadataTask.setTaskName("订单元数据");
        when(metadataCollectTaskRepository.findAllByDataSourceId(DS_ID)).thenReturn(List.of(metadataTask));

        DataApi api = new DataApi();
        api.setId("api-1");
        api.setName("订单查询服务");
        when(dataApiRepository.findAllByDataSourceId(DS_ID)).thenReturn(List.of(api));

        AnalysisDataset dataset = new AnalysisDataset();
        dataset.setId("dataset-1");
        dataset.setName("订单数据集");
        when(analysisDatasetRepository.findAllByDataSourceId(DS_ID)).thenReturn(List.of(dataset));

        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setId("dashboard-1");
        dashboard.setName("订单看板");
        when(analysisDashboardRepository.findAllByDataSourceId(DS_ID)).thenReturn(List.of(dashboard));

        DataModel model = new DataModel();
        model.setId("model-1");
        model.setName("订单模型");
        when(dataModelRepository.findAllByDataSourceId(DS_ID)).thenReturn(List.of(model));

        List<DataSourceReferenceDTO> references = service.findReferences(DS_ID);

        assertThat(references).extracting(DataSourceReferenceDTO::getType).containsExactly(
            "JOB",
            "ETL_TASK",
            "DATA_SYNC",
            "DATA_COMPARE",
            "DATA_QUALITY",
            "METADATA_COLLECT",
            "DATA_API",
            "ANALYSIS_DATASET",
            "ANALYSIS_DASHBOARD",
            "DATA_MODEL"
        );
        assertThat(references).filteredOn(reference -> "METADATA_COLLECT".equals(reference.getType())).singleElement().satisfies(
            reference -> assertThat(reference.isCascadeDelete()).isTrue()
        );
        assertThat(references).filteredOn(reference -> "ETL_TASK".equals(reference.getType())).hasSize(1);
    }

    @Test
    void returnsEmptyWhenIdIsBlank() {
        assertThat(service.findReferences(" ")).isEmpty();
    }

    private static ETLNode node(String taskId) {
        ETLNode node = new ETLNode();
        node.setTaskId(taskId);
        return node;
    }
}
