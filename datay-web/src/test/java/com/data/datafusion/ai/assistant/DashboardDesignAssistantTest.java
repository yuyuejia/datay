package com.data.datafusion.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.tool.PrepareMetricDatasetTool;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.dashboard.DashboardSpecValidator;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link DashboardDesignAssistant} 数据集引用后处理测试。
 */
class DashboardDesignAssistantTest {

    private final AnalysisDatasetService datasetService = mock(AnalysisDatasetService.class);
    private final DashboardDesignAssistant assistant = new DashboardDesignAssistant(new ObjectMapper(), datasetService);

    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistPreparedDatasetDraftAndReplaceRefId() {
        AnalysisDatasetDTO saved = dataset("saved-id", "销售指标");
        when(datasetService.findAllSimple()).thenReturn(List.of());
        when(datasetService.save(any())).thenReturn(saved);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("name", "销售指标");
        draft.put("definition", "{\"metricQuery\":{\"metricCodes\":[\"sales_amount\"]}}");
        Map<String, Object> artifacts = Map.of(
            PrepareMetricDatasetTool.ARTIFACT_DRAFTS,
            Map.of("draft-key", draft)
        );
        Map<String, Object> spec = specWithDataset(
            Map.of("id", "ds1", "refId", PrepareMetricDatasetTool.DRAFT_PREFIX + "draft-key", "name", "临时数据集", "type", "METRIC")
        );

        Map<String, Object> processed = assistant.postProcessSpec(spec, artifacts);

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) processed.get("datasets");
        assertThat(datasets).hasSize(1);
        assertThat(datasets.get(0)).containsEntry("refId", "saved-id").containsEntry("name", "销售指标");
        assertThat(datasets.get(0)).doesNotContainKeys("metricQuery", "sql", "params", "dataSourceId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackToSinglePreparedDraftWhenRefIdInvalid() {
        AnalysisDatasetDTO saved = dataset("saved-profit-id", "销售毛利指标");
        when(datasetService.findAllSimple()).thenReturn(List.of());
        when(datasetService.findOne("ds_sales_profit_region_category")).thenReturn(Optional.empty());
        when(datasetService.save(any())).thenReturn(saved);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("name", "销售毛利指标");
        draft.put("definition", "{\"metricQuery\":{\"metricCodes\":[\"sales_amount\",\"gross_profit\"]}}");
        Map<String, Object> artifacts = Map.of(
            PrepareMetricDatasetTool.ARTIFACT_DRAFTS,
            Map.of("draft-key", draft)
        );
        Map<String, Object> spec = specWithDataset(
            Map.of(
                "id",
                "ds_sales_profit_region_category",
                "refId",
                "ds_sales_profit_region_category",
                "name",
                "销售毛利区域品类数据集",
                "type",
                "METRIC"
            )
        );

        Map<String, Object> processed = assistant.postProcessSpec(spec, artifacts);

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) processed.get("datasets");
        assertThat(datasets.get(0))
            .containsEntry("refId", "saved-profit-id")
            .containsEntry("name", "销售毛利指标")
            .doesNotContainKey("metricQuery");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackToExistingDatasetByNameWhenRefIdInvalid() {
        AnalysisDatasetDTO existing = dataset("existing-profit-id", "销售毛利区域品类数据集");
        existing.setCode("ds_sales_profit_region_category");
        when(datasetService.findOne("ds_sales_profit_region_category")).thenReturn(Optional.empty());
        when(datasetService.findAllSimple()).thenReturn(List.of(existing));
        Map<String, Object> spec = specWithDataset(
            Map.of(
                "id",
                "ds_sales_profit_region_category",
                "refId",
                "ds_sales_profit_region_category",
                "name",
                "销售毛利区域品类数据集",
                "type",
                "METRIC"
            )
        );

        Map<String, Object> processed = assistant.postProcessSpec(spec, Map.of());

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) processed.get("datasets");
        assertThat(datasets.get(0)).containsEntry("refId", "existing-profit-id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackToEmbeddedMetricQueryWhenRefIdInvalid() {
        AnalysisDatasetDTO saved = dataset("saved-embedded-id", "内嵌销售毛利");
        when(datasetService.findAllSimple()).thenReturn(List.of());
        when(datasetService.findOne("ds_sales_profit_region_category")).thenReturn(Optional.empty());
        when(datasetService.save(any())).thenReturn(saved);
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "ds_sales_profit_region_category");
        dataset.put("refId", "ds_sales_profit_region_category");
        dataset.put("name", "内嵌销售毛利");
        dataset.put("type", "METRIC");
        dataset.put("metricQuery", Map.of("metricCodes", List.of("sales_amount", "gross_profit")));

        Map<String, Object> processed = assistant.postProcessSpec(specWithDataset(dataset), Map.of());

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) processed.get("datasets");
        assertThat(datasets.get(0)).containsEntry("refId", "saved-embedded-id").doesNotContainKey("metricQuery");
    }

    @Test
    void shouldRejectInvalidRefIdWithoutRecoverableDraft() {
        DashboardDesignAssistant strictAssistant = new DashboardDesignAssistant(
            new ObjectMapper(),
            datasetService,
            mock(DashboardSpecValidator.class)
        );
        when(datasetService.findOne("ds_sales_profit_region_category")).thenReturn(Optional.empty());
        Map<String, Object> spec = specWithDataset(
            Map.of(
                "id",
                "ds_sales_profit_region_category",
                "refId",
                "ds_sales_profit_region_category",
                "name",
                "销售毛利区域品类数据集",
                "type",
                "METRIC"
            )
        );

        assertThatThrownBy(() -> strictAssistant.postProcessSpec(spec, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据集引用无效")
            .hasMessageContaining("refId 必须原样使用");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldConvertEmbeddedMetricQueryToDatasetReference() {
        AnalysisDatasetDTO saved = dataset("saved-id", "内嵌指标数据集");
        when(datasetService.findAllSimple()).thenReturn(List.of());
        when(datasetService.save(any())).thenReturn(saved);
        Map<String, Object> spec = specWithDataset(
            new LinkedHashMap<>(
                Map.of("id", "ds1", "name", "内嵌指标数据集", "type", "METRIC", "metricQuery", Map.of("metricCodes", List.of("m1")))
            )
        );

        Map<String, Object> processed = assistant.postProcessSpec(spec, Map.of());

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) processed.get("datasets");
        assertThat(datasets.get(0)).containsEntry("refId", "saved-id");
        assertThat(datasets.get(0)).doesNotContainKey("metricQuery");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveExistingDatasetReference() {
        AnalysisDatasetDTO existing = dataset("existing-id", "已有指标数据集");
        existing.setType(AnalysisDataset.TYPE_METRIC);
        when(datasetService.findOne("existing-id")).thenReturn(Optional.of(existing));
        Map<String, Object> spec = specWithDataset(Map.of("id", "ds1", "refId", "existing-id"));

        Map<String, Object> processed = assistant.postProcessSpec(spec, Map.of());

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) processed.get("datasets");
        assertThat(datasets.get(0)).containsEntry("refId", "existing-id").containsEntry("name", "已有指标数据集");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRepairCommonAiWidgetMistakes() {
        Map<String, Object> legacyEncode = new LinkedHashMap<>();
        legacyEncode.put("categoryField", "region");
        legacyEncode.put("values", List.of(Map.of("field", "amount", "agg", "sum")));
        Map<String, Object> chartWidget = new LinkedHashMap<>();
        chartWidget.put("id", "w1");
        chartWidget.put("type", "echarts");
        chartWidget.put("encode", legacyEncode);
        chartWidget.put("echartsOption", Map.of("series", List.of(Map.of("type", "line", "areaStyle", Map.of()))));

        Map<String, Object> pivotWidget = new LinkedHashMap<>();
        pivotWidget.put("id", "p1");
        pivotWidget.put("type", "pivot");
        pivotWidget.put(
            "encode",
            Map.of(
                "dimensions",
                List.of(Map.of("field", "region", "role", "x"), Map.of("field", "channel", "role", "series")),
                "measures",
                List.of(Map.of("field", "amount", "agg", "sum", "role", "value"))
            )
        );
        Map<String, Object> spec = specWithDataset(Map.of("id", "ds1", "refId", "existing-id"));
        spec.put("widgets", List.of(chartWidget, pivotWidget));

        Map<String, Object> processed = assistant.postProcessSpec(spec, Map.of());
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) processed.get("widgets");

        Map<String, Object> repairedChart = widgets.get(0);
        assertThat((Map<String, Object>) repairedChart.get("chart")).containsEntry("type", "area");
        Map<String, Object> encode = (Map<String, Object>) repairedChart.get("encode");
        assertThat(encode).doesNotContainKeys("categoryField", "values");
        assertThat(encode.get("dimensions")).asList().containsExactly(Map.of("field", "region", "role", "x"));
        assertThat(encode.get("measures")).asList().containsExactly(Map.of("field", "amount", "agg", "sum", "role", "y"));

        Map<String, Object> repairedPivot = widgets.get(1);
        Map<String, Object> pivotEncode = (Map<String, Object>) repairedPivot.get("encode");
        assertThat(pivotEncode.get("dimensions"))
            .asList()
            .containsExactly(Map.of("field", "region", "role", "row"), Map.of("field", "channel", "role", "column"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDedupeDuplicateAiEncodeFields() {
        Map<String, Object> pivotWidget = new LinkedHashMap<>();
        pivotWidget.put("id", "w_pivot_region_category");
        pivotWidget.put("type", "pivot");
        pivotWidget.put(
            "encode",
            Map.of(
                "dimensions",
                List.of(
                    Map.of("field", "level1_name", "role", "row"),
                    Map.of("field", "level1_name", "role", "column"),
                    Map.of("field", "channel", "role", "column")
                ),
                "measures",
                List.of(
                    Map.of("field", "amount", "agg", "sum", "role", "value"),
                    Map.of("field", "amount", "agg", "avg", "role", "value")
                )
            )
        );
        Map<String, Object> spec = specWithDataset(Map.of("id", "ds1", "refId", "existing-id"));
        spec.put("widgets", List.of(pivotWidget));

        Map<String, Object> processed = assistant.postProcessSpec(spec, Map.of());
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) processed.get("widgets");
        Map<String, Object> encode = (Map<String, Object>) widgets.get(0).get("encode");

        assertThat(encode.get("dimensions"))
            .asList()
            .containsExactly(
                Map.of("field", "level1_name", "role", "row"),
                Map.of("field", "channel", "role", "column")
            );
        assertThat(encode.get("measures")).asList().hasSize(1);
        assertThat((Map<String, Object>) ((List<?>) encode.get("measures")).get(0)).containsEntry("agg", "sum");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackPivotDimensionToRowWhenOnlyColumnExists() {
        Map<String, Object> pivotWidget = new LinkedHashMap<>();
        pivotWidget.put("id", "p1");
        pivotWidget.put("type", "pivot");
        pivotWidget.put(
            "encode",
            Map.of(
                "dimensions",
                List.of(Map.of("field", "level1_name", "role", "column")),
                "measures",
                List.of(Map.of("field", "amount", "agg", "sum", "role", "value"))
            )
        );
        Map<String, Object> spec = specWithDataset(Map.of("id", "ds1", "refId", "existing-id"));
        spec.put("widgets", List.of(pivotWidget));

        Map<String, Object> processed = assistant.postProcessSpec(spec, Map.of());
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) processed.get("widgets");
        Map<String, Object> encode = (Map<String, Object>) widgets.get(0).get("encode");

        assertThat(encode.get("dimensions")).asList().containsExactly(Map.of("field", "level1_name", "role", "row"));
    }

    @Test
    void shouldExposeDatasetPreparationToolsAndRequireRefIds() {
        assertThat(assistant.toolNames()).contains("list_analysis_datasets", "prepare_metric_dataset");
        String prompt = assistant.buildSystemPrompt(new AiAssistantContext(null, "生成销售看板"));
        assertThat(prompt)
            .contains("list_analysis_datasets")
            .contains("prepare_metric_dataset")
            .contains("禁止内嵌 metricQuery")
            .contains("一个看板默认只使用一个指标数据集")
            .contains("生成后请自检")
            .contains("透视表必须有 role=row")
            .contains("refId");
    }

    @Test
    void shouldRejectMissingDraftArtifact() {
        Map<String, Object> spec = specWithDataset(
            Map.of("id", "ds1", "refId", PrepareMetricDatasetTool.DRAFT_PREFIX + "missing", "name", "失效草稿")
        );

        assertThatThrownBy(() -> assistant.postProcessSpec(spec, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("草稿已失效");
    }

    private Map<String, Object> specWithDataset(Map<String, Object> dataset) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("title", "测试看板");
        spec.put("datasets", List.of(dataset));
        spec.put("widgets", List.of());
        return spec;
    }

    private AnalysisDatasetDTO dataset(String id, String name) {
        AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setCode(id);
        dto.setType(AnalysisDataset.TYPE_METRIC);
        return dto;
    }
}
