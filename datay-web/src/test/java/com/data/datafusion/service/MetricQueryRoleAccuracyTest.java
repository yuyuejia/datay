package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DimensionRole;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DimensionRoleRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.DatasetPersistenceService;
import com.data.datafusion.service.RoleDatasetScopeService;
import com.data.datafusion.service.dashboard.AnalysisDatasetResolver;
import com.data.datafusion.service.dashboard.DatasetQueryOptions;
import com.data.datafusion.service.dashboard.DatasetQueryService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.data.datafusion.service.dto.MetricQueryFieldDTO;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.MetricFilterConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 维度角色真实数据验证：使用 DuckDB 执行指标查询 SQL，校验单事实多角色、
 * 业务限定角色、跨事实共有维度与跨事实衍生指标的实际结果。
 */
class MetricQueryRoleAccuracyTest {

    private static final String ORDER_DATE = "ORDER_DATE";
    private static final String PAY_DATE = "PAY_DATE";
    private static final String SHIP_DATE = "SHIP_DATE";

    @TempDir
    Path tempDir;

    private MetricQueryService metricQueryService;

    private DataModel dimDate;
    private DataModel orderFact;
    private DataModel payFact;
    private DataModel dimOrder;

    private Metric orderAmount;
    private Metric payAmount;
    private Metric costAmount;
    private Metric grossProfit;
    private Metric payRatio;
    private Metric orderGrossMargin;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile = tempDir.resolve("role-accuracy.duckdb");
        prepareDatabase(databaseFile);

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId("ds-1");
        dataSource.setType("DUCKDB");
        dataSource.setUrl("jdbc:duckdb:" + databaseFile);
        dataSource.setSchemaName("main");

        MetricRepository metricRepository = mock(MetricRepository.class);
        DataModelRepository dataModelRepository = mock(DataModelRepository.class);
        ModelFieldRepository modelFieldRepository = mock(ModelFieldRepository.class);
        DimensionRoleRepository dimensionRoleRepository = mock(DimensionRoleRepository.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        RoleDataScopeService roleDataScopeService = mock(RoleDataScopeService.class);

        dimDate = model("dim-date", "dim_date", "日期维度", "DIMENSION", "dim_date");
        dimOrder = model("dim-order", "dim_order", "订单号退化维度", "DIMENSION", "dim_order");
        dimOrder.setDimensionKind("DEGENERATE");
        dimOrder.setTableName(null);
        orderFact = model("fact-order", "fact_order", "订单事实", "DWD", "fact_order");
        orderFact.setTimeFieldName("order_date_key");
        payFact = model("fact-pay", "fact_pay", "支付事实", "DWD", "fact_pay");
        payFact.setTimeFieldName("pay_date_key");
        orderFact.setDataSourceId("ds-1");
        payFact.setDataSourceId("ds-1");

        ModelField dateKey = field("date_key", null, null, true);
        ModelField year = field("year", null, null, false);
        ModelField month = field("month", null, null, false);

        ModelField orderDateKey = field("order_date_key", "dim-date", ORDER_DATE, false);
        ModelField orderPayDateKey = field("pay_date_key", "dim-date", PAY_DATE, false);
        ModelField shipDateKey = field("ship_date_key", "dim-date", SHIP_DATE, false);
        ModelField orderId = field("order_id", "dim-order", null, false);
        ModelField amount = field("order_amount", null, null, false);
        ModelField cost = field("cost_amount", null, null, false);
        ModelField payDateKey = field("pay_date_key", "dim-date", PAY_DATE, false);
        ModelField payOrderId = field("order_id", "dim-order", null, false);
        ModelField payAmountField = field("pay_amount", null, null, false);

        when(dataModelRepository.findById("dim-date")).thenReturn(Optional.of(dimDate));
        when(dataModelRepository.findById("dim-order")).thenReturn(Optional.of(dimOrder));
        when(dataModelRepository.findById("fact-order")).thenReturn(Optional.of(orderFact));
        when(dataModelRepository.findById("fact-pay")).thenReturn(Optional.of(payFact));
        when(dataModelRepository.findFirstByCode("dim_date")).thenReturn(Optional.of(dimDate));
        when(dataModelRepository.findFirstByCode("dim_order")).thenReturn(Optional.of(dimOrder));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("dim-date")).thenReturn(List.of(dateKey, year, month));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("dim-order")).thenReturn(List.of());
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("fact-order"))
            .thenReturn(List.of(orderDateKey, orderPayDateKey, shipDateKey, orderId, amount, cost));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("fact-pay"))
            .thenReturn(List.of(payDateKey, payOrderId, payAmountField));

        orderAmount = atomic("m-order-amount", "order_amount_metric", "SUM(order_amount)", "fact-order");
        payAmount = atomic("m-pay-amount", "pay_amount_metric", "SUM(pay_amount)", "fact-pay");
        costAmount = atomic("m-cost-amount", "cost_amount_metric", "SUM(cost_amount)", "fact-order");
        grossProfit = derived("m-gross-profit", "gross_profit_metric", "${order_amount_metric} - ${cost_amount_metric}");
        orderGrossMargin = derived("m-order-gross-margin", "order_gross_margin", "${gross_profit_metric} / ${order_amount_metric}");
        payRatio = derived("m-pay-ratio", "pay_ratio", "${pay_amount_metric} / ${order_amount_metric}");

        List<Metric> metrics = List.of(orderAmount, payAmount, costAmount, grossProfit, orderGrossMargin, payRatio);
        when(metricRepository.findAll()).thenReturn(metrics);
        for (Metric metric : metrics) {
            when(metricRepository.findByCode(metric.getCode())).thenReturn(Optional.of(metric));
        }
        when(dataSourceService.findOne("ds-1")).thenReturn(Optional.of(dataSource));
        when(roleDataScopeService.resolveEffectiveScopes()).thenReturn(List.of());

        List<DimensionRole> roles = new ArrayList<>();
        roles.add(role(dimDate.getId(), ORDER_DATE, "下单日期"));
        roles.add(role(dimDate.getId(), PAY_DATE, "支付日期"));
        roles.add(role(dimDate.getId(), SHIP_DATE, "发货日期"));
        when(dimensionRoleRepository.findByDimensionModelIdOrderBySortOrderAscCodeAsc(dimDate.getId())).thenReturn(roles);
        for (DimensionRole role : roles) {
            when(dimensionRoleRepository.existsByDimensionModelIdAndCodeIgnoreCase(dimDate.getId(), role.getCode())).thenReturn(true);
        }

        metricQueryService = new MetricQueryService(
            metricRepository,
            dataModelRepository,
            modelFieldRepository,
            dimensionRoleRepository,
            dataSourceService,
            new DataSourceQueryService(),
            new ObjectMapper(),
            roleDataScopeService
        );
    }

    @Test
    void shouldQuerySameFactByOrderDateRole() throws Exception {
        Map<String, Object> result = query(metricCodes("order_amount_metric"), List.of(dateDimension(ORDER_DATE, "year", "month")), null);

        Map<String, BigDecimal> values = byKey(result, "month__ORDER_DATE", "order_amount_metric");
        assertDecimal(values.get("1"), "350");
    }

    @Test
    void shouldQuerySameFactByPayDateAndShipDateRoles() throws Exception {
        Map<String, Object> payResult = query(metricCodes("order_amount_metric"), List.of(dateDimension(PAY_DATE, "year", "month")), null);
        Map<String, BigDecimal> payValues = byKey(payResult, "month__PAY_DATE", "order_amount_metric");
        assertDecimal(payValues.get("1"), "350");

        Map<String, Object> shipResult = query(metricCodes("order_amount_metric"), List.of(dateDimension(SHIP_DATE, "year", "month")), null);
        Map<String, BigDecimal> shipValues = byKey(shipResult, "month__SHIP_DATE", "order_amount_metric");
        assertDecimal(shipValues.get("1"), "300");
        assertDecimal(shipValues.get("2"), "50");
    }

    @Test
    void shouldQueryTwoRolesOfSameDimensionInOneRequest() throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes("order_amount_metric"));
        dto.setDimensions(List.of(dateDimension(ORDER_DATE, "year", "month"), dateDimension(SHIP_DATE, "year", "month")));

        Map<String, Object> result = metricQueryService.query(dto);

        assertThat(result.get("columns")).asString().contains("year__ORDER_DATE", "month__ORDER_DATE", "year__SHIP_DATE", "month__SHIP_DATE");
        List<Map<String, Object>> rows = rows(result);
        assertThat(rows).hasSize(2);
        assertThat(value(rows.get(0), "month__ORDER_DATE")).isEqualTo(1);
        assertThat(value(rows.get(0), "month__SHIP_DATE")).isEqualTo(1);
        assertDecimal(rowDecimal(rows.get(0), "order_amount_metric"), "300");
        assertThat(value(rows.get(1), "month__ORDER_DATE")).isEqualTo(1);
        assertThat(value(rows.get(1), "month__SHIP_DATE")).isEqualTo(2);
        assertDecimal(rowDecimal(rows.get(1), "order_amount_metric"), "50");
    }

    @Test
    void shouldApplyDimensionConditionOnRole() throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes("order_amount_metric"));
        dto.setFilterConfig(
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionModelCode\":\"dim_date\",\"dimensionRole\":\"SHIP_DATE\"," +
            "\"dimensionFieldName\":\"month\",\"operator\":\"EQ\",\"value\":\"2\",\"logic\":\"AND\"}]}"
        );

        Map<String, Object> result = metricQueryService.query(dto);

        assertDecimal(rowDecimal(rows(result).get(0), "order_amount_metric"), "50");
    }

    @Test
    void shouldComputeCrossFactRatioWithDifferentDefaultRoles() throws Exception {
        Map<String, Object> result = query(metricCodes("pay_ratio"), List.of(dateDimension(null, "date_key")), null);

        Map<String, BigDecimal> values = byKey(result, "date_key", "pay_ratio");
        assertApprox(values.get("20240101"), "0.3333");
        assertApprox(values.get("20240102"), "5.0");
    }

    @Test
    void shouldComputeCrossFactRatioWithExplicitSameRole() throws Exception {
        Map<String, Object> result = query(metricCodes("pay_ratio"), List.of(dateDimension(PAY_DATE, "date_key")), null);

        Map<String, BigDecimal> values = byKey(result, "date_key__PAY_DATE", "pay_ratio");
        assertApprox(values.get("20240101"), "1.0");
        assertApprox(values.get("20240102"), "1.0");
    }

    @Test
    void shouldComputeSameFactDerivedMetricByRole() throws Exception {
        Map<String, Object> result = query(metricCodes("order_gross_margin"), List.of(dateDimension(SHIP_DATE, "month")), null);

        Map<String, BigDecimal> values = byKey(result, "month__SHIP_DATE", "order_gross_margin");
        // 1 月：毛利 300-200=100，毛利率 100/300=0.3333；2 月：毛利 50-30=20，毛利率 20/50=0.4
        assertApprox(values.get("1"), "0.3333");
        assertApprox(values.get("2"), "0.4");
    }

    @Test
    void shouldQueryAtomicMetricsFromTwoFactsUsingDifferentRoles() throws Exception {
        Map<String, Object> result = query(
            metricCodes("order_amount_metric", "pay_amount_metric"),
            List.of(dateDimension(null, "date_key")),
            null
        );

        List<Map<String, Object>> rows = rows(result);
        assertThat(rows).hasSize(2);
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(String.valueOf(value(row, "date_key")), row);
        }
        assertDecimal(rowDecimal(byDate.get("20240101"), "order_amount_metric"), "300");
        assertDecimal(rowDecimal(byDate.get("20240101"), "pay_amount_metric"), "100");
        assertDecimal(rowDecimal(byDate.get("20240102"), "order_amount_metric"), "50");
        assertDecimal(rowDecimal(byDate.get("20240102"), "pay_amount_metric"), "250");
    }

    @Test
    void shouldComputeCrossFactRatioWithoutDimensions() throws Exception {
        Map<String, Object> result = query(metricCodes("pay_ratio"), List.of(), null);

        List<Map<String, Object>> rows = rows(result);
        assertThat(rows).hasSize(1);
        assertApprox(rowDecimal(rows.get(0), "pay_ratio"), "1.0");
    }

    @Test
    void shouldFallbackToDefaultAssociationWhenFactMissesRequestedRole() throws Exception {
        // 订单事实有 SHIP_DATE，支付事实没有；支付事实回退到默认 PAY_DATE。
        Map<String, Object> result = query(metricCodes("pay_ratio"), List.of(dateDimension(SHIP_DATE, "date_key")), null);

        Map<String, BigDecimal> values = byKey(result, "date_key__SHIP_DATE", "pay_ratio");
        assertThat(values.get("20240101")).isNull();
        assertApprox(values.get("20240102"), "0.8333");
        assertThat(values.get("20240201")).isNull();
    }

    @Test
    void shouldCombineSameFactAndCrossFactDerivedMetrics() throws Exception {
        Map<String, Object> result = query(
            metricCodes("order_gross_margin", "pay_ratio"),
            List.of(dateDimension(null, "date_key")),
            null
        );

        List<Map<String, Object>> rows = rows(result);
        assertThat(rows).hasSize(2);
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(String.valueOf(value(row, "date_key")), row);
        }
        assertApprox(rowDecimal(byDate.get("20240101"), "order_gross_margin"), "0.3333");
        assertApprox(rowDecimal(byDate.get("20240101"), "pay_ratio"), "0.3333");
        assertApprox(rowDecimal(byDate.get("20240102"), "order_gross_margin"), "0.4");
        assertApprox(rowDecimal(byDate.get("20240102"), "pay_ratio"), "5.0");
    }

    @Test
    void shouldQueryDegenerateDimensionDirectlyFromFactField() throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes("order_amount_metric"));
        dto.setDimensions(List.of(dimension("dim_order", null)));

        String sql = metricQueryService.buildSqlFor(dto);
        assertThat(sql).doesNotContain("JOIN main.dim_order");
        assertThat(sql).contains("f.order_id AS order_id");
        assertThat(sql).contains("GROUP BY f.order_id");

        Map<String, Object> result = metricQueryService.query(dto);
        Map<String, BigDecimal> values = byKey(result, "order_id", "order_amount_metric");
        assertDecimal(values.get("1"), "100");
        assertDecimal(values.get("2"), "200");
        assertDecimal(values.get("3"), "50");
    }

    @Test
    void shouldUseDegenerateDimensionAsCommonDimensionAcrossFacts() throws Exception {
        Map<String, Object> result = query(
            metricCodes("order_amount_metric", "pay_amount_metric"),
            List.of(dimension("dim_order", null)),
            null
        );

        List<Map<String, Object>> rows = rows(result);
        assertThat(rows).hasSize(3);
        Map<String, Map<String, Object>> byOrder = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byOrder.put(String.valueOf(value(row, "order_id")), row);
        }
        assertDecimal(rowDecimal(byOrder.get("1"), "order_amount_metric"), "100");
        assertDecimal(rowDecimal(byOrder.get("1"), "pay_amount_metric"), "100");
        assertDecimal(rowDecimal(byOrder.get("2"), "order_amount_metric"), "200");
        assertDecimal(rowDecimal(byOrder.get("2"), "pay_amount_metric"), "200");
        assertDecimal(rowDecimal(byOrder.get("3"), "order_amount_metric"), "50");
        assertDecimal(rowDecimal(byOrder.get("3"), "pay_amount_metric"), "50");

        Map<String, Object> ratio = query(metricCodes("pay_ratio"), List.of(dimension("dim_order", null)), null);
        Map<String, BigDecimal> ratioValues = byKey(ratio, "order_id", "pay_ratio");
        assertApprox(ratioValues.get("1"), "1.0");
        assertApprox(ratioValues.get("2"), "1.0");
        assertApprox(ratioValues.get("3"), "1.0");
    }

    @Test
    void shouldExposeDegenerateDimensionMetadataFromFactFields() {
        MetricQueryDTO request = new MetricQueryDTO();
        request.setMetricCodes(metricCodes("order_amount_metric"));

        Map<String, Object> meta = metricQueryService.queryMeta(request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) meta.get("dimensions");
        Map<String, Object> orderDimension = dimensions
            .stream()
            .filter(dimension -> "dim_order".equals(dimension.get("dimensionModelCode")))
            .findFirst()
            .orElseThrow();
        assertThat(orderDimension).containsEntry("isDegenerateDimension", true);
        assertThat(orderDimension).containsEntry("recommendedDisplayField", "order_id");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) orderDimension.get("fields");
        assertThat(fields).extracting(field -> field.get("fieldName")).contains("order_id");
    }

    @Test
    void shouldQueryMetricDatasetByDegenerateDimension() throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes("order_amount_metric"));
        dto.setDimensions(List.of(dimension("dim_order", null, "order_id")));

        Map<String, Object> result = metricQueryService.queryDataset(dto, List.of(), DatasetQueryOptions.NONE);

        assertThat(result.get("columns")).asString().contains("order_id", "order_amount_metric");
        Map<String, BigDecimal> values = byKey(result, "order_id", "order_amount_metric");
        assertDecimal(values.get("1"), "100");
        assertDecimal(values.get("2"), "200");
        assertDecimal(values.get("3"), "50");
    }

    @Test
    void shouldQueryMetricDatasetWithDegenerateDimensionAcrossFacts() throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes("order_amount_metric", "pay_amount_metric", "pay_ratio"));
        dto.setDimensions(List.of(dimension("dim_order", null, "order_id")));

        Map<String, Object> result = metricQueryService.queryDataset(dto, List.of(), DatasetQueryOptions.NONE);

        assertThat(result.get("columns")).asString().contains("order_id", "order_amount_metric", "pay_amount_metric", "pay_ratio");
        List<Map<String, Object>> rows = rows(result);
        assertThat(rows).hasSize(3);
        Map<String, Map<String, Object>> byOrder = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byOrder.put(String.valueOf(value(row, "order_id")), row);
        }
        assertDecimal(rowDecimal(byOrder.get("1"), "order_amount_metric"), "100");
        assertDecimal(rowDecimal(byOrder.get("1"), "pay_amount_metric"), "100");
        assertApprox(rowDecimal(byOrder.get("1"), "pay_ratio"), "1.0");
        assertDecimal(rowDecimal(byOrder.get("2"), "order_amount_metric"), "200");
        assertApprox(rowDecimal(byOrder.get("2"), "pay_ratio"), "1.0");
        assertDecimal(rowDecimal(byOrder.get("3"), "order_amount_metric"), "50");
        assertApprox(rowDecimal(byOrder.get("3"), "pay_ratio"), "1.0");
    }

    @Test
    void shouldFilterMetricDatasetByDegenerateDimension() throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes("order_amount_metric"));
        dto.setDimensions(List.of(dimension("dim_order", null, "order_id")));
        dto.setFilterConfig(
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionModelCode\":\"dim_order\"," +
            "\"dimensionFieldName\":\"order_id\",\"operator\":\"IN\",\"value\":\"1,3\",\"logic\":\"AND\"}]}"
        );

        Map<String, Object> result = metricQueryService.queryDataset(dto, List.of(), DatasetQueryOptions.NONE);

        Map<String, BigDecimal> values = byKey(result, "order_id", "order_amount_metric");
        assertThat(values).containsOnlyKeys("1", "3");
        assertDecimal(values.get("1"), "100");
        assertDecimal(values.get("3"), "50");
    }

    @Test
    void shouldQueryMetricDatasetServiceWithDegenerateDimensionAndCrossFactMetrics() throws Exception {
        RoleDatasetScopeService roleDatasetScopeService = mock(RoleDatasetScopeService.class);
        when(roleDatasetScopeService.resolveConditions(null)).thenReturn(List.of());
        DatasetQueryService datasetQueryService = new DatasetQueryService(
            metricQueryService,
            mock(DataSourceService.class),
            new DataSourceQueryService(),
            roleDatasetScopeService,
            mock(RoleDataScopeService.class),
            mock(DatasetPersistenceService.class),
            mock(AnalysisDatasetResolver.class),
            new ObjectMapper()
        );

        Map<String, Object> metricQuery = new LinkedHashMap<>();
        metricQuery.put("metricCodes", List.of("order_amount_metric", "pay_amount_metric", "pay_ratio"));
        metricQuery.put(
            "dimensions",
            List.of(
                Map.of(
                    "dimensionModelCode",
                    "dim_order",
                    "dimensionFieldNames",
                    List.of("order_id")
                )
            )
        );
        metricQuery.put(
            "filterConfig",
            Map.of(
                "conditions",
                List.of(
                    Map.of(
                        "type",
                        "DIMENSION",
                        "dimensionModelCode",
                        "dim_order",
                        "dimensionFieldName",
                        "order_id",
                        "operator",
                        "IN",
                        "value",
                        "1,3",
                        "logic",
                        "AND"
                    )
                )
            )
        );
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("type", "METRIC");
        dataset.put("metricQuery", metricQuery);

        Map<String, Object> result = datasetQueryService.query(dataset, null, List.of(), Map.of(), null);

        List<Map<String, Object>> rows = rows(result);
        assertThat(rows).hasSize(2);
        Map<String, Map<String, Object>> byOrder = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byOrder.put(String.valueOf(value(row, "order_id")), row);
        }
        assertDecimal(rowDecimal(byOrder.get("1"), "order_amount_metric"), "100");
        assertDecimal(rowDecimal(byOrder.get("1"), "pay_amount_metric"), "100");
        assertApprox(rowDecimal(byOrder.get("1"), "pay_ratio"), "1.0");
        assertDecimal(rowDecimal(byOrder.get("3"), "order_amount_metric"), "50");
        assertDecimal(rowDecimal(byOrder.get("3"), "pay_amount_metric"), "50");
        assertApprox(rowDecimal(byOrder.get("3"), "pay_ratio"), "1.0");
    }

    @Test
    void shouldReturnRoleSuffixedColumnsFromMetricDatasetQuery() throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes("order_amount_metric"));
        dto.setDimensions(List.of(dateDimension(ORDER_DATE, "year", "month"), dateDimension(SHIP_DATE, "year", "month")));

        Map<String, Object> result = metricQueryService.queryDataset(dto, List.of(), DatasetQueryOptions.NONE);

        assertThat(result.get("columns")).asString().contains("year__ORDER_DATE", "year__SHIP_DATE");
        assertThat(result.get("columnLabels"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsKeys("year__ORDER_DATE", "year__SHIP_DATE");
        assertThat(rows(result)).hasSize(2);
    }

    private Map<String, Object> query(
        List<String> metricCodes,
        List<MetricQueryFieldDTO> dimensions,
        MetricFilterConfig filterConfig
    ) throws Exception {
        MetricQueryDTO dto = new MetricQueryDTO();
        dto.setMetricCodes(metricCodes);
        dto.setDimensions(dimensions);
        if (filterConfig != null) {
            dto.setFilterConfig(new ObjectMapper().writeValueAsString(filterConfig));
        }
        return metricQueryService.query(dto);
    }

    private List<String> metricCodes(String... codes) {
        return List.of(codes);
    }

    private MetricQueryFieldDTO dateDimension(String role, String... fields) {
        return dimension("dim_date", role, fields);
    }

    private MetricQueryFieldDTO dimension(String dimensionModelCode, String role, String... fields) {
        MetricQueryFieldDTO dimension = new MetricQueryFieldDTO();
        dimension.setDimensionModelCode(dimensionModelCode);
        dimension.setDimensionRole(role);
        if (fields.length > 0) {
            dimension.setDimensionFieldNames(List.of(fields));
        }
        return dimension;
    }

    private Map<String, BigDecimal> byKey(Map<String, Object> result, String key, String metricCode) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows(result)) {
            values.put(String.valueOf(value(row, key)), rowDecimal(row, metricCode));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rows");
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null && key.equalsIgnoreCase(String.valueOf(entry.getKey())))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private BigDecimal rowDecimal(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private void assertDecimal(BigDecimal actual, String expected) {
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
    }

    private void assertApprox(BigDecimal actual, String expected) {
        assertThat(actual).isNotNull();
        assertThat(actual.doubleValue()).isCloseTo(new BigDecimal(expected).doubleValue(), org.assertj.core.data.Offset.offset(0.0001));
    }

    private DataModel model(String id, String code, String name, String modelType, String tableName) {
        DataModel model = new DataModel();
        model.setId(id);
        model.setCode(code);
        model.setName(name);
        model.setModelType(modelType);
        model.setSchemaName("main");
        model.setTableName(tableName);
        return model;
    }

    private ModelField field(String fieldName, String dimensionModelId, String dimensionRole, boolean primaryKey) {
        ModelField field = new ModelField();
        field.setFieldName(fieldName);
        field.setDimensionModelId(dimensionModelId);
        field.setDimensionRole(dimensionRole);
        field.setIsPrimaryKey(primaryKey);
        field.setFieldType(primaryKey || fieldName.endsWith("_key") || fieldName.equals("year") || fieldName.equals("month") ? "INTEGER" : "DECIMAL");
        return field;
    }

    private Metric atomic(String id, String code, String formula, String factModelId) {
        Metric metric = new Metric();
        metric.setId(id);
        metric.setCode(code);
        metric.setName(code);
        metric.setMetricType(Metric.TYPE_ATOMIC);
        metric.setFormula(formula);
        metric.setFactModelId(factModelId);
        return metric;
    }

    private Metric derived(String id, String code, String formula) {
        Metric metric = new Metric();
        metric.setId(id);
        metric.setCode(code);
        metric.setName(code);
        metric.setMetricType(Metric.TYPE_DERIVED);
        metric.setFormula(formula);
        return metric;
    }

    private DimensionRole role(String dimensionModelId, String code, String name) {
        DimensionRole role = new DimensionRole();
        role.setDimensionModelId(dimensionModelId);
        role.setCode(code);
        role.setName(name);
        return role;
    }

    private void prepareDatabase(Path databaseFile) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + databaseFile);
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE dim_date (date_key INTEGER PRIMARY KEY, year INTEGER, month INTEGER)");
            statement.execute("INSERT INTO dim_date VALUES (20240101, 2024, 1), (20240102, 2024, 1), (20240201, 2024, 2)");
            statement.execute(
                "CREATE TABLE fact_order (order_id INTEGER, order_date_key INTEGER, pay_date_key INTEGER, ship_date_key INTEGER, order_amount DECIMAL(18,4), cost_amount DECIMAL(18,4))"
            );
            statement.execute(
                "INSERT INTO fact_order VALUES " +
                "(1, 20240101, 20240101, 20240102, 100, 60), " +
                "(2, 20240101, 20240102, 20240102, 200, 140), " +
                "(3, 20240102, 20240102, 20240201, 50, 30)"
            );
            statement.execute("CREATE TABLE fact_pay (pay_id INTEGER, order_id INTEGER, pay_date_key INTEGER, pay_amount DECIMAL(18,4))");
            statement.execute("INSERT INTO fact_pay VALUES (1, 1, 20240101, 100), (2, 2, 20240102, 200), (3, 3, 20240102, 50)");
        }
    }
}
