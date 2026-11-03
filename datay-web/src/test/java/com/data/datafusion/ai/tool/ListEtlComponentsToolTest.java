package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.assistant.EtlComponentDocRegistry;
import com.data.datafusion.service.ETLComponentCatalogService;
import com.data.datafusion.service.dto.ETLComponentDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ListEtlComponentsTool} 组件目录工具测试。
 */
class ListEtlComponentsToolTest {

    private final ETLComponentCatalogService catalogService = mock(ETLComponentCatalogService.class);
    private final EtlComponentDocRegistry docRegistry = mock(EtlComponentDocRegistry.class);
    private final ListEtlComponentsTool tool = new ListEtlComponentsTool(catalogService, docRegistry);

    @Test
    @SuppressWarnings("unchecked")
    void shouldListAllComponentsAndMarkDocAvailability() {
        when(catalogService.listCatalog()).thenReturn(
            List.of(component("StreamJdbcInput", "数据源输入", "数据输入"), component("ModelWrite", "模型写入", "数据输出"))
        );
        when(docRegistry.contains("StreamJdbcInput")).thenReturn(true);
        when(docRegistry.contains("ModelWrite")).thenReturn(false);

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of(), null);

        assertThat(result).containsEntry("total", 2);
        List<Map<String, Object>> components = (List<Map<String, Object>>) result.get("components");
        assertThat(components).extracting(item -> item.get("code")).containsExactlyInAnyOrder("StreamJdbcInput", "ModelWrite");
        assertThat(components).anyMatch(item -> "StreamJdbcInput".equals(item.get("code")) && Boolean.TRUE.equals(item.get("docAvailable")));
        assertThat(components).anyMatch(item -> "ModelWrite".equals(item.get("code")) && Boolean.FALSE.equals(item.get("docAvailable")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFilterByKeywordGroupAndDocAvailability() {
        when(catalogService.listCatalog()).thenReturn(
            List.of(component("StreamJdbcInput", "数据源输入", "数据输入"), component("ModelWrite", "模型写入", "数据输出"))
        );
        when(docRegistry.contains("StreamJdbcInput")).thenReturn(true);
        when(docRegistry.contains("ModelWrite")).thenReturn(false);

        Map<String, Object> onlyDoc = (Map<String, Object>) tool.execute(Map.of("onlyDocAvailable", true), null);
        assertThat(onlyDoc).containsEntry("total", 1);

        Map<String, Object> byKeyword = (Map<String, Object>) tool.execute(Map.of("keyword", "模型"), null);
        assertThat(byKeyword).containsEntry("total", 1);
        List<Map<String, Object>> components = (List<Map<String, Object>>) byKeyword.get("components");
        assertThat(components.get(0)).containsEntry("code", "ModelWrite");

        Map<String, Object> byGroup = (Map<String, Object>) tool.execute(Map.of("group", "数据输入"), null);
        assertThat(byGroup).containsEntry("total", 1);
    }

    private static ETLComponentDTO component(String code, String name, String group) {
        ETLComponentDTO dto = new ETLComponentDTO();
        dto.setCode(code);
        dto.setName(name);
        dto.setGroup(group);
        dto.setDesc(name);
        return dto;
    }
}
