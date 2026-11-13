package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.DataSyncDTO;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import com.data.datafusion.service.mapper.DataSyncMapper;
import com.data.datafusion.service.mapper.DataSyncTableConfigMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataSyncServiceTest {

    @Test
    void generateETLJobJsonKeepsRenamedTargetTableMapping() {
        DataSourceService dataSourceService = mock(DataSourceService.class);
        when(dataSourceService.findOne("src"))
            .thenReturn(Optional.of(datasource("src", "jdbc:mysql://localhost:3306/src", "src_schema")));
        when(dataSourceService.findOne("tgt"))
            .thenReturn(Optional.of(datasource("tgt", "jdbc:mysql://localhost:3306/tgt", "tgt_schema")));

        DataSyncService service = new DataSyncService(
            mock(DataSyncRepository.class),
            dataSourceService,
            mock(DataSyncTableConfigMapper.class),
            mock(DataSyncMapper.class),
            mock(DataSyncTableConfigRepository.class),
            mock(JobService.class)
        );

        DataSyncDTO dataSyncDTO = new DataSyncDTO();
        dataSyncDTO.setSource("src");
        dataSyncDTO.setTarget("tgt");
        dataSyncDTO.setType("FULL_SYNC");
        dataSyncDTO.setSelectedTables(
            List.of(
                table("orders", "orders_new", "src_schema", "tgt_schema"),
                table("users", "users", "src_schema", "tgt_schema")
            )
        );

        JSONObject jobJson = JSONUtil.parseObj(service.generateETLJobJson(dataSyncDTO));
        JSONArray units = jobJson.getJSONArray("units");
        JSONObject output = units.getJSONObject(1);

        // 目标表重命名需要随任务下发给 StreamJdbcOutput，否则数据仍会写入源表名
        assertThat(output.getStr(".name")).isEqualTo("StreamJdbcOutput");
        assertThat(output.getStr("table")).isEmpty();
        java.util.Map<String, Object> tableMapping = output.getJSONObject("tableMapping");
        assertThat(tableMapping).containsEntry("orders", "orders_new").doesNotContainKey("users");
    }

    private static DataSourceDTO datasource(String id, String url, String schemaName) {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setId(id);
        dto.setType("mysql");
        dto.setUrl(url);
        dto.setSchemaName(schemaName);
        return dto;
    }

    private static DataSyncTableConfigDTO table(String srcTable, String desTable, String srcSchema, String desSchema) {
        DataSyncTableConfigDTO dto = new DataSyncTableConfigDTO();
        dto.setSrcTableName(srcTable);
        dto.setDesTableName(desTable);
        dto.setSrcSchemaName(srcSchema);
        dto.setDesSchemaName(desSchema);
        return dto;
    }
}
