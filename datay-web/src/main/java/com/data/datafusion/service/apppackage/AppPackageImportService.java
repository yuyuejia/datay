package com.data.datafusion.service.apppackage;

import com.data.datafusion.domain.*;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.*;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.JobService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.apppackage.model.*;
import com.data.datafusion.service.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产包初始化服务：把一份资产包安装到当前租户。
 *
 * <p>核心工作是 <b>ID 替换</b>：资产包内部使用的是「来源租户的真实 ID」，安装时必须换成
 * 当前租户重新生成的 ID。映射按资产类型分表维护：
 * <ul>
 *   <li>数据源：先按 {@code 名称 + 类型 + 连接地址} 在目标租户内查找，命中直接复用（不重复建连接），
 *       未命中才新建；也支持调用方显式指定绑定到某个已有数据源；</li>
 *   <li>数据模型 / 模型字段：新建后记录映射，字段的「关联维度模型 / 关联维度字段」据此重写；</li>
 *   <li>指标：事实模型 ID 重写，业务限定 {@code filterConfig} 里的 {@code dimensionModelId}、
 *       {@code dimensionModelCode} 一并重写；衍生指标公式基于编码引用，编码被重命名时同步改写公式；</li>
 *   <li>ETL 任务：节点配置里的 {@code sourceId}（数据源）与 {@code modelId}（数据模型）重写；</li>
 *   <li>SQL 任务：jobContext 里的 {@code dataSourceId} 重写；</li>
 *   <li>编排任务：jobContext 里的子任务 ID（jobs / jobDepends / nodeLayout）按 Job 映射重写。</li>
 * </ul>
 *
 * <p>资产包内所有对象一律以「离线」状态导入，避免初始化动作直接把任务挂到调度上；
 * 需要上线时由调用方显式传 {@code onlineJobs=true}。
 */
@Service
public class AppPackageImportService {

    private static final Logger LOG = LoggerFactory.getLogger(AppPackageImportService.class);

    /** 冲突策略：自动重命名（默认）。 */
    public static final String STRATEGY_RENAME = "RENAME";

    /** 冲突策略：跳过已存在的同编码资产。 */
    public static final String STRATEGY_SKIP = "SKIP";

    /** 导入后的初始状态：离线。 */
    private static final String IMPORTED_STATUS = TaskConstants.TASK_STATUS_OFFLINE;

    /** 衍生指标公式中的指标引用。 */
    private static final Pattern FORMULA_REF_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");

    private final DataSourceRepository dataSourceRepository;

    private final DataModelRepository dataModelRepository;

    private final ModelFieldRepository modelFieldRepository;

    private final ModelDirectoryRepository modelDirectoryRepository;

    private final MetricRepository metricRepository;

    private final MetricDirectoryRepository metricDirectoryRepository;

    private final ETLTaskRepository etlTaskRepository;

    private final JobRepository jobRepository;

    private final JobDependRepository jobDependRepository;

    private final MetricService metricService;

    private final ETLTaskService etlTaskService;

    private final JobService jobService;

    public AppPackageImportService(
        DataSourceRepository dataSourceRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        ModelDirectoryRepository modelDirectoryRepository,
        MetricRepository metricRepository,
        MetricDirectoryRepository metricDirectoryRepository,
        ETLTaskRepository etlTaskRepository,
        JobRepository jobRepository,
        JobDependRepository jobDependRepository,
        MetricService metricService,
        ETLTaskService etlTaskService,
        JobService jobService
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.modelDirectoryRepository = modelDirectoryRepository;
        this.metricRepository = metricRepository;
        this.metricDirectoryRepository = metricDirectoryRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.jobRepository = jobRepository;
        this.jobDependRepository = jobDependRepository;
        this.metricService = metricService;
        this.etlTaskService = etlTaskService;
        this.jobService = jobService;
    }

    /**
     * 把资产包安装到当前租户。
     *
     * @param content   资产包内容
     * @param request   初始化参数（冲突策略、数据源绑定、是否上线）
     * @param packageId 资产包在市场的 ID，用上传文件初始化时为 null
     * @return 初始化结果（含各类 ID 映射）
     */
    @Transactional
    public AppPackageInitResultDTO importContent(AppPackageContent content, AppPackageInitRequestDTO request, Long packageId) {
        AppPackageInitResultDTO result = new AppPackageInitResultDTO();
        result.packageId = packageId;
        result.packageName = content.getMeta() == null ? null : content.getMeta().getName();
        result.packageCode = content.getMeta() == null ? null : content.getMeta().getCode();
        String strategy = normalizeStrategy(request);

        // 1. 数据源：复用优先，其次新建，同时应用调用方指定的绑定
        Map<Long, Long> dataSourceMap = importDataSources(content, request, result);

        // 2. 模型目录与模型（含字段）
        Map<Long, Long> modelDirectoryMap = importModelDirectories(content, result);
        Map<Long, Long> modelMap = new LinkedHashMap<>();
        Map<Long, Long> modelFieldMap = new LinkedHashMap<>();
        Map<String, String> modelCodeRename = new LinkedHashMap<>();
        importModels(content, strategy, dataSourceMap, modelDirectoryMap, modelMap, modelFieldMap, modelCodeRename, result);

        // 3. 指标目录与指标
        Map<Long, Long> metricDirectoryMap = importMetricDirectories(content, result);
        Map<Long, Long> metricMap = new LinkedHashMap<>();
        importMetrics(content, strategy, modelMap, metricDirectoryMap, modelCodeRename, metricMap, result);

        // 4. ETL 任务（会同步生成调度 Job）
        Map<Long, Long> etlTaskMap = new LinkedHashMap<>();
        Map<Long, Long> jobMap = new LinkedHashMap<>();
        importEtlTasks(content, strategy, dataSourceMap, modelMap, request, etlTaskMap, jobMap, result);

        // 5. SQL 任务与其它任务
        importJobs(content.getSqlJobs(), TaskConstants.TASK_TYPE_SQL, strategy, dataSourceMap, request, jobMap, result);
        for (Map.Entry<String, List<AppPackageJob>> entry : groupOtherJobs(content.getOtherJobs()).entrySet()) {
            importJobs(entry.getValue(), entry.getKey(), strategy, dataSourceMap, request, jobMap, result);
        }

        // 6. 编排任务（依赖前面的 Job 映射，按依赖顺序多轮创建）
        Map<Long, Long> dagJobMap = new LinkedHashMap<>();
        importDagJobs(content, strategy, Boolean.TRUE.equals(request == null ? null : request.onlineJobs), jobMap, dagJobMap, result);

        // 7. 任务依赖
        importJobDepends(content, jobMap, result);

        result.idMapping.put("dataSources", toStringKeyMap(dataSourceMap));
        result.idMapping.put("models", toStringKeyMap(modelMap));
        result.idMapping.put("modelFields", toStringKeyMap(modelFieldMap));
        result.idMapping.put("metrics", toStringKeyMap(metricMap));
        result.idMapping.put("etlTasks", toStringKeyMap(etlTaskMap));
        result.idMapping.put("jobs", toStringKeyMap(jobMap));
        result.idMapping.put("dagJobs", toStringKeyMap(dagJobMap));
        result.nextSteps.addAll(buildNextSteps(content, dagJobMap, jobMap));
        result.status = AppPackageInstance.STATUS_SUCCESS;
        result.message = "数据应用初始化完成";
        LOG.info("Initialized app package {} into current tenant: {}", result.packageCode, result.counts);
        return result;
    }

    /**
     * 生成「初始化之后该做什么」的提示。
     *
     * <p>资产包里的 SQL / ETL 任务往往有上下游依赖（例如 ADS 汇总依赖事实表），
     * 直接单跑某个下游任务必然失败。这里明确告诉用户应当先运行编排任务。
     */
    private List<String> buildNextSteps(AppPackageContent content, Map<Long, Long> dagJobMap, Map<Long, Long> jobMap) {
        List<String> steps = new ArrayList<>();
        List<String> dagNames = new ArrayList<>();
        for (AppPackageJob item : nonNull(content.getDagJobs())) {
            if (item.oldId != null && dagJobMap.containsKey(item.oldId) && item.jobName != null && !item.jobName.isBlank()) {
                dagNames.add(item.jobName);
            }
        }
        if (!dagNames.isEmpty()) {
            steps.add(
                "本资产包的任务存在上下游依赖，请先到「数据开发 → 任务编排」执行编排任务：" +
                String.join("、", dagNames) +
                "（执行一次会按依赖顺序完成「准备源数据 → 维度/事实同步 → 汇总」）"
            );
        } else if (!jobMap.isEmpty()) {
            steps.add("本资产包包含 ETL / SQL 任务，请按依赖顺序先执行上游任务，再执行下游任务");
        }
        return steps;
    }

    // ------------------------------------------------------------------ 数据源

    private Map<Long, Long> importDataSources(AppPackageContent content, AppPackageInitRequestDTO request, AppPackageInitResultDTO result) {
        Map<Long, Long> mapping = new LinkedHashMap<>();
        int created = 0;
        int reused = 0;
        int bound = 0;
        for (AppPackageDataSource item : nonNull(content.getDataSources())) {
            if (item.oldId == null) {
                continue;
            }
            String key = item.key == null || item.key.isBlank() ? AppPackageDataSource.buildKey(item.name, item.type, item.url) : item.key;
            AppPackageInitResultDTO.DataSourceMapping detail = new AppPackageInitResultDTO.DataSourceMapping();
            detail.oldId = item.oldId;
            detail.key = key;
            detail.name = item.name;

            Long newId = resolveBoundDataSource(request, key, item.oldId);
            if (newId != null) {
                detail.action = "MAPPED";
                bound++;
            } else {
                Optional<DataSource> existing = dataSourceRepository.findFirstByNameAndTypeAndUrl(item.name, item.type, item.url);
                if (existing.isPresent()) {
                    newId = existing.get().getId();
                    detail.action = "REUSED";
                    reused++;
                } else {
                    DataSource saved = dataSourceRepository.save(toDataSource(item));
                    newId = saved.getId();
                    detail.action = "CREATED";
                    created++;
                }
            }
            detail.newId = newId;
            mapping.put(item.oldId, newId);
            result.dataSources.add(detail);
        }
        result.counts.put("dataSourcesCreated", created);
        result.counts.put("dataSourcesReused", reused);
        result.counts.put("dataSourcesBound", bound);
        result.counts.put("dataSources", mapping.size());
        return mapping;
    }

    private Long resolveBoundDataSource(AppPackageInitRequestDTO request, String key, Long oldId) {
        if (request == null || request.dataSourceMapping == null || request.dataSourceMapping.isEmpty()) {
            return null;
        }
        Long mapped = request.dataSourceMapping.get(key);
        if (mapped == null) {
            mapped = request.dataSourceMapping.get(String.valueOf(oldId));
        }
        if (mapped == null) {
            return null;
        }
        Long targetId = mapped;
        return dataSourceRepository
            .findById(targetId)
            .map(DataSource::getId)
            .orElseThrow(() -> new IllegalArgumentException("指定的数据源不存在：" + targetId));
    }

    private DataSource toDataSource(AppPackageDataSource item) {
        DataSource entity = new DataSource();
        entity.setName(item.name);
        entity.setDescription(item.description);
        entity.setType(item.type);
        entity.setUrl(item.url);
        entity.setHostname(item.hostname);
        entity.setPort(item.port);
        entity.setSchemaName(item.schemaName);
        entity.setDatabase(item.database);
        entity.setUsername(item.username);
        entity.setPassword(item.password);
        entity.setConnectionMode(item.connectionMode);
        entity.setExtraParams(item.extraParams == null || item.extraParams.isEmpty() ? null : new LinkedHashMap<>(item.extraParams));
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    // ------------------------------------------------------------------ 目录

    private Map<Long, Long> importModelDirectories(AppPackageContent content, AppPackageInitResultDTO result) {
        Map<Long, Long> mapping = new LinkedHashMap<>();
        int created = 0;
        List<AppPackageDirectory> pending = new ArrayList<>(nonNull(content.getModelDirectories()));
        while (!pending.isEmpty()) {
            List<AppPackageDirectory> next = new ArrayList<>();
            boolean progress = false;
            for (AppPackageDirectory directory : pending) {
                if (directory.oldId == null) {
                    continue;
                }
                if (directory.parentOldId != null && !mapping.containsKey(directory.parentOldId)) {
                    next.add(directory);
                    continue;
                }
                Long parentId = directory.parentOldId == null ? null : mapping.get(directory.parentOldId);
                ModelDirectory entity = findModelDirectory(directory.name, parentId);
                if (entity == null) {
                    entity = new ModelDirectory();
                    entity.setName(directory.name == null ? "导入目录" : directory.name);
                    entity.setParentId(parentId == null ? 0L : parentId);
                    entity.setSortOrder(directory.sortOrder == null ? 0 : directory.sortOrder);
                    ZonedDateTime now = ZonedDateTime.now();
                    entity.setCreateTime(now);
                    entity.setUpdateTime(now);
                    entity = modelDirectoryRepository.save(entity);
                    created++;
                }
                mapping.put(directory.oldId, entity.getId());
                progress = true;
            }
            if (!progress) {
                for (AppPackageDirectory directory : next) {
                    result.warnings.add("模型目录的父目录不在资产包内，已挂到根目录：" + directory.name);
                    ModelDirectory entity = new ModelDirectory();
                    entity.setName(directory.name == null ? "导入目录" : directory.name);
                    entity.setParentId(0L);
                    entity.setSortOrder(directory.sortOrder == null ? 0 : directory.sortOrder);
                    ZonedDateTime now = ZonedDateTime.now();
                    entity.setCreateTime(now);
                    entity.setUpdateTime(now);
                    entity = modelDirectoryRepository.save(entity);
                    mapping.put(directory.oldId, entity.getId());
                    created++;
                }
                break;
            }
            pending = next;
        }
        result.counts.put("modelDirectories", created);
        return mapping;
    }

    private ModelDirectory findModelDirectory(String name, Long parentId) {
        String directoryName = name == null ? "导入目录" : name;
        if (parentId == null) {
            return modelDirectoryRepository
                .findFirstByNameAndParentId(directoryName, 0L)
                .or(() -> modelDirectoryRepository.findFirstByNameAndParentIdIsNull(directoryName))
                .orElse(null);
        }
        return modelDirectoryRepository.findFirstByNameAndParentId(directoryName, parentId).orElse(null);
    }

    private Map<Long, Long> importMetricDirectories(AppPackageContent content, AppPackageInitResultDTO result) {
        Map<Long, Long> mapping = new LinkedHashMap<>();
        int created = 0;
        List<AppPackageDirectory> pending = new ArrayList<>(nonNull(content.getMetricDirectories()));
        while (!pending.isEmpty()) {
            List<AppPackageDirectory> next = new ArrayList<>();
            boolean progress = false;
            for (AppPackageDirectory directory : pending) {
                if (directory.oldId == null) {
                    continue;
                }
                if (directory.parentOldId != null && !mapping.containsKey(directory.parentOldId)) {
                    next.add(directory);
                    continue;
                }
                Long parentId = directory.parentOldId == null ? null : mapping.get(directory.parentOldId);
                MetricDirectory entity = findMetricDirectory(directory.name, parentId);
                if (entity == null) {
                    entity = new MetricDirectory();
                    entity.setName(directory.name == null ? "导入目录" : directory.name);
                    entity.setParentId(parentId == null ? 0L : parentId);
                    entity.setSortOrder(directory.sortOrder == null ? 0 : directory.sortOrder);
                    ZonedDateTime now = ZonedDateTime.now();
                    entity.setCreateTime(now);
                    entity.setUpdateTime(now);
                    entity = metricDirectoryRepository.save(entity);
                    created++;
                }
                mapping.put(directory.oldId, entity.getId());
                progress = true;
            }
            if (!progress) {
                for (AppPackageDirectory directory : next) {
                    result.warnings.add("指标目录的父目录不在资产包内，已挂到根目录：" + directory.name);
                    MetricDirectory entity = new MetricDirectory();
                    entity.setName(directory.name == null ? "导入目录" : directory.name);
                    entity.setParentId(0L);
                    entity.setSortOrder(directory.sortOrder == null ? 0 : directory.sortOrder);
                    ZonedDateTime now = ZonedDateTime.now();
                    entity.setCreateTime(now);
                    entity.setUpdateTime(now);
                    entity = metricDirectoryRepository.save(entity);
                    mapping.put(directory.oldId, entity.getId());
                    created++;
                }
                break;
            }
            pending = next;
        }
        result.counts.put("metricDirectories", created);
        return mapping;
    }

    private MetricDirectory findMetricDirectory(String name, Long parentId) {
        String directoryName = name == null ? "导入目录" : name;
        if (parentId == null) {
            return metricDirectoryRepository
                .findFirstByNameAndParentId(directoryName, 0L)
                .or(() -> metricDirectoryRepository.findFirstByNameAndParentIdIsNull(directoryName))
                .orElse(null);
        }
        return metricDirectoryRepository.findFirstByNameAndParentId(directoryName, parentId).orElse(null);
    }

    // ------------------------------------------------------------------ 模型与字段

    private void importModels(
        AppPackageContent content,
        String strategy,
        Map<Long, Long> dataSourceMap,
        Map<Long, Long> directoryMap,
        Map<Long, Long> modelMap,
        Map<Long, Long> modelFieldMap,
        Map<String, String> modelCodeRename,
        AppPackageInitResultDTO result
    ) {
        int created = 0;
        int skipped = 0;
        for (AppPackageModel item : nonNull(content.getModels())) {
            if (item.oldId == null) {
                continue;
            }
            if (item.code != null && dataModelRepository.findFirstByCode(item.code).isPresent()) {
                if (STRATEGY_SKIP.equals(strategy)) {
                    result.warnings.add("数据模型编码已存在，按策略跳过：" + item.code);
                    skipped++;
                    continue;
                }
                String newCode = uniqueModelCode(item.code);
                modelCodeRename.put(item.code, newCode);
                result.warnings.add("数据模型编码已存在，已重命名为：" + item.code + " -> " + newCode);
                item.code = newCode;
            }
            DataModel entity = new DataModel();
            entity.setName(item.name);
            entity.setCode(item.code);
            entity.setDescription(item.description);
            entity.setModelType(item.modelType);
            entity.setDimensionKind(item.dimensionKind);
            entity.setLevelCount(item.levelCount);
            entity.setTimeFieldName(item.timeFieldName);
            entity.setTimeLevels(item.timeLevels);
            entity.setTimeStart(item.timeStart);
            entity.setTimeEnd(item.timeEnd);
            entity.setIsRegistered(item.isRegistered == null ? Boolean.FALSE : item.isRegistered);
            entity.setProject(item.project);
            entity.setSchemaName(item.schemaName);
            entity.setTableName(item.tableName);
            entity.setDisplayFieldName(item.displayFieldName);
            if (item.dataSourceOldId != null && !dataSourceMap.containsKey(item.dataSourceOldId)) {
                result.warnings.add("数据模型引用的数据源不在资产包内，模型将不绑定数据源：" + item.code + " -> dataSourceId=" + item.dataSourceOldId);
            }
            if (item.directoryOldId != null && !directoryMap.containsKey(item.directoryOldId)) {
                result.warnings.add("数据模型引用的目录不在资产包内，模型将挂到根目录：" + item.code);
            }
            entity.setDataSourceId(item.dataSourceOldId == null ? null : dataSourceMap.get(item.dataSourceOldId));
            entity.setDirectoryId(item.directoryOldId == null ? null : directoryMap.get(item.directoryOldId));
            ZonedDateTime now = ZonedDateTime.now();
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity = dataModelRepository.save(entity);
            modelMap.put(item.oldId, entity.getId());
            created++;
        }
        result.counts.put("models", created);
        result.counts.put("modelsSkipped", skipped);

        // 字段分两轮写入：第一轮建立字段与维度模型的关联，第二轮补上「关联维度字段」
        // （dimensionFieldId 依赖第一轮生成的字段 ID）
        List<long[]> pendingDimensionField = new ArrayList<>();
        int fieldCount = 0;
        for (AppPackageModel item : nonNull(content.getModels())) {
            Long newModelId = item.oldId == null ? null : modelMap.get(item.oldId);
            if (newModelId == null) {
                continue;
            }
            for (AppPackageModel.AppPackageModelField field : nonNull(item.fields)) {
                ModelField entity = new ModelField();
                entity.setModelId(newModelId);
                entity.setFieldName(field.fieldName);
                entity.setFieldType(field.fieldType);
                entity.setFieldLength(field.fieldLength);
                entity.setFieldPrecision(field.fieldPrecision);
                entity.setFieldScale(field.fieldScale);
                entity.setDescription(field.description);
                entity.setSortOrder(field.sortOrder);
                entity.setIsPartitionKey(field.isPartitionKey);
                entity.setIsPrimaryKey(field.isPrimaryKey);
                if (field.dimensionModelOldId != null && !modelMap.containsKey(field.dimensionModelOldId)) {
                    result.warnings.add("字段关联的维度模型不在资产包内，已忽略该关联：" + item.code + "." + field.fieldName);
                }
                entity.setDimensionModelId(field.dimensionModelOldId == null ? null : modelMap.get(field.dimensionModelOldId));
                entity.setFieldRole(field.fieldRole);
                entity.setLevelIndex(field.levelIndex);
                ZonedDateTime now = ZonedDateTime.now();
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                entity = modelFieldRepository.save(entity);
                if (field.oldId != null) {
                    modelFieldMap.put(field.oldId, entity.getId());
                }
                if (field.dimensionFieldOldId != null) {
                    pendingDimensionField.add(new long[] { entity.getId(), field.dimensionFieldOldId });
                }
                fieldCount++;
            }
        }
        for (long[] pair : pendingDimensionField) {
            Long dimensionFieldId = modelFieldMap.get(pair[1]);
            if (dimensionFieldId == null) {
                result.warnings.add("字段关联的维度字段不在资产包内，已忽略该关联：field#" + pair[0]);
                continue;
            }
            modelFieldRepository
                .findById(pair[0])
                .ifPresent(field -> {
                    field.setDimensionFieldId(dimensionFieldId);
                    field.setUpdateTime(ZonedDateTime.now());
                    modelFieldRepository.save(field);
                });
        }
        result.counts.put("modelFields", fieldCount);
    }

    private String uniqueModelCode(String code) {
        String candidate = code + "_copy";
        int index = 2;
        while (dataModelRepository.findFirstByCode(candidate).isPresent()) {
            candidate = code + "_copy" + index;
            index++;
        }
        return candidate;
    }

    // ------------------------------------------------------------------ 指标

    private void importMetrics(
        AppPackageContent content,
        String strategy,
        Map<Long, Long> modelMap,
        Map<Long, Long> directoryMap,
        Map<String, String> modelCodeRename,
        Map<Long, Long> metricMap,
        AppPackageInitResultDTO result
    ) {
        List<AppPackageMetric> items = new ArrayList<>(nonNull(content.getMetrics()));

        // 预判编码冲突，先定稿每个指标的最终编码：后续公式改写、依赖排序都以最终编码为准
        Set<String> tenantCodes = new LinkedHashSet<>();
        metricRepository.findAll().forEach(metric -> tenantCodes.add(metric.getCode()));
        Set<String> plannedCodes = new LinkedHashSet<>(tenantCodes);
        Map<String, String> codeRename = new LinkedHashMap<>();
        Set<Long> skippedIds = new LinkedHashSet<>();
        for (AppPackageMetric item : items) {
            if (item.oldId == null || item.code == null) {
                continue;
            }
            if (!plannedCodes.add(item.code)) {
                if (STRATEGY_SKIP.equals(strategy)) {
                    result.warnings.add("指标编码已存在，按策略跳过：" + item.code);
                    skippedIds.add(item.oldId);
                    continue;
                }
                String newCode = uniqueMetricCode(item.code, plannedCodes);
                codeRename.put(item.code, newCode);
                result.warnings.add("指标编码已存在，已重命名为：" + item.code + " -> " + newCode);
                item.code = newCode;
                plannedCodes.add(newCode);
            }
        }

        // 按依赖顺序导入：衍生指标必须等它引用的指标就位后再导入
        List<AppPackageMetric> pending = new ArrayList<>();
        for (AppPackageMetric item : items) {
            if (item.oldId != null && !skippedIds.contains(item.oldId)) {
                pending.add(item);
            }
        }
        Set<String> availableCodes = new LinkedHashSet<>(tenantCodes);
        int created = 0;
        int round = 0;
        while (!pending.isEmpty() && round <= pending.size() + 1) {
            round++;
            List<AppPackageMetric> next = new ArrayList<>();
            for (AppPackageMetric item : pending) {
                boolean derived = Metric.TYPE_DERIVED.equals(item.metricType);
                if (derived && !availableCodes.containsAll(parseFormulaRefs(rewriteFormula(item.formula, codeRename)))) {
                    next.add(item);
                    continue;
                }
                // 原子指标依赖事实表模型：模型被跳过（SKIP 策略）时指标无法重建，一并跳过
                if (!derived && item.factModelOldId != null && !modelMap.containsKey(item.factModelOldId)) {
                    result.warnings.add("指标依赖的事实表模型未导入，已跳过该指标：" + item.code);
                    skippedIds.add(item.oldId);
                    continue;
                }
                MetricDTO dto = new MetricDTO();
                dto.setName(item.name);
                dto.setCode(item.code);
                dto.setDescription(item.description);
                dto.setMetricType(item.metricType);
                dto.setStatus(item.status == null || item.status.isBlank() ? Metric.STATUS_ENABLED : item.status);
                dto.setFactModelId(item.factModelOldId == null ? null : modelMap.get(item.factModelOldId));
                dto.setFilterConfig(remapFilterConfig(item.filterConfig, modelMap, modelCodeRename));
                dto.setUnit(item.unit);
                dto.setDataType(item.dataType);
                dto.setIsAdditive(item.isAdditive);
                dto.setFormula(rewriteFormula(item.formula, codeRename));
                MetricDTO saved = metricService.save(dto);
                metricMap.put(item.oldId, saved.getId());
                if (saved.getCode() != null) {
                    availableCodes.add(saved.getCode());
                }
                created++;
            }
            if (next.size() == pending.size()) {
                StringBuilder missing = new StringBuilder();
                for (AppPackageMetric item : next) {
                    missing.append(item.name == null ? item.code : item.name).append(' ');
                }
                throw new IllegalArgumentException("以下衍生指标引用的指标不在资产包内，且当前租户也不存在：" + missing.toString().trim());
            }
            pending = next;
        }
        result.counts.put("metrics", created);
        result.counts.put("metricsSkipped", skippedIds.size());
    }

    private String uniqueMetricCode(String code, Set<String> usedCodes) {
        String candidate = code + "_copy";
        int index = 2;
        while (usedCodes.contains(candidate)) {
            candidate = code + "_copy" + index;
            index++;
        }
        return candidate;
    }

    private Set<String> parseFormulaRefs(String formula) {
        Set<String> codes = new LinkedHashSet<>();
        if (formula == null) {
            return codes;
        }
        Matcher matcher = FORMULA_REF_PATTERN.matcher(formula);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    /** 编码被重命名时，同步改写衍生指标公式里的引用，保证包内引用自洽。 */
    private String rewriteFormula(String formula, Map<String, String> codeRename) {
        if (formula == null || codeRename.isEmpty()) {
            return formula;
        }
        String rewritten = formula;
        for (Map.Entry<String, String> entry : codeRename.entrySet()) {
            rewritten = rewritten.replace("${" + entry.getKey() + "}", "${" + entry.getValue() + "}");
        }
        return rewritten;
    }

    /**
     * 重写指标业务限定：{@code dimensionModelId} 换成新模型 ID，
     * {@code dimensionModelCode} 在模型编码被重命名时同步改写。
     *
     * <p>兼容两种资产包写法：结构化的 JSON 对象，或（历史版本 / 手工编写）以字符串承载的 JSON 文本；
     * 返回值统一为可写回 {@code Metric.filterConfig} 的 JSON 字符串。
     */
    private String remapFilterConfig(JsonNode filterConfig, Map<Long, Long> modelMap, Map<String, String> modelCodeRename) {
        if (filterConfig == null || filterConfig.isNull()) {
            return null;
        }
        JsonNode root = filterConfig;
        if (root.isTextual()) {
            JsonNode parsed = AppPackageJson.readTreeQuietly(root.asText());
            if (parsed == null) {
                return root.asText();
            }
            root = parsed;
        }
        JsonNode remapped = remapIds(root.deepCopy(), Map.of("dimensionModelId", modelMap));
        if (!modelCodeRename.isEmpty() && remapped.isObject()) {
            JsonNode conditions = remapped.get("conditions");
            if (conditions != null && conditions.isArray()) {
                for (JsonNode condition : conditions) {
                    if (!condition.isObject()) {
                        continue;
                    }
                    JsonNode code = condition.get("dimensionModelCode");
                    if (code == null || code.isNull()) {
                        continue;
                    }
                    String renamed = modelCodeRename.get(code.asText());
                    if (renamed != null) {
                        ((ObjectNode) condition).put("dimensionModelCode", renamed);
                    }
                }
            }
        }
        return remapped.toString();
    }

    // ------------------------------------------------------------------ ETL 任务

    private void importEtlTasks(
        AppPackageContent content,
        String strategy,
        Map<Long, Long> dataSourceMap,
        Map<Long, Long> modelMap,
        AppPackageInitRequestDTO request,
        Map<Long, Long> etlTaskMap,
        Map<Long, Long> jobMap,
        AppPackageInitResultDTO result
    ) {
        int created = 0;
        for (AppPackageEtlTask item : nonNull(content.getEtlTasks())) {
            if (item.oldId == null) {
                continue;
            }
            if (item.taskCode != null && etlTaskRepository.findFirstByTaskCode(item.taskCode).isPresent()) {
                if (STRATEGY_SKIP.equals(strategy)) {
                    result.warnings.add("ETL 任务编码已存在，按策略跳过：" + item.taskCode);
                    continue;
                }
                String newCode = uniqueEtlTaskCode(item.taskCode);
                result.warnings.add("ETL 任务编码已存在，已重命名为：" + item.taskCode + " -> " + newCode);
                item.taskCode = newCode;
            }
            ETLTaskDTO dto = new ETLTaskDTO();
            dto.setTaskName(item.taskName);
            dto.setTaskCode(item.taskCode);
            dto.setTaskDesc(item.taskDesc);
            dto.setDir(item.dir);
            dto.setType(item.type == null ? TaskConstants.TASK_TYPE_ETL : item.type);
            dto.setCron(item.cron);
            dto.setProject(item.project);
            dto.setStatus(IMPORTED_STATUS);
            dto.setDr(0);
            dto.setNodes(nonNull(item.nodes).stream().map(node -> toNodeDto(node, dataSourceMap, modelMap, result)).toList());
            dto.setEdges(nonNull(item.edges).stream().map(this::toEdgeDto).toList());
            ETLTaskDTO saved = etlTaskService.save(dto);
            etlTaskMap.put(item.oldId, saved.getId());
            if (item.jobOldId != null && saved.getJobId() != null) {
                // 编排任务引用的是 Job ID，这里把 ETL 任务的 Job ID 也登记进映射表
                jobMap.put(item.jobOldId, saved.getJobId());
            }
            if (Boolean.TRUE.equals(request == null ? null : request.onlineJobs)) {
                etlTaskService.online(saved.getId());
            }
            created++;
        }
        result.counts.put("etlTasks", created);
    }

    private String uniqueEtlTaskCode(String code) {
        String candidate = code + "_copy";
        int index = 2;
        while (etlTaskRepository.findFirstByTaskCode(candidate).isPresent()) {
            candidate = code + "_copy" + index;
            index++;
        }
        return candidate;
    }

    private ETLNodeDTO toNodeDto(
        AppPackageEtlTask.AppPackageEtlNode node,
        Map<Long, Long> dataSourceMap,
        Map<Long, Long> modelMap,
        AppPackageInitResultDTO result
    ) {
        ETLNodeDTO dto = new ETLNodeDTO();
        dto.setLabel(node.label);
        dto.setCode(node.code);
        dto.setDesc(node.desc);
        dto.setType(node.type);
        dto.setxAxis(node.xAxis);
        dto.setyAxis(node.yAxis);
        dto.setStatus(node.status);
        JsonNode raw = node.config;
        if (raw == null) {
            return dto;
        }
        warnUnmappedReferences(node.label, raw, dataSourceMap, modelMap, result);
        Map<String, Map<Long, Long>> remap = new LinkedHashMap<>();
        remap.put("sourceId", dataSourceMap);
        remap.put("modelId", modelMap);
        JsonNode remapped = remapIds(raw.deepCopy(), remap);
        dto.setConfig(remapped == null ? null : remapped.toString());
        return dto;
    }

    private void warnUnmappedReferences(
        String nodeLabel,
        JsonNode config,
        Map<Long, Long> dataSourceMap,
        Map<Long, Long> modelMap,
        AppPackageInitResultDTO result
    ) {
        if (config == null || !config.isObject()) {
            return;
        }
        JsonNode sourceId = config.get("sourceId");
        if (sourceId != null && sourceId.canConvertToLong() && !dataSourceMap.containsKey(sourceId.asLong())) {
            result.warnings.add("ETL 节点引用的数据源不在资产包内：" + nodeLabel + " -> dataSourceId=" + sourceId.asLong());
        }
        JsonNode modelId = config.get("modelId");
        if (modelId != null && modelId.canConvertToLong() && !modelMap.containsKey(modelId.asLong())) {
            result.warnings.add("ETL 节点引用的数据模型不在资产包内：" + nodeLabel + " -> modelId=" + modelId.asLong());
        }
    }

    private ETLEdgeDTO toEdgeDto(AppPackageEtlTask.AppPackageEtlEdge edge) {
        ETLEdgeDTO dto = new ETLEdgeDTO();
        dto.setName(edge.name);
        dto.setCode(edge.code);
        dto.setSource(edge.source);
        dto.setTarget(edge.target);
        dto.setConfig(edge.config);
        dto.setStatus(edge.status);
        return dto;
    }

    // ------------------------------------------------------------------ 普通任务（SQL / Shell 等）

    private void importJobs(
        List<AppPackageJob> items,
        String type,
        String strategy,
        Map<Long, Long> dataSourceMap,
        AppPackageInitRequestDTO request,
        Map<Long, Long> jobMap,
        AppPackageInitResultDTO result
    ) {
        int created = 0;
        for (AppPackageJob item : nonNull(items)) {
            if (item.oldId == null) {
                continue;
            }
            JsonNode context = item.jobContext == null ? null : item.jobContext.deepCopy();
            if (context != null && TaskConstants.TASK_TYPE_SQL.equals(type)) {
                if (context.isObject()) {
                    JsonNode dataSourceId = context.get("dataSourceId");
                    if (dataSourceId != null && dataSourceId.canConvertToLong() && !dataSourceMap.containsKey(dataSourceId.asLong())) {
                        result.warnings.add("SQL 任务引用的数据源不在资产包内：" + item.jobName + " -> dataSourceId=" + dataSourceId.asLong());
                    }
                }
                context = remapIds(context, Map.of("dataSourceId", dataSourceMap));
            }
            String jobName = resolveJobName(item.jobName, strategy, result);
            if (jobName == null) {
                continue;
            }
            Job job = new Job();
            job.setJobName(jobName);
            job.setJobGroup(item.jobGroup == null ? "datafusion" : item.jobGroup);
            job.setType(type == null ? item.type : type);
            job.setCron(item.cron);
            job.setJobContext(context == null ? null : context.toString());
            job.setStatus(IMPORTED_STATUS);
            job.setProject(item.project);
            ZonedDateTime now = ZonedDateTime.now();
            job.setCreateTime(now);
            job.setUpdateTime(now);
            job = jobRepository.save(job);
            jobMap.put(item.oldId, job.getId());
            if (Boolean.TRUE.equals(request == null ? null : request.onlineJobs)) {
                jobService.online(job, false);
            }
            created++;
        }
        result.counts.merge(jobCountKey(type), created, Integer::sum);
    }

    private String jobCountKey(String type) {
        if (TaskConstants.TASK_TYPE_SQL.equals(type)) {
            return "sqlJobs";
        }
        if (TaskConstants.TASK_TYPE_DAG.equals(type)) {
            return "dagJobs";
        }
        return "otherJobs";
    }

    /**
     * 处理任务名称冲突：任务表没有唯一约束，但重复导入同一资产包会产生同名任务，
     * 因此按冲突策略决定是重命名还是跳过。返回 null 表示跳过该任务。
     */
    private String resolveJobName(String jobName, String strategy, AppPackageInitResultDTO result) {
        if (jobName == null || jobRepository.findFirstByJobName(jobName).isEmpty()) {
            return jobName;
        }
        if (STRATEGY_SKIP.equals(strategy)) {
            result.warnings.add("任务名称已存在，按策略跳过：" + jobName);
            return null;
        }
        String candidate = jobName + " (副本)";
        int index = 2;
        while (jobRepository.findFirstByJobName(candidate).isPresent()) {
            candidate = jobName + " (副本" + index + ")";
            index++;
        }
        result.warnings.add("任务名称已存在，已重命名为：" + jobName + " -> " + candidate);
        return candidate;
    }

    private Map<String, List<AppPackageJob>> groupOtherJobs(List<AppPackageJob> items) {
        Map<String, List<AppPackageJob>> grouped = new LinkedHashMap<>();
        for (AppPackageJob item : nonNull(items)) {
            String type = item.type == null ? TaskConstants.TASK_TYPE_DEMO : item.type;
            grouped.computeIfAbsent(type, key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    // ------------------------------------------------------------------ 编排任务

    private void importDagJobs(
        AppPackageContent content,
        String strategy,
        boolean onlineJobs,
        Map<Long, Long> jobMap,
        Map<Long, Long> dagJobMap,
        AppPackageInitResultDTO result
    ) {
        List<AppPackageJob> pending = new ArrayList<>(nonNull(content.getDagJobs()));
        int created = 0;
        int round = 0;
        while (!pending.isEmpty() && round <= pending.size() + 1) {
            round++;
            List<AppPackageJob> next = new ArrayList<>();
            for (AppPackageJob item : pending) {
                if (item.oldId == null) {
                    continue;
                }
                // 编排任务可能引用包内另一个编排任务，必须等被引用者先建好
                boolean waitingForInnerDag = extractDagReferences(item.jobContext)
                    .stream()
                    .anyMatch(id -> !id.equals(item.oldId) && isDagInPackage(content, id) && !dagJobMap.containsKey(id));
                if (waitingForInnerDag) {
                    next.add(item);
                    continue;
                }
                if (saveDagJob(item, strategy, onlineJobs, jobMap, dagJobMap, result)) {
                    created++;
                }
            }
            if (next.size() == pending.size()) {
                for (AppPackageJob item : next) {
                    if (saveDagJob(item, strategy, onlineJobs, jobMap, dagJobMap, result)) {
                        created++;
                    }
                }
                break;
            }
            pending = next;
        }
        result.counts.merge("dagJobs", created, Integer::sum);
    }

    private boolean saveDagJob(
        AppPackageJob item,
        String strategy,
        boolean online,
        Map<Long, Long> jobMap,
        Map<Long, Long> dagJobMap,
        AppPackageInitResultDTO result
    ) {
        String jobName = resolveJobName(item.jobName, strategy, result);
        if (jobName == null) {
            return false;
        }
        JsonNode context = remapDagContext(item.jobContext, jobMap, result, jobName);
        Job job = new Job();
        job.setJobName(jobName);
        job.setJobGroup(item.jobGroup == null ? "datafusion" : item.jobGroup);
        job.setType(TaskConstants.TASK_TYPE_DAG);
        job.setCron(item.cron);
        job.setJobContext(context == null ? null : context.toString());
        job.setStatus(IMPORTED_STATUS);
        job.setProject(item.project);
        ZonedDateTime now = ZonedDateTime.now();
        job.setCreateTime(now);
        job.setUpdateTime(now);
        job = jobRepository.save(job);
        jobMap.put(item.oldId, job.getId());
        dagJobMap.put(item.oldId, job.getId());
        if (online) {
            jobService.online(job, false);
        }
        return true;
    }

    private boolean isDagInPackage(AppPackageContent content, Long jobOldId) {
        return nonNull(content.getDagJobs()).stream().anyMatch(item -> jobOldId.equals(item.oldId));
    }

    private Set<Long> extractDagReferences(JsonNode context) {
        Set<Long> ids = new LinkedHashSet<>();
        if (context == null || !context.isObject()) {
            return ids;
        }
        JsonNode jobs = context.get("jobs");
        if (jobs != null && jobs.isArray()) {
            for (JsonNode job : jobs) {
                Long id = asLong(job.get("id"));
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /**
     * 重写编排任务 jobContext 中的子任务引用：jobs、jobDepends、nodeLayout。
     * 引用了包外任务的节点会被移除并记录告警，避免导入出一个必然失败的 DAG。
     */
    private JsonNode remapDagContext(JsonNode context, Map<Long, Long> jobMap, AppPackageInitResultDTO result, String dagName) {
        if (context == null || !context.isObject()) {
            return context;
        }
        ObjectNode root = ((ObjectNode) context).deepCopy();

        Set<Long> keptOldIds = new LinkedHashSet<>();
        ArrayNode jobs = AppPackageJson.mapper().createArrayNode();
        JsonNode jobsNode = root.get("jobs");
        if (jobsNode != null && jobsNode.isArray()) {
            for (JsonNode job : jobsNode) {
                Long oldId = asLong(job.get("id"));
                Long newId = oldId == null ? null : jobMap.get(oldId);
                if (newId == null) {
                    result.warnings.add("编排任务引用的子任务不在资产包内，已移除该节点：" + dagName + " -> jobId=" + oldId);
                    continue;
                }
                ObjectNode copy = job.isObject() ? ((ObjectNode) job).deepCopy() : AppPackageJson.mapper().createObjectNode();
                copy.put("id", newId);
                jobs.add(copy);
                keptOldIds.add(oldId);
            }
        }
        root.set("jobs", jobs);

        ArrayNode depends = AppPackageJson.mapper().createArrayNode();
        JsonNode dependsNode = root.get("jobDepends");
        if (dependsNode != null && dependsNode.isArray()) {
            for (JsonNode depend : dependsNode) {
                Long parentOld = asLong(depend.get("parentJobCode"));
                Long childOld = asLong(depend.get("childJobCode"));
                Long parentNew = parentOld == null ? null : jobMap.get(parentOld);
                Long childNew = childOld == null ? null : jobMap.get(childOld);
                if (parentNew == null || childNew == null || !keptOldIds.contains(parentOld) || !keptOldIds.contains(childOld)) {
                    continue;
                }
                ObjectNode copy = depend.isObject() ? ((ObjectNode) depend).deepCopy() : AppPackageJson.mapper().createObjectNode();
                copy.put("parentJobCode", parentNew);
                copy.put("childJobCode", childNew);
                depends.add(copy);
            }
        }
        root.set("jobDepends", depends);

        ArrayNode layout = AppPackageJson.mapper().createArrayNode();
        JsonNode layoutNode = root.get("nodeLayout");
        if (layoutNode != null && layoutNode.isArray()) {
            for (JsonNode item : layoutNode) {
                Long oldId = asLong(item.get("jobId"));
                Long newId = oldId == null ? null : jobMap.get(oldId);
                if (newId == null || !keptOldIds.contains(oldId)) {
                    continue;
                }
                ObjectNode copy = item.isObject() ? ((ObjectNode) item).deepCopy() : AppPackageJson.mapper().createObjectNode();
                copy.put("jobId", newId);
                layout.add(copy);
            }
        }
        root.set("nodeLayout", layout);
        return root;
    }

    // ------------------------------------------------------------------ 任务依赖

    private void importJobDepends(AppPackageContent content, Map<Long, Long> jobMap, AppPackageInitResultDTO result) {
        int created = 0;
        for (AppPackageJobDepend item : nonNull(content.getJobDepends())) {
            Long parentNew = item.parentJobOldId == null ? null : jobMap.get(item.parentJobOldId);
            Long childNew = item.childJobOldId == null ? null : jobMap.get(item.childJobOldId);
            if (parentNew == null || childNew == null) {
                result.warnings.add("任务依赖引用的任务不在资产包内，已忽略：" + item.parentJobOldId + " -> " + item.childJobOldId);
                continue;
            }
            JobDepend entity = new JobDepend();
            entity.setParentJobCode(String.valueOf(parentNew));
            entity.setChildJobCode(String.valueOf(childNew));
            entity.setJobCode(String.valueOf(childNew));
            entity.setLastInterval(item.lastInterval == null ? 0L : item.lastInterval);
            entity.setCreateTime(ZonedDateTime.now());
            jobDependRepository.save(entity);
            created++;
        }
        result.counts.put("jobDepends", created);
    }

    // ------------------------------------------------------------------ 通用工具

    /**
     * 递归重写 JSON 中指定字段名的数字 ID：字段名命中且值能在映射表中找到时替换为新 ID，
     * 找不到则保留原值（由调用方决定是否告警）。
     */
    private JsonNode remapIds(JsonNode node, Map<String, Map<Long, Long>> remapByField) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = object.get(name);
                Map<Long, Long> mapping = remapByField.get(name);
                if (mapping != null && value != null && value.canConvertToLong()) {
                    Long mapped = mapping.get(value.asLong());
                    if (mapped != null) {
                        object.put(name, mapped);
                        continue;
                    }
                }
                JsonNode remapped = remapIds(value, remapByField);
                if (remapped != null && remapped != value) {
                    object.set(name, remapped);
                }
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode remapped = remapIds(array.get(i), remapByField);
                if (remapped != null && remapped != array.get(i)) {
                    array.set(i, remapped);
                }
            }
            return array;
        }
        return node;
    }

    private static Long asLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.asLong();
        }
        try {
            return Long.valueOf(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Long> toStringKeyMap(Map<Long, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String normalizeStrategy(AppPackageInitRequestDTO request) {
        String strategy = request == null ? null : request.conflictStrategy;
        if (strategy == null || strategy.isBlank()) {
            return STRATEGY_RENAME;
        }
        return strategy.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
