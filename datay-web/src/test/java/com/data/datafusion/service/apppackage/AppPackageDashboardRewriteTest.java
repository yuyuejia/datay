package com.data.datafusion.service.apppackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.service.dto.AppPackageInitResultDTO;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 资产包导入时 v1 看板 refId 重写回归。
 */
class AppPackageDashboardRewriteTest {

    @Test
    void shouldRewriteRefIdAndKeepV1EncodeAndTargets() throws Exception {
        AppPackageImportService service = new AppPackageImportService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode spec = objectMapper.readTree(
            """
            {
              "version": 1,
              "datasets": [ { "id": "ds1", "refId": "9600", "type": "SQL" } ],
              "widgets": [
                {
                  "id": "w1",
                  "type": "table",
                  "layout": { "x": 0, "y": 0, "w": 12, "h": 8 },
                  "datasetId": "ds1",
                  "encode": { "limit": 100, "pageSize": 20 }
                }
              ],
              "filters": [
                {
                  "id": "store",
                  "type": "select",
                  "targets": [ { "datasetId": "ds1", "field": "store_name", "mode": "server" } ]
                }
              ]
            }
            """
        );
        Map<String, String> datasetMap = new LinkedHashMap<>();
        datasetMap.put("9600", "new-dataset-id");
        Map<String, String> dataSourceMap = new LinkedHashMap<>();
        dataSourceMap.put("9001", "new-source-id");
        AppPackageInitResultDTO result = new AppPackageInitResultDTO();

        JsonNode rewritten = service.rewriteDashboardSpec(
            spec,
            datasetMap,
            dataSourceMap,
            Map.of(),
            Map.of(),
            new AppPackageContent(),
            "dashboard-1",
            result
        );

        assertThat(rewritten).isNotNull();
        assertThat(rewritten.path("version").asInt()).isEqualTo(1);
        assertThat(rewritten.path("datasets").get(0).path("refId").asText()).isEqualTo("new-dataset-id");
        assertThat(rewritten.path("widgets").get(0).path("encode").path("pageSize").asInt()).isEqualTo(20);
        assertThat(rewritten.path("filters").get(0).path("targets").get(0).path("field").asText()).isEqualTo("store_name");
        assertThat(rewritten.toString()).doesNotContain("dataRef").doesNotContain("bindings");
        assertThat(result.warnings).isEmpty();
    }
}
