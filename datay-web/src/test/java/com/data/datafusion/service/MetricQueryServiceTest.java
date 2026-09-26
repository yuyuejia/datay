package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.data.datafusion.service.dto.MetricQueryFieldDTO;
import com.data.datafusion.service.dto.MetricQueryTimeRangeDTO;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.ScopedDimension;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link MetricQueryService} 查询 SQL 生成的单元测试。
 */
class MetricQueryServiceTest {

    private MetricRepository metricRepository;
    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private RoleDataScopeService roleDataScopeService;
    private MetricQueryService metricQueryService;

    private Metric salesAmount;
    private Metric salesQuantity;
    private Metric avgPrice;

    @BeforeEach
    void setUp() {
        metricRepository = mock(MetricRepository.class);
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        roleDataScopeService = mock(RoleDataScopeService.class);
        metricQueryService = new MetricQueryService(
            metricRepository,
            dataModelRepository,
            modelFieldRepository,
            mock(DataSourceService.class),
            mock(DataSourceQueryService.class),
            new ObjectMapper(),
            roleDataScopeService
        );

        DataModel factModel = new DataModel();
        factModel.setId(3101L);
        factModel.setCode("fact_sales_order_item");
        factModel.setModelType("DWD");
        factModel.setName("销售订单明细事实表");
        factModel.setSchemaName("main");
        factModel.setTableName("fact_sales_order_item");
        factModel.setTimeFieldName("order_date_sk");

        DataModel dimProduct = new DataModel();
        dimProduct.setId(3003L);
        dimProduct.setCode("dim_product");
        dimProduct.setName("商品维度");
        dimProduct.setSchemaName("main");
        dimProduct.setTableName("dim_product");

        ModelField orderStatus = new ModelField();
        orderStatus.setFieldName("order_status");
        ModelField productSk = new ModelField();
        productSk.setFieldName("product_sk");
        productSk.setDimensionModelId(3003L);
        ModelField customerSk = new ModelField();
        customerSk.setFieldName("customer_sk");
        customerSk.setDimensionModelId(3001L);
        ModelField orderDateSk = new ModelField();
        orderDateSk.setFieldName("order_date_sk");
        ModelField category = new ModelField();
        category.setFieldName("category_l1");
        ModelField productPk = new ModelField();
        productPk.setFieldName("product_sk");
        productPk.setIsPrimaryKey(true);

        DataModel dimCustomer = new DataModel();
        dimCustomer.setId(3001L);
        dimCustomer.setCode("dim_customer");
        dimCustomer.setName("客户维度");
        dimCustomer.setSchemaName("main");
        dimCustomer.setTableName("dim_customer");

        when(dataModelRepository.findById(3101L)).thenReturn(Optional.of(factModel));
        when(dataModelRepository.findById(3003L)).thenReturn(Optional.of(dimProduct));
        when(dataModelRepository.findById(3001L)).thenReturn(Optional.of(dimCustomer));
        when(dataModelRepository.findFirstByCode("dim_product")).thenReturn(Optional.of(dimProduct));
        when(dataModelRepository.findFirstByCode("dim_customer")).thenReturn(Optional.of(dimCustomer));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L))
            .thenReturn(List.of(orderStatus, productSk, customerSk, orderDateSk));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3003L)).thenReturn(List.of(productPk, category));

        salesAmount = atomic(2610L, "sales_amount", "SUM(amount)");
        salesQuantity = atomic(2611L, "sales_quantity", "SUM(quantity)");
        avgPrice = new Metric();
        avgPrice.setId(2615L);
        avgPrice.setCode("avg_selling_price");
        avgPrice.setName("平均销售价格");
        avgPrice.setMetricType(Metric.TYPE_DERIVED);
        avgPrice.setFormula("${sales_amount} / ${sales_quantity}");

        when(metricRepository.findAll()).thenReturn(List.of(salesAmount, salesQuantity, avgPrice));
        when(metricRepository.findByCode("sales_amount")).thenReturn(Optional.of(salesAmount));
        when(metricRepository.findByCode("sales_quantity")).thenReturn(Optional.of(salesQuantity));
        when(metricRepository.findByCode("avg_selling_price")).thenReturn(Optional.of(avgPrice));
    }

    private Metric atomic(Long id, String code, String formula) {
        Metric metric = new Metric();
        metric.setId(id);
        metric.setCode(code);
        metric.setName(code);
        metric.setMetricType(Metric.TYPE_ATOMIC);
        metric.setFactModelId(3101L);
        metric.setFormula(formula);
        return metric;
    }

    @Test
    void shouldBuildAtomicSqlWithDimensionFilterAndTimeRange() {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));

        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setFactFieldName("product_sk");
        dimension.setDimensionModelCode("dim_product");
        dimension.setDimensionFieldName("category_l1");
        dto.setDimensions(List.of(dimension));
        dto.setFilterConfig(
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionModelCode\":\"dim_product\",\"dimensionFieldName\":\"category_l1\",\"operator\":\"EQ\",\"value\":\"电子产品\",\"logic\":\"AND\"}]}"
        );
        MetricQueryTimeRangeDTO timeRange = new MetricQueryTimeRangeDTO();
        timeRange.setStart("20240101");
        timeRange.setEnd("20241231");
        dto.setTimeRange(timeRange);

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("d0.category_l1 AS category_l1");
        assertThat(sql).doesNotContain("category_l1_1");
        assertThat(sql).contains("AS sales_amount");
        assertThat(sql).contains("FROM main.fact_sales_order_item f");
        assertThat(sql).contains("LEFT JOIN main.dim_product d0 ON f.product_sk = d0.product_sk");
        assertThat(sql).contains("GROUP BY d0.category_l1");
        assertThat(sql).doesNotContain("GROUP BY d0.product_sk");
        assertThat(sql).contains("d0.category_l1 = '电子产品'");
        assertThat(sql).contains("f.order_date_sk BETWEEN 20240101 AND 20241231");
    }

    @Test
    void shouldDefaultToPrimaryKeyWhenNoDisplayField() {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_product");
        dto.setDimensions(List.of(dimension));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("d0.product_sk AS product_sk");
        assertThat(sql).contains("GROUP BY d0.product_sk");
    }

    @Test
    void shouldBuildDerivedSqlBySubstitutingReferencedMetrics() {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("avg_selling_price"));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("AS avg_selling_price");
        assertThat(sql).contains("(SUM(amount))");
        assertThat(sql).contains("(SUM(quantity))");
        assertThat(sql).doesNotContain("${");
    }

    @Test
    void shouldIgnoreTimeRangeWhenFactModelHasNoTimeField() {
        DataModel noTimeFact = new DataModel();
        noTimeFact.setId(3101L);
        noTimeFact.setModelType("DWD");
        noTimeFact.setSchemaName("main");
        noTimeFact.setTableName("fact_sales_order_item");
        when(dataModelRepository.findById(3101L)).thenReturn(Optional.of(noTimeFact));

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryTimeRangeDTO timeRange = new MetricQueryTimeRangeDTO();
        timeRange.setStart("20240101");
        timeRange.setEnd("20241231");
        dto.setTimeRange(timeRange);

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).doesNotContain("BETWEEN");
        assertThat(sql).doesNotContain("order_date_sk");
    }

    @Test
    void shouldNormalizeNumericTimeFieldDateValues() {
        DataModel factModel = new DataModel();
        factModel.setId(3101L);
        factModel.setModelType("DWD");
        factModel.setSchemaName("main");
        factModel.setTableName("fact_sales_order_item");
        factModel.setTimeFieldName("order_date_sk");
        when(dataModelRepository.findById(3101L)).thenReturn(Optional.of(factModel));

        ModelField orderDateSk = new ModelField();
        orderDateSk.setFieldName("order_date_sk");
        orderDateSk.setFieldType("LONG");
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L)).thenReturn(List.of(orderDateSk));

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryTimeRangeDTO timeRange = new MetricQueryTimeRangeDTO();
        timeRange.setStart("2024-01-01");
        timeRange.setEnd("2024-12-31");
        dto.setTimeRange(timeRange);

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("f.order_date_sk BETWEEN 20240101 AND 20241231");
    }

    @Test
    void shouldConvertTimeRangeByFieldType() {
        DataModel factModel = new DataModel();
        factModel.setId(3101L);
        factModel.setModelType("DWD");
        factModel.setSchemaName("main");
        factModel.setTableName("fact_sales_order_item");
        factModel.setTimeFieldName("order_date");
        when(dataModelRepository.findById(3101L)).thenReturn(Optional.of(factModel));

        ModelField orderDate = new ModelField();
        orderDate.setFieldName("order_date");
        orderDate.setFieldType("DATE");
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L)).thenReturn(List.of(orderDate));

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryTimeRangeDTO timeRange = new MetricQueryTimeRangeDTO();
        timeRange.setStart("2024-01-01 00:00:00");
        timeRange.setEnd("2024-12-31 23:59:59");
        dto.setTimeRange(timeRange);

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("f.order_date BETWEEN '2024-01-01' AND '2024-12-31'");
    }

    @Test
    void shouldBuildMultiFactSqlWithCommonDimensions() {
        DataModel orderFact = new DataModel();
        orderFact.setId(3100L);
        orderFact.setModelType("DWD");
        orderFact.setName("销售订单事实表");
        orderFact.setSchemaName("main");
        orderFact.setTableName("fact_sales_order");

        ModelField orderProductSk = new ModelField();
        orderProductSk.setFieldName("product_sk");
        orderProductSk.setDimensionModelId(3003L);
        ModelField orderAmount = new ModelField();
        orderAmount.setFieldName("order_amount");

        Metric orderMetric = atomic(2700L, "order_amount_metric", "SUM(order_amount)");
        orderMetric.setFactModelId(3100L);

        when(dataModelRepository.findById(3100L)).thenReturn(Optional.of(orderFact));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3100L)).thenReturn(List.of(orderProductSk, orderAmount));
        when(metricRepository.findAll()).thenReturn(List.of(salesAmount, salesQuantity, avgPrice, orderMetric));
        when(metricRepository.findByCode("order_amount_metric")).thenReturn(Optional.of(orderMetric));

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount", "order_amount_metric"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_product");
        dimension.setDimensionFieldName("category_l1");
        dto.setDimensions(List.of(dimension));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("FULL OUTER JOIN");
        assertThat(sql).contains("COALESCE");
        assertThat(sql).contains("t0.key_0 = t1.key_0");
        assertThat(sql).contains("dim_0_category_l1");
        assertThat(sql).contains("t0.sales_amount");
        assertThat(sql).contains("t1.order_amount_metric");
        assertThat(sql).contains("main.fact_sales_order_item");
        assertThat(sql).contains("main.fact_sales_order");
    }

    @Test
    void shouldAggregateHierarchyDimensionByLevel() {
        DataModel dimRegion = new DataModel();
        dimRegion.setId(4000L);
        dimRegion.setCode("dim_region");
        dimRegion.setName("地区维度");
        dimRegion.setSchemaName("main");
        dimRegion.setTableName("dim_region");
        dimRegion.setDimensionKind("HIERARCHY");
        dimRegion.setLevelCount(2);

        ModelField regionSk = new ModelField();
        regionSk.setFieldName("region_sk");
        regionSk.setDimensionModelId(4000L);

        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L)).thenReturn(List.of(regionSk));
        when(dataModelRepository.findFirstByCode("dim_region")).thenReturn(Optional.of(dimRegion));
        when(dataModelRepository.findById(4000L)).thenReturn(Optional.of(dimRegion));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(4000L))
            .thenReturn(
                List.of(
                    hierarchyField("level1_id", "LEVEL_ID", 1, false),
                    hierarchyField("level1_name", "LEVEL_NAME", 1, false),
                    hierarchyField("level2_id", "LEVEL_ID", 2, false),
                    hierarchyField("level2_name", "LEVEL_NAME", 2, false),
                    hierarchyField("member_id", "MEMBER_ID", null, true),
                    hierarchyField("member_name", "MEMBER_NAME", null, false),
                    hierarchyField("hierarchy", "HIERARCHY", null, false)
                )
            );

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_region");
        dimension.setLevelIndex(1);
        dto.setDimensions(List.of(dimension));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("LEFT JOIN main.dim_region d0 ON f.region_sk = d0.member_id");
        assertThat(sql).contains("d0.level1_name AS level1_name");
        assertThat(sql).contains("GROUP BY d0.level1_id, d0.level1_name");
    }

    @Test
    void shouldGroupByTimeDimensionPeriodFields() {
        DataModel dimDate = new DataModel();
        dimDate.setId(3000L);
        dimDate.setCode("dim_date");
        dimDate.setName("日期维度");
        dimDate.setSchemaName("main");
        dimDate.setTableName("dim_date");

        ModelField dateSk = new ModelField();
        dateSk.setFieldName("date_sk");
        dateSk.setIsPrimaryKey(true);
        ModelField year = new ModelField();
        year.setFieldName("year");
        ModelField month = new ModelField();
        month.setFieldName("month");

        ModelField orderDateSk = new ModelField();
        orderDateSk.setFieldName("order_date_sk");
        orderDateSk.setDimensionModelId(3000L);

        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L)).thenReturn(List.of(orderDateSk));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3000L)).thenReturn(List.of(dateSk, year, month));
        when(dataModelRepository.findFirstByCode("dim_date")).thenReturn(Optional.of(dimDate));
        when(dataModelRepository.findById(3000L)).thenReturn(Optional.of(dimDate));

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_date");
        dimension.setDimensionFieldNames(List.of("year", "month"));
        dto.setDimensions(List.of(dimension));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("LEFT JOIN main.dim_date d0 ON f.order_date_sk = d0.date_sk");
        assertThat(sql).contains("d0.year AS year");
        assertThat(sql).contains("d0.month AS month");
        assertThat(sql).contains("GROUP BY d0.year, d0.month");

        Map<String, Object> meta = metricQueryService.queryMeta(dto);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) meta.get("dimensions");
        assertThat(dimensions.get(0)).containsEntry("isTimeDimension", true);
        @SuppressWarnings("unchecked")
        List<String> periodFields = (List<String>) dimensions.get(0).get("periodFields");
        assertThat(periodFields).contains("year", "month");
    }

    @Test
    void shouldExposeDimensionDisplayFieldInMeta() {
        DataModel dimStore = new DataModel();
        dimStore.setId(3002L);
        dimStore.setCode("dim_store");
        dimStore.setName("门店维度");
        dimStore.setDimensionKind("NORMAL");
        dimStore.setDisplayFieldName("store_name");
        when(dataModelRepository.findById(3002L)).thenReturn(Optional.of(dimStore));
        when(dataModelRepository.findFirstByCode("dim_store")).thenReturn(Optional.of(dimStore));

        ModelField storeSk = new ModelField();
        storeSk.setFieldName("store_sk");
        storeSk.setDimensionModelId(3002L);
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L)).thenReturn(List.of(storeSk));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3002L)).thenReturn(
            List.of(modelField("store_sk", true, null, null), modelField("store_name", false, null, null))
        );

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_store");
        dto.setDimensions(List.of(dimension));

        Map<String, Object> meta = metricQueryService.queryMeta(dto);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) meta.get("dimensions");
        assertThat(dimensions.get(0)).containsEntry("displayFieldName", "store_name");
        assertThat(dimensions.get(0)).containsEntry("recommendedDisplayField", "store_name");
    }

    @Test
    void shouldAggregateSingleFactBySelectedDimensionFields() {
        DataModel dimDate = new DataModel();
        dimDate.setId(3000L);
        dimDate.setCode("dim_date");
        dimDate.setName("日期维度");
        dimDate.setSchemaName("main");
        dimDate.setTableName("dim_date");

        ModelField orderDateSk = new ModelField();
        orderDateSk.setFieldName("order_date_sk");
        orderDateSk.setDimensionModelId(3000L);

        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L)).thenReturn(List.of(orderDateSk));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3000L))
            .thenReturn(
                List.of(
                    modelField("date_sk", true, null, null),
                    modelField("year", false, null, null),
                    modelField("month", false, null, null)
                )
            );
        when(dataModelRepository.findFirstByCode("dim_date")).thenReturn(Optional.of(dimDate));
        when(dataModelRepository.findById(3000L)).thenReturn(Optional.of(dimDate));

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_date");
        dimension.setDimensionFieldNames(List.of("year", "month"));
        dto.setDimensions(List.of(dimension));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("d0.year AS year");
        assertThat(sql).contains("d0.month AS month");
        assertThat(sql).contains("GROUP BY d0.year, d0.month");
        assertThat(sql).doesNotContain("GROUP BY d0.date_sk");
    }

    private ModelField modelField(String name, boolean primaryKey, String role, Integer levelIndex) {
        ModelField field = new ModelField();
        field.setFieldName(name);
        field.setIsPrimaryKey(primaryKey);
        field.setFieldRole(role);
        field.setLevelIndex(levelIndex);
        return field;
    }

    @Test
    void shouldAggregateTimeDimensionByLevel() {
        DataModel dimTime = new DataModel();
        dimTime.setId(3000L);
        dimTime.setCode("dim_time");
        dimTime.setName("时间维度");
        dimTime.setSchemaName("main");
        dimTime.setTableName("dim_time");
        dimTime.setDimensionKind("TIME");
        dimTime.setTimeLevels("YEAR,MONTH");

        ModelField orderDateSk = new ModelField();
        orderDateSk.setFieldName("order_date_sk");
        orderDateSk.setDimensionModelId(3000L);

        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3101L)).thenReturn(List.of(orderDateSk));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3000L))
            .thenReturn(
                List.of(
                    hierarchyField("year_id", "LEVEL_ID", 1, false),
                    hierarchyField("year_name", "LEVEL_NAME", 1, false),
                    hierarchyField("month_id", "LEVEL_ID", 2, false),
                    hierarchyField("month_name", "LEVEL_NAME", 2, false),
                    hierarchyField("date_key", "MEMBER_ID", null, true),
                    hierarchyField("hierarchy", "HIERARCHY", null, false)
                )
            );
        when(dataModelRepository.findFirstByCode("dim_time")).thenReturn(Optional.of(dimTime));
        when(dataModelRepository.findById(3000L)).thenReturn(Optional.of(dimTime));

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_time");
        dimension.setLevelIndex(2);
        dto.setDimensions(List.of(dimension));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("LEFT JOIN main.dim_time d0 ON f.order_date_sk = d0.date_key");
        assertThat(sql).contains("d0.month_name AS month_name");
        assertThat(sql).contains("GROUP BY d0.month_id, d0.month_name");

        Map<String, Object> meta = metricQueryService.queryMeta(dto);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) meta.get("dimensions");
        assertThat(dimensions.get(0)).containsEntry("isHierarchy", true).containsEntry("dimensionKind", "TIME");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) dimensions.get(0).get("levels");
        assertThat(levels.get(1)).containsEntry("granularity", "MONTH").containsEntry("label", "月");
        assertThat(levels.get(1)).containsEntry("idField", "month_id").containsEntry("nameField", "month_name");
    }

    private ModelField hierarchyField(String name, String role, Integer levelIndex, boolean primaryKey) {
        ModelField field = new ModelField();
        field.setFieldName(name);
        field.setFieldRole(role);
        field.setLevelIndex(levelIndex);
        field.setIsPrimaryKey(primaryKey);
        return field;
    }

    @Test
    void shouldReturnAssociatedDimensionsInQueryMeta() {
        MetricQueryDTO request = new MetricQueryDTO();
        request.setMetricCodes(List.of("sales_amount"));
        Map<String, Object> meta = metricQueryService.queryMeta(request);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) meta.get("dimensions");
        assertThat(dimensions).hasSize(2);
        assertThat(dimensions.get(0))
            .containsEntry("dimensionModelCode", "dim_product")
            .containsEntry("dimensionModelName", "商品维度")
            .containsEntry("factFieldName", "product_sk");
        assertThat(dimensions.get(1)).containsEntry("dimensionModelCode", "dim_customer").containsEntry("factFieldName", "customer_sk");
    }

    @Test
    void shouldApplyRoleDataScopeAndCombineWithUserConditions() {
        when(roleDataScopeService.resolveEffectiveScopes()).thenReturn(
            List.of(scopedDimension(3003L, scopeCondition("category_l1", "IN", "电子产品,图书")))
        );

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode("dim_product");
        dimension.setDimensionFieldName("category_l1");
        dto.setDimensions(List.of(dimension));
        dto.setFilterConfig(
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionModelCode\":\"dim_product\",\"dimensionFieldName\":\"category_l1\",\"operator\":\"EQ\",\"value\":\"图书\",\"logic\":\"AND\"}]}"
        );

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("d0.category_l1 IN ('电子产品', '图书')");
        assertThat(sql).contains("d0.category_l1 = '图书'");
        assertThat(sql.indexOf("IN ('电子产品")).isLessThan(sql.indexOf("= '图书'"));
        assertThat(sql).contains(") AND ((");
    }

    @Test
    void shouldOrWithinDimensionAndAndAcrossDimensions() {
        when(roleDataScopeService.resolveEffectiveScopes()).thenReturn(
            List.of(
                scopedDimension(
                    3003L,
                    scopeCondition("category_l1", "IN", "A"),
                    scopeCondition("category_l1", "IN", "B")
                ),
                scopedDimension(3001L, scopeCondition("city", "EQ", "北京"))
            )
        );

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).contains("d0.category_l1 IN ('A')");
        assertThat(sql).contains(") OR (");
        assertThat(sql).contains("d0.category_l1 IN ('B')");
        assertThat(sql).contains("d1.city = '北京'");
    }

    @Test
    void shouldSkipScopeWhenFactHasNoControlledDimension() {
        when(roleDataScopeService.resolveEffectiveScopes()).thenReturn(
            List.of(scopedDimension(9999L, scopeCondition("region", "EQ", "华东")))
        );

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).doesNotContain("region");
        assertThat(sql).doesNotContain("WHERE");
    }

    @Test
    void shouldDenyScopeWhenMissingDimensionPolicyIsDeny() {
        ReflectionTestUtils.setField(metricQueryService, "missingDimensionPolicy", "DENY");
        when(roleDataScopeService.resolveEffectiveScopes()).thenReturn(
            List.of(scopedDimension(9999L, scopeCondition("region", "EQ", "华东")))
        );

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount"));

        assertThatThrownBy(() -> metricQueryService.buildSqlFor(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少受控维度");
    }

    @Test
    void shouldApplyRoleDataScopeToEveryFactSubquery() {
        DataModel orderFact = new DataModel();
        orderFact.setId(3100L);
        orderFact.setModelType("DWD");
        orderFact.setName("销售订单事实表");
        orderFact.setSchemaName("main");
        orderFact.setTableName("fact_sales_order");
        ModelField orderProductSk = new ModelField();
        orderProductSk.setFieldName("product_sk");
        orderProductSk.setDimensionModelId(3003L);
        ModelField orderAmount = new ModelField();
        orderAmount.setFieldName("order_amount");

        Metric orderMetric = atomic(2700L, "order_amount_metric", "SUM(order_amount)");
        orderMetric.setFactModelId(3100L);

        when(dataModelRepository.findById(3100L)).thenReturn(Optional.of(orderFact));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3100L)).thenReturn(List.of(orderProductSk, orderAmount));
        when(metricRepository.findAll()).thenReturn(List.of(salesAmount, salesQuantity, avgPrice, orderMetric));
        when(metricRepository.findByCode("order_amount_metric")).thenReturn(Optional.of(orderMetric));
        when(roleDataScopeService.resolveEffectiveScopes()).thenReturn(
            List.of(scopedDimension(3003L, scopeCondition("category_l1", "IN", "电子产品")))
        );

        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(List.of("sales_amount", "order_amount_metric"));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(countOccurrences(sql, "d0.category_l1 IN ('电子产品')")).isEqualTo(2);
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }

    private static ScopedDimension scopedDimension(Long dimensionModelId, MetricFilterCondition... conditions) {
        ScopedDimension scoped = new ScopedDimension();
        scoped.setDimensionModelId(dimensionModelId);
        scoped.setConditions(List.of(conditions));
        return scoped;
    }

    private static MetricFilterCondition scopeCondition(String fieldName, String operator, String value) {
        MetricFilterCondition condition = new MetricFilterCondition();
        condition.setType(MetricFilterCondition.TYPE_DIMENSION);
        condition.setDimensionFieldName(fieldName);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }
}
