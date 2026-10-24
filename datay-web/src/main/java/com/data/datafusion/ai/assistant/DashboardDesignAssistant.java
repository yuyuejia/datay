package com.data.datafusion.ai.assistant;

import com.data.datafusion.ai.DashboardJsonExtractor;
import com.data.datafusion.ai.tool.DimensionInfoTool;
import com.data.datafusion.ai.tool.ListAnalysisDatasetsTool;
import com.data.datafusion.ai.tool.MetricCatalogTool;
import com.data.datafusion.ai.tool.MetricDimensionValueTool;
import com.data.datafusion.ai.tool.MetricMetaTool;
import com.data.datafusion.ai.tool.MetricQueryDataTool;
import com.data.datafusion.ai.tool.MetricRagSearchTool;
import com.data.datafusion.ai.tool.PrepareMetricDatasetTool;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 看板设计助手。
 *
 * <p>根据用户的自然语言诉求，先确定可复用的指标数据集，再产出一份完整的看板定义 JSON：
 * 数据集引用（refId）、组件（ECharts option / HTML / 表格 / KPI）、布局与筛选器。
 * 助手不会在看板 spec 中内嵌指标定义；新建的指标数据集会在生成结束后统一落入数据集库。
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
    private final AnalysisDatasetService datasetService;

    public DashboardDesignAssistant(ObjectMapper objectMapper, AnalysisDatasetService datasetService) {
        this.objectMapper = objectMapper;
        this.datasetService = datasetService;
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
        return "先确定可复用的指标数据集，再生成布局、图表与筛选器";
    }

    @Override
    public List<String> samplePrompts() {
        return SAMPLES;
    }

    @Override
    public Set<String> toolNames() {
        return Set.of(
            ListAnalysisDatasetsTool.NAME,
            PrepareMetricDatasetTool.NAME,
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
        sb.append("1. 先确定指标数据集，再生成看板。看板只能引用数据集库中的数据集，禁止在 datasets 中内嵌 metricQuery。\n");
        sb.append("   a. 先调用 list_analysis_datasets，keyword 使用用户提到的业务主题/指标名称，type 固定为 METRIC。\n");
        sb.append("      如果已有数据集能覆盖看板所需的指标和维度（允许包含额外指标/维度），直接使用返回的 id 作为 refId，\n");
        sb.append("      不要再创建更窄的新数据集。可结合 definition / fields 核对指标编码、维度字段和列名。\n");
        sb.append("   b. 如果没有能覆盖的数据集，再用 retrieve_metric_context 对用户原话做语义检索；\n");
        sb.append("      用 list_metrics（可带 keyword）把「销售额」等口语匹配到真实指标编码；\n");
        sb.append("      用 describe_metrics 获取维度、默认展示字段和时间字段；\n");
        sb.append("      需要维度筛选值时，用 sample_dimension_values 确认成员可用。\n");
        sb.append("   c. 【聚合数据集】先汇总整个看板需要的全部指标、维度、业务限定和时间范围，再准备一个数据集：\n");
        sb.append("      默认只调用一次 prepare_metric_dataset(name=数据集名称, metricQuery={metricCodes,dimensions,filterConfig,timeRange})；\n");
        sb.append("      用户有业务限定时传入 filterConfig，否则可省略。\n");
        sb.append("      metricCodes 使用全部指标的并集，dimensions 使用全部图表维度的并集；各图表从同一数据集中选择所需字段。\n");
        sb.append("      工具会优先复用能覆盖本次需求的已有数据集，或登记一份待创建草稿，并返回 refId。\n");
        sb.append("      生成结束后系统会自动把草稿落库为分析数据集。\n");
        sb.append("      只有不同指标的时间范围/口径无法合并时才创建多个数据集；能合并时禁止按图表拆分数据集。\n");
        sb.append("   d. 维度展示字段规则：\n");
        sb.append("      - 普通维度使用 recommendedDisplayField（通常为 *_name，如 store_name、brand_name）；\n");
        sb.append("      - 层级维度用 levelIndex 指定层级，展示字段取 levels[].nameField；\n");
        sb.append("      - 时间维度从 periodFields 中选择 year/quarter/month/day 等粒度；\n");
        sb.append("      - 禁止使用 *_sk / *_id 等代理键作为展示字段。\n");
        sb.append("   重要约束：\n");
        sb.append("   - 所有指标编码、维度编码、字段名必须来自工具返回的真实元数据，严禁编造；\n");
        sb.append("   - 看板 datasets 只允许保存 { id, refId, name, type }，禁止出现 metricQuery、sql、params、dataSourceId；\n");
        sb.append("   - refId 必须来自 list_analysis_datasets 的 id 或 prepare_metric_dataset 的返回值；\n");
        sb.append("   - 一个看板默认只使用一个指标数据集，多个图表共享该数据集的不同指标/维度字段。\n");
        sb.append("2. 基于已确定的指标数据集设计布局与图表：为每个数据集选取合适的可视化（趋势用折线、对比用柱状、\n");
        sb.append("   占比用饼图、明细用表格、核心数字用 KPI 卡片），并直接给出完整的 ECharts option（含 tooltip/xAxis/yAxis/series 等样式）。\n");
        sb.append("   默认 x 轴规则：图表的 x 轴（xAxis 数据，以及 dataRef.categoryField）使用该数据集维度对应的默认显示字段，\n");
        sb.append("   禁止使用 *_sk / *_id。图表建议设置 dataRef.groupBy=true，由前端按分类字段聚合数值。\n");
        sb.append("3. 决定筛选器：先判断用户诉求是否隐含可交互维度；需要时再添加，不需要则输出空数组。\n");
        sb.append("   - 筛选器直接配置 datasetId 与字段 field，不再使用 bindings；\n");
        sb.append("   - 字段 field 必须是该数据集真实存在的列名/维度展示字段；\n");
        sb.append("   - 若存在时间维度，默认增加时间范围筛选，默认时间范围为空，显示所有数据；\n");
        sb.append("   - 若含常用业务维度（区域/品类/门店/渠道等），按需加 select/multiSelect；\n");
        sb.append("   - 金额、数量等数值阈值可用 numberRange；文本模糊匹配用 input；\n");
        sb.append("   - 最多 1 个时间筛选 + 3 个维度/数值筛选，宁少勿多；无必要则 [];\n");
        sb.append("   - 下拉选项由数据集字段自动去重生成，无需配置 options.mode=dimension 或 bindings。\n\n");

        sb.append("输出一个看板定义 JSON 对象，且仅输出该 JSON（放在 ```json 代码块中）。示例结构如下：\n");
        sb.append("""
            {
              "title": "看板标题",
              "description": "一句话说明",
              "layout": {
                "mode": "standard",
                "columns": 24,
                "rowHeight": 40,
                "gap": 12,
                "screen": { "width": 1920, "height": 1080 }
              },
              "datasets": [
                {
                  "id": "ds_sales",
                  "refId": "list_analysis_datasets 或 prepare_metric_dataset 返回的 refId",
                  "name": "门店销售分析数据集",
                  "type": "METRIC"
                }
              ],
              "widgets": [
                {
                  "id": "w_store",
                  "title": "门店销售额 Top10",
                  "type": "echarts",
                  "layout": { "x": 0, "y": 0, "w": 16, "h": 8 },
                  "datasetId": "ds_sales",
                  "dataRef": {
                    "categoryField": "store_name",
                    "series": [ { "name": "销售额", "field": "sales_amount" } ],
                    "groupBy": true,
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
                  "datasetId": "ds_sales",
                  "field": "region",
                  "defaultValue": []
                }
              ]
            }
            """);

        sb.append("\n字段约束：\n");
        sb.append("- datasets 必须引用数据集库：{ id, refId, name, type }；禁止内嵌 metricQuery、sql 或数据源定义。\n");
        sb.append("- refId 必须来自 list_analysis_datasets 或 prepare_metric_dataset 的返回值；name/type 可沿用工具返回结果。\n");
        sb.append("- 组件 type 取值：echarts / html / table / kpi。echarts 必须给出 echartsOption 与 dataRef；\n");
        sb.append("  html 用 html 字段承载简单 HTML 片段；table 按数据集渲染明细表；kpi 展示单值指标。\n");
        sb.append("- 组件标题统一用 widget.title，ECharts option 内不要再写 title（会被忽略，避免与卡片标题重复）。\n");
        sb.append("- layout 使用 24 列栅格：x 起点(0-23)、y 行号、w 宽度(1-24)、h 高度(2-40)。组件横纵不得重叠。\n");
        sb.append("- 指标卡（kpi）默认 w=6、h=4，多个指标卡在同一行横向并排，避免过高（h 不超过 8）。\n");
        sb.append("- 普通图表默认 w=12、h=8，整行图表使用 w=24；同一行组件宽度之和不要超过 24。\n");
        sb.append("- 需要投屏 / 大屏展示时，把 layout.mode 设为 screen，并在 layout.screen 指定 width / height（默认 1920×1080）。\n");
        sb.append("- 大屏可选设置：layout.screen.fitMode(contain=居中/cover=裁切/stretch=铺满，默认 stretch)、theme(light/dark)、autoRefreshSeconds(0-86400)；\n");
        sb.append("  背景色/渐变/背景图由用户在设置面板配置，不要生成 background.imagePath。\n");
        sb.append("- dataRef 约定：categoryField 使用数据集中的维度展示字段，禁止使用 *_sk / *_id；series[].field 为数值字段；\n");
        sb.append("  可选 seriesField 表示按该字段透视成多系列；groupBy=true 表示图表按分类字段聚合；limit/sortField/sortOrder 控制排序与截断。\n");
        sb.append("- 筛选器使用 datasetId + field 直接绑定数据集字段，不再生成 bindings；field 必须是数据集真实列名。\n");
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
            sb.append("- 新增或调整指标口径时，先通过 list_analysis_datasets / prepare_metric_dataset 确定数据集 refId；\n");
            sb.append("- 数据集与筛选器字段仍需用工具核验真实性，禁止在 spec 内嵌 metricQuery。\n");
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
            确保 JSON 合法，datasets 与 widgets 均非空；每个数据集必须包含 refId，禁止内嵌 metricQuery/sql，
            refId 只能来自 list_analysis_datasets 的 id 或 prepare_metric_dataset 的返回值。
            能合并时默认只使用一个指标数据集，不要按图表拆分数据集。
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

    /**
     * 把工具阶段准备的指标数据集草稿落库，并将看板 spec 中的临时 refId 替换为真实数据集 id。
     * 同时兼容模型误输出内嵌 metricQuery 的情况，确保最终看板只保存数据集引用。
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> postProcessSpec(Map<String, Object> spec, Map<String, Object> artifacts) {
        if (spec == null || !(spec.get("datasets") instanceof List<?> rawDatasets)) {
            return spec;
        }
        Map<String, Object> drafts = artifacts != null && artifacts.get(PrepareMetricDatasetTool.ARTIFACT_DRAFTS) instanceof Map<?, ?> rawDrafts
            ? (Map<String, Object>) rawDrafts
            : Map.of();

        List<Map<String, Object>> resolvedDatasets = new ArrayList<>();
        for (Object element : rawDatasets) {
            if (!(element instanceof Map<?, ?> rawDataset)) {
                continue;
            }
            Map<String, Object> dataset = new LinkedHashMap<>((Map<String, Object>) rawDataset);
            String refId = asString(dataset.get("refId"));
            Map<String, Object> draft = null;
            if (refId != null && refId.startsWith(PrepareMetricDatasetTool.DRAFT_PREFIX)) {
                String key = refId.substring(PrepareMetricDatasetTool.DRAFT_PREFIX.length());
                Object rawDraft = drafts.get(key);
                if (rawDraft instanceof Map<?, ?> draftMap) {
                    draft = new LinkedHashMap<>((Map<String, Object>) draftMap);
                } else {
                    throw new IllegalArgumentException("指标数据集草稿已失效，请重新生成看板");
                }
            } else if ((refId == null || refId.isBlank()) && dataset.get("metricQuery") instanceof Map<?, ?> metricQuery) {
                // 兜底：模型未使用 prepare_metric_dataset 而直接内嵌 metricQuery 时，也统一转为数据集引用。
                draft = draftFromMetricQuery(dataset, (Map<String, Object>) metricQuery);
            }

            if (draft != null) {
                AnalysisDatasetDTO saved = persistDraft(draft);
                dataset.put("refId", saved.getId());
                dataset.put("name", saved.getName());
                dataset.put("type", saved.getType());
            } else if (refId != null && !refId.isBlank()) {
                datasetService.findOne(refId).ifPresent(existing -> {
                    dataset.putIfAbsent("type", existing.getType());
                    if (asString(dataset.get("name")) == null || asString(dataset.get("name")).isBlank()) {
                        dataset.put("name", existing.getName());
                    }
                });
            }

            // 看板只保留数据集引用，去除所有内嵌定义。
            dataset.remove("metricQuery");
            dataset.remove("sql");
            dataset.remove("params");
            dataset.remove("dataSourceId");
            dataset.remove("fields");
            resolvedDatasets.add(dataset);
        }

        Map<String, Object> result = new LinkedHashMap<>(spec);
        result.put("datasets", resolvedDatasets);
        return result;
    }

    private Map<String, Object> draftFromMetricQuery(Map<String, Object> dataset, Map<String, Object> metricQuery) {
        Map<String, Object> draft = new LinkedHashMap<>();
        String name = asString(dataset.get("name"));
        draft.put("name", name == null || name.isBlank() ? "指标数据集" : name);
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("metricQuery", metricQuery);
        try {
            draft.put("definition", objectMapper.writeValueAsString(definition));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("指标数据集定义序列化失败：" + e.getOriginalMessage());
        }
        return draft;
    }

    private AnalysisDatasetDTO persistDraft(Map<String, Object> draft) {
        String definition = asString(draft.get("definition"));
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("指标数据集草稿缺少 definition");
        }
        for (AnalysisDatasetDTO existing : datasetService.findAllSimple()) {
            if (
                AnalysisDataset.TYPE_METRIC.equalsIgnoreCase(existing.getType()) &&
                sameDefinition(existing.getDefinition(), definition)
            ) {
                return existing;
            }
        }
        String name = asString(draft.get("name"));
        AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
        dto.setName(name == null || name.isBlank() ? "指标数据集" : name);
        dto.setDescription(asString(draft.get("description")));
        dto.setType(AnalysisDataset.TYPE_METRIC);
        dto.setDefinition(definition);
        return datasetService.save(dto);
    }

    private boolean sameDefinition(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        try {
            JsonNode leftNode = objectMapper.readTree(left);
            JsonNode rightNode = objectMapper.readTree(right);
            return leftNode.equals(rightNode);
        } catch (JsonProcessingException e) {
            return left.equals(right);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
