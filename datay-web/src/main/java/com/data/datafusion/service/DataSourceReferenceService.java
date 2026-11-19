package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.domain.DataSync;
import com.data.datafusion.domain.DataSyncTableConfig;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 查询数据源被哪些任务、模型或服务引用，供删除前提示和强制删除确认使用。
 */
@Service
@Transactional(readOnly = true)
public class DataSourceReferenceService {

    private final JobRepository jobRepository;

    private final ETLNodeRepository etlNodeRepository;

    private final ETLTaskRepository etlTaskRepository;

    private final DataSyncRepository dataSyncRepository;

    private final DataSyncTableConfigRepository dataSyncTableConfigRepository;

    private final DataCompareTaskRepository dataCompareTaskRepository;

    private final DataQualityTaskRepository dataQualityTaskRepository;

    private final MetadataCollectTaskRepository metadataCollectTaskRepository;

    private final DataApiRepository dataApiRepository;

    private final AnalysisDatasetRepository analysisDatasetRepository;

    private final AnalysisDashboardRepository analysisDashboardRepository;

    private final DataModelRepository dataModelRepository;

    public DataSourceReferenceService(
        JobRepository jobRepository,
        ETLNodeRepository etlNodeRepository,
        ETLTaskRepository etlTaskRepository,
        DataSyncRepository dataSyncRepository,
        DataSyncTableConfigRepository dataSyncTableConfigRepository,
        DataCompareTaskRepository dataCompareTaskRepository,
        DataQualityTaskRepository dataQualityTaskRepository,
        MetadataCollectTaskRepository metadataCollectTaskRepository,
        DataApiRepository dataApiRepository,
        AnalysisDatasetRepository analysisDatasetRepository,
        AnalysisDashboardRepository analysisDashboardRepository,
        DataModelRepository dataModelRepository
    ) {
        this.jobRepository = jobRepository;
        this.etlNodeRepository = etlNodeRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.dataSyncRepository = dataSyncRepository;
        this.dataSyncTableConfigRepository = dataSyncTableConfigRepository;
        this.dataCompareTaskRepository = dataCompareTaskRepository;
        this.dataQualityTaskRepository = dataQualityTaskRepository;
        this.metadataCollectTaskRepository = metadataCollectTaskRepository;
        this.dataApiRepository = dataApiRepository;
        this.analysisDatasetRepository = analysisDatasetRepository;
        this.analysisDashboardRepository = analysisDashboardRepository;
        this.dataModelRepository = dataModelRepository;
    }

    /**
     * 查询指定数据源的全部直接引用。
     *
     * @param dataSourceId 数据源 ID。
     * @return 去重后的引用列表。
     */
    public List<DataSourceReferenceDTO> findReferences(String dataSourceId) {
        if (!StringUtils.hasText(dataSourceId)) {
            return List.of();
        }
        Map<String, DataSourceReferenceDTO> references = new LinkedHashMap<>();

        // 自定义 SQL 任务：数据源 ID 保存在 Job.jobContext 中。
        for (Job job : jobRepository.findByTypeAndJobContextContaining(TaskConstants.TASK_TYPE_SQL, dataSourceId)) {
            put(references, new DataSourceReferenceDTO("JOB", "SQL 任务", job.getId(), job.getJobName()));
        }

        // ETL 任务：设计器节点 config 中保存数据源 ID，兼容历史 Job 上下文中仍保留 ID 的情况。
        Set<String> etlTaskIds = new LinkedHashSet<>();
        for (ETLNode node : etlNodeRepository.findByConfigContaining(dataSourceId)) {
            if (StringUtils.hasText(node.getTaskId())) {
                etlTaskIds.add(node.getTaskId());
            }
        }
        for (ETLTask task : etlTaskRepository.findByJobContextContaining(dataSourceId)) {
            if (StringUtils.hasText(task.getId())) {
                etlTaskIds.add(task.getId());
            }
        }
        for (String taskId : etlTaskIds) {
            etlTaskRepository
                .findById(taskId)
                .ifPresent(task -> put(references, new DataSourceReferenceDTO("ETL_TASK", "ETL 任务", task.getId(), task.getTaskName())));
        }

        Set<String> dataSyncIds = new LinkedHashSet<>();
        for (DataSync dataSync : dataSyncRepository.findBySourceOrTarget(dataSourceId, dataSourceId)) {
            dataSyncIds.add(dataSync.getId());
        }
        for (
            DataSyncTableConfig tableConfig : dataSyncTableConfigRepository.findBySrcDatasourceOrDesDatasource(dataSourceId, dataSourceId)
        ) {
            if (StringUtils.hasText(tableConfig.getSyncTask())) {
                dataSyncIds.add(tableConfig.getSyncTask());
            }
        }
        for (String dataSyncId : dataSyncIds) {
            dataSyncRepository
                .findById(dataSyncId)
                .ifPresent(dataSync ->
                    put(references, new DataSourceReferenceDTO("DATA_SYNC", "数据同步任务", dataSync.getId(), dataSync.getJobName()))
                );
        }

        for (
            DataCompareTask task : dataCompareTaskRepository.findByLeftDataSourceIdOrRightDataSourceId(dataSourceId, dataSourceId)
        ) {
            put(references, new DataSourceReferenceDTO("DATA_COMPARE", "数据比对任务", task.getId(), task.getTaskName()));
        }

        for (DataQualityTask task : dataQualityTaskRepository.findByDataSourceId(dataSourceId)) {
            put(references, new DataSourceReferenceDTO("DATA_QUALITY", "数据质量任务", task.getId(), task.getTaskName()));
        }

        for (MetadataCollectTask task : metadataCollectTaskRepository.findAllByDataSourceId(dataSourceId)) {
            put(
                references,
                new DataSourceReferenceDTO("METADATA_COLLECT", "元数据采集任务", task.getId(), task.getTaskName(), true)
            );
        }

        for (DataApi api : dataApiRepository.findAllByDataSourceId(dataSourceId)) {
            put(references, new DataSourceReferenceDTO("DATA_API", "数据服务", api.getId(), api.getName()));
        }

        for (AnalysisDataset dataset : analysisDatasetRepository.findAllByDataSourceId(dataSourceId)) {
            put(references, new DataSourceReferenceDTO("ANALYSIS_DATASET", "分析数据集", dataset.getId(), dataset.getName()));
        }

        for (AnalysisDashboard dashboard : analysisDashboardRepository.findAllByDataSourceId(dataSourceId)) {
            put(references, new DataSourceReferenceDTO("ANALYSIS_DASHBOARD", "分析看板", dashboard.getId(), dashboard.getName()));
        }

        for (DataModel model : dataModelRepository.findAllByDataSourceId(dataSourceId)) {
            put(references, new DataSourceReferenceDTO("DATA_MODEL", "数据模型", model.getId(), model.getName()));
        }

        return new ArrayList<>(references.values());
    }

    private static void put(Map<String, DataSourceReferenceDTO> references, DataSourceReferenceDTO reference) {
        references.putIfAbsent(reference.getType() + ":" + reference.getId(), reference);
    }
}
