package com.data.datafusion.service.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link DatasetFieldValueSqlBuilder} 数据集字段取值 SQL 构建的单元测试。
 */
class DatasetFieldValueSqlBuilderTest {

    @Test
    void shouldBuildDistinctValueQueryWithPaging() {
        String sql = DatasetFieldValueSqlBuilder.buildValueQuery("SELECT region, amount FROM orders;", "region", null, 20, 40);

        assertThat(sql).isEqualTo(
            "SELECT DISTINCT region AS dp_value FROM (SELECT region, amount FROM orders) dp_values" +
            " WHERE region IS NOT NULL ORDER BY dp_value LIMIT 20 OFFSET 40"
        );
    }

    @Test
    void shouldEscapeLikeKeyword() {
        String sql = DatasetFieldValueSqlBuilder.buildValueQuery("SELECT region FROM orders", "region", "O'Reilly", 10, 0);

        assertThat(sql).contains("region LIKE '%O''Reilly%'");
    }

    @Test
    void shouldBuildCountQuery() {
        String sql = DatasetFieldValueSqlBuilder.buildCountQuery("SELECT region FROM orders", "region", "华");

        assertThat(sql).isEqualTo(
            "SELECT COUNT(DISTINCT region) AS cnt FROM (SELECT region FROM orders) dp_values" +
            " WHERE region IS NOT NULL AND region LIKE '%华%'"
        );
    }

    @Test
    void shouldRejectIllegalFieldName() {
        assertThatThrownBy(() ->
                DatasetFieldValueSqlBuilder.buildValueQuery("SELECT region FROM orders", "region; DROP TABLE t", null, 20, 0)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("字段名不合法");
    }
}
