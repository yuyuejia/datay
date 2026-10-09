package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.MetricDTO;
import com.data.datafusion.service.mapper.MetricMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MetricService} 校验逻辑与依赖解析的单元测试。
 */
class MetricServiceTest {

    private MetricRepository metricRepository;
    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private MetricService metricService;

    @BeforeEach
    void setUp() {
        metricRepository = mock(MetricRepository.class);
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        metricService = new MetricService(
            metricRepository,
            dataModelRepository,
            modelFieldRepository,
            mock(MetricMapper.class),
            new ObjectMapper()
        );
        when(metricRepository.findByCode(any())).thenReturn(Optional.empty());
    }

    private DataModel model(String modelType) {
        DataModel model = new DataModel();
        model.setId("100");
        model.setModelType(modelType);
        model.setSchemaName("dwd");
        model.setTableName("fact_sales_order_item");
        return model;
    }

    private MetricDTO atomicDto() {
        MetricDTO dto = new MetricDTO();
        dto.setName("销售额");
        dto.setCode("sales_amount");
        dto.setMetricType(Metric.TYPE_ATOMIC);
        dto.setFactModelId("100");
        dto.setFormula("SUM(amount)");
        dto.setDataType("DECIMAL");
        return dto;
    }

    @Test
    void saveShouldRejectBlankName() {
        MetricDTO dto = atomicDto();
        dto.setName(" ");
        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("指标名称");
    }

    @Test
    void saveShouldRejectInvalidCode() {
        MetricDTO dto = atomicDto();
        dto.setCode("sales amount!");
        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("指标编码");
    }

    @Test
    void saveShouldRejectNonDwdFactModel() {
        MetricDTO dto = atomicDto();
        when(dataModelRepository.findById("100")).thenReturn(Optional.of(model("DWS")));
        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DWD");
    }

    @Test
    void saveShouldRejectEmptyAtomicFormula() {
        MetricDTO dto = atomicDto();
        dto.setFormula("  ");
        when(dataModelRepository.findById("100")).thenReturn(Optional.of(model("DWD")));
        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("计算公式不能为空");
    }

    @Test
    void saveShouldRejectIllegalAtomicFormula() {
        MetricDTO dto = atomicDto();
        dto.setFormula("SUM(amount); DROP TABLE t");
        when(dataModelRepository.findById("100")).thenReturn(Optional.of(model("DWD")));
        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("计算公式只能包含");
    }

    @Test
    void saveShouldRejectMissingDataType() {
        MetricDTO dto = atomicDto();
        dto.setDataType(null);
        when(dataModelRepository.findById("100")).thenReturn(Optional.of(model("DWD")));
        ModelField field = new ModelField();
        field.setFieldName("amount");
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("100")).thenReturn(List.of(field));
        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("数据类型");
    }

    @Test
    void saveShouldRejectUnknownFormulaReference() {
        MetricDTO dto = new MetricDTO();
        dto.setName("平均单价");
        dto.setCode("avg_price");
        dto.setMetricType(Metric.TYPE_DERIVED);
        dto.setFormula("${unknown_metric} * 2");
        dto.setDataType("DECIMAL");
        when(metricRepository.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> metricService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("公式引用的指标不存在");
    }

    @Test
    void saveShouldRejectFormulaWithIllegalCharacters() {
        MetricDTO dto = new MetricDTO();
        dto.setName("平均单价");
        dto.setCode("avg_price");
        dto.setMetricType(Metric.TYPE_DERIVED);
        dto.setFormula("${sales_amount}; DROP TABLE t");
        dto.setDataType("DECIMAL");
        when(metricRepository.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("公式只能包含");
    }

    @Test
    void saveShouldRejectCyclicReference() {
        Metric a = new Metric();
        a.setId("1");
        a.setCode("a");
        a.setName("A");
        a.setMetricType(Metric.TYPE_ATOMIC);
        Metric b = new Metric();
        b.setId("2");
        b.setCode("b");
        b.setName("B");
        b.setMetricType(Metric.TYPE_DERIVED);
        b.setFormula("${a}");

        when(metricRepository.findAll()).thenReturn(List.of(a, b));

        MetricDTO dto = new MetricDTO();
        dto.setId("1");
        dto.setName("A");
        dto.setCode("a");
        dto.setMetricType(Metric.TYPE_DERIVED);
        dto.setFormula("${b}");
        dto.setDataType("DECIMAL");
        when(metricRepository.findByCodeAndIdNot(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> metricService.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("循环引用");
    }

    @Test
    void parseFormulaRefsShouldExtractCodes() {
        assertThat(metricService.parseFormulaRefs("${sales_amount} / ${sales_quantity} + 1"))
            .containsExactly("sales_amount", "sales_quantity");
    }
}
