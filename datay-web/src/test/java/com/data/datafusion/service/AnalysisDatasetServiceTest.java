package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.service.dashboard.AnalysisDatasetResolver;
import com.data.datafusion.service.dashboard.DatasetQueryService;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.data.datafusion.service.mapper.AnalysisDatasetMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AnalysisDatasetService} 数据集校验与删除保护的单元测试。
 */
class AnalysisDatasetServiceTest {

    private AnalysisDatasetRepository repository;
    private AnalysisDashboardRepository dashboardRepository;
    private AnalysisDatasetResolver resolver;
    private DatasetQueryService queryService;
    private AnalysisDatasetService service;

    @BeforeEach
    void setUp() {
        repository = mock(AnalysisDatasetRepository.class);
        dashboardRepository = mock(AnalysisDashboardRepository.class);
        resolver = mock(AnalysisDatasetResolver.class);
        queryService = mock(DatasetQueryService.class);
        service = new AnalysisDatasetService(
            repository,
            dashboardRepository,
            mock(AnalysisDatasetMapper.class),
            resolver,
            queryService,
            new ObjectMapper()
        );
    }

    @Test
    void shouldRejectDeleteWhenDatasetReferenced() {
        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setSpec("{\"datasets\":[{\"id\":\"ds1\",\"refId\":\"lib-1\"}]}");
        when(dashboardRepository.findAll()).thenReturn(List.of(dashboard));
        when(resolver.collectRefIds(any())).thenReturn(Set.of("lib-1"));

        assertThatThrownBy(() -> service.delete("lib-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1 个看板引用");
        verify(repository, never()).deleteById(anyString());
    }

    @Test
    void shouldDeleteUnreferencedDataset() {
        when(dashboardRepository.findAll()).thenReturn(List.of());
        when(resolver.collectRefIds(any())).thenReturn(Set.of());

        service.delete("lib-1");

        verify(repository).deleteById("lib-1");
    }

    @Test
    void shouldNormalizeMetricDefinitionWithFiltersAndTimeRange() {
        AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
        dto.setName("销售");
        dto.setType("metric");
        dto.setDefinition(
            "{\"metricQuery\":{\"metricCodes\":[\"sales_amount\"]," +
            "\"filterConfig\":{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionModelCode\":\"dim_store\"," +
            "\"dimensionFieldName\":\"region\",\"operator\":\"EQ\",\"value\":\"华东\"}]}," +
            "\"timeRange\":{\"start\":\"2026-01-01\",\"end\":\"2026-12-31\"}}}"
        );

        AnalysisDatasetDTO result = service.validateDefinition(dto);

        assertThat(result.getType()).isEqualTo(AnalysisDataset.TYPE_METRIC);
        assertThat(result.getDefinition())
            .contains("\"metricQuery\"")
            .contains("sales_amount")
            .contains("\"filterConfig\"")
            .contains("dim_store")
            .contains("\"timeRange\"")
            .contains("2026-12-31");
    }

    @Test
    void shouldRejectNonReadOnlySql() {
        AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
        dto.setName("删除数据");
        dto.setType("SQL");
        dto.setDataSourceId("source-1");
        dto.setDefinition("{\"sql\":\"DELETE FROM orders\"}");

        assertThatThrownBy(() -> service.validateDefinition(dto)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCacheInferredColumnTypesOnPreview() throws Exception {
        AnalysisDataset entity = new AnalysisDataset();
        entity.setId("ds-1");
        entity.setName("订单明细");
        entity.setType(AnalysisDataset.TYPE_SQL);
        entity.setDataSourceId("source-1");
        entity.setDefinition("{\"sql\":\"SELECT region, amount FROM orders\"}");
        entity.setFields("{\"columns\":[{\"name\":\"amount\",\"description\":\"成交金额\",\"type\":\"number\",\"primaryKey\":true}]}");
        when(repository.findById("ds-1")).thenReturn(Optional.of(entity));
        when(resolver.parseDefinition(anyString())).thenReturn(Map.of("sql", "SELECT region, amount FROM orders"));
        when(queryService.query(any(), any(), any(), any())).thenReturn(
            Map.of(
                "columns",
                List.of("region", "amount", "quantity"),
                "rows",
                List.of(Map.of("region", "华东", "amount", 12.5, "quantity", 2)),
                "columnLabels",
                Map.of("region", "区域")
            )
        );

        service.preview("ds-1", Map.of());

        JsonNode fields = new ObjectMapper().readTree(entity.getFields());
        assertThat(fields.get("columns").get(0).get("name").asText()).isEqualTo("region");
        assertThat(fields.get("columns").get(0).get("type").asText()).isEqualTo("string");
        assertThat(fields.get("columns").get(1).get("name").asText()).isEqualTo("amount");
        assertThat(fields.get("columns").get(1).get("type").asText()).isEqualTo("number");
        assertThat(fields.get("columns").get(1).get("description").asText()).isEqualTo("成交金额");
        assertThat(fields.get("columns").get(1).get("primaryKey").asBoolean()).isTrue();
        assertThat(fields.get("columns").get(2).get("name").asText()).isEqualTo("quantity");
        assertThat(fields.get("columns").get(2).get("description").asText()).isEqualTo("quantity");
        assertThat(fields.get("columnLabels").get("region").asText()).isEqualTo("区域");
        assertThat(fields.get("columnLabels").get("amount").asText()).isEqualTo("成交金额");
        assertThat(fields.get("columnLabels").get("quantity").asText()).isEqualTo("quantity");
        verify(repository).save(entity);
    }
}
