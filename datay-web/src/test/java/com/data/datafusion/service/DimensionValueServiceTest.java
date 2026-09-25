package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DimensionValueService} 维度取值分页查询的单元测试。
 */
class DimensionValueServiceTest {

    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private DataSourceService dataSourceService;
    private DataSourceQueryService dataSourceQueryService;
    private DimensionValueService dimensionValueService;

    @BeforeEach
    void setUp() {
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        dataSourceService = mock(DataSourceService.class);
        dataSourceQueryService = mock(DataSourceQueryService.class);
        dimensionValueService = new DimensionValueService(dataModelRepository, modelFieldRepository, dataSourceService, dataSourceQueryService);
    }

    @Test
    void shouldPageDimensionValues() throws SQLException {
        DataModel dimStore = new DataModel();
        dimStore.setId(3002L);
        dimStore.setCode("dim_store");
        dimStore.setModelType("DIMENSION");
        dimStore.setSchemaName("main");
        dimStore.setTableName("dim_store");
        dimStore.setDataSourceId(1600L);
        when(dataModelRepository.findById(3002L)).thenReturn(Optional.of(dimStore));
        ModelField city = new ModelField();
        city.setFieldName("city");
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3002L)).thenReturn(List.of(city));
        when(dataSourceService.findOne(1600L)).thenReturn(Optional.of(new DataSourceDTO()));
        when(dataSourceQueryService.executeQuery(any(), anyString()))
            .thenReturn(Map.of("rows", List.of(Map.of("cnt", 2L))))
            .thenReturn(Map.of("rows", List.of(Map.of("city", "北京"), Map.of("city", "上海"))));

        Map<String, Object> result = dimensionValueService.pageValues(3002L, "city", "北", 1, 20);

        assertThat(result).containsEntry("total", 2L).containsEntry("page", 1).containsEntry("size", 20);
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) result.get("values");
        assertThat(values).containsExactly("北京", "上海");
    }

    @Test
    void shouldRejectNonDimensionModel() {
        DataModel fact = new DataModel();
        fact.setId(3101L);
        fact.setCode("fact_sales_order_item");
        fact.setModelType("DWD");
        when(dataModelRepository.findById(3101L)).thenReturn(Optional.of(fact));

        assertThatThrownBy(() -> dimensionValueService.pageValues(3101L, "amount", null, 1, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("只能查询维度模型");
    }
}
