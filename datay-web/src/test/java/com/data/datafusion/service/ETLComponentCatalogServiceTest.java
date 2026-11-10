package com.data.datafusion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datafusion.service.dto.ETLComponentDTO;
import com.data.datafusion.service.etl.ETLNodeTranslationContext;
import com.data.datafusion.service.etl.ETLNodeTranslator;
import com.data.job.ComponentDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ETLComponentCatalogService} 组件字段元数据测试。
 */
class ETLComponentCatalogServiceTest {

    @Test
    void shouldExposeFieldsForAnnotatedComponents() {
        ETLComponentCatalogService service = new ETLComponentCatalogService(List.of());

        ETLComponentDTO doris = find(service.listCatalog(), "DorisStreamLoad");
        assertNotNull(doris.getFields());
        assertFalse(doris.getFields().isEmpty());
        assertTrue(doris.getFields().stream().anyMatch(field -> "sourceId".equals(field.getKey())));
        assertTrue(doris.getFields().stream().anyMatch(field -> "TABLE".equals(field.getType())));

        ETLComponentDTO generate = find(service.listCatalog(), "GenerateFlowFile");
        assertTrue(generate.getFields() == null || generate.getFields().isEmpty());
    }

    @Test
    void shouldKeepFieldsWhenTranslatorOverridesDescriptor() {
        ETLNodeTranslator translator = new ETLNodeTranslator() {
            @Override
            public String supportedType() {
                return "DorisStreamLoad";
            }

            @Override
            public void translate(ETLNodeTranslationContext context) {
                // 测试字段合并，不执行实际翻译
            }

            @Override
            public ComponentDescriptor descriptor() {
                return new ComponentDescriptor("DorisStreamLoad", "设计器侧Doris写入", "数据输出", "覆盖描述", "dummy", 1);
            }
        };

        ETLComponentCatalogService service = new ETLComponentCatalogService(List.of(translator));
        ETLComponentDTO doris = find(service.listCatalog(), "DorisStreamLoad");

        assertEquals("设计器侧Doris写入", doris.getName());
        assertNotNull(doris.getFields());
        assertFalse(doris.getFields().isEmpty());
    }

    private ETLComponentDTO find(List<ETLComponentDTO> catalog, String code) {
        return catalog.stream().filter(component -> code.equals(component.getCode())).findFirst().orElseThrow();
    }
}
