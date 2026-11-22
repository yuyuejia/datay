package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
import com.data.datafusion.service.dashboard.DatasetQueryOptions;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.data.datafusion.service.dto.MetricQueryFieldDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * MySQL / PostgreSQL / Oracle 下的指标数据集查询能力与结果准确性验证。
 *
 * <p>每个数据库建同一套维表、事实表和指标定义，执行相同的指标数据集查询，
 * 校验单事实、多事实、跨事实衍生指标、退化维度和数据集过滤的实际结果。</p>
 */
class MetricDatasetCrossDatabaseAccuracyTest {

    private static final String ORDER_DATE = "ORDER_DATE";
    private static final String PAY_DATE = "PAY_DATE";

    private static final DbEnv MYSQL = new DbEnv(
        "MySQL",
        "MYSQL",
        "jdbc:mysql://127.0.0.1:3306/datay_verify?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "root",
        "1234qwer",
        "datay_verify",
        false
    );

    private static final DbEnv POSTGRESQL = new DbEnv(
        "PostgreSQL",
        "POSTGRESQL",
        "jdbc:postgresql://127.0.0.1:5432/testdb",
        "admin",
        "123456",
        "public",
        false
    );

    private static final DbEnv ORACLE = new DbEnv(
        "Oracle",
        "ORACLE",
        "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
        "datay",
        "datay123",
        "DATAY",
        true
    );

    @Test
    void shouldVerifyMetricDatasetOnMysql() throws Exception {
        verify(MYSQL);
    }

    @Test
    void shouldVerifyMetricDatasetOnPostgresql() throws Exception {
        verify(POSTGRESQL);
    }

    @Test
    void shouldVerifyMetricDatasetOnOracle() throws Exception {
        verify(ORACLE);
    }

    private void verify(DbEnv db) throws Exception {
        assumeTrue(canConnect(db), db.name + " 不可用，跳过");
        prepareTables(db);

        DataSourceDTO dataSource = dataSource(db);
        DataModel dimDate = model(db, "dim-date", "dim_date", "日期维度", "DIMENSION", db.table("role_verify_dim_date"));
        dimDate.setDimensionKind("NORMAL");
        DataModel dimOrder = model(db, "dim-order", "dim_order", "订单号退化维度", "DIMENSION", null);
        dimOrder.setDimensionKind("DEGENERATE");
        DataModel orderFact = model(db, "fact-order", "fact_order", "订单事实", "DWD", db.table("role_verify_fact_order"));
        orderFact.setTimeFieldName("order_date_key");
        orderFact.setDataSourceId(db.name);
        DataModel payFact = model(db, "fact-pay", "fact_pay", "支付事实", "DWD", db.table("role_verify_fact_pay"));
        payFact.setTimeFieldName("pay_date_key");
        payFact.setDataSourceId(db.name);

        ModelField dateKey = field("date_key", null, null, true);
        ModelField year = field("year", null, null, false);
        ModelField month = field("month", null, null, false);
        ModelField orderId = field("order_id", "dim-order", null, false);
        ModelField orderDateKey = field("order_date_key", "dim-date", ORDER_DATE, false);
        ModelField payDateKey = field("pay_date_key", "dim-date", PAY_DATE, false);
        ModelField amount = field("order_amount", null, null, false);
        ModelField cost = field("cost_amount", null, null, false);
        ModelField payOrderId = field("order_id", "dim-order", null, false);
        ModelField payPayDateKey = field("pay_date_key", "dim-date", PAY_DATE, false);
        ModelField payAmountField = field("pay_amount", null, null, false);

        DataModelRepository dataModelRepository = mock(DataModelRepository.class);
        ModelFieldRepository modelFieldRepository = mock(ModelFieldRepository.class);
        MetricRepository metricRepository = mock(MetricRepository.class);
        DimensionRoleRepository dimensionRoleRepository = mock(DimensionRoleRepository.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        RoleDataScopeService roleDataScopeService = mock(RoleDataScopeService.class);

        when(dataModelRepository.findById("dim-date")).thenReturn(Optional.of(dimDate));
        when(dataModelRepository.findById("dim-order")).thenReturn(Optional.of(dimOrder));
        when(dataModelRepository.findById("fact-order")).thenReturn(Optional.of(orderFact));
        when(dataModelRepository.findById("fact-pay")).thenReturn(Optional.of(payFact));
        when(dataModelRepository.findFirstByCode("dim_date")).thenReturn(Optional.of(dimDate));
        when(dataModelRepository.findFirstByCode("dim_order")).thenReturn(Optional.of(dimOrder));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("dim-date")).thenReturn(List.of(dateKey, year, month));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("dim-order")).thenReturn(List.of());
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("fact-order"))
            .thenReturn(List.of(orderId, orderDateKey, payDateKey, amount, cost));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("fact-pay"))
            .thenReturn(List.of(payOrderId, payPayDateKey, payAmountField));

        Metric orderAmount = atomic("m-order", "order_amount_metric", "SUM(order_amount)", "fact-order");
        Metric costAmount = atomic("m-cost", "cost_amount_metric", "SUM(cost_amount)", "fact-order");
        Metric payAmount = atomic("m-pay", "pay_amount_metric", "SUM(pay_amount)", "fact-pay");
        Metric grossProfit = derived("m-gross", "gross_profit_metric", "${order_amount_metric} - ${cost_amount_metric}");
        Metric grossMargin = derived("m-margin", "gross_margin", "${gross_profit_metric} / ${order_amount_metric}");
        Metric payRatio = derived("m-ratio", "pay_ratio", "${pay_amount_metric} / ${order_amount_metric}");
        List<Metric> metrics = List.of(orderAmount, costAmount, payAmount, grossProfit, grossMargin, payRatio);
        when(metricRepository.findAll()).thenReturn(metrics);
        for (Metric metric : metrics) {
            when(metricRepository.findByCode(metric.getCode())).thenReturn(Optional.of(metric));
        }

        List<DimensionRole> roles = new ArrayList<>();
        roles.add(role("dim-date", ORDER_DATE, "下单日期"));
        roles.add(role("dim-date", PAY_DATE, "支付日期"));
        when(dimensionRoleRepository.findByDimensionModelIdOrderBySortOrderAscCodeAsc("dim-date")).thenReturn(roles);
        for (DimensionRole role : roles) {
            when(dimensionRoleRepository.existsByDimensionModelIdAndCodeIgnoreCase("dim-date", role.getCode())).thenReturn(true);
        }

        when(dataSourceService.findOne(db.name)).thenReturn(Optional.of(dataSource));
        when(roleDataScopeService.resolveEffectiveScopes()).thenReturn(List.of());

        MetricQueryService service = new MetricQueryService(
            metricRepository,
            dataModelRepository,
            modelFieldRepository,
            dimensionRoleRepository,
            dataSourceService,
            new DataSourceQueryService(),
            new ObjectMapper(),
            roleDataScopeService
        );

        // 1. 单事实 + 日期角色：按下单日期按月
        MetricQueryDTO dateQuery = new MetricQueryDTO();
        dateQuery.setMetricCodes(List.of("order_amount_metric", "gross_margin"));
        dateQuery.setDimensions(List.of(dimension("dim_date", ORDER_DATE, "year", "month")));
        Map<String, Object> dateResult = service.queryDataset(dateQuery, List.of(), DatasetQueryOptions.NONE);
        Map<String, BigDecimal> orderByMonth = byKey(dateResult, "month__ORDER_DATE", "order_amount_metric");
        assertDecimal(db, orderByMonth.get("1"), "300");
        assertDecimal(db, orderByMonth.get("2"), "50");
        Map<String, BigDecimal> marginByMonth = byKey(dateResult, "month__ORDER_DATE", "gross_margin");
        assertApprox(db, marginByMonth.get("1"), "0.3333");
        assertApprox(db, marginByMonth.get("2"), "0.4");

        // 2. 退化维度：直接使用事实表 order_id
        MetricQueryDTO degenerateQuery = new MetricQueryDTO();
        degenerateQuery.setMetricCodes(List.of("order_amount_metric", "pay_amount_metric", "pay_ratio"));
        degenerateQuery.setDimensions(List.of(dimension("dim_order", null, "order_id")));
        Map<String, Object> degenerateResult = service.queryDataset(degenerateQuery, List.of(), DatasetQueryOptions.NONE);
        List<Map<String, Object>> degenerateRows = rows(degenerateResult);
        assertThat(degenerateRows).as(db.name + " 退化维度行数").hasSize(3);
        Map<String, Map<String, Object>> byOrder = new LinkedHashMap<>();
        for (Map<String, Object> row : degenerateRows) {
            byOrder.put(keyOf(value(row, "order_id")), row);
        }
        assertDecimal(db, rowDecimal(byOrder.get("1"), "order_amount_metric"), "100");
        assertDecimal(db, rowDecimal(byOrder.get("1"), "pay_amount_metric"), "100");
        assertApprox(db, rowDecimal(byOrder.get("1"), "pay_ratio"), "1.0");
        assertDecimal(db, rowDecimal(byOrder.get("3"), "order_amount_metric"), "50");
        assertApprox(db, rowDecimal(byOrder.get("3"), "pay_ratio"), "1.0");

        // 3. 多事实跨事实衍生指标：显式按 PAY_DATE 对齐
        MetricQueryDTO crossFactQuery = new MetricQueryDTO();
        crossFactQuery.setMetricCodes(List.of("pay_ratio"));
        crossFactQuery.setDimensions(List.of(dimension("dim_date", PAY_DATE, "date_key")));
        Map<String, Object> crossFactResult = service.queryDataset(crossFactQuery, List.of(), DatasetQueryOptions.NONE);
        Map<String, BigDecimal> ratioByDate = byKey(crossFactResult, "date_key__PAY_DATE", "pay_ratio");
        assertThat(ratioByDate).as(db.name + " 跨事实日期").containsKeys("20240101", "20240102", "20240201");
        assertApprox(db, ratioByDate.get("20240101"), "1.0");
        assertApprox(db, ratioByDate.get("20240102"), "1.0");
        assertApprox(db, ratioByDate.get("20240201"), "1.0");

        // 4. 退化维度 + 数据集过滤
        MetricQueryDTO filteredQuery = new MetricQueryDTO();
        filteredQuery.setMetricCodes(List.of("order_amount_metric"));
        filteredQuery.setDimensions(List.of(dimension("dim_order", null, "order_id")));
        filteredQuery.setFilterConfig(
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionModelCode\":\"dim_order\"," +
            "\"dimensionFieldName\":\"order_id\",\"operator\":\"IN\",\"value\":\"1,3\",\"logic\":\"AND\"}]}"
        );
        Map<String, Object> filteredResult = service.queryDataset(filteredQuery, List.of(), DatasetQueryOptions.NONE);
        Map<String, BigDecimal> filteredValues = byKey(filteredResult, "order_id", "order_amount_metric");
        assertThat(filteredValues).as(db.name + " 过滤结果").containsOnlyKeys("1", "3");
        assertDecimal(db, filteredValues.get("1"), "100");
        assertDecimal(db, filteredValues.get("3"), "50");
    }

    private boolean canConnect(DbEnv db) {
        DriverManager.setLoginTimeout(5);
        try (Connection ignored = DriverManager.getConnection(db.url, db.username, db.password)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void prepareTables(DbEnv db) throws Exception {
        String dimDate = db.table("role_verify_dim_date");
        String factOrder = db.table("role_verify_fact_order");
        String factPay = db.table("role_verify_fact_pay");
        try (Connection connection = DriverManager.getConnection(db.url, db.username, db.password); Statement statement = connection.createStatement()) {
            dropQuietly(statement, dimDate);
            dropQuietly(statement, factOrder);
            dropQuietly(statement, factPay);

            statement.execute(
                "CREATE TABLE " + dimDate + " (date_key INT PRIMARY KEY, year INT, month INT)"
            );
            statement.execute(
                "CREATE TABLE " + factOrder + " (order_id INT, order_date_key INT, pay_date_key INT, order_amount DECIMAL(18,4), cost_amount DECIMAL(18,4))"
            );
            statement.execute(
                "CREATE TABLE " + factPay + " (order_id INT, pay_date_key INT, pay_amount DECIMAL(18,4))"
            );

            statement.execute("INSERT INTO " + dimDate + " VALUES (20240101, 2024, 1)");
            statement.execute("INSERT INTO " + dimDate + " VALUES (20240102, 2024, 1)");
            statement.execute("INSERT INTO " + dimDate + " VALUES (20240201, 2024, 2)");

            statement.execute("INSERT INTO " + factOrder + " VALUES (1, 20240101, 20240101, 100, 60)");
            statement.execute("INSERT INTO " + factOrder + " VALUES (2, 20240101, 20240102, 200, 140)");
            statement.execute("INSERT INTO " + factOrder + " VALUES (3, 20240201, 20240201, 50, 30)");

            statement.execute("INSERT INTO " + factPay + " VALUES (1, 20240101, 100)");
            statement.execute("INSERT INTO " + factPay + " VALUES (2, 20240102, 200)");
            statement.execute("INSERT INTO " + factPay + " VALUES (3, 20240201, 50)");
        }
    }

    private void dropQuietly(Statement statement, String table) {
        try {
            statement.execute("DROP TABLE " + table);
        } catch (Exception ignored) {
            // 首次创建时表不存在
        }
    }

    private DataSourceDTO dataSource(DbEnv db) {
        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId(db.name);
        dataSource.setName(db.name + "-verify");
        dataSource.setType(db.type);
        dataSource.setUrl(db.url);
        dataSource.setUsername(db.username);
        dataSource.setPassword(db.password);
        dataSource.setSchemaName(db.schema);
        return dataSource;
    }

    private DataModel model(DbEnv db, String id, String code, String name, String modelType, String tableName) {
        DataModel model = new DataModel();
        model.setId(id);
        model.setCode(code);
        model.setName(name);
        model.setModelType(modelType);
        model.setSchemaName(tableName == null ? null : db.schema);
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
            values.put(keyOf(value(row, key)), rowDecimal(row, metricCode));
        }
        return values;
    }

    private String keyOf(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number)).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rows");
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
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

    private void assertDecimal(DbEnv db, BigDecimal actual, String expected) {
        assertThat(actual).as(db.name + " 数值").isNotNull();
        assertThat(actual).as(db.name + " 数值").isEqualByComparingTo(new BigDecimal(expected));
    }

    private void assertApprox(DbEnv db, BigDecimal actual, String expected) {
        assertThat(actual).as(db.name + " 数值").isNotNull();
        assertThat(actual.doubleValue())
            .as(db.name + " 数值")
            .isCloseTo(new BigDecimal(expected).doubleValue(), org.assertj.core.data.Offset.offset(0.0001));
    }

    private record DbEnv(String name, String type, String url, String username, String password, String schema, boolean oracle) {
        String table(String name) {
            return oracle ? name.toUpperCase() : name;
        }
    }
}
