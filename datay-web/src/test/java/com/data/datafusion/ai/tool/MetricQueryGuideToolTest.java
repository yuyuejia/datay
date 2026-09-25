package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link MetricQueryGuideTool} 的单元测试，校验工具契约与引导内容。
 */
class MetricQueryGuideToolTest {

    private final MetricQueryGuideTool tool = new MetricQueryGuideTool();

    @Test
    void shouldExposeReadOnlyContractWithEmptySchema() {
        assertThat(tool.name()).isEqualTo(MetricQueryGuideTool.NAME).isEqualTo("metric_query_guide");
        assertThat(tool.description()).contains("工作流");
        assertThat(tool.mutating()).isFalse();

        Map<String, Object> schema = tool.parametersSchema();
        assertThat(schema).containsEntry("type", "object");
        assertThat((Map<?, ?>) schema.get("properties")).isEmpty();
    }

    @Test
    void shouldReturnWorkflowConstraintsAndToday() {
        Object result = tool.execute(Map.of(), new AiToolContext(null, null, null, null));

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) result;
        assertThat(map.get("today")).isEqualTo(LocalDate.now().toString());
        assertThat((List<?>) map.get("workflow")).isNotEmpty();
        assertThat((List<?>) map.get("constraints")).isNotEmpty();
    }
}
