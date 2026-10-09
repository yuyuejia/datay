package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.data.datafusion.service.dto.MetricQueryFieldDTO;
import com.data.datafusion.service.dto.MetricQueryTimeRangeDTO;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.MetricFilterConfig;
import com.data.datafusion.service.metric.MetricFilterOperator;
import com.data.datafusion.service.metric.ScopedDimension;
import com.data.metadata.util.DBUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公共指标查询服务：支持多指标，按各指标事实表共同存在的维度分组，
 * 业务限定仅支持维度过滤，并支持时间统计范围。
 */
@Service
public class MetricQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricQueryService.class);

    private static final Pattern FORMULA_REF_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /** 事实表别名，避免与维度表字段重名。 */
    private static final String FACT_ALIAS = "f";

    /** 层级维度类型。 */
    private static final String DIMENSION_KIND_HIERARCHY = "HIERARCHY";

    /** 时间维度类型，与层级维度一样支持按层级（时间粒度）分组。 */
    private static final String DIMENSION_KIND_TIME = "TIME";

    /** 时间维度层级字段的中文标签，用于元数据展示。 */
    private static final Map<String, String> TIME_LEVEL_LABELS = Map.of(
        "YEAR",
        "年",
        "QUARTER",
        "季",
        "MONTH",
        "月",
        "WEEK",
        "周",
        "DAY",
        "日"
    );

    /** 字段角色。 */
    private static final String ROLE_LEVEL_ID = "LEVEL_ID";
    private static final String ROLE_LEVEL_NAME = "LEVEL_NAME";

    /** 可用于时间分组的周期字段（按年/季/月/周/日等）。 */
    private static final Pattern PERIOD_FIELD_PATTERN = Pattern.compile(
        "(?i)^(year|quarter|month|week_of_year|week|day_of_week|day|full_date|date)(_id|_name)?$"
    );

    /** 事实表缺少受控维度时的策略：SKIP 跳过并告警，DENY 直接拒绝查询。 */
    @Value("${datay.security.data-scope.missing-dimension-policy:SKIP}")
    private String missingDimensionPolicy;

    /** 支持聚合 FILTER 子句的聚合函数（用于把指标业务限定下推到聚合上）。 */
    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
        "SUM",
        "COUNT",
        "AVG",
        "MIN",
        "MAX",
        "STDDEV",
        "STDDEV_POP",
        "STDDEV_SAMP",
        "VAR_POP",
        "VAR_SAMP",
        "VARIANCE",
        "MEDIAN",
        "PRODUCT",
        "STRING_AGG",
        "GROUP_CONCAT",
        "LIST",
        "ARRAY_AGG",
        "ANY_VALUE",
        "FIRST",
        "LAST",
        "BIT_AND",
        "BIT_OR",
        "BOOL_AND",
        "BOOL_OR",
        "APPROX_COUNT_DISTINCT"
    );

    private final MetricRepository metricRepository;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;
    private final ObjectMapper objectMapper;
    private final RoleDataScopeService roleDataScopeService;

    public MetricQueryService(
        MetricRepository metricRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService,
        ObjectMapper objectMapper,
        RoleDataScopeService roleDataScopeService
    ) {
        this.metricRepository = metricRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.objectMapper = objectMapper;
        this.roleDataScopeService = roleDataScopeService;
    }

    /**
     * 执行公共指标查询，返回 columns / rows / affectedRows / sql。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> query(MetricQueryDTO dto) throws SQLException {
        QueryContext context = prepare(dto);
        String dataSourceId = validateDataSource(context);
        DataSourceDTO dataSource = dataSourceService
            .findOne(dataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + dataSourceId));
        String sql = buildSql(context);
        Map<String, Object> result = new LinkedHashMap<>(dataSourceQueryService.executeQuery(dataSource, sql));
        result.put("columnLabels", buildColumnLabels(context));
        result.put("sql", sql);
        return result;
    }

    /**
     * 构建列标签：维度展示字段使用字段描述（为空回退字段名），指标使用指标名称（为空回退编码）。
     */
    private Map<String, String> buildColumnLabels(QueryContext context) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (DimSelection dimension : context.dimensions) {
            if (dimension.displayFields == null) {
                continue;
            }
            Map<String, String> descriptions = new LinkedHashMap<>();
            for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimension.dimensionModelId)) {
                descriptions.put(field.getFieldName(), field.getDescription());
            }
            for (String fieldName : dimension.displayFields) {
                String description = descriptions.get(fieldName);
                labels.put(fieldName, description != null && !description.isBlank() ? description : fieldName);
            }
        }
        for (Metric metric : context.metrics) {
            String name = metric.getName();
            labels.put(metric.getCode(), name != null && !name.isBlank() ? name : metric.getCode());
        }
        return labels;
    }

    /**
     * 仅生成查询 SQL，不执行。
     */
    @Transactional(readOnly = true)
    public String buildSqlFor(MetricQueryDTO dto) {
        return buildSql(prepare(dto));
    }

    /**
     * 返回查询元数据：指标信息、事实表、共同维度、时间字段。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> queryMeta(MetricQueryDTO dto) {
        QueryContext context = prepare(dto);

        List<Map<String, Object>> metrics = new ArrayList<>();
        for (Metric metric : context.metrics) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", metric.getName());
            item.put("code", metric.getCode());
            item.put("metricType", metric.getMetricType());
            metrics.add(item);
        }

        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (String dimensionModelId : context.commonDimensionModelIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            DataModel dimensionModel = dataModelRepository.findById(dimensionModelId).orElse(null);
            item.put("dimensionModelCode", dimensionModel == null ? null : dimensionModel.getCode());
            item.put("dimensionModelName", dimensionModel == null ? null : dimensionModel.getName());
            item.put("factFieldName", context.single ? context.factDimensionMaps.get(context.factModels.get(0).getId()).get(dimensionModelId) : null);
            boolean hierarchy = dimensionModel != null && isHierarchyLike(dimensionModel.getDimensionKind());
            item.put("isHierarchy", hierarchy);
            item.put("dimensionKind", dimensionModel == null ? null : dimensionModel.getDimensionKind());
            boolean timeKind = dimensionModel != null && DIMENSION_KIND_TIME.equalsIgnoreCase(dimensionModel.getDimensionKind());
            item.put("levelCount", hierarchy ? resolveLevelCount(dimensionModel) : null);
            item.put("isTimeDimension", isTimeDimension(context, dimensionModelId) || timeKind);
            // 维度的默认显示字段：供看板设计助手确定图表 x 轴字段
            if (dimensionModel != null) {
                if (dimensionModel.getDisplayFieldName() != null && !dimensionModel.getDisplayFieldName().isBlank()) {
                    item.put("displayFieldName", dimensionModel.getDisplayFieldName());
                }
                item.put("recommendedDisplayField", resolveDisplayField(dimensionModel));
            }
            if (hierarchy) {
                int levelCount = resolveLevelCount(dimensionModel);
                List<Map<String, Object>> levels = new ArrayList<>();
                for (int level = 1; level <= levelCount; level++) {
                    Map<String, Object> levelItem = new LinkedHashMap<>();
                    levelItem.put("levelIndex", level);
                    levelItem.put("idField", levelFieldName(dimensionModelId, level, ROLE_LEVEL_ID));
                    levelItem.put("nameField", levelFieldName(dimensionModelId, level, ROLE_LEVEL_NAME));
                    if (timeKind) {
                        String granularity = timeGranularityCode(dimensionModel, level);
                        levelItem.put("granularity", granularity);
                        levelItem.put("label", TIME_LEVEL_LABELS.getOrDefault(granularity, granularity));
                    }
                    levels.add(levelItem);
                }
                item.put("levels", levels);
            }
            List<Map<String, Object>> dimensionFields = new ArrayList<>();
            List<String> periodFields = new ArrayList<>();
            for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimensionModelId)) {
                Map<String, Object> fieldItem = new LinkedHashMap<>();
                fieldItem.put("fieldName", field.getFieldName());
                fieldItem.put("fieldType", field.getFieldType());
                if (field.getDescription() != null && !field.getDescription().isBlank()) {
                    fieldItem.put("description", field.getDescription());
                }
                dimensionFields.add(fieldItem);
                if (field.getFieldName() != null && PERIOD_FIELD_PATTERN.matcher(field.getFieldName()).matches()) {
                    periodFields.add(field.getFieldName());
                }
            }
            item.put("fields", dimensionFields);
            if (!periodFields.isEmpty()) {
                item.put("periodFields", periodFields);
            }
            dimensions.add(item);
        }

        List<Map<String, Object>> timeFields = new ArrayList<>();
        Set<String> seenTimeFields = new LinkedHashSet<>();
        for (DataModel factModel : context.factModels) {
            String timeField = factModel.getTimeFieldName();
            if (timeField != null && !timeField.isBlank() && seenTimeFields.add(timeField)) {
                String fieldType = context.factFields
                    .getOrDefault(factModel.getId(), List.of())
                    .stream()
                    .filter(f -> timeField.equals(f.getFieldName()))
                    .findFirst()
                    .map(ModelField::getFieldType)
                    .orElse(null);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fieldName", timeField);
                item.put("fieldType", fieldType);
                item.put("factTable", physicalTableName(factModel));
                timeFields.add(item);
            }
        }

        List<String> factTableNames = new ArrayList<>();
        context.factModels.forEach(fact -> factTableNames.add(physicalTableName(fact)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metrics", metrics);
        result.put("factTables", factTableNames);
        result.put("dimensions", dimensions);
        result.put("timeFields", timeFields);
        return result;
    }

    /**
     * 查询上下文：解析指标、事实表、维度连接信息、公共维度与时间字段。
     */
    private QueryContext prepare(MetricQueryDTO dto) {
        QueryContext context = new QueryContext();
        context.metrics = resolveMetrics(dto);
        context.byCode = loadByCode();

        LinkedHashMap<String, DataModel> factMap = new LinkedHashMap<>();
        for (Metric metric : context.metrics) {
            String factModelId = resolveFactModelId(metric, context.byCode);
            context.metricFactModel.put(metric.getId(), factModelId);
            DataModel factModel = dataModelRepository
                .findById(factModelId)
                .orElseThrow(() -> new IllegalArgumentException("事实表不存在：" + factModelId));
            factMap.put(factModelId, factModel);
        }
        context.factModels = new ArrayList<>(factMap.values());
        context.single = context.factModels.size() == 1;

        for (DataModel factModel : context.factModels) {
            context.factFields.put(factModel.getId(), modelFieldRepository.findByModelIdOrderBySortOrderAsc(factModel.getId()));
            context.factDimensionMaps.put(factModel.getId(), dimensionJoinMap(factModel.getId()));
        }

        context.commonDimensionModelIds = commonDimensions(context.factModels);

        context.dimensions = new ArrayList<>();
        for (MetricQueryFieldDTO dimension : dto.getDimensions() == null ? List.<MetricQueryFieldDTO>of() : dto.getDimensions()) {
            String dimensionModelCode = dimension.getDimensionModelCode();
            if (dimensionModelCode == null || dimensionModelCode.isBlank()) {
                continue;
            }
            DataModel dimensionModel = dataModelRepository
                .findFirstByCode(dimensionModelCode)
                .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + dimensionModelCode));
            String dimensionModelId = dimensionModel.getId();
            if (!context.commonDimensionModelIds.contains(dimensionModelId)) {
                throw new IllegalArgumentException("维度不在所选指标事实表的共同维度中：" + dimensionModelCode);
            }
            boolean hierarchy = isHierarchyLike(dimensionModel.getDimensionKind());

            DimSelection selection = new DimSelection();
            selection.dimensionModelId = dimensionModelId;

            if (hierarchy) {
                int levelCount = resolveLevelCount(dimensionModel);
                int level = dimension.getLevelIndex() == null ? levelCount : dimension.getLevelIndex();
                if (level < 1 || level > levelCount) {
                    throw new IllegalArgumentException("层级维度层级序号超出范围：" + level);
                }
                selection.groupKeyFields = List.of(levelFieldName(dimensionModelId, level, ROLE_LEVEL_ID));
                selection.displayFields = List.of(levelFieldName(dimensionModelId, level, ROLE_LEVEL_NAME));
            } else {
                List<String> displayFields = new ArrayList<>();
                if (dimension.getDimensionFieldNames() != null) {
                    for (String field : dimension.getDimensionFieldNames()) {
                        if (field != null && !field.isBlank()) {
                            displayFields.add(field);
                        }
                    }
                }
                if (displayFields.isEmpty() && dimension.getDimensionFieldName() != null && !dimension.getDimensionFieldName().isBlank()) {
                    displayFields.add(dimension.getDimensionFieldName());
                }
                List<String> primaryKeys = primaryKeyFields(dimensionModelId);
                if (displayFields.isEmpty()) {
                    // 未选择显示字段时，默认按维度主键汇总并显示维度主键
                    if (primaryKeys.isEmpty()) {
                        continue;
                    }
                    displayFields.addAll(primaryKeys);
                    selection.groupKeyFields = primaryKeys;
                } else if (context.single) {
                    // 单事实表且指定了显示字段时，按显示字段汇总（维度主键仅用于关联，不参与分组），
                    // 例如按日期维度的 year+month 汇总得到月度粒度，而非按 date_sk 的日粒度
                    selection.groupKeyFields = new ArrayList<>(displayFields);
                } else {
                    // 多事实表需要以维度主键作为跨事实表的关联键
                    selection.groupKeyFields = primaryKeys;
                }
                selection.displayFields = displayFields;
            }
            context.dimensions.add(selection);
        }

        context.conditions = new ArrayList<>();
        MetricFilterConfig config = parseFilterConfig(dto.getFilterConfig());
        if (config != null && !config.isEmpty()) {
            for (MetricFilterCondition condition : config.getConditions()) {
                if (!MetricFilterCondition.TYPE_DIMENSION.equals(condition.getType())) {
                    throw new IllegalArgumentException("业务限定仅支持按维度过滤");
                }
                String dimensionModelId = condition.getDimensionModelId();
                String dimensionModelCode = condition.getDimensionModelCode();
                if (dimensionModelCode != null && !dimensionModelCode.isBlank()) {
                    dimensionModelId = dataModelRepository
                        .findFirstByCode(dimensionModelCode)
                        .orElseThrow(() -> new IllegalArgumentException("业务限定维度模型不存在：" + dimensionModelCode))
                        .getId();
                    condition.setDimensionModelId(dimensionModelId);
                }
                if (dimensionModelId == null || condition.getDimensionFieldName() == null || condition.getDimensionFieldName().isBlank()) {
                    continue;
                }
                if (!context.commonDimensionModelIds.contains(dimensionModelId)) {
                    throw new IllegalArgumentException("业务限定维度不在所选指标事实表的共同维度中：" + dimensionModelCode);
                }
                context.conditions.add(condition);
            }
        }

        // 基于 RBAC 的维度成员范围：作为强制过滤条件，用户业务限定无法绕过
        context.dataScopes = roleDataScopeService.resolveEffectiveScopes();

        context.timeRange = dto.getTimeRange();
        return context;
    }

    private String validateDataSource(QueryContext context) {
        String dataSourceId = null;
        for (DataModel factModel : context.factModels) {
            if (factModel.getDataSourceId() == null) {
                throw new IllegalArgumentException("事实表未绑定数据源，无法查询：" + factModel.getName());
            }
            if (dataSourceId == null) {
                dataSourceId = factModel.getDataSourceId();
            } else if (!dataSourceId.equals(factModel.getDataSourceId())) {
                throw new IllegalArgumentException("所选指标的事实表来自不同数据源，暂不支持联合查询");
            }
        }
        return dataSourceId;
    }

    private String buildSql(QueryContext context) {
        if (context.single) {
            DataModel factModel = context.factModels.get(0);
            List<Metric> metrics = new ArrayList<>(context.metrics);
            return buildSingleFactSql(context, factModel, metrics);
        }
        return buildMultiFactSql(context);
    }

    private String buildSingleFactSql(QueryContext context, DataModel factModel, List<Metric> metrics) {
        StringBuilder joins = new StringBuilder();
        Map<String, String> dimensionAlias = new HashMap<>();
        Map<String, String> dimensionJoin = context.factDimensionMaps.get(factModel.getId());
        List<String> selects = new ArrayList<>();
        List<String> groupBys = new ArrayList<>();

        for (DimSelection dimension : context.dimensions) {
            String alias = ensureDimensionJoin(
                dimensionJoin.get(dimension.dimensionModelId),
                dimension.dimensionModelId,
                dimensionAlias,
                joins
            );
            for (String groupKey : dimension.groupKeyFields) {
                addGroup(groupBys, alias + "." + groupKey);
            }
            for (String field : dimension.displayFields) {
                String column = alias + "." + field;
                selects.add(column + " AS " + field);
                addGroup(groupBys, column);
            }
        }
        for (Metric metric : metrics) {
            selects.add(
                metricExpression(metric.getCode(), context.byCode, new HashSet<>(), filterPredicateProvider(factModel, dimensionJoin, dimensionAlias, joins)) +
                " AS " +
                metric.getCode()
            );
        }

        String where = buildWhere(context, factModel, dimensionJoin, dimensionAlias, joins);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selects));
        sql.append("\nFROM ").append(physicalTableName(factModel)).append(" ").append(FACT_ALIAS);
        sql.append(joins);
        if (!where.isEmpty()) {
            sql.append("\nWHERE ").append(where);
        }
        if (!groupBys.isEmpty()) {
            sql.append("\nGROUP BY ").append(String.join(", ", groupBys));
        }
        LOG.debug("Metric query SQL: {}", sql);
        return sql.toString();
    }

    private String buildMultiFactSql(QueryContext context) {
        List<String> subqueries = new ArrayList<>();
        List<List<Metric>> metricsByFact = new ArrayList<>();
        for (DataModel factModel : context.factModels) {
            List<Metric> metrics = context.metrics
                .stream()
                .filter(m -> factModel.getId().equals(context.metricFactModel.get(m.getId())))
                .collect(java.util.stream.Collectors.toList());
            metricsByFact.add(metrics);
            subqueries.add(buildFactSubquery(context, factModel, metrics));
        }

        List<String> finalSelects = new ArrayList<>();
        for (int i = 0; i < context.dimensions.size(); i++) {
            DimSelection dimension = context.dimensions.get(i);
            for (String field : dimension.displayFields) {
                finalSelects.add(coalesceDimField(i, field, context.factModels.size()) + " AS " + field);
            }
        }
        for (int i = 0; i < context.factModels.size(); i++) {
            for (Metric metric : metricsByFact.get(i)) {
                finalSelects.add("t" + i + "." + metric.getCode());
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", finalSelects));
        sql.append("\nFROM (").append(subqueries.get(0)).append(") t0");
        for (int i = 1; i < subqueries.size(); i++) {
            sql.append("\nFULL OUTER JOIN (").append(subqueries.get(i)).append(") t").append(i).append(" ON ").append(joinCondition(i, context.dimensions.size()));
        }
        LOG.debug("Metric query SQL: {}", sql);
        return sql.toString();
    }

    private String buildFactSubquery(QueryContext context, DataModel factModel, List<Metric> metrics) {
        StringBuilder joins = new StringBuilder();
        Map<String, String> dimensionAlias = new HashMap<>();
        Map<String, String> dimensionJoin = context.factDimensionMaps.get(factModel.getId());
        List<String> selects = new ArrayList<>();
        List<String> groupBys = new ArrayList<>();

        for (int i = 0; i < context.dimensions.size(); i++) {
            DimSelection dimension = context.dimensions.get(i);
            String alias = ensureDimensionJoin(
                dimensionJoin.get(dimension.dimensionModelId),
                dimension.dimensionModelId,
                dimensionAlias,
                joins
            );
            List<String> groupKeys = dimension.groupKeyFields.isEmpty() ? new ArrayList<>() : new ArrayList<>(dimension.groupKeyFields);
            if (groupKeys.isEmpty()) {
                String joinField = dimensionJoin.get(dimension.dimensionModelId);
                if (joinField != null) {
                    groupKeys.add(joinField);
                }
            }
            for (String groupKey : groupKeys) {
                addGroup(groupBys, alias + "." + groupKey);
            }
            if (!groupKeys.isEmpty()) {
                selects.add(alias + "." + groupKeys.get(0) + " AS key_" + i);
            }
            for (String field : dimension.displayFields) {
                String column = alias + "." + field;
                selects.add(column + " AS dim_" + i + "_" + field);
                addGroup(groupBys, column);
            }
        }
        for (Metric metric : metrics) {
            selects.add(
                metricExpression(metric.getCode(), context.byCode, new HashSet<>(), filterPredicateProvider(factModel, dimensionJoin, dimensionAlias, joins)) +
                " AS " +
                metric.getCode()
            );
        }

        String where = buildWhere(context, factModel, dimensionJoin, dimensionAlias, joins);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selects));
        sql.append("\nFROM ").append(physicalTableName(factModel)).append(" ").append(FACT_ALIAS);
        sql.append(joins);
        if (!where.isEmpty()) {
            sql.append("\nWHERE ").append(where);
        }
        if (!groupBys.isEmpty()) {
            sql.append("\nGROUP BY ").append(String.join(", ", groupBys));
        }
        return sql.toString();
    }

    private String buildWhere(
        QueryContext context,
        DataModel factModel,
        Map<String, String> dimensionJoin,
        Map<String, String> dimensionAlias,
        StringBuilder joins
    ) {
        List<String> parts = new ArrayList<>();
        String scopePredicate = buildScopePredicate(context, factModel, dimensionJoin, dimensionAlias, joins);
        if (!scopePredicate.isEmpty()) {
            parts.add("(" + scopePredicate + ")");
        }
        String conditionPredicate = buildConditionPredicate(context, dimensionJoin, dimensionAlias, joins);
        if (!conditionPredicate.isEmpty()) {
            parts.add("(" + conditionPredicate + ")");
        }
        String timePredicate = buildTimePredicate(context, factModel);
        if (!timePredicate.isEmpty()) {
            parts.add("(" + timePredicate + ")");
        }
        return String.join(" AND ", parts);
    }

    /**
     * 构建当前用户角色的维度成员范围条件：同一维度内多条条件 OR，跨维度 AND。
     * <p>
     * 该条件为用户业务限定的强制前置条件，无法被查询入参绕过。
     */
    private String buildScopePredicate(
        QueryContext context,
        DataModel factModel,
        Map<String, String> dimensionJoin,
        Map<String, String> dimensionAlias,
        StringBuilder joins
    ) {
        if (context.dataScopes == null || context.dataScopes.isEmpty()) {
            return "";
        }
        List<String> groupPredicates = new ArrayList<>();
        for (ScopedDimension scope : context.dataScopes) {
            String dimensionModelId = scope.getDimensionModelId();
            String factFieldName = dimensionJoin.get(dimensionModelId);
            if (factFieldName == null) {
                if ("DENY".equalsIgnoreCase(missingDimensionPolicy)) {
                    throw new IllegalArgumentException(
                        "当前指标事实表缺少受控维度，无法应用数据权限：" + physicalTableName(factModel) + " 缺少维度 " + dimensionModelId
                    );
                }
                LOG.warn(
                    "数据权限维度未与事实表关联，已按 {} 策略跳过：fact={} dimensionModelId={}",
                    missingDimensionPolicy,
                    physicalTableName(factModel),
                    dimensionModelId
                );
                continue;
            }
            String alias = ensureDimensionJoin(factFieldName, dimensionModelId, dimensionAlias, joins);
            List<String> inner = new ArrayList<>();
            for (MetricFilterCondition condition : scope.getConditions()) {
                // 层级维度按层级字段过滤时，自然包含该层级下的所有下级成员，无需额外展开
                String column = alias + "." + condition.getDimensionFieldName();
                String predicate = buildPredicate(condition, column);
                if (predicate != null) {
                    inner.add("(" + predicate + ")");
                }
            }
            if (!inner.isEmpty()) {
                groupPredicates.add("(" + String.join(" OR ", inner) + ")");
            }
        }
        return String.join(" AND ", groupPredicates);
    }

    private String buildConditionPredicate(
        QueryContext context,
        Map<String, String> dimensionJoin,
        Map<String, String> dimensionAlias,
        StringBuilder joins
    ) {
        StringBuilder predicate = new StringBuilder();
        for (MetricFilterCondition condition : context.conditions) {
            String alias = ensureDimensionJoin(
                dimensionJoin.get(condition.getDimensionModelId()),
                condition.getDimensionModelId(),
                dimensionAlias,
                joins
            );
            String column = alias + "." + condition.getDimensionFieldName();
            String item = buildPredicate(condition, column);
            if (item != null) {
                if (predicate.length() == 0) {
                    predicate.append("(").append(item).append(")");
                } else {
                    String logic = "OR".equalsIgnoreCase(condition.getLogic()) ? " OR " : " AND ";
                    predicate.append(logic).append("(").append(item).append(")");
                }
            }
        }
        return predicate.toString();
    }

    private String buildTimePredicate(QueryContext context, DataModel factModel) {
        MetricQueryTimeRangeDTO timeRange = context.timeRange;
        boolean hasStart = timeRange != null && timeRange.getStart() != null && !timeRange.getStart().isBlank();
        boolean hasEnd = timeRange != null && timeRange.getEnd() != null && !timeRange.getEnd().isBlank();
        if (!hasStart && !hasEnd) {
            return "";
        }
        // 时间过滤字段由事实表模型配置的「时间周期字段」决定；未配置时忽略时间过滤
        String timeField = factModel.getTimeFieldName();
        if (timeField == null || timeField.isBlank()) {
            return "";
        }
        ModelField timeFieldMeta = context.factFields
            .getOrDefault(factModel.getId(), List.of())
            .stream()
            .filter(f -> timeField.equals(f.getFieldName()))
            .findFirst()
            .orElse(null);
        if (timeFieldMeta == null) {
            return "";
        }
        String column = FACT_ALIAS + "." + timeField;
        String start = normalizeTimeValue(timeRange.getStart(), timeFieldMeta.getFieldType());
        String end = normalizeTimeValue(timeRange.getEnd(), timeFieldMeta.getFieldType());
        if (hasStart && hasEnd) {
            return column + " BETWEEN " + quote(start) + " AND " + quote(end);
        }
        if (hasStart) {
            return column + " >= " + quote(start);
        }
        return column + " <= " + quote(end);
    }

    private String coalesceDimField(int dimIndex, String field, int factCount) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < factCount; i++) {
            parts.add("t" + i + ".dim_" + dimIndex + "_" + field);
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return "COALESCE(" + String.join(", ", parts) + ")";
    }

    private void addGroup(List<String> groups, String expression) {
        if (!groups.contains(expression)) {
            groups.add(expression);
        }
    }

    private List<String> primaryKeyFields(String dimensionModelId) {
        List<String> keys = new ArrayList<>();
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimensionModelId)) {
            if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                keys.add(field.getFieldName());
            }
        }
        return keys;
    }

    /**
     * 计算维度的推荐显示字段（仅用于元数据展示，不影响查询）：优先维度配置的默认显示字段，
     * 层级维度取末级名称字段，其余优先业务名称字段（{@code *_name} / {@code name}）。
     */
    private String resolveDisplayField(DataModel dimensionModel) {
        String dimensionModelId = dimensionModel.getId();
        List<ModelField> fields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimensionModelId);
        String configured = dimensionModel.getDisplayFieldName();
        if (configured != null && !configured.isBlank() && fields.stream().anyMatch(field -> configured.equals(field.getFieldName()))) {
            return configured;
        }
        if (isHierarchyLike(dimensionModel.getDimensionKind())) {
            int levelCount = resolveLevelCount(dimensionModel);
            if (levelCount > 0) {
                return levelFieldName(dimensionModelId, levelCount, ROLE_LEVEL_NAME);
            }
        }
        for (ModelField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsPrimaryKey()) && isNameField(field.getFieldName())) {
                return field.getFieldName();
            }
        }
        for (ModelField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsPrimaryKey()) && isTextType(field.getFieldType())) {
                return field.getFieldName();
            }
        }
        return null;
    }

    private static boolean isNameField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return lower.equals("name") || lower.endsWith("_name");
    }

    private static boolean isTextType(String fieldType) {
        if (fieldType == null) {
            return false;
        }
        String upper = fieldType.toUpperCase();
        return upper.contains("CHAR") || upper.contains("TEXT") || upper.contains("STRING");
    }

    private String levelFieldName(String dimensionModelId, int level, String role) {
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimensionModelId)) {
            if (role.equalsIgnoreCase(field.getFieldRole()) && field.getLevelIndex() != null && field.getLevelIndex() == level) {
                return field.getFieldName();
            }
        }
        return ROLE_LEVEL_ID.equals(role) ? "level" + level + "_id" : "level" + level + "_name";
    }

    private String joinCondition(int factIndex, int dimCount) {
        if (dimCount == 0) {
            return "1=1";
        }
        List<String> parts = new ArrayList<>();
        for (int d = 0; d < dimCount; d++) {
            List<String> left = new ArrayList<>();
            for (int i = 0; i < factIndex; i++) {
                left.add("t" + i + ".key_" + d);
            }
            String leftExpr = left.size() == 1 ? left.get(0) : "COALESCE(" + String.join(", ", left) + ")";
            parts.add(leftExpr + " = t" + factIndex + ".key_" + d);
        }
        return String.join(" AND ", parts);
    }

    private List<Metric> resolveMetrics(MetricQueryDTO dto) {
        List<String> codes = new ArrayList<>();
        if (dto.getMetricCodes() != null) {
            codes.addAll(dto.getMetricCodes());
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("请选择要查询的指标");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(codes);
        List<Metric> metrics = new ArrayList<>();
        for (String code : unique) {
            if (code == null || code.isBlank()) {
                continue;
            }
            metrics.add(metricRepository.findByCode(code).orElseThrow(() -> new IllegalArgumentException("指标不存在：" + code)));
        }
        if (metrics.isEmpty()) {
            throw new IllegalArgumentException("请选择要查询的指标");
        }
        return metrics;
    }

    private Map<String, Metric> loadByCode() {
        Map<String, Metric> byCode = new HashMap<>();
        for (Metric metric : metricRepository.findAll()) {
            byCode.put(metric.getCode(), metric);
        }
        return byCode;
    }

    private Map<String, String> dimensionJoinMap(String factModelId) {
        Map<String, String> map = new HashMap<>();
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(factModelId)) {
            if (field.getDimensionModelId() != null) {
                map.putIfAbsent(field.getDimensionModelId(), field.getFieldName());
            }
        }
        return map;
    }

    /** 层级维度与时间维度均支持按层级分组。 */
    private static boolean isHierarchyLike(String dimensionKind) {
        return DIMENSION_KIND_HIERARCHY.equalsIgnoreCase(dimensionKind) || DIMENSION_KIND_TIME.equalsIgnoreCase(dimensionKind);
    }

    private int resolveLevelCount(DataModel dimensionModel) {
        if (dimensionModel == null) {
            return 0;
        }
        if (DIMENSION_KIND_TIME.equalsIgnoreCase(dimensionModel.getDimensionKind())) {
            String levels = dimensionModel.getTimeLevels();
            if (levels == null || levels.isBlank()) {
                return 0;
            }
            int count = 0;
            for (String token : levels.split(",")) {
                if (!token.isBlank()) {
                    count++;
                }
            }
            return count;
        }
        return dimensionModel.getLevelCount() == null ? 0 : dimensionModel.getLevelCount();
    }

    private String timeGranularityCode(DataModel dimensionModel, int level) {
        String levels = dimensionModel.getTimeLevels();
        if (levels == null || levels.isBlank()) {
            return null;
        }
        int index = 0;
        for (String token : levels.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            index++;
            if (index == level) {
                return value.toUpperCase();
            }
        }
        return null;
    }

    /**
     * 判断维度是否为事实表时间字段所关联的日期/时间维度（用于按年/月/周等时间分组）。
     */
    private boolean isTimeDimension(QueryContext context, String dimensionModelId) {
        for (DataModel factModel : context.factModels) {
            String timeField = factModel.getTimeFieldName();
            if (timeField == null || timeField.isBlank()) {
                continue;
            }
            Map<String, String> joinMap = context.factDimensionMaps.get(factModel.getId());
            String joinField = joinMap == null ? null : joinMap.get(dimensionModelId);
            if (timeField.equalsIgnoreCase(joinField)) {
                return true;
            }
        }
        return false;
    }

    private List<String> commonDimensions(List<DataModel> factModels) {
        if (factModels.isEmpty()) {
            return List.of();
        }
        List<String> common = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(factModels.get(0).getId())) {
            String dimensionModelId = field.getDimensionModelId();
            if (dimensionModelId == null || !seen.add(dimensionModelId)) {
                continue;
            }
            boolean existsInAll = true;
            for (int i = 1; i < factModels.size(); i++) {
                if (!dimensionJoinMap(factModels.get(i).getId()).containsKey(dimensionModelId)) {
                    existsInAll = false;
                    break;
                }
            }
            if (existsInAll) {
                common.add(dimensionModelId);
            }
        }
        return common;
    }

    /**
     * 展开指标表达式。
     *
     * <p>{@code filterPredicateProvider} 用于把「指标级业务限定」下推到原子指标的聚合上
     * （见 {@link #applyAggregateFilter}）：原子指标在外层包一层 {@code FILTER (WHERE ...)}，
     * 衍生指标由各个被引用的原子指标各自带上限定，从而保证「销售额」与「有效销售额」口径互不污染。
     */
    private String metricExpression(String code, Map<String, Metric> byCode, Set<String> visiting, Function<Metric, String> filterPredicateProvider) {
        Metric metric = byCode.get(code);
        if (metric == null) {
            throw new IllegalArgumentException("指标不存在：" + code);
        }
        if (!visiting.add(code)) {
            throw new IllegalArgumentException("指标公式存在循环引用：" + code);
        }
        String expression;
        if (Metric.TYPE_ATOMIC.equals(metric.getMetricType())) {
            String formula = metric.getFormula() == null || metric.getFormula().isBlank() ? "1" : metric.getFormula().trim();
            String predicate = filterPredicateProvider == null ? null : filterPredicateProvider.apply(metric);
            expression = predicate == null || predicate.isBlank() ? "(" + formula + ")" : applyAggregateFilter(formula, predicate);
        } else {
            String formula = metric.getFormula() == null ? "" : metric.getFormula();
            Matcher matcher = FORMULA_REF_PATTERN.matcher(formula);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String refExpr = metricExpression(matcher.group(1), byCode, visiting, filterPredicateProvider);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(refExpr));
            }
            matcher.appendTail(sb);
            expression = "(" + sb + ")";
        }
        visiting.remove(code);
        return expression;
    }

    /**
     * 指标级业务限定的谓词提供器：把指标的 {@code filterConfig} 翻译成聚合 FILTER 子句里的谓词。
     *
     * <p>业务限定只在目标库支持聚合 {@code FILTER} 子句时下推，其它数据库保持原有行为并告警，
     * 避免生成对方无法解析的 SQL。
     */
    private Function<Metric, String> filterPredicateProvider(
        DataModel factModel,
        Map<String, String> dimensionJoin,
        Map<String, String> dimensionAlias,
        StringBuilder joins
    ) {
        boolean supported = supportsAggregateFilter(factModel);
        return metric -> {
            if (metric.getFilterConfig() == null || metric.getFilterConfig().isBlank()) {
                return null;
            }
            if (!supported) {
                LOG.warn(
                    "数据源不支持聚合 FILTER，指标业务限定未生效：metric={}, fact={}",
                    metric.getCode(),
                    physicalTableName(factModel)
                );
                return null;
            }
            return metricFilterPredicate(metric, factModel, dimensionJoin, dimensionAlias, joins);
        };
    }

    /** 目标库是否支持 {@code 聚合函数 FILTER (WHERE ...)}。 */
    private boolean supportsAggregateFilter(DataModel factModel) {
        if (factModel.getDataSourceId() == null) {
            return false;
        }
        return Optional
            .ofNullable(dataSourceService.findOne(factModel.getDataSourceId()).orElse(null))
            .map(DataSourceDTO::getUrl)
            .filter(url -> url != null && !url.isBlank())
            .map(url -> {
                String type = DBUtils.getDBType(url);
                return "DUCKDB".equalsIgnoreCase(type) || "DUCKLAKE".equalsIgnoreCase(type) || "POSTGRESQL".equalsIgnoreCase(type);
            })
            .orElse(false);
    }

    /**
     * 把指标业务限定翻译成谓词：维度条件自动 LEFT JOIN 维度表并在维度字段上过滤，
     * 其余条件（事实表字段 / 事实表时间字段）直接作用在事实表别名上。
     */
    private String metricFilterPredicate(
        Metric metric,
        DataModel factModel,
        Map<String, String> dimensionJoin,
        Map<String, String> dimensionAlias,
        StringBuilder joins
    ) {
        MetricFilterConfig config = parseFilterConfig(metric.getFilterConfig());
        if (config == null || config.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (MetricFilterCondition condition : config.getConditions()) {
            String column;
            boolean dimensionScoped =
                MetricFilterCondition.TYPE_DIMENSION.equals(condition.getType()) ||
                (MetricFilterCondition.TYPE_TIME.equals(condition.getType()) &&
                    condition.getDimensionModelId() != null &&
                    condition.getDimensionFieldName() != null &&
                    !condition.getDimensionFieldName().isBlank());
            if (dimensionScoped) {
                String dimensionModelId = condition.getDimensionModelId();
                if (dimensionModelId == null) {
                    throw new IllegalArgumentException("指标业务限定的维度条件缺少关联维度：" + metric.getCode());
                }
                String alias = ensureDimensionJoin(
                    dimensionJoin == null ? null : dimensionJoin.get(dimensionModelId),
                    dimensionModelId,
                    dimensionAlias,
                    joins
                );
                column = alias + "." + condition.getDimensionFieldName();
            } else {
                if (condition.getFactFieldName() == null || condition.getFactFieldName().isBlank()) {
                    throw new IllegalArgumentException("指标业务限定缺少事实表字段：" + metric.getCode());
                }
                column = FACT_ALIAS + "." + condition.getFactFieldName();
            }
            String predicate = buildPredicate(condition, column);
            if (predicate == null) {
                continue;
            }
            String logic = parts.isEmpty() ? "" : " " + ("OR".equalsIgnoreCase(condition.getLogic()) ? "OR" : "AND") + " ";
            parts.add(logic + "(" + predicate + ")");
        }
        if (parts.isEmpty()) {
            return null;
        }
        LOG.debug("Metric {} business filter applied: {}", metric.getCode(), String.join("", parts));
        return String.join("", parts);
    }

    /**
     * 给公式里的每个聚合函数补上 {@code FILTER (WHERE predicate)}。
     *
     * <p>示例：{@code SUM(order_amount) - SUM(cost_amount)} + {@code order_status = 'PAID'}
     * → {@code (SUM(order_amount) FILTER (WHERE order_status = 'PAID') - SUM(cost_amount) FILTER (WHERE order_status = 'PAID'))}。
     */
    private String applyAggregateFilter(String formula, String predicate) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        int length = formula.length();
        boolean applied = false;
        while (index < length) {
            char current = formula.charAt(index);
            if (Character.isLetter(current) || current == '_') {
                int start = index;
                while (index < length && (Character.isLetterOrDigit(formula.charAt(index)) || formula.charAt(index) == '_')) {
                    index++;
                }
                String word = formula.substring(start, index);
                int open = skipWhitespace(formula, index);
                if (open < length && formula.charAt(open) == '(' && AGGREGATE_FUNCTIONS.contains(word.toUpperCase(Locale.ROOT))) {
                    int close = matchParenthesis(formula, open);
                    if (close > open) {
                        result.append(formula, start, close + 1).append(" FILTER (WHERE ").append(predicate).append(')');
                        applied = true;
                        index = close + 1;
                        continue;
                    }
                }
                result.append(word);
                continue;
            }
            result.append(current);
            index++;
        }
        String body = result.toString();
        return applied ? "(" + body + ")" : "(" + formula + ")";
    }

    private static int skipWhitespace(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** 返回与 {@code openIndex} 处左括号配对的右括号下标，找不到返回 -1。 */
    private static int matchParenthesis(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String ensureDimensionJoin(String factFieldName, String dimensionModelId, Map<String, String> dimensionAlias, StringBuilder joins) {
        String existing = dimensionAlias.get(dimensionModelId);
        if (existing != null) {
            return existing;
        }
        if (factFieldName == null) {
            throw new IllegalArgumentException("维度未与所选事实表建立关联：" + dimensionModelId);
        }
        DataModel dimensionModel = dataModelRepository
            .findById(dimensionModelId)
            .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + dimensionModelId));
        List<ModelField> dimensionFields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimensionModelId);
        String joinField = dimensionFields
            .stream()
            .filter(f -> Boolean.TRUE.equals(f.getIsPrimaryKey()))
            .map(ModelField::getFieldName)
            .findFirst()
            .orElseGet(() -> dimensionFields.stream().map(ModelField::getFieldName).filter(factFieldName::equals).findFirst().orElse(null));
        String alias = "d" + dimensionAlias.size();
        dimensionAlias.put(dimensionModelId, alias);
        joins.append("\nLEFT JOIN ").append(physicalTableName(dimensionModel)).append(" ").append(alias);
        if (joinField != null) {
            joins.append(" ON ").append(FACT_ALIAS).append(".").append(factFieldName).append(" = ").append(alias).append(".").append(joinField);
        }
        return alias;
    }

    private String buildPredicate(MetricFilterCondition condition, String column) {
        MetricFilterOperator operator = MetricFilterOperator.fromCode(condition.getOperator());
        if (operator == null) {
            throw new IllegalArgumentException("不支持的业务限定运算符：" + condition.getOperator());
        }
        if (operator.isUnary()) {
            return column + " " + operator.getSymbol();
        }
        if (operator.isRange()) {
            if (condition.getValue() == null || condition.getValueEnd() == null) {
                throw new IllegalArgumentException("BETWEEN 条件需要提供起止值");
            }
            return column + " BETWEEN " + quote(condition.getValue()) + " AND " + quote(condition.getValueEnd());
        }
        if (operator == MetricFilterOperator.IN || operator == MetricFilterOperator.NOT_IN) {
            String[] parts = condition.getValue() == null ? new String[0] : condition.getValue().split(",");
            String values = java.util.Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::quote)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            return column + " " + operator.getSymbol() + " (" + values + ")";
        }
        if (condition.getValue() == null) {
            throw new IllegalArgumentException("业务限定缺少取值");
        }
        return column + " " + operator.getSymbol() + " " + quote(condition.getValue());
    }

    private String quote(String value) {
        if (value == null) {
            return "NULL";
        }
        String trimmed = value.trim();
        if (NUMBER_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "'" + trimmed.replace("'", "''") + "'";
    }

    /**
     * 时间过滤取值归一化：数值型时间键（如 yyyyMMdd 的 order_date_sk）会将日期字符串转换为数值形式。
     */
    private String normalizeTimeValue(String value, String fieldType) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim().replace('T', ' ');
        String datePart = normalized.length() >= 10 ? normalized.substring(0, 10) : normalized;
        String type = fieldType == null ? "" : fieldType.toUpperCase();
        if ("DATE".equals(type)) {
            return datePart;
        }
        if (isNumericFieldType(type)) {
            return datePart.replace("-", "");
        }
        // DATETIME / TIMESTAMP 及其它：保留完整日期时间
        return normalized;
    }

    private boolean isNumericFieldType(String fieldType) {
        if (fieldType == null) {
            return false;
        }
        String type = fieldType.toUpperCase();
        return type.equals("INTEGER") || type.equals("LONG") || type.equals("DOUBLE") || type.equals("DECIMAL");
    }

    private String physicalTableName(DataModel model) {
        List<String> parts = new ArrayList<>();
        if (model.getSchemaName() != null && !model.getSchemaName().isBlank()) {
            parts.add(model.getSchemaName());
        }
        if (model.getTableName() != null && !model.getTableName().isBlank()) {
            parts.add(model.getTableName());
        } else if (model.getCode() != null && !model.getCode().isBlank()) {
            parts.add(model.getCode());
        }
        return String.join(".", parts);
    }

    private String resolveFactModelId(Metric metric, Map<String, Metric> byCode) {
        if (Metric.TYPE_ATOMIC.equals(metric.getMetricType())) {
            if (metric.getFactModelId() == null) {
                throw new IllegalArgumentException("原子指标未绑定事实表：" + metric.getName());
            }
            return metric.getFactModelId();
        }
        Set<String> factModelIds = new LinkedHashSet<>();
        collectFactModelIds(metric, byCode, factModelIds, new HashSet<>());
        if (factModelIds.isEmpty()) {
            throw new IllegalArgumentException("无法确定衍生指标的事实表：" + metric.getName());
        }
        if (factModelIds.size() > 1) {
            throw new IllegalArgumentException("衍生指标引用的原子指标来自多个事实表，暂不支持查询：" + metric.getName());
        }
        return factModelIds.iterator().next();
    }

    private void collectFactModelIds(Metric metric, Map<String, Metric> byCode, Set<String> out, Set<String> visited) {
        if (metric == null || !visited.add(metric.getCode())) {
            return;
        }
        if (Metric.TYPE_ATOMIC.equals(metric.getMetricType())) {
            if (metric.getFactModelId() != null) {
                out.add(metric.getFactModelId());
            }
            return;
        }
        Matcher matcher = FORMULA_REF_PATTERN.matcher(metric.getFormula() == null ? "" : metric.getFormula());
        while (matcher.find()) {
            collectFactModelIds(byCode.get(matcher.group(1)), byCode, out, visited);
        }
    }

    private MetricFilterConfig parseFilterConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MetricFilterConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("业务限定格式错误：" + e.getOriginalMessage());
        }
    }

    /** 查询上下文。 */
    private static class QueryContext {

        List<Metric> metrics;
        Map<String, Metric> byCode;
        List<DataModel> factModels;
        boolean single;
        Map<String, String> metricFactModel = new LinkedHashMap<>();
        Map<String, List<ModelField>> factFields = new HashMap<>();
        Map<String, Map<String, String>> factDimensionMaps = new HashMap<>();
        List<String> commonDimensionModelIds = new ArrayList<>();
        List<DimSelection> dimensions = new ArrayList<>();
        List<MetricFilterCondition> conditions = new ArrayList<>();
        List<ScopedDimension> dataScopes = new ArrayList<>();
        MetricQueryTimeRangeDTO timeRange;
    }

    /** 维度选择：维度模型 + 分组键字段 + 展示字段。 */
    private static class DimSelection {

        String dimensionModelId;
        List<String> groupKeyFields = new ArrayList<>();
        List<String> displayFields;
    }
}
