package com.data.datafusion.ai.tool;

import com.data.datafusion.domain.Metric;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.dto.MetricDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 指标清单工具（只读）。
 *
 * <p>把启用中的指标名称、编码、口径描述、单位、事实表等信息提供给模型，
 * 使其能把用户口语化说法（如「销售额」）模糊匹配到指标库中的真实指标（如「销售金额」）。
 * 相比一次性把全部指标塞进提示词，按需查询并支持关键字过滤能有效控制 token 消耗。
 */
@Component
public class MetricCatalogTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "list_metrics";

    /** 单次返回的指标数量上限，避免上下文爆炸。 */
    private static final int MAX_LIST_SIZE = 100;

    private final MetricService metricService;

    public MetricCatalogTool(MetricService metricService) {
        this.metricService = metricService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            查询指标库中启用状态的指标清单，用于把用户口语化的指标说法匹配到真实指标编码。
            每个指标返回 code（查询时使用）、name（中文名）、description（统计口径）、unit、metricType、factTableName。
            当用户提到的指标名称与指标库不完全一致时（例如用户说「销售额」，指标库中是「销售金额」），
            请依据 name 与 description 做语义匹配，选择最接近的指标，并在最终回答中说明该映射关系。
            可选参数 keyword 用于按名称/编码/口径做模糊过滤；不传则返回全部指标。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "可选，指标名称/编码/口径的模糊过滤关键字");
        props.put("keyword", keyword);
        schema.put("properties", props);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        String keyword = asString(arguments.get("keyword"));
        String normalized = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> metrics = new ArrayList<>();
        boolean truncated = false;
        for (MetricDTO dto : metricService.findAllSimple()) {
            if (!Metric.STATUS_ENABLED.equals(dto.getStatus())) {
                continue;
            }
            if (normalized != null && !normalized.isEmpty() && !matches(dto, normalized)) {
                continue;
            }
            if (metrics.size() >= MAX_LIST_SIZE) {
                truncated = true;
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", dto.getCode());
            item.put("name", dto.getName());
            if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
                item.put("description", dto.getDescription());
            }
            item.put("metricType", dto.getMetricType());
            if (dto.getUnit() != null && !dto.getUnit().isBlank()) {
                item.put("unit", dto.getUnit());
            }
            if (dto.getFactTableName() != null && !dto.getFactTableName().isBlank()) {
                item.put("factTableName", dto.getFactTableName());
            }
            metrics.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", metrics.size());
        result.put("truncated", truncated);
        result.put("metrics", metrics);
        if (metrics.isEmpty()) {
            result.put("hint", "未匹配到指标，请放宽关键字或调用时不传 keyword 查看全部指标");
        }
        return result;
    }

    private boolean matches(MetricDTO dto, String keyword) {
        return contains(dto.getName(), keyword) || contains(dto.getCode(), keyword) || contains(dto.getDescription(), keyword);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
