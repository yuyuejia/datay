package com.data.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link ComponentMetadataExtractor} 组件字段元数据提取测试。
 */
class ComponentMetadataExtractorTest {

    @Test
    void shouldExtractFieldsFromClassHierarchy() {
        List<ComponentFieldDescriptor> fields = ComponentMetadataExtractor.extract(SampleComponent.class);

        assertEquals(4, fields.size());

        ComponentFieldDescriptor source = find(fields, "sourceId").orElseThrow();
        assertEquals("数据源", source.getLabel());
        assertEquals("DATASOURCE", source.getType());
        assertEquals("基础配置", source.getGroup());
        assertTrue(source.isRequired());
        assertEquals("JDBC", source.getDataSourceType());
        assertEquals("schema", source.getSchemaField());

        ComponentFieldDescriptor table = find(fields, "table").orElseThrow();
        assertEquals("TABLE", table.getType());
        assertEquals(20, table.getOrder());
        assertTrue(table.isMultiple());
        assertEquals("COMMA_STRING", table.getValueFormat());

        ComponentFieldDescriptor matchMode = find(fields, "matchMode").orElseThrow();
        assertEquals("RADIO", matchMode.getType());
        assertEquals(2, matchMode.getOptions().size());
        assertEquals("a", matchMode.getOptions().get(0).getValue());
        assertEquals("选项A", matchMode.getOptions().get(0).getLabel());
        assertEquals(List.of("a"), matchMode.getVisibleWhen());

        ComponentFieldDescriptor childKey = find(fields, "childKey").orElseThrow();
        assertEquals("子类字段", childKey.getLabel());
        assertEquals("^[a-z]+$", childKey.getPattern());
    }

    @Test
    void shouldExposeFieldsForRegisteredComponents() {
        assertFalse(ComponentFactory.getFields("DorisStreamLoad").isEmpty());
        assertTrue(find(ComponentFactory.getFields("DorisStreamLoad"), "sourceId").isPresent());
        assertTrue(find(ComponentFactory.getFields("DorisStreamLoad"), "table").isPresent());

        assertTrue(find(ComponentFactory.getFields("KafkaConsumer"), "bootstrapServers").isPresent());
        assertTrue(find(ComponentFactory.getFields("RouteOnAttribute"), "matchMode").isPresent());

        // 未声明字段注解的组件保持空列表，前端自动回退到原有 Vue 表单
        assertTrue(ComponentFactory.getFields("GenerateFlowFile").isEmpty());
    }

    private Optional<ComponentFieldDescriptor> find(List<ComponentFieldDescriptor> fields, String key) {
        return fields.stream().filter(field -> key.equals(field.getKey())).findFirst();
    }

    private static class ParentComponent {

        @ComponentField(
            key = "sourceId",
            label = "数据源",
            type = ComponentFieldType.DATASOURCE,
            required = true,
            dataSourceType = "JDBC",
            schemaField = "schema",
            order = 10
        )
        private String datasource;

        @ComponentField(
            label = "表名",
            type = ComponentFieldType.TABLE,
            multiple = true,
            valueFormat = ComponentFieldValueFormat.COMMA_STRING,
            order = 20
        )
        private String table;
    }

    private static class SampleComponent extends ParentComponent {

        @ComponentField(
            label = "匹配模式",
            type = ComponentFieldType.RADIO,
            options = {
                @ComponentFieldOption(value = "a", label = "选项A"),
                @ComponentFieldOption(value = "b", label = "选项B")
            },
            visibleWhen = { "a" },
            dependsOn = "other",
            order = 30
        )
        private String matchMode;

        @ComponentField(
            label = "子类字段",
            type = ComponentFieldType.INPUT,
            pattern = "^[a-z]+$",
            patternMessage = "只能是小写字母",
            order = 40
        )
        private String childKey;

        @ComponentField(label = "静态字段")
        private static String ignoredStatic;

        @ComponentField(label = "临时字段")
        private transient String ignoredTransient;
    }
}
