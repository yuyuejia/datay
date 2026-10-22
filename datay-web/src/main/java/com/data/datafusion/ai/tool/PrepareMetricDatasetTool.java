package com.data.datafusion.ai.tool;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 指标数据集准备工具。
 *
 * <p>模型先通过指标工具确定 metricQuery，再调用本工具获得可被看板引用的数据集：
 * <ul>
 *     <li>若数据集库已存在等价指标数据集，直接返回其 refId；</li>
 *     <li>若不存在，则在工具上下文中登记一份草稿，返回临时 refId；</li>
 * </ul>
 * 草稿不会立即写库。Agent 产出看板后，由 {@code DashboardDesignAssistant} 统一落库并替换 refId，
 * 从而保证看板只保存数据集引用，不在 spec 内嵌 metricQuery。
 */
@Component
public class PrepareMetricDatasetTool implements AiTool {

    public static final String NAME = "prepare_metric_dataset";

    /** 工具产物键：本次会话中待落库的指标数据集草稿。 */
    public static final String ARTIFACT_DRAFTS = "metricDatasetDrafts";

    /** 临时 refId 前缀，后处理阶段会被替换为真实数据集 id。 */
    public static final String DRAFT_PREFIX = "draft:";

    private final AnalysisDatasetService datasetService;
    private final ObjectMapper objectMapper;

    public PrepareMetricDatasetTool(AnalysisDatasetService datasetService, ObjectMapper objectMapper) {
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            在生成看板前准备指标数据集。先把整个看板需要的全部指标、维度和时间范围汇总成一个 metricQuery，
            再只调用一次本工具。工具会优先复用能覆盖本次 metricCodes / dimensions / timeRange 的已有数据集，
            允许复用包含额外指标或维度的数据集；只有无法覆盖时才登记一个待创建草稿，并返回临时 refId。
            生成看板 datasets 时必须使用返回的 refId，禁止在 datasets 中直接内嵌 metricQuery；
            一个看板应尽量只使用一个指标数据集，不要为每个图表分别创建数据集。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "数据集名称，例如「门店销售分析数据集」");
        props.put("name", name);
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "可选，数据集说明");
        props.put("description", description);
        Map<String, Object> metricQuery = new LinkedHashMap<>();
        metricQuery.put("type", "object");
        metricQuery.put(
            "description",
            "指标查询定义：metricCodes 必填；dimensions 为维度分组；timeRange 为可选日期区间（start/end）"
        );
        props.put("metricQuery", metricQuery);
        schema.put("properties", props);
        schema.put("required", List.of("name", "metricQuery"));
        return schema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        Object rawMetricQuery = arguments.get("metricQuery");
        if (!(rawMetricQuery instanceof Map<?, ?> metricQueryMap)) {
            return Map.of("error", "缺少 metricQuery，请先通过指标工具确定 metricCodes / dimensions");
        }
        Map<String, Object> metricQuery = new LinkedHashMap<>((Map<String, Object>) metricQueryMap);
        List<?> metricCodes = metricQuery.get("metricCodes") instanceof List<?> list ? list : List.of();
        if (metricCodes.isEmpty()) {
            return Map.of("error", "metricQuery.metricCodes 不能为空");
        }

        String name = asString(arguments.get("name"));
        if (name == null || name.isBlank()) {
            name = "指标数据集";
        }
        String description = asString(arguments.get("description"));

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("metricQuery", metricQuery);
        try {
            AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
            dto.setName(name.trim());
            dto.setDescription(description);
            dto.setType(AnalysisDataset.TYPE_METRIC);
            dto.setDefinition(objectMapper.writeValueAsString(definition));
            AnalysisDatasetDTO normalized = datasetService.validateDefinition(dto);

            Map<String, Object> normalizedQuery = extractMetricQuery(normalized.getDefinition());
            for (AnalysisDatasetDTO existing : datasetService.findAllSimple()) {
                if (AnalysisDataset.TYPE_METRIC.equalsIgnoreCase(existing.getType()) && covers(existing.getDefinition(), normalizedQuery)) {
                    return summary(existing, false);
                }
            }

            String key = UUID.nameUUIDFromBytes(normalized.getDefinition().getBytes(StandardCharsets.UTF_8)).toString();
            Map<String, Object> drafts = context.getArtifact(ARTIFACT_DRAFTS) instanceof Map<?, ?> rawDrafts
                ? (Map<String, Object>) rawDrafts
                : new LinkedHashMap<>();
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("name", normalized.getName());
            draft.put("description", normalized.getDescription());
            draft.put("definition", normalized.getDefinition());
            drafts.put(key, draft);
            context.putArtifact(ARTIFACT_DRAFTS, drafts);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("refId", DRAFT_PREFIX + key);
            result.put("name", normalized.getName());
            result.put("type", AnalysisDataset.TYPE_METRIC);
            result.put("draft", true);
            result.put("metricQuery", metricQuery);
            result.put("hint", "使用该 refId 作为看板数据集引用；系统会在生成结束后自动创建数据集");
            return result;
        } catch (JsonProcessingException e) {
            return Map.of("error", "metricQuery 序列化失败: " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> summary(AnalysisDatasetDTO dataset, boolean draft) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refId", dataset.getId());
        result.put("name", dataset.getName());
        result.put("code", dataset.getCode());
        result.put("type", dataset.getType());
        result.put("draft", draft);
        if (dataset.getFields() != null && !dataset.getFields().isBlank()) {
            Object fields = parseJson(dataset.getFields());
            if (fields != null) {
                result.put("fields", fields);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetricQuery(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            JsonNode metricQuery = root.path("metricQuery");
            if (metricQuery.isMissingNode() || metricQuery.isNull()) {
                return Map.of();
            }
            return objectMapper.convertValue(metricQuery, Map.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Map.of();
        }
    }

    /**
     * 判断已有数据集是否覆盖本次需求：指标是超集、维度是超集、时间范围覆盖本次区间。
     * 这样优先复用宽数据集，避免为每个图表创建窄数据集。
     */
    private boolean covers(String existingDefinition, Map<String, Object> requestedQuery) {
        Map<String, Object> existingQuery = extractMetricQuery(existingDefinition);
        if (existingQuery.isEmpty() || requestedQuery.isEmpty()) {
            return false;
        }
        List<String> existingCodes = asStringList(existingQuery.get("metricCodes"));
        List<String> requestedCodes = asStringList(requestedQuery.get("metricCodes"));
        if (!existingCodes.containsAll(requestedCodes)) {
            return false;
        }
        if (!dimensionsCover(existingQuery.get("dimensions"), requestedQuery.get("dimensions"))) {
            return false;
        }
        return timeRangeCovers(asMap(existingQuery.get("timeRange")), asMap(requestedQuery.get("timeRange")));
    }

    private boolean dimensionsCover(Object existingRaw, Object requestedRaw) {
        List<Map<String, Object>> existing = asMapList(existingRaw);
        List<Map<String, Object>> requested = asMapList(requestedRaw);
        if (requested.isEmpty()) {
            return true;
        }
        for (Map<String, Object> requestedDimension : requested) {
            boolean matched = false;
            for (Map<String, Object> existingDimension : existing) {
                if (!sameDimensionModel(existingDimension, requestedDimension)) {
                    continue;
                }
                Set<String> existingFields = dimensionFields(existingDimension);
                Set<String> requestedFields = dimensionFields(requestedDimension);
                if (existingFields.containsAll(requestedFields)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean sameDimensionModel(Map<String, Object> left, Map<String, Object> right) {
        String leftCode = asString(left.get("dimensionModelCode"));
        String rightCode = asString(right.get("dimensionModelCode"));
        if (leftCode == null || !leftCode.equals(rightCode)) {
            return false;
        }
        Object leftLevel = left.get("levelIndex");
        Object rightLevel = right.get("levelIndex");
        if (leftLevel == null && rightLevel == null) {
            return true;
        }
        return leftLevel != null && rightLevel != null && String.valueOf(leftLevel).equals(String.valueOf(rightLevel));
    }

    private Set<String> dimensionFields(Map<String, Object> dimension) {
        Set<String> fields = new LinkedHashSet<>(asStringList(dimension.get("dimensionFieldNames")));
        String single = asString(dimension.get("dimensionFieldName"));
        if (single != null && !single.isBlank()) {
            fields.add(single);
        }
        return fields;
    }

    private boolean timeRangeCovers(Map<String, Object> existing, Map<String, Object> requested) {
        String existingStart = asString(existing.get("start"));
        String existingEnd = asString(existing.get("end"));
        String requestedStart = asString(requested.get("start"));
        String requestedEnd = asString(requested.get("end"));
        boolean existingEmpty = isBlank(existingStart) && isBlank(existingEnd);
        boolean requestedEmpty = isBlank(requestedStart) && isBlank(requestedEnd);
        if (requestedEmpty) {
            // 本次要求全量数据，只有同样未限制时间范围的数据集才能覆盖
            return existingEmpty;
        }
        if (existingEmpty) {
            return true;
        }
        if (!isBlank(requestedStart) && (isBlank(existingStart) || existingStart.compareTo(requestedStart) > 0)) {
            return false;
        }
        return isBlank(requestedEnd) || (!isBlank(existingEnd) && existingEnd.compareTo(requestedEnd) >= 0);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> asStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().filter(item -> item != null && !String.valueOf(item).isBlank()).map(String::valueOf).toList();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
