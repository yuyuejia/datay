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
 * <p>当前只支持 spec v2；历史 dataRef / bindings / v1 encode 会被拒绝。
 */
class DashboardSpecValidatorTest {

    private final AnalysisDatasetRepository datasetRepository = mock(AnalysisDatasetRepository.class);

    private final AnalysisDatasetResolver resolver = new AnalysisDatasetResolver(datasetRepository, new ObjectMapper());

    private final DashboardSpecValidator validator = new DashboardSpecValidator(new ObjectMapper(), resolver);

    private static final String METRIC_SPEC = """
        {
          "version": 2,
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
                "dimensions": [ { "field": "month", "role": "x" } ],
                "measures": [ { "name": "销售额", "field": "sales_amount", "agg": "sum", "role": "y" } ]
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
            .containsEntry("version", 2);
        assertThat(spec.get("filters")).asList().hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> layout = (Map<String, Object>) spec.get("layout");
        assertThat(layout).containsEntry("mode", "standard").containsEntry("rowHeight", 40).doesNotContainKeys("columns", "gap");
        Map<String, Object> screen = (Map<String, Object>) layout.get("screen");
        assertThat(screen).containsEntry("theme", "light").containsEntry("fitMode", "stretch");
        assertThat((Map<String, Object>) screen.get("canvas")).containsEntry("unit", "grid");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistThemePresetInStandardMode() {
        String spec =
            """
            {
              "version": 2,
              "layout": {
                "mode": "standard",
                "screen": {
                  "theme": "dark",
                  "themePreset": "tech-blue",
                  "tokens": { "primary": "#2fa8ff", "palette": ["#2fa8ff", "#1de9b6"] },
                  "canvas": { "unit": "px", "padding": 0 }
                }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "table",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 6 }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> screen = (Map<String, Object>) ((Map<String, Object>) normalized.get("layout")).get("screen");

        assertThat(screen).containsEntry("theme", "dark").containsEntry("themePreset", "tech-blue");
        assertThat((Map<String, Object>) screen.get("tokens")).containsEntry("primary", "#2fa8ff");
        // 标准模式即使传了 px，也固定回落到栅格定位。
        assertThat((Map<String, Object>) screen.get("canvas")).containsEntry("unit", "grid");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeScreenLayout() {
        String spec =
            """
            {
              "version": 2,
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
        assertThat(layout).containsEntry("mode", "screen").containsEntry("rowHeight", 80).doesNotContainKeys("columns", "gap");
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
        assertThat(targets.get(0))
            .containsEntry("datasetId", "ds1")
            .containsEntry("field", "region")
            .containsEntry("mode", "server");
        assertThat(targets.get(1))
            .containsEntry("datasetId", "ds2")
            .containsEntry("field", "region")
            .containsEntry("mode", "server");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeMetricDirectFieldFilterToServer() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                {
                  "id": "region",
                  "type": "select",
                  "targets": [ { "datasetId": "ds1", "field": "region", "mode": "client" } ]
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> filters = (List<Map<String, Object>>) normalized.get("filters");
        List<Map<String, Object>> targets = (List<Map<String, Object>>) filters.get(0).get("targets");
        assertThat(targets.get(0)).containsEntry("mode", "server").containsEntry("field", "region");
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
              "version": 3,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
    @SuppressWarnings("unchecked")
    void shouldAllowDirectDateRangeForMetricDataset() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                { "id": "f1", "type": "dateRange", "targets": [ { "datasetId": "ds1", "field": "stat_date" } ] }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> filters = (List<Map<String, Object>>) normalized.get("filters");
        List<Map<String, Object>> targets = (List<Map<String, Object>>) filters.get(0).get("targets");
        assertThat(targets.get(0)).containsEntry("field", "stat_date").containsEntry("mode", "server");
    }

    @Test
    void shouldRejectTargetWithBothFieldAndPushdown() {
        String spec =
            """
            {
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [ { "name": "销售额", "field": "missing_field", "agg": "sum", "role": "y" } ]
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
              "version": 2,
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
    void shouldKeepValidDateRangeDefaultValue() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "layout": { "x":0,"y":0,"w":6,"h":4 }, "datasetId": "ds1" } ],
              "filters": [
                {
                  "id": "f1",
                  "type": "dateRange",
                  "targets": [ { "datasetId": "ds1", "mode": "server", "pushdown": { "kind": "metricTime" } } ],
                  "defaultValue": { "start": "2026-01-01", "end": "2026-01-31" }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> filters = (List<Map<String, Object>>) normalized.get("filters");
        assertThat(filters.get(0).get("defaultValue")).isEqualTo(Map.of("start", "2026-01-01", "end", "2026-01-31"));
    }

    @Test
    void shouldRejectMissingAggregation() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "role": "y" } ] },
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
    @SuppressWarnings("unchecked")
    void shouldNormalizeChartVariantsAndComboSeries() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1", "m2"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":12,"h":8 },
                  "datasetId": "ds1",
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [
                      { "field": "m1", "agg": "sum", "role": "y" },
                      { "field": "m2", "agg": "sum", "role": "y" }
                    ],
                    "groupBy": true
                  },
                  "chart": {
                    "type": "bar",
                    "subtype": "stackedPercent",
                    "orientation": "horizontal",
                    "series": [
                      { "measure": "m1", "type": "bar", "axis": "left" },
                      { "measure": "m2", "type": "line", "axis": "right" }
                    ]
                  },
                  "echartsOption": { "series": [ { "type": "bar" }, { "type": "line" } ] }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> chart = (Map<String, Object>) widgets.get(0).get("chart");
        assertThat(chart)
            .containsEntry("type", "bar")
            .containsEntry("subtype", "stackedPercent")
            .containsEntry("orientation", "horizontal");
        assertThat(chart.get("series")).asList().hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowGaugeWithoutDimension() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":8 },
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ], "groupBy": false },
                  "chart": { "type": "gauge" },
                  "echartsOption": { "series": [ { "type": "gauge" } ] }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> encode = (Map<String, Object>) widgets.get(0).get("encode");
        assertThat(encode).doesNotContainKey("dimensions");
        assertThat(encode.get("measures")).asList().hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcceptAdvancedChartsAndPivot() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1", "m2"] } } ],
              "widgets": [
                {
                  "id": "w1", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ], "groupBy": false },
                  "chart": { "type": "scatter" },
                  "echartsOption": { "series": [ { "type": "scatter" } ] }
                },
                {
                  "id": "w2", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":6,"y":0,"w":6,"h":4 },
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ] },
                  "chart": { "type": "radar" },
                  "echartsOption": { "series": [ { "type": "radar" } ] }
                },
                {
                  "id": "w3", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":12,"y":0,"w":6,"h":4 },
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "chart": { "type": "funnel" },
                  "echartsOption": { "series": [ { "type": "funnel" } ] }
                },
                {
                  "id": "w4", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":18,"y":0,"w":6,"h":4 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" }, { "field": "region", "role": "series" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ]
                  },
                  "chart": { "type": "heatmap" },
                  "echartsOption": { "series": [ { "type": "heatmap" } ] }
                },
                {
                  "id": "w5", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":0,"y":4,"w":6,"h":4 },
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "chart": { "type": "treemap" },
                  "echartsOption": { "series": [ { "type": "treemap" } ] }
                },
                {
                  "id": "w6", "type": "pivot", "datasetId": "ds1",
                  "layout": { "x":6,"y":4,"w":12,"h":8 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "row" }, { "field": "region", "role": "column" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ]
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        assertThat(normalized.get("widgets")).asList().hasSize(6);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        assertThat(((Map<String, Object>) widgets.get(0).get("chart")).get("type")).isEqualTo("scatter");
        assertThat(((Map<String, Object>) widgets.get(1).get("chart")).get("type")).isEqualTo("radar");
        assertThat(((Map<String, Object>) widgets.get(2).get("chart")).get("type")).isEqualTo("funnel");
        assertThat(((Map<String, Object>) widgets.get(3).get("chart")).get("type")).isEqualTo("heatmap");
        assertThat(((Map<String, Object>) widgets.get(4).get("chart")).get("type")).isEqualTo("treemap");
        assertThat(widgets.get(5)).containsEntry("type", "pivot");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcceptP2ChartsAndFormatRules() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1", "m2"] } } ],
              "widgets": [
                {
                  "id": "w1", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ] },
                  "chart": { "type": "waterfall" },
                  "echartsOption": { "series": [ { "type": "bar" } ] }
                },
                {
                  "id": "w2", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":6,"y":0,"w":6,"h":4 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" }, { "field": "region", "role": "series" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ]
                  },
                  "chart": { "type": "sankey" },
                  "echartsOption": { "series": [ { "type": "sankey" } ] }
                },
                {
                  "id": "w3", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":12,"y":0,"w":6,"h":4 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" }, { "field": "region", "role": "series" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ]
                  },
                  "chart": { "type": "graph" },
                  "echartsOption": { "series": [ { "type": "graph" } ] }
                },
                {
                  "id": "w4", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":18,"y":0,"w":6,"h":4 },
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ], "groupBy": false },
                  "chart": { "type": "boxplot" },
                  "echartsOption": { "series": [ { "type": "boxplot" } ] }
                },
                {
                  "id": "t1", "type": "table", "datasetId": "ds1",
                  "layout": { "x":0,"y":4,"w":12,"h":8 },
                  "encode": { "limit": 100 },
                  "formatRules": [
                    { "field": "m1", "operator": "gt", "value": 100, "color": "#67c23a", "bold": true },
                    { "operator": "between", "value": 0, "value2": 50, "background": "#fef0f0" }
                  ]
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        assertThat(widgets).hasSize(5);
        assertThat(((Map<String, Object>) widgets.get(0).get("chart")).get("type")).isEqualTo("waterfall");
        assertThat(((Map<String, Object>) widgets.get(1).get("chart")).get("type")).isEqualTo("sankey");
        assertThat(((Map<String, Object>) widgets.get(2).get("chart")).get("type")).isEqualTo("graph");
        assertThat(((Map<String, Object>) widgets.get(3).get("chart")).get("type")).isEqualTo("boxplot");
        assertThat(widgets.get(4).get("formatRules")).asList().hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcceptMapChartAndDefaultRegion() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":0,"y":0,"w":24,"h":12 },
                  "encode": {
                    "dimensions": [ { "field": "province", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ]
                  },
                  "chart": { "type": "map" },
                  "echartsOption": { "series": [ { "type": "map" } ] }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        assertThat(((Map<String, Object>) widgets.get(0).get("chart")).get("type")).isEqualTo("map");
        assertThat(((Map<String, Object>) widgets.get(0).get("chart")).get("map")).isEqualTo("china");
    }

    @Test
    void shouldRejectUnsupportedMapRegion() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":0,"y":0,"w":24,"h":12 },
                  "encode": {
                    "dimensions": [ { "field": "province", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ]
                  },
                  "chart": { "type": "map", "map": "world" },
                  "echartsOption": { "series": [ { "type": "map" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的地图区域");
    }

    @Test
    void shouldRejectInvalidFormatRule() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "t1", "type": "table", "datasetId": "ds1",
                  "layout": { "x":0,"y":0,"w":12,"h":8 },
                  "encode": { "limit": 100 },
                  "formatRules": [ { "field": "m1", "operator": "unknown", "value": 100 } ]
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("条件格式 operator");
    }

    @Test
    void shouldRejectHeatmapWithoutSeriesDimension() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1", "type": "echarts", "datasetId": "ds1",
                  "layout": { "x":0,"y":0,"w":12,"h":8 },
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "chart": { "type": "heatmap" },
                  "echartsOption": { "series": [ { "type": "heatmap" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("热力图需要 role=series");
    }

    @Test
    void shouldRejectPivotWithoutRowDimension() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "p1", "type": "pivot", "datasetId": "ds1",
                  "layout": { "x":0,"y":0,"w":12,"h":8 },
                  "encode": {
                    "dimensions": [ { "field": "region", "role": "column" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ]
                  }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("需要 role=row");
    }

    @Test
    void shouldRejectUnsupportedChartType() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "encode": { "dimensions": [ { "field": "month", "role": "x" } ], "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ] },
                  "chart": { "type": "sunburst" },
                  "echartsOption": { "series": [ { "type": "sunburst" } ] }
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
              "version": 2,
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
        assertThat(validator.validateAndNormalize(spec)).containsEntry("version", 2);

        String missing =
            """
            {
              "version": 2,
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
    void shouldRejectLegacyV1EncodeFields() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "layout": { "x":0,"y":0,"w":6,"h":4 },
                  "datasetId": "ds1",
                  "encode": { "categoryField": "month", "values": [ { "field": "m1", "agg": "sum" } ] },
                  "chart": { "type": "bar" },
                  "echartsOption": { "series": [ { "type": "bar" } ] }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("已废弃的 encode.categoryField");
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
              "version": 2,
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
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "k1", "type": "kpi", "datasetId": "ds1", "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] } }
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
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "k1",
                  "type": "kpi",
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
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
    @SuppressWarnings("unchecked")
    void shouldNormalizePixelCanvasAndAllowOverlap() {
        String spec =
            """
            {
              "version": 2,
              "layout": {
                "mode": "screen",
                "screen": {
                  "width": 1920,
                  "height": 1080,
                  "canvas": { "unit": "px", "padding": 0, "gap": 200 }
                }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "w1", "type": "table", "datasetId": "ds1", "zIndex": 5, "layout": { "x": 0, "y": 0, "w": 600, "h": 300 } },
                { "id": "w2", "type": "table", "datasetId": "ds1", "zIndex": 5000, "layout": { "x": 100, "y": 100, "w": 600, "h": 300 } },
                { "id": "w3", "type": "table", "datasetId": "ds1", "layout": { "x": 5000, "y": 5000, "w": 600, "h": 300 } }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> layout = (Map<String, Object>) normalized.get("layout");
        Map<String, Object> screen = (Map<String, Object>) layout.get("screen");
        assertThat(screen.get("canvas")).isEqualTo(Map.of("unit", "px", "padding", 0, "gap", 48));

        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        assertThat((Map<String, Object>) widgets.get(0).get("layout")).containsEntry("x", 0).containsEntry("y", 0);
        assertThat(widgets.get(0)).containsEntry("zIndex", 5);
        assertThat(widgets.get(1)).containsEntry("zIndex", 999);
        assertThat((Map<String, Object>) widgets.get(2).get("layout"))
            .containsEntry("x", 1320)
            .containsEntry("y", 780)
            .containsEntry("w", 600)
            .containsEntry("h", 300);
        assertThat(widgets.get(2)).doesNotContainKey("zIndex");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClampPixelLayoutInsideCanvasPadding() {
        String spec =
            """
            {
              "version": 2,
              "layout": {
                "mode": "screen",
                "screen": {
                  "width": 1920,
                  "height": 1080,
                  "canvas": { "unit": "px", "padding": 300 }
                }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "w1", "type": "table", "datasetId": "ds1", "layout": { "x": -50, "y": -50, "w": 5000, "h": 5000 } }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> screen = (Map<String, Object>) ((Map<String, Object>) normalized.get("layout")).get("screen");
        assertThat((Map<String, Object>) screen.get("canvas")).containsEntry("padding", 64);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        assertThat((Map<String, Object>) widgets.get(0).get("layout"))
            .containsEntry("x", 0)
            .containsEntry("y", 0)
            .containsEntry("w", 1792)
            .containsEntry("h", 952);
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
    @SuppressWarnings("unchecked")
    void shouldNormalizeWidgetStyleAndThemeTokens() {
        String spec =
            """
            {
              "version": 2,
              "layout": {
                "mode": "screen",
                "screen": {
                  "width": 1920,
                  "height": 1080,
                  "theme": "dark",
                  "themePreset": "tech-blue",
                  "tokens": {
                    "primary": "#2fa8ff",
                    "surface": "rgba(9,26,48,0.55)",
                    "border": "not-a-color",
                    "palette": ["#2fa8ff", "#1de9b6", "invalid"],
                    "fontFamily": "number-digital",
                    "map": { "area": "#0b3a5b" }
                  }
                }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "table",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 600, "h": 300 },
                  "style": {
                    "card": {
                      "preset": "tech-corner",
                      "fill": { "type": "gradient", "gradient": { "from": "#0a1b2e", "to": "#0d2643", "angle": 180 } },
                      "opacity": 0.85,
                      "border": { "width": 2, "color": "#1b5c8a", "style": "dashed", "radius": 6 },
                      "shadow": { "x": 0, "y": 0, "blur": 18, "spread": 0, "color": "rgba(0,180,255,0.25)" },
                      "padding": 8,
                      "unknown": "ignored"
                    },
                    "title": {
                      "icon": "chart-line",
                      "fontSize": 16,
                      "color": "#7fe3ff",
                      "align": "center",
                      "height": 36,
                      "letterSpacing": 1,
                      "decoration": "gradient-underline"
                    },
                    "body": { "padding": 4 }
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> screen = (Map<String, Object>) ((Map<String, Object>) normalized.get("layout")).get("screen");
        assertThat(screen).containsEntry("themePreset", "tech-blue");

        Map<String, Object> tokens = (Map<String, Object>) screen.get("tokens");
        assertThat(tokens).containsEntry("primary", "#2fa8ff").containsEntry("fontFamily", "number-digital");
        assertThat(tokens).doesNotContainKey("border");
        assertThat((List<String>) tokens.get("palette")).containsExactly("#2fa8ff", "#1de9b6");
        assertThat((Map<String, Object>) tokens.get("map")).containsEntry("area", "#0b3a5b");

        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> style = (Map<String, Object>) widgets.get(0).get("style");
        Map<String, Object> card = (Map<String, Object>) style.get("card");
        assertThat(card).containsEntry("preset", "tech-corner").containsEntry("opacity", 0.85).containsEntry("padding", 8);
        assertThat(card).doesNotContainKey("unknown");
        assertThat((Map<String, Object>) card.get("fill")).containsEntry("type", "gradient");
        assertThat((Map<String, Object>) card.get("border"))
            .containsEntry("width", 2)
            .containsEntry("style", "dashed")
            .containsEntry("radius", 6);
        Map<String, Object> title = (Map<String, Object>) style.get("title");
        assertThat(title)
            .containsEntry("icon", "chart-line")
            .containsEntry("align", "center")
            .containsEntry("decoration", "gradient-underline");
        assertThat((Map<String, Object>) style.get("body")).containsEntry("padding", 4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDropInvalidStyleValues() {
        String spec =
            """
            {
              "version": 2,
              "layout": {
                "mode": "screen",
                "screen": { "width": 1920, "height": 1080, "themePreset": "unknown-preset" }
              },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "table",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 600, "h": 300 },
                  "style": {
                    "card": {
                      "preset": "not-a-preset",
                      "fill": { "type": "neon", "color": "#ffffff" },
                      "opacity": 5,
                      "border": { "color": "nope" }
                    },
                    "title": { "icon": "Bad Icon!", "align": "justify", "fontSize": 200, "show": false }
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        Map<String, Object> screen = (Map<String, Object>) ((Map<String, Object>) normalized.get("layout")).get("screen");
        assertThat(screen).doesNotContainKey("themePreset");

        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> style = (Map<String, Object>) widgets.get(0).get("style");
        Map<String, Object> card = (Map<String, Object>) style.get("card");
        assertThat(card).doesNotContainKeys("preset", "fill", "border");
        assertThat(card).containsEntry("opacity", 1.0);
        Map<String, Object> title = (Map<String, Object>) style.get("title");
        assertThat(title).doesNotContainKeys("icon", "align");
        assertThat(title).containsEntry("fontSize", 48).containsEntry("show", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeKpiStyle() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "k1",
                  "type": "kpi",
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "layout": { "x": 0, "y": 0, "w": 6, "h": 4 },
                  "style": {
                    "kpi": {
                      "label": "学生总数",
                      "icon": "users",
                      "layout": "label-left",
                      "value": {
                        "fontSize": 56,
                        "color": "#eaf6ff",
                        "fontFamily": "number-digital",
                        "bold": true,
                        "letterSpacing": 2
                      },
                      "unit": { "text": "人", "fontSize": 16, "color": "#8fb6d6" },
                      "animate": true,
                      "decimals": 1,
                      "unknown": "ignored"
                    }
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> kpi = (Map<String, Object>) ((Map<String, Object>) widgets.get(0).get("style")).get("kpi");

        assertThat(kpi)
            .containsEntry("label", "学生总数")
            .containsEntry("icon", "users")
            .containsEntry("layout", "label-left")
            .containsEntry("animate", true)
            .containsEntry("decimals", 1)
            .doesNotContainKey("unknown");
        assertThat((Map<String, Object>) kpi.get("value"))
            .containsEntry("fontSize", 56)
            .containsEntry("fontFamily", "number-digital")
            .containsEntry("bold", true)
            .containsEntry("letterSpacing", 2);
        assertThat((Map<String, Object>) kpi.get("unit"))
            .containsEntry("text", "人")
            .containsEntry("fontSize", 16)
            .containsEntry("color", "#8fb6d6");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClampInvalidKpiStyleValues() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "k1",
                  "type": "kpi",
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "layout": { "x": 0, "y": 0, "w": 6, "h": 4 },
                  "style": {
                    "kpi": {
                      "label": "这是一个超过四十个字符的标签这是一个超过四十个字符的标签这是一个超过四十个字符的标签",
                      "layout": "diagonal",
                      "value": { "fontSize": 500, "fontFamily": "comic-sans" },
                      "unit": { "text": "一个非常非常非常长的单位文本", "fontSize": 200 },
                      "animate": "true",
                      "decimals": 9
                    }
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> kpi = (Map<String, Object>) ((Map<String, Object>) widgets.get(0).get("style")).get("kpi");

        assertThat(kpi).doesNotContainKeys("label", "layout");
        assertThat(kpi).containsEntry("animate", true).containsEntry("decimals", 6);
        assertThat((Map<String, Object>) kpi.get("value")).containsEntry("fontSize", 120);
        assertThat((Map<String, Object>) kpi.get("value")).doesNotContainKey("fontFamily");
        assertThat((Map<String, Object>) kpi.get("unit")).containsEntry("fontSize", 40).doesNotContainKey("text");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeMapLayers() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [
                {
                  "id": "ds1",
                  "type": "SQL",
                  "sql": "select province, students from t",
                  "fields": { "columns": [ { "name": "province" }, { "name": "students" } ] }
                },
                {
                  "id": "ds_city",
                  "type": "SQL",
                  "sql": "select city, lng, lat, students from c",
                  "fields": { "columns": [ { "name": "city" }, { "name": "lng" }, { "name": "lat" }, { "name": "students" } ] }
                },
                {
                  "id": "ds_flow",
                  "type": "SQL",
                  "sql": "select from_lng, from_lat, to_lng, to_lat, flow from f",
                  "fields": { "columns": [ { "name": "from_lng" }, { "name": "from_lat" }, { "name": "to_lng" }, { "name": "to_lat" }, { "name": "flow" } ] }
                }
              ],
              "widgets": [
                {
                  "id": "m1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 8 },
                  "encode": {
                    "dimensions": [ { "field": "province", "role": "x" } ],
                    "measures": [ { "field": "students", "agg": "sum", "role": "value" } ]
                  },
                  "chart": {
                    "type": "map",
                    "map": "china",
                    "layers": [
                      { "kind": "region", "valueField": "students" },
                      {
                        "kind": "effectScatter",
                        "datasetId": "ds_city",
                        "lngField": "lng",
                        "latField": "lat",
                        "nameField": "city",
                        "valueField": "students",
                        "symbolSize": [ 10, 30 ],
                        "color": "#ffd54f"
                      },
                      {
                        "kind": "lines",
                        "datasetId": "ds_flow",
                        "fromLngField": "from_lng",
                        "fromLatField": "from_lat",
                        "toLngField": "to_lng",
                        "toLatField": "to_lat",
                        "valueField": "flow",
                        "color": "#ffd54f",
                        "curveness": 0.3,
                        "effect": { "show": true, "period": 4, "trailLength": 0.4 }
                      }
                    ]
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> chart = (Map<String, Object>) widgets.get(0).get("chart");
        List<Map<String, Object>> layers = (List<Map<String, Object>>) chart.get("layers");

        assertThat(layers).hasSize(3);
        assertThat(layers.get(0)).containsEntry("kind", "region").containsEntry("valueField", "students").containsEntry("agg", "sum");
        assertThat(layers.get(1))
            .containsEntry("kind", "effectScatter")
            .containsEntry("datasetId", "ds_city")
            .containsEntry("lngField", "lng")
            .containsEntry("nameField", "city");
        assertThat((List<Integer>) layers.get(1).get("symbolSize")).containsExactly(10, 30);
        assertThat(layers.get(2))
            .containsEntry("kind", "lines")
            .containsEntry("toLngField", "to_lng")
            .containsEntry("curveness", 0.3);
        assertThat((Map<String, Object>) layers.get(2).get("effect"))
            .containsEntry("show", true)
            .containsEntry("period", 4)
            .containsEntry("trailLength", 0.4);
    }

    @Test
    void shouldRejectInvalidMapLayers() {
        String baseSpec =
            """
            {
              "version": 2,
              "datasets": [
                { "id": "ds1", "type": "SQL", "sql": "select province, students from t",
                  "fields": { "columns": [ { "name": "province" }, { "name": "students" } ] } }
              ],
              "widgets": [
                {
                  "id": "m1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "encode": {
                    "dimensions": [ { "field": "province", "role": "x" } ],
                    "measures": [ { "field": "students", "agg": "sum", "role": "value" } ]
                  },
                  "chart": { "type": "map", "map": "china", "layers": [ %s ] }
                }
              ]
            }
            """;

        assertThatThrownBy(() ->
            validator.validateAndNormalize(baseSpec.formatted(
                "{ \"kind\": \"effectScatter\", \"lngField\": \"lng\", \"latField\": \"lat\" }"
            ))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的字段");

        assertThatThrownBy(() ->
            validator.validateAndNormalize(baseSpec.formatted("{ \"kind\": \"region\", \"valueField\": \"missing\" }"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的字段");

        assertThatThrownBy(() ->
            validator.validateAndNormalize(baseSpec.formatted("{ \"kind\": \"region\" }"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少字段");
    }

    @Test
    void shouldRejectRegionLayerNotFirstAndUnknownDataset() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "SQL", "sql": "select province, students from t" } ],
              "widgets": [
                {
                  "id": "m1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "encode": {
                    "dimensions": [ { "field": "province", "role": "x" } ],
                    "measures": [ { "field": "students", "agg": "sum", "role": "value" } ]
                  },
                  "chart": {
                    "type": "map",
                    "layers": [
                      { "kind": "effectScatter", "lngField": "lng", "latField": "lat" },
                      { "kind": "region", "valueField": "students" }
                    ]
                  }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("region 图层只能有一个且必须位于第一位");

        String unknownDataset =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "SQL", "sql": "select province, students from t" } ],
              "widgets": [
                {
                  "id": "m1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "encode": {
                    "dimensions": [ { "field": "province", "role": "x" } ],
                    "measures": [ { "field": "students", "agg": "sum", "role": "value" } ]
                  },
                  "chart": {
                    "type": "map",
                    "layers": [
                      { "kind": "effectScatter", "datasetId": "ds_missing", "lngField": "lng", "latField": "lat" }
                    ]
                  }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(unknownDataset))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的数据集");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeChartStylePreset() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 8 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ]
                  },
                  "chart": { "type": "bar" },
                  "style": {
                    "chart": {
                      "preset": "tech",
                      "gradient": true,
                      "glow": "false",
                      "showAxis": false,
                      "showLegend": true,
                      "showLabel": true,
                      "unknown": "ignored"
                    }
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> chartStyle = (Map<String, Object>) ((Map<String, Object>) widgets.get(0).get("style")).get("chart");

        assertThat(chartStyle)
            .containsEntry("preset", "tech")
            .containsEntry("gradient", true)
            .containsEntry("glow", false)
            .containsEntry("showAxis", false)
            .containsEntry("showLegend", true)
            .containsEntry("showLabel", true)
            .doesNotContainKey("unknown");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDropInvalidChartStylePreset() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 8 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ]
                  },
                  "chart": { "type": "bar" },
                  "style": { "chart": { "preset": "neon", "gradient": false } }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> chartStyle = (Map<String, Object>) ((Map<String, Object>) widgets.get(0).get("style")).get("chart");

        assertThat(chartStyle).doesNotContainKey("preset").containsEntry("gradient", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeChartAxisAndLegendStyle() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 8 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ]
                  },
                  "chart": { "type": "bar" },
                  "style": {
                    "chart": {
                      "legendPosition": "left",
                      "legendFontSize": 14,
                      "axisTitleFontSize": 13,
                      "axisTitleColor": "#ff8800",
                      "labelFontSize": 15,
                      "barRadius": 8,
                      "xAxis": { "show": false, "title": "月份", "labelFontSize": 12, "tick": true, "lineColor": "#123456" },
                      "yAxis": { "title": "销售额", "grid": true, "gridStyle": "dotted" }
                    }
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> chartStyle = (Map<String, Object>) ((Map<String, Object>) widgets.get(0).get("style")).get("chart");

        assertThat(chartStyle)
            .containsEntry("legendPosition", "left")
            .containsEntry("legendFontSize", 14)
            .containsEntry("axisTitleFontSize", 13)
            .containsEntry("axisTitleColor", "#ff8800")
            .containsEntry("labelFontSize", 15)
            .containsEntry("barRadius", 8);
        assertThat((Map<String, Object>) chartStyle.get("xAxis"))
            .containsEntry("show", false)
            .containsEntry("title", "月份")
            .containsEntry("labelFontSize", 12)
            .containsEntry("tick", true)
            .containsEntry("lineColor", "#123456");
        assertThat((Map<String, Object>) chartStyle.get("yAxis"))
            .containsEntry("title", "销售额")
            .containsEntry("grid", true)
            .containsEntry("gridStyle", "dotted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDropInvalidChartAxisAndLegendStyle() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 8 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ]
                  },
                  "chart": { "type": "bar" },
                  "style": {
                    "chart": {
                      "legendPosition": "middle",
                      "axisTitleColor": "not-a-color",
                      "xAxis": { "title": "这是一个明显超过三十个字符长度的坐标轴标题用于验证长度限制被丢弃", "gridStyle": "wavy" }
                    }
                  }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> style = (Map<String, Object>) widgets.get(0).get("style");
        Map<String, Object> chartStyle = style == null ? null : (Map<String, Object>) style.get("chart");

        // 所有配置都非法时整段 style.chart 都会被丢弃。
        assertThat(chartStyle).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeWidgetInteraction() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [
                { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } },
                { "id": "ds2", "type": "METRIC", "metricQuery": { "metricCodes": ["m2"] } }
              ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 8 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ]
                  },
                  "chart": { "type": "bar" },
                  "interaction": {
                    "clickAction": "linkage",
                    "targets": [
                      { "widgetId": "w2" },
                      { "widgetId": "w3", "fieldMap": [ { "sourceField": "month", "targetField": "stat_month" } ] },
                      { "widgetId": "w1" },
                      { "widgetId": "missing" }
                    ]
                  }
                },
                {
                  "id": "w2",
                  "type": "echarts",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 8, "w": 12, "h": 8 },
                  "encode": {
                    "dimensions": [ { "field": "month", "role": "x" } ],
                    "measures": [ { "field": "m1", "agg": "sum", "role": "y" } ]
                  },
                  "chart": { "type": "line" },
                  "interaction": { "clickAction": "bogus" }
                },
                {
                  "id": "w3",
                  "type": "table",
                  "datasetId": "ds2",
                  "layout": { "x": 0, "y": 16, "w": 12, "h": 4 }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        Map<String, Object> first = widgets.get(0);
        Map<String, Object> interaction = (Map<String, Object>) first.get("interaction");
        assertThat(interaction).containsEntry("clickAction", "linkage");
        List<Map<String, Object>> targets = (List<Map<String, Object>>) interaction.get("targets");
        assertThat(targets).hasSize(2);
        assertThat(targets.get(0)).containsEntry("widgetId", "w2").doesNotContainKey("fieldMap");
        assertThat(targets.get(1)).containsEntry("widgetId", "w3");
        assertThat((List<Map<String, Object>>) targets.get(1).get("fieldMap"))
            .containsExactly(Map.of("sourceField", "month", "targetField", "stat_month"));

        Map<String, Object> second = widgets.get(1);
        assertThat(second).doesNotContainKey("interaction");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeTitleAndClockWidgets() {
        String spec =
            """
            {
              "version": 2,
              "layout": { "mode": "screen", "screen": { "width": 1920, "height": 1080 } },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "t1",
                  "type": "title",
                  "layout": { "x": 0, "y": 0, "w": 24, "h": 2 },
                  "props": {
                    "text": "领导驾驶舱",
                    "subText": "学生管理主题",
                    "align": "center",
                    "frame": "tech-frame"
                  }
                },
                {
                  "id": "c1",
                  "type": "clock",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 2, "w": 8, "h": 1 },
                  "props": { "format": "date-week" }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertThat(widgets.get(0)).doesNotContainKey("datasetId");
        assertThat((Map<String, Object>) widgets.get(0).get("props"))
            .containsEntry("text", "领导驾驶舱")
            .containsEntry("subText", "学生管理主题")
            .containsEntry("align", "center")
            .containsEntry("frame", "tech-frame");
        // 数据集组件才需要 datasetId；clock 即使误传也会被清掉。
        assertThat(widgets.get(1)).doesNotContainKey("datasetId");
        assertThat((Map<String, Object>) widgets.get(1).get("props")).containsEntry("format", "date-week");
    }

    @Test
    void shouldRejectTitleWithoutText() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "t1", "type": "title", "layout": { "x": 0, "y": 0, "w": 24, "h": 2 }, "props": {} }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("props.text");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackInvalidTitleAndClockProps() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "t1",
                  "type": "title",
                  "layout": { "x": 0, "y": 0, "w": 24, "h": 2 },
                  "props": { "text": "标题", "align": "justify", "frame": "neon" }
                },
                {
                  "id": "c1",
                  "type": "clock",
                  "layout": { "x": 0, "y": 2, "w": 8, "h": 1 },
                  "props": { "format": "lunar" }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");
        assertThat((Map<String, Object>) widgets.get(0).get("props"))
            .containsEntry("align", "center")
            .containsEntry("frame", "none");
        assertThat((Map<String, Object>) widgets.get(1).get("props")).containsEntry("format", "date-week");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeContainerWithChildren() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "panel1",
                  "type": "container",
                  "title": "招生数据",
                  "datasetId": "ds1",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 12 }
                },
                {
                  "id": "w1",
                  "type": "kpi",
                  "parentId": "panel1",
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "layout": { "x": 1, "y": 2, "w": 4, "h": 3 }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertThat(widgets.get(0)).doesNotContainKey("datasetId").containsEntry("type", "container");
        assertThat(widgets.get(1)).containsEntry("parentId", "panel1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeChildLayoutToTwentyFourGrid() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "panel1", "type": "container", "layout": { "x": 0, "y": 0, "w": 12, "h": 12 } },
                {
                  "id": "w1",
                  "type": "kpi",
                  "parentId": "panel1",
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "layout": { "x": 20, "y": 2, "w": 10, "h": 4 }
                },
                {
                  "id": "w2",
                  "type": "kpi",
                  "parentId": "panel1",
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "layout": { "x": 120, "y": 60, "w": 300, "h": 200 }
                }
              ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertThat(asMap(widgets.get(1).get("layout"))).containsEntry("x", 20).containsEntry("w", 4);
        // 旧版绝对坐标回落到默认 12 × 6。
        assertThat(asMap(widgets.get(2).get("layout")))
            .containsEntry("x", 0)
            .containsEntry("y", 0)
            .containsEntry("w", 12)
            .containsEntry("h", 6);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistConfiguredRowHeight() {
        String spec =
            """
            {
              "version": 2,
              "layout": { "mode": "standard", "rowHeight": 20 },
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [ { "id": "w1", "type": "table", "datasetId": "ds1", "layout": { "x": 0, "y": 0, "w": 12, "h": 4 } } ]
            }
            """;
        Map<String, Object> normalized = validator.validateAndNormalize(spec);
        assertThat((Map<String, Object>) normalized.get("layout")).containsEntry("rowHeight", 20);
    }

    @Test
    void shouldRejectInvalidContainerParents() {
        String base =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                {
                  "id": "panel1",
                  "type": "container",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 12 }
                },
                {
                  "id": "w1",
                  "type": "kpi",
                  "datasetId": "ds1",
                  "encode": { "measures": [ { "field": "m1", "agg": "sum", "role": "value" } ] },
                  "layout": { "x": 1, "y": 2, "w": 4, "h": 3 },
                  "parentId": "%s"
                }
              ]
            }
            """;

        assertThatThrownBy(() -> validator.validateAndNormalize(base.formatted("w1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能把自己设为容器");

        assertThatThrownBy(() -> validator.validateAndNormalize(base.formatted("missing")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在的容器");
    }

    @Test
    void shouldRejectNestedContainers() {
        String spec =
            """
            {
              "version": 2,
              "datasets": [ { "id": "ds1", "type": "METRIC", "metricQuery": { "metricCodes": ["m1"] } } ],
              "widgets": [
                { "id": "panel1", "type": "container", "layout": { "x": 0, "y": 0, "w": 12, "h": 12 } },
                {
                  "id": "panel2",
                  "type": "container",
                  "parentId": "panel1",
                  "layout": { "x": 1, "y": 1, "w": 6, "h": 6 }
                }
              ]
            }
            """;
        assertThatThrownBy(() -> validator.validateAndNormalize(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能再嵌套容器");
    }

    @Test
    void guardShouldBlockWriteInsideCte() {
        assertThatThrownBy(() ->
            DashboardSqlGuard.requireReadOnly("WITH x AS (DELETE FROM t RETURNING *) SELECT * FROM x")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("写操作");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
