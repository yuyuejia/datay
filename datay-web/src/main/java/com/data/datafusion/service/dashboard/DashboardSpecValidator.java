package com.data.datafusion.service.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    public static final int DEFAULT_ROW_HEIGHT = 40;
    public static final int DEFAULT_GAP = 12;
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
        Map<String, Object> root = parse(specJson);
        root.putIfAbsent("title", "分析看板");
        root.put("layout", normalizeRootLayout(root.get("layout")));

        Set<String> datasetIds = new LinkedHashSet<>();
        Map<String, String> datasetTypes = new LinkedHashMap<>();
        List<Map<String, Object>> datasets = normalizeDatasets(root.get("datasets"), datasetIds, datasetTypes);
        root.put("datasets", datasets);
        if (datasets.isEmpty()) {
            throw new IllegalArgumentException("看板至少需要定义一个数据集");
        }

        List<Map<String, Object>> widgets = normalizeWidgets(root.get("widgets"), datasetIds);
        root.put("widgets", widgets);
        if (widgets.isEmpty()) {
            throw new IllegalArgumentException("看板至少需要一个图表或内容组件");
        }

        root.put("filters", normalizeFilters(root.get("filters"), datasetIds, datasetTypes));
        return root;
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

    private List<Map<String, Object>> normalizeDatasets(Object raw, Set<String> datasetIds, Map<String, String> datasetTypes) {
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
                result.add(dataset);
                continue;
            }

            String type = resolveDatasetType(dataset);
            dataset.put("type", type);
            datasetTypes.put(id, type);
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

    // ----------------------------------------------------------------- widgets

    private List<Map<String, Object>> normalizeWidgets(Object raw, Set<String> datasetIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String id = asString(item.get("id"));
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("存在缺少 id 的看板组件");
            }
            Map<String, Object> widget = new LinkedHashMap<>(item);
            widget.put("id", id);
            String type = asString(widget.get("type"));
            type = type == null || type.isBlank() ? WIDGET_ECHARTS : type.trim().toLowerCase();
            if (!WIDGET_TYPES.contains(type)) {
                throw new IllegalArgumentException("不支持的组件类型：" + type);
            }
            widget.put("type", type);
            widget.put("layout", normalizeLayout(widget.get("layout"), type));

            String datasetId = asString(widget.get("datasetId"));
            if (datasetId != null && !datasetId.isBlank()) {
                if (!datasetIds.contains(datasetId)) {
                    throw new IllegalArgumentException("组件 " + id + " 引用了不存在的数据集：" + datasetId);
                }
                widget.put("datasetId", datasetId);
            }

            if (WIDGET_ECHARTS.equals(type)) {
                Map<String, Object> option = asMap(widget.get("echartsOption"));
                widget.put("echartsOption", normalizeEchartsOption(option));
            } else if (WIDGET_HTML.equals(type)) {
                String html = asString(widget.get("html"));
                widget.put("html", html == null ? "" : html);
            }
            result.add(widget);
        }
        return result;
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

    private List<Map<String, Object>> normalizeFilters(Object raw, Set<String> datasetIds, Map<String, String> datasetTypes) {
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
            filter.putIfAbsent("label", id);
            String datasetId = asString(filter.get("datasetId"));
            String field = asString(filter.get("field"));
            if (datasetId != null && !datasetId.isBlank()) {
                if (!datasetIds.contains(datasetId)) {
                    throw new IllegalArgumentException("筛选器 " + id + " 引用了不存在的数据集：" + datasetId);
                }
                if (field == null || field.isBlank()) {
                    throw new IllegalArgumentException("筛选器 " + id + " 缺少字段");
                }
                filter.put("datasetId", datasetId);
                filter.put("field", field.trim());
                // 新配置直接使用数据集字段，不再保存旧的绑定关系
                filter.remove("bindings");
            } else {
                filter.remove("datasetId");
                filter.remove("field");
                filter.put("bindings", normalizeBindings(id, type, filter.get("bindings"), datasetIds, datasetTypes));
            }
            result.add(filter);
        }
        return result;
    }

    private List<Map<String, Object>> normalizeBindings(
        String filterId,
        String filterType,
        Object raw,
        Set<String> datasetIds,
        Map<String, String> datasetTypes
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : asMapList(raw)) {
            String datasetId = asString(item.get("datasetId"));
            if (datasetId == null || datasetId.isBlank() || !datasetIds.contains(datasetId)) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 绑定了不存在的数据集：" + datasetId);
            }
            Map<String, Object> binding = new LinkedHashMap<>(item);
            binding.put("datasetId", datasetId);
            String kind = asString(binding.get("kind"));
            if (kind == null || kind.isBlank()) {
                kind = inferBindingKind(filterType, datasetTypes.get(datasetId));
            }
            binding.put("kind", kind);
            switch (kind) {
                case BINDING_METRIC_TIME -> {
                    // 时间筛选，无需附加字段
                }
                case BINDING_METRIC_DIMENSION -> requireFields(binding, filterId, "dimensionModelCode", "dimensionFieldName");
                case BINDING_METRIC_FACT -> requireFields(binding, filterId, "factFieldName");
                case BINDING_SQL_PARAM -> requireFields(binding, filterId, "param");
                default -> throw new IllegalArgumentException("筛选器 " + filterId + " 的绑定类型不支持：" + kind);
            }
            result.add(binding);
        }
        return result;
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
        normalized.put("columns", GRID_COLUMNS);
        normalized.put("rowHeight", DEFAULT_ROW_HEIGHT);
        normalized.put("gap", DEFAULT_GAP);
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

    private static int intOrDefault(Object raw, int defaultValue) {
        if (raw instanceof Number number) {
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
