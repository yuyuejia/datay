package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.service.DataModelLineageService;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceReferenceService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DefaultWarehouseConfigService;
import com.data.datafusion.service.MetadataCollectTaskService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.DataSourceReferenceDTO;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * {@link DataSourceResource} 查询入口的行数参数解析验证。
 */
class DataSourceResourceQueryLimitTest {

    private DataSourceService dataSourceService;
    private DataSourceQueryService dataSourceQueryService;
    private DataSourceReferenceService dataSourceReferenceService;
    private DataSourceResource resource;
    private DataSourceDTO dataSource;

    @BeforeEach
    void setUp() {
        dataSourceService = mock(DataSourceService.class);
        dataSourceQueryService = mock(DataSourceQueryService.class);
        dataSourceReferenceService = mock(DataSourceReferenceService.class);
        resource = new DataSourceResource(
            dataSourceService,
            mock(DataSourceRepository.class),
            dataSourceQueryService,
            mock(DefaultWarehouseConfigService.class),
            mock(DataModelLineageService.class),
            mock(MetadataCollectTaskService.class),
            dataSourceReferenceService
        );
        dataSource = new DataSourceDTO();
        dataSource.setId("ds1");
        when(dataSourceService.findOne("ds1")).thenReturn(Optional.of(dataSource));
    }

    @Test
    void shouldPassNumericLimitToService() throws SQLException {
        Map<String, Object> response = new HashMap<>();
        response.put("rows", List.of());
        when(dataSourceQueryService.executeQuery(any(), anyString(), eq(100))).thenReturn(response);

        ResponseEntity<Map<String, Object>> result = resource.executeQuery("ds1", request("SELECT 1", 100));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(dataSourceQueryService).executeQuery(dataSource, "SELECT 1", 100);
    }

    @Test
    void shouldParseNumericStringLimit() throws SQLException {
        when(dataSourceQueryService.executeQuery(any(), anyString(), eq(500))).thenReturn(Map.of());

        ResponseEntity<Map<String, Object>> result = resource.executeQuery("ds1", request("SELECT 1", "500"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(dataSourceQueryService).executeQuery(dataSource, "SELECT 1", 500);
    }

    @Test
    void shouldTreatInvalidLimitAsUnspecified() throws SQLException {
        when(dataSourceQueryService.executeQuery(any(), anyString(), isNull())).thenReturn(Map.of());

        ResponseEntity<Map<String, Object>> result = resource.executeQuery("ds1", request("SELECT 1", "abc"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(dataSourceQueryService).executeQuery(dataSource, "SELECT 1", null);
    }

    @Test
    void shouldPassNullWhenLimitMissing() throws SQLException {
        when(dataSourceQueryService.executeQuery(any(), anyString(), isNull())).thenReturn(Map.of());

        Map<String, Object> body = new HashMap<>();
        body.put("sql", "SELECT 1");
        ResponseEntity<Map<String, Object>> result = resource.executeQuery("ds1", body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(dataSourceQueryService).executeQuery(dataSource, "SELECT 1", null);
    }

    @Test
    void shouldRejectBlankSql() throws SQLException {
        ResponseEntity<Map<String, Object>> result = resource.executeQuery("ds1", request("   ", 100));

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        verify(dataSourceQueryService, never()).executeQuery(any(), anyString(), any());
    }

    @Test
    void shouldReturnNotFoundForMissingDataSource() {
        when(dataSourceService.findOne("missing")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> result = resource.executeQuery("missing", request("SELECT 1", 100));

        assertThat(result.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void shouldRejectDeleteWhenDataSourceIsReferenced() {
        when(dataSourceReferenceService.findReferences("ds1")).thenReturn(
            List.of(new DataSourceReferenceDTO("JOB", "SQL 任务", "job-1", "订单 SQL"))
        );

        ResponseEntity<?> result = resource.deleteDataSource("ds1", false);

        assertThat(result.getStatusCode().value()).isEqualTo(409);
        Map<?, ?> body = (Map<?, ?>) result.getBody();
        assertThat(body.get("code")).isEqualTo("DATA_SOURCE_IN_USE");
        assertThat((List<?>) body.get("references")).hasSize(1);
        verify(dataSourceService, never()).delete(anyString());
    }

    @Test
    void shouldForceDeleteReferencedDataSource() {
        when(dataSourceReferenceService.findReferences("ds1")).thenReturn(
            List.of(new DataSourceReferenceDTO("JOB", "SQL 任务", "job-1", "订单 SQL"))
        );

        ResponseEntity<?> result = resource.deleteDataSource("ds1", true);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        verify(dataSourceService).delete("ds1");
    }

    private Map<String, Object> request(String sql, Object limit) {
        Map<String, Object> body = new HashMap<>();
        body.put("sql", sql);
        body.put("limit", limit);
        return body;
    }
}
