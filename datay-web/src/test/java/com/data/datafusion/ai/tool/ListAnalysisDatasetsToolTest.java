package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
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
 * {@link ListAnalysisDatasetsTool} 数据集库查询测试。
 */
class ListAnalysisDatasetsToolTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldListMetricDatasetsAndParseDefinitionAndFields() {
        AnalysisDatasetService service = mock(AnalysisDatasetService.class);
        AnalysisDatasetDTO metric = dataset(
            "ds-1",
            "门店销售",
            AnalysisDataset.TYPE_METRIC,
            "{\"metricQuery\":{\"metricCodes\":[\"sales_amount\"]}}"
        );
        metric.setFields("{\"columns\":[{\"name\":\"store_name\",\"type\":\"string\"}]}");
        AnalysisDatasetDTO sql = dataset("ds-2", "门店明细", AnalysisDataset.TYPE_SQL, "{\"sql\":\"SELECT 1\"}");
        when(service.findAllSimple()).thenReturn(List.of(metric, sql));

        ListAnalysisDatasetsTool tool = new ListAnalysisDatasetsTool(service, new ObjectMapper());
        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("keyword", "销售"), new AiToolContext(null, null, "", null));

        assertThat(result).containsEntry("type", AnalysisDataset.TYPE_METRIC).containsEntry("total", 1);
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) result.get("datasets");
        assertThat(datasets).hasSize(1);
        assertThat(datasets.get(0)).containsEntry("id", "ds-1").containsEntry("type", AnalysisDataset.TYPE_METRIC);
        assertThat(datasets.get(0)).containsKeys("definition", "fields");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFilterByTypeAndKeyword() {
        AnalysisDatasetService service = mock(AnalysisDatasetService.class);
        when(service.findAllSimple()).thenReturn(
            List.of(
                dataset("ds-1", "销售指标", AnalysisDataset.TYPE_METRIC, "{\"metricQuery\":{\"metricCodes\":[\"m1\"]}}"),
                dataset("ds-2", "订单明细", AnalysisDataset.TYPE_SQL, "{\"sql\":\"SELECT 1\"}")
            )
        );

        ListAnalysisDatasetsTool tool = new ListAnalysisDatasetsTool(service, new ObjectMapper());
        Map<String, Object> result = (Map<String, Object>) tool.execute(
            Map.of("type", "SQL", "keyword", "订单"),
            new AiToolContext(null, null, "", null)
        );

        assertThat(result).containsEntry("total", 1);
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) result.get("datasets");
        assertThat(datasets.get(0)).containsEntry("id", "ds-2");
        assertThat(tool.mutating()).isFalse();
    }

    private AnalysisDatasetDTO dataset(String id, String name, String type, String definition) {
        AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setCode(id);
        dto.setType(type);
        dto.setDefinition(definition);
        return dto;
    }
}
