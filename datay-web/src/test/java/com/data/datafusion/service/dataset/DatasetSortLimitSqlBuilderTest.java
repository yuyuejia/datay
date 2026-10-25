package com.data.datafusion.service.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 数据集排序/Top N SQL 构建测试。
 */
class DatasetSortLimitSqlBuilderTest {

    @Test
    void shouldBuildLimitAndOrderForCommonDatabases() {
        DataSourceDTO dataSource = dataSource("MYSQL");
        String sql = DatasetSortLimitSqlBuilder.apply(
            dataSource,
            "SELECT region, amount FROM orders;",
            "amount",
            "desc",
            10,
            null,
            Set.of("region", "amount")
        );

        assertThat(sql).isEqualTo(
            "SELECT * FROM (SELECT region, amount FROM orders) dp_sort ORDER BY amount DESC LIMIT 10"
        );
    }

    @Test
    void shouldUseTopForSqlServerAndRownumForOracle() {
        String sqlServer = DatasetSortLimitSqlBuilder.apply(
            dataSource("SQLSERVER"),
            "SELECT * FROM orders",
            "amount",
            "asc",
            5,
            null,
            Set.of()
        );
        assertThat(sqlServer).isEqualTo("SELECT TOP 5 * FROM (SELECT * FROM orders) dp_sort ORDER BY amount ASC");

        String oracle = DatasetSortLimitSqlBuilder.apply(
            dataSource("ORACLE"),
            "SELECT * FROM orders",
            "amount",
            "asc",
            5,
            null,
            Set.of()
        );
        assertThat(oracle).contains("ORDER BY amount ASC").contains("ROWNUM <= 5");
    }

    @Test
    void shouldRejectUnknownSortField() {
        assertThatThrownBy(() ->
            DatasetSortLimitSqlBuilder.apply(dataSource("MYSQL"), "SELECT 1", "missing", "asc", 10, null, Set.of("amount"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("排序字段不存在");
    }

    @Test
    void shouldBuildOffsetPagination() {
        String mysql = DatasetSortLimitSqlBuilder.apply(
            dataSource("MYSQL"),
            "SELECT * FROM orders",
            "amount",
            "desc",
            10,
            20,
            Set.of("amount")
        );
        assertThat(mysql).contains("ORDER BY amount DESC LIMIT 10 OFFSET 20");

        String sqlServer = DatasetSortLimitSqlBuilder.apply(
            dataSource("SQLSERVER"),
            "SELECT * FROM orders",
            "amount",
            "desc",
            10,
            20,
            Set.of("amount")
        );
        assertThat(sqlServer).contains("ORDER BY amount DESC").contains("OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY");

        String oracle = DatasetSortLimitSqlBuilder.apply(
            dataSource("ORACLE"),
            "SELECT * FROM orders",
            "amount",
            "desc",
            10,
            20,
            Set.of("amount")
        );
        assertThat(oracle).contains("ROWNUM <= 30").contains("rn > 20");
    }

    @Test
    void shouldRequireLimitWhenOffsetProvided() {
        assertThatThrownBy(() ->
            DatasetSortLimitSqlBuilder.apply(dataSource("MYSQL"), "SELECT 1", "amount", "asc", null, 10, Set.of("amount"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
    }

    @Test
    void shouldReturnOriginalSqlWhenNoSortOrLimit() {
        assertThat(DatasetSortLimitSqlBuilder.apply(dataSource("MYSQL"), "SELECT 1", null, null, null, null, Set.of())).isEqualTo(
            "SELECT 1"
        );
    }

    private DataSourceDTO dataSource(String type) {
        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setType(type);
        return dataSource;
    }
}
