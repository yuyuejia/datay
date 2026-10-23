package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.RoleDatasetScopeService;
import com.data.datafusion.service.dataset.DatasetScopeCondition;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DatasetQueryService} 数据集行级数据权限应用的单元测试。
 */
class DatasetQueryServiceTest {

    private MetricQueryService metricQueryService;
    private DataSourceService dataSourceService;
    private DataSourceQueryService dataSourceQueryService;
    private RoleDatasetScopeService roleDatasetScopeService;
    private AnalysisDatasetResolver datasetResolver;
    private DatasetQueryService datasetQueryService;

    @BeforeEach
    void setUp() {
        metricQueryService = mock(MetricQueryService.class);
        dataSourceService = mock(DataSourceService.class);
        dataSourceQueryService = mock(DataSourceQueryService.class);
        roleDatasetScopeService = mock(RoleDatasetScopeService.class);
        datasetResolver = mock(AnalysisDatasetResolver.class);
        datasetQueryService = new DatasetQueryService(
            metricQueryService,
            dataSourceService,
            dataSourceQueryService,
            roleDatasetScopeService,
            datasetResolver,
            new ObjectMapper()
        );
    }

    @Test
    void shouldWrapSqlDatasetWithScopePredicate() throws Exception {
        Map<String, Object> dataset = sqlDataset();
        when(roleDatasetScopeService.resolveConditions("lib-1")).thenReturn(List.of(condition("region", "EQ", "华东")));
        when(dataSourceService.findOne("source-1")).thenReturn(Optional.of(dataSource()));
        when(dataSourceQueryService.executeQuery(any(), any())).thenReturn(new LinkedHashMap<>(Map.of("columns", List.of(), "rows", List.of())));

        datasetQueryService.query(dataset, null, List.of(), Map.of(), "lib-1");

        verify(dataSourceQueryService)
            .executeQuery(
                any(),
                eq("SELECT * FROM (SELECT region, amount FROM orders) dp_dataset WHERE ((region = '华东'))")
            );
    }

    @Test
    void shouldPassScopeConditionsToMetricQuery() throws Exception {
        Map<String, Object> dataset = metricDataset();
        List<DatasetScopeCondition> conditions = List.of(condition("region", "EQ", "华东"));
        when(roleDatasetScopeService.resolveConditions("lib-1")).thenReturn(conditions);
        when(metricQueryService.query(any(), anyList())).thenReturn(new LinkedHashMap<>(Map.of("columns", List.of(), "rows", List.of())));

        datasetQueryService.query(dataset, null, List.of(), Map.of(), "lib-1");

        verify(metricQueryService).query(any(), eq(conditions));
    }

    @Test
    void shouldNotApplyScopeWhenDatasetIdIsNull() throws Exception {
        Map<String, Object> dataset = sqlDataset();
        when(roleDatasetScopeService.resolveConditions(null)).thenReturn(List.of());
        when(dataSourceService.findOne("source-1")).thenReturn(Optional.of(dataSource()));
        when(dataSourceQueryService.executeQuery(any(), any())).thenReturn(new LinkedHashMap<>(Map.of("columns", List.of(), "rows", List.of())));

        Map<String, Object> result = datasetQueryService.query(dataset, null, List.of(), Map.of(), null);

        verify(dataSourceQueryService).executeQuery(any(), eq("SELECT region, amount FROM orders"));
        assertThat(result.get("sql")).isEqualTo("SELECT region, amount FROM orders");
    }

    @Test
    void shouldLoadSqlDatasetFieldValues() throws Exception {
        when(datasetResolver.resolveDataset(any())).thenReturn(sqlDataset());
        when(roleDatasetScopeService.resolveConditions("lib-1")).thenReturn(List.of());
        when(dataSourceService.findOne("source-1")).thenReturn(Optional.of(dataSource()));
        when(dataSourceQueryService.executeQuery(any(), any()))
            .thenReturn(rows(Map.of("dp_value", "华东"), Map.of("dp_value", "华南")))
            .thenReturn(rows(Map.of("cnt", 2)));

        Map<String, Object> result = datasetQueryService.loadFieldValues("lib-1", "region", "华", 1, 20);

        assertThat(result.get("values")).isEqualTo(List.of("华东", "华南"));
        assertThat(result.get("total")).isEqualTo(2L);
        verify(dataSourceQueryService)
            .executeQuery(
                any(),
                eq(
                    "SELECT DISTINCT region AS dp_value FROM (SELECT region, amount FROM orders) dp_values" +
                    " WHERE region IS NOT NULL AND region LIKE '%华%' ORDER BY dp_value LIMIT 20 OFFSET 0"
                )
            );
    }

    @Test
    void shouldLoadMetricDatasetFieldValues() throws Exception {
        when(datasetResolver.resolveDataset(any())).thenReturn(metricDataset());
        when(roleDatasetScopeService.resolveConditions("lib-1")).thenReturn(List.of());
        when(metricQueryService.plan(any()))
            .thenReturn(new MetricQueryService.QueryPlan("SELECT region, sales_amount FROM fact_sales", dataSource(), Map.of()));
        when(dataSourceQueryService.executeQuery(any(), any()))
            .thenReturn(rows(Map.of("dp_value", "华东")))
            .thenReturn(rows(Map.of("cnt", 1)));

        Map<String, Object> result = datasetQueryService.loadFieldValues("lib-1", "region", null, 1, 20);

        assertThat(result.get("values")).isEqualTo(List.of("华东"));
        assertThat(result.get("total")).isEqualTo(1L);
        verify(metricQueryService).plan(any());
    }

    @Test
    void shouldRejectUnknownFieldWhenLoadingFieldValues() {
        Map<String, Object> dataset = sqlDataset();
        dataset.put("fields", "{\"columns\":[{\"name\":\"region\"}]}");
        when(datasetResolver.resolveDataset(any())).thenReturn(dataset);

        assertThatThrownBy(() -> datasetQueryService.loadFieldValues("lib-1", "unknown", null, 1, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据集字段不存在");
    }

    @SafeVarargs
    private Map<String, Object> rows(Map<String, Object>... rowItems) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", List.of());
        result.put("rows", List.of(rowItems));
        return result;
    }

    private Map<String, Object> sqlDataset() {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "ds-1");
        dataset.put("type", "SQL");
        dataset.put("sql", "SELECT region, amount FROM orders");
        dataset.put("dataSourceId", "source-1");
        dataset.put("fields", "{\"columns\":[{\"name\":\"region\"},{\"name\":\"amount\"}]}");
        return dataset;
    }

    private Map<String, Object> metricDataset() {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "ds-local");
        dataset.put("refId", "lib-1");
        dataset.put("type", "METRIC");
        dataset.put("fields", "{\"columns\":[{\"name\":\"region\"}]}");
        dataset.put("metricQuery", Map.of("metricCodes", List.of("sales_amount")));
        return dataset;
    }

    private DataSourceDTO dataSource() {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setId("source-1");
        dto.setType("MYSQL");
        return dto;
    }

    private DatasetScopeCondition condition(String fieldName, String operator, String value) {
        DatasetScopeCondition condition = new DatasetScopeCondition();
        condition.setFieldName(fieldName);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }
}
