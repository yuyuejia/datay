package com.data.datafusion.service.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * SQL 数据集看板筛选条件构建测试。
 */
class DatasetFilterSqlBuilderTest {

    @Test
    void shouldWrapBaseSqlWithEqualityAndInFilters() {
        DatasetScopeCondition region = condition("region", "IN", "华东,华南", null);
        String sql = DatasetFilterSqlBuilder.wrap(
            "SELECT region, amount FROM orders;",
            List.of(region),
            Set.of("region", "amount")
        );

        assertThat(sql).startsWith("SELECT * FROM (SELECT region, amount FROM orders) dp_filter WHERE");
        assertThat(sql).contains("region IN ('华东', '华南')");
    }

    @Test
    void shouldBuildLikeAndBetweenConditions() {
        DatasetScopeCondition name = condition("name", "LIKE", "%东%", null);
        DatasetScopeCondition amount = condition("amount", "BETWEEN", "100", "200");

        String sql = DatasetFilterSqlBuilder.wrap("SELECT * FROM orders", List.of(name, amount), Set.of("name", "amount"));

        assertThat(sql).contains("(name LIKE '%东%') AND (amount BETWEEN 100 AND 200)");
    }

    @Test
    void shouldRejectUnknownFieldAndInvalidIdentifier() {
        assertThatThrownBy(() ->
            DatasetFilterSqlBuilder.wrap(
                "SELECT * FROM orders",
                List.of(condition("missing", "EQ", "1", null)),
                Set.of("region")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在");
        assertThatThrownBy(() ->
            DatasetFilterSqlBuilder.wrap(
                "SELECT * FROM orders",
                List.of(condition("region; DROP TABLE t", "EQ", "1", null)),
                Set.of()
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不合法");
    }

    @Test
    void shouldReturnOriginalSqlWhenNoConditions() {
        assertThat(DatasetFilterSqlBuilder.wrap("SELECT 1", List.of(), Set.of())).isEqualTo("SELECT 1");
    }

    private DatasetScopeCondition condition(String field, String operator, String value, String valueEnd) {
        DatasetScopeCondition condition = new DatasetScopeCondition();
        condition.setFieldName(field);
        condition.setOperator(operator);
        condition.setValue(value);
        condition.setValueEnd(valueEnd);
        return condition;
    }
}
