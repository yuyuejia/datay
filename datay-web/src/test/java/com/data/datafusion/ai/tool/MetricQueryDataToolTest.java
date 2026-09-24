package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.AiResult;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link MetricQueryDataTool} 的单元测试，重点校验入参组装与产物回传。
 */
class MetricQueryDataToolTest {

    private MetricQueryService metricQueryService;
    private MetricQueryDataTool tool;

    @BeforeEach
    void setUp() {
        metricQueryService = mock(MetricQueryService.class);
        tool = new MetricQueryDataTool(metricQueryService, new ObjectMapper());
    }

    @Test
    void shouldAssembleQueryDtoAndRecordArtifact() throws Exception {
        Map<String, Object> queryResult = new LinkedHashMap<>();
        queryResult.put("columns", List.of("city", "sales_amount"));
        queryResult.put("rows", List.of(Map.of("city", "北京", "sales_amount", 1000)));
        queryResult.put("affectedRows", 1);
        queryResult.put("sql", "SELECT ...");
        when(metricQueryService.query(any(MetricQueryDTO.class))).thenReturn(queryResult);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("metricCodes", List.of("sales_amount"));
        arguments.put(
            "dimensions",
            List.of(Map.of("dimensionModelCode", "dim_store", "dimensionFieldNames", List.of("city")))
        );
        arguments.put(
            "conditions",
            List.of(
                Map.of(
                    "dimensionModelCode",
                    "dim_store",
                    "dimensionFieldName",
                    "city",
                    "operator",
                    "EQ",
                    "value",
                    "北京"
                )
            )
        );
        arguments.put("timeRange", Map.of("start", "2024-01-01", "end", "2024-01-07"));

        AiToolContext context = new AiToolContext(null, null, "最近一周北京的销售额", null);
        Object result = tool.execute(arguments, context);

        ArgumentCaptor<MetricQueryDTO> captor = ArgumentCaptor.forClass(MetricQueryDTO.class);
        org.mockito.Mockito.verify(metricQueryService).query(captor.capture());
        MetricQueryDTO dto = captor.getValue();
        assertThat(dto.getMetricCodes()).containsExactly("sales_amount");
        assertThat(dto.getDimensions()).hasSize(1);
        assertThat(dto.getDimensions().get(0).getDimensionModelCode()).isEqualTo("dim_store");
        assertThat(dto.getDimensions().get(0).getDimensionFieldNames()).containsExactly("city");
        assertThat(dto.getFilterConfig()).contains("\"type\":\"DIMENSION\"").contains("\"value\":\"北京\"");
        assertThat(dto.getTimeRange().getStart()).isEqualTo("2024-01-01");
        assertThat(dto.getTimeRange().getEnd()).isEqualTo("2024-01-07");

        assertThat(context.getArtifact(AiResult.ARTIFACT_METRIC_QUERY)).isEqualTo(queryResult);

        @SuppressWarnings("unchecked")
        Map<String, Object> modelView = (Map<String, Object>) result;
        assertThat(modelView).containsEntry("totalRows", 1);
        assertThat(modelView).containsEntry("sql", "SELECT ...");
    }

    @Test
    void shouldReturnErrorWhenMetricCodesMissing() throws Exception {
        AiToolContext context = new AiToolContext(null, null, "查询", null);
        Object result = tool.execute(Map.of(), context);
        assertThat(((Map<?, ?>) result).get("error")).isNotNull();
    }
}
