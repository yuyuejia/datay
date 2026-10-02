package com.data.datafusion.mcp.harness;

import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolContext;
import com.data.datafusion.ai.tool.MetricCatalogTool;
import com.data.datafusion.ai.tool.MetricDimensionValueTool;
import com.data.datafusion.ai.tool.MetricMetaTool;
import com.data.datafusion.ai.tool.MetricQueryDataTool;
import com.data.datafusion.ai.tool.MetricQueryGuideTool;
import com.data.datafusion.ai.tool.MetricRagSearchTool;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指标智能问数的 Harness 工具族。
 *
 * <p>把平台内置智能问数助手使用的 6 个只读 {@link AiTool} 原样暴露到 {@code /mcp/datay}，
 * 由 MCP 客户端的模型完成「指标识别 → 维度拆解 → 业务限定 → 时间范围」的推理，服务端无需 LLM Key。
 *
 * <p>刻意<strong>不重写</strong>这些工具：工具名、说明与入参 schema 全部直接取自 {@link AiTool} 实现，
 * 因此与 {@code /mcp/metric} 端点、内置问数助手共用同一份实现与提示词，不会出现两套口径漂移。
 * 工具名保持 {@code list_metrics} / {@code query_metric_data} 等原样，因为工具说明之间是互相引用的
 * （例如「先调用 …再按顺序调用 retrieve_metric_context、list_metrics、…」），改名会让说明失效。
 *
 * <p>这里按<strong>具体类型</strong>注入指标工具，而不是走 {@code AiToolRegistry} 按名查找：
 * 一是类型安全、编译期即可发现改名或移除；二是避免在 {@link DatayHarnessTool} 的构造路径上
 * 提前触发 {@code AiToolRegistry} 初始化——那会让它拿到一份不完整的 {@code List<AiTool>}，
 * 连带削弱内置问数助手可用的工具集。
 *
 * <p>这些工具都是只读的：{@link AiTool#mutating()} 默认 false，因此即使服务端关闭写操作
 * （{@code datay.mcp.harness.allow-mutating-tools=false}），问数能力依然可用。
 */
@Configuration
public class MetricHarnessTools {

    /**
     * 指标问数工具的推荐调用顺序，仅用于文档与测试断言。
     */
    static final List<String> METRIC_TOOL_NAMES = List.of(
        MetricQueryGuideTool.NAME,
        MetricRagSearchTool.NAME,
        MetricCatalogTool.NAME,
        MetricMetaTool.NAME,
        MetricDimensionValueTool.NAME,
        MetricQueryDataTool.NAME
    );

    /**
     * 指标问数工具在目录中的分组名。
     */
    static final String GROUP = "metric";

    private final MetricQueryGuideTool queryGuideTool;
    private final MetricRagSearchTool ragSearchTool;
    private final MetricCatalogTool catalogTool;
    private final MetricMetaTool metaTool;
    private final MetricDimensionValueTool dimensionValueTool;
    private final MetricQueryDataTool queryDataTool;

    public MetricHarnessTools(
        MetricQueryGuideTool queryGuideTool,
        MetricRagSearchTool ragSearchTool,
        MetricCatalogTool catalogTool,
        MetricMetaTool metaTool,
        MetricDimensionValueTool dimensionValueTool,
        MetricQueryDataTool queryDataTool
    ) {
        this.queryGuideTool = queryGuideTool;
        this.ragSearchTool = ragSearchTool;
        this.catalogTool = catalogTool;
        this.metaTool = metaTool;
        this.dimensionValueTool = dimensionValueTool;
        this.queryDataTool = queryDataTool;
    }

    @Bean
    DatayHarnessTool metricQueryGuideHarnessTool() {
        return metricTool(queryGuideTool);
    }

    @Bean
    DatayHarnessTool metricRagSearchHarnessTool() {
        return metricTool(ragSearchTool);
    }

    @Bean
    DatayHarnessTool metricCatalogHarnessTool() {
        return metricTool(catalogTool);
    }

    @Bean
    DatayHarnessTool metricMetaHarnessTool() {
        return metricTool(metaTool);
    }

    @Bean
    DatayHarnessTool metricDimensionValueHarnessTool() {
        return metricTool(dimensionValueTool);
    }

    @Bean
    DatayHarnessTool metricQueryDataHarnessTool() {
        return metricTool(queryDataTool);
    }

    /**
     * 把一个指标 {@link AiTool} 包装为 Harness 工具。
     *
     * <p>只加一层「指标智能问数」前缀帮助模型把这些工具归为一组，
     * 其余说明、入参与写标记全部沿用原实现。
     */
    private DatayHarnessTool metricTool(AiTool aiTool) {
        return DatayHarnessTool.of(
            aiTool.name(),
            GROUP,
            "【指标智能问数】\n" + aiTool.description(),
            aiTool.parametersSchema(),
            aiTool.mutating(),
            Set.of(),
            (args, context) -> aiTool.execute(args, new AiToolContext(null, null, null, null))
        );
    }
}
