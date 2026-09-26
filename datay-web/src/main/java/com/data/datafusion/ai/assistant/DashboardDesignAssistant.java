package com.data.datafusion.ai.assistant;

import com.data.datafusion.ai.DashboardJsonExtractor;
import com.data.datafusion.ai.tool.DimensionInfoTool;
import com.data.datafusion.ai.tool.MetricCatalogTool;
import com.data.datafusion.ai.tool.MetricDimensionValueTool;
import com.data.datafusion.ai.tool.MetricMetaTool;
import com.data.datafusion.ai.tool.MetricQueryDataTool;
import com.data.datafusion.ai.tool.MetricRagSearchTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 看板设计助手。
 *
 * <p>根据用户的自然语言诉求，探查指标口径与维度，一次性产出一份完整的看板定义 JSON：
 * 数据集（仅通过指标查询接口取数）、组件（ECharts option / HTML / 表格 / KPI）、布局与筛选器。
 * 助手自主决定是否需要筛选器以及添加哪些筛选器。
 */
@Component
@Order(5)
public class DashboardDesignAssistant implements AiAssistant {

    public static final String ID = "dashboard-design";

    private static final List<String> SAMPLES = List.of(
        "帮我做一个电商销售分析看板",
        "生成一个按区域和品类分析销售与毛利的看板",
        "做一个近期订单趋势与门店 Top10 的看板"
    );

    private final ObjectMapper objectMapper;

    public DashboardDesignAssistant(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "看板设计助手";
    }

    @Override
    public String description() {
        return "用一句话描述你想要的看板，我来确定数据集、布局、图表与筛选器";
    }

    @Override
    public List<String> samplePrompts() {
        return SAMPLES;
    }

    @Override
    public Set<String> toolNames() {
        return Set.of(
            MetricRagSearchTool.NAME,
            MetricCatalogTool.NAME,
            MetricMetaTool.NAME,
            DimensionInfoTool.NAME,
            MetricDimensionValueTool.NAME,
            MetricQueryDataTool.NAME
        );
    }

    @Override
    public String buildSystemPrompt(AiAssistantContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 DataY 数据平台内置的「看板设计」助手，职责是根据用户的自然语言诉求，设计一个可在浏览器独立打开的\n");
        sb.append("数据分析看板，产出完整的看板定义 JSON。\n\n");

        sb.append("工作步骤：\n");
        sb.append("1. 确定数据集，支持多个数据集，基于指标查询接口定义数据集：\n");
        sb.append("   a. 先用 retrieve_metric_context 以用户原话做语义检索，获取候选指标、维度（含相似度）；\n");
        sb.append("   b. 用 list_metrics（可带 keyword）核对完整指标清单，把用户口语化的说法模糊匹配到真实指标编码，\n");
        sb.append("      例如用户说「销售额」而指标库中是「销售金额」，应选择最接近的指标并在说明中给出映射；\n");
        sb.append("   c. 用 describe_metrics 获取所选指标可用的维度，其中每个维度会返回其默认显示字段\n");
        sb.append("      （displayFieldName / recommendedDisplayField）与时间字段；\n");
        sb.append("   d. 【确定展示字段】图表的维度展示字段直接使用该维度设置的默认显示字段，\n");
        sb.append("      把它放入 metricQuery.dimensions[].dimensionFieldNames：\n");
        sb.append("      - 普通维度：使用 describe_metrics 返回的 recommendedDisplayField（即维度默认显示字段，通常为 *_name，如 store_name、brand_name）；\n");
        sb.append("      - 层级维度：用 levelIndex 指定层级，展示字段取 levels[].nameField（levelN_name），无需手写 id；\n");
        sb.append("      - 时间维度：从 periodFields 中按所需粒度选择 year/quarter/month/day 等；\n");
        sb.append("      - 如需查看维度的完整字段清单，可再调用 describe_dimension。\n");
        sb.append("   e. 需要维度筛选值时，用 sample_dimension_values 确认维度成员可用。\n");
        sb.append("   重要约束：\n");
        sb.append("   - 所有指标编码、维度编码、字段名必须来自工具返回的真实元数据，严禁编造；\n");
        sb.append("   - 维度分组/展示使用维度的默认显示字段，禁止使用 维度主键字段；\n");
        sb.append("   - 每个数据集的 type 一律为 METRIC，并给出 metricQuery（metricCodes / dimensions / timeRange）；\n");
        sb.append("2. 依据指标查询结果的列结构设计布局与图表：为每个数据集选取合适的可视化（趋势用折线、对比用柱状、\n");
        sb.append("   占比用饼图、明细用表格、核心数字用 KPI 卡片），并直接给出完整的 ECharts option（含 tooltip/xAxis/yAxis/series 等样式）。\n");
        sb.append("   默认 x 轴规则：图表的 x 轴（xAxis 数据，以及 dataRef.categoryField）使用该维度设置的默认显示字段——\n");
        sb.append("   即 dimensions[].dimensionFieldNames 中的显示字段（如 store_name、level1_name），禁止使用 *_sk / *_id。\n");
        sb.append("3. 决定筛选器：先判断用户诉求是否隐含可交互维度；需要时再添加，不需要则输出空数组。\n");
        sb.append("   - 若存在时间维度，默认增加时间范围筛选，默认时间范围为空，显示所有数据\n");
        sb.append("   - 若含常用业务维度（区域/品类/门店/渠道等），按需加 select/multiSelect；\n");
        sb.append("   - 金额、数量等数值阈值可用 numberRange；文本模糊匹配用 input；\n");
        sb.append("   - 最多 1 个时间筛选 + 3 个维度/数值筛选，宁少勿多；无必要则 [];\n");
        sb.append("   - 下拉选项优先 options.mode=dimension（动态维度成员），并设置合理的 defaultValue；\n");
        sb.append("   - 添加维度筛选器前，先用 sample_dimension_values 确认该维度真实存在且有成员数据；\n");
        sb.append("     维度不存在或没有数据的，不要添加为筛选器。\n\n");

        sb.append("输出一个看板定义 JSON 对象，且仅输出该 JSON（放在 ```json 代码块中）。示例结构如下：\n");
        sb.append("""
            {
              "title": "看板标题",
              "description": "一句话说明",
              "layout": { "columns": 12, "rowHeight": 90, "gap": 16 },
              "datasets": [
                {
                  "id": "ds_sales",
                  "name": "门店销售额",
                  "type": "METRIC",
                  "metricQuery": {
                    "metricCodes": ["sales_amount"],
                    "dimensions": [ { "dimensionModelCode": "dim_store", "dimensionFieldNames": ["store_name"] } ],
                    "timeRange": { "start": "2024-01-01", "end": "2024-12-31" }
                  }
                }
              ],
              "widgets": [
                {
                  "id": "w_store",
                  "title": "门店销售额 Top10",
                  "type": "echarts",
                  "layout": { "x": 0, "y": 0, "w": 8, "h": 4 },
                  "datasetId": "ds_sales",
                  "dataRef": {
                    "categoryField": "store_name",
                    "series": [ { "name": "销售额", "field": "sales_amount" } ],
                    "sortField": "sales_amount", "sortOrder": "desc", "limit": 10
                  },
                  "echartsOption": {
                    "tooltip": { "trigger": "axis" },
                    "xAxis": { "type": "category" },
                    "yAxis": { "type": "value" },
                    "series": [ { "type": "bar" } ]
                  }
                }
              ],
              "filters": [
                {
                  "id": "region",
                  "type": "multiSelect",
                  "label": "区域",
                  "options": { "mode": "dimension", "dimensionModelCode": "dim_store", "dimensionFieldName": "region" },
                  "defaultValue": [],
                  "bindings": [
                    { "datasetId": "ds_sales", "kind": "metricDimension", "dimensionModelCode": "dim_store", "dimensionFieldName": "region" }
                  ]
                }
              ]
            }
            """);

        sb.append("\n字段约束：\n");
        sb.append("- 数据集 type 只能是 METRIC；metricQuery 至少包含一个 metricCodes，dimensions 用于分组，timeRange 限定整体区间。\n");
        sb.append("- dimensions 每一项：先用 describe_dimension 获取维度基本信息与字段清单，再按 recommendedDisplayField 选名称字段：\n");
        sb.append("  普通维度取 *_name（如 store_name）；层级维度用 levelIndex（展示字段取 levels[].nameField）；时间维度取 periodFields 中的周期字段；\n");
        sb.append("- 组件 type 取值：echarts / html / table / kpi。echarts 必须给出 echartsOption 与 dataRef；\n");
        sb.append("  html 用 html 字段承载简单 HTML 片段；table 按数据集渲染明细表；kpi 展示单值指标。\n");
        sb.append("- 组件标题统一用 widget.title，ECharts option 内不要再写 title（会被忽略，避免与卡片标题重复）。\n");
        sb.append("- layout 使用 12 列栅格：x 起点(0-11)、y 行号、w 宽度(1-12)、h 高度(2-24)。组件横纵不得重叠。\n");
        sb.append("- 指标卡（kpi）默认 w=3、h=2，多个指标卡在同一行横向并排，避免过高（h 不超过 4）。\n");
        sb.append("- dataRef 约定：x 轴（分类轴）默认使用该维度的显示字段，即 categoryField 应等于 dimensionFieldNames 中的名称字段\n");
        sb.append("  （如 store_name、level1_name），与 xAxis 数据一致，禁止使用 *_sk / *_id；series[].field 为数值字段；\n");
        sb.append("  可选 seriesField 表示按该字段透视成多系列（同样使用其名称字段）；limit/sortField/sortOrder 控制排序与截断。\n");
        sb.append("- 筛选器 binding.kind 取值：metricTime（时间范围）、metricDimension（维度条件，需 dimensionModelCode+dimensionFieldName）、\n");
        sb.append("- 筛选器未设值时等价于不过滤、返回全部数据，无需为「空值」编写特殊逻辑。\n");
        sb.append("- 只做只读查询，不得生成任何写操作。\n");

        Object currentSpec = context.getContextData().get("currentSpec");
        if (currentSpec != null) {
            sb.append("\n当前看板定义（JSON）如下，请在它基础上按用户本次的最新要求进行调整：\n");
            sb.append(serialize(currentSpec)).append("\n");
            sb.append("调整要求：\n");
            sb.append("- 输出调整后的【完整】看板 JSON，而不是片段或差异；\n");
            sb.append("- 保留用户未要求变更的数据集、组件、布局与筛选器，仅改动相关部分；\n");
            sb.append("- 保持已有 id 稳定；确需新增组件时追加新的 id（如 w2、ds2），删除时移除对应项；\n");
            sb.append("- 若用户明确表示要做「另一个/全新的看板」或当前定义与需求无关，则重新设计，不必沿用；\n");
            sb.append("- 调整数据集或筛选器后，仍需按前述规则用工具核验指标编码与维度字段的真实性。\n");
        }
        return sb.toString();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    @Override
    public String finalizeInstruction() {
        return """
            你已获得足够信息，请立即给出最终结论。不要再请求任何工具调用。
            只输出一个完整的看板定义 JSON（放在 ```json 代码块中），不要输出任何解释、SQL 或额外文字。
            确保 JSON 合法，datasets 与 widgets 均非空，且所有数据集 type 均为 METRIC（只从指标接口取数）。
            """;
    }

    @Override
    public Map<String, Object> extractSpec(String content) {
        String json = DashboardJsonExtractor.extract(content);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
