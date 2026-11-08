package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONObject;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataQualityRuleBindingDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataQualityTaskServiceTest {

    @Test
    void shouldBuildEtlJobJsonWithInputAndQualityCheck() {
        DataQualityTaskService service = new DataQualityTaskService(
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

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setType("MYSQL");
        dataSource.setUrl("jdbc:mysql://localhost:3306/shop");
        dataSource.setUsername("u");
        dataSource.setPassword("p");

        DataQualityRuleBindingDTO rule = new DataQualityRuleBindingDTO();
        rule.setRuleCode("DQ_FIELD_NOT_NULL");
        rule.setRuleName("订单号非空");
        rule.setRuleLevel("FIELD");
        rule.setRuleType("NOT_NULL");
        rule.setColumn("order_id");
        rule.setSeverity("ERROR");
        rule.setMaxInvalidRows(0L);
        rule.setMaxInvalidRatio(0.0D);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("trimWhitespace", true);
        rule.setConfig(config);

        FileStorageConfig storage = new FileStorageConfig();
        storage.type = "local";
        storage.localBasePath = "./data/files";

        JSONObject job = service.buildJobJson(
            dataSource,
            "shop",
            "orders",
            "id > 0",
            List.of(rule),
            "data-quality/task1",
            "CSV",
            100,
            false,
            storage,
            "tenants/default"
        );

        assertThat(job.getJSONArray("units")).hasSize(2);
        assertThat(job.getJSONArray("connections")).hasSize(1);
        String text = job.toString();
        assertThat(text).contains("StreamJdbcInput", "DataQualityCheck", "data-quality/task1");
        assertThat(text).contains("\"checkMode\":\"FULL\"");
        assertThat(text).contains("jdbc:mysql://localhost:3306/shop");
        assertThat(text).contains("NOT_NULL", "order_id", "订单号非空");
        assertThat(text).contains("tenants/default");
    }
}
