package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link DashboardSpecValidator} 与 {@link DashboardSqlGuard} 单元测试。
 */
class DashboardSpecValidatorTest {

    private final AnalysisDatasetResolver resolver = new AnalysisDatasetResolver(mock(AnalysisDatasetRepository.class), new ObjectMapper());

    private final DashboardSpecValidator validator = new DashboardSpecValidator(new ObjectMapper(), resolver);

    private static final String METRIC_SPEC = """
        {
          "title": "销售看板",
          "datasets": [
            {
              "id": "ds1",
              "type": "METRIC",
              "metricQuery": {
                "metricCodes": ["sales_amount"],
                "dimensions": [ { "dimensionModelCode": "dim_date", "dimensionFieldNames": ["year","month"] } ]
              }
            }
          ],
          "widgets": [
            {
              "id": "w1",
              "type": "echarts",
              "layout": { "x": 0, "y": 0, "w": 6, "h": 4 },
              "datasetId": "ds1",
              "dataRef": { "categoryField": "month", "series": [ { "name": "销售额", "field": "sales_amount" } ] },
              "echartsOption": { "series": [ { "type": "line" } ] }
            }
          ],
          "filters": [
            {
              "id": "date_range",
              "type": "dateRange",
              "bindings": [ { "datasetId": "ds1", "kind": "metricTime" } ]
            }
          ]
        }
        """;

    @Test
    void shouldNormalizeValidMetricSpec() {
        Map<String, Object> spec = validator.validateAndNormalize(METRIC_SPEC);

        assertThat(spec).containsKeys("layout", "datasets", "widgets", "filters");
        assertThat(spec.get("filters")).asList().hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> layout = (Map<String, Object>) spec.get("layout");
        assertThat(layout).containsEntry("columns", 12);
    }

    @Test
    void shouldInferMetricTimeBindingForDateRange() {
        Map<String, Object> spec = validator.validateAndNormalize(METRIC_SPEC);
        @SuppressWarnings("unchecked")
        var filters = (java.util.List<Map<String, Object>>) spec.get("filters");
        @SuppressWarnings("unchecked")
        var bindings = (java.util.List<Map<String, Object>>) filters.get(0).get("bindings");
        assertThat(bindings.get(0)).containsEntry("kind", "metricTime");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeDatasetFieldFilterWithoutBindings() {
        String spec = """
            {
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [ { "id": "region", "type": "select", "datasetId": "ds1", "field": "region" } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        var filters = (java.util.List<Map<String, Object>>) normalized.get("filters");
        Map<String, Object> filter = filters.get(0);
        assertThat(filter).containsEntry("datasetId", "ds1").containsEntry("field", "region");
        assertThat(filter).doesNotContainKey("bindings");
    }

    @Test
    void shouldRejectFilterWithUnknownDataset() {
        String spec = """
            {
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [ { "id": "region", "type": "select", "datasetId": "missing", "field": "region" } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的数据集");
    }

    @Test
    void shouldRejectNonReadOnlySql() {
        String spec = """
            {
              "datasets": [ { "id": "ds1", "type": "SQL", "dataSourceId": 1, "sql": "DELETE FROM t" } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBindingToUnknownDataset() {
        String spec = """
            {
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [ { "id": "f1", "type": "select", "bindings": [ { "datasetId": "missing", "kind": "metricDimension" } ] } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireSqlParamFields() {
        String spec = """
            {
              "datasets": [ { "id": "ds1", "type": "SQL", "dataSourceId": 1, "sql": "SELECT 1 AS a" } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [ { "id": "f1", "type": "input", "bindings": [ { "datasetId": "ds1", "kind": "sqlParam" } ] } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardShouldAllowSelectAndBlockWrite() {
        assertThat(DashboardSqlGuard.requireReadOnly("SELECT * FROM t")).startsWith("SELECT");
        assertThat(DashboardSqlGuard.requireReadOnly("  WITH x AS (SELECT 1) SELECT * FROM x")).startsWith("WITH");
        assertThatThrownBy(() -> DashboardSqlGuard.requireReadOnly("SELECT 1; DROP TABLE t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DashboardSqlGuard.requireReadOnly("UPDATE t SET a=1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardShouldAllowReadOnlyFunctionsAndIgnoreStringLiterals() {
        assertThat(DashboardSqlGuard.requireReadOnly("SELECT replace(name, 'a', 'b') FROM t")).contains("replace");
        assertThat(DashboardSqlGuard.requireReadOnly("SELECT * FROM t WHERE note = 'please update me'")).contains("SELECT");
    }

    @Test
    void guardShouldBlockWriteInsideCte() {
        assertThatThrownBy(() ->
            DashboardSqlGuard.requireReadOnly("WITH x AS (DELETE FROM t RETURNING *) SELECT * FROM x")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("写操作");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDefaultKpiLayoutToCompactSize() {
        String spec = """
            {
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "k1", "type": "kpi", "datasetId": "ds1" } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        java.util.List<Map<String, Object>> widgets = (java.util.List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> layout = (Map<String, Object>) widgets.get(0).get("layout");
        assertThat(layout).containsEntry("w", 3).containsEntry("h", 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCapKpiHeight() {
        String spec = """
            {
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "k1", "type": "kpi", "datasetId": "ds1", "layout": { "x": 0, "y": 0, "w": 3, "h": 8 } } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        java.util.List<Map<String, Object>> widgets = (java.util.List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> layout = (Map<String, Object>) widgets.get(0).get("layout");
        assertThat(layout).containsEntry("h", 4);
    }
}
