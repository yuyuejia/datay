package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.rag.MetricRagProperties;
import com.data.datafusion.ai.rag.MetricRagService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 指标知识库检索工具（只读）。
 *
 * <p>基于 DuckDB 向量索引，根据自然语言关键词召回最匹配的指标、维度字段与维度成员：
 * <ul>
 *     <li>metrics：指标候选（code / name / 相似度），用于「销售额 → 销售金额」的模糊匹配；</li>
 *     <li>dimensionFields：维度字段候选，用于选择分组维度；</li>
 *     <li>members：维度成员候选（dimensionModelCode / fieldName / memberValue），
 *         用于把「北京」映射到 dim_store.city 之类的维度限定。</li>
 * </ul>
 */
@Component
public class MetricRagSearchTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "retrieve_metric_context";

    private final MetricRagService metricRagService;
    private final MetricRagProperties properties;

    public MetricRagSearchTool(MetricRagService metricRagService, MetricRagProperties properties) {
        this.metricRagService = metricRagService;
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            基于指标知识库的语义检索，根据用户问题召回最匹配的指标、维度字段与维度成员。
            建议在处理用户问题的一开始就调用本工具，用 query 传入用户原话（或其中的关键词）。
            返回结果：
            - metrics：指标候选（code/name/score），据此选择最精确的指标编码；
            - dimensionFields：维度字段候选（dimensionModelCode/fieldName/score），据此选择分组维度；
            - members：维度成员候选（dimensionModelCode/fieldName/memberValue/score），
              据此把用户给出的具体值（如「北京」）映射为业务限定字段。
            可用 scope 限定检索范围（all/metric/dimension/member），
            用 dimensionModelCodes 把维度相关检索限定在候选维度内。
            检索结果为候选，仍需结合 describe_metrics 的真实元数据确认后再查询。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("query", Map.of("type", "string", "description", "用户问题或其中的关键词"));

        props.put("topK", Map.of("type", "integer", "description", "每类候选数量，默认取系统配置"));

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("type", "string");
        scope.put("enum", List.of("all", "metric", "dimension", "member"));
        scope.put("description", "检索范围，默认 all");
        props.put("scope", scope);

        Map<String, Object> dimensionModelCodes = new LinkedHashMap<>();
        dimensionModelCodes.put("type", "array");
        dimensionModelCodes.put("description", "可选的维度模型编码，限定维度字段与成员的检索范围");
        dimensionModelCodes.put("items", Map.of("type", "string"));
        props.put("dimensionModelCodes", dimensionModelCodes);

        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        String query = asString(arguments.get("query"));
        if (query == null || query.isBlank()) {
            return Map.of("error", "缺少参数 query");
        }
        int topK = asInt(arguments.get("topK"), properties.getTopK());
        String scope = asString(arguments.get("scope"));
        List<String> dimensionModelCodes = asStringList(arguments.get("dimensionModelCodes"));

        Map<String, Object> result = metricRagService.search(query, topK, dimensionModelCodes);
        if (Boolean.FALSE.equals(result.get("enabled"))) {
            return result;
        }
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return result;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        filtered.put("enabled", result.get("enabled"));
        filtered.put("provider", result.get("provider"));
        filtered.put("query", result.get("query"));
        String normalized = scope.toLowerCase(Locale.ROOT);
        if ("metric".equals(normalized)) {
            filtered.put("metrics", result.get("metrics"));
        } else if ("dimension".equals(normalized)) {
            filtered.put("dimensionFields", result.get("dimensionFields"));
        } else if ("member".equals(normalized)) {
            filtered.put("members", result.get("members"));
        } else {
            return result;
        }
        return filtered;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
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

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
