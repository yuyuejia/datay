package com.data.datafusion.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.ai.tool.ValidateJavaScriptTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaScriptAssistantTest {

    private final JavaScriptAssistant assistant = new JavaScriptAssistant();

    @Test
    void exposesExpectedMetadata() {
        assertThat(assistant.id()).isEqualTo("javascript");
        assertThat(assistant.toolNames()).containsExactly(ValidateJavaScriptTool.NAME);
        assertThat(assistant.samplePrompts()).isNotEmpty();
    }

    @Test
    void systemPromptCarriesContractSpecAndExamples() {
        String prompt = assistant.buildSystemPrompt(new AiAssistantContext(null, "处理 JSON 数组"));

        assertThat(prompt).contains("public class UserScript");
        assertThat(prompt).contains("public FlowFile process(FlowFile flowFile, Map<String, Object> context)");
        assertThat(prompt).contains("【参考示例】");
        assertThat(prompt).contains("validate_javascript");
        assertThat(prompt).contains("upsertColumnMeta");
    }

    @Test
    void systemPromptIncludesUpstreamDebugData() {
        Map<String, Object> contextData = Map.of(
            "upstream",
            List.of(
                Map.of(
                    "node",
                    "SqlInput-1",
                    "columns",
                    List.of(Map.of("name", "id", "type", "INTEGER"), Map.of("name", "amount", "type", "DECIMAL(10,2)")),
                    "rows",
                    List.of(List.of(1, 100.5))
                )
            )
        );

        String prompt = assistant.buildSystemPrompt(new AiAssistantContext(null, "处理数据", contextData));

        assertThat(prompt).contains("【上游输入调试数据】");
        assertThat(prompt).contains("SqlInput-1");
        assertThat(prompt).contains("amount");
        assertThat(prompt).contains("DECIMAL(10,2)");
    }

    @Test
    void systemPromptOmitsUpstreamSectionWhenAbsent() {
        String prompt = assistant.buildSystemPrompt(new AiAssistantContext(null, "处理数据"));

        assertThat(prompt).doesNotContain("【上游输入调试数据】");
    }

    @Test
    void extractsJavaScriptInsteadOfSql() {
        String content = """
            已生成脚本：

            ```java
            import com.data.job.FlowFile;
            import java.util.Map;

            public class UserScript {
                public FlowFile process(FlowFile flowFile, Map<String, Object> context) {
                    return flowFile;
                }
            }
            ```
            """;

        String code = assistant.extractSql(content);

        assertThat(code).contains("public class UserScript");
        assertThat(code).doesNotContain("```");
    }

    @Test
    void returnsNullForSqlOnlyReply() {
        assertThat(assistant.extractSql("```sql\nSELECT 1\n```")).isNull();
    }
}
