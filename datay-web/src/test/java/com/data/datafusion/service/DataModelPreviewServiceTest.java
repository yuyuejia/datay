package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ModelPreviewDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataModelPreviewServiceTest {

    private DataModelRepository dataModelRepository;
    private DataSourceService dataSourceService;
    private DataSourceQueryService dataSourceQueryService;
    private DataModelPreviewService service;

    @BeforeEach
    void setUp() {
        dataModelRepository = mock(DataModelRepository.class);
        dataSourceService = mock(DataSourceService.class);
        dataSourceQueryService = mock(DataSourceQueryService.class);
        service = new DataModelPreviewService(dataModelRepository, dataSourceService, dataSourceQueryService, new ObjectMapper());
    }

    @Test
    void shouldPreviewPhysicalTableWithLimit() throws SQLException {
        DataModel model = model("1", "main", "fact_sales");
        DataSourceDTO dataSource = dataSource("ds1", "MYSQL", "jdbc:mysql://localhost:3306/demo");
        when(dataModelRepository.findById("1")).thenReturn(Optional.of(model));
        when(dataSourceService.findOne("ds1")).thenReturn(Optional.of(dataSource));
        when(dataSourceQueryService.executeQuery(any(), anyString()))
            .thenReturn(Map.of("columns", List.of("id", "amount"), "rows", List.of(Map.of("id", 1, "amount", 2))));

        ModelPreviewDTO result = service.preview("1", 10);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(dataSourceQueryService).executeQuery(any(), sql.capture());
        assertThat(sql.getValue()).isEqualTo("SELECT * FROM `main`.`fact_sales` LIMIT 10");
        assertThat(result.columns()).containsExactly("id", "amount");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.table()).isEqualTo("main.fact_sales");
        assertThat(result.dataSourceName()).isEqualTo("测试数据源");
    }

    @Test
    void shouldRejectModelWithoutPhysicalTable() {
        DataModel model = model("1", null, null);
        when(dataModelRepository.findById("1")).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> service.preview("1", 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("尚未绑定物理表");
    }

    private DataModel model(String id, String schema, String table) {
        DataModel model = new DataModel();
        model.setId(id);
        model.setCode("fact_sales");
        model.setSchemaName(schema);
        model.setTableName(table);
        model.setDataSourceId("ds1");
        return model;
    }

    private DataSourceDTO dataSource(String id, String type, String url) {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setId(id);
        dto.setName("测试数据源");
        dto.setType(type);
        dto.setUrl(url);
        return dto;
    }
}
