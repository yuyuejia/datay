package com.data.datafusion.ai.tool;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分析数据集库查询工具（只读）。
 *
 * <p>看板设计助手在生成看板前先通过该工具确定要引用的数据集，尤其是分析数据集库中已有的指标数据集。
 * 返回内容包含数据集 id、名称、类型、字段缓存和定义，模型据此选择 refId 并生成组件字段映射。
 */
@Component
public class ListAnalysisDatasetsTool implements AiTool {

    public static final String NAME = "list_analysis_datasets";

    private static final int MAX_LIST_SIZE = 50;

    private final AnalysisDatasetService datasetService;
    private final ObjectMapper objectMapper;
    private final MetricRepository metricRepository;

    public ListAnalysisDatasetsTool(AnalysisDatasetService datasetService, ObjectMapper objectMapper) {
        this(datasetService, objectMapper, null);
    }

    @Autowired
    public ListAnalysisDatasetsTool(
        AnalysisDatasetService datasetService,
        ObjectMapper objectMapper,
        MetricRepository metricRepository
    ) {
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
        this.metricRepository = metricRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            查询分析数据集库中的可复用数据集，用于在生成看板前先确定数据集。
            默认优先返回指标数据集（METRIC），也可以按 keyword 按名称/编码/描述/指标编码过滤。
            返回字段：id（数据集库主键，即看板 refId）、name、code、type、fields（最近缓存的列信息）、definition（指标查询或 SQL 定义）。
            如果已有数据集包含本次需要的指标和维度（允许有额外字段），优先复用该宽数据集，不要为每个图表创建更窄的数据集。
            看板 datasets 中只需要引用 id 作为 refId，禁止把 metricQuery 内嵌到看板 spec。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "可选，按数据集名称、编码、描述或指标编码模糊过滤");
        props.put("keyword", keyword);
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("type", "string");
        type.put("enum", List.of("METRIC", "SQL", "ALL"));
        type.put("description", "可选，数据集类型；默认 METRIC");
        props.put("type", type);
        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "可选，最多返回条数，默认 20，最大 50");
        props.put("limit", limit);
        schema.put("properties", props);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        String keyword = asString(arguments.get("keyword"));
        String type = asString(arguments.get("type"));
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedType = type == null || type.isBlank() ? AnalysisDataset.TYPE_METRIC : type.trim().toUpperCase(Locale.ROOT);
        int limit = normalizeLimit(arguments.get("limit"));

        List<Map<String, Object>> datasets = new ArrayList<>();
        for (AnalysisDatasetDTO dto : datasetService.findAllSimple()) {
            if (!"ALL".equals(normalizedType) && !normalizedType.equalsIgnoreCase(dto.getType())) {
                continue;
            }
            if (normalizedKeyword != null && !normalizedKeyword.isEmpty() && !matches(dto, normalizedKeyword)) {
                continue;
            }
            if (datasets.size() >= limit) {
                break;
            }
            datasets.add(toSummary(dto));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", normalizedType);
        result.put("total", datasets.size());
        result.put("datasets", datasets);
        if (datasets.isEmpty()) {
            result.put("hint", "没有匹配的数据集。请放宽 keyword，或先准备一个指标数据集，再生成看板。");
        }
        return result;
    }

    private Map<String, Object> toSummary(AnalysisDatasetDTO dto) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", dto.getId());
        item.put("name", dto.getName());
        item.put("code", dto.getCode());
        item.put("type", dto.getType());
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            item.put("description", dto.getDescription());
        }
        Object fields = parseJson(dto.getFields());
        if (fields != null) {
            item.put("fields", fields);
        }
        Object definition = parseJson(dto.getDefinition());
        if (definition != null) {
            item.put("definition", definition);
            List<Map<String, Object>> metrics = resolveMetrics(definition);
            if (!metrics.isEmpty()) {
                item.put("metrics", metrics);
            }
        }
        return item;
    }

    private List<Map<String, Object>> resolveMetrics(Object definition) {
        if (metricRepository == null || !(definition instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object metricQuery = map.get("metricQuery");
        List<?> codes = List.of();
        if (metricQuery instanceof Map<?, ?> query && query.get("metricCodes") instanceof List<?> list) {
            codes = list;
        } else if (map.get("metricCodes") instanceof List<?> list) {
            codes = list;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object code : codes) {
            String value = asString(code);
            if (value == null || value.isBlank()) {
                continue;
            }
            metricRepository.findByCode(value).ifPresent(metric -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", metric.getCode());
                item.put("name", metric.getName());
                item.put("metricType", metric.getMetricType());
                item.put("isAdditive", metric.getIsAdditive());
                result.add(item);
            });
        }
        return result;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    private boolean matches(AnalysisDatasetDTO dto, String keyword) {
        return contains(dto.getName(), keyword) || contains(dto.getCode(), keyword) || contains(dto.getDescription(), keyword) || contains(dto.getDefinition(), keyword);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static int normalizeLimit(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(1, Math.min(number.intValue(), MAX_LIST_SIZE));
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Math.max(1, Math.min(Integer.parseInt(text.trim()), MAX_LIST_SIZE));
            } catch (NumberFormatException ignored) {
                return 20;
            }
        }
        return 20;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
