package com.data.datafusion.service.apppackage;

import com.data.datafusion.domain.*;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.*;
import com.data.datafusion.service.apppackage.model.*;
import com.data.datafusion.service.dto.AppPackageExportRequestDTO;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产包导出服务：把当前租户中已经开发完成的数据资产序列化为一份可移植的资产包。
 *
 * <p>导出时包内所有对象都会带上「包内逻辑 ID」{@code oldId}，其值就是来源租户的真实 ID；
 * 其它对象通过 {@code xxxOldId} 引用它。这样导入方只要建立一张「旧 ID → 新 ID」映射表，
 * 就能把整包资产原样落到自己的租户里。
 *
 * <p>默认会自动补齐被引用的资产（{@code includeReferences=true}），例如指标引用的事实模型、
 * 模型引用的维度模型与数据源、编排任务引用的子任务，避免导出半截资产包。
 */
@Service
@Transactional(readOnly = true)
public class AppPackageExportService {

    private static final Logger LOG = LoggerFactory.getLogger(AppPackageExportService.class);

    /** 引用展开的最大轮次，防止异常数据导致死循环。 */
    private static final int MAX_EXPAND_ROUNDS = 8;

    private final DataSourceRepository dataSourceRepository;

    private final DataModelRepository dataModelRepository;

    private final ModelFieldRepository modelFieldRepository;

    private final ModelDirectoryRepository modelDirectoryRepository;

    private final MetricRepository metricRepository;

    private final MetricDirectoryRepository metricDirectoryRepository;

    private final ETLTaskRepository etlTaskRepository;

    private final ETLNodeRepository etlNodeRepository;

    private final ETLEdgeRepository etlEdgeRepository;

    private final JobRepository jobRepository;

    private final JobDependRepository jobDependRepository;

    public AppPackageExportService(
        DataSourceRepository dataSourceRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        ModelDirectoryRepository modelDirectoryRepository,
        MetricRepository metricRepository,
        MetricDirectoryRepository metricDirectoryRepository,
        ETLTaskRepository etlTaskRepository,
        ETLNodeRepository etlNodeRepository,
        ETLEdgeRepository etlEdgeRepository,
        JobRepository jobRepository,
        JobDependRepository jobDependRepository
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.modelDirectoryRepository = modelDirectoryRepository;
        this.metricRepository = metricRepository;
        this.metricDirectoryRepository = metricDirectoryRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.etlNodeRepository = etlNodeRepository;
        this.etlEdgeRepository = etlEdgeRepository;
        this.jobRepository = jobRepository;
        this.jobDependRepository = jobDependRepository;
    }

    /**
     * 根据导出选择构建资产包内容。
     *
     * @param request 导出请求，含用户勾选的资产 ID
     * @return 资产包清单
     */
    public AppPackageContent buildContent(AppPackageExportRequestDTO request) {
        Collection<String> initialDataSources = toIdSet(request.dataSourceIds);
        Collection<String> initialModels = toIdSet(request.modelIds);
        Collection<String> initialMetrics = toIdSet(request.metricIds);
        Collection<String> initialEtlTasks = toIdSet(request.etlTaskIds);
        Collection<String> initialSqlJobs = toIdSet(request.sqlJobIds);
        Collection<String> initialDagJobs = toIdSet(request.dagJobIds);

        Selection selection = new Selection();
        selection.dataSourceIds.addAll(initialDataSources);
        selection.modelIds.addAll(initialModels);
        selection.metricIds.addAll(initialMetrics);
        selection.etlTaskIds.addAll(initialEtlTasks);
        selection.jobIdsByType(TaskConstants.TASK_TYPE_SQL).addAll(initialSqlJobs);
        selection.jobIdsByType(TaskConstants.TASK_TYPE_DAG).addAll(initialDagJobs);

        boolean includeReferences = request.includeReferences == null || Boolean.TRUE.equals(request.includeReferences);
        if (includeReferences) {
            expandReferences(selection);
        }

        AppPackageContent content = new AppPackageContent();
        AppPackageContent.Meta meta = content.getMeta();
        meta.setName(trimToNull(request.name));
        meta.setCode(trimToNull(request.code));
        meta.setDescription(trimToNull(request.description));
        meta.setCategory(trimToNull(request.category));
        meta.setVersion(trimToNull(request.version) == null ? "1.0.0" : request.version.trim());
        meta.setSource(AppPackage.TYPE_TENANT);
        content.setDataSources(buildDataSources(selection));
        content.setModels(buildModels(selection));
        content.setModelDirectories(buildModelDirectories(selection));
        content.setMetrics(buildMetrics(selection));
        content.setMetricDirectories(buildMetricDirectories(selection));
        content.setEtlTasks(buildEtlTasks(selection));
        content.setSqlJobs(buildJobs(selection, TaskConstants.TASK_TYPE_SQL));
        content.setDagJobs(buildJobs(selection, TaskConstants.TASK_TYPE_DAG));
        content.setOtherJobs(buildOtherJobs(selection));
        content.setJobDepends(buildJobDepends(selection));
        content.rebuildSummary();
        LOG.debug("Built app package content: {}", content.getSummary());
        return content;
    }

    /**
     * 反复展开各类资产的引用关系，直到不再新增为止。
     */
    private void expandReferences(Selection selection) {
        for (int round = 0; round < MAX_EXPAND_ROUNDS; round++) {
            int before = selection.size();

            // 数据模型：字段引用的维度模型 + 模型绑定的数据源
            for (String modelId : new ArrayList<>(selection.modelIds)) {
                DataModel model = dataModelRepository.findById(modelId).orElse(null);
                if (model == null) {
                    continue;
                }
                if (model.getDataSourceId() != null) {
                    selection.dataSourceIds.add(model.getDataSourceId());
                }
                for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(modelId)) {
                    if (field.getDimensionModelId() != null) {
                        selection.modelIds.add(field.getDimensionModelId());
                    }
                }
            }

            // 指标：事实模型 + 业务限定里引用的维度模型（按编码定位，包内自洽）
            for (String metricId : new ArrayList<>(selection.metricIds)) {
                Metric metric = metricRepository.findById(metricId).orElse(null);
                if (metric == null) {
                    continue;
                }
                if (metric.getFactModelId() != null) {
                    selection.modelIds.add(metric.getFactModelId());
                }
                for (String modelCode : extractDimensionModelCodes(metric.getFilterConfig())) {
                    dataModelRepository.findFirstByCode(modelCode).ifPresent(model -> selection.modelIds.add(model.getId()));
                }
            }

            // ETL 任务：节点配置里的数据源与数据模型
            for (String etlTaskId : new ArrayList<>(selection.etlTaskIds)) {
                ETLTask task = etlTaskRepository.findById(etlTaskId).orElse(null);
                if (task == null) {
                    continue;
                }
                if (task.getJobId() != null) {
                    selection.etlJobIds.add(task.getJobId());
                }
                List<ETLNode> nodes = etlNodeRepository.findAllByTaskId(etlTaskId).orElse(Collections.emptyList());
                for (ETLNode node : nodes) {
                    JsonNode config = AppPackageJson.readTreeQuietly(node.getConfig());
                    if (config == null || !config.isObject()) {
                        continue;
                    }
                    JsonNode sourceId = config.get("sourceId");
                    if (sourceId != null && !sourceId.isContainerNode()) {
                        selection.dataSourceIds.add(sourceId.asText());
                    }
                    JsonNode modelId = config.get("modelId");
                    if (modelId != null && !modelId.isContainerNode()) {
                        selection.modelIds.add(modelId.asText());
                    }
                }
            }

            // SQL 任务：绑定的数据源
            for (String jobId : new ArrayList<>(selection.jobIdsByType(TaskConstants.TASK_TYPE_SQL))) {
                Job job = jobRepository.findById(jobId).orElse(null);
                if (job == null) {
                    continue;
                }
                JsonNode context = AppPackageJson.readTreeQuietly(job.getJobContext());
                if (context != null && context.isObject()) {
                    JsonNode dataSourceId = context.get("dataSourceId");
                    if (dataSourceId != null && !dataSourceId.isContainerNode()) {
                        selection.dataSourceIds.add(dataSourceId.asText());
                    }
                }
            }

            // 编排任务：递归纳入被编排的子任务
            for (String jobId : new ArrayList<>(selection.jobIdsByType(TaskConstants.TASK_TYPE_DAG))) {
                Job job = jobRepository.findById(jobId).orElse(null);
                if (job == null) {
                    continue;
                }
                for (String childJobId : extractDagChildJobIds(job.getJobContext())) {
                    addReferencedJob(selection, childJobId);
                }
            }

            if (selection.size() == before) {
                return;
            }
        }
        LOG.warn("Reached max reference expansion rounds ({}) while exporting app package", MAX_EXPAND_ROUNDS);
    }

    /**
     * 把一个被引用的 Job 按类型归入选择集：ETL 任务归入 {@code etlTaskIds}，SQL / DAG 归入对应类型，
     * 其余（Shell / Spark / Flink 等）作为其它任务一并打包。
     */
    private void addReferencedJob(Selection selection, String jobId) {
        if (selection.allJobIds().contains(jobId)) {
            return;
        }
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getType() == null) {
            return;
        }
        switch (job.getType()) {
            case TaskConstants.TASK_TYPE_SQL -> selection.jobIdsByType(TaskConstants.TASK_TYPE_SQL).add(jobId);
            case TaskConstants.TASK_TYPE_DAG -> selection.jobIdsByType(TaskConstants.TASK_TYPE_DAG).add(jobId);
            case TaskConstants.TASK_TYPE_ETL ->
                etlTaskRepository.findByJobId(jobId).ifPresent(task -> selection.etlTaskIds.add(task.getId()));
            default -> selection.jobIdsByType(job.getType()).add(jobId);
        }
    }
    private List<AppPackageDataSource> buildDataSources(Selection selection) {
        List<AppPackageDataSource> items = new ArrayList<>();
        for (String id : selection.dataSourceIds) {
            dataSourceRepository
                .findById(id)
                .ifPresent(dataSource -> {
                    AppPackageDataSource item = new AppPackageDataSource();
                    item.oldId = dataSource.getId();
                    item.name = dataSource.getName();
                    item.description = dataSource.getDescription();
                    item.type = dataSource.getType();
                    item.url = dataSource.getUrl();
                    item.hostname = dataSource.getHostname();
                    item.port = dataSource.getPort();
                    item.schemaName = dataSource.getSchemaName();
                    item.database = dataSource.getDatabase();
                    item.username = dataSource.getUsername();
                    item.password = dataSource.getPassword();
                    item.connectionMode = dataSource.getConnectionMode();
                    item.extraParams = dataSource.getExtraParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dataSource.getExtraParams());
                    item.key = AppPackageDataSource.buildKey(item.name, item.type, item.url);
                    items.add(item);
                });
        }
        return items;
    }

    private List<AppPackageModel> buildModels(Selection selection) {
        List<AppPackageModel> items = new ArrayList<>();
        for (String id : selection.modelIds) {
            dataModelRepository
                .findById(id)
                .ifPresent(model -> {
                    AppPackageModel item = new AppPackageModel();
                    item.oldId = model.getId();
                    item.dataSourceOldId = model.getDataSourceId();
                    item.directoryOldId = model.getDirectoryId();
                    item.name = model.getName();
                    item.code = model.getCode();
                    item.description = model.getDescription();
                    item.modelType = model.getModelType();
                    item.dimensionKind = model.getDimensionKind();
                    item.levelCount = model.getLevelCount();
                    item.timeFieldName = model.getTimeFieldName();
                    item.timeLevels = model.getTimeLevels();
                    item.timeStart = model.getTimeStart();
                    item.timeEnd = model.getTimeEnd();
                    item.isRegistered = model.getIsRegistered();
                    item.project = model.getProject();
                    item.schemaName = model.getSchemaName();
                    item.tableName = model.getTableName();
                    item.displayFieldName = model.getDisplayFieldName();
                    item.fields = modelFieldRepository
                        .findByModelIdOrderBySortOrderAsc(id)
                        .stream()
                        .map(this::toFieldItem)
                        .toList();
                    items.add(item);
                });
        }
        return items;
    }

    private AppPackageModel.AppPackageModelField toFieldItem(ModelField field) {
        AppPackageModel.AppPackageModelField item = new AppPackageModel.AppPackageModelField();
        item.oldId = field.getId();
        item.fieldName = field.getFieldName();
        item.fieldType = field.getFieldType();
        item.fieldLength = field.getFieldLength();
        item.fieldPrecision = field.getFieldPrecision();
        item.fieldScale = field.getFieldScale();
        item.description = field.getDescription();
        item.sortOrder = field.getSortOrder();
        item.isPartitionKey = field.getIsPartitionKey();
        item.isPrimaryKey = field.getIsPrimaryKey();
        item.dimensionModelOldId = field.getDimensionModelId();
        item.dimensionFieldOldId = field.getDimensionFieldId();
        item.fieldRole = field.getFieldRole();
        item.levelIndex = field.getLevelIndex();
        return item;
    }

    private List<AppPackageDirectory> buildModelDirectories(Selection selection) {
        Set<String> directoryIds = new LinkedHashSet<>();
        for (String modelId : selection.modelIds) {
            dataModelRepository.findById(modelId).map(DataModel::getDirectoryId).ifPresent(directoryIds::add);
        }
        List<AppPackageDirectory> items = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String directoryId : directoryIds) {
            collectModelDirectory(directoryId, items, added);
        }
        items.sort(Comparator.comparing(item -> item.oldId == null ? "" : item.oldId));
        return items;
    }

    private void collectModelDirectory(String directoryId, List<AppPackageDirectory> items, Set<String> added) {
        String current = directoryId;
        while (current != null && added.add(current)) {
            ModelDirectory directory = modelDirectoryRepository.findById(current).orElse(null);
            if (directory == null) {
                return;
            }
            AppPackageDirectory item = new AppPackageDirectory();
            item.oldId = directory.getId();
            item.name = directory.getName();
            item.parentOldId = directory.getParentId() == null || directory.getParentId().isBlank() ? null : directory.getParentId();
            item.sortOrder = directory.getSortOrder();
            items.add(item);
            current = item.parentOldId;
        }
    }

    private List<AppPackageMetric> buildMetrics(Selection selection) {
        List<AppPackageMetric> items = new ArrayList<>();
        for (String id : selection.metricIds) {
            metricRepository
                .findById(id)
                .ifPresent(metric -> {
                    AppPackageMetric item = new AppPackageMetric();
                    item.oldId = metric.getId();
                    item.directoryOldId = metric.getDirectoryId();
                    item.name = metric.getName();
                    item.code = metric.getCode();
                    item.description = metric.getDescription();
                    item.metricType = metric.getMetricType();
                    item.status = metric.getStatus();
                    item.factModelOldId = metric.getFactModelId();
                    item.filterConfig = AppPackageJson.readTreeQuietly(metric.getFilterConfig());
                    item.unit = metric.getUnit();
                    item.dataType = metric.getDataType();
                    item.isAdditive = metric.getIsAdditive();
                    item.formula = metric.getFormula();
                    items.add(item);
                });
        }
        return items;
    }

    private List<AppPackageDirectory> buildMetricDirectories(Selection selection) {
        Set<String> directoryIds = new LinkedHashSet<>();
        for (String metricId : selection.metricIds) {
            metricRepository.findById(metricId).map(Metric::getDirectoryId).ifPresent(directoryIds::add);
        }
        List<AppPackageDirectory> items = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String directoryId : directoryIds) {
            String current = directoryId;
            while (current != null && added.add(current)) {
                MetricDirectory directory = metricDirectoryRepository.findById(current).orElse(null);
                if (directory == null) {
                    break;
                }
                AppPackageDirectory item = new AppPackageDirectory();
                item.oldId = directory.getId();
                item.name = directory.getName();
                item.parentOldId = directory.getParentId() == null || directory.getParentId().isBlank() ? null : directory.getParentId();
                item.sortOrder = directory.getSortOrder();
                items.add(item);
                current = item.parentOldId;
            }
        }
        items.sort(Comparator.comparing(item -> item.oldId == null ? "" : item.oldId));
        return items;
    }

    private List<AppPackageEtlTask> buildEtlTasks(Selection selection) {
        List<AppPackageEtlTask> items = new ArrayList<>();
        for (String id : selection.etlTaskIds) {
            etlTaskRepository
                .findById(id)
                .ifPresent(task -> {
                    AppPackageEtlTask item = new AppPackageEtlTask();
                    item.oldId = task.getId();
                    item.jobOldId = task.getJobId();
                    item.taskName = task.getTaskName();
                    item.taskCode = task.getTaskCode();
                    item.taskDesc = task.getTaskDesc();
                    item.dir = task.getDir();
                    item.type = task.getType();
                    item.cron = task.getCron();
                    item.project = task.getProject();
                    item.nodes =
                        etlNodeRepository
                            .findAllByTaskId(id)
                            .orElse(Collections.emptyList())
                            .stream()
                            .map(this::toNodeItem)
                            .toList();
                    item.edges =
                        etlEdgeRepository
                            .findAllByTaskId(id)
                            .orElse(Collections.emptyList())
                            .stream()
                            .map(this::toEdgeItem)
                            .toList();
                    items.add(item);
                });
        }
        return items;
    }

    private AppPackageEtlTask.AppPackageEtlNode toNodeItem(ETLNode node) {
        AppPackageEtlTask.AppPackageEtlNode item = new AppPackageEtlTask.AppPackageEtlNode();
        item.oldId = node.getId();
        item.label = node.getLabel();
        item.code = node.getCode();
        item.desc = node.getDesc();
        item.type = node.getType();
        item.config = AppPackageJson.readTreeQuietly(node.getConfig());
        item.xAxis = node.getxAxis();
        item.yAxis = node.getyAxis();
        item.status = node.getStatus();
        return item;
    }

    private AppPackageEtlTask.AppPackageEtlEdge toEdgeItem(ETLEdge edge) {
        AppPackageEtlTask.AppPackageEtlEdge item = new AppPackageEtlTask.AppPackageEtlEdge();
        item.oldId = edge.getId();
        item.name = edge.getName();
        item.code = edge.getCode();
        item.source = edge.getSource();
        item.target = edge.getTarget();
        item.config = edge.getConfig();
        item.status = edge.getStatus();
        return item;
    }

    private List<AppPackageJob> buildJobs(Selection selection, String type) {
        List<AppPackageJob> items = new ArrayList<>();
        for (String id : selection.jobIdsByType(type)) {
            jobRepository.findById(id).ifPresent(job -> items.add(toJobItem(job)));
        }
        return items;
    }

    private List<AppPackageJob> buildOtherJobs(Selection selection) {
        List<AppPackageJob> items = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : selection.jobIdsByType.entrySet()) {
            if (TaskConstants.TASK_TYPE_SQL.equals(entry.getKey()) || TaskConstants.TASK_TYPE_DAG.equals(entry.getKey())) {
                continue;
            }
            for (String id : entry.getValue()) {
                jobRepository.findById(id).ifPresent(job -> items.add(toJobItem(job)));
            }
        }
        return items;
    }

    private AppPackageJob toJobItem(Job job) {
        AppPackageJob item = new AppPackageJob();
        item.oldId = job.getId();
        item.jobName = job.getJobName();
        item.jobGroup = job.getJobGroup();
        item.type = job.getType();
        item.cron = job.getCron();
        item.jobContext = AppPackageJson.readTreeQuietly(job.getJobContext());
        item.status = job.getStatus();
        item.project = job.getProject();
        return item;
    }

    /**
     * 导出包内任务之间的依赖关系：父、子任务都在包内时才导出。
     */
    private List<AppPackageJobDepend> buildJobDepends(Selection selection) {
        Set<String> jobIds = selection.allJobIds();
        if (jobIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<AppPackageJobDepend> items = new ArrayList<>();
        for (JobDepend depend : jobDependRepository.findAll()) {
            String parent = trimToNull(depend.getParentJobCode());
            String child = trimToNull(depend.getChildJobCode());
            if (parent == null || child == null || !jobIds.contains(parent) || !jobIds.contains(child)) {
                continue;
            }
            AppPackageJobDepend item = new AppPackageJobDepend();
            item.oldId = depend.getId();
            item.parentJobOldId = parent;
            item.childJobOldId = child;
            item.lastInterval = depend.getLastInterval();
            items.add(item);
        }
        return items;
    }

    /**
     * 从指标业务限定 JSON 中取出引用的维度模型编码。
     */
    private Set<String> extractDimensionModelCodes(String filterConfig) {
        Set<String> codes = new LinkedHashSet<>();
        JsonNode root = AppPackageJson.readTreeQuietly(filterConfig);
        if (root == null) {
            return codes;
        }
        JsonNode conditions = root.get("conditions");
        if (conditions == null || !conditions.isArray()) {
            return codes;
        }
        for (JsonNode condition : conditions) {
            JsonNode code = condition.get("dimensionModelCode");
            if (code != null && !code.isNull() && !code.asText().isBlank()) {
                codes.add(code.asText());
            }
        }
        return codes;
    }

    /**
     * 从编排任务的 jobContext 中取出子任务 ID。
     */
    private Set<String> extractDagChildJobIds(String jobContext) {
        Set<String> ids = new LinkedHashSet<>();
        JsonNode root = AppPackageJson.readTreeQuietly(jobContext);
        if (root == null || !root.isObject()) {
            return ids;
        }
        JsonNode jobs = root.get("jobs");
        if (jobs != null && jobs.isArray()) {
            for (JsonNode job : jobs) {
                addIfPresent(ids, job.get("id"));
            }
        }
        JsonNode depends = root.get("jobDepends");
        if (depends != null && depends.isArray()) {
            for (JsonNode depend : depends) {
                addIfPresent(ids, depend.get("parentJobCode"));
                addIfPresent(ids, depend.get("childJobCode"));
            }
        }
        return ids;
    }

    private void addIfPresent(Set<String> ids, JsonNode node) {
        if (node == null || node.isNull() || node.isContainerNode()) {
            return;
        }
        String value = trimToNull(node.asText());
        if (value != null) {
            ids.add(value);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Collection<String> toIdSet(List<String> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * 导出选择集：各资产类型的 ID 集合，按 Job 类型分桶以便区分 SQL / DAG / 其它任务。
     */
    private static final class Selection {

        private final Set<String> dataSourceIds = new LinkedHashSet<>();

        private final Set<String> modelIds = new LinkedHashSet<>();

        private final Set<String> metricIds = new LinkedHashSet<>();

        private final Set<String> etlTaskIds = new LinkedHashSet<>();

        /** ETL 任务对应的调度 Job ID，供编排任务引用映射使用。 */
        private final Set<String> etlJobIds = new LinkedHashSet<>();

        private final Map<String, Set<String>> jobIdsByType = new LinkedHashMap<>();

        Set<String> jobIdsByType(String type) {
            return jobIdsByType.computeIfAbsent(type == null ? "UNKNOWN" : type, key -> new LinkedHashSet<>());
        }

        Set<String> allJobIds() {
            Set<String> all = new LinkedHashSet<>(etlJobIds);
            jobIdsByType.values().forEach(all::addAll);
            return all;
        }

        int size() {
            return (
                dataSourceIds.size() +
                modelIds.size() +
                metricIds.size() +
                etlTaskIds.size() +
                etlJobIds.size() +
                jobIdsByType.values().stream().mapToInt(Set::size).sum()
            );
        }
    }
}
