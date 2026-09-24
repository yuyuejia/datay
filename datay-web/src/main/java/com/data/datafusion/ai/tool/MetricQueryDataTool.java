package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.AiResult;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.data.datafusion.service.dto.MetricQueryFieldDTO;
import com.data.datafusion.service.dto.MetricQueryTimeRangeDTO;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.MetricFilterConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 指标数据查询工具（只读）。
 *
 * <p>把模型拆解出的指标、维度、业务限定与时间范围组装为 {@link MetricQueryDTO}，
 * 复用 {@link MetricQueryService} 生成并执行指标查询 SQL。
 * 完整结果（columns / rows / sql）会写入 {@link AiToolContext} 的
 * {@link AiResult#ARTIFACT_METRIC_QUERY} 产物，供前端直接渲染数据表；
 * 回传给模型的内容做了行数裁剪，避免上下文膨胀。
 */
@Component
public class MetricQueryDataTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "query_metric_data";

    private static final Logger LOG = LoggerFactory.getLogger(MetricQueryDataTool.class);

    /** 回传给模型的数据行数上限。 */
    private static final int MAX_ROWS_TO_MODEL = 50;

    private final MetricQueryService metricQueryService;
    private final ObjectMapper objectMapper;

    public MetricQueryDataTool(MetricQueryService metricQueryService, ObjectMapper objectMapper) {
        this.metricQueryService = metricQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            执行指标数据查询，返回查询结果的列与数据行。
            入参：metricCodes（指标编码，必填）、dimensions（分组维度）、conditions（业务限定，仅支持维度条件）、timeRange（时间范围）。
            dimensions 每项：{dimensionModelCode, dimensionFieldNames[], levelIndex}；levelIndex 用于层级维度按某层级汇总。
            conditions 每项：{dimensionModelCode, dimensionFieldName, operator, value, valueEnd, logic}，
              operator 取值 EQ/NE/GT/GE/LT/LE/IN/NOT_IN/LIKE/IS_NULL/IS_NOT_NULL/BETWEEN，IN 时 value 用英文逗号分隔多个值，logic 默认 AND。
            timeRange 为 {start, end}，使用 yyyy-MM-dd 具体日期；查询执行后会返回 columns 与 rows 供你展示。
            重要：按月/季/年/周的趋势或分布，应使用时间维度（isTimeDimension=true）的周期字段（如 dim_date 的 year、month）作为分组维度放入 dimensions，
            由一次查询完成分组；不要用 timeRange 逐个月份反复查询。timeRange 仅用于限定整体统计区间。
            若结果为空，可结合查询元数据反思过滤值或口径后调整入参重试。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> metricCodes = new LinkedHashMap<>();
        metricCodes.put("type", "array");
        metricCodes.put("description", "指标编码列表");
        metricCodes.put("items", Map.of("type", "string"));
        props.put("metricCodes", metricCodes);

        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("type", "array");
        dimensions.put("description", "分组维度列表");
        dimensions.put("items", dimensionSchema());
        props.put("dimensions", dimensions);

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("type", "array");
        conditions.put("description", "业务限定（维度条件）列表");
        conditions.put("items", conditionSchema());
        props.put("conditions", conditions);

        Map<String, Object> timeRange = new LinkedHashMap<>();
        timeRange.put("type", "object");
        Map<String, Object> timeProps = new LinkedHashMap<>();
        timeProps.put("start", Map.of("type", "string", "description", "起始日期，yyyy-MM-dd"));
        timeProps.put("end", Map.of("type", "string", "description", "结束日期，yyyy-MM-dd"));
        timeRange.put("properties", timeProps);
        props.put("timeRange", timeRange);

        schema.put("properties", props);
        schema.put("required", List.of("metricCodes"));
        return schema;
    }

    private static Map<String, Object> dimensionSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("dimensionModelCode", Map.of("type", "string", "description", "维度模型编码，来自 describe_metrics"));
        props.put("dimensionFieldNames", Map.of("type", "array", "description", "维度显示字段", "items", Map.of("type", "string")));
        props.put("dimensionFieldName", Map.of("type", "string", "description", "维度显示字段（单个）"));
        props.put("levelIndex", Map.of("type", "integer", "description", "层级维度按第几层汇总，1..N"));
        item.put("properties", props);
        item.put("required", List.of("dimensionModelCode"));
        return item;
    }

    private static Map<String, Object> conditionSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("dimensionModelCode", Map.of("type", "string", "description", "维度模型编码"));
        props.put("dimensionFieldName", Map.of("type", "string", "description", "维度字段名"));
        props.put("operator", Map.of("type", "string", "description", "运算符，默认 EQ"));
        props.put("value", Map.of("type", "string", "description", "取值；IN 时英文逗号分隔"));
        props.put("valueEnd", Map.of("type", "string", "description", "BETWEEN 的结束值"));
        props.put("logic", Map.of("type", "string", "description", "与上一条条件的组合逻辑 AND / OR，默认 AND"));
        item.put("properties", props);
        item.put("required", List.of("dimensionModelCode", "dimensionFieldName", "operator", "value"));
        return item;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        List<String> metricCodes = asStringList(arguments.get("metricCodes"));
        if (metricCodes.isEmpty()) {
            return Map.of("error", "缺少参数 metricCodes，请先通过 list_metrics 选择指标编码");
        }

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes);
        dto.setDimensions(parseDimensions(arguments.get("dimensions")));
        dto.setTimeRange(parseTimeRange(arguments.get("timeRange")));

        MetricFilterConfig config = new MetricFilterConfig();
        config.setConditions(parseConditions(arguments.get("conditions")));
        if (!config.isEmpty()) {
            try {
                dto.setFilterConfig(objectMapper.writeValueAsString(config));
            } catch (JsonProcessingException e) {
                return Map.of("error", "业务限定序列化失败: " + e.getOriginalMessage());
            }
        }

        try {
            Map<String, Object> queryResult = new LinkedHashMap<>(metricQueryService.query(dto));
            context.putArtifact(AiResult.ARTIFACT_METRIC_QUERY, queryResult);
            return toModelView(queryResult, dto);
        } catch (IllegalArgumentException | SQLException e) {
            LOG.debug("AI tool query_metric_data failed: {}", e.getMessage());
            return Map.of("error", "指标查询失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toModelView(Map<String, Object> queryResult, MetricQueryDTO dto) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", queryResult.get("columns"));
        List<?> rows = queryResult.get("rows") instanceof List<?> list ? list : List.of();
        result.put("totalRows", rows.size());
        result.put("rows", rows.size() > MAX_ROWS_TO_MODEL ? rows.subList(0, MAX_ROWS_TO_MODEL) : rows);
        if (rows.size() > MAX_ROWS_TO_MODEL) {
            result.put("truncated", true);
        }
        result.put("sql", queryResult.get("sql"));
        result.put("parsed", parsedRequest(dto));
        if (rows.isEmpty()) {
            result.put("hint", "查询结果为空，请核对业务限定取值或时间范围是否正确");
        }
        return result;
    }

    private Map<String, Object> parsedRequest(MetricQueryDTO dto) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("metricCodes", dto.getMetricCodes());
        parsed.put("dimensions", dto.getDimensions());
        if (dto.getFilterConfig() != null && !dto.getFilterConfig().isBlank()) {
            parsed.put("filterConfig", dto.getFilterConfig());
        }
        if (dto.getTimeRange() != null) {
            parsed.put("timeRange", dto.getTimeRange());
        }
        return parsed;
    }

    private List<MetricQueryFieldDTO> parseDimensions(Object raw) {
        List<MetricQueryFieldDTO> dimensions = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return dimensions;
        }
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            String code = asString(map.get("dimensionModelCode"));
            if (code == null || code.isBlank()) {
                continue;
            }
            MetricQueryFieldDTO field = new MetricQueryFieldDTO();
            field.setDimensionModelCode(code);
            List<String> fieldNames = asStringList(map.get("dimensionFieldNames"));
            if (!fieldNames.isEmpty()) {
                field.setDimensionFieldNames(fieldNames);
            } else {
                String single = asString(map.get("dimensionFieldName"));
                if (single != null && !single.isBlank()) {
                    field.setDimensionFieldName(single);
                }
            }
            Integer levelIndex = asInteger(map.get("levelIndex"));
            if (levelIndex != null) {
                field.setLevelIndex(levelIndex);
            }
            dimensions.add(field);
        }
        return dimensions;
    }

    private List<MetricFilterCondition> parseConditions(Object raw) {
        List<MetricFilterCondition> conditions = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return conditions;
        }
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            String code = asString(map.get("dimensionModelCode"));
            String fieldName = asString(map.get("dimensionFieldName"));
            if (code == null || code.isBlank() || fieldName == null || fieldName.isBlank()) {
                continue;
            }
            MetricFilterCondition condition = new MetricFilterCondition();
            condition.setType(MetricFilterCondition.TYPE_DIMENSION);
            condition.setDimensionModelCode(code);
            condition.setDimensionFieldName(fieldName);
            String operator = asString(map.get("operator"));
            condition.setOperator(operator == null || operator.isBlank() ? "EQ" : operator);
            condition.setValue(asString(map.get("value")));
            condition.setValueEnd(asString(map.get("valueEnd")));
            condition.setLogic(asString(map.get("logic")));
            conditions.add(condition);
        }
        return conditions;
    }

    private MetricQueryTimeRangeDTO parseTimeRange(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        String start = asString(map.get("start"));
        String end = asString(map.get("end"));
        if ((start == null || start.isBlank()) && (end == null || end.isBlank())) {
            return null;
        }
        MetricQueryTimeRangeDTO timeRange = new MetricQueryTimeRangeDTO();
        timeRange.setStart(start);
        timeRange.setEnd(end);
        return timeRange;
    }

    private static List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (value instanceof String text && !text.isBlank()) {
            result.add(text);
        }
        return result;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
