package com.data.datafusion.ai.assistant;

import com.data.datafusion.ai.tool.MetricCatalogTool;
import com.data.datafusion.ai.tool.MetricDimensionValueTool;
import com.data.datafusion.ai.tool.MetricMetaTool;
import com.data.datafusion.ai.tool.MetricQueryDataTool;
import com.data.datafusion.ai.tool.MetricRagSearchTool;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 指标问数助手。
 *
 * <p>把用户的自然语言问题拆解为「指标 / 维度 / 业务限定 / 时间范围」四要素，
 * 模糊匹配指标库中的真实指标，并调用指标查询接口获取数据。
 * 查询结果会以结构化产物返回给前端，在对话中直接渲染数据表。
 */
@Component
@Order(1)
public class MetricQueryAssistant implements AiAssistant {

    public static final String ID = "metric-query";

    private static final List<String> SAMPLES = List.of(
        "最近一周北京的销售额",
        "上个月各品类的销售额和毛利率",
        "本月各区域的订单数和订单金额",
        "今年每个月的销售趋势"
    );

    private static final String FINALIZE_INSTRUCTION = """
        你已获得足够信息，请立即给出最终结论。不要再请求任何工具调用。
        输出格式要求：
        1. 先以「查询解析」列出：指标（含匹配到的指标名与编码）、分组维度、业务限定、时间范围；
        2. 若用户说法与指标库不一致，明确说明映射关系（例如「将『销售额』匹配为指标『销售金额』」）；
        3. 用 Markdown 表格展示数据，以用户所问口径的主查询结果为准；若为核对口径执行了附加查询，
           仅在必要时简要说明，不要作为主要结果，也不要输出 SQL；
        4. 对关键数字做一句简要说明。
        """;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "智能问数助手";
    }

    @Override
    public String description() {
        return "用一句话描述你想看什么指标，我来拆解维度与筛选条件并查询数据";
    }

    @Override
    public List<String> samplePrompts() {
        return SAMPLES;
    }

    /**
     * 指标问数可用工具：指标清单、指标元数据、维度取值抽样、指标数据查询。
     */
    @Override
    public Set<String> toolNames() {
        return Set.of(
            MetricRagSearchTool.NAME,
            MetricCatalogTool.NAME,
            MetricMetaTool.NAME,
            MetricDimensionValueTool.NAME,
            MetricQueryDataTool.NAME
        );
    }

    @Override
    public String finalizeInstruction() {
        return FINALIZE_INSTRUCTION;
    }

    @Override
    public String buildSystemPrompt(AiAssistantContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 DataY 数据平台内置的「指标问数」助手，职责是把用户的自然语言问题拆解为\n");
        sb.append("「指标、维度、业务限定、时间范围」四要素，并调用指标查询工具获取真实数据。\n\n");
        sb.append("今天是 ").append(LocalDate.now()).append("，所有相对时间都应换算为具体日期。\n\n");
        sb.append("工作方式：\n");
        sb.append("0. 先用 retrieve_metric_context 以用户原话做语义检索，获取指标、维度字段与维度成员的候选（含相似度）；\n");
        sb.append("   检索是候选，需结合后续工具的真实元数据确认。若检索不可用，再回退到 list_metrics。\n");
        sb.append("1. 用 list_metrics（可带 keyword）核对完整指标清单，把用户口语化的指标说法模糊匹配到真实指标编码；\n");
        sb.append("   例如用户说「销售额」而指标库中是「销售金额」，应选择 retrieve_metric_context 与 list_metrics 共同指向的最接近指标，并在最终回答中说明该映射。\n");
        sb.append("2. 用 describe_metrics 获取所选指标可用的维度、维度层级与时间字段。\n");
        sb.append("3. 识别维度：用户想按什么分组（如「按品类/按地区」），映射为 dimensions；层级维度按某层级汇总时传 levelIndex。\n");
        sb.append("   关键-时间维度：当用户要求「每个月的趋势/各月/按月/按季度/按年/按周/每天」等按时间粒度分组时，\n");
        sb.append("   必须使用时间维度（describe_metrics 中 isTimeDimension=true 的维度，通常为日期维度）的周期字段作为 dimensions，\n");
        sb.append("   例如按「每月」= dimensions 传 [{dimensionModelCode:\"dim_date\", dimensionFieldNames:[\"year\",\"month\"]}]，一次查询完成分组；\n");
        sb.append("   不要把每个月份当成 timeRange 逐月分别查询。\n");
        sb.append("4. 识别业务限定：把「北京」这类具体值映射到最合适的维度字段；\n");
        sb.append("   优先使用 retrieve_metric_context 返回的 members 候选确定所属维度与字段；\n");
        sb.append("   若仍不确定该值属于哪个字段（城市/省份/大区），用 sample_dimension_values 抽样确认；\n");
        sb.append("   同一字段的多个取值优先用 IN（英文逗号分隔），单值用 EQ。\n");
        sb.append("5. 识别整体时间范围：把「最近一周/上个月/近30天/今年/本季度」等换算为 yyyy-MM-dd 的 start/end，作为 timeRange 限定整体区间；\n");
        sb.append("   「最近一周」指含今天在内的最近 7 天。若用户未提时间或要求「全部/历史」，可不传 timeRange。\n");
        sb.append("6. 用 query_metric_data 执行查询获取数据。\n\n");
        sb.append("硬性约束：\n");
        sb.append("- 所有指标编码、维度编码、字段名必须来自工具返回的真实元数据，严禁编造。\n");
        sb.append("- 只做只读查询，不得生成任何写操作。\n");
        sb.append("- 最终回答用简体中文，使用 Markdown 表格展示数据；数据以用户所问口径的主查询为准，不要输出 SQL。\n");
        return sb.toString();
    }
}
