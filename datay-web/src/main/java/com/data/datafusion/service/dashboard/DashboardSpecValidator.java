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
    public static final int SPEC_VERSION = 2;

    public static final String WIDGET_ECHARTS = "echarts";
    public static final String WIDGET_HTML = "html";
    public static final String WIDGET_TABLE = "table";
    public static final String WIDGET_KPI = "kpi";
    public static final String WIDGET_PIVOT = "pivot";
    public static final String WIDGET_TITLE = "title";
    public static final String WIDGET_CLOCK = "clock";
    public static final String WIDGET_CONTAINER = "container";

    private static final Set<String> WIDGET_TYPES = Set.of(
        WIDGET_ECHARTS,
        WIDGET_HTML,
        WIDGET_TABLE,
        WIDGET_KPI,
        WIDGET_PIVOT,
        WIDGET_TITLE,
        WIDGET_CLOCK,
        WIDGET_CONTAINER
    );

    /** 不绑定数据集的组件：说明文本、标题栏、日期时间。 */
    private static final Set<String> DATASETLESS_WIDGET_TYPES = Set.of(
        WIDGET_HTML,
        WIDGET_TITLE,
        WIDGET_CLOCK,
        WIDGET_CONTAINER
    );

    private static final String LAYOUT_MODE_STANDARD = "standard";
    private static final String LAYOUT_MODE_SCREEN = "screen";

    private static final Set<String> FIT_MODES = Set.of("contain", "stretch", "cover");
    private static final Set<String> CANVAS_UNITS = Set.of("grid", "px");
    private static final String CANVAS_UNIT_GRID = "grid";
    private static final String CANVAS_UNIT_PX = "px";
    private static final int MAX_CANVAS_PADDING = 64;
    private static final int MAX_CANVAS_GAP = 48;
    private static final int MAX_WIDGET_Z_INDEX = 999;
    private static final Set<String> THEME_PRESETS = Set.of("tech-blue", "tech-cyan", "gov-red", "ink-dark", "custom");
    private static final Set<String> FONT_FAMILIES = Set.of("system", "number-digital", "number-condensed");
    private static final Set<String> CARD_PRESETS = Set.of("none", "tech-corner", "tech-frame", "bracket");
    private static final Set<String> FILL_TYPES = Set.of("none", "color", "gradient");
    private static final Set<String> BORDER_STYLES = Set.of("solid", "dashed", "dotted");
    private static final Set<String> TITLE_ALIGNS = Set.of("left", "center", "right");
    private static final Set<String> TITLE_DECORATIONS = Set.of("none", "underline", "gradient-underline", "left-bar");
    private static final Set<String> KPI_LAYOUTS = Set.of("label-top", "label-left", "value-only");
    private static final Set<String> CHART_STYLE_PRESETS = Set.of("tech", "minimal", "dark");
    private static final Set<String> LEGEND_POSITIONS = Set.of("top", "bottom", "left", "right");
    private static final int MAX_AXIS_TITLE_LENGTH = 30;
    private static final Set<String> TITLE_FRAMES = Set.of("none", "tech-frame", "bracket");
    private static final Set<String> TITLE_ALIGNS_PROPS = Set.of("left", "center", "right");
    private static final Set<String> CLOCK_FORMATS = Set.of("date", "datetime", "time", "date-week");
    private static final Pattern ICON_NAME_PATTERN = Pattern.compile("^[a-z0-9-]{1,40}$");
    private static final int MAX_STYLE_JSON_LENGTH = 4096;
    private static final int MAX_THEME_PALETTE = 12;
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

    /**
     * v2 图表类型。
     *
     * <p>P0 覆盖基础图表；P1 追加 scatter/radar/funnel/heatmap/treemap。
     */
    private static final Set<String> CHART_TYPES = Set.of(
        "bar",
        "line",
        "area",
        "pie",
        "donut",
        "gauge",
        "scatter",
        "radar",
        "funnel",
        "heatmap",
        "treemap",
        "waterfall",
        "sankey",
        "graph",
        "boxplot",
        "map"
    );

    private static final Set<String> CHART_DIMENSION_ROLES = Set.of("x", "series");

    private static final Set<String> MAP_REGIONS = Set.of("china");
    private static final Set<String> MAP_LAYER_KINDS = Set.of("region", "effectScatter", "lines");
    private static final int MAX_MAP_LAYERS = 4;

    private static final Set<String> NODE_LINK_CHART_TYPES = Set.of("sankey", "graph");

    private static final Set<String> PIVOT_DIMENSION_ROLES = Set.of("row", "column");

    private static final Set<String> ECHARTS_OPTION_SERIES_TYPES = Set.of(
        "bar",
        "line",
        "area",
        "pie",
        "gauge",
        "scatter",
        "radar",
        "funnel",
        "heatmap",
        "treemap",
        "graph",
        "sankey",
        "boxplot",
        "waterfall",
        "map"
    );

    private static final Map<String, String> CHART_SUBTYPES = Map.of(
        "grouped",
        "grouped",
        "stacked",
        "stacked",
        "stackedpercent",
        "stackedPercent",
        "smooth",
        "smooth",
        "step",
        "step"
    );

    private static final Set<String> CHART_ORIENTATIONS = Set.of("vertical", "horizontal");

    private static final Set<String> CARTESIAN_SERIES_TYPES = Set.of("bar", "line", "area");

    private static final Set<String> DIMENSION_ROLES = Set.of("x", "series", "row", "column");

    private static final Set<String> MEASURE_ROLES = Set.of("y", "value", "size");

    private static final Set<String> TRANSFORM_TYPES = Set.of("percentOfTotal");

    private static final Set<String> FORMAT_RULE_OPERATORS = Set.of(
        "gt",
        "gte",
        "lt",
        "lte",
        "eq",
        "neq",
        "between"
    );

    private static final int MAX_FORMAT_RULES = 50;


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
        Map<String, Object> layout = normalizeRootLayout(root.get("layout"));
        root.put("layout", layout);
        CanvasMetrics canvas = canvasMetrics(layout);

        Set<String> datasetIds = new LinkedHashSet<>();
        Map<String, String> datasetTypes = new LinkedHashMap<>();
        Map<String, Set<String>> datasetFields = new LinkedHashMap<>();
        List<Map<String, Object>> datasets = normalizeDatasets(root.get("datasets"), datasetIds, datasetTypes, datasetFields);
        root.put("datasets", datasets);
        if (datasets.isEmpty()) {
            throw new IllegalArgumentException("看板至少需要定义一个数据集");
        }
        assertWithinLimit("数据集", datasets.size(), MAX_DATASETS);

        List<Map<String, Object>> widgets = normalizeWidgets(root.get("widgets"), datasetIds, datasetFields, canvas);
        root.put("widgets", widgets);
        if (widgets.isEmpty()) {
            throw new IllegalArgumentException("看板至少需要一个图表或内容组件");
        }
        assertWithinLimit("组件", widgets.size(), MAX_WIDGETS);
        assertNoLayoutOverlap(widgets, canvas);

        List<Map<String, Object>> filters = normalizeFilters(root.get("filters"), datasetIds, datasetTypes, datasetFields);
        assertWithinLimit("筛选器", filters.size(), MAX_FILTERS);
        root.put("filters", filters);
        root.put("version", SPEC_VERSION);
        return root;
    }

    /**
     * 当前仅支持 v2；不再读取无 version 的历史 spec。
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
        Map<String, Set<String>> datasetFields,
        CanvasMetrics canvas
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
            String rawParentId = trimToNull(asString(widget.get("parentId")));
            widget.put("layout", normalizeLayout(widget.get("layout"), type, canvas, rawParentId != null));
            int zIndex = clamp(intOrDefault(widget.get("zIndex"), 0), 0, MAX_WIDGET_Z_INDEX);
            if (zIndex > 0) {
                widget.put("zIndex", zIndex);
            } else {
                widget.remove("zIndex");
            }
            Map<String, Object> style = normalizeWidgetStyle(id, widget.get("style"));
            if (style.isEmpty()) {
                widget.remove("style");
            } else {
                widget.put("style", style);
            }

            String datasetId = asString(widget.get("datasetId"));
            if (DATASETLESS_WIDGET_TYPES.contains(type)) {
                widget.remove("datasetId");
            } else if (datasetId == null || datasetId.isBlank()) {
                throw new IllegalArgumentException("组件 " + id + " 缺少 datasetId");
            } else {
                if (!datasetIds.contains(datasetId)) {
                    throw new IllegalArgumentException("组件 " + id + " 引用了不存在的数据集：" + datasetId);
                }
                widget.put("datasetId", datasetId);
            }

            if (WIDGET_ECHARTS.equals(type)) {
                Map<String, Object> option = asMap(widget.get("echartsOption"));
                widget.put("echartsOption", normalizeEchartsOption(option));
                Map<String, Object> encode = normalizeEncode(widget, type);
                if (encode.isEmpty()) {
                    throw new IllegalArgumentException("图表组件 " + id + " 缺少 encode");
                }
                widget.put("encode", encode);
                Map<String, Object> chart = normalizeWidgetChart(widget, encode, datasetFields);
                validateChartEncoding(id, chart, encode);
                validateEncodeFields(id, datasetId, encode, datasetFields.get(datasetId), true);
            } else if (WIDGET_KPI.equals(type)) {
                Map<String, Object> encode = normalizeEncode(widget, type);
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
            } else if (WIDGET_TITLE.equals(type) || WIDGET_CLOCK.equals(type)) {
                widget.put("props", normalizeWidgetProps(type, widget.get("props")));
            } else if (WIDGET_CONTAINER.equals(type)) {
                // 容器只负责分组与面板皮肤，不绑定数据，也不需要 encode。
                widget.remove("encode");
                widget.remove("chart");
                widget.remove("echartsOption");
            } else if (WIDGET_TABLE.equals(type)) {
                Map<String, Object> encode = normalizeEncode(widget, type);
                if (!encode.isEmpty()) {
                    widget.put("encode", encode);
                    validateEncodeFields(id, datasetId, encode, datasetFields.get(datasetId), false);
                }
            } else if (WIDGET_PIVOT.equals(type)) {
                Map<String, Object> encode = normalizeEncode(widget, type);
                if (encode.isEmpty()) {
                    throw new IllegalArgumentException("透视表组件 " + id + " 缺少 encode");
                }
                widget.put("encode", encode);
                normalizeWidgetFormat(widget);
                validatePivotEncoding(id, encode);
                validateEncodeFields(id, datasetId, encode, datasetFields.get(datasetId), true);
            }
            if (widget.containsKey("formatRules")) {
                widget.put("formatRules", normalizeFormatRules(id, widget.get("formatRules"), datasetFields.get(datasetId)));
            }
            result.add(widget);
        }
        validateContainerParents(result);
        return result;
    }

    /**
     * 容器父子关系：parentId 必须指向同看板内的容器；容器不能再嵌套容器。
     */
    private void validateContainerParents(List<Map<String, Object>> widgets) {
        Map<String, String> types = new LinkedHashMap<>();
        for (Map<String, Object> widget : widgets) {
            types.put(asString(widget.get("id")), asString(widget.get("type")));
        }
        for (Map<String, Object> widget : widgets) {
            String id = asString(widget.get("id"));
            String type = asString(widget.get("type"));
            String parentId = trimToNull(asString(widget.get("parentId")));
            if (parentId == null) {
                widget.remove("parentId");
                continue;
            }
            if (parentId.equals(id)) {
                throw new IllegalArgumentException("组件 " + id + " 不能把自己设为容器");
            }
            String parentType = types.get(parentId);
            if (parentType == null) {
                throw new IllegalArgumentException("组件 " + id + " 引用了不存在的容器：" + parentId);
            }
            if (!WIDGET_CONTAINER.equals(parentType)) {
                throw new IllegalArgumentException("组件 " + id + " 的 parentId 必须指向容器组件：" + parentId);
            }
            if (WIDGET_CONTAINER.equals(type)) {
                throw new IllegalArgumentException("容器组件 " + id + " 不能再嵌套容器");
            }
            widget.put("parentId", parentId);
        }
    }

    /**
     * 大屏标题栏 / 日期时间组件的展示内容。
     */
    private Map<String, Object> normalizeWidgetProps(String type, Object raw) {
        Map<String, Object> props = asMap(raw);
        Map<String, Object> result = new LinkedHashMap<>();
        if (WIDGET_TITLE.equals(type)) {
            String text = trimToNull(asString(props.get("text")));
            if (text == null) {
                throw new IllegalArgumentException("标题栏组件缺少 props.text");
            }
            if (text.length() > 120) {
                throw new IllegalArgumentException("标题栏文本不能超过 120 个字符");
            }
            result.put("text", text);
            String subText = trimToNull(asString(props.get("subText")));
            if (subText != null && subText.length() <= 60) {
                result.put("subText", subText);
            }
            String align = trimToNull(asString(props.get("align")));
            result.put("align", align != null && TITLE_ALIGNS_PROPS.contains(align.toLowerCase()) ? align.toLowerCase() : "center");
            String frame = trimToNull(asString(props.get("frame")));
            result.put("frame", frame != null && TITLE_FRAMES.contains(frame.toLowerCase()) ? frame.toLowerCase() : "none");
            return result;
        }
        String format = trimToNull(asString(props.get("format")));
        result.put("format", format != null && CLOCK_FORMATS.contains(format.toLowerCase()) ? format.toLowerCase() : "date-week");
        return result;
    }

    /**
     * v2 encode：dimensions[] + measures[]。
     *
     * <p>v1 的 categoryField / values / seriesField 不再兼容，避免新旧两套映射同时存在。
     */
    private Map<String, Object> normalizeEncode(Map<String, Object> widget, String widgetType) {
        String id = asString(widget.get("id"));
        Map<String, Object> encode = asMap(widget.get("encode"));
        if (encode.isEmpty()) {
            return encode;
        }
        for (String deprecated : List.of("categoryField", "values", "seriesField", "valueField", "series")) {
            if (encode.containsKey(deprecated)) {
                throw new IllegalArgumentException(
                    "组件 " + id + " 使用了已废弃的 encode." + deprecated + "，请改用 encode.dimensions / encode.measures"
                );
            }
        }
        String defaultDimensionRole = WIDGET_PIVOT.equals(widgetType) ? "row" : "x";
        List<Map<String, Object>> dimensions = normalizeDimensions(id, encode.get("dimensions"), defaultDimensionRole);
        String defaultMeasureRole = WIDGET_KPI.equals(widgetType) || WIDGET_PIVOT.equals(widgetType) ? "value" : "y";
        List<Map<String, Object>> measures = normalizeMeasures(id, encode.get("measures"), defaultMeasureRole);
        if (!dimensions.isEmpty()) {
            encode.put("dimensions", dimensions);
        } else {
            encode.remove("dimensions");
        }
        if (!measures.isEmpty()) {
            encode.put("measures", measures);
        } else {
            encode.remove("measures");
        }

        String sortOrder = asString(encode.get("sortOrder"));
        if (sortOrder != null && !sortOrder.isBlank()) {
            sortOrder = sortOrder.trim().toLowerCase();
            if (!Set.of("asc", "desc").contains(sortOrder)) {
                throw new IllegalArgumentException("组件 " + id + " 的 encode.sortOrder 只能是 asc 或 desc");
            }
            encode.put("sortOrder", sortOrder);
        }
        Long limit = asLong(encode.get("limit"));
        if (limit != null) {
            if (limit < 1 || limit > 10000) {
                throw new IllegalArgumentException("组件 " + id + " 的 encode.limit 必须在 1-10000 之间");
            }
            encode.put("limit", limit);
        }
        Long pageSize = asLong(encode.get("pageSize"));
        if (pageSize != null) {
            if (pageSize < 5 || pageSize > 1000) {
                throw new IllegalArgumentException("组件 " + id + " 的 encode.pageSize 必须在 5-1000 之间");
            }
            encode.put("pageSize", pageSize);
        }
        if (encode.containsKey("groupBy")) {
            Object rawGroupBy = encode.get("groupBy");
            encode.put("groupBy", rawGroupBy instanceof Boolean ? rawGroupBy : Boolean.parseBoolean(String.valueOf(rawGroupBy)));
        } else if (WIDGET_ECHARTS.equals(widgetType)) {
            encode.put("groupBy", true);
        }
        List<Map<String, Object>> transforms = normalizeTransforms(id, encode.get("transform"), measures);
        if (!transforms.isEmpty()) {
            encode.put("transform", transforms);
        } else {
            encode.remove("transform");
        }
        List<Map<String, Object>> columns = asMapList(encode.get("columns"));
        if (!columns.isEmpty()) {
            encode.put("columns", columns);
        } else {
            encode.remove("columns");
        }
        return encode;
    }

    private List<Map<String, Object>> normalizeDimensions(String widgetId, Object raw, String defaultRole) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> fields = new LinkedHashSet<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String field = trimToNull(asString(item.get("field")));
            if (field == null) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.dimensions[].field 不能为空");
            }
            if (!fields.add(field)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的 encode.dimensions 字段重复：" + field + "，同一字段不能同时作为多个维度"
                );
            }
            String role = trimToNull(asString(item.get("role")));
            role = role == null ? defaultRole : role.toLowerCase();
            if (!DIMENSION_ROLES.contains(role)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的维度 " + field + " 缺少合法 role（x/series/row/column）"
                );
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("field", field);
            normalized.put("role", role);
            String name = trimToNull(asString(item.get("name")));
            if (name != null) {
                normalized.put("name", name);
            }
            result.add(normalized);
        }
        return result;
    }

    private List<Map<String, Object>> normalizeMeasures(String widgetId, Object raw, String defaultRole) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> fields = new LinkedHashSet<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String field = trimToNull(asString(item.get("field")));
            if (field == null) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.measures[].field 不能为空");
            }
            if (!fields.add(field)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的 encode.measures 字段重复：" + field + "，同一字段不能同时作为多个度量"
                );
            }
            String agg = trimToNull(asString(item.get("agg")));
            agg = agg == null ? null : agg.toLowerCase();
            if (agg == null || !AGGREGATIONS.contains(agg)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的字段 " + field + " 缺少合法 agg（sum/avg/min/max/count/none）"
                );
            }
            String role = trimToNull(asString(item.get("role")));
            role = role == null ? defaultRole : role.toLowerCase();
            if (!MEASURE_ROLES.contains(role)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的度量 " + field + " 缺少合法 role（y/value）"
                );
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("field", field);
            normalized.put("agg", agg.trim().toLowerCase());
            normalized.put("role", role);
            String name = trimToNull(asString(item.get("name")));
            if (name != null) {
                normalized.put("name", name);
            }
            if (item.get("format") instanceof Map<?, ?>) {
                normalized.put("format", item.get("format"));
            }
            result.add(normalized);
        }
        return result;
    }

    private List<Map<String, Object>> normalizeTransforms(String widgetId, Object raw, List<Map<String, Object>> measures) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> measureFields = new LinkedHashSet<>();
        for (Map<String, Object> measure : measures) {
            String field = asString(measure.get("field"));
            if (field != null) {
                measureFields.add(field);
            }
        }
        for (Map<String, Object> item : asMapList(raw)) {
            String type = trimToNull(asString(item.get("type")));
            if (type == null) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.transform[].type 不能为空");
            }
            type = type.toLowerCase();
            if (!TRANSFORM_TYPES.contains(type)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 不支持的数据变换：" + type);
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("type", type);
            String measure = trimToNull(asString(item.get("measure")));
            if (measure != null) {
                if (!measureFields.isEmpty() && !measureFields.contains(measure)) {
                    throw new IllegalArgumentException(
                        "组件 " + widgetId + " 的数据变换引用了未声明的度量：" + measure
                    );
                }
                normalized.put("measure", measure);
            }
            result.add(normalized);
        }
        return result;
    }

    /**
     * v2 图表声明：chart.type 是唯一权威来源，echartsOption 只负责样式覆盖。
     */
    private Map<String, Object> normalizeWidgetChart(
        Map<String, Object> widget,
        Map<String, Object> encode,
        Map<String, Set<String>> datasetFields
    ) {
        String widgetId = asString(widget.get("id"));
        Map<String, Object> chart = asMap(widget.get("chart"));
        String chartType = trimToNull(asString(chart.get("type")));
        if (chartType == null) {
            for (Map<String, Object> series : asMapList(chart.get("series"))) {
                chartType = trimToNull(asString(series.get("type")));
                if (chartType != null) {
                    break;
                }
            }
        }
        if (chartType == null) {
            throw new IllegalArgumentException("图表组件 " + widgetId + " 缺少 chart.type");
        }
        chartType = chartType.toLowerCase();
        if (!CHART_TYPES.contains(chartType)) {
            throw new IllegalArgumentException("不支持的图表类型：" + chartType);
        }
        chart.put("type", chartType);

        if ("map".equals(chartType)) {
            String map = trimToNull(asString(chart.get("map")));
            map = map == null ? "china" : map.toLowerCase();
            if (!MAP_REGIONS.contains(map)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 不支持的地图区域：" + map);
            }
            chart.put("map", map);
            List<Map<String, Object>> layers = normalizeMapLayers(
                widgetId,
                chart.get("layers"),
                asString(widget.get("datasetId")),
                datasetFields
            );
            if (layers.isEmpty()) {
                chart.remove("layers");
            } else {
                chart.put("layers", layers);
            }
        } else {
            chart.remove("map");
            chart.remove("layers");
        }

        String subtype = trimToNull(asString(chart.get("subtype")));
        if (subtype != null) {
            String canonicalSubtype = CHART_SUBTYPES.get(subtype.toLowerCase());
            if (canonicalSubtype == null) {
                throw new IllegalArgumentException("组件 " + widgetId + " 不支持图表子类型：" + subtype);
            }
            chart.put("subtype", canonicalSubtype);
        } else {
            chart.remove("subtype");
        }
        String orientation = trimToNull(asString(chart.get("orientation")));
        if (orientation != null) {
            orientation = orientation.toLowerCase();
            if (!CHART_ORIENTATIONS.contains(orientation)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的图表方向只能是 vertical 或 horizontal");
            }
            chart.put("orientation", orientation);
        } else if ("bar".equals(chartType)) {
            chart.put("orientation", "vertical");
        } else {
            chart.remove("orientation");
        }

        List<Map<String, Object>> series = normalizeChartSeries(widgetId, chart.get("series"), chartType, encode);
        if (!series.isEmpty()) {
            chart.put("series", series);
        } else {
            chart.remove("series");
        }
        widget.put("chart", chart);

        // echartsOption 允许保留样式模板，但 series 类型不能超出当前渲染器能力。
        for (Map<String, Object> seriesOption : asMapList(asMap(widget.get("echartsOption")).get("series"))) {
            String seriesType = trimToNull(asString(seriesOption.get("type")));
            if (seriesType != null && !ECHARTS_OPTION_SERIES_TYPES.contains(seriesType.toLowerCase())) {
                throw new IllegalArgumentException("不支持的 ECharts series 类型：" + seriesType);
            }
        }
        return chart;
    }

    /**
     * 地图多图层：region（区域填色）/ effectScatter（涟漪点）/ lines（飞线）。
     *
     * <p>region 最多一个且必须位于第一位；图层可各自引用 datasets 中的数据集，
     * 未指定时沿用组件自身的数据集。字段存在性按数据集字段缓存校验。
     */
    private List<Map<String, Object>> normalizeMapLayers(
        String widgetId,
        Object raw,
        String widgetDatasetId,
        Map<String, Set<String>> datasetFields
    ) {
        List<Map<String, Object>> layers = new ArrayList<>();
        boolean hasRegion = false;
        for (Map<String, Object> item : asMapList(raw)) {
            if (layers.size() >= MAX_MAP_LAYERS) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的地图图层不能超过 " + MAX_MAP_LAYERS + " 个");
            }
            String kind = trimToNull(asString(item.get("kind")));
            if (kind == null || !MAP_LAYER_KINDS.contains(kind)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的地图图层类型不合法：" + kind);
            }
            String datasetId = trimToNull(asString(item.get("datasetId")));
            String effectiveDatasetId = datasetId == null ? widgetDatasetId : datasetId;
            if (datasetId != null && !datasetFields.containsKey(datasetId)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的地图图层引用了不存在的数据集：" + datasetId);
            }
            Set<String> fields = datasetFields.getOrDefault(effectiveDatasetId, Set.of());

            Map<String, Object> layer = new LinkedHashMap<>();
            layer.put("kind", kind);
            if (datasetId != null) {
                layer.put("datasetId", datasetId);
            }
            if ("region".equals(kind)) {
                if (hasRegion || !layers.isEmpty()) {
                    throw new IllegalArgumentException("组件 " + widgetId + " 的 region 图层只能有一个且必须位于第一位");
                }
                hasRegion = true;
                layer.put("valueField", requireLayerField(widgetId, kind, item, "valueField", fields));
                String agg = trimToNull(asString(item.get("agg")));
                agg = agg == null ? "sum" : agg.toLowerCase();
                if (!AGGREGATIONS.contains(agg)) {
                    throw new IllegalArgumentException("组件 " + widgetId + " 的地图区域聚合方式不合法：" + agg);
                }
                layer.put("agg", agg);
            } else if ("effectScatter".equals(kind)) {
                layer.put("lngField", requireLayerField(widgetId, kind, item, "lngField", fields));
                layer.put("latField", requireLayerField(widgetId, kind, item, "latField", fields));
                putOptionalLayerField(widgetId, layer, "nameField", item, fields);
                putOptionalLayerField(widgetId, layer, "valueField", item, fields);
                List<Integer> symbolSize = normalizeSymbolSize(item.get("symbolSize"));
                if (!symbolSize.isEmpty()) {
                    layer.put("symbolSize", symbolSize);
                }
                String color = normalizeColor(item.get("color"), null);
                if (color != null) {
                    layer.put("color", color);
                }
            } else {
                layer.put("fromLngField", requireLayerField(widgetId, kind, item, "fromLngField", fields));
                layer.put("fromLatField", requireLayerField(widgetId, kind, item, "fromLatField", fields));
                layer.put("toLngField", requireLayerField(widgetId, kind, item, "toLngField", fields));
                layer.put("toLatField", requireLayerField(widgetId, kind, item, "toLatField", fields));
                putOptionalLayerField(widgetId, layer, "valueField", item, fields);
                String color = normalizeColor(item.get("color"), null);
                if (color != null) {
                    layer.put("color", color);
                }
                putDoubleIfPresent(layer, "curveness", item.get("curveness"), 0, 1);
                Map<String, Object> effect = asMap(item.get("effect"));
                if (!effect.isEmpty()) {
                    Map<String, Object> normalizedEffect = new LinkedHashMap<>();
                    if (effect.containsKey("show")) {
                        normalizedEffect.put("show", Boolean.parseBoolean(String.valueOf(effect.get("show"))));
                    }
                    putIntIfPresent(normalizedEffect, "period", effect.get("period"), 1, 20);
                    putDoubleIfPresent(normalizedEffect, "trailLength", effect.get("trailLength"), 0, 1);
                    if (!normalizedEffect.isEmpty()) {
                        layer.put("effect", normalizedEffect);
                    }
                }
            }
            layers.add(layer);
        }
        return layers;
    }

    private String requireLayerField(
        String widgetId,
        String kind,
        Map<String, Object> item,
        String key,
        Set<String> fields
    ) {
        String field = trimToNull(asString(item.get(key)));
        if (field == null) {
            throw new IllegalArgumentException("组件 " + widgetId + " 的地图图层 " + kind + " 缺少字段：" + key);
        }
        assertLayerFieldExists(widgetId, key, field, fields);
        return field;
    }

    private void putOptionalLayerField(
        String widgetId,
        Map<String, Object> layer,
        String key,
        Map<String, Object> item,
        Set<String> fields
    ) {
        String field = trimToNull(asString(item.get(key)));
        if (field == null) {
            return;
        }
        assertLayerFieldExists(widgetId, key, field, fields);
        layer.put(key, field);
    }

    private void assertLayerFieldExists(String widgetId, String key, String field, Set<String> fields) {
        if (fields != null && !fields.isEmpty() && !fields.contains(field)) {
            throw new IllegalArgumentException(
                "组件 " + widgetId + " 的地图图层引用了不存在的字段：" + key + "=" + field
            );
        }
    }

    private List<Integer> normalizeSymbolSize(Object raw) {
        List<Integer> sizes = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Double value = asDouble(item);
                if (value != null) {
                    sizes.add(clamp((int) Math.round(value), 2, 80));
                }
                if (sizes.size() >= 2) {
                    break;
                }
            }
        } else {
            Double value = asDouble(raw);
            if (value != null) {
                sizes.add(clamp((int) Math.round(value), 2, 80));
            }
        }
        return sizes;
    }

    private List<Map<String, Object>> normalizeChartSeries(
        String widgetId,
        Object raw,
        String chartType,
        Map<String, Object> encode
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> measureFields = new LinkedHashSet<>();
        for (Map<String, Object> measure : asMapList(encode.get("measures"))) {
            String field = asString(measure.get("field"));
            if (field != null) {
                measureFields.add(field);
            }
        }
        boolean cartesian = Set.of("bar", "line", "area").contains(chartType);
        for (Map<String, Object> item : asMapList(raw)) {
            String measure = trimToNull(asString(item.get("measure")));
            if (measure == null) {
                measure = trimToNull(asString(item.get("field")));
            }
            if (measure == null) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 chart.series[].measure 不能为空");
            }
            if (!measureFields.contains(measure)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的 chart.series 引用了未声明的 measure：" + measure
                );
            }
            String seriesType = trimToNull(asString(item.get("type")));
            seriesType = seriesType == null ? chartType : seriesType.toLowerCase();
            if (!CARTESIAN_SERIES_TYPES.contains(seriesType)) {
                throw new IllegalArgumentException("不支持的 chart.series 类型：" + seriesType);
            }
            if (!cartesian) {
                throw new IllegalArgumentException("组件 " + widgetId + " 只有柱状/折线/面积图支持 chart.series");
            }
            String axis = trimToNull(asString(item.get("axis")));
            axis = axis == null ? "left" : axis.toLowerCase();
            if (!Set.of("left", "right").contains(axis)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 chart.series[].axis 只能是 left 或 right");
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("measure", measure);
            normalized.put("type", seriesType);
            normalized.put("axis", axis);
            String name = trimToNull(asString(item.get("name")));
            if (name != null) {
                normalized.put("name", name);
            }
            String stack = trimToNull(asString(item.get("stack")));
            if (stack != null) {
                normalized.put("stack", stack);
            }
            if (item.containsKey("smooth")) {
                normalized.put("smooth", Boolean.parseBoolean(String.valueOf(item.get("smooth"))));
            }
            if (item.containsKey("step")) {
                Object step = item.get("step");
                if (step instanceof Boolean || step instanceof String) {
                    normalized.put("step", step);
                }
            }
            result.add(normalized);
        }
        return result;
    }

    private void validateChartEncoding(String widgetId, Map<String, Object> chart, Map<String, Object> encode) {
        String chartType = asString(chart.get("type"));
        List<Map<String, Object>> dimensions = asMapList(encode.get("dimensions"));
        List<Map<String, Object>> measures = asMapList(encode.get("measures"));
        if (measures.isEmpty()) {
            throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.measures 至少需要一个度量字段");
        }
        for (Map<String, Object> dimension : dimensions) {
            String role = asString(dimension.get("role"));
            if (!CHART_DIMENSION_ROLES.contains(role)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的图表维度 role 只能是 x 或 series");
            }
        }
        boolean hasX = dimensions.stream().anyMatch(dimension -> "x".equals(asString(dimension.get("role"))));
        boolean hasSeries = dimensions.stream().anyMatch(dimension -> "series".equals(asString(dimension.get("role"))));
        if (!"gauge".equals(chartType) && !hasX) {
            throw new IllegalArgumentException("组件 " + widgetId + " 缺少 role=x 的维度字段");
        }
        if (("heatmap".equals(chartType) || NODE_LINK_CHART_TYPES.contains(chartType)) && !hasSeries) {
            String chartLabel = "heatmap".equals(chartType) ? "热力图" : "sankey".equals(chartType) ? "桑基图" : "关系图";
            throw new IllegalArgumentException("组件 " + widgetId + " 的" + chartLabel + "需要 role=series 的维度字段");
        }
        if ("scatter".equals(chartType)) {
            boolean hasY = measures.stream().anyMatch(measure -> "y".equals(asString(measure.get("role"))));
            if (!hasY) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的散点图至少需要一个 role=y 的度量字段");
            }
        }
        String subtype = asString(chart.get("subtype"));
        if (subtype != null && !subtype.isBlank()) {
            switch (subtype) {
                case "grouped", "stacked", "stackedPercent" -> {
                    if (!"bar".equals(chartType) && !"area".equals(chartType)) {
                        throw new IllegalArgumentException("组件 " + widgetId + " 的子类型 " + subtype + " 仅支持柱状图/面积图");
                    }
                }
                case "smooth", "step" -> {
                    if (!"line".equals(chartType) && !"area".equals(chartType)) {
                        throw new IllegalArgumentException("组件 " + widgetId + " 的子类型 " + subtype + " 仅支持折线图/面积图");
                    }
                }
                default -> {
                    // normalizeWidgetChart 已校验枚举
                }
            }
        }
        if ("horizontal".equals(asString(chart.get("orientation"))) && !"bar".equals(chartType)) {
            throw new IllegalArgumentException("组件 " + widgetId + " 只有柱状图支持横向展示");
        }
    }

    private List<Map<String, Object>> normalizeFormatRules(
        String widgetId,
        Object raw,
        Set<String> fields
    ) {
        List<Map<String, Object>> rules = asMapList(raw);
        if (rules.size() > MAX_FORMAT_RULES) {
            throw new IllegalArgumentException("组件 " + widgetId + " 的条件格式规则不能超过 " + MAX_FORMAT_RULES + " 条");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : rules) {
            String operator = trimToNull(asString(item.get("operator")));
            operator = operator == null ? null : operator.toLowerCase();
            if (operator == null || !FORMAT_RULE_OPERATORS.contains(operator)) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的条件格式 operator 不合法");
            }
            Double value = asDouble(item.get("value"));
            Double value2 = asDouble(item.get("value2"));
            if (value == null) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的条件格式缺少 value");
            }
            if ("between".equals(operator) && value2 == null) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 between 条件格式缺少 value2");
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            String id = trimToNull(asString(item.get("id")));
            if (id != null) {
                normalized.put("id", id);
            }
            String field = trimToNull(asString(item.get("field")));
            if (field != null) {
                if (fields != null && !fields.isEmpty() && !fields.contains(field)) {
                    throw new IllegalArgumentException(
                        "组件 " + widgetId + " 的条件格式引用了不存在的字段：" + field
                    );
                }
                normalized.put("field", field);
            }
            normalized.put("operator", operator);
            normalized.put("value", value);
            if (value2 != null) {
                normalized.put("value2", value2);
            }
            String colorRaw = trimToNull(asString(item.get("color")));
            if (colorRaw != null) {
                String color = normalizeColor(colorRaw, null);
                if (color == null) {
                    throw new IllegalArgumentException("组件 " + widgetId + " 的条件格式 color 不合法");
                }
                normalized.put("color", color);
            }
            String backgroundRaw = trimToNull(asString(item.get("background")));
            if (backgroundRaw != null) {
                String background = normalizeColor(backgroundRaw, null);
                if (background == null) {
                    throw new IllegalArgumentException("组件 " + widgetId + " 的条件格式 background 不合法");
                }
                normalized.put("background", background);
            }
            if (item.containsKey("bold")) {
                normalized.put("bold", Boolean.parseBoolean(String.valueOf(item.get("bold"))));
            }
            result.add(normalized);
        }
        return result;
    }

    private void validatePivotEncoding(String widgetId, Map<String, Object> encode) {
        List<Map<String, Object>> dimensions = asMapList(encode.get("dimensions"));
        List<Map<String, Object>> measures = asMapList(encode.get("measures"));
        boolean hasRow = dimensions.stream().anyMatch(dimension -> "row".equals(asString(dimension.get("role"))));
        if (!hasRow) {
            throw new IllegalArgumentException("透视表组件 " + widgetId + " 需要 role=row 的维度字段");
        }
        for (Map<String, Object> dimension : dimensions) {
            String role = asString(dimension.get("role"));
            if (!PIVOT_DIMENSION_ROLES.contains(role)) {
                throw new IllegalArgumentException("透视表组件 " + widgetId + " 的维度 role 只能是 row 或 column");
            }
        }
        if (measures.isEmpty()) {
            throw new IllegalArgumentException("透视表组件 " + widgetId + " 的 encode.measures 至少需要一个度量字段");
        }
        for (Map<String, Object> measure : measures) {
            String role = asString(measure.get("role"));
            if (!"value".equals(role)) {
                throw new IllegalArgumentException("透视表组件 " + widgetId + " 的度量 role 只能是 value");
            }
        }
    }

    /**
     * 校验 v2 encode 的字段引用与聚合方式。
     */
    private void validateEncodeFields(
        String widgetId,
        String datasetId,
        Map<String, Object> encode,
        Set<String> fields,
        boolean requireMeasures
    ) {
        List<Map<String, Object>> measures = asMapList(encode.get("measures"));
        if (requireMeasures && measures.isEmpty()) {
            throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.measures 至少需要一个度量字段");
        }
        for (Map<String, Object> measure : measures) {
            String field = asString(measure.get("field"));
            String agg = asString(measure.get("agg"));
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("组件 " + widgetId + " 的 encode.measures[].field 不能为空");
            }
            if (agg == null || !AGGREGATIONS.contains(agg)) {
                throw new IllegalArgumentException(
                    "组件 " + widgetId + " 的字段 " + field + " 缺少合法 agg（sum/avg/min/max/count/none）"
                );
            }
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
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<String> referenced = new ArrayList<>();
        for (Map<String, Object> dimension : asMapList(encode.get("dimensions"))) {
            referenced.add(asString(dimension.get("field")));
        }
        for (Map<String, Object> measure : measures) {
            referenced.add(asString(measure.get("field")));
        }
        referenced.add(asString(encode.get("sortField")));
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

    /**
     * grid 模式不允许组件重叠；px 模式（大屏自由画布）允许叠放，由 zIndex 决定层级。
     */
    private void assertNoLayoutOverlap(List<Map<String, Object>> widgets, CanvasMetrics canvas) {
        if (canvas.pixel()) {
            return;
        }
        List<Map<String, Object>> topLevel = widgets.stream()
            .filter(widget -> trimToNull(asString(widget.get("parentId"))) == null)
            .toList();
        for (int i = 0; i < topLevel.size(); i++) {
            Map<String, Object> leftWidget = topLevel.get(i);
            Map<String, Object> left = asMap(leftWidget.get("layout"));
            for (int j = i + 1; j < topLevel.size(); j++) {
                Map<String, Object> rightWidget = topLevel.get(j);
                Map<String, Object> right = asMap(rightWidget.get("layout"));
                boolean overlap =
                    intOrDefault(left.get("x"), 0) < intOrDefault(right.get("x"), 0) + intOrDefault(right.get("w"), 1) &&
                    intOrDefault(left.get("x"), 0) + intOrDefault(left.get("w"), 1) > intOrDefault(right.get("x"), 0) &&
                    intOrDefault(left.get("y"), 0) < intOrDefault(right.get("y"), 0) + intOrDefault(right.get("h"), 1) &&
                    intOrDefault(left.get("y"), 0) + intOrDefault(left.get("h"), 1) > intOrDefault(right.get("y"), 0);
                if (overlap) {
                    throw new IllegalArgumentException(
                        "看板组件布局重叠：" + asString(leftWidget.get("id")) + " 与 " + asString(rightWidget.get("id"))
                    );
                }
            }
        }
    }

    private Map<String, Object> normalizeLayout(Object raw, String widgetType, CanvasMetrics canvas, boolean child) {
        Map<String, Object> layout = asMap(raw);
        if (child) {
            return normalizeChildLayout(layout);
        }
        if (canvas.pixel()) {
            return normalizePixelLayout(layout, canvas);
        }
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

    /**
     * 容器子组件固定使用容器内的 24 栅格，与看板本身是栅格还是像素无关。
     */
    private Map<String, Object> normalizeChildLayout(Map<String, Object> layout) {
        int x = intOrDefault(layout.get("x"), 0);
        int y = intOrDefault(layout.get("y"), 0);
        int w = intOrDefault(layout.get("w"), 12);
        int h = intOrDefault(layout.get("h"), 6);
        // 兼容旧版绝对坐标：明显超出 24 栅格的布局回落到默认位置。
        if (x < 0 || x > GRID_COLUMNS - 1 || w < 1 || w > GRID_COLUMNS) {
            x = 0;
            y = 0;
            w = 12;
            h = 6;
        }
        w = clamp(w, 1, GRID_COLUMNS - x);
        y = clamp(y, 0, 1000);
        h = clamp(h, 1, 100);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("x", x);
        normalized.put("y", y);
        normalized.put("w", w);
        normalized.put("h", h);
        return normalized;
    }

    /**
     * px 模式：坐标与尺寸都是画布内容区像素，越界时钳制而不是报错。
     */
    private Map<String, Object> normalizePixelLayout(Map<String, Object> layout, CanvasMetrics canvas) {
        int contentWidth = canvas.contentWidth();
        int contentHeight = canvas.contentHeight();
        int w = clamp(intOrDefault(layout.get("w"), Math.max(40, contentWidth / 2)), 40, contentWidth);
        int h = clamp(intOrDefault(layout.get("h"), Math.max(20, contentHeight / 3)), 20, contentHeight);
        int x = clamp(intOrDefault(layout.get("x"), 0), 0, Math.max(0, contentWidth - w));
        int y = clamp(intOrDefault(layout.get("y"), 0), 0, Math.max(0, contentHeight - h));
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("x", x);
        normalized.put("y", y);
        normalized.put("w", w);
        normalized.put("h", h);
        return normalized;
    }

    /**
     * 组件皮肤：只保留视觉字段，未知键丢弃，非法值跳过。
     *
     * <p>与结构性配置不同，样式问题不应阻塞保存，因此这里以“尽量保留 + 回落默认”为主；
     * 仅当单个组件样式序列化后超过 4KB 时拒绝，避免 spec 被样式拖爆。
     */
    private Map<String, Object> normalizeWidgetStyle(String widgetId, Object raw) {
        Map<String, Object> style = asMap(raw);
        if (style.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> card = normalizeCardStyle(asMap(style.get("card")));
        if (!card.isEmpty()) {
            result.put("card", card);
        }
        Map<String, Object> title = normalizeTitleStyle(asMap(style.get("title")));
        if (!title.isEmpty()) {
            result.put("title", title);
        }
        Map<String, Object> body = normalizeBodyStyle(asMap(style.get("body")));
        if (!body.isEmpty()) {
            result.put("body", body);
        }
        Map<String, Object> kpi = normalizeKpiStyle(asMap(style.get("kpi")));
        if (!kpi.isEmpty()) {
            result.put("kpi", kpi);
        }
        Map<String, Object> chart = normalizeChartStyle(asMap(style.get("chart")));
        if (!chart.isEmpty()) {
            result.put("chart", chart);
        }
        if (result.isEmpty()) {
            return result;
        }
        if (toJson(result).length() > MAX_STYLE_JSON_LENGTH) {
            throw new IllegalArgumentException(
                "组件 " + widgetId + " 的样式过大，最大允许 " + MAX_STYLE_JSON_LENGTH + " 个字符"
            );
        }
        return result;
    }

    private Map<String, Object> normalizeCardStyle(Map<String, Object> card) {
        Map<String, Object> result = new LinkedHashMap<>();
        String preset = trimToNull(asString(card.get("preset")));
        if (preset != null && CARD_PRESETS.contains(preset.toLowerCase())) {
            result.put("preset", preset.toLowerCase());
        }
        Map<String, Object> fillRaw = asMap(card.get("fill"));
        if (!fillRaw.isEmpty()) {
            String type = trimToNull(asString(fillRaw.get("type")));
            type = type == null ? "none" : type.toLowerCase();
            if (FILL_TYPES.contains(type)) {
                Map<String, Object> fill = new LinkedHashMap<>();
                fill.put("type", type);
                if ("color".equals(type)) {
                    String color = normalizeColor(fillRaw.get("color"), null);
                    if (color != null) {
                        fill.put("color", color);
                    }
                } else if ("gradient".equals(type)) {
                    Map<String, Object> gradient = asMap(fillRaw.get("gradient"));
                    Map<String, Object> normalizedGradient = new LinkedHashMap<>();
                    normalizedGradient.put("from", normalizeColor(gradient.get("from"), "#0f172a"));
                    normalizedGradient.put("to", normalizeColor(gradient.get("to"), "#1e293b"));
                    normalizedGradient.put("angle", clamp(intOrDefault(gradient.get("angle"), 180), 0, 360));
                    fill.put("gradient", normalizedGradient);
                }
                result.put("fill", fill);
            }
        }
        putIntIfPresent(result, "padding", card.get("padding"), 0, 48);
        putDoubleIfPresent(result, "opacity", card.get("opacity"), 0, 1);
        putIntIfPresent(result, "backdropBlur", card.get("backdropBlur"), 0, 20);

        Map<String, Object> border = asMap(card.get("border"));
        if (!border.isEmpty()) {
            Map<String, Object> normalizedBorder = new LinkedHashMap<>();
            putIntIfPresent(normalizedBorder, "width", border.get("width"), 0, 8);
            putIntIfPresent(normalizedBorder, "radius", border.get("radius"), 0, 32);
            String color = normalizeColor(border.get("color"), null);
            if (color != null) {
                normalizedBorder.put("color", color);
            }
            String borderStyle = trimToNull(asString(border.get("style")));
            if (borderStyle != null && BORDER_STYLES.contains(borderStyle.toLowerCase())) {
                normalizedBorder.put("style", borderStyle.toLowerCase());
            }
            if (!normalizedBorder.isEmpty()) {
                result.put("border", normalizedBorder);
            }
        }

        Map<String, Object> shadow = asMap(card.get("shadow"));
        if (!shadow.isEmpty()) {
            Map<String, Object> normalizedShadow = new LinkedHashMap<>();
            putIntIfPresent(normalizedShadow, "x", shadow.get("x"), -50, 50);
            putIntIfPresent(normalizedShadow, "y", shadow.get("y"), -50, 50);
            putIntIfPresent(normalizedShadow, "blur", shadow.get("blur"), 0, 100);
            putIntIfPresent(normalizedShadow, "spread", shadow.get("spread"), -20, 50);
            String color = normalizeColor(shadow.get("color"), null);
            if (color != null) {
                normalizedShadow.put("color", color);
            }
            if (!normalizedShadow.isEmpty()) {
                result.put("shadow", normalizedShadow);
            }
        }
        return result;
    }

    private Map<String, Object> normalizeTitleStyle(Map<String, Object> title) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (title.containsKey("show")) {
            result.put("show", Boolean.parseBoolean(String.valueOf(title.get("show"))));
        }
        String icon = trimToNull(asString(title.get("icon")));
        if (icon != null && ICON_NAME_PATTERN.matcher(icon).matches()) {
            result.put("icon", icon);
        }
        putIntIfPresent(result, "fontSize", title.get("fontSize"), 10, 48);
        putIntIfPresent(result, "fontWeight", title.get("fontWeight"), 100, 900);
        putIntIfPresent(result, "height", title.get("height"), 0, 80);
        putIntIfPresent(result, "letterSpacing", title.get("letterSpacing"), 0, 8);
        String color = normalizeColor(title.get("color"), null);
        if (color != null) {
            result.put("color", color);
        }
        String background = normalizeColor(title.get("background"), null);
        if (background != null) {
            result.put("background", background);
        }
        String align = trimToNull(asString(title.get("align")));
        if (align != null && TITLE_ALIGNS.contains(align.toLowerCase())) {
            result.put("align", align.toLowerCase());
        }
        String decoration = trimToNull(asString(title.get("decoration")));
        if (decoration != null && TITLE_DECORATIONS.contains(decoration.toLowerCase())) {
            result.put("decoration", decoration.toLowerCase());
        }
        return result;
    }

    private Map<String, Object> normalizeBodyStyle(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        putIntIfPresent(result, "padding", body.get("padding"), 0, 32);
        return result;
    }

    /**
     * 图表样式预设：只保存显式配置项，未配置的项由前端按预设决定。
     */
    private Map<String, Object> normalizeChartStyle(Map<String, Object> chart) {
        Map<String, Object> result = new LinkedHashMap<>();
        String preset = trimToNull(asString(chart.get("preset")));
        if (preset != null && CHART_STYLE_PRESETS.contains(preset.toLowerCase())) {
            result.put("preset", preset.toLowerCase());
        }
        for (String key : List.of("gradient", "glow", "showAxis", "showLegend", "showLabel")) {
            if (chart.containsKey(key)) {
                result.put(key, Boolean.parseBoolean(String.valueOf(chart.get(key))));
            }
        }
        String legendPosition = trimToNull(asString(chart.get("legendPosition")));
        if (legendPosition != null && LEGEND_POSITIONS.contains(legendPosition.toLowerCase())) {
            result.put("legendPosition", legendPosition.toLowerCase());
        }
        putIntIfPresent(result, "legendFontSize", chart.get("legendFontSize"), 8, 24);
        putIntIfPresent(result, "axisTitleFontSize", chart.get("axisTitleFontSize"), 8, 24);
        String axisTitleColor = normalizeColor(chart.get("axisTitleColor"), null);
        if (axisTitleColor != null) {
            result.put("axisTitleColor", axisTitleColor);
        }
        putIntIfPresent(result, "labelFontSize", chart.get("labelFontSize"), 8, 24);
        putIntIfPresent(result, "barRadius", chart.get("barRadius"), 0, 16);
        for (String key : List.of("xAxis", "yAxis")) {
            Map<String, Object> axis = asMap(chart.get(key));
            if (!axis.isEmpty()) {
                Map<String, Object> normalizedAxis = normalizeChartAxisStyle(axis);
                if (!normalizedAxis.isEmpty()) {
                    result.put(key, normalizedAxis);
                }
            }
        }
        return result;
    }

    /** 单个坐标轴的样式覆盖：显隐、标题、标签、刻度与网格线。 */
    private Map<String, Object> normalizeChartAxisStyle(Map<String, Object> axis) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (axis.containsKey("show")) {
            result.put("show", Boolean.parseBoolean(String.valueOf(axis.get("show"))));
        }
        String title = trimToNull(asString(axis.get("title")));
        if (title != null && title.length() <= MAX_AXIS_TITLE_LENGTH) {
            result.put("title", title);
        }
        putIntIfPresent(result, "labelFontSize", axis.get("labelFontSize"), 8, 24);
        if (axis.containsKey("tick")) {
            result.put("tick", Boolean.parseBoolean(String.valueOf(axis.get("tick"))));
        }
        String lineColor = normalizeColor(axis.get("lineColor"), null);
        if (lineColor != null) {
            result.put("lineColor", lineColor);
        }
        if (axis.containsKey("grid")) {
            result.put("grid", Boolean.parseBoolean(String.valueOf(axis.get("grid"))));
        }
        String gridStyle = trimToNull(asString(axis.get("gridStyle")));
        if (gridStyle != null && BORDER_STYLES.contains(gridStyle.toLowerCase())) {
            result.put("gridStyle", gridStyle.toLowerCase());
        }
        return result;
    }

    /**
     * KPI 展示配置：标签、图标、数值/单位排版与数字动效开关。
     */
    private Map<String, Object> normalizeKpiStyle(Map<String, Object> kpi) {
        Map<String, Object> result = new LinkedHashMap<>();
        String label = trimToNull(asString(kpi.get("label")));
        if (label != null && label.length() <= 40) {
            result.put("label", label);
        }
        String icon = trimToNull(asString(kpi.get("icon")));
        if (icon != null && ICON_NAME_PATTERN.matcher(icon).matches()) {
            result.put("icon", icon);
        }
        String layout = trimToNull(asString(kpi.get("layout")));
        if (layout != null && KPI_LAYOUTS.contains(layout.toLowerCase())) {
            result.put("layout", layout.toLowerCase());
        }
        Map<String, Object> value = asMap(kpi.get("value"));
        if (!value.isEmpty()) {
            Map<String, Object> normalizedValue = new LinkedHashMap<>();
            putIntIfPresent(normalizedValue, "fontSize", value.get("fontSize"), 12, 120);
            putIntIfPresent(normalizedValue, "letterSpacing", value.get("letterSpacing"), 0, 8);
            String color = normalizeColor(value.get("color"), null);
            if (color != null) {
                normalizedValue.put("color", color);
            }
            String fontFamily = trimToNull(asString(value.get("fontFamily")));
            if (fontFamily != null && FONT_FAMILIES.contains(fontFamily.toLowerCase())) {
                normalizedValue.put("fontFamily", fontFamily.toLowerCase());
            }
            if (value.containsKey("bold")) {
                normalizedValue.put("bold", Boolean.parseBoolean(String.valueOf(value.get("bold"))));
            }
            if (!normalizedValue.isEmpty()) {
                result.put("value", normalizedValue);
            }
        }
        Map<String, Object> unit = asMap(kpi.get("unit"));
        if (!unit.isEmpty()) {
            Map<String, Object> normalizedUnit = new LinkedHashMap<>();
            String text = trimToNull(asString(unit.get("text")));
            if (text != null && text.length() <= 8) {
                normalizedUnit.put("text", text);
            }
            putIntIfPresent(normalizedUnit, "fontSize", unit.get("fontSize"), 10, 40);
            String color = normalizeColor(unit.get("color"), null);
            if (color != null) {
                normalizedUnit.put("color", color);
            }
            if (!normalizedUnit.isEmpty()) {
                result.put("unit", normalizedUnit);
            }
        }
        if (kpi.containsKey("animate")) {
            result.put("animate", Boolean.parseBoolean(String.valueOf(kpi.get("animate"))));
        }
        putIntIfPresent(result, "decimals", kpi.get("decimals"), 0, 6);
        return result;
    }

    private void putIntIfPresent(Map<String, Object> target, String key, Object raw, int min, int max) {
        Double value = asDouble(raw);
        if (value != null) {
            target.put(key, clamp((int) Math.round(value), min, max));
        }
    }

    private void putDoubleIfPresent(Map<String, Object> target, String key, Object raw, double min, double max) {
        Double value = asDouble(raw);
        if (value != null) {
            double clamped = Math.max(min, Math.min(max, value));
            target.put(key, Math.round(clamped * 100.0) / 100.0);
        }
    }

    private String normalizeThemePreset(Object raw) {
        String preset = trimToNull(asString(raw));
        if (preset == null) {
            return null;
        }
        String normalized = preset.toLowerCase();
        return THEME_PRESETS.contains(normalized) ? normalized : null;
    }

    /**
     * 大屏主题 token：颜色 / 序列色 / 字体 / 地图配色，只保存显式覆盖项。
     */
    private Map<String, Object> normalizeThemeTokens(Object raw) {
        Map<String, Object> tokens = asMap(raw);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("primary", "secondary", "accent", "text", "textSecondary", "surface", "border")) {
            String color = normalizeColor(tokens.get(key), null);
            if (color != null) {
                result.put(key, color);
            }
        }
        Object rawPalette = tokens.get("palette");
        if (rawPalette instanceof List<?> list) {
            List<String> palette = new ArrayList<>();
            for (Object item : list) {
                String color = normalizeColor(item, null);
                if (color != null) {
                    palette.add(color);
                }
                if (palette.size() >= MAX_THEME_PALETTE) {
                    break;
                }
            }
            if (!palette.isEmpty()) {
                result.put("palette", palette);
            }
        }
        String fontFamily = trimToNull(asString(tokens.get("fontFamily")));
        if (fontFamily != null && FONT_FAMILIES.contains(fontFamily.toLowerCase())) {
            result.put("fontFamily", fontFamily.toLowerCase());
        }
        Map<String, Object> map = asMap(tokens.get("map"));
        if (!map.isEmpty()) {
            Map<String, Object> normalizedMap = new LinkedHashMap<>();
            for (String key : List.of("area", "border", "emphasis", "label")) {
                String color = normalizeColor(map.get(key), null);
                if (color != null) {
                    normalizedMap.put(key, color);
                }
            }
            if (!normalizedMap.isEmpty()) {
                result.put("map", normalizedMap);
            }
        }
        return result;
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
        Set<String> fields = datasetFields.get(datasetId);
        if (fields != null && !fields.isEmpty() && !fields.contains(trimmedField)) {
            throw new IllegalArgumentException(
                "筛选器 " + filterId + " 引用了数据集 " + datasetId + " 中不存在的字段：" + trimmedField
            );
        }
        return trimmedField;
    }

    /**
     * 规范化 filters[].targets[]：直接字段与 pushdown 统一由服务端执行。
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
                // 所有直接字段目标统一走服务端过滤；mode 仅保留为兼容字段。
                target.put("mode", "server");
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
        int rowHeight = clamp(intOrDefault(layout.get("rowHeight"), 40), 8, 80);
        String theme = normalizeTheme(screen.get("theme"));
        String fitMode = normalizeFitMode(screen.get("fitMode"));
        int autoRefreshSeconds = clamp(intOrDefault(screen.get("autoRefreshSeconds"), 0), 0, 86400);
        Map<String, Object> background = normalizeBackground(screen.get("background"), theme);
        Map<String, Object> canvas = normalizeScreenCanvas(screen.get("canvas"));
        String themePreset = normalizeThemePreset(screen.get("themePreset"));
        Map<String, Object> tokens = normalizeThemeTokens(screen.get("tokens"));

        // 主题、背景与画布配置在标准模式同样需要持久化；标准模式固定使用栅格定位。
        if (!LAYOUT_MODE_SCREEN.equals(mode)) {
            canvas.put("unit", CANVAS_UNIT_GRID);
        }
        Map<String, Object> normalizedScreen = new LinkedHashMap<>();
        normalizedScreen.put("width", width);
        normalizedScreen.put("height", height);
        normalizedScreen.put("fitMode", fitMode);
        normalizedScreen.put("theme", theme);
        if (themePreset != null) {
            normalizedScreen.put("themePreset", themePreset);
        }
        if (!tokens.isEmpty()) {
            normalizedScreen.put("tokens", tokens);
        }
        normalizedScreen.put("autoRefreshSeconds", autoRefreshSeconds);
        normalizedScreen.put("canvas", canvas);
        if (!background.isEmpty()) {
            normalizedScreen.put("background", background);
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("mode", mode);
        normalized.put("rowHeight", rowHeight);
        normalized.put("screen", normalizedScreen);
        return normalized;
    }

    /**
     * 大屏画布配置：grid（24 栅格）或 px（画布绝对像素）。
     *
     * <p>padding 为画布内边距，0 表示贴边；gap 仅在 grid 模式生效。
     */
    private Map<String, Object> normalizeScreenCanvas(Object raw) {
        Map<String, Object> canvas = asMap(raw);
        String unit = trimToNull(asString(canvas.get("unit")));
        unit = unit != null && CANVAS_UNITS.contains(unit.toLowerCase()) ? unit.toLowerCase() : CANVAS_UNIT_GRID;
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("unit", unit);
        normalized.put("padding", clamp(intOrDefault(canvas.get("padding"), 16), 0, MAX_CANVAS_PADDING));
        normalized.put("gap", clamp(intOrDefault(canvas.get("gap"), 12), 0, MAX_CANVAS_GAP));
        return normalized;
    }

    /**
     * 把归一化后的 layout 换算成组件定位所需的画布度量。
     *
     * <p>标准模式没有 screen 块，统一回落到 grid 与默认分辨率。
     */
    private CanvasMetrics canvasMetrics(Map<String, Object> layout) {
        Map<String, Object> screen = asMap(layout.get("screen"));
        Map<String, Object> canvas = asMap(screen.get("canvas"));
        String unit = asString(canvas.get("unit"));
        boolean pixel = LAYOUT_MODE_SCREEN.equals(asString(layout.get("mode"))) && CANVAS_UNIT_PX.equals(unit);
        int width = clamp(intOrDefault(screen.get("width"), DEFAULT_SCREEN_WIDTH), 640, 7680);
        int height = clamp(intOrDefault(screen.get("height"), DEFAULT_SCREEN_HEIGHT), 480, 4320);
        int padding = clamp(intOrDefault(canvas.get("padding"), 16), 0, MAX_CANVAS_PADDING);
        int gap = clamp(intOrDefault(canvas.get("gap"), 12), 0, MAX_CANVAS_GAP);
        int rowHeight = clamp(intOrDefault(layout.get("rowHeight"), 40), 8, 80);
        return new CanvasMetrics(pixel ? CANVAS_UNIT_PX : CANVAS_UNIT_GRID, width, height, padding, gap, GRID_COLUMNS, rowHeight);
    }

    /**
     * 组件定位上下文；px 模式下坐标与尺寸均为画布内容区像素。
     */
    private record CanvasMetrics(String unit, int width, int height, int padding, int gap, int columns, int rowHeight) {
        boolean pixel() {
            return CANVAS_UNIT_PX.equals(unit);
        }

        int contentWidth() {
            return Math.max(1, width - padding * 2);
        }

        int contentHeight() {
            return Math.max(1, height - padding * 2);
        }
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

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    static Double asDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
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
