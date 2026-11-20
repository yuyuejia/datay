package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.service.dataset.DatasetAggregationSqlBuilder;
import com.data.datafusion.service.dashboard.DatasetQueryOptions.Aggregation;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 聚合 SQL 的真实执行结果校验。
 *
 * <p>除了确认 SQL 结构，还用 DuckDB 执行生成的 SQL，校验 sum/avg/min/max/count
 * 以及平均值两位小数取整的实际结果，避免只做字符串断言造成数值偏差。
 */
class DatasetAggregationSqlAccuracyTest {

    private static final String CREATE_TABLE = "CREATE TABLE orders (region VARCHAR, amount DECIMAL(18,4))";

    private static final String INSERT_ROWS =
        "INSERT INTO orders VALUES " +
        "('华东', 1.00), ('华东', 2.00), ('华东', 2.00), " +
        "('华南', 120.50), ('华南', 60.00), ('华南', NULL), " +
        "('边界', 1.00), ('边界', 1.01)";

    @Test
    void shouldProduceAccurateGroupedAggregations() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            prepareData(connection);

            Map<String, BigDecimal> avg = groupedValues(connection, "avg");
            assertDecimal(avg.get("华东"), "1.67");
            assertDecimal(avg.get("华南"), "90.25");
            assertDecimal(avg.get("边界"), "1.01");

            Map<String, BigDecimal> sum = groupedValues(connection, "sum");
            assertDecimal(sum.get("华东"), "5.00");
            assertDecimal(sum.get("华南"), "180.50");

            Map<String, BigDecimal> min = groupedValues(connection, "min");
            assertDecimal(min.get("华东"), "1.00");
            assertDecimal(min.get("华南"), "60.00");

            Map<String, BigDecimal> max = groupedValues(connection, "max");
            assertDecimal(max.get("华东"), "2.00");
            assertDecimal(max.get("华南"), "120.50");

            Map<String, BigDecimal> count = groupedValues(connection, "count");
            assertDecimal(count.get("华东"), "3");
            assertDecimal(count.get("华南"), "2");
        }
    }

    @Test
    void shouldProduceAccurateGlobalAggregationForKpiAndGauge() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            prepareData(connection);

            String sql = DatasetAggregationSqlBuilder.apply(
                duckdbDataSource(),
                "SELECT region, amount FROM orders",
                List.of(),
                List.of(new Aggregation("amount", "avg")),
                null,
                null,
                10,
                0,
                Set.of("region", "amount")
            );

            try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
                assertThat(resultSet.next()).isTrue();
                // 非空金额：1 + 2 + 2 + 120.5 + 60 + 1 + 1.01 = 187.51，共 7 条
                assertDecimal(resultSet.getBigDecimal("amount"), "26.79");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    private Map<String, BigDecimal> groupedValues(Connection connection, String agg) throws Exception {
        String sql = DatasetAggregationSqlBuilder.apply(
            duckdbDataSource(),
            "SELECT region, amount FROM orders",
            List.of("region"),
            List.of(new Aggregation("amount", agg)),
            null,
            null,
            null,
            0,
            Set.of("region", "amount")
        );
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                result.put(resultSet.getString("region"), resultSet.getBigDecimal("amount"));
            }
        }
        return result;
    }

    private void prepareData(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            statement.execute(INSERT_ROWS);
        }
    }

    private DataSourceDTO duckdbDataSource() {
        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setType("DUCKDB");
        return dataSource;
    }

    private void assertDecimal(BigDecimal actual, String expected) {
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
    }
}
