package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.dto.DataModelDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ListDataModelsTool} 数据模型清单工具测试。
 */
class ListDataModelsToolTest {

    private final DataModelService dataModelService = mock(DataModelService.class);
    private final ListDataModelsTool tool = new ListDataModelsTool(dataModelService);

    @Test
    @SuppressWarnings("unchecked")
    void shouldListModelsAndFilterByKeyword() {
        when(dataModelService.findAll()).thenReturn(List.of(model("1", "订单模型", "order_model"), model("2", "用户模型", "user_model")));

        Map<String, Object> all = (Map<String, Object>) tool.execute(Map.of(), null);
        assertThat(all).containsEntry("total", 2);

        Map<String, Object> filtered = (Map<String, Object>) tool.execute(Map.of("keyword", "订单"), null);
        assertThat(filtered).containsEntry("total", 1);
        List<Map<String, Object>> models = (List<Map<String, Object>>) filtered.get("models");
        assertThat(models.get(0)).containsEntry("id", "1").containsEntry("tableName", "dw_order");
    }

    private static DataModelDTO model(String id, String name, String code) {
        DataModelDTO dto = new DataModelDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setCode(code);
        dto.setDataSourceId("ds1");
        dto.setSchemaName("dw");
        dto.setTableName("dw_order");
        dto.setModelType("FACT");
        return dto;
    }
}
