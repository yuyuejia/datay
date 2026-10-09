package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mapstruct.factory.Mappers;

/**
 * {@link MetricSqlService} 计算 SQL 生成的单元测试。
 */
class MetricSqlServiceTest {

    private MetricRepository metricRepository;
    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private MetricSqlService metricSqlService;

    private DataModel factModel;

    @BeforeEach
    void setUp() {
        metricRepository = mock(MetricRepository.class);
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        metricSqlService = new MetricSqlService(
            metricRepository,
            dataModelRepository,
            modelFieldRepository,
            Mappers.getMapper(MetricMapper.class),
            new ObjectMapper()
        );

        factModel = new DataModel();
        factModel.setId("100");
        factModel.setModelType("DWD");
        factModel.setName("销售订单明细事实表");
        factModel.setSchemaName("dwd");
        factModel.setTableName("fact_sales_order_item");

        ModelField amount = new ModelField();
        amount.setFieldName("amount");
        amount.setFieldType("DECIMAL");
        ModelField quantity = new ModelField();
        quantity.setFieldName("quantity");
        quantity.setFieldType("DECIMAL");
        ModelField orderStatus = new ModelField();
        orderStatus.setFieldName("order_status");
        orderStatus.setFieldType("VARCHAR");
        ModelField productSk = new ModelField();
        productSk.setFieldName("product_sk");
        productSk.setFieldType("LONG");
        productSk.setDimensionModelId("300");

        when(dataModelRepository.findById("100")).thenReturn(Optional.of(factModel));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("100")).thenReturn(List.of(amount, quantity, orderStatus, productSk));
    }

    private MetricDTO atomicDto() {
        MetricDTO dto = new MetricDTO();
        dto.setName("销售额");
        dto.setCode("sales_amount");
        dto.setMetricType(Metric.TYPE_ATOMIC);
        dto.setFactModelId("100");
        dto.setFormula("SUM(amount)");
        return dto;
    }

    @Test
    void shouldGenerateAtomicSql() {
        String sql = metricSqlService.generateSql(atomicDto());
        assertThat(sql).contains("SELECT SUM(amount) AS sales_amount");
        assertThat(sql).contains("FROM dwd.fact_sales_order_item f");
        assertThat(sql).doesNotContain("WHERE");
    }

    @Test
    void shouldGenerateAtomicSqlFromMultiFieldFormula() {
        MetricDTO dto = atomicDto();
        dto.setFormula("SUM(amount * quantity)");
        String sql = metricSqlService.generateSql(dto);
        assertThat(sql).contains("SELECT SUM(amount * quantity) AS sales_amount");
    }

    @Test
    void shouldGenerateAtomicSqlWithFactFieldFilter() {
        MetricDTO dto = atomicDto();
        dto.setFilterConfig(
            "{\"conditions\":[{\"type\":\"FACT_FIELD\",\"factFieldName\":\"order_status\",\"operator\":\"EQ\",\"value\":\"PAID\",\"logic\":\"AND\"}]}"
        );
        String sql = metricSqlService.generateSql(dto);
        assertThat(sql).contains("WHERE (f.order_status = 'PAID')");
    }

    @Test
    void shouldGenerateAtomicSqlWithDimensionJoin() {
        MetricDTO dto = atomicDto();
        dto.setFilterConfig(
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"factFieldName\":\"product_sk\",\"dimensionModelId\":\"300\",\"dimensionFieldName\":\"category_l1\",\"operator\":\"EQ\",\"value\":\"饮料\",\"logic\":\"AND\"}]}"
        );

        DataModel dimModel = new DataModel();
        dimModel.setId("300");
        dimModel.setSchemaName("dwd");
        dimModel.setTableName("dim_product");
        ModelField dimPk = new ModelField();
        dimPk.setFieldName("product_sk");
        dimPk.setIsPrimaryKey(true);
        ModelField category = new ModelField();
        category.setFieldName("category_l1");
        when(dataModelRepository.findById("300")).thenReturn(Optional.of(dimModel));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("300")).thenReturn(List.of(dimPk, category));

        String sql = metricSqlService.generateSql(dto);
        assertThat(sql).contains("LEFT JOIN dwd.dim_product d0 ON f.product_sk = d0.product_sk");
        assertThat(sql).contains("d0.category_l1 = '饮料'");
    }

    @Test
    void shouldGenerateDerivedSqlWithCtes() {
        Metric a = new Metric();
        a.setId("1");
        a.setCode("sales_amount");
        a.setName("销售额");
        a.setMetricType(Metric.TYPE_ATOMIC);
        a.setFactModelId("100");
        a.setFormula("SUM(amount)");

        Metric b = new Metric();
        b.setId("2");
        b.setCode("sales_quantity");
        b.setName("销售量");
        b.setMetricType(Metric.TYPE_ATOMIC);
        b.setFactModelId("100");
        b.setFormula("SUM(quantity)");

        when(metricRepository.findAll()).thenReturn(List.of(a, b));

        MetricDTO target = new MetricDTO();
        target.setName("平均销售价格");
        target.setCode("avg_sales_price");
        target.setMetricType(Metric.TYPE_DERIVED);
        target.setFormula("${sales_amount} / ${sales_quantity}");

        String sql = metricSqlService.generateSql(target);
        assertThat(sql).contains("WITH ");
        assertThat(sql).contains("m_sales_amount AS");
        assertThat(sql).contains("m_sales_quantity AS");
        assertThat(sql).contains("(SELECT v FROM m_sales_amount) / (SELECT v FROM m_sales_quantity)");
        assertThat(sql).contains("SELECT v AS avg_sales_price FROM m_avg_sales_price");
    }
}
