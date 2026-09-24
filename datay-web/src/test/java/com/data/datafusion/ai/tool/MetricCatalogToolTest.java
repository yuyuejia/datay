package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Metric;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.dto.MetricDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MetricCatalogTool} 的单元测试。
 */
class MetricCatalogToolTest {

    private MetricService metricService;
    private MetricCatalogTool tool;

    @BeforeEach
    void setUp() {
        metricService = mock(MetricService.class);
        tool = new MetricCatalogTool(metricService);
        when(metricService.findAllSimple()).thenReturn(List.of(metric("销售金额", "sales_amount"), metric("订单数", "order_count")));
    }

    @Test
    void shouldReturnAllEnabledMetrics() throws Exception {
        Object result = tool.execute(Map.of(), new AiToolContext(null, null, "有哪些指标", null));
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) result;
        assertThat(view).containsEntry("total", 2);
        assertThat((List<?>) view.get("metrics")).hasSize(2);
    }

    @Test
    void shouldFilterByKeywordAcrossName() throws Exception {
        Object result = tool.execute(Map.of("keyword", "销售"), new AiToolContext(null, null, "销售额", null));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) ((Map<String, Object>) result).get("metrics");
        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0)).containsEntry("code", "sales_amount");
    }

    private MetricDTO metric(String name, String code) {
        MetricDTO dto = new MetricDTO();
        dto.setName(name);
        dto.setCode(code);
        dto.setStatus(Metric.STATUS_ENABLED);
        dto.setMetricType(Metric.TYPE_ATOMIC);
        dto.setFactTableName("main.fact_sales_order_item");
        return dto;
    }
}
