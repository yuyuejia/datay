package com.data.datafusion.service.datamap;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.domain.DataSource;
import com.data.datafusion.domain.DataSync;
import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.MetadataCollectTask;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.MetadataCollectTaskRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dashboard.AnalysisDatasetResolver;
import com.data.datafusion.service.datamap.MetadataSnapshotLoader.ColumnSnapshot;
import com.data.datafusion.service.datamap.MetadataSnapshotLoader.Snapshot;
import com.data.datafusion.service.datamap.MetadataSnapshotLoader.TableSnapshot;
import com.data.datafusion.service.dto.datamap.DataMapAssetTypes;
import com.data.datafusion.service.dto.datamap.DataMapEdgeDTO;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import com.data.datafusion.service.lineage.SqlLineageParser;
import com.data.datafusion.service.lineage.SqlReferences;
import com.data.datafusion.service.lineage.TableRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把平台对象与采集元数据组装为数据地图节点和关系。
 */
@Component
public class DataMapGraphAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapGraphAssembler.class);

    private static final Pattern METRIC_FORMULA_REF = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");

    private final DataSourceRepository dataSourceRepository;
    private final MetadataCollectTaskRepository metadataCollectTaskRepository;
    private final ETLTaskRepository etlTaskRepository;
    private final ETLNodeRepository etlNodeRepository;
    private final DataSyncRepository dataSyncRepository;
    private final DataSyncTableConfigRepository dataSyncTableConfigRepository;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final MetricRepository metricRepository;
    private final AnalysisDatasetRepository analysisDatasetRepository;
    private final AnalysisDashboardRepository analysisDashboardRepository;
    private final DataApiRepository dataApiRepository;
    private final DataQualityTaskRepository dataQualityTaskRepository;
    private final MetadataSnapshotLoader metadataSnapshotLoader;
    private final EtlTableRefResolver etlTableRefResolver;
    private final SqlLineageParser sqlLineageParser;
    private final AnalysisDatasetResolver analysisDatasetResolver;
    private final ObjectMapper objectMapper;

    public DataMapGraphAssembler(
        DataSourceRepository dataSourceRepository,
        MetadataCollectTaskRepository metadataCollectTaskRepository,
        ETLTaskRepository etlTaskRepository,
        ETLNodeRepository etlNodeRepository,
        DataSyncRepository dataSyncRepository,
        DataSyncTableConfigRepository dataSyncTableConfigRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        MetricRepository metricRepository,
        AnalysisDatasetRepository analysisDatasetRepository,
        AnalysisDashboardRepository analysisDashboardRepository,
        DataApiRepository dataApiRepository,
        DataQualityTaskRepository dataQualityTaskRepository,
        MetadataSnapshotLoader metadataSnapshotLoader,
        EtlTableRefResolver etlTableRefResolver,
        SqlLineageParser sqlLineageParser,
        AnalysisDatasetResolver analysisDatasetResolver,
        ObjectMapper objectMapper
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.metadataCollectTaskRepository = metadataCollectTaskRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.etlNodeRepository = etlNodeRepository;
        this.dataSyncRepository = dataSyncRepository;
        this.dataSyncTableConfigRepository = dataSyncTableConfigRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.metricRepository = metricRepository;
        this.analysisDatasetRepository = analysisDatasetRepository;
        this.analysisDashboardRepository = analysisDashboardRepository;
        this.dataApiRepository = dataApiRepository;
        this.dataQualityTaskRepository = dataQualityTaskRepository;
        this.metadataSnapshotLoader = metadataSnapshotLoader;
        this.etlTableRefResolver = etlTableRefResolver;
        this.sqlLineageParser = sqlLineageParser;
        this.analysisDatasetResolver = analysisDatasetResolver;
        this.objectMapper = objectMapper;
    }

    public GraphBuildResult build(String tenantCode) {
        BuildContext context = new BuildContext();
        List<DataSource> dataSources = dataSourceRepository.findAll();
        for (DataSource dataSource : dataSources) {
            context.dataSources.put(dataSource.getId(), dataSource);
        }

        Map<String, Snapshot> snapshots = loadSnapshots(tenantCode, context);
        addDataSourceNodes(context, snapshots);
        addMetadataNodes(context, snapshots);
        addEtlNodes(context);
        addSyncNodes(context);
        addModelNodes(context);
        addMetricNodes(context);
        addDatasetNodes(context);
        addDashboardNodes(context);
        addApiNodes(context);
        addQualityNodes(context);
        applyDegrees(context);

        String coverage = snapshots.size() + "/" + dataSources.size();
        return new GraphBuildResult(new ArrayList<>(context.nodes.values()), new ArrayList<>(context.edges.values()), coverage);
    }

    private Map<String, Snapshot> loadSnapshots(String tenantCode, BuildContext context) {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        for (MetadataCollectTask task : metadataCollectTaskRepository.findAll()) {
            if (task.getDataSourceId() == null || !context.dataSources.containsKey(task.getDataSourceId())) {
                continue;
            }
            metadataSnapshotLoader.load(tenantCode, task).ifPresent(snapshot -> result.put(task.getDataSourceId(), snapshot));
        }
        return result;
    }

    private void addDataSourceNodes(BuildContext context, Map<String, Snapshot> snapshots) {
        for (DataSource dataSource : context.dataSources.values()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", dataSource.getType());
            meta.put("hostname", dataSource.getHostname());
            meta.put("port", dataSource.getPort());
            meta.put("database", dataSource.getDatabase());
            meta.put("defaultSchema", dataSource.getSchemaName());
            meta.put("metadataStatus", snapshots.containsKey(dataSource.getId()) ? "COLLECTED" : "MISSING");
            addNode(
                context,
                new DataMapNodeDTO(
                    sourceNodeId(dataSource.getId()),
                    DataMapAssetTypes.DATASOURCE,
                    dataSource.getName(),
                    null,
                    dataSource.getDescription(),
                    dataSource.getName(),
                    dataSource.getId(),
                    dataSource.getName(),
                    dataSource.getSchemaName(),
                    null,
                    0,
                    null,
                    timestamp(dataSource.getUpdateTime()),
                    0,
                    meta
                )
            );
            if (dataSource.getSchemaName() != null && !dataSource.getSchemaName().isBlank()) {
                ensureSchema(context, dataSource.getId(), null, dataSource.getSchemaName());
            }
        }
    }

    private void addMetadataNodes(BuildContext context, Map<String, Snapshot> snapshots) {
        for (Map.Entry<String, Snapshot> entry : snapshots.entrySet()) {
            String dataSourceId = entry.getKey();
            Snapshot snapshot = entry.getValue();
            for (TableSnapshot table : snapshot.tables()) {
                String tableNodeId = ensureTable(
                    context,
                    dataSourceId,
                    table.catalog(),
                    table.schema(),
                    table.table(),
                    "COLLECTED",
                    table.comment()
                );
                DataMapNodeDTO existing = context.nodes.get(tableNodeId);
                if (existing != null && table.comment() != null && !table.comment().isBlank()) {
                    Map<String, Object> meta = new LinkedHashMap<>(existing.meta());
                    meta.put("comment", table.comment());
                    meta.put("collectedAt", table.collectedAt());
                    context.nodes.put(tableNodeId, replaceDescription(existing, table.comment(), meta));
                }
            }
            for (ColumnSnapshot column : snapshot.columns()) {
                String tableNodeId = ensureTable(
                    context,
                    dataSourceId,
                    column.catalog(),
                    column.schema(),
                    column.table(),
                    "COLLECTED",
                    null
                );
                String columnNodeId = tableNodeId + "." + normalize(column.column());
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("dataType", column.dataType());
                meta.put("ordinal", column.ordinal());
                meta.put("length", column.length());
                meta.put("precision", column.precision());
                meta.put("scale", column.scale());
                meta.put("nullable", column.nullable());
                meta.put("primaryKey", column.primaryKey());
                meta.put("defaultValue", column.defaultValue());
                meta.put("comment", column.comment());
                meta.put("collectedAt", column.collectedAt());
                addNode(
                    context,
                    new DataMapNodeDTO(
                        columnNodeId,
                        DataMapAssetTypes.COLUMN,
                        column.column(),
                        null,
                        column.comment(),
                        joinTable(column.catalog(), column.schema(), column.table()) + "." + column.column(),
                        dataSourceId,
                        dataSourceName(context, dataSourceId),
                        column.schema(),
                        column.table(),
                        0,
                        null,
                        column.collectedAt(),
                        0,
                        meta
                    )
                );
                addEdge(context, tableNodeId, columnNodeId, "CONTAINS", "包含字段", "CONFIG");
            }
        }
    }

    private void addEtlNodes(BuildContext context) {
        Map<String, List<ETLNode>> nodesByTask = etlNodeRepository
            .findAll()
            .stream()
            .filter(node -> node.getTaskId() != null)
            .collect(Collectors.groupingBy(ETLNode::getTaskId, LinkedHashMap::new, Collectors.toList()));
        for (ETLTask task : etlTaskRepository.findAll()) {
            addNode(
                context,
                new DataMapNodeDTO(
                    etlNodeId(task.getId()),
                    DataMapAssetTypes.ETL_TASK,
                    firstNonBlank(task.getTaskName(), task.getTaskCode(), task.getId()),
                    task.getTaskCode(),
                    task.getTaskDesc(),
                    firstNonBlank(task.getTaskCode(), task.getTaskName()),
                    null,
                    null,
                    null,
                    null,
                    0,
                    task.getStatus(),
                    timestamp(task.getUpdateTime()),
                    0,
                    meta("taskType", task.getType(), "cron", task.getCron(), "directoryId", task.getDirectoryId())
                )
            );
            for (ETLNode node : nodesByTask.getOrDefault(task.getId(), List.of())) {
                JsonNode config = readJson(node.getConfig());
                if (config == null) {
                    continue;
                }
                for (EtlTableRefResolver.TableTarget target : etlTableRefResolver.readTargets(node, config, null)) {
                    if (target.dataSourceId() == null) {
                        continue;
                    }
                    addTableEdge(context, etlNodeId(task.getId()), target, "READS", "读取", "CONFIG");
                }
                for (EtlTableRefResolver.TableTarget target : etlTableRefResolver.writeTargets(node, config, null)) {
                    if (target.dataSourceId() == null) {
                        continue;
                    }
                    addTableEdge(context, etlNodeId(task.getId()), target, "WRITES", "写入", "CONFIG");
                }
                String modelId = etlTableRefResolver.modelWriteId(node, config);
                if (modelId != null) {
                    addEdge(context, etlNodeId(task.getId()), modelNodeId(modelId), "WRITES_MODEL", "写入模型", "CONFIG");
                }
            }
        }
    }

    private void addSyncNodes(BuildContext context) {
        Map<String, List<DataSyncTableConfig>> configsBySync = dataSyncTableConfigRepository
            .findAll()
            .stream()
            .filter(config -> config.getSyncTask() != null)
            .collect(Collectors.groupingBy(DataSyncTableConfig::getSyncTask, LinkedHashMap::new, Collectors.toList()));
        for (DataSync sync : dataSyncRepository.findAll()) {
            addNode(
                context,
                new DataMapNodeDTO(
                    syncNodeId(sync.getId()),
                    DataMapAssetTypes.DATA_SYNC,
                    firstNonBlank(sync.getJobName(), sync.getJobCode(), sync.getId()),
                    sync.getJobCode(),
                    sync.getJobDesc(),
                    firstNonBlank(sync.getJobCode(), sync.getJobName()),
                    null,
                    null,
                    null,
                    null,
                    0,
                    sync.getStatus(),
                    timestamp(sync.getUpdateTime()),
                    0,
                    meta("syncType", sync.getType(), "cron", sync.getCron())
                )
            );
            for (DataSyncTableConfig config : configsBySync.getOrDefault(sync.getId(), List.of())) {
                addTableEdge(
                    context,
                    syncNodeId(sync.getId()),
                    new EtlTableRefResolver.TableTarget(
                        config.getSrcDatasource(),
                        TableRef.of(null, config.getSrcSchemaName(), config.getSrcTableName())
                    ),
                    "READS",
                    "同步来源",
                    "CONFIG"
                );
                addTableEdge(
                    context,
                    syncNodeId(sync.getId()),
                    new EtlTableRefResolver.TableTarget(
                        config.getDesDatasource(),
                        TableRef.of(null, config.getDesSchemaName(), config.getDesTableName())
                    ),
                    "WRITES",
                    "同步目标",
                    "CONFIG"
                );
            }
        }
    }

    private void addModelNodes(BuildContext context) {
        Map<String, List<ModelField>> fieldsByModel = modelFieldRepository
            .findAll()
            .stream()
            .filter(field -> field.getModelId() != null)
            .collect(Collectors.groupingBy(ModelField::getModelId, LinkedHashMap::new, Collectors.toList()));
        for (DataModel model : dataModelRepository.findAll()) {
            addNode(
                context,
                new DataMapNodeDTO(
                    modelNodeId(model.getId()),
                    DataMapAssetTypes.DATA_MODEL,
                    firstNonBlank(model.getName(), model.getCode(), model.getId()),
                    model.getCode(),
                    model.getDescription(),
                    firstNonBlank(model.getCode(), model.getName()),
                    model.getDataSourceId(),
                    dataSourceName(context, model.getDataSourceId()),
                    model.getSchemaName(),
                    model.getTableName(),
                    0,
                    Boolean.TRUE.equals(model.getIsRegistered()) ? "REGISTERED" : "UNREGISTERED",
                    timestamp(model.getUpdateTime()),
                    0,
                    meta(
                        "modelType",
                        model.getModelType(),
                        "dimensionKind",
                        model.getDimensionKind(),
                        "directoryId",
                        model.getDirectoryId()
                    )
                )
            );
            if (model.getTableName() != null && !model.getTableName().isBlank() && model.getDataSourceId() != null) {
                String tableNodeId = ensureTable(
                    context,
                    model.getDataSourceId(),
                    null,
                    model.getSchemaName(),
                    model.getTableName(),
                    "MISSING",
                    null
                );
                addEdge(context, modelNodeId(model.getId()), tableNodeId, "MAPS_TO", "映射物理表", "CONFIG");
            }
            Set<String> dimensionModels = new LinkedHashSet<>();
            for (ModelField field : fieldsByModel.getOrDefault(model.getId(), List.of())) {
                String fieldNodeId = modelFieldNodeId(field.getId());
                addNode(
                    context,
                    new DataMapNodeDTO(
                        fieldNodeId,
                        DataMapAssetTypes.MODEL_FIELD,
                        field.getFieldName(),
                        null,
                        field.getDescription(),
                        firstNonBlank(model.getCode(), model.getName()) + "." + field.getFieldName(),
                        model.getDataSourceId(),
                        dataSourceName(context, model.getDataSourceId()),
                        model.getSchemaName(),
                        model.getTableName(),
                        0,
                        null,
                        timestamp(field.getUpdateTime()),
                        0,
                        meta(
                            "fieldType",
                            field.getFieldType(),
                            "fieldLength",
                            field.getFieldLength(),
                            "fieldPrecision",
                            field.getFieldPrecision(),
                            "fieldScale",
                            field.getFieldScale(),
                            "primaryKey",
                            field.getIsPrimaryKey(),
                            "partitionKey",
                            field.getIsPartitionKey(),
                            "dimensionModelId",
                            field.getDimensionModelId()
                        )
                    )
                );
                addEdge(context, modelNodeId(model.getId()), fieldNodeId, "CONTAINS", "包含字段", "CONFIG");
                if (field.getDimensionModelId() != null && !field.getDimensionModelId().isBlank()) {
                    dimensionModels.add(field.getDimensionModelId());
                }
            }
            for (String dimensionModelId : dimensionModels) {
                addEdge(context, modelNodeId(model.getId()), modelNodeId(dimensionModelId), "DIMENSION", "关联维度", "CONFIG");
            }
        }
    }

    private void addMetricNodes(BuildContext context) {
        Map<String, Metric> metricsByCode = context.metricsByCode;
        List<Metric> metrics = metricRepository.findAll();
        for (Metric metric : metrics) {
            if (metric.getCode() != null) {
                metricsByCode.put(metric.getCode(), metric);
            }
            addNode(
                context,
                new DataMapNodeDTO(
                    metricNodeId(metric.getId()),
                    DataMapAssetTypes.METRIC,
                    firstNonBlank(metric.getName(), metric.getCode(), metric.getId()),
                    metric.getCode(),
                    metric.getDescription(),
                    firstNonBlank(metric.getCode(), metric.getName()),
                    null,
                    null,
                    null,
                    null,
                    0,
                    metric.getStatus(),
                    timestamp(metric.getUpdateTime()),
                    0,
                    meta(
                        "metricType",
                        metric.getMetricType(),
                        "unit",
                        metric.getUnit(),
                        "factModelId",
                        metric.getFactModelId(),
                        "directoryId",
                        metric.getDirectoryId()
                    )
                )
            );
        }
        for (Metric metric : metrics) {
            if (metric.getFactModelId() != null && !metric.getFactModelId().isBlank()) {
                addEdge(context, modelNodeId(metric.getFactModelId()), metricNodeId(metric.getId()), "COMPUTES", "计算指标", "CONFIG");
            }
            if (Metric.TYPE_DERIVED.equals(metric.getMetricType())) {
                for (String code : formulaRefs(metric.getFormula())) {
                    Metric parent = metricsByCode.get(code);
                    if (parent != null) {
                        addEdge(
                            context,
                            metricNodeId(parent.getId()),
                            metricNodeId(metric.getId()),
                            "DERIVES",
                            "引用指标",
                            "PARSED"
                        );
                    }
                }
            }
        }
    }

    private void addDatasetNodes(BuildContext context) {
        for (AnalysisDataset dataset : analysisDatasetRepository.findAll()) {
            addNode(
                context,
                new DataMapNodeDTO(
                    datasetNodeId(dataset.getId()),
                    DataMapAssetTypes.DATASET,
                    firstNonBlank(dataset.getName(), dataset.getCode(), dataset.getId()),
                    dataset.getCode(),
                    dataset.getDescription(),
                    firstNonBlank(dataset.getCode(), dataset.getName()),
                    dataset.getDataSourceId(),
                    dataSourceName(context, dataset.getDataSourceId()),
                    null,
                    null,
                    0,
                    dataset.getStatus(),
                    timestamp(dataset.getUpdateTime()),
                    0,
                    meta("datasetType", dataset.getType())
                )
            );
            Map<String, Object> definition;
            try {
                definition = analysisDatasetResolver.parseDefinition(dataset.getDefinition());
            } catch (Exception e) {
                LOG.debug("解析数据集 {} 定义失败: {}", dataset.getId(), e.getMessage());
                continue;
            }
            if (AnalysisDataset.TYPE_METRIC.equalsIgnoreCase(dataset.getType())) {
                Object metricQuery = definition.get("metricQuery");
                if (metricQuery instanceof Map<?, ?> map) {
                    for (String code : stringList(map.get("metricCodes"))) {
                        addEdgeByCode(context, datasetNodeId(dataset.getId()), code, "REFERENCES", "引用指标", "CONFIG");
                    }
                }
            } else if (AnalysisDataset.TYPE_SQL.equalsIgnoreCase(dataset.getType())) {
                String sql = stringValue(definition.get("sql"));
                String dataSourceId = firstNonBlank(
                    stringValue(definition.get("dataSourceId")),
                    dataset.getDataSourceId()
                );
                addSqlTableEdges(context, datasetNodeId(dataset.getId()), sql, dataSourceId, "REFERENCES", "读取", "PARSED");
            }
        }
    }

    private void addDashboardNodes(BuildContext context) {
        for (AnalysisDashboard dashboard : analysisDashboardRepository.findAll()) {
            addNode(
                context,
                new DataMapNodeDTO(
                    dashboardNodeId(dashboard.getId()),
                    DataMapAssetTypes.DASHBOARD,
                    firstNonBlank(dashboard.getName(), dashboard.getCode(), dashboard.getId()),
                    dashboard.getCode(),
                    dashboard.getDescription(),
                    firstNonBlank(dashboard.getCode(), dashboard.getName()),
                    dashboard.getDataSourceId(),
                    dataSourceName(context, dashboard.getDataSourceId()),
                    null,
                    null,
                    0,
                    dashboard.getStatus(),
                    timestamp(dashboard.getUpdateTime()),
                    0,
                    meta("dashboardId", dashboard.getId())
                )
            );
            JsonNode spec = readResolvedDashboardSpec(dashboard);
            if (spec == null) {
                continue;
            }
            JsonNode datasets = spec.path("datasets");
            if (!datasets.isArray()) {
                continue;
            }
            for (JsonNode dataset : datasets) {
                String refId = text(dataset, "refId");
                if (refId != null) {
                    addEdge(
                        context,
                        dashboardNodeId(dashboard.getId()),
                        datasetNodeId(refId),
                        "USES_DATASET",
                        "使用数据集",
                        "CONFIG"
                    );
                } else {
                    JsonNode metricCodes = dataset.path("metricQuery").path("metricCodes");
                    if (metricCodes.isArray()) {
                        for (JsonNode code : metricCodes) {
                            addEdgeByCode(
                                context,
                                dashboardNodeId(dashboard.getId()),
                                code.asText(),
                                "REFERENCES",
                                "引用指标",
                                "CONFIG"
                            );
                        }
                    }
                    String sql = text(dataset, "sql");
                    if (sql != null) {
                        String dataSourceId = firstNonBlank(text(dataset, "dataSourceId"), dashboard.getDataSourceId());
                        addSqlTableEdges(
                            context,
                            dashboardNodeId(dashboard.getId()),
                            sql,
                            dataSourceId,
                            "REFERENCES",
                            "读取",
                            "PARSED"
                        );
                    }
                }
            }
        }
    }

    private void addApiNodes(BuildContext context) {
        for (DataApi api : dataApiRepository.findAll()) {
            addNode(
                context,
                new DataMapNodeDTO(
                    apiNodeId(api.getId()),
                    DataMapAssetTypes.DATA_API,
                    firstNonBlank(api.getName(), api.getCode(), api.getId()),
                    api.getCode(),
                    api.getDescription(),
                    firstNonBlank(api.getCode(), api.getName()),
                    api.getDataSourceId(),
                    dataSourceName(context, api.getDataSourceId()),
                    api.getSchemaName(),
                    api.getTableName(),
                    0,
                    api.getStatus(),
                    timestamp(api.getUpdateTime()),
                    0,
                    meta("sourceType", api.getSourceType(), "directoryId", api.getDirectoryId())
                )
            );
            if (DataApi.SOURCE_TYPE_DATASET.equalsIgnoreCase(api.getSourceType()) && api.getDatasetId() != null) {
                addEdge(context, apiNodeId(api.getId()), datasetNodeId(api.getDatasetId()), "EXPOSES", "开放数据集", "CONFIG");
            } else if (DataApi.SOURCE_TYPE_TABLE.equalsIgnoreCase(api.getSourceType())) {
                addTableEdge(
                    context,
                    apiNodeId(api.getId()),
                    new EtlTableRefResolver.TableTarget(
                        api.getDataSourceId(),
                        TableRef.of(null, api.getSchemaName(), api.getTableName())
                    ),
                    "EXPOSES",
                    "开放表",
                    "CONFIG"
                );
            } else if (DataApi.SOURCE_TYPE_SQL.equalsIgnoreCase(api.getSourceType())) {
                addSqlTableEdges(
                    context,
                    apiNodeId(api.getId()),
                    api.getSqlText(),
                    api.getDataSourceId(),
                    "EXPOSES",
                    "读取",
                    "PARSED"
                );
            }
        }
    }

    private void addQualityNodes(BuildContext context) {
        for (DataQualityTask task : dataQualityTaskRepository.findAll()) {
            addNode(
                context,
                new DataMapNodeDTO(
                    qualityNodeId(task.getId()),
                    DataMapAssetTypes.QUALITY_TASK,
                    firstNonBlank(task.getTaskName(), task.getId()),
                    null,
                    task.getTaskDesc(),
                    firstNonBlank(task.getTaskName(), task.getId()),
                    task.getDataSourceId(),
                    dataSourceName(context, task.getDataSourceId()),
                    task.getSchemaName(),
                    task.getTableName(),
                    0,
                    task.getStatus(),
                    timestamp(task.getUpdateTime()),
                    0,
                    Map.of()
                )
            );
            addTableEdge(
                context,
                qualityNodeId(task.getId()),
                new EtlTableRefResolver.TableTarget(
                    task.getDataSourceId(),
                    TableRef.of(null, task.getSchemaName(), task.getTableName())
                ),
                "CHECKS",
                "质量检核",
                "CONFIG"
            );
        }
    }

    private void addTableEdge(
        BuildContext context,
        String sourceNodeId,
        EtlTableRefResolver.TableTarget target,
        String relation,
        String label,
        String confidence
    ) {
        if (
            target == null ||
            target.dataSourceId() == null ||
            target.dataSourceId().isBlank() ||
            target.table() == null ||
            target.table().table() == null ||
            target.table().table().isBlank()
        ) {
            return;
        }
        String tableNodeId = ensureTable(
            context,
            target.dataSourceId(),
            target.table().catalog(),
            target.table().schema(),
            target.table().table(),
            "MISSING",
            null
        );
        addEdge(context, sourceNodeId, tableNodeId, relation, label, confidence);
    }

    private void addSqlTableEdges(
        BuildContext context,
        String sourceNodeId,
        String sql,
        String dataSourceId,
        String relation,
        String label,
        String confidence
    ) {
        if (sql == null || sql.isBlank() || dataSourceId == null || dataSourceId.isBlank()) {
            return;
        }
        SqlReferences parsed = parseSql(sql);
        for (TableRef table : parsed.reads()) {
            addTableEdge(
                context,
                sourceNodeId,
                new EtlTableRefResolver.TableTarget(dataSourceId, table),
                relation,
                label,
                confidence
            );
        }
    }

    private SqlReferences parseSql(String sql) {
        try {
            return sqlLineageParser.parse(sql);
        } catch (Exception e) {
            LOG.debug("解析数据地图 SQL 失败: {}", e.getMessage());
            return SqlReferences.empty();
        }
    }

    private void addEdgeByCode(BuildContext context, String sourceNodeId, String metricCode, String relation, String label, String confidence) {
        Metric metric = context.metricsByCode.get(metricCode);
        if (metric != null) {
            addEdge(context, sourceNodeId, metricNodeId(metric.getId()), relation, label, confidence);
        }
    }

    private String ensureTable(
        BuildContext context,
        String dataSourceId,
        String catalog,
        String schema,
        String table,
        String metadataStatus,
        String comment
    ) {
        if (dataSourceId == null || dataSourceId.isBlank() || table == null || table.isBlank()) {
            return null;
        }
        String exactKey = exactKey(dataSourceId, catalog, schema, table);
        String looseKey = looseKey(dataSourceId, schema, table);
        String existingId = context.tableByExactKey.get(exactKey);
        if (existingId == null) {
            existingId = context.tableByLooseKey.get(looseKey);
        }
        if (existingId != null) {
            DataMapNodeDTO existing = context.nodes.get(existingId);
            if (existing != null && comment != null && !comment.isBlank()) {
                Map<String, Object> meta = new LinkedHashMap<>(existing.meta());
                meta.put("comment", comment);
                meta.put("metadataStatus", metadataStatus);
                context.nodes.put(existingId, replaceDescription(existing, comment, meta));
            }
            return existingId;
        }
        String schemaNodeId = null;
        if (schema != null && !schema.isBlank()) {
            schemaNodeId = ensureSchema(context, dataSourceId, catalog, schema);
        }
        String id = tableNodeId(dataSourceId, catalog, schema, table);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("catalog", catalog);
        meta.put("metadataStatus", metadataStatus);
        meta.put("comment", comment);
        addNode(
            context,
            new DataMapNodeDTO(
                id,
                DataMapAssetTypes.TABLE,
                table,
                null,
                comment,
                joinTable(catalog, schema, table),
                dataSourceId,
                dataSourceName(context, dataSourceId),
                schema,
                table,
                0,
                null,
                null,
                0,
                meta
            )
        );
        context.tableByExactKey.put(exactKey, id);
        context.tableByLooseKey.putIfAbsent(looseKey, id);
        if (schemaNodeId != null) {
            addEdge(context, schemaNodeId, id, "CONTAINS", "包含表", "CONFIG");
        }
        return id;
    }

    private String ensureSchema(BuildContext context, String dataSourceId, String catalog, String schema) {
        if (schema == null || schema.isBlank()) {
            return null;
        }
        String id = schemaNodeId(dataSourceId, catalog, schema);
        if (context.nodes.containsKey(id)) {
            return id;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("catalog", catalog);
        addNode(
            context,
            new DataMapNodeDTO(
                id,
                DataMapAssetTypes.SCHEMA,
                schema,
                null,
                null,
                joinTable(catalog, schema, null),
                dataSourceId,
                dataSourceName(context, dataSourceId),
                schema,
                null,
                0,
                null,
                null,
                0,
                meta
            )
        );
        addEdge(context, sourceNodeId(dataSourceId), id, "CONTAINS", "包含 Schema", "CONFIG");
        return id;
    }

    private void addNode(BuildContext context, DataMapNodeDTO node) {
        if (node == null || node.id() == null || node.id().isBlank()) {
            return;
        }
        context.nodes.putIfAbsent(node.id(), node);
    }

    private void addEdge(BuildContext context, String source, String target, String relation, String label, String confidence) {
        if (source == null || target == null || source.isBlank() || target.isBlank() || source.equals(target)) {
            return;
        }
        String id = source + "->" + target + ":" + relation;
        context.edges.putIfAbsent(id, new DataMapEdgeDTO(id, source, target, relation, label, confidence));
    }

    private void applyDegrees(BuildContext context) {
        Map<String, Integer> degree = new HashMap<>();
        for (DataMapEdgeDTO edge : context.edges.values()) {
            degree.merge(edge.source(), 1, Integer::sum);
            degree.merge(edge.target(), 1, Integer::sum);
        }
        for (Map.Entry<String, DataMapNodeDTO> entry : new LinkedHashMap<>(context.nodes).entrySet()) {
            int value = degree.getOrDefault(entry.getKey(), 0);
            context.nodes.put(entry.getKey(), entry.getValue().withDegree(value));
        }
    }

    private JsonNode readResolvedDashboardSpec(AnalysisDashboard dashboard) {
        JsonNode spec = readJson(dashboard.getSpec());
        if (spec == null) {
            return null;
        }
        try {
            return readJson(analysisDatasetResolver.resolveSpecJson(dashboard.getSpec()));
        } catch (Exception e) {
            LOG.debug("解析看板 {} 数据集引用失败: {}", dashboard.getId(), e.getMessage());
            return spec;
        }
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            LOG.debug("解析数据地图 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private Set<String> formulaRefs(String formula) {
        Set<String> codes = new LinkedHashSet<>();
        if (formula == null) {
            return codes;
        }
        Matcher matcher = METRIC_FORMULA_REF.matcher(formula);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private DataMapNodeDTO replaceDescription(DataMapNodeDTO node, String description, Map<String, Object> meta) {
        return new DataMapNodeDTO(
            node.id(),
            node.type(),
            node.name(),
            node.code(),
            description,
            node.qualifiedName(),
            node.dataSourceId(),
            node.dataSourceName(),
            node.schema(),
            node.table(),
            node.degree(),
            node.status(),
            node.updatedAt(),
            node.layer(),
            meta
        );
    }

    private String dataSourceName(BuildContext context, String dataSourceId) {
        DataSource dataSource = dataSourceId == null ? null : context.dataSources.get(dataSourceId);
        return dataSource == null ? null : dataSource.getName();
    }

    private String sourceNodeId(String id) {
        return "source:" + id;
    }

    private String schemaNodeId(String dataSourceId, String catalog, String schema) {
        // Schema 以数据源 + schema 名唯一标识；catalog 只用于表身份区分，避免不同驱动
        // （MySQL 的 database、DuckDB 的 catalog）把同一个 schema 拆成多个节点。
        return "schema:" + dataSourceId + ":" + normalize(schema);
    }

    private String tableNodeId(String dataSourceId, String catalog, String schema, String table) {
        return "table:" + dataSourceId + ":" + joinTable(catalog, schema, table).toLowerCase(Locale.ROOT);
    }

    private String exactKey(String dataSourceId, String catalog, String schema, String table) {
        return dataSourceId + "|" + normalize(catalog) + "|" + normalize(schema) + "|" + normalize(table);
    }

    private String looseKey(String dataSourceId, String schema, String table) {
        return dataSourceId + "|" + normalize(schema) + "|" + normalize(table);
    }

    private String etlNodeId(String id) {
        return "etl:" + id;
    }

    private String syncNodeId(String id) {
        return "sync:" + id;
    }

    private String modelNodeId(String id) {
        return "model:" + id;
    }

    private String modelFieldNodeId(String id) {
        return "model-field:" + id;
    }

    private String metricNodeId(String id) {
        return "metric:" + id;
    }

    private String datasetNodeId(String id) {
        return "dataset:" + id;
    }

    private String dashboardNodeId(String id) {
        return "dashboard:" + id;
    }

    private String apiNodeId(String id) {
        return "api:" + id;
    }

    private String qualityNodeId(String id) {
        return "quality:" + id;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String joinTable(String catalog, String schema, String table) {
        List<String> parts = new ArrayList<>();
        if (catalog != null && !catalog.isBlank()) {
            parts.add(catalog);
        }
        if (schema != null && !schema.isBlank()) {
            parts.add(schema);
        }
        if (table != null && !table.isBlank()) {
            parts.add(table);
        }
        return String.join(".", parts);
    }

    private String timestamp(java.time.ZonedDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Map<String, Object> meta(Object... values) {
        Map<String, Object> meta = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i] != null) {
                meta.put(String.valueOf(values[i]), values[i + 1]);
            }
        }
        return meta;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    public record GraphBuildResult(List<DataMapNodeDTO> nodes, List<DataMapEdgeDTO> edges, String coverage) {}

    private static class BuildContext {

        private final Map<String, DataMapNodeDTO> nodes = new LinkedHashMap<>();
        private final Map<String, DataMapEdgeDTO> edges = new LinkedHashMap<>();
        private final Map<String, DataSource> dataSources = new LinkedHashMap<>();
        private final Map<String, Metric> metricsByCode = new LinkedHashMap<>();
        private final Map<String, String> tableByExactKey = new LinkedHashMap<>();
        private final Map<String, String> tableByLooseKey = new LinkedHashMap<>();
    }
}
