package com.data.datafusion.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.tool.DescribeEtlComponentsTool;
import com.data.datafusion.ai.tool.ListEtlComponentsTool;
import com.data.datafusion.service.etl.EtlTaskSpecValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link EtlTaskDesignAssistant} 基础行为测试。
 */
class EtlTaskDesignAssistantTest {

    private final EtlTaskSpecValidator validator = mock(EtlTaskSpecValidator.class);
    private final EtlTaskDesignAssistant assistant = new EtlTaskDesignAssistant(new ObjectMapper(), validator);

    @Test
    void shouldExposeEtlAssistantIdentityAndTools() {
        assertThat(assistant.id()).isEqualTo(EtlTaskDesignAssistant.ID);
        assertThat(assistant.specType()).isEqualTo(AiAssistant.SpecType.ETL_TASK);
        assertThat(assistant.toolNames()).contains(ListEtlComponentsTool.NAME, DescribeEtlComponentsTool.NAME);
        assertThat(assistant.samplePrompts()).isNotEmpty();
    }

    @Test
    void shouldExtractTaskSpecFromJsonFence() {
        String content =
            """
            这是任务定义：
            ```json
            {
              "taskName": "订单同步",
              "nodes": [
                { "key": "n1", "type": "StreamJdbcInput", "config": { "table": "orders" } }
              ],
              "edges": []
            }
            ```
            """;

        Map<String, Object> spec = assistant.extractSpec(content);

        assertThat(spec).isNotNull();
        assertThat(spec).containsEntry("taskName", "订单同步");
        assertThat(spec).containsKey("nodes");
    }

    @Test
    void shouldDelegatePostProcessToValidator() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("nodes", java.util.List.of());
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("nodes", java.util.List.of());
        Map<String, Object> artifacts = new LinkedHashMap<>();
        when(validator.validateAndNormalize(raw, artifacts)).thenReturn(normalized);

        assertThat(assistant.postProcessSpec(raw, artifacts)).isSameAs(normalized);
    }

    @Test
    void shouldRequireComponentCatalogAndDocsInPrompt() {
        String prompt = assistant.buildSystemPrompt(new AiAssistantContext(null, "同步订单"));

        assertThat(prompt).contains("list_etl_components");
        assertThat(prompt).contains("describe_etl_components");
        assertThat(prompt).contains("sourceId");
        assertThat(prompt).contains("不要凭记忆");
    }
}
