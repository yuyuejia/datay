package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PrepareMetricDatasetTool} 指标数据集准备测试。
 */
class PrepareMetricDatasetToolTest {

    @Test
    void shouldReuseExistingMetricDataset() {
        AnalysisDatasetService service = mock(AnalysisDatasetService.class);
        when(service.validateDefinition(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisDatasetDTO existing = new AnalysisDatasetDTO();
        existing.setId("ds-existing");
        existing.setName("销售指标");
        existing.setCode("sales_metric");
        existing.setType(AnalysisDataset.TYPE_METRIC);
        existing.setDefinition("{\"metricQuery\":{\"metricCodes\":[\"sales_amount\"]}}");
        when(service.findAllSimple()).thenReturn(List.of(existing));
        PrepareMetricDatasetTool tool = new PrepareMetricDatasetTool(service, new ObjectMapper());
        AiToolContext context = new AiToolContext(null, null, "", null);

        Object result = tool.execute(
            Map.of("name", "销售指标", "metricQuery", Map.of("metricCodes", List.of("sales_amount"))),
            context
        );

        assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("refId", "ds-existing")
            .containsEntry("draft", false);
        assertThat(context.getArtifacts()).doesNotContainKey(PrepareMetricDatasetTool.ARTIFACT_DRAFTS);
    }

    @Test
    void shouldReuseDatasetThatCoversRequestedMetricsAndDimensions() {
        AnalysisDatasetService service = mock(AnalysisDatasetService.class);
        when(service.validateDefinition(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisDatasetDTO existing = new AnalysisDatasetDTO();
        existing.setId("ds-wide");
        existing.setName("销售宽数据集");
        existing.setType(AnalysisDataset.TYPE_METRIC);
        existing.setDefinition(
            "{\"metricQuery\":{\"metricCodes\":[\"sales_amount\",\"gross_profit\"]," +
            "\"dimensions\":[{\"dimensionModelCode\":\"dim_store\",\"dimensionFieldNames\":[\"store_name\",\"region\"]}]}}"
        );
        when(service.findAllSimple()).thenReturn(List.of(existing));
        PrepareMetricDatasetTool tool = new PrepareMetricDatasetTool(service, new ObjectMapper());

        Object result = tool.execute(
            Map.of(
                "name",
                "销售子集",
                "metricQuery",
                Map.of(
                    "metricCodes",
                    List.of("sales_amount"),
                    "dimensions",
                    List.of(Map.of("dimensionModelCode", "dim_store", "dimensionFieldNames", List.of("store_name")))
                )
            ),
            new AiToolContext(null, null, "", null)
        );

        assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("refId", "ds-wide")
            .containsEntry("draft", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateDraftWhenMetricIsNotCovered() {
        AnalysisDatasetService service = mock(AnalysisDatasetService.class);
        when(service.validateDefinition(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisDatasetDTO existing = new AnalysisDatasetDTO();
        existing.setId("ds-sales");
        existing.setType(AnalysisDataset.TYPE_METRIC);
        existing.setDefinition("{\"metricQuery\":{\"metricCodes\":[\"sales_amount\"]}}");
        when(service.findAllSimple()).thenReturn(List.of(existing));
        PrepareMetricDatasetTool tool = new PrepareMetricDatasetTool(service, new ObjectMapper());
        AiToolContext context = new AiToolContext(null, null, "", null);

        Object result = tool.execute(
            Map.of("name", "毛利数据集", "metricQuery", Map.of("metricCodes", List.of("gross_profit"))),
            context
        );

        assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("draft", true);
        assertThat(context.getArtifacts()).containsKey(PrepareMetricDatasetTool.ARTIFACT_DRAFTS);
    }

    @Test
    void shouldReuseDatasetWhenTimeRangeCoversRequestedRange() {
        AnalysisDatasetService service = mock(AnalysisDatasetService.class);
        when(service.validateDefinition(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisDatasetDTO existing = new AnalysisDatasetDTO();
        existing.setId("ds-year");
        existing.setType(AnalysisDataset.TYPE_METRIC);
        existing.setDefinition(
            "{\"metricQuery\":{\"metricCodes\":[\"sales_amount\"],\"timeRange\":{\"start\":\"2026-01-01\",\"end\":\"2026-12-31\"}}}"
        );
        when(service.findAllSimple()).thenReturn(List.of(existing));
        PrepareMetricDatasetTool tool = new PrepareMetricDatasetTool(service, new ObjectMapper());

        Object result = tool.execute(
            Map.of(
                "name",
                "六月销售",
                "metricQuery",
                Map.of(
                    "metricCodes",
                    List.of("sales_amount"),
                    "timeRange",
                    Map.of("start", "2026-06-01", "end", "2026-06-30")
                )
            ),
            new AiToolContext(null, null, "", null)
        );

        assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("refId", "ds-year");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateDraftWhenNoEquivalentDatasetExists() {
        AnalysisDatasetService service = mock(AnalysisDatasetService.class);
        when(service.validateDefinition(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(service.findAllSimple()).thenReturn(List.of());
        PrepareMetricDatasetTool tool = new PrepareMetricDatasetTool(service, new ObjectMapper());
        AiToolContext context = new AiToolContext(null, null, "", null);

        Map<String, Object> result = (Map<String, Object>) tool.execute(
            Map.of("name", "销售指标", "metricQuery", Map.of("metricCodes", List.of("sales_amount"))),
            context
        );

        assertThat(result).containsEntry("name", "销售指标").containsEntry("draft", true);
        assertThat(String.valueOf(result.get("refId"))).startsWith(PrepareMetricDatasetTool.DRAFT_PREFIX);
        Map<String, Object> drafts = (Map<String, Object>) context.getArtifact(PrepareMetricDatasetTool.ARTIFACT_DRAFTS);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.values().iterator().next()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("name", "销售指标")
            .containsKey("definition");
    }

    @Test
    void shouldRejectMissingMetricCodes() {
        PrepareMetricDatasetTool tool = new PrepareMetricDatasetTool(mock(AnalysisDatasetService.class), new ObjectMapper());

        Object result = tool.execute(
            Map.of("name", "空指标", "metricQuery", Map.of("dimensions", List.of())),
            new AiToolContext(null, null, "", null)
        );

        assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsKey("error");
    }
}
