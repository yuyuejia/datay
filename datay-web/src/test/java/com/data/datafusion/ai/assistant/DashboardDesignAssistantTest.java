package com.data.datafusion.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.tool.PrepareMetricDatasetTool;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.service.AnalysisDatasetService;
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
    void shouldExposeDatasetPreparationToolsAndRequireRefIds() {
        assertThat(assistant.toolNames()).contains("list_analysis_datasets", "prepare_metric_dataset");
        String prompt = assistant.buildSystemPrompt(new AiAssistantContext(null, "生成销售看板"));
        assertThat(prompt)
            .contains("list_analysis_datasets")
            .contains("prepare_metric_dataset")
            .contains("禁止内嵌 metricQuery")
            .contains("一个看板默认只使用一个指标数据集")
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
