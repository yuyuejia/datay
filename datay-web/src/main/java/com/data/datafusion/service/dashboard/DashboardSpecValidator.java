package com.data.datafusion.service.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 看板定义校验与规范化。
 *
 * <p>AI 生成的看板 JSON 可能存在缺省字段或轻微结构偏差，这里统一做容错规范化：
 * 补齐 datasets / widgets / filters 结构，校验数据集类型、SQL 只读、组件引用与筛选器绑定，
 * 保证入库的 spec 始终可被前端查看器安全消费。结构性问题直接抛出
 * {@link IllegalArgumentException}，由前端提示重新生成。
 */
@Component
public class DashboardSpecValidator {

    public static final String DATASET_METRIC = "METRIC";
    public static final String DATASET_SQL = "SQL";
    public static final int SPEC_VERSION = 1;

    public static final String WIDGET_ECHARTS = "echarts";
    public static final String WIDGET_HTML = "html";
    public static final String WIDGET_TABLE = "table";
    public static final String WIDGET_KPI = "kpi";

    private static final Set<String> WIDGET_TYPES = Set.of(WIDGET_ECHARTS, WIDGET_HTML, WIDGET_TABLE, WIDGET_KPI);

    private static final String LAYOUT_MODE_STANDARD = "standard";
    private static final String LAYOUT_MODE_SCREEN = "screen";

    private static final Set<String> FIT_MODES = Set.of("contain", "stretch", "cover");
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String DEFAULT_LIGHT_BACKGROUND = "#f5f7fa";
    private static final String DEFAULT_DARK_BACKGROUND = "#0f172a";
    private static final Pattern COLOR_PATTERN = Pattern.compile("^(#[0-9a-fA-F]{3,8}|rgba?\\([^)]+\\)|hsla?\\([^)]+\\))$");
    private static final int MAX_BACKGROUND_PATH_LENGTH = 1024;

    public static final int GRID_COLUMNS = 24;
    public static final int DEFAULT_SCREEN_WIDTH = 1920;
    public static final int DEFAULT_SCREEN_HEIGHT = 1080;

    public static final String FILTER_DATE_RANGE = "dateRange";
    public static final String FILTER_SELECT = "select";
    public static final String FILTER_MULTI_SELECT = "multiSelect";
    public static final String FILTER_INPUT = "input";
    public static final String FILTER_NUMBER_RANGE = "numberRange";

    private static final Set<String> FILTER_TYPES = Set.of(
        FILTER_DATE_RANGE,
        FILTER_SELECT,
        FILTER_MULTI_SELECT,
        FILTER_INPUT,
        FILTER_NUMBER_RANGE
    );

    private static final String BINDING_METRIC_TIME = "metricTime";
    private static final String BINDING_METRIC_DIMENSION = "metricDimension";
    private static final String BINDING_METRIC_FACT = "metricFactField";
    private static final String BINDING_SQL_PARAM = "sqlParam";

    private static final Set<String> AGGREGATIONS = Set.of("sum", "avg", "min", "max", "count", "none");
    private static final Set<String> CHART_TYPES = Set.of("bar", "line", "pie", "area", "gauge");
    private static final Set<String> SERIES_TYPES = Set.of("bar", "line", "pie", "gauge");

    private static final int MAX_SPEC_JSON_LENGTH = 1_000_000;
    private static final int MAX_DATASETS = 50;
    private static final int MAX_WIDGETS = 200;
    private static final int MAX_FILTERS = 100;
    private static final int MAX_HTML_LENGTH = 100_000;

    private final ObjectMapper objectMapper;
    private final AnalysisDatasetResolver datasetResolver;

    public DashboardSpecValidator(ObjectMapper objectMapper, AnalysisDatasetResolver datasetResolver) {
        this.objectMapper = objectMapper;
        this.datasetResolver = datasetResolver;
    }

    /**
     * 校验并规范化看板 spec，返回规范化后的对象。
     */
    public Map<String, Object> validateAndNormalize(String specJson) {
        if (specJson == null || specJson.isBlank()) {
            throw new IllegalArgumentException("看板定义不能为空");
        }
        if (specJson.length() > MAX_SPEC_JSON_LENGTH) {
            throw new IllegalArgumentException("看板定义过大，最大允许 " + MAX_SPEC_JSON_LENGTH + " 个字符");
        }
        Map<String, Object> root = parse(specJson);
        assertVersion(root.get("version"));
        root.putIfAbsent("title", "分析看板");
        root.put("layout", normalizeRootLayout(root.get("layout")));

        Set<String> datasetIds = new LinkedHashSet<>();
        Map<String, String> datasetTypes = new LinkedHashMap<>();
        Map<String, Set<String>> datasetFields = new LinkedHashMap<>();
        List<Map<String, Object>> datasets = normalizeDatasets(root.get("datasets"), datasetIds, datasetTypes, datasetFields);
        root.put("datasets", datasets);
        if (datasets.isEmpty()) {
            throw new IllegalArgumentException("看板至少需要定义一个数据集");
        }
        assertWithinLimit("数据集", datasets.size(), MAX_DATASETS);

        List<Map<String, Object>> widgets = normalizeWidgets(root.get("widgets"), datasetIds, datasetFields);
        root.put("widgets", widgets);
        if (widgets.isEmpty()) {
            throw new IllegalArgumentException("看板至少需要一个图表或内容组件");
        }
        assertWithinLimit("组件", widgets.size(), MAX_WIDGETS);
        assertNoLayoutOverlap(widgets);

        List<Map<String, Object>> filters = normalizeFilters(root.get("filters"), datasetIds, datasetTypes, datasetFields);
        assertWithinLimit("筛选器", filters.size(), MAX_FILTERS);
        root.put("filters", filters);
        root.put("version", SPEC_VERSION);
        return root;
    }

    /**
     * 当前仅支持 v1；不再读取无 version 的历史 spec。
     */
    private void assertVersion(Object raw) {
        int version;
        if (raw instanceof Number number) {
            version = number.intValue();
        } else if (raw != null) {
            try {
                version = Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("不支持的看板 spec 版本：" + raw + "，当前仅支持 version=" + SPEC_VERSION);
            }
        } else {
            throw new IllegalArgumentException("看板 spec 缺少 version，当前仅支持 version=" + SPEC_VERSION);
        }
        if (version != SPEC_VERSION) {
            throw new IllegalArgumentException("不支持的看板 spec 版本：" + raw + "，当前仅支持 version=" + SPEC_VERSION);
        }
    }

    public Map<String, Object> parse(String specJson) {
        try {
            Map<String, Object> map = objectMapper.readValue(specJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return map == null ? new LinkedHashMap<>() : map;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("看板定义不是合法 JSON：" + e.getOriginalMessage());
        }
    }

    public String toJson(Map<String, Object> spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("看板定义序列化失败：" + e.getOriginalMessage());
        }
    }

    // ---------------------------------------------------------------- datasets

    private List<Map<String, Object>> normalizeDatasets(
        Object raw,
        Set<String> datasetIds,
        Map<String, String> datasetTypes,
        Map<String, Set<String>> datasetFields
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String id = asString(item.get("id"));
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("存在缺少 id 的数据集");
            }
            if (!datasetIds.add(id)) {
                throw new IllegalArgumentException("数据集 id 重复：" + id);
            }
            Map<String, Object> dataset = new LinkedHashMap<>(item);
            dataset.put("id", id);

            String refId = asString(dataset.get("refId"));
            if (refId != null && !refId.isBlank()) {
                datasetResolver.assertReferenceExists(refId);
                Map<String, Object> resolved = datasetResolver.resolveDataset(dataset);
                String type = asString(resolved.get("type"));
                type = type == null ? DATASET_METRIC : type.trim().toUpperCase();
                if (!DATASET_METRIC.equals(type) && !DATASET_SQL.equals(type)) {
                    throw new IllegalArgumentException("不支持的数据集类型：" + type);
                }
                dataset.put("refId", refId);
                dataset.put("type", type);
                if (asString(dataset.get("name")) == null || asString(dataset.get("name")).isBlank()) {
                    String name = datasetResolver.findName(refId);
                    if (name != null && !name.isBlank()) {
                        dataset.put("name", name);
                    }
                }
                dataset.putIfAbsent("name", id);
                // 引用型数据集只保存别名与引用，定义始终以数据集库为准
                dataset.remove("metricQuery");
                dataset.remove("sql");
                dataset.remove("params");
                dataset.remove("dataSourceId");
                datasetTypes.put(id, type);
                datasetFields.put(id, extractFieldNames(resolved));
                result.add(dataset);
                continue;
            }

            String type = resolveDatasetType(dataset);
            dataset.put("type", type);
            datasetTypes.put(id, type);
            datasetFields.put(id, extractFieldNames(dataset));
            if (DATASET_SQL.equals(type)) {
                String sql = asString(dataset.get("sql"));
                dataset.put("sql", DashboardSqlGuard.requireReadOnly(sql));
                Long dataSourceId = asLong(dataset.get("dataSourceId"));
                if (dataSourceId != null) {
                    dataset.put("dataSourceId", dataSourceId);
                }
            } else {
                Map<String, Object> metricQuery = asMap(dataset.get("metricQuery"));
                List<String> metricCodes = asStringList(metricQuery.get("metricCodes"));
                if (metricCodes.isEmpty()) {
                    throw new IllegalArgumentException("指标数据集 " + id + " 缺少 metricCodes");
                }
                metricQuery.put("metricCodes", metricCodes);
                metricQuery.putIfAbsent("dimensions", new ArrayList<>());
                dataset.put("metricQuery", metricQuery);
            }
            dataset.putIfAbsent("name", id);
            result.add(dataset);
        }
        return result;
    }

    private String resolveDatasetType(Map<String, Object> dataset) {
        String type = asString(dataset.get("type"));
        if (type != null && !type.isBlank()) {
            type = type.trim().toUpperCase();
            if (!DATASET_METRIC.equals(type) && !DATASET_SQL.equals(type)) {
                throw new IllegalArgumentException("不支持的数据集类型：" + type);
            }
            return type;
        }
        return dataset.get("metricQuery") != null ? DATASET_METRIC : DATASET_SQL;
    }

    /**
     * 从数据集字段缓存中提取输出列名。
     *
     * <p>字段缓存可能是 JSON 字符串或对象，结构为 {@code {columns: [{name, type, description}]}}。
     * 缓存缺失或无法解析时返回空集合，由运行时查询再兜底，不阻塞保存。
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractFieldNames(Map<String, Object> dataset) {
        Object raw = dataset.get("fields");
        if (raw == null) {
            return Set.of();
        }
        Object parsed = raw;
        if (raw instanceof String text) {
            if (text.isBlank()) {
                return Set.of();
            }
            try {
                parsed = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                return Set.of();
            }
        }
        Object columns = parsed instanceof Map<?, ?> map ? map.get("columns") : parsed;
        if (!(columns instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object column : list) {
            if (column instanceof Map<?, ?> map) {
                Object name = map.get("name");
                if (name != null && !String.valueOf(name).isBlank()) {
                    names.add(String.valueOf(name).trim());
                }
            } else if (column != null && !String.valueOf(column).isBlank()) {
                names.add(String.valueOf(column).trim());
            }
        }
        return names;
    }

    // ----------------------------------------------------------------- widgets

    private List<Map<String, Object>> normalizeWidgets(
        Object raw,
        Set<String> datasetIds,
        Map<String, Set<String>> datasetFields
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> widgetIds = new LinkedHashSet<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String id = asString(item.get("id"));
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("存在缺少 id 的看板组件");
            }
            if (!widgetIds.add(id)) {
                throw new IllegalArgumentException("看板组件 id 重复：" + id);
            }
            Map<String, Object> widget = new LinkedHashMap<>(item);
            widget.put("id", id);
            if (widget.containsKey("dataRef")) {
                throw new IllegalArgumentException("组件 " + id + " 使用了已废弃的 dataRef，请改用 encode");
            }
            String type = asString(widget.get("type"));
            type = type == null || type.isBlank() ? WIDGET_ECHARTS : type.trim().toLowerCase();
            if (!WIDGET_TYPES.contains(type)) {
                throw new IllegalArgumentException("不支持的组件类型：" + type);
            }
            widget.put("type", type);
            widget.put("layout", normalizeLayout(widget.get("layout"), type));

            String datasetId = asString(widget.get("datasetId"));
            if (datasetId == null || datasetId.isBlank()) {
                if (!WIDGET_HTML.equals(type)) {
                    throw new IllegalArgumentException("组件 " + id + " 缺少 datasetId");
                }
            } else {
                if (!datasetIds.contains(datasetId)) {
                    throw new IllegalArgumentException("组件 " + id + " 引用了不存在的数据集：" + datasetId);
                }
                widget.put("datasetId", datasetId);
            }

            if (WIDGET_ECHARTS.equals(type)) {
                Map<String, Object> option = asMap(widget.get("echartsOption"));
                widget.put("echartsOption", normalizeEchartsOption(option));
                Map<String, Object> encode = normalizeEncode(widget);
                if (encode.isEmpty()) {
                    throw new IllegalArgumentException("图表组件 " + id + " 缺少 encode");
                }
                widget.put("encode", encode);
                normalizeWidgetChart(widget, option);
                validateEncodeFields(id, datasetId, encode, datasetFields.get(datasetId), true);
            } else if (WIDGET_KPI.equals(type)) {
                Map<String, Object> encode = normalizeEncode(widget);
                if (encode.isEmpty()) {
                    throw new IllegalArgumentException("指标卡组件 " + id + " 缺少 encode");
                }
                widget.put("encode", encode);
                normalizeWidgetFormat(widget);
                validateEncodeFields(id, datasetId, encode, datasetFields.get(datasetId), true);
            } else if (WIDGET_HTML.equals(type)) {
                String html = asString(widget.get("html"));
                if (html != null && html.length() > MAX_HTML_LENGTH) {
                    throw new IllegalArgumentException("组件 " + id + " 的 HTML 内容过大，最大允许 " + MAX_HTML_LENGTH + " 个字符");
                }
                widget.put("html", html == null ? "" : html);
            } else if (WIDGET_TABLE.equals(type)) {
                Map<String, Object> encode = normalizeEncode(widget);
                if (!encode.isEmpty()) {
                    widget.put("encode", encode);
                    validateEncodeFields(id, datasetId, encode, datasetFields.get(datasetId), false);
                }
            }
            result.add(widget);
        }
        return result;
    }

    /**
     * 兼容早期 v1 草稿中的 encode.valueField / encode.series，一次性迁移为 values[]。
     * 这是持久化数据修复，不恢复 v0 dataRef/bindings 协议。
     */
    private Map<String, Object> normalizeEncode(Map<String, Object> widget) {
        Map<String, Object> encode = asMap(widget.get("encode"));
        List<Map<String, Object>> values = asMapList(encode.get("values"));
        if (values.isEmpty()) {
            List<Map<String, Object>> migrated = new ArrayList<>();
            String valueField = asString(encode.get("valueField"));
            if (valueField != null && !valueField.isBlank()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("field", valueField.trim());
                value.put("agg", "sum");
                migrated.add(value);
            } else {
                for (Map<String, Object> series : asMapList(encode.get("series"))) {
                    String field = asString(series.get("field"));
                    if (field == null || field.isBlank()) {
                        continue;
                    }
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("field", field.trim());
                    String name = asString(series.get("name"));
                    if (name != null && !name.isBlank()) {
                        value.put("name", name);
                    }
                    value.put("agg", "sum");
                    migrated.add(value);
                }
            }
            if (!migrated.isEmpty()) {
                encode.put("values", migrated);
            }
        }
        encode.remove("valueField");
        encode.remove("series");
        return encode;
    }

    /**
     * 确保 echarts 组件声明 chart.type；缺省时从 series[0].type 推导。
     */
    private void normalizeWidgetChart(Map<String, Object> widget, Map<String, Object> option) {
        Map<String, Object> chart = asMap(widget.get("chart"));
        String chartType = asString(chart.get("type"));
        if (chartType == null || chartType.isBlank()) {
            for (Map<String, Object> series : asMapList(option.get("series"))) {
                chartType = asString(series.get("type"));
                if (chartType != null && !chartType.isBlank()) {
                    break;
                }
            }
        }
        if (chartType == null || chartType.isBlank()) {
            throw new IllegalArgumentException("图表组件 " + widget.get("id") + " 缺少 chart.type");
        }
        chartType = chartType.trim().toLowerCase();
        if (!CHART_TYPES.contains(chartType)) {
            throw new IllegalArgumentException("不支持的图表类型：" + chartType);
        }
        chart.put("type", chartType);
        widget.put("chart", chart);
        for (Map<String, Object> series : asMapList(option.get("series"))) {
            String seriesType = asString(series.get("type"));
            if (seriesType != null && !seriesType.isBlank() && !SERIES_TYPES.contains(seriesType.trim().toLowerCase())) {
                throw new IllegalArgumentException("不支持的 ECharts series 类型：" + seriesType);
            }
        }
    }

    /**
     * 校验 v1 encode 结构、聚合方式与字段存在性。
     */
    private void validateEncodeFields(
        String widgetId,
        String datasetId,
        Map<String, Object> encode,
        Set<String> fields,
        boolean requireValues
    ) {
        List<Map<String, Object>> values = asMapList(encode.get("values"));
        if (requireValues && values.isEmpty()) {
            throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.values 至少需要一个数值字段");
        }
        Set<String> valueFields = new LinkedHashSet<>();
        for (Map<String, Object> value : values) {
            String field = asString(value.get("field"));
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.values[].field 不能为空");
            }
            String trimmed = field.trim();
            if (!valueFields.add(trimmed)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.values 字段重复：" + trimmed);
            }
            String agg = asString(value.get("agg"));
            if (agg == null || !AGGREGATIONS.contains(agg)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的字段 " + trimmed + " 缺少合法 agg（sum/avg/min/max/count/none）"
                );
            }
        }
        String sortOrder = asString(encode.get("sortOrder"));
        if (sortOrder != null && !Set.of("asc", "desc").contains(sortOrder)) {
            throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.sortOrder 只能是 asc 或 desc");
        }
        List<Map<String, Object>> columns = asMapList(encode.get("columns"));
        for (Map<String, Object> column : columns) {
            String field = asString(column.get("field"));
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.columns[].field 不能为空");
            }
            String label = asString(column.get("label"));
            if (label != null && label.length() > 200) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的列标题过长：" + field);
            }
            Long width = asLong(column.get("width"));
            if (width != null && (width < 40 || width > 2000)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的列宽必须在 40-2000 之间：" + field);
            }
        }
        Long pageSize = asLong(encode.get("pageSize"));
        if (pageSize != null && (pageSize < 5 || pageSize > 1000)) {
            throw new IllegalArgumentException("组件 " + widgetId + " 的 pageSize 必须在 5-1000 之间");
        }
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<String> referenced = new ArrayList<>();
        referenced.add(asString(encode.get("categoryField")));
        referenced.add(asString(encode.get("seriesField")));
        referenced.add(asString(encode.get("sortField")));
        referenced.addAll(valueFields);
        for (Map<String, Object> column : columns) {
            referenced.add(asString(column.get("field")));
        }
        for (String field : referenced) {
            if (field == null || field.isBlank()) {
                continue;
            }
            if (!fields.contains(field.trim())) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 引用了数据集 " + datasetId + " 中不存在的字段：" + field.trim()
                );
            }
        }
    }

    /**
     * KPI 可选的展示格式：unit / precision / prefix / suffix。
     */
    private void normalizeWidgetFormat(Map<String, Object> widget) {
        Map<String, Object> format = asMap(widget.get("format"));
        if (format.isEmpty()) {
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String key : List.of("unit", "prefix", "suffix")) {
            String value = asString(format.get(key));
            if (value != null && !value.isBlank()) {
                if (value.length() > 32) {
                    throw new IllegalArgumentException("组件 " + widget.get("id") + " 的 format." + key + " 过长");
                }
                normalized.put(key, value);
            }
        }
        Long precision = asLong(format.get("precision"));
        if (precision != null) {
            if (precision < 0 || precision > 6) {
                throw new IllegalArgumentException("组件 " + widget.get("id") + " 的 format.precision 必须在 0-6 之间");
            }
            normalized.put("precision", precision);
        }
        if (!normalized.isEmpty()) {
            widget.put("format", normalized);
        }
    }

    private void assertNoLayoutOverlap(List<Map<String, Object>> widgets) {
        for (int i = 0; i < widgets.size(); i++) {
            Map<String, Object> left = asMap(widgets.get(i).get("layout"));
            for (int j = i + 1; j < widgets.size(); j++) {
                Map<String, Object> right = asMap(widgets.get(j).get("layout"));
                boolean overlap =
                    intOrDefault(left.get("x"), 0) < intOrDefault(right.get("x"), 0) + intOrDefault(right.get("w"), 1) &&
                    intOrDefault(left.get("x"), 0) + intOrDefault(left.get("w"), 1) > intOrDefault(right.get("x"), 0) &&
                    intOrDefault(left.get("y"), 0) < intOrDefault(right.get("y"), 0) + intOrDefault(right.get("h"), 1) &&
                    intOrDefault(left.get("y"), 0) + intOrDefault(left.get("h"), 1) > intOrDefault(right.get("y"), 0);
                if (overlap) {
                    throw new IllegalArgumentException(
                        "看板组件布局重叠：" + asString(widgets.get(i).get("id")) + " 与 " + asString(widgets.get(j).get("id"))
                    );
                }
            }
        }
    }

    private Map<String, Object> normalizeLayout(Object raw, String widgetType) {
        Map<String, Object> layout = asMap(raw);
        boolean kpi = WIDGET_KPI.equals(widgetType);
        int defaultWidth = kpi ? 6 : 12;
        int defaultHeight = kpi ? 4 : 8;
        int w = clamp(intOrDefault(layout.get("w"), defaultWidth), 1, GRID_COLUMNS);
        int x = clamp(intOrDefault(layout.get("x"), 0), 0, GRID_COLUMNS - w);
        int y = Math.max(0, intOrDefault(layout.get("y"), 0));
        // 指标卡内容简单，默认高度 4 并限制上限，避免过高留白
        int h = clamp(intOrDefault(layout.get("h"), defaultHeight), 2, kpi ? 8 : 40);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("x", x);
        normalized.put("y", y);
        normalized.put("w", w);
        normalized.put("h", h);
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeEchartsOption(Map<String, Object> option) {
        Map<String, Object> result = option.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(option);
        Object series = result.get("series");
        if (!(series instanceof List<?> list) || list.isEmpty()) {
            result.put("series", new ArrayList<>());
        }
        return result;
    }

    // ----------------------------------------------------------------- filters

    private List<Map<String, Object>> normalizeFilters(
        Object raw,
        Set<String> datasetIds,
        Map<String, String> datasetTypes,
        Map<String, Set<String>> datasetFields
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> filterIds = new LinkedHashSet<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String id = asString(item.get("id"));
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("存在缺少 id 的筛选器");
            }
            if (!filterIds.add(id)) {
                throw new IllegalArgumentException("筛选器 id 重复：" + id);
            }
            Map<String, Object> filter = new LinkedHashMap<>(item);
            filter.put("id", id);
            String type = asString(filter.get("type"));
            if (type == null || !FILTER_TYPES.contains(type)) {
                throw new IllegalArgumentException("不支持的筛选器类型：" + type);
            }
            if (filter.containsKey("datasetId") || filter.containsKey("field") || filter.containsKey("bindings")) {
                throw new IllegalArgumentException("筛选器 " + id + " 使用了已废弃的 datasetId/field/bindings，请改用 targets");
            }
            filter.putIfAbsent("label", id);
            List<Map<String, Object>> targets = normalizeFilterTargets(
                id,
                type,
                filter.get("targets"),
                datasetIds,
                datasetTypes,
                datasetFields
            );
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("筛选器 " + id + " 缺少 targets");
            }
            filter.put("targets", targets);
            Map<String, Object> options = asMap(filter.get("options"));
            if (!options.isEmpty()) {
                Map<String, Object> normalizedOptions = new LinkedHashMap<>();
                normalizedOptions.put("values", asStringList(options.get("values")));
                filter.put("options", normalizedOptions);
            }
            Object defaultValue = filter.get("defaultValue");
            validateDefaultValue(id, type, defaultValue);
            if (
                (FILTER_DATE_RANGE.equals(type) || FILTER_NUMBER_RANGE.equals(type)) && isEmptyFilterValue(defaultValue)
            ) {
                filter.remove("defaultValue");
            }
            result.add(filter);
        }
        return result;
    }

    /**
     * 校验筛选器直接字段，并返回 trim 后的字段名。
     */
    private String validateDirectFilterField(
        String filterId,
        String type,
        String datasetId,
        String field,
        Set<String> datasetIds,
        Map<String, String> datasetTypes,
        Map<String, Set<String>> datasetFields
    ) {
        if (datasetId == null || datasetId.isBlank() || !datasetIds.contains(datasetId)) {
            throw new IllegalArgumentException("筛选器 " + filterId + " 引用了不存在的数据集：" + datasetId);
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("筛选器 " + filterId + " 缺少字段");
        }
        String trimmedField = field.trim();
        if (FILTER_DATE_RANGE.equals(type) && DATASET_METRIC.equals(datasetTypes.get(datasetId))) {
            throw new IllegalArgumentException(
                "筛选器 " + filterId + " 不能直接按指标数据集字段做日期筛选，请改用 metricTime 绑定"
            );
        }
        Set<String> fields = datasetFields.get(datasetId);
        if (fields != null && !fields.isEmpty() && !fields.contains(trimmedField)) {
            throw new IllegalArgumentException(
                "筛选器 " + filterId + " 引用了数据集 " + datasetId + " 中不存在的字段：" + trimmedField
            );
        }
        return trimmedField;
    }

    /**
     * 规范化 v1 filters[].targets[]：支持直接字段（客户端）与 pushdown（服务端）。
     */
    private List<Map<String, Object>> normalizeFilterTargets(
        String filterId,
        String type,
        Object raw,
        Set<String> datasetIds,
        Map<String, String> datasetTypes,
        Map<String, Set<String>> datasetFields
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String datasetId = asString(item.get("datasetId"));
            if (datasetId == null || datasetId.isBlank() || !datasetIds.contains(datasetId)) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 的目标引用了不存在的数据集：" + datasetId);
            }
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("datasetId", datasetId);
            Map<String, Object> pushdown = asMap(item.get("pushdown"));
            String field = asString(item.get("field"));
            if (!pushdown.isEmpty()) {
                if (field != null && !field.isBlank()) {
                    throw new IllegalArgumentException("筛选器 " + filterId + " 的目标不能同时配置 field 与 pushdown");
                }
                target.put("mode", "server");
                target.put("pushdown", normalizePushdown(filterId, type, datasetId, pushdown, datasetTypes));
            } else {
                String trimmedField = validateDirectFilterField(
                    filterId,
                    type,
                    datasetId,
                    field,
                    datasetIds,
                    datasetTypes,
                    datasetFields
                );
                String mode = asString(item.get("mode"));
                if ("server".equalsIgnoreCase(mode)) {
                    if (!DATASET_SQL.equals(datasetTypes.get(datasetId))) {
                        throw new IllegalArgumentException("筛选器 " + filterId + " 的指标数据集字段目标只支持 mode=client，服务端过滤请配置 pushdown");
                    }
                    target.put("mode", "server");
                } else {
                    target.put("mode", "client");
                }
                target.put("field", trimmedField);
            }
            result.add(target);
        }
        return result;
    }

    /**
     * 校验筛选器默认值的基本类型，避免把字符串/数字直接交给日期区间控件。
     */
    private void validateDefaultValue(String filterId, String type, Object value) {
        if (value == null) {
            return;
        }
        if (isEmptyFilterValue(value)) {
            return;
        }
        if (FILTER_MULTI_SELECT.equals(type)) {
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 的默认值必须是数组");
            }
            return;
        }
        if (FILTER_DATE_RANGE.equals(type)) {
            if (!(value instanceof Map<?, ?> map) || !hasOnlyRangeKeys(map, "start", "end")) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 的默认值必须是 {start,end} 对象");
            }
            return;
        }
        if (FILTER_NUMBER_RANGE.equals(type)) {
            if (!(value instanceof Map<?, ?> map) || !hasOnlyRangeKeys(map, "min", "max")) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 的默认值必须是 {min,max} 对象");
            }
        }
    }

    /**
     * 空默认值：null、空字符串、空数组，或只包含空值的区间对象。
     */
    private boolean isEmptyFilterValue(Object value) {
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
            return map.isEmpty() || map.values().stream().allMatch(item -> item == null || String.valueOf(item).isBlank());
        }
        return false;
    }

    private boolean hasOnlyRangeKeys(Map<?, ?> value, String first, String second) {
        for (Object key : value.keySet()) {
            String name = String.valueOf(key);
            if (!first.equals(name) && !second.equals(name)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> normalizePushdown(
        String filterId,
        String filterType,
        String datasetId,
        Map<String, Object> raw,
        Map<String, String> datasetTypes
    ) {
        Map<String, Object> pushdown = new LinkedHashMap<>(raw);
        pushdown.remove("datasetId");
        String kind = asString(pushdown.get("kind"));
        if (kind == null || kind.isBlank()) {
            kind = inferBindingKind(filterType, datasetTypes.get(datasetId));
        }
        String datasetType = datasetTypes.get(datasetId);
        if (DATASET_SQL.equals(datasetType) && !BINDING_SQL_PARAM.equals(kind)) {
            throw new IllegalArgumentException("筛选器 " + filterId + " 的 SQL 数据集目标只支持 kind=sqlParam");
        }
        if (DATASET_METRIC.equals(datasetType) && BINDING_SQL_PARAM.equals(kind)) {
            throw new IllegalArgumentException("筛选器 " + filterId + " 的指标数据集目标不能使用 kind=sqlParam");
        }
        pushdown.put("kind", kind);
        switch (kind) {
            case BINDING_METRIC_TIME -> {
                // 时间筛选，无需附加字段
            }
            case BINDING_METRIC_DIMENSION -> requireFields(pushdown, filterId, "dimensionModelCode", "dimensionFieldName");
            case BINDING_METRIC_FACT -> requireFields(pushdown, filterId, "factFieldName");
            case BINDING_SQL_PARAM -> requireFields(pushdown, filterId, "param");
            default -> throw new IllegalArgumentException("筛选器 " + filterId + " 的绑定类型不支持：" + kind);
        }
        return pushdown;
    }

    private String inferBindingKind(String filterType, String datasetType) {
        if (DATASET_SQL.equals(datasetType)) {
            throw new IllegalArgumentException("SQL 数据集的筛选器绑定必须显式指定 kind=sqlParam 与 param");
        }
        if (FILTER_DATE_RANGE.equals(filterType)) {
            return BINDING_METRIC_TIME;
        }
        if (FILTER_NUMBER_RANGE.equals(filterType)) {
            return BINDING_METRIC_FACT;
        }
        return BINDING_METRIC_DIMENSION;
    }

    private void requireFields(Map<String, Object> binding, String filterId, String... fields) {
        for (String field : fields) {
            String value = asString(binding.get(field));
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 的绑定缺少字段：" + field);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> normalizeRootLayout(Object raw) {
        Map<String, Object> layout = asMap(raw);
        String mode = asString(layout.get("mode"));
        mode = mode != null && LAYOUT_MODE_SCREEN.equalsIgnoreCase(mode.trim()) ? LAYOUT_MODE_SCREEN : LAYOUT_MODE_STANDARD;
        Map<String, Object> screen = asMap(layout.get("screen"));
        int width = clamp(intOrDefault(screen.get("width"), DEFAULT_SCREEN_WIDTH), 640, 7680);
        int height = clamp(intOrDefault(screen.get("height"), DEFAULT_SCREEN_HEIGHT), 480, 4320);
        String theme = normalizeTheme(screen.get("theme"));
        String fitMode = normalizeFitMode(screen.get("fitMode"));
        int autoRefreshSeconds = clamp(intOrDefault(screen.get("autoRefreshSeconds"), 0), 0, 86400);
        Map<String, Object> background = normalizeBackground(screen.get("background"), theme);

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("mode", mode);
        if (LAYOUT_MODE_SCREEN.equals(mode)) {
            Map<String, Object> normalizedScreen = new LinkedHashMap<>();
            normalizedScreen.put("width", width);
            normalizedScreen.put("height", height);
            normalizedScreen.put("fitMode", fitMode);
            normalizedScreen.put("theme", theme);
            normalizedScreen.put("autoRefreshSeconds", autoRefreshSeconds);
            if (!background.isEmpty()) {
                normalizedScreen.put("background", background);
            }
            normalized.put("screen", normalizedScreen);
        }
        return normalized;
    }

    private String normalizeFitMode(Object raw) {
        String fitMode = asString(raw);
        if (fitMode != null && FIT_MODES.contains(fitMode.trim().toLowerCase())) {
            return fitMode.trim().toLowerCase();
        }
        return "stretch";
    }

    private String normalizeTheme(Object raw) {
        String theme = asString(raw);
        return theme != null && THEME_DARK.equalsIgnoreCase(theme.trim()) ? THEME_DARK : THEME_LIGHT;
    }

    private Map<String, Object> normalizeBackground(Object raw, String theme) {
        Map<String, Object> background = asMap(raw);
        if (background.isEmpty()) {
            return new LinkedHashMap<>();
        }
        String defaultColor = THEME_DARK.equals(theme) ? DEFAULT_DARK_BACKGROUND : DEFAULT_LIGHT_BACKGROUND;
        String type = asString(background.get("type"));
        type = type == null ? "color" : type.trim().toLowerCase();
        if ("gradient".equals(type)) {
            Map<String, Object> gradient = asMap(background.get("gradient"));
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("type", "gradient");
            Map<String, Object> normalizedGradient = new LinkedHashMap<>();
            normalizedGradient.put("from", normalizeColor(gradient.get("from"), "#0f172a"));
            normalizedGradient.put("to", normalizeColor(gradient.get("to"), "#1e293b"));
            normalizedGradient.put("angle", clamp(intOrDefault(gradient.get("angle"), 135), 0, 360));
            normalized.put("gradient", normalizedGradient);
            return normalized;
        }
        if ("image".equals(type)) {
            String imagePath = asString(background.get("imagePath"));
            if (imagePath != null && !imagePath.isBlank() && imagePath.trim().length() <= MAX_BACKGROUND_PATH_LENGTH) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("type", "image");
                normalized.put("imagePath", imagePath.trim());
                String contentType = asString(background.get("imageContentType"));
                if (contentType != null && !contentType.isBlank()) {
                    normalized.put("imageContentType", contentType.trim());
                }
                return normalized;
            }
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("type", "color");
        normalized.put("color", normalizeColor(background.get("color"), defaultColor));
        return normalized;
    }

    private String normalizeColor(Object raw, String fallback) {
        String color = asString(raw);
        if (color == null) {
            return fallback;
        }
        String trimmed = color.trim();
        return COLOR_PATTERN.matcher(trimmed).matches() ? trimmed : fallback;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
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

    static Long asLong(Object raw) {
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

    private static void assertWithinLimit(String label, int count, int max) {
        if (count > max) {
            throw new IllegalArgumentException(label + "数量超过限制：" + count + " > " + max);
        }
    }

    private static int intOrDefault(Object raw, int defaultValue) {        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
