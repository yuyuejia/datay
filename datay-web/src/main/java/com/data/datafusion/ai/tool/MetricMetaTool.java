package com.data.datafusion.ai.tool;

import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.dto.MetricQueryDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 指标查询元数据工具（只读）。
 *
 * <p>在确定指标后，返回该指标可用的分组维度、维度字段（含层级维度的层级）、
 * 时间字段与事实表，供模型把用户需求拆解为维度、业务限定与时间范围。
 */
@Component
public class MetricMetaTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "describe_metrics";

    private final MetricQueryService metricQueryService;

    public MetricMetaTool(MetricQueryService metricQueryService) {
        this.metricQueryService = metricQueryService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            查询指定指标可用的查询元数据：可分组维度、各维度的字段与层级、时间字段、事实表。
            在选出指标编码（metricCodes）后调用，用于确定用户需求中的维度、业务限定字段与时间字段。
            返回的 dimensions[].dimensionModelCode 用于构造维度与业务限定；timeFields[].fieldName 为事实表时间字段。
            层级维度（isHierarchy=true）会给出 levels[].levelIndex 与对应的 idField/nameField，按某层级汇总时传 levelIndex。
            时间维度（isTimeDimension=true）为日期维度，periodFields 列出可用于时间分组的周期字段（如 year/quarter/month/week_of_year/day 等）。
            每个维度会返回其默认显示字段（recommendedDisplayField，可能含 displayFieldName）；维度分组与图表 x 轴应使用该字段，
            普通维度通常为 *_name，层级维度为 levels[].nameField，不要使用 *_sk / *_id 代理键。
            当用户要求「按年/季度/月/周/天的趋势或分布」时，应使用该时间维度的周期字段作为分组维度，而不是把每个周期当作 timeRange 分别查询。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> metricCodes = new LinkedHashMap<>();
        metricCodes.put("type", "array");
        metricCodes.put("description", "指标编码列表，来自 list_metrics 的 code");
        metricCodes.put("items", Map.of("type", "string"));
        props.put("metricCodes", metricCodes);
        schema.put("properties", props);
        schema.put("required", List.of("metricCodes"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        List<String> metricCodes = asStringList(arguments.get("metricCodes"));
        if (metricCodes.isEmpty()) {
            return Map.of("error", "缺少参数 metricCodes，请先通过 list_metrics 选择指标编码");
        }
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes);
        try {
            Map<String, Object> meta = metricQueryService.queryMeta(dto);
            Map<String, Object> result = new LinkedHashMap<>(meta);
            result.put(
                "hint",
                "业务限定请使用 type=DIMENSION，并填写 dimensionModelCode 与 dimensionFieldName；" +
                "时间范围请换算为具体日期区间；" +
                "若要求按月/季/周/年的趋势，请使用 isTimeDimension=true 的维度并将 periodFields 中的字段作为分组维度传入 dimensions"
            );
            return result;
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
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
}
