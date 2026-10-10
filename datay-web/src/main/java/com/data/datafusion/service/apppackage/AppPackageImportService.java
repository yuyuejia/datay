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
 * 需要上线时由调用方显式传 {@code onlineJobs=true}。当冲突策略为 {@link #STRATEGY_OVERWRITE} 时，
 * 已存在的资产会就地更新（保留原 ID），原本在线的任务在覆盖后继续保持在线，便于资产包升级后的覆盖安装。
 */
@Service
public class AppPackageImportService {

    private static final Logger LOG = LoggerFactory.getLogger(AppPackageImportService.class);

    /** 冲突策略：自动重命名。 */
    public static final String STRATEGY_RENAME = "RENAME";

    /** 冲突策略：跳过已存在的同编码资产。 */
    public static final String STRATEGY_SKIP = "SKIP";

    /** 冲突策略：覆盖更新已存在的同编码 / 同名资产（默认，用于资产包升级后的覆盖安装）。 */
    public static final String STRATEGY_OVERWRITE = "OVERWRITE";

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
    public AppPackageInitResultDTO importContent(AppPackageContent content, AppPackageInitRequestDTO request, String packageId) {
        AppPackageInitResultDTO result = new AppPackageInitResultDTO();
        result.packageId = packageId;
        result.packageName = content.getMeta() == null ? null : content.getMeta().getName();
        result.packageCode = content.getMeta() == null ? null : content.getMeta().getCode();
        String strategy = normalizeStrategy(request);

        // 1. 数据源：复用优先，其次新建，同时应用调用方指定的绑定
        Map<String, String> dataSourceMap = importDataSources(content, request, result);

        // 2. 模型目录与模型（含字段）
        Map<String, String> modelDirectoryMap = importModelDirectories(content, result);
        Map<String, String> modelMap = new LinkedHashMap<>();
        Map<String, String> modelFieldMap = new LinkedHashMap<>();
        Map<String, String> modelCodeRename = new LinkedHashMap<>();
        importModels(content, strategy, dataSourceMap, modelDirectoryMap, modelMap, modelFieldMap, modelCodeRename, result);

        // 3. 指标目录与指标
        Map<String, String> metricDirectoryMap = importMetricDirectories(content, result);
        Map<String, String> metricMap = new LinkedHashMap<>();
        importMetrics(content, strategy, modelMap, metricDirectoryMap, modelCodeRename, metricMap, result);

        // 4. ETL 任务（会同步生成调度 Job）
        Map<String, String> etlTaskMap = new LinkedHashMap<>();
        Map<String, String> jobMap = new LinkedHashMap<>();
        importEtlTasks(content, strategy, dataSourceMap, modelMap, request, etlTaskMap, jobMap, result);

        // 5. SQL 任务与其它任务
        importJobs(content.getSqlJobs(), TaskConstants.TASK_TYPE_SQL, strategy, dataSourceMap, request, jobMap, result);
        for (Map.Entry<String, List<AppPackageJob>> entry : groupOtherJobs(content.getOtherJobs()).entrySet()) {
            importJobs(entry.getValue(), entry.getKey(), strategy, dataSourceMap, request, jobMap, result);
        }

        // 6. 编排任务（依赖前面的 Job 映射，按依赖顺序多轮创建）
        Map<String, String> dagJobMap = new LinkedHashMap<>();
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
    private List<String> buildNextSteps(AppPackageContent content, Map<String, String> dagJobMap, Map<String, String> jobMap) {
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

    private Map<String, String> importDataSources(AppPackageContent content, AppPackageInitRequestDTO request, AppPackageInitResultDTO result) {
        Map<String, String> mapping = new LinkedHashMap<>();
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

            String newId = resolveBoundDataSource(request, key, item.oldId);
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

    private String resolveBoundDataSource(AppPackageInitRequestDTO request, String key, String oldId) {
        if (request == null || request.dataSourceMapping == null || request.dataSourceMapping.isEmpty()) {
            return null;
        }
        String mapped = request.dataSourceMapping.get(key);
        if (mapped == null) {
            mapped = request.dataSourceMapping.get(oldId);
        }
        if (mapped == null) {
            return null;
        }
        String targetId = mapped;
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

    private Map<String, String> importModelDirectories(AppPackageContent content, AppPackageInitResultDTO result) {
        Map<String, String> mapping = new LinkedHashMap<>();
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
                String parentId = directory.parentOldId == null ? null : mapping.get(directory.parentOldId);
                ModelDirectory entity = findModelDirectory(directory.name, parentId);
                if (entity == null) {
                    entity = new ModelDirectory();
                    entity.setName(directory.name == null ? "导入目录" : directory.name);
                    entity.setParentId(parentId);
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
                    entity.setParentId(null);
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

    private ModelDirectory findModelDirectory(String name, String parentId) {
        String directoryName = name == null ? "导入目录" : name;
        if (parentId == null) {
            return modelDirectoryRepository.findFirstByNameAndParentIdIsNull(directoryName).orElse(null);
        }
        return modelDirectoryRepository.findFirstByNameAndParentId(directoryName, parentId).orElse(null);
    }

    private Map<String, String> importMetricDirectories(AppPackageContent content, AppPackageInitResultDTO result) {
        Map<String, String> mapping = new LinkedHashMap<>();
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
                String parentId = directory.parentOldId == null ? null : mapping.get(directory.parentOldId);
                MetricDirectory entity = findMetricDirectory(directory.name, parentId);
                if (entity == null) {
                    entity = new MetricDirectory();
                    entity.setName(directory.name == null ? "导入目录" : directory.name);
                    entity.setParentId(parentId);
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
                    entity.setParentId(null);
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

    private MetricDirectory findMetricDirectory(String name, String parentId) {
        String directoryName = name == null ? "导入目录" : name;
        if (parentId == null) {
            return metricDirectoryRepository.findFirstByNameAndParentIdIsNull(directoryName).orElse(null);
        }
        return metricDirectoryRepository.findFirstByNameAndParentId(directoryName, parentId).orElse(null);
    }

    // ------------------------------------------------------------------ 模型与字段

    private void importModels(
        AppPackageContent content,
        String strategy,
        Map<String, String> dataSourceMap,
        Map<String, String> directoryMap,
        Map<String, String> modelMap,
        Map<String, String> modelFieldMap,
        Map<String, String> modelCodeRename,
        AppPackageInitResultDTO result
    ) {
        int created = 0;
        int skipped = 0;
        int overwritten = 0;
        for (AppPackageModel item : nonNull(content.getModels())) {
            if (item.oldId == null) {
                continue;
            }
            DataModel existing = item.code == null ? null : dataModelRepository.findFirstByCode(item.code).orElse(null);
            boolean overwrite = existing != null && STRATEGY_OVERWRITE.equals(strategy);
            DataModel entity;
            if (overwrite) {
                // 覆盖安装：沿用已存在模型与其字段，就地更新定义
                entity = existing;
                overwritten++;
            } else {
                if (existing != null) {
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
                entity = new DataModel();
                entity.setCreateTime(ZonedDateTime.now());
                created++;
            }
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
            entity.setUpdateTime(ZonedDateTime.now());
            entity = dataModelRepository.save(entity);
            modelMap.put(item.oldId, entity.getId());
        }
        result.counts.put("models", created);
        result.counts.put("modelsSkipped", skipped);
        result.counts.put("modelsOverwritten", overwritten);

        // 字段分两轮写入：第一轮按字段名 upsert（覆盖时保留原字段 ID），第二轮补上「关联维度字段」
        // （dimensionFieldId 依赖第一轮生成的字段 ID）
        List<String[]> pendingDimensionField = new ArrayList<>();
        int fieldCount = 0;
        int fieldRemoved = 0;
        for (AppPackageModel item : nonNull(content.getModels())) {
            String newModelId = item.oldId == null ? null : modelMap.get(item.oldId);
            if (newModelId == null) {
                continue;
            }
            Map<String, ModelField> existingFields = new LinkedHashMap<>();
            for (ModelField existing : modelFieldRepository.findByModelIdOrderBySortOrderAsc(newModelId)) {
                if (existing.getFieldName() != null) {
                    existingFields.put(existing.getFieldName(), existing);
                }
            }
            Set<String> packageFieldNames = new LinkedHashSet<>();
            for (AppPackageModel.AppPackageModelField field : nonNull(item.fields)) {
                if (field.fieldName != null) {
                    packageFieldNames.add(field.fieldName);
                }
                ModelField entity = field.fieldName == null ? null : existingFields.get(field.fieldName);
                boolean isNew = entity == null;
                if (isNew) {
                    entity = new ModelField();
                    entity.setModelId(newModelId);
                }
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
                if (isNew) {
                    entity.setCreateTime(ZonedDateTime.now());
                }
                entity.setUpdateTime(ZonedDateTime.now());
                entity = modelFieldRepository.save(entity);
                if (field.oldId != null) {
                    modelFieldMap.put(field.oldId, entity.getId());
                }
                if (field.dimensionFieldOldId != null) {
                    pendingDimensionField.add(new String[] { entity.getId(), field.dimensionFieldOldId });
                }
                fieldCount++;
            }
            // 覆盖安装：删除资产包中已移除的字段（新模型时 existingFields 为空，不会误删）
            for (ModelField removed : existingFields.values()) {
                if (!packageFieldNames.contains(removed.getFieldName())) {
                    modelFieldRepository.delete(removed);
                    fieldRemoved++;
                }
            }
        }
        for (String[] pair : pendingDimensionField) {
            String dimensionFieldId = modelFieldMap.get(pair[1]);
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
        result.counts.put("modelFieldsRemoved", fieldRemoved);
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
        Map<String, String> modelMap,
        Map<String, String> directoryMap,
        Map<String, String> modelCodeRename,
        Map<String, String> metricMap,
        AppPackageInitResultDTO result
    ) {
        List<AppPackageMetric> items = new ArrayList<>(nonNull(content.getMetrics()));

        // 预判编码冲突，先定稿每个指标的最终编码：后续公式改写、依赖排序都以最终编码为准
        Set<String> tenantCodes = new LinkedHashSet<>();
        Map<String, String> existingMetricIds = new LinkedHashMap<>();
        metricRepository.findAll().forEach(metric -> {
            tenantCodes.add(metric.getCode());
            existingMetricIds.put(metric.getCode(), metric.getId());
        });
        Set<String> plannedCodes = new LinkedHashSet<>(tenantCodes);
        Map<String, String> codeRename = new LinkedHashMap<>();
        Set<String> skippedIds = new LinkedHashSet<>();
        Set<String> overwriteCodes = new LinkedHashSet<>();
        for (AppPackageMetric item : items) {
            if (item.oldId == null || item.code == null) {
                continue;
            }
            if (!plannedCodes.add(item.code)) {
                if (STRATEGY_OVERWRITE.equals(strategy) && tenantCodes.contains(item.code) && overwriteCodes.add(item.code)) {
                    // 覆盖安装：沿用已存在指标的编码，稍后就地更新
                    continue;
                }
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
        int overwritten = 0;
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
                String existingId = STRATEGY_OVERWRITE.equals(strategy) ? existingMetricIds.get(dto.getCode()) : null;
                MetricDTO saved;
                if (existingId != null) {
                    saved = metricService
                        .update(existingId, dto)
                        .orElseThrow(() -> new IllegalArgumentException("指标不存在，无法覆盖：" + dto.getCode()));
                    overwritten++;
                } else {
                    saved = metricService.save(dto);
                    created++;
                }
                metricMap.put(item.oldId, saved.getId());
                if (saved.getCode() != null) {
                    availableCodes.add(saved.getCode());
                }
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
        result.counts.put("metricsOverwritten", overwritten);
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
    private String remapFilterConfig(JsonNode filterConfig, Map<String, String> modelMap, Map<String, String> modelCodeRename) {
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
        JsonNode remapped = AppPackageIdRemapper.remapIds(root.deepCopy(), Map.of("dimensionModelId", modelMap));
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
        Map<String, String> dataSourceMap,
        Map<String, String> modelMap,
        AppPackageInitRequestDTO request,
        Map<String, String> etlTaskMap,
        Map<String, String> jobMap,
        AppPackageInitResultDTO result
    ) {
        int created = 0;
        int overwritten = 0;
        for (AppPackageEtlTask item : nonNull(content.getEtlTasks())) {
            if (item.oldId == null) {
                continue;
            }
            ETLTask existing = item.taskCode == null ? null : etlTaskRepository.findFirstByTaskCode(item.taskCode).orElse(null);
            boolean overwrite = existing != null && STRATEGY_OVERWRITE.equals(strategy);
            if (existing != null && !overwrite) {
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
            dto.setDr(0);
            dto.setNodes(nonNull(item.nodes).stream().map(node -> toNodeDto(node, dataSourceMap, modelMap, result)).toList());
            dto.setEdges(nonNull(item.edges).stream().map(this::toEdgeDto).toList());
            ETLTaskDTO saved;
            boolean keepOnline = false;
            if (overwrite) {
                // 覆盖安装：就地更新任务与它对应的调度 Job，保留任务 ID 以维持编排引用
                dto.setId(existing.getId());
                dto.setJobId(existing.getJobId());
                dto.setCreateTime(existing.getCreateTime());
                dto.setTenantId(existing.getTenantId());
                keepOnline = TaskConstants.TASK_STATUS_ONLINE.equals(existing.getStatus());
                dto.setStatus(keepOnline ? TaskConstants.TASK_STATUS_ONLINE : IMPORTED_STATUS);
                saved = etlTaskService.update(dto);
                overwritten++;
            } else {
                dto.setStatus(IMPORTED_STATUS);
                saved = etlTaskService.save(dto);
                created++;
            }
            etlTaskMap.put(item.oldId, saved.getId());
            if (item.jobOldId != null && saved.getJobId() != null) {
                // 编排任务引用的是 Job ID，这里把 ETL 任务的 Job ID 也登记进映射表
                jobMap.put(item.jobOldId, saved.getJobId());
            }
            if (keepOnline || Boolean.TRUE.equals(request == null ? null : request.onlineJobs)) {
                etlTaskService.online(saved.getId());
            }
        }
        result.counts.put("etlTasks", created);
        result.counts.put("etlTasksOverwritten", overwritten);
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
        Map<String, String> dataSourceMap,
        Map<String, String> modelMap,
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
        Map<String, Map<String, String>> remap = new LinkedHashMap<>();
        remap.put("sourceId", dataSourceMap);
        remap.put("modelId", modelMap);
        JsonNode remapped = AppPackageIdRemapper.remapIds(raw.deepCopy(), remap);
        dto.setConfig(remapped == null ? null : remapped.toString());
        return dto;
    }

    private void warnUnmappedReferences(
        String nodeLabel,
        JsonNode config,
        Map<String, String> dataSourceMap,
        Map<String, String> modelMap,
        AppPackageInitResultDTO result
    ) {
        if (config == null || !config.isObject()) {
            return;
        }
        JsonNode sourceId = config.get("sourceId");
        if (sourceId != null && !sourceId.isContainerNode() && !dataSourceMap.containsKey(sourceId.asText())) {
            result.warnings.add("ETL 节点引用的数据源不在资产包内：" + nodeLabel + " -> dataSourceId=" + sourceId.asText());
        }
        JsonNode modelId = config.get("modelId");
        if (modelId != null && !modelId.isContainerNode() && !modelMap.containsKey(modelId.asText())) {
            result.warnings.add("ETL 节点引用的数据模型不在资产包内：" + nodeLabel + " -> modelId=" + modelId.asText());
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
        Map<String, String> dataSourceMap,
        AppPackageInitRequestDTO request,
        Map<String, String> jobMap,
        AppPackageInitResultDTO result
    ) {
        int created = 0;
        int overwritten = 0;
        String resolvedType = type == null ? null : type;
        for (AppPackageJob item : nonNull(items)) {
            if (item.oldId == null) {
                continue;
            }
            JsonNode context = item.jobContext == null ? null : item.jobContext.deepCopy();
            if (context != null && TaskConstants.TASK_TYPE_SQL.equals(type)) {
                if (context.isObject()) {
                    JsonNode dataSourceId = context.get("dataSourceId");
                    if (dataSourceId != null && !dataSourceId.isContainerNode() && !dataSourceMap.containsKey(dataSourceId.asText())) {
                        result.warnings.add("SQL 任务引用的数据源不在资产包内：" + item.jobName + " -> dataSourceId=" + dataSourceId.asText());
                    }
                }
                context = AppPackageIdRemapper.remapIds(context, Map.of("dataSourceId", dataSourceMap));
            }
            String jobType = resolvedType == null ? item.type : resolvedType;
            Job existing = item.jobName == null ? null : jobRepository.findFirstByJobName(item.jobName).orElse(null);
            Job job;
            boolean keepOnline = false;
            if (existing != null && STRATEGY_OVERWRITE.equals(strategy) && Objects.equals(jobType, existing.getType())) {
                // 覆盖安装：就地更新同名同类型任务，保留任务 ID 以维持编排引用
                job = existing;
                keepOnline = TaskConstants.TASK_STATUS_ONLINE.equals(existing.getStatus());
                overwritten++;
            } else {
                String jobName = resolveJobName(item.jobName, strategy, result);
                if (jobName == null) {
                    continue;
                }
                job = new Job();
                job.setJobName(jobName);
                job.setCreateTime(ZonedDateTime.now());
                created++;
            }
            job.setJobGroup(item.jobGroup == null ? "datafusion" : item.jobGroup);
            job.setType(jobType);
            job.setCron(item.cron);
            job.setJobContext(context == null ? null : context.toString());
            job.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
            job.setProject(item.project);
            job.setUpdateTime(ZonedDateTime.now());
            job = jobRepository.save(job);
            jobMap.put(item.oldId, job.getId());
            if (keepOnline || Boolean.TRUE.equals(request == null ? null : request.onlineJobs)) {
                jobService.online(job, false);
            }
        }
        result.counts.merge(jobCountKey(type), created, Integer::sum);
        result.counts.merge(jobCountKey(type) + "Overwritten", overwritten, Integer::sum);
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
        Map<String, String> jobMap,
        Map<String, String> dagJobMap,
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
        Map<String, String> jobMap,
        Map<String, String> dagJobMap,
        AppPackageInitResultDTO result
    ) {
        Job existing = item.jobName == null ? null : jobRepository.findFirstByJobName(item.jobName).orElse(null);
        Job job;
        String jobName;
        boolean keepOnline = false;
        if (existing != null && STRATEGY_OVERWRITE.equals(strategy) && TaskConstants.TASK_TYPE_DAG.equals(existing.getType())) {
            // 覆盖安装：就地更新同名编排任务，保留任务 ID 以维持编排引用
            job = existing;
            jobName = item.jobName;
            keepOnline = TaskConstants.TASK_STATUS_ONLINE.equals(existing.getStatus());
            result.counts.merge("dagJobsOverwritten", 1, Integer::sum);
        } else {
            jobName = resolveJobName(item.jobName, strategy, result);
            if (jobName == null) {
                return false;
            }
            job = new Job();
            job.setJobName(jobName);
            job.setCreateTime(ZonedDateTime.now());
        }
        JsonNode context = remapDagContext(item.jobContext, jobMap, result, jobName);
        job.setJobGroup(item.jobGroup == null ? "datafusion" : item.jobGroup);
        job.setType(TaskConstants.TASK_TYPE_DAG);
        job.setCron(item.cron);
        job.setJobContext(context == null ? null : context.toString());
        job.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        job.setProject(item.project);
        job.setUpdateTime(ZonedDateTime.now());
        job = jobRepository.save(job);
        jobMap.put(item.oldId, job.getId());
        dagJobMap.put(item.oldId, job.getId());
        if (keepOnline || online) {
            jobService.online(job, false);
        }
        return true;
    }

    private boolean isDagInPackage(AppPackageContent content, String jobOldId) {
        return nonNull(content.getDagJobs()).stream().anyMatch(item -> jobOldId.equals(item.oldId));
    }

    private Set<String> extractDagReferences(JsonNode context) {
        Set<String> ids = new LinkedHashSet<>();
        if (context == null || !context.isObject()) {
            return ids;
        }
        JsonNode jobs = context.get("jobs");
        if (jobs != null && jobs.isArray()) {
            for (JsonNode job : jobs) {
                String id = asText(job.get("id"));
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
    private JsonNode remapDagContext(JsonNode context, Map<String, String> jobMap, AppPackageInitResultDTO result, String dagName) {
        if (context == null || !context.isObject()) {
            return context;
        }
        ObjectNode root = ((ObjectNode) context).deepCopy();

        Set<String> keptOldIds = new LinkedHashSet<>();
        ArrayNode jobs = AppPackageJson.mapper().createArrayNode();
        JsonNode jobsNode = root.get("jobs");
        if (jobsNode != null && jobsNode.isArray()) {
            for (JsonNode job : jobsNode) {
                String oldId = asText(job.get("id"));
                String newId = oldId == null ? null : jobMap.get(oldId);
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
                String parentOld = asText(depend.get("parentJobCode"));
                String childOld = asText(depend.get("childJobCode"));
                String parentNew = parentOld == null ? null : jobMap.get(parentOld);
                String childNew = childOld == null ? null : jobMap.get(childOld);
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
                String oldId = asText(item.get("jobId"));
                String newId = oldId == null ? null : jobMap.get(oldId);
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

    private void importJobDepends(AppPackageContent content, Map<String, String> jobMap, AppPackageInitResultDTO result) {
        int created = 0;
        for (AppPackageJobDepend item : nonNull(content.getJobDepends())) {
            String parentNew = item.parentJobOldId == null ? null : jobMap.get(item.parentJobOldId);
            String childNew = item.childJobOldId == null ? null : jobMap.get(item.childJobOldId);
            if (parentNew == null || childNew == null) {
                result.warnings.add("任务依赖引用的任务不在资产包内，已忽略：" + item.parentJobOldId + " -> " + item.childJobOldId);
                continue;
            }
            JobDepend entity = new JobDepend();
            entity.setParentJobCode(parentNew);
            entity.setChildJobCode(childNew);
            entity.setJobCode(childNew);
            entity.setLastInterval(item.lastInterval == null ? 0L : item.lastInterval);
            entity.setCreateTime(ZonedDateTime.now());
            jobDependRepository.save(entity);
            created++;
        }
        result.counts.put("jobDepends", created);
    }

    // ------------------------------------------------------------------ 通用工具

    private static String asText(JsonNode node) {
        if (node == null || node.isNull() || node.isContainerNode()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> toStringKeyMap(Map<String, String> source) {
        return new LinkedHashMap<>(source);
    }

    private static String normalizeStrategy(AppPackageInitRequestDTO request) {
        String strategy = request == null ? null : request.conflictStrategy;
        if (strategy == null || strategy.isBlank()) {
            return STRATEGY_OVERWRITE;
        }
        return strategy.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
