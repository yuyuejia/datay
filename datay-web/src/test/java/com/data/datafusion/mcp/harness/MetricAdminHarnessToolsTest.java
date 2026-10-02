package com.data.datafusion.mcp.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Metric;
import com.data.datafusion.service.MetricDirectoryService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.MetricSqlService;
import com.data.datafusion.service.dto.MetricDTO;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 指标管理工具族的行为测试。
 *
 * <p>重点覆盖「部分更新」的字段合并语义：平台 {@code MetricService.update} 是完整覆盖 + 强校验，
 * 若 harness 直接把入参 DTO 递过去，未提交的字段会被清空并触发校验失败，
 * 因此这里断言未提交字段确实被原值保留。
 */
class MetricAdminHarnessToolsTest {

    private MetricService metricService;
    private MetricDirectoryService metricDirectoryService;
    private MetricSqlService metricSqlService;
    private MetricAdminHarnessTools tools;

    @BeforeEach
    void setUp() {
        metricService = mock(MetricService.class);
        metricDirectoryService = mock(MetricDirectoryService.class);
        metricSqlService = mock(MetricSqlService.class);
        tools = new MetricAdminHarnessTools(metricService, metricDirectoryService, metricSqlService);
    }

    @Test
    void shouldMergeUpdateWithoutWipingUnspecifiedFields() throws Exception {
        MetricDTO existing = existingAtomicMetric();
        when(metricService.findOne(7L)).thenReturn(Optional.of(existing));
        when(metricService.update(eq(7L), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        Object result = tools
            .metricUpdateHarnessTool()
            .execute(Map.of("id", 7L, "description", "口径改为：成交金额（含税）"), context());

        ArgumentCaptor<MetricDTO> captor = ArgumentCaptor.forClass(MetricDTO.class);
        verify(metricService).update(eq(7L), captor.capture());
        MetricDTO sent = captor.getValue();

        assertThat(sent.getDescription()).isEqualTo("口径改为：成交金额（含税）");
        // 未提交的字段必须原样保留，否则平台校验会失败（如公式为空、事实表缺失）
        assertThat(sent.getName()).isEqualTo("销售额");
        assertThat(sent.getCode()).isEqualTo("sales_amount");
        assertThat(sent.getFormula()).isEqualTo("SUM(amount)");
        assertThat(sent.getFactModelId()).isEqualTo(3101L);
        assertThat(sent.getDataType()).isEqualTo("DECIMAL");
        assertThat(sent.getMetricType()).isEqualTo(Metric.TYPE_ATOMIC);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("code")).isEqualTo("sales_amount");
    }

    @Test
    void shouldRejectUpdateForUnknownMetric() {
        when(metricService.findOne(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.metricUpdateHarnessTool().execute(Map.of("id", 404L, "name", "x"), context()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("指标不存在");
    }

    @Test
    void shouldCreateAtomicMetricFromRequiredFields() throws Exception {
        when(metricService.save(any())).thenAnswer(invocation -> {
            MetricDTO dto = invocation.getArgument(0);
            dto.setId(99L);
            return dto;
        });

        tools
            .metricCreateHarnessTool()
            .execute(
                Map.of(
                    "name", "销售额",
                    "code", "sales_amount",
                    "metricType", Metric.TYPE_ATOMIC,
                    "dataType", "DECIMAL",
                    "formula", "SUM(amount)",
                    "factModelId", 3101,
                    "unit", "元",
                    "filterConfig", "{\"conditions\":[]}"
                ),
                context()
            );

        ArgumentCaptor<MetricDTO> captor = ArgumentCaptor.forClass(MetricDTO.class);
        verify(metricService).save(captor.capture());
        MetricDTO sent = captor.getValue();
        assertThat(sent.getName()).isEqualTo("销售额");
        assertThat(sent.getCode()).isEqualTo("sales_amount");
        assertThat(sent.getFormula()).isEqualTo("SUM(amount)");
        assertThat(sent.getFactModelId()).isEqualTo(3101L);
        assertThat(sent.getUnit()).isEqualTo("元");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeleteMetricAndReportWhatWasRemoved() throws Exception {
        when(metricService.findOne(7L)).thenReturn(Optional.of(existingAtomicMetric()));

        Map<String, Object> result = (Map<String, Object>) tools.metricDeleteHarnessTool().execute(Map.of("id", 7L), context());

        verify(metricService).delete(7L);
        assertThat(result).containsEntry("deletedId", 7L).containsEntry("code", "sales_amount");
    }

    @Test
    void shouldPreviewSqlForDraftDefinition() throws Exception {
        when(metricSqlService.generateSql(any())).thenReturn("SELECT SUM(amount) FROM main.fact_sales_order_item");

        Object result = tools
            .metricPreviewSqlHarnessTool()
            .execute(
                Map.of(
                    "code", "sales_amount",
                    "metricType", Metric.TYPE_ATOMIC,
                    "dataType", "DECIMAL",
                    "factModelId", 3101,
                    "formula", "SUM(amount)"
                ),
                context()
            );

        assertThat(((Map<?, ?>) result).get("sql")).isEqualTo("SELECT SUM(amount) FROM main.fact_sales_order_item");
        assertThat(((Map<?, ?>) result).get("metric")).isEqualTo("sales_amount");
    }

    @Test
    void shouldExposeMetricCrudToolsAsWritesAndQueriesAsReads() {
        assertThat(tools.metricListHarnessTool().mutating()).isFalse();
        assertThat(tools.metricGetHarnessTool().mutating()).isFalse();
        assertThat(tools.metricPreviewSqlHarnessTool().mutating()).isFalse();
        assertThat(tools.metricDirectoryListHarnessTool().mutating()).isFalse();

        assertThat(tools.metricCreateHarnessTool().mutating()).isTrue();
        assertThat(tools.metricUpdateHarnessTool().mutating()).isTrue();
        assertThat(tools.metricDeleteHarnessTool().mutating()).isTrue();
        assertThat(tools.metricDirectoryCreateHarnessTool().mutating()).isTrue();
        assertThat(tools.metricDirectoryUpdateHarnessTool().mutating()).isTrue();
        assertThat(tools.metricDirectoryDeleteHarnessTool().mutating()).isTrue();

        // 与问数工具同属 metric 分组，目录里聚在一起
        assertThat(tools.metricListHarnessTool().group()).isEqualTo("metric");
    }

    private static MetricDTO existingAtomicMetric() {
        MetricDTO dto = new MetricDTO();
        dto.setId(7L);
        dto.setName("销售额");
        dto.setCode("sales_amount");
        dto.setDescription("原口径");
        dto.setMetricType(Metric.TYPE_ATOMIC);
        dto.setStatus(Metric.STATUS_ENABLED);
        dto.setDataType("DECIMAL");
        dto.setFactModelId(3101L);
        dto.setFormula("SUM(amount)");
        return dto;
    }

    private static DatayHarnessContext context() {
        return new DatayHarnessContext(null, 1L, "t1");
    }
}
