package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.assistant.EtlComponentDoc;
import com.data.datafusion.ai.assistant.EtlComponentDocRegistry;
import com.data.datafusion.ai.assistant.MarkdownDocParser;
import com.data.datafusion.service.ETLComponentCatalogService;
import com.data.datafusion.service.dto.ETLComponentDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link DescribeEtlComponentsTool} 组件详细说明工具测试。
 */
class DescribeEtlComponentsToolTest {

    private final ETLComponentCatalogService catalogService = mock(ETLComponentCatalogService.class);
    private final EtlComponentDocRegistry docRegistry = mock(EtlComponentDocRegistry.class);
    private final DescribeEtlComponentsTool tool = new DescribeEtlComponentsTool(catalogService, docRegistry);

    @Test
    @SuppressWarnings("unchecked")
    void shouldDescribeMultipleComponentsAndKeepUnknownTypes() {
        when(catalogService.listCatalog()).thenReturn(
            List.of(
                component("StreamJdbcInput", "数据源输入", "数据输入"),
                component("ModelWrite", "模型写入", "数据输出")
            )
        );
        EtlComponentDoc doc = new EtlComponentDoc(
            "StreamJdbcInput",
            "StreamJdbcInput 组件配置文档",
            "从关系型数据库流式读取数据。",
            List.of("支持多表同步"),
            List.of(
                new EtlComponentDoc.ConfigTable(
                    "配置参数",
                    List.of("参数名", "类型", "必填", "描述"),
                    List.of(Map.of("参数名", "table", "类型", "String", "必填", "是", "描述", "源表名"))
                )
            ),
            List.of(),
            List.of("在 DataY Web 中配置时使用 sourceId + schema。"),
            List.of(new EtlComponentDoc.CodeExample("json", "{\"table\":\"orders\"}")),
            List.of("字符串需要加引号。"),
            "doc excerpt",
            false,
            "# doc"
        );
        when(docRegistry.find("StreamJdbcInput")).thenReturn(Optional.of(doc));
        when(docRegistry.find("ModelWrite")).thenReturn(Optional.empty());

        Map<String, Object> result = (Map<String, Object>) tool.execute(
            Map.of("types", List.of("StreamJdbcInput", "ModelWrite", "NotExists")),
            null
        );

        List<Map<String, Object>> components = (List<Map<String, Object>>) result.get("components");
        assertThat(components).hasSize(2);
        assertThat(components.get(0)).containsEntry("type", "StreamJdbcInput").containsEntry("docAvailable", true);
        assertThat(components.get(0)).containsKey("configTables");
        assertThat(components.get(1)).containsEntry("type", "ModelWrite").containsEntry("docAvailable", false);
        assertThat(components.get(1)).containsEntry("skeletonOnly", true);
        List<Map<String, Object>> unknown = (List<Map<String, Object>>) result.get("unknown");
        assertThat(unknown).hasSize(1);
        assertThat(unknown.get(0)).containsEntry("type", "NotExists");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkEngineAndDesignerScopes() {
        when(catalogService.listCatalog()).thenReturn(List.of(component("StreamJdbcInput", "数据源输入", "数据输入")));
        EtlComponentDoc doc = new EtlComponentDoc(
            "StreamJdbcInput",
            "StreamJdbcInput",
            "overview",
            List.of(),
            List.of(
                new EtlComponentDoc.ConfigTable(
                    "配置参数",
                    List.of("参数名", "类型", "必填", "描述"),
                    List.of(
                        Map.of("参数名", "datasource", "类型", "Object", "必填", "是", "描述", "数据源连接"),
                        Map.of("参数名", "table", "类型", "String", "必填", "是", "描述", "表名")
                    )
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "",
            false,
            ""
        );
        when(docRegistry.find("StreamJdbcInput")).thenReturn(Optional.of(doc));

        Map<String, Object> result = (Map<String, Object>) tool.execute(
            Map.of("types", List.of("StreamJdbcInput")),
            null
        );

        List<Map<String, Object>> components = (List<Map<String, Object>>) result.get("components");
        List<Map<String, Object>> tables = (List<Map<String, Object>>) components.get(0).get("configTables");
        List<Map<String, String>> rows = (List<Map<String, String>>) tables.get(0).get("rows");
        assertThat(rows).anyMatch(row -> "datasource".equals(row.get("参数名")) && "engine".equals(row.get("scope")));
        assertThat(rows).anyMatch(row -> "table".equals(row.get("参数名")) && "designer".equals(row.get("scope")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReadRealHelpDocFromClasspath() {
        EtlComponentDocRegistry realRegistry = new EtlComponentDocRegistry(new MarkdownDocParser(new ObjectMapper()));
        realRegistry.load();
        DescribeEtlComponentsTool realTool = new DescribeEtlComponentsTool(catalogService, realRegistry);
        when(catalogService.listCatalog()).thenReturn(List.of(component("Join", "多表关联", "数据处理")));

        Map<String, Object> result = (Map<String, Object>) realTool.execute(
            Map.of("types", List.of("Join")),
            null
        );

        List<Map<String, Object>> components = (List<Map<String, Object>>) result.get("components");
        assertThat(components).hasSize(1);
        assertThat(components.get(0)).containsEntry("docAvailable", true);
        assertThat((List<?>) components.get(0).get("configTables")).isNotEmpty();
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
