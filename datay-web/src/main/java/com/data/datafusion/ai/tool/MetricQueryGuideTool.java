package com.data.datafusion.ai.tool;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 指标问数引导工具（只读）。
 *
 * <p>MCP 客户端没有系统提示词，模型未必知道该按什么顺序调用指标相关工具。
 * 本工具返回「指标问数」的推荐工作流与硬性约束，供模型在第一次处理问数请求时调用。
 *
 * <p>它同样是一个标准 {@link AiTool}，但内置的 {@code MetricQueryAssistant} 不声明它，
 * 因此不会重复出现在内置助手的工具集合中，仅由 MCP 端点主动暴露。
 */
@Component
public class MetricQueryGuideTool implements AiTool {

    /** 工具名常量，供 MCP 端点显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "metric_query_guide";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            获取「指标智能问数」的推荐工作流与硬性约束。当用户提出任何指标数据问题时，建议先调用本工具了解标准步骤，
            再按顺序调用 retrieve_metric_context、list_metrics、describe_metrics、sample_dimension_values、query_metric_data。
            本工具无入参。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", LocalDate.now().toString());
        result.put("role", "你是 DataY 数据平台内置的「指标问数」助手，职责是把用户的自然语言问题拆解为「指标、维度、业务限定、时间范围」四要素，并调用指标查询工具获取真实数据。");
        result.put(
            "workflow",
            List.of(
                "1. retrieve_metric_context：先用用户原话做语义检索，获取指标、维度字段与维度成员的候选（含相似度）。检索结果是候选，需结合后续工具的真实元数据确认。",
                "2. list_metrics：核对指标清单（可用 keyword 过滤），把用户口语化的指标说法模糊匹配到真实指标编码；例如用户说「销售额」而指标库中是「销售金额」，选择最接近的指标并在最终回答中说明映射。",
                "3. describe_metrics：获取所选指标可用的维度、维度层级与时间字段。",
                "4. 识别分组维度：用户想按什么分组（如「按品类/按地区」）映射为 dimensions；层级维度按某层级汇总时传 levelIndex。",
                "5. 识别时间粒度：当用户要求「按月/按季/按年/按周/每天」的趋势或分布时，必须使用时间维度（isTimeDimension=true，通常为日期维度）的周期字段作为 dimensions，一次查询完成分组；不要把每个周期当作 timeRange 反复查询。例如按「每月」= dimensions 传 [{dimensionModelCode:\"dim_date\", dimensionFieldNames:[\"year\",\"month\"]}]。",
                "6. 识别业务限定：把「北京」这类具体值映射到最合适的维度字段；优先用 retrieve_metric_context 的 members 候选，仍不确定时用 sample_dimension_values 抽样确认；同一字段多值优先用 IN（英文逗号分隔），单值用 EQ。",
                "7. 识别时间范围：把「最近一周/上个月/近30天/今年/本季度」换算为 yyyy-MM-dd 的 start/end 作为 timeRange 限定整体区间；「最近一周」指含今天在内的最近 7 天；未提时间或要求「全部/历史」时可不传 timeRange。",
                "8. query_metric_data：执行查询获取数据；若结果为空，结合元数据反思过滤值或口径后调整入参重试。"
            )
        );
        result.put(
            "constraints",
            List.of(
                "所有指标编码、维度编码、字段名必须来自工具返回的真实元数据，严禁编造。",
                "只做只读查询，不得生成任何写操作。",
                "最终回答用简体中文，使用 Markdown 表格展示数据，不要输出 SQL。"
            )
        );
        return result;
    }
}
