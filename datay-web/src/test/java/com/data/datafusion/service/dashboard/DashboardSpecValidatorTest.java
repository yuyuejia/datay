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
import org.junit.jupiter.api.Test;

/**
 * {@link DashboardSpecValidator} 与 {@link DashboardSqlGuard} 单元测试。
 *
 * <p>当前只支持 spec v1；历史 dataRef / bindings / datasetId+field 会被拒绝。
 */
class DashboardSpecValidatorTest {

    private final AnalysisDatasetRepository datasetRepository = mock(AnalysisDatasetRepository.class);

    private final AnalysisDatasetResolver resolver = new AnalysisDatasetResolver(datasetRepository, new ObjectMapper());

    private final DashboardSpecValidator validator = new DashboardSpecValidator(new ObjectMapper(), resolver);

    private static final String METRIC_SPEC = """
        {
          "version": 1,
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
              "encode": {
                "categoryField": "month",
                "values": [ { "name": "销售额", "field": "sales_amount", "agg": "sum" } ]
              },
              "chart": { "type": "line" },
              "echartsOption": { "series": [ { "type": "line" } ] }
            }
          ],
          "filters": [
            {
              "id": "date_range",
              "type": "dateRange",
              "targets": [
                { "datasetId": "ds1", "mode": "server", "pushdown": { "kind": "metricTime" } }
              ]
            }
          ]
        }
        """;

    @Test
    void shouldNormalizeValidMetricSpec() {
        Map<String, Object> spec = validator.validateAndNormalize(METRIC_SPEC);

        assertThat(spec)
            .containsKeys("version", "layout", "datasets", "widgets", "filters")
            .containsEntry("version", 1);
        assertThat(spec.get("filters")).asList().hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> layout = (Map<String, Object>) spec.get("layout");
        assertThat(layout).containsEntry("mode", "standard").doesNotContainKeys("columns", "rowHeight", "gap", "screen");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeScreenLayout() {
        String spec =
            """
            {
              "version": 1,
              "layout": {
                "mode": "screen",
                "columns": 12,
                "rowHeight": 999,
                "gap": -5,
                "screen": { "width": 100, "height": 9000 }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "datasetId": "ds1", "layout": { "x": 20, "y": 0, "w": 12, "h": 4 } } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> layout = (Map<String, Object>) normalized.get("layout");
        assertThat(layout).containsEntry("mode", "screen").doesNotContainKeys("columns", "rowHeight", "gap");
        Map<String, Object> screen = (Map<String, Object>) layout.get("screen");
        assertThat(screen)
            .containsEntry("width", 640)
            .containsEntry("height", 4320)
            .containsEntry("fitMode", "stretch")
            .containsEntry("theme", "light")
            .containsEntry("autoRefreshSeconds", 0);

        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> widgetLayout = (Map<String, Object>) widgets.get(0).get("layout");
        assertThat(widgetLayout).containsEntry("x", 12).containsEntry("w", 12);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeScreenAppearanceSettings() {
        String spec =
            """
            {
              "version": 1,
              "layout": {
                "mode": "screen",
                "screen": {
                  "width": 1920,
                  "height": 1080,
                  "fitMode": "cover",
                  "theme": "dark",
                  "autoRefreshSeconds": 30,
                  "background": {
                    "type": "gradient",
                    "gradient": { "from": "#000000", "to": "#ffffff", "angle": 45 }
                  }
                }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "datasetId": "ds1" } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> layout = (Map<String, Object>) normalized.get("layout");
        Map<String, Object> screen = (Map<String, Object>) layout.get("screen");
        assertThat(screen)
            .containsEntry("fitMode", "cover")
            .containsEntry("theme", "dark")
            .containsEntry("autoRefreshSeconds", 30);
        Map<String, Object> background = (Map<String, Object>) screen.get("background");
        assertThat(background).containsEntry("type", "gradient");
        Map<String, Object> gradient = (Map<String, Object>) background.get("gradient");
        assertThat(gradient).containsEntry("from", "#000000").containsEntry("to", "#ffffff").containsEntry("angle", 45);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackInvalidScreenAppearanceSettings() {
        String spec =
            """
            {
              "version": 1,
              "layout": {
                "mode": "screen",
                "screen": {
                  "width": 1920,
                  "height": 1080,
                  "fitMode": "unknown",
                  "theme": "unknown",
                  "autoRefreshSeconds": 999999,
                  "background": { "type": "image", "imagePath": "" }
                }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "datasetId": "ds1" } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> layout = (Map<String, Object>) normalized.get("layout");
        Map<String, Object> screen = (Map<String, Object>) layout.get("screen");
        assertThat(screen)
            .containsEntry("fitMode", "stretch")
            .containsEntry("theme", "light")
            .containsEntry("autoRefreshSeconds", 86400);
        Map<String, Object> background = (Map<String, Object>) screen.get("background");
        assertThat(background).containsEntry("type", "color").containsEntry("color", "#f5f7fa");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcceptMultipleFilterTargets() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [
                { "id": "ds1", "type": "SQL", "sql": "SELECT region FROM t1" },
                { "id": "ds2", "type": "SQL", "sql": "SELECT region FROM t2" }
              ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                {
                  "id": "region",
                  "type": "select",
                  "targets": [
                    { "datasetId": "ds1", "field": "region", "mode": "client" },
                    { "datasetId": "ds2", "field": "region", "mode": "client" }
                  ]
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);

        List<Map<String, Object>> filters = (List<Map<String, Object>>) normalized.get("filters");
        List<Map<String, Object>> targets = (List<Map<String, Object>>) filters.get(0).get("targets");
        assertThat(targets).hasSize(2);
        assertThat(targets.get(1)).containsEntry("datasetId", "ds2").containsEntry("field", "region");
    }

    @Test
    void shouldRejectMissingVersion() {
        String spec =
            """
            {
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少 version");
    }

    @Test
    void shouldRejectUnsupportedSpecVersion() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的看板 spec 版本");
    }

    @Test
    void shouldRejectLegacyWidgetDataRef() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "k1",
                  "type": "kpi",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "dataRef": { "valueField": "m1" }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dataRef");
    }

    @Test
    void shouldRejectLegacyFilterFields() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [ { "id": "region", "type": "select", "datasetId": "ds1", "field": "region" } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("targets");
    }

    @Test
    void shouldRejectFilterWithoutTargets() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [ { "id": "region", "type": "select", "targets": [] } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少 targets");
    }

    @Test
    void shouldRejectFilterTargetWithUnknownDataset() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                { "id": "region", "type": "select", "targets": [ { "datasetId": "missing", "field": "region" } ] }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的数据集");
    }

    @Test
    void shouldRejectDirectDateRangeForMetricDataset() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                { "id": "f1", "type": "dateRange", "targets": [ { "datasetId": "ds1", "field": "stat_date" } ] }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("metricTime");
    }

    @Test
    void shouldRejectTargetWithBothFieldAndPushdown() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                {
                  "id": "f1",
                  "type": "select",
                  "targets": [
                    {
                      "datasetId": "ds1",
                      "field": "region",
                      "pushdown": { "kind": "metricDimension", "dimensionModelCode": "dim", "dimensionFieldName": "region" }
                    }
                  ]
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能同时配置");
    }

    @Test
    void shouldRejectSqlTargetWithNonSqlPushdown() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "SQL", "dataSourceId": 1, "sql": "SELECT 1 AS a" } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                { "id": "f1", "type": "select", "targets": [ { "datasetId": "ds1", "pushdown": { "kind": "metricDimension" } } ] }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("只支持 kind=sqlParam");
    }

    @Test
    void shouldRejectNonReadOnlySql() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "SQL", "dataSourceId": 1, "sql": "DELETE FROM t" } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPushdownToUnknownDataset() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                { "id": "f1", "type": "select", "targets": [ { "datasetId": "missing", "pushdown": { "kind": "metricDimension" } } ] }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireSqlParamFields() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "SQL", "dataSourceId": 1, "sql": "SELECT 1 AS a" } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                { "id": "f1", "type": "input", "targets": [ { "datasetId": "ds1", "pushdown": { "kind": "sqlParam" } } ] }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDuplicateWidgetId() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" },
                { "id": "w1", "type": "table", "layout": { "x":6,"y":0,"w":6,"h":4 }, "datasetId": "ds1" }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("组件 id 重复");
    }

    @Test
    void shouldRejectNonHtmlWidgetWithoutDataset() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 } } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少 datasetId");
    }

    @Test
    void shouldRejectChartWithoutEncode() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "echartsOption": { "series": [ { "type": "bar" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少 encode");
    }

    @Test
    void shouldRejectKpiWithoutEncode() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "k1", "type": "kpi", "datasetId": "ds1" } ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少 encode");
    }

    @Test
    void shouldRejectOverlappingWidgets() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" },
                { "id": "w2", "type": "table", "layout": { "x":3,"y":2,"w":6,"h":4 }, "datasetId": "ds1" }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("布局重叠");
    }

    @Test
    void shouldRejectWidgetFieldMissingFromDatasetCache() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [
                {
                  "id": "ds1",
                  "type": "SQL",
                  "dataSourceId": 1,
                  "sql": "SELECT month, sales_amount FROM t",
                  "fields": { "columns": [ { "name": "month" }, { "name": "sales_amount" } ] }
                }
              ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "encode": {
                    "categoryField": "month",
                    "values": [ { "name": "销售额", "field": "missing_field", "agg": "sum" } ]
                  },
                  "chart": { "type": "line" },
                  "echartsOption": { "series": [ { "type": "line" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的字段");
    }

    @Test
    void shouldRejectFilterFieldMissingFromDatasetCache() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [
                {
                  "id": "ds1",
                  "type": "SQL",
                  "dataSourceId": 1,
                  "sql": "SELECT month, sales_amount FROM t",
                  "fields": { "columns": [ { "name": "month" }, { "name": "sales_amount" } ] }
                }
              ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                { "id": "f1", "type": "select", "targets": [ { "datasetId": "ds1", "field": "missing_field", "mode": "client" } ] }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的字段");
    }

    @Test
    void shouldRejectInvalidMultiSelectDefaultValue() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                {
                  "id": "f1",
                  "type": "multiSelect",
                  "targets": [ { "datasetId": "ds1", "field": "region", "mode": "client" } ],
                  "defaultValue": "华东"
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("默认值必须是数组");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTreatEmptyRangeDefaultValueAsNoDefault() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                {
                  "id": "f1",
                  "type": "dateRange",
                  "targets": [ { "datasetId": "ds1", "mode": "server", "pushdown": { "kind": "metricTime" } } ],
                  "defaultValue": []
                },
                {
                  "id": "f2",
                  "type": "numberRange",
                  "targets": [ { "datasetId": "ds1", "field": "m1", "mode": "client" } ],
                  "defaultValue": {}
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> filters = (List<Map<String, Object>>) normalized.get("filters");
        assertThat(filters.get(0)).doesNotContainKey("defaultValue");
        assertThat(filters.get(1)).doesNotContainKey("defaultValue");
    }

    @Test
    void shouldRejectInvalidDateRangeDefaultValue() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                {
                  "id": "f1",
                  "type": "dateRange",
                  "targets": [ { "datasetId": "ds1", "mode": "server", "pushdown": { "kind": "metricTime" } } ],
                  "defaultValue": "2026-01-01"
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("{start,end}");
    }

    @Test
    void shouldRejectMissingAggregation() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "encode": { "categoryField": "month", "values": [ { "field": "m1" } ] },
                  "chart": { "type": "bar" },
                  "echartsOption": { "series": [ { "type": "bar" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("agg");
    }

    @Test
    void shouldRejectUnsupportedChartType() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "encode": { "categoryField": "month", "values": [ { "field": "m1", "agg": "sum" } ] },
                  "chart": { "type": "radar" },
                  "echartsOption": { "series": [ { "type": "radar" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的");
    }

    @Test
    void shouldValidateTableColumnConfiguration() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [
                {
                  "id": "ds1",
                  "type": "SQL",
                  "dataSourceId": 1,
                  "sql": "SELECT region, amount FROM t",
                  "fields": { "columns": [ { "name": "region" }, { "name": "amount" } ] }
                }
              ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "table",
                  "layout": { "x":0,"y":0,"w":12,"h":8 },
                  "datasetId": "ds1",
                  "encode": {
                    "limit": 100,
                    "columns": [ { "field": "region", "label": "区域", "width": 160 } ]
                  }
                }
              ]
            }
            """;
        assertThat(validator.validateAndNormalize(spec)).containsEntry("version", 1);

        String missing =
            """
            {
              "version": 1,
              "datasets": [
                {
                  "id": "ds1",
                  "type": "SQL",
                  "dataSourceId": 1,
                  "sql": "SELECT region FROM t",
                  "fields": { "columns": [ { "name": "region" } ] }
                }
              ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "table",
                  "layout": { "x":0,"y":0,"w":12,"h":8 },
                  "datasetId": "ds1",
                  "encode": { "columns": [ { "field": "missing" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(missing))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的字段");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMigrateEarlyV1EncodeValueField() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "encode": { "categoryField": "month", "valueField": "m1" },
                  "chart": { "type": "bar" },
                  "echartsOption": { "series": [ { "type": "bar" } ] }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> encode = (Map<String, Object>) widgets.get(0).get("encode");
        List<Map<String, Object>> values = (List<Map<String, Object>>) encode.get("values");
        assertThat(values).hasSize(1);
        assertThat(values.get(0)).containsEntry("field", "m1").containsEntry("agg", "sum");
        assertThat(encode).doesNotContainKey("valueField");
    }

    @Test
    void shouldRejectOversizedSpec() {
        String spec = "{" + " ".repeat(1_000_000);
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("看板定义过大");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseReferencedDatasetTypeInsteadOfSpecType() {
        AnalysisDataset entity = new AnalysisDataset();
        entity.setId("ref1");
        entity.setName("引用数据集");
        entity.setType(AnalysisDataset.TYPE_SQL);
        entity.setDefinition("{\"sql\":\"SELECT 1 AS value\"}");
        when(datasetRepository.existsById("ref1")).thenReturn(true);
        when(datasetRepository.findById("ref1")).thenReturn(Optional.of(entity));

        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "refId": "ref1", "type": "METRIC" } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) normalized.get("datasets");
        assertThat(datasets.get(0)).containsEntry("type", "SQL").containsEntry("refId", "ref1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDefaultKpiLayoutToCompactSize() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "k1", "type": "kpi", "datasetId": "ds1", "encode": { "values": [ { "field": "m1", "agg": "sum" } ] } }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> layout = (Map<String, Object>) widgets.get(0).get("layout");
        assertThat(layout).containsEntry("w", 6).containsEntry("h", 4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCapKpiHeight() {
        String spec =
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "k1",
                  "type": "kpi",
                  "datasetId": "ds1",
                  "encode": { "values": [ { "field": "m1", "agg": "sum" } ] },
                  "layout": { "x": 0, "y": 0, "w": 6, "h": 20 }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> layout = (Map<String, Object>) widgets.get(0).get("layout");
        assertThat(layout).containsEntry("h", 8);
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
}
