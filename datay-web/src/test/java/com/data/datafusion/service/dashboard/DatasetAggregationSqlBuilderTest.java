package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.data.datafusion.service.dataset.DatasetAggregationSqlBuilder;
import com.data.datafusion.service.dashboard.DatasetQueryOptions.Aggregation;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatasetAggregationSqlBuilderTest {

    private final DataSourceDTO mysql = dataSource();

    @Test
    void shouldBuildGroupedAggregationWithSortAndLimit() {
        String sql = DatasetAggregationSqlBuilder.apply(
            mysql,
            "SELECT region, amount FROM orders",
            List.of("region"),
            List.of(new Aggregation("amount", "sum")),
            "amount",
            "desc",
            10,
            0,
            Set.of("region", "amount")
        );

        assertThat(sql)
            .isEqualTo(
                "SELECT * FROM (SELECT region, SUM(amount) AS amount FROM (SELECT region, amount FROM orders) dp_agg GROUP BY region) dp_sort ORDER BY amount DESC LIMIT 10"
            );
    }

    @Test
    void shouldBuildAverageAggregation() {
        String sql = DatasetAggregationSqlBuilder.apply(
            mysql,
            "SELECT region, amount FROM orders",
            List.of("region", "channel"),
            List.of(new Aggregation("amount", "avg")),
            null,
            null,
            null,
            0,
            Set.of("region", "channel", "amount")
        );

        assertThat(sql).contains("AVG(amount) AS amount").contains("GROUP BY region, channel");
    }

    @Test
    void shouldFallbackToSortLimitWhenNoAggregation() {
        String sql = DatasetAggregationSqlBuilder.apply(
            mysql,
            "SELECT region, amount FROM orders",
            List.of(),
            List.of(),
            "amount",
            "asc",
            5,
            0,
            Set.of("region", "amount")
        );

        assertThat(sql).isEqualTo("SELECT * FROM (SELECT region, amount FROM orders) dp_sort ORDER BY amount ASC LIMIT 5");
    }

    @Test
    void shouldRejectUnknownAggregationField() {
        assertThatThrownBy(() ->
            DatasetAggregationSqlBuilder.apply(
                mysql,
                "SELECT region, amount FROM orders",
                List.of("region"),
                List.of(new Aggregation("missing", "sum")),
                null,
                null,
                10,
                0,
                Set.of("region", "amount")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("聚合字段不存在");
    }

    @Test
    void shouldRejectInvalidAggregationOperator() {
        assertThatThrownBy(() ->
            DatasetAggregationSqlBuilder.apply(
                mysql,
                "SELECT region, amount FROM orders",
                List.of("region"),
                List.of(new Aggregation("amount", "median")),
                null,
                null,
                10,
                0,
                Set.of("region", "amount")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的聚合方式");
    }

    private DataSourceDTO dataSource() {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setType("MYSQL");
        return dto;
    }
}
