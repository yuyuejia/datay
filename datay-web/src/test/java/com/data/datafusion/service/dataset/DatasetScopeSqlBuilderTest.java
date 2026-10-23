package com.data.datafusion.service.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link DatasetScopeSqlBuilder} 数据集行级权限 SQL 构建的单元测试。
 */
class DatasetScopeSqlBuilderTest {

    @Test
    void shouldReturnEmptyPredicateWithoutConditions() {
        assertThat(DatasetScopeSqlBuilder.buildPredicate(List.of(), Set.of())).isEmpty();
        assertThat(DatasetScopeSqlBuilder.wrap("SELECT * FROM orders", List.of(), Set.of())).isEqualTo("SELECT * FROM orders");
    }

    @Test
    void shouldCombineConditionsWithOr() {
        String predicate = DatasetScopeSqlBuilder.buildPredicate(
            List.of(condition("region", "EQ", "华东", null), condition("amount", "GT", "100", null)),
            Set.of("region", "amount")
        );

        assertThat(predicate).isEqualTo("((region = '华东') OR (amount > 100))");
    }

    @Test
    void shouldEscapeQuotesAndSupportInAndBetween() {
        String predicate = DatasetScopeSqlBuilder.buildPredicate(
            List.of(condition("region", "IN", "华东,华南", null), condition("amount", "BETWEEN", "1", "9")),
            Set.of("region", "amount")
        );

        assertThat(predicate).contains("region IN ('华东', '华南')");
        assertThat(predicate).contains("amount BETWEEN 1 AND 9");
    }

    @Test
    void shouldWrapBaseSqlAndStripTrailingSemicolon() {
        String wrapped = DatasetScopeSqlBuilder.wrap(
            "SELECT region, amount FROM orders;",
            List.of(condition("region", "EQ", "O'Reilly", null)),
            Set.of("region", "amount")
        );

        assertThat(wrapped).isEqualTo("SELECT * FROM (SELECT region, amount FROM orders) dp_dataset WHERE ((region = 'O''Reilly'))");
    }

    @Test
    void shouldRejectUnknownField() {
        assertThatThrownBy(() ->
                DatasetScopeSqlBuilder.buildPredicate(List.of(condition("unknown", "EQ", "x", null)), Set.of("region"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据集权限字段不存在");
    }

    @Test
    void shouldRejectIllegalIdentifier() {
        assertThatThrownBy(() -> DatasetScopeSqlBuilder.buildPredicate(List.of(condition("region; DROP TABLE t", "EQ", "x", null)), Set.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("字段名不合法");
    }

    @Test
    void shouldRejectInvalidOperator() {
        assertThatThrownBy(() -> DatasetScopeSqlBuilder.buildPredicate(List.of(condition("region", "BAD", "x", null)), Set.of("region")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的数据集权限运算符");
    }

    private DatasetScopeCondition condition(String fieldName, String operator, String value, String valueEnd) {
        DatasetScopeCondition condition = new DatasetScopeCondition();
        condition.setFieldName(fieldName);
        condition.setOperator(operator);
        condition.setValue(value);
        condition.setValueEnd(valueEnd);
        return condition;
    }
}
