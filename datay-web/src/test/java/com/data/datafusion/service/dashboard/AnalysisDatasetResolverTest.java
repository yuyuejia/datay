package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AnalysisDatasetResolver} 引用解析单元测试。
 */
class AnalysisDatasetResolverTest {

    private AnalysisDatasetRepository repository;
    private AnalysisDatasetResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(AnalysisDatasetRepository.class);
        resolver = new AnalysisDatasetResolver(repository, new ObjectMapper());
    }

    @Test
    void shouldResolveMetricDatasetReference() {
        AnalysisDataset entity = new AnalysisDataset();
        entity.setId("lib-1");
        entity.setName("销售指标");
        entity.setType(AnalysisDataset.TYPE_METRIC);
        entity.setDefinition("{\"metricQuery\":{\"metricCodes\":[\"sales_amount\"],\"dimensions\":[]}}");
        entity.setFields("{\"columns\":[{\"name\":\"sales_amount\",\"description\":\"销售额\",\"type\":\"number\"}]}");
        when(repository.findById("lib-1")).thenReturn(Optional.of(entity));

        Map<String, Object> resolved = resolver.resolveDataset(
            Map.of("id", "ds1", "refId", "lib-1", "name", "旧名称", "metricQuery", Map.of("metricCodes", List.of("stale")))
        );

        assertThat(resolved).containsEntry("id", "ds1").containsEntry("refId", "lib-1").containsEntry("type", "METRIC");
        assertThat(resolved).containsEntry("name", "旧名称");
        assertThat(String.valueOf(resolved.get("fields"))).contains("销售额");
        @SuppressWarnings("unchecked")
        Map<String, Object> metricQuery = (Map<String, Object>) resolved.get("metricQuery");
        assertThat(metricQuery).containsEntry("metricCodes", List.of("sales_amount"));
    }

    @Test
    void shouldUseLibraryNameWhenReferenceNameMissed() {
        AnalysisDataset entity = new AnalysisDataset();
        entity.setId("lib-2");
        entity.setName("订单 SQL");
        entity.setType(AnalysisDataset.TYPE_SQL);
        entity.setDataSourceId("ds-source");
        entity.setDefinition("{\"sql\":\"SELECT 1\",\"params\":[]}");
        when(repository.findById("lib-2")).thenReturn(Optional.of(entity));

        Map<String, Object> resolved = resolver.resolveDataset(Map.of("id", "ds2", "refId", "lib-2", "sql", "SELECT stale"));

        assertThat(resolved).containsEntry("name", "订单 SQL").containsEntry("sql", "SELECT 1").containsEntry("dataSourceId", "ds-source");
    }

    @Test
    void shouldResolveAllDatasetsInSpec() {
        AnalysisDataset entity = new AnalysisDataset();
        entity.setId("lib-3");
        entity.setName("指标");
        entity.setType(AnalysisDataset.TYPE_METRIC);
        entity.setDefinition("{\"metricCodes\":[\"m1\"]}");
        when(repository.findById("lib-3")).thenReturn(Optional.of(entity));

        Map<String, Object> spec = resolver.resolveSpec(
            Map.of("datasets", List.of(Map.of("id", "ds1", "refId", "lib-3"), Map.of("id", "ds2", "type", "SQL", "sql", "SELECT 2")))
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) spec.get("datasets");
        assertThat(datasets).hasSize(2);
        assertThat(datasets.get(0)).containsEntry("refId", "lib-3");
        assertThat(datasets.get(1)).containsEntry("sql", "SELECT 2");
    }

    @Test
    void shouldFailWhenReferenceMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveDataset(Map.of("id", "ds1", "refId", "missing")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("引用的数据集不存在");
    }

    @Test
    void shouldCollectReferenceIds() {
        Map<String, Object> spec = Map.of(
            "datasets",
            List.of(Map.of("id", "ds1", "refId", "lib-1"), Map.of("id", "ds2"), Map.of("id", "ds3", "refId", "lib-2"))
        );

        assertThat(resolver.collectRefIds(spec)).containsExactly("lib-1", "lib-2");
    }
}
