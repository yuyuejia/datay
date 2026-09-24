package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.rag.MetricRagProperties;
import com.data.datafusion.ai.rag.MetricRagService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MetricRagSearchTool} 的单元测试。
 */
class MetricRagSearchToolTest {

    private MetricRagService metricRagService;
    private MetricRagSearchTool tool;

    @BeforeEach
    void setUp() {
        metricRagService = mock(MetricRagService.class);
        MetricRagProperties properties = new MetricRagProperties();
        properties.setTopK(5);
        tool = new MetricRagSearchTool(metricRagService, properties);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", true);
        response.put("provider", "local");
        response.put("query", "销售额");
        response.put("metrics", java.util.List.of(Map.of("code", "sales_amount")));
        response.put("dimensionFields", java.util.List.of());
        response.put("members", java.util.List.of());
        when(metricRagService.search(eq("销售额"), eq(5), any())).thenReturn(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAllScopesByDefault() {
        Object result = tool.execute(Map.of("query", "销售额"), new AiToolContext(null, null, "销售额", null));
        Map<String, Object> view = (Map<String, Object>) result;
        assertThat(view).containsKeys("metrics", "dimensionFields", "members");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFilterByScope() {
        Object result = tool.execute(
            Map.of("query", "销售额", "scope", "metric"),
            new AiToolContext(null, null, "销售额", null)
        );
        Map<String, Object> view = (Map<String, Object>) result;
        assertThat(view).containsKey("metrics");
        assertThat(view).doesNotContainKey("members");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportErrorWhenQueryMissing() {
        Object result = tool.execute(Map.of(), new AiToolContext(null, null, "", null));
        assertThat(((Map<String, Object>) result).get("error")).isNotNull();
    }
}
