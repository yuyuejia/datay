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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Pattern DATE_NAME_PATTERN = Pattern.compile("date|time", Pattern.CASE_INSENSITIVE);

    /** 事实表别名，避免与维度表字段重名。 */
    private static final String FACT_ALIAS = "f";

    /** 层级维度类型。 */
    private static final String DIMENSION_KIND_HIERARCHY = "HIERARCHY";

    /** 字段角色。 */
    private static final String ROLE_LEVEL_ID = "LEVEL_ID";
    private static final String ROLE_LEVEL_NAME = "LEVEL_NAME";

    private final MetricRepository metricRepository;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;
    private final ObjectMapper objectMapper;

    public MetricQueryService(
        MetricRepository metricRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService,
        ObjectMapper objectMapper
    ) {
        this.metricRepository = metricRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行公共指标查询，返回 columns / rows / affectedRows / sql。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> query(MetricQueryDTO dto) throws SQLException {
        QueryContext context = prepare(dto);
        Long dataSourceId = validateDataSource(context);
        DataSourceDTO dataSource = dataSourceService
            .findOne(dataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + dataSourceId));
        String sql = buildSql(context);
        Map<String, Object> result = new LinkedHashMap<>(dataSourceQueryService.executeQuery(dataSource, sql));
        result.put("sql", sql);
        return result;
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
            item.put("id", metric.getId());
            item.put("name", metric.getName());
            item.put("code", metric.getCode());
            item.put("metricType", metric.getMetricType());
            item.put("factModelId", context.metricFactModel.get(metric.getId()));
            metrics.add(item);
        }

        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (Long dimensionModelId : context.commonDimensionModelIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dimensionModelId", dimensionModelId);
            DataModel dimensionModel = dataModelRepository.findById(dimensionModelId).orElse(null);
            item.put("dimensionModelName", dimensionModel == null ? null : dimensionModel.getName());
            item.put("factFieldName", context.single ? context.factDimensionMaps.get(context.factModels.get(0).getId()).get(dimensionModelId) : null);
            boolean hierarchy = dimensionModel != null && DIMENSION_KIND_HIERARCHY.equalsIgnoreCase(dimensionModel.getDimensionKind());
            item.put("isHierarchy", hierarchy);
            item.put("levelCount", hierarchy ? dimensionModel.getLevelCount() : null);
            if (hierarchy) {
                int levelCount = dimensionModel.getLevelCount() == null ? 0 : dimensionModel.getLevelCount();
                List<Map<String, Object>> levels = new ArrayList<>();
                for (int level = 1; level <= levelCount; level++) {
                    Map<String, Object> levelItem = new LinkedHashMap<>();
                    levelItem.put("levelIndex", level);
                    levelItem.put("idField", levelFieldName(dimensionModelId, level, ROLE_LEVEL_ID));
                    levelItem.put("nameField", levelFieldName(dimensionModelId, level, ROLE_LEVEL_NAME));
                    levels.add(levelItem);
                }
                item.put("levels", levels);
            }
            dimensions.add(item);
        }

        List<Map<String, Object>> dateFields = new ArrayList<>();
        for (String fieldName : context.commonDateFieldNames) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fieldName", fieldName);
            item.put("fieldType", context.dateFieldType.get(fieldName));
            dateFields.add(item);
        }

        List<String> factTableNames = new ArrayList<>();
        context.factModels.forEach(fact -> factTableNames.add(physicalTableName(fact)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metrics", metrics);
        result.put("factTables", factTableNames);
        result.put("dimensions", dimensions);
        result.put("dateFields", dateFields);
        return result;
    }

    /**
     * 查询上下文：解析指标、事实表、维度连接信息、公共维度与时间字段。
     */
    private QueryContext prepare(MetricQueryDTO dto) {
        QueryContext context = new QueryContext();
        context.metrics = resolveMetrics(dto);
        context.byCode = loadByCode();

        LinkedHashMap<Long, DataModel> factMap = new LinkedHashMap<>();
        for (Metric metric : context.metrics) {
            Long factModelId = resolveFactModelId(metric, context.byCode);
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
        context.commonDateFieldNames = commonDateFieldNames(context.factModels);
        List<ModelField> firstFactFields = context.factFields.get(context.factModels.get(0).getId());
        for (String fieldName : context.commonDateFieldNames) {
            String fieldType = firstFactFields
                .stream()
                .filter(f -> f.getFieldName().equals(fieldName))
                .findFirst()
                .map(ModelField::getFieldType)
                .orElse(null);
            context.dateFieldType.put(fieldName, fieldType);
        }

        context.dimensions = new ArrayList<>();
        for (MetricQueryFieldDTO dimension : dto.getDimensions() == null ? List.<MetricQueryFieldDTO>of() : dto.getDimensions()) {
            Long dimensionModelId = dimension.getDimensionModelId();
            if (dimensionModelId == null) {
                continue;
            }
            if (!context.commonDimensionModelIds.contains(dimensionModelId)) {
                throw new IllegalArgumentException("维度不在所选指标事实表的共同维度中：" + dimensionModelId);
            }
            DataModel dimensionModel = dataModelRepository
                .findById(dimensionModelId)
                .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + dimensionModelId));
            boolean hierarchy = DIMENSION_KIND_HIERARCHY.equalsIgnoreCase(dimensionModel.getDimensionKind());

            DimSelection selection = new DimSelection();
            selection.dimensionModelId = dimensionModelId;

            if (hierarchy) {
                int levelCount = dimensionModel.getLevelCount() == null ? 0 : dimensionModel.getLevelCount();
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
                if (displayFields.isEmpty()) {
                    continue;
                }
                selection.groupKeyFields = primaryKeyFields(dimensionModelId);
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
                if (
                    condition.getDimensionModelId() == null ||
                    condition.getDimensionFieldName() == null ||
                    condition.getDimensionFieldName().isBlank()
                ) {
                    continue;
                }
                if (!context.commonDimensionModelIds.contains(condition.getDimensionModelId())) {
                    throw new IllegalArgumentException("业务限定维度不在所选指标事实表的共同维度中：" + condition.getDimensionModelId());
                }
                context.conditions.add(condition);
            }
        }

        context.timeRange = dto.getTimeRange();
        return context;
    }

    private Long validateDataSource(QueryContext context) {
        Long dataSourceId = null;
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
        Map<Long, String> dimensionAlias = new HashMap<>();
        Map<Long, String> dimensionJoin = context.factDimensionMaps.get(factModel.getId());
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
            selects.add(metricExpression(metric.getCode(), context.byCode, new HashSet<>()) + " AS " + metric.getCode());
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
        Map<Long, String> dimensionAlias = new HashMap<>();
        Map<Long, String> dimensionJoin = context.factDimensionMaps.get(factModel.getId());
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
            selects.add(metricExpression(metric.getCode(), context.byCode, new HashSet<>()) + " AS " + metric.getCode());
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
        Map<Long, String> dimensionJoin,
        Map<Long, String> dimensionAlias,
        StringBuilder joins
    ) {
        List<String> fragments = new ArrayList<>();
        for (MetricFilterCondition condition : context.conditions) {
            String alias = ensureDimensionJoin(
                dimensionJoin.get(condition.getDimensionModelId()),
                condition.getDimensionModelId(),
                dimensionAlias,
                joins
            );
            String column = alias + "." + condition.getDimensionFieldName();
            String predicate = buildPredicate(condition, column);
            if (predicate != null) {
                if (fragments.isEmpty()) {
                    fragments.add("(" + predicate + ")");
                } else {
                    String logic = "OR".equalsIgnoreCase(condition.getLogic()) ? " OR " : " AND ";
                    fragments.add(logic + "(" + predicate + ")");
                }
            }
        }

        MetricQueryTimeRangeDTO timeRange = context.timeRange;
        if (
            timeRange != null &&
            timeRange.getFactFieldName() != null &&
            !timeRange.getFactFieldName().isBlank() &&
            ((timeRange.getStart() != null && !timeRange.getStart().isBlank()) || (timeRange.getEnd() != null && !timeRange.getEnd().isBlank()))
        ) {
            Set<String> factFieldNames = new HashSet<>();
            context.factFields.get(factModel.getId()).forEach(f -> factFieldNames.add(f.getFieldName()));
            if (factFieldNames.contains(timeRange.getFactFieldName())) {
                String column = FACT_ALIAS + "." + timeRange.getFactFieldName();
                String prefix = fragments.isEmpty() ? "" : " AND ";
                if (timeRange.getStart() != null && !timeRange.getStart().isBlank() && timeRange.getEnd() != null && !timeRange.getEnd().isBlank()) {
                    fragments.add(prefix + "(" + column + " BETWEEN " + quote(timeRange.getStart()) + " AND " + quote(timeRange.getEnd()) + ")");
                } else if (timeRange.getStart() != null && !timeRange.getStart().isBlank()) {
                    fragments.add(prefix + "(" + column + " >= " + quote(timeRange.getStart()) + ")");
                } else {
                    fragments.add(prefix + "(" + column + " <= " + quote(timeRange.getEnd()) + ")");
                }
            }
        }
        return String.join("", fragments);
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

    private List<String> primaryKeyFields(Long dimensionModelId) {
        List<String> keys = new ArrayList<>();
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimensionModelId)) {
            if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                keys.add(field.getFieldName());
            }
        }
        return keys;
    }

    private String levelFieldName(Long dimensionModelId, int level, String role) {
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
        List<Long> ids = new ArrayList<>();
        if (dto.getMetricIds() != null) {
            ids.addAll(dto.getMetricIds());
        }
        if (ids.isEmpty() && dto.getMetricId() != null) {
            ids.add(dto.getMetricId());
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要查询的指标");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
        List<Metric> metrics = new ArrayList<>();
        for (Long id : unique) {
            metrics.add(metricRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("指标不存在：" + id)));
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

    private Map<Long, String> dimensionJoinMap(Long factModelId) {
        Map<Long, String> map = new HashMap<>();
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(factModelId)) {
            if (field.getDimensionModelId() != null) {
                map.putIfAbsent(field.getDimensionModelId(), field.getFieldName());
            }
        }
        return map;
    }

    private List<Long> commonDimensions(List<DataModel> factModels) {
        if (factModels.isEmpty()) {
            return List.of();
        }
        List<Long> common = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(factModels.get(0).getId())) {
            Long dimensionModelId = field.getDimensionModelId();
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

    private List<String> commonDateFieldNames(List<DataModel> factModels) {
        if (factModels.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> common = new LinkedHashSet<>();
        for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(factModels.get(0).getId())) {
            if (!isDateLike(field)) {
                continue;
            }
            boolean existsInAll = true;
            for (int i = 1; i < factModels.size(); i++) {
                boolean found = modelFieldRepository
                    .findByModelIdOrderBySortOrderAsc(factModels.get(i).getId())
                    .stream()
                    .anyMatch(f -> f.getFieldName().equals(field.getFieldName()));
                if (!found) {
                    existsInAll = false;
                    break;
                }
            }
            if (existsInAll) {
                common.add(field.getFieldName());
            }
        }
        return new ArrayList<>(common);
    }

    private boolean isDateLike(ModelField field) {
        String type = field.getFieldType() == null ? "" : field.getFieldType().toUpperCase();
        return type.equals("DATE") || type.equals("DATETIME") || type.equals("TIMESTAMP") || DATE_NAME_PATTERN.matcher(field.getFieldName()).find();
    }

    private String metricExpression(String code, Map<String, Metric> byCode, Set<String> visiting) {
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
            expression = "(" + formula + ")";
        } else {
            String formula = metric.getFormula() == null ? "" : metric.getFormula();
            Matcher matcher = FORMULA_REF_PATTERN.matcher(formula);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String refExpr = metricExpression(matcher.group(1), byCode, visiting);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(refExpr));
            }
            matcher.appendTail(sb);
            expression = "(" + sb + ")";
        }
        visiting.remove(code);
        return expression;
    }

    private String ensureDimensionJoin(String factFieldName, Long dimensionModelId, Map<Long, String> dimensionAlias, StringBuilder joins) {
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

    private Long resolveFactModelId(Metric metric, Map<String, Metric> byCode) {
        if (Metric.TYPE_ATOMIC.equals(metric.getMetricType())) {
            if (metric.getFactModelId() == null) {
                throw new IllegalArgumentException("原子指标未绑定事实表：" + metric.getName());
            }
            return metric.getFactModelId();
        }
        Set<Long> factModelIds = new LinkedHashSet<>();
        collectFactModelIds(metric, byCode, factModelIds, new HashSet<>());
        if (factModelIds.isEmpty()) {
            throw new IllegalArgumentException("无法确定衍生指标的事实表：" + metric.getName());
        }
        if (factModelIds.size() > 1) {
            throw new IllegalArgumentException("衍生指标引用的原子指标来自多个事实表，暂不支持查询：" + metric.getName());
        }
        return factModelIds.iterator().next();
    }

    private void collectFactModelIds(Metric metric, Map<String, Metric> byCode, Set<Long> out, Set<String> visited) {
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
        Map<Long, Long> metricFactModel = new LinkedHashMap<>();
        Map<Long, List<ModelField>> factFields = new HashMap<>();
        Map<Long, Map<Long, String>> factDimensionMaps = new HashMap<>();
        List<Long> commonDimensionModelIds = new ArrayList<>();
        List<String> commonDateFieldNames = new ArrayList<>();
        Map<String, String> dateFieldType = new HashMap<>();
        List<DimSelection> dimensions = new ArrayList<>();
        List<MetricFilterCondition> conditions = new ArrayList<>();
        MetricQueryTimeRangeDTO timeRange;
    }

    /** 维度选择：维度模型 + 分组键字段 + 展示字段。 */
    private static class DimSelection {

        Long dimensionModelId;
        List<String> groupKeyFields = new ArrayList<>();
        List<String> displayFields;
    }
}
