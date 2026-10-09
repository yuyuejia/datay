package com.data.datafusion.service.dashboard;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DimensionValueService;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.data.datafusion.service.dto.MetricQueryFieldDTO;
import com.data.datafusion.service.dto.MetricQueryTimeRangeDTO;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.MetricFilterConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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
 * 看板数据查询服务。
 *
 * <p>按看板 spec 中的数据集定义执行查询：指标数据集复用 {@link MetricQueryService}（含 RBAC 数据权限），
 * 物理表数据集在只读校验后用预编译参数执行。运行时筛选器值按 binding 合并进查询，
 * 客户端无需、也不能直接提交任意 SQL。
 */
@Service
public class DashboardDataService {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardDataService.class);

    private static final String BINDING_METRIC_TIME = "metricTime";
    private static final String BINDING_METRIC_DIMENSION = "metricDimension";
    private static final String BINDING_METRIC_FACT = "metricFactField";
    private static final String BINDING_SQL_PARAM = "sqlParam";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private final AnalysisDashboardRepository dashboardRepository;
    private final DashboardSpecValidator specValidator;
    private final MetricQueryService metricQueryService;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;
    private final DimensionValueService dimensionValueService;
    private final DataModelRepository dataModelRepository;
    private final ObjectMapper objectMapper;

    public DashboardDataService(
        AnalysisDashboardRepository dashboardRepository,
        DashboardSpecValidator specValidator,
        MetricQueryService metricQueryService,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService,
        DimensionValueService dimensionValueService,
        DataModelRepository dataModelRepository,
        ObjectMapper objectMapper
    ) {
        this.dashboardRepository = dashboardRepository;
        this.specValidator = specValidator;
        this.metricQueryService = metricQueryService;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.dimensionValueService = dimensionValueService;
        this.dataModelRepository = dataModelRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行看板的某个数据集。
     *
     * @param dashboardId  看板 id
     * @param datasetId    数据集 id
     * @param filterValues 运行时筛选器值，键为筛选器 id；缺省时回退到 spec 中的 defaultValue
     * @return {@code columns} / {@code rows} / {@code affectedRows} / {@code sql}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> queryDataset(String dashboardId, String datasetId, Map<String, Object> filterValues) throws SQLException {
        AnalysisDashboard dashboard = dashboardRepository
            .findById(dashboardId)
            .orElseThrow(() -> new IllegalArgumentException("看板不存在：" + dashboardId));
        Map<String, Object> spec = specValidator.parse(dashboard.getSpec());
        return queryDataset(spec, dashboard.getDataSourceId(), datasetId, filterValues);
    }

    /**
     * 基于内存中的看板 spec 执行数据集，用于设计页未保存时的实时预览。
     */
    public Map<String, Object> queryDataset(
        Map<String, Object> spec,
        String fallbackDataSourceId,
        String datasetId,
        Map<String, Object> filterValues
    ) throws SQLException {
        Map<String, Object> dataset = findDataset(spec, datasetId);
        String type = asString(dataset.get("type"));
        if (DashboardSpecValidator.DATASET_SQL.equals(type)) {
            return querySqlDataset(fallbackDataSourceId, spec, dataset, filterValues);
        }
        return queryMetricDataset(spec, dataset, filterValues);
    }

    /**
     * 加载筛选器选项：动态维度成员或静态值。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> loadFilterOptions(String dashboardId, String filterId, String keyword, Integer limit) {
        AnalysisDashboard dashboard = dashboardRepository
            .findById(dashboardId)
            .orElseThrow(() -> new IllegalArgumentException("看板不存在：" + dashboardId));
        Map<String, Object> spec = specValidator.parse(dashboard.getSpec());
        return loadFilterOptions(spec, filterId, keyword, limit);
    }

    /**
     * 基于内存中的看板 spec 加载筛选器选项。
     */
    public Map<String, Object> loadFilterOptions(Map<String, Object> spec, String filterId, String keyword, Integer limit) {
        Map<String, Object> filter = findFilter(spec, filterId);
        Map<String, Object> options = asMap(filter.get("options"));
        String mode = asString(options.get("mode"));
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
        if ("dimension".equalsIgnoreCase(mode)) {
            String modelCode = asString(options.get("dimensionModelCode"));
            String fieldName = asString(options.get("dimensionFieldName"));
            if (modelCode == null || fieldName == null) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 未配置维度字段");
            }
            DataModel model = dataModelRepository
                .findFirstByCode(modelCode)
                .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + modelCode));
            return dimensionValueService.pageValues(model.getId(), fieldName, keyword, 1, size);
        }
        List<String> staticValues = asStringList(options.get("values"));
        List<String> filtered = new ArrayList<>();
        for (String value : staticValues) {
            if (keyword == null || keyword.isBlank() || value.toLowerCase().contains(keyword.toLowerCase())) {
                filtered.add(value);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("values", filtered);
        result.put("total", filtered.size());
        return result;
    }

    // ------------------------------------------------------------- metric data

    private Map<String, Object> queryMetricDataset(
        Map<String, Object> spec,
        Map<String, Object> dataset,
        Map<String, Object> filterValues
    ) throws SQLException {
        Map<String, Object> metricQuery = asMap(dataset.get("metricQuery"));
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(asStringList(metricQuery.get("metricCodes")));
        dto.setDimensions(parseDimensions(metricQuery.get("dimensions")));
        dto.setTimeRange(parseTimeRange(metricQuery.get("timeRange")));

        List<MetricFilterCondition> conditions = new ArrayList<>();
        MetricFilterConfig existing = parseFilterConfig(metricQuery.get("filterConfig"));
        if (existing != null && existing.getConditions() != null) {
            conditions.addAll(existing.getConditions());
        }

        String datasetId = asString(dataset.get("id"));
        for (Map<String, Object> filter : asMapList(spec.get("filters"))) {
            String filterType = asString(filter.get("type"));
            Object value = effectiveValue(filter, filterValues);
            if (isEmptyValue(value)) {
                continue;
            }
            for (Map<String, Object> binding : asMapList(filter.get("bindings"))) {
                if (!datasetId.equals(asString(binding.get("datasetId")))) {
                    continue;
                }
                String kind = asString(binding.get("kind"));
                if (BINDING_METRIC_TIME.equals(kind)) {
                    String[] range = readRange(value);
                    if (range[0] == null || range[1] == null) {
                        continue;
                    }
                    MetricQueryTimeRangeDTO timeRange = new MetricQueryTimeRangeDTO();
                    timeRange.setStart(range[0]);
                    timeRange.setEnd(range[1]);
                    dto.setTimeRange(timeRange);
                } else if (BINDING_METRIC_DIMENSION.equals(kind)) {
                    MetricFilterCondition condition = buildDimensionCondition(filterType, binding, value);
                    if (condition != null) {
                        conditions.add(condition);
                    }
                } else if (BINDING_METRIC_FACT.equals(kind)) {
                    MetricFilterCondition condition = buildFactCondition(binding, value);
                    if (condition != null) {
                        conditions.add(condition);
                    }
                }
            }
        }

        if (!conditions.isEmpty()) {
            try {
                MetricFilterConfig config = new MetricFilterConfig();
                config.setConditions(conditions);
                dto.setFilterConfig(objectMapper.writeValueAsString(config));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("筛选条件序列化失败：" + e.getOriginalMessage());
            }
        }
        return metricQueryService.query(dto);
    }

    private MetricFilterCondition buildDimensionCondition(String filterType, Map<String, Object> binding, Object value) {
        MetricFilterCondition condition = new MetricFilterCondition();
        condition.setType(MetricFilterCondition.TYPE_DIMENSION);
        condition.setDimensionModelCode(asString(binding.get("dimensionModelCode")));
        condition.setDimensionFieldName(asString(binding.get("dimensionFieldName")));
        if (DashboardSpecValidator.FILTER_INPUT.equals(filterType)) {
            condition.setOperator("LIKE");
            condition.setValue("%" + firstValue(value) + "%");
        } else if (DashboardSpecValidator.FILTER_NUMBER_RANGE.equals(filterType)) {
            String[] range = readRange(value);
            condition.setOperator("BETWEEN");
            condition.setValue(range[0]);
            condition.setValueEnd(range[1]);
        } else {
            List<String> values = asStringList(value);
            if (values.isEmpty()) {
                return null;
            }
            if (values.size() == 1) {
                condition.setOperator("EQ");
                condition.setValue(values.get(0));
            } else {
                condition.setOperator("IN");
                condition.setValue(String.join(",", values));
            }
        }
        return condition;
    }

    private MetricFilterCondition buildFactCondition(Map<String, Object> binding, Object value) {
        String[] range = readRange(value);
        if (range[0] == null && range[1] == null) {
            return null;
        }
        MetricFilterCondition condition = new MetricFilterCondition();
        condition.setType(MetricFilterCondition.TYPE_FACT_FIELD);
        condition.setFactFieldName(asString(binding.get("factFieldName")));
        condition.setOperator("BETWEEN");
        condition.setValue(range[0]);
        condition.setValueEnd(range[1]);
        return condition;
    }

    // ---------------------------------------------------------------- sql data

    private Map<String, Object> querySqlDataset(
        String fallbackDataSourceId,
        Map<String, Object> spec,
        Map<String, Object> dataset,
        Map<String, Object> filterValues
    ) throws SQLException {
        String sql = DashboardSqlGuard.requireReadOnly(asString(dataset.get("sql")));
        String dataSourceId = asString(dataset.get("dataSourceId"));
        if (dataSourceId == null) {
            dataSourceId = fallbackDataSourceId;
        }
        if (dataSourceId == null) {
            throw new IllegalArgumentException("SQL 数据集未绑定数据源");
        }
        final String resolvedDataSourceId = dataSourceId;
        DataSourceDTO dataSource = dataSourceService
            .findOne(resolvedDataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + resolvedDataSourceId));

        Map<String, Object> namedParams = collectSqlParams(spec, dataset, filterValues);
        String effectiveSql = neutralizeEmptyParams(sql, namedParams);
        String renderedSql = renderSql(effectiveSql, namedParams);

        Map<String, Object> result = new LinkedHashMap<>(dataSourceQueryService.executeQuery(dataSource, renderedSql));
        result.put("sql", sql);
        return result;
    }

    private Map<String, Object> collectSqlParams(
        Map<String, Object> spec,
        Map<String, Object> dataset,
        Map<String, Object> filterValues
    ) {
        String datasetId = asString(dataset.get("id"));
        Map<String, Object> named = new LinkedHashMap<>();
        for (Map<String, Object> filter : asMapList(spec.get("filters"))) {
            String filterType = asString(filter.get("type"));
            Object value = effectiveValue(filter, filterValues);
            boolean emptyValue = isEmptyValue(value);
            for (Map<String, Object> binding : asMapList(filter.get("bindings"))) {
                if (!datasetId.equals(asString(binding.get("datasetId"))) || !BINDING_SQL_PARAM.equals(asString(binding.get("kind")))) {
                    continue;
                }
                String param = asString(binding.get("param"));
                String paramEnd = asString(binding.get("paramEnd"));
                if (emptyValue) {
                    // 空筛选条件：该占位符在做谓词中和处理，不再参与绑定，等价于「不过滤，取全部」
                    continue;
                }
                if (DashboardSpecValidator.FILTER_DATE_RANGE.equals(filterType) || DashboardSpecValidator.FILTER_NUMBER_RANGE.equals(filterType)) {
                    String[] range = readRange(value);
                    named.put(param, range[0]);
                    if (paramEnd != null && !paramEnd.isBlank()) {
                        named.put(paramEnd, range[1]);
                    }
                } else {
                    named.put(param, value);
                }
            }
        }
        return named;
    }

    /**
     * 空筛选条件的占位符中和：把 {@code col = ${x}} / {@code col IN (${x})} / {@code col BETWEEN ${x} AND ${y}}
     * 等谓词替换为恒真 {@code 1=1}，从而在筛选器未设值时返回全部数据，同时保证 SQL 合法。
     */
    static String neutralizeEmptyParams(String sql, Map<String, Object> namedParams) {
        Set<String> emptyParams = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!namedParams.containsKey(name)) {
                emptyParams.add(name);
            }
        }
        String result = sql;
        for (String name : emptyParams) {
            result = neutralizeParam(result, name);
        }
        return result;
    }

    private static String neutralizeParam(String sql, String name) {
        Pattern predicate = Pattern.compile(
            "(?i)([A-Za-z0-9_.\"`]+\\s*(?:NOT\\s+IN\\s*\\(|IN\\s*\\(|NOT\\s+LIKE|LIKE|BETWEEN|<>|!=|>=|<=|=|>|<)\\s*['\"]?\\s*)\\$\\{" +
            Pattern.quote(name) +
            "\\}(['\"]?\\s*\\)?)?"
        );
        String replaced = predicate.matcher(sql).replaceAll("1=1");
        return replaced.replace("${" + name + "}", "1=1");
    }

    /**
     * 渲染 SQL：把 {@code ${参数名}} 替换为安全的字面量。
     *
     * <p>相比预编译占位符，字面量渲染能正确处理 AI 生成 SQL 中常见的引号包裹
     * （如 {@code '${x}'}、{@code '%${x}%'}）场景，避免 {@code '?'} 落在字符串内导致参数个数不匹配。
     * 字符串值通过转义单引号防止 SQL 注入，数值/布尔保持原样。
     */
    static String renderSql(String sql, Map<String, Object> namedParams) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                out.append(c);
                i++;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                out.append(c);
                i++;
                continue;
            }
            if (c == '$' && i + 1 < sql.length() && sql.charAt(i + 1) == '{') {
                int end = sql.indexOf('}', i + 2);
                if (end > 0) {
                    String name = sql.substring(i + 2, end);
                    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        Object value = namedParams.get(name);
                        out.append(inSingle || inDouble ? rawLiteral(value) : sqlLiteral(value));
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String sqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "NULL";
            }
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(sqlLiteral(list.get(index)));
            }
            return builder.toString();
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    private static String rawLiteral(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    builder.append(",");
                }
                builder.append(String.valueOf(list.get(index)).replace("'", "''"));
            }
            return builder.toString();
        }
        return String.valueOf(value).replace("'", "''");
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> findDataset(Map<String, Object> spec, String datasetId) {
        for (Map<String, Object> dataset : asMapList(spec.get("datasets"))) {
            if (datasetId.equals(asString(dataset.get("id")))) {
                return dataset;
            }
        }
        throw new IllegalArgumentException("数据集不存在：" + datasetId);
    }

    private Map<String, Object> findFilter(Map<String, Object> spec, String filterId) {
        for (Map<String, Object> filter : asMapList(spec.get("filters"))) {
            if (filterId.equals(asString(filter.get("id")))) {
                return filter;
            }
        }
        throw new IllegalArgumentException("筛选器不存在：" + filterId);
    }

    private List<MetricQueryFieldDTO> parseDimensions(Object raw) {
        List<MetricQueryFieldDTO> dimensions = new ArrayList<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String code = asString(item.get("dimensionModelCode"));
            if (code == null || code.isBlank()) {
                continue;
            }
            MetricQueryFieldDTO field = new MetricQueryFieldDTO();
            field.setDimensionModelCode(code);
            List<String> fieldNames = asStringList(item.get("dimensionFieldNames"));
            if (!fieldNames.isEmpty()) {
                field.setDimensionFieldNames(fieldNames);
            } else {
                String single = asString(item.get("dimensionFieldName"));
                if (single != null && !single.isBlank()) {
                    field.setDimensionFieldName(single);
                }
            }
            Long levelIndex = asLong(item.get("levelIndex"));
            if (levelIndex != null) {
                field.setLevelIndex(levelIndex.intValue());
            }
            dimensions.add(field);
        }
        return dimensions;
    }

    private MetricQueryTimeRangeDTO parseTimeRange(Object raw) {
        String[] range = readRange(raw);
        if (range[0] == null && range[1] == null) {
            return null;
        }
        MetricQueryTimeRangeDTO timeRange = new MetricQueryTimeRangeDTO();
        timeRange.setStart(range[0]);
        timeRange.setEnd(range[1]);
        return timeRange;
    }

    private MetricFilterConfig parseFilterConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof String text) {
                if (text.isBlank()) {
                    return null;
                }
                return objectMapper.readValue(text, MetricFilterConfig.class);
            }
            if (raw instanceof Map<?, ?>) {
                return objectMapper.convertValue(raw, MetricFilterConfig.class);
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            LOG.warn("解析看板数据集 filterConfig 失败: {}", e.getMessage());
        }
        return null;
    }

    private Object effectiveValue(Map<String, Object> filter, Map<String, Object> filterValues) {
        String id = asString(filter.get("id"));
        if (filterValues != null && filterValues.containsKey(id)) {
            return filterValues.get(id);
        }
        return filter.get("defaultValue");
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().allMatch(this::isEmptyValue);
        }
        return false;
    }

    /**
     * 读取区间值，兼容 {@code {start,end}} / {@code {min,max}} / {@code [a,b]}。
     */
    private String[] readRange(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            String first = firstNonNull(map.get("start"), map.get("min"), map.get("from"));
            String second = firstNonNull(map.get("end"), map.get("max"), map.get("to"));
            return new String[] { blankToNull(first), blankToNull(second) };
        }
        if (raw instanceof List<?> list) {
            String first = list.isEmpty() ? null : String.valueOf(list.get(0));
            String second = list.size() < 2 ? null : String.valueOf(list.get(1));
            return new String[] { blankToNull(first), blankToNull(second) };
        }
        if (raw instanceof String text && text.contains(",")) {
            String[] parts = text.split(",", 2);
            return new String[] { blankToNull(parts[0]), blankToNull(parts[1]) };
        }
        return new String[] { null, null };
    }

    private String firstValue(Object value) {
        List<String> list = asStringList(value);
        return list.isEmpty() ? "" : list.get(0);
    }

    private static String firstNonNull(Object... values) {
        return Arrays.stream(values).filter(v -> v != null && !String.valueOf(v).isBlank()).map(String::valueOf).findFirst().orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = new LinkedHashMap<>((Map<String, Object>) map);
            return cast;
        }
        return new LinkedHashMap<>();
    }

    static List<Map<String, Object>> asMapList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = new LinkedHashMap<>((Map<String, Object>) map);
                    result.add(cast);
                }
            }
        }
        return result;
    }

    static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    public static Long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static List<String> asStringList(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (raw instanceof String text && !text.isBlank()) {
            result.add(text);
        }
        return result;
    }
}
