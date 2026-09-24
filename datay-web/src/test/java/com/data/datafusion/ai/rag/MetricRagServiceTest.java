package com.data.datafusion.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.rag.embedding.LocalHashEmbeddingClient;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetricDTO;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link MetricRagService} 的单元测试：使用临时 DuckDB 文件验证索引与检索。
 */
class MetricRagServiceTest {

    @TempDir
    Path tempDir;

    private MetricRagService service;

    @BeforeEach
    void setUp() throws Exception {
        MetricRagProperties properties = new MetricRagProperties();
        properties.setDbFile(tempDir.resolve("rag.duckdb").toString());
        properties.setProvider("local");
        properties.setLocalDimensions(128);
        properties.setMemberIndexEnabled(true);
        properties.setMemberSampleLimit(50);
        properties.setTopK(5);

        MetricService metricService = mock(MetricService.class);
        DataModelRepository dataModelRepository = mock(DataModelRepository.class);
        ModelFieldRepository modelFieldRepository = mock(ModelFieldRepository.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        DataSourceQueryService dataSourceQueryService = mock(DataSourceQueryService.class);

        when(metricService.findAllSimple()).thenReturn(List.of(metric("销售金额", "sales_amount"), metric("订单数", "order_count")));

        DataModel dimStore = new DataModel();
        dimStore.setId(3002L);
        dimStore.setCode("dim_store");
        dimStore.setName("门店维度");
        dimStore.setModelType("DIMENSION");
        dimStore.setSchemaName("main");
        dimStore.setTableName("dim_store");
        dimStore.setDataSourceId(1600L);

        ModelField city = new ModelField();
        city.setFieldName("city");
        city.setFieldType("VARCHAR");
        city.setDescription("城市");

        when(dataModelRepository.findByModelType("DIMENSION")).thenReturn(List.of(dimStore));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3002L)).thenReturn(List.of(city));

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId(1600L);
        dataSource.setType("DUCKDB");
        dataSource.setUrl("jdbc:duckdb:./data/ecommerce.duckdb");
        when(dataSourceService.findOne(1600L)).thenReturn(Optional.of(dataSource));
        when(dataSourceQueryService.executeQuery(any(DataSourceDTO.class), anyString()))
            .thenReturn(Map.of("columns", List.of("city"), "rows", List.of(Map.of("city", "北京"), Map.of("city", "上海"))));

        service = new MetricRagService(
            properties,
            new LocalHashEmbeddingClient(128),
            metricService,
            dataModelRepository,
            modelFieldRepository,
            dataSourceService,
            dataSourceQueryService
        );
    }

    @Test
    void rebuildShouldIndexMetricsDimensionsAndMembers() {
        Map<String, Object> result = service.rebuild();
        assertThat(result).containsEntry("metricCount", 2).containsEntry("dimensionCount", 1).containsEntry("memberCount", 2);

        Map<String, Object> status = service.status();
        assertThat(status).containsEntry("indexed", true).containsEntry("metricCount", 2).containsEntry("provider", "local");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldMatchMetricByName() {
        service.rebuild();
        Map<String, Object> result = service.search("销售额", 5, null);
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) result.get("metrics");
        assertThat(metrics).isNotEmpty();
        assertThat(metrics.get(0)).containsEntry("code", "sales_amount");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldMatchDimensionMember() {
        service.rebuild();
        Map<String, Object> result = service.search("北京", 5, null);
        List<Map<String, Object>> members = (List<Map<String, Object>>) result.get("members");
        assertThat(members).isNotEmpty();
        assertThat(members.get(0))
            .containsEntry("dimension_code", "dim_store")
            .containsEntry("field_name", "city")
            .containsEntry("member_value", "北京");
    }

    private MetricDTO metric(String name, String code) {
        MetricDTO dto = new MetricDTO();
        dto.setName(name);
        dto.setCode(code);
        dto.setStatus(Metric.STATUS_ENABLED);
        dto.setMetricType(Metric.TYPE_ATOMIC);
        dto.setDescription("测试指标");
        return dto;
    }
}
