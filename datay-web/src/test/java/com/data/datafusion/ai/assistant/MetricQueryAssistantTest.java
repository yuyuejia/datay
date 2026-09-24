package com.data.datafusion.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.ai.tool.MetricCatalogTool;
import com.data.datafusion.ai.tool.MetricDimensionValueTool;
import com.data.datafusion.ai.tool.MetricMetaTool;
import com.data.datafusion.ai.tool.MetricQueryDataTool;
import com.data.datafusion.ai.tool.MetricRagSearchTool;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * {@link MetricQueryAssistant} 的单元测试。
 */
class MetricQueryAssistantTest {

    private final MetricQueryAssistant assistant = new MetricQueryAssistant();

    @Test
    void shouldExposeMetricQueryContract() {
        assertThat(assistant.id()).isEqualTo(MetricQueryAssistant.ID);
        assertThat(assistant.toolNames())
            .containsExactlyInAnyOrder(
                MetricRagSearchTool.NAME,
                MetricCatalogTool.NAME,
                MetricMetaTool.NAME,
                MetricDimensionValueTool.NAME,
                MetricQueryDataTool.NAME
            );
        assertThat(assistant.samplePrompts()).isNotEmpty();
        assertThat(assistant.allowWrites()).isFalse();
    }

    @Test
    void systemPromptShouldContainDateAndFourElements() {
        String prompt = assistant.buildSystemPrompt(new AiAssistantContext(null, "最近一周北京的销售额"));
        assertThat(prompt).contains(LocalDate.now().toString());
        assertThat(prompt).contains("指标");
        assertThat(prompt).contains("维度");
        assertThat(prompt).contains("业务限定");
        assertThat(prompt).contains("时间范围");
        assertThat(prompt).contains(MetricRagSearchTool.NAME);
        assertThat(prompt).contains(MetricCatalogTool.NAME);
        assertThat(prompt).contains(MetricQueryDataTool.NAME);
    }
}
