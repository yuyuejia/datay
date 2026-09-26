package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 空筛选条件占位符中和逻辑测试：筛选器未设值时应等价于「不过滤，取全部」。
 */
class DashboardDataServiceTest {

    @Test
    void shouldNeutralizeEmptyInPredicate() {
        String result = DashboardDataService.neutralizeEmptyParams(
            "SELECT * FROM t WHERE 1=1 AND region IN (${region})",
            new LinkedHashMap<>()
        );
        assertThat(result).doesNotContain("${region}");
        assertThat(result).contains("1=1");
    }

    @Test
    void shouldNeutralizeEmptyEqualityAndLikePredicates() {
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE city = ${city}", new LinkedHashMap<>())).contains("1=1");
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE name LIKE ${kw}", new LinkedHashMap<>())).contains("1=1");
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE amt >= ${min}", new LinkedHashMap<>())).contains("1=1");
    }

    @Test
    void shouldNeutralizeBetweenPredicateBothBounds() {
        String result = DashboardDataService.neutralizeEmptyParams(
            "WHERE order_date BETWEEN ${start} AND ${end}",
            new LinkedHashMap<>()
        );
        assertThat(result).doesNotContain("${start}").doesNotContain("${end}");
        assertThat(result).contains("1=1");
    }

    @Test
    void shouldNeutralizeQuotedEmptyPredicate() {
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE d = '${start}'", new LinkedHashMap<>())).contains("1=1");
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE region IN ('${region}')", new LinkedHashMap<>())).contains("1=1");
    }

    @Test
    void shouldKeepValuedPlaceholderUntouched() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("region", List.of("华东", "华南"));
        String result = DashboardDataService.neutralizeEmptyParams(
            "SELECT * FROM t WHERE region IN (${region})",
            named
        );
        assertThat(result).contains("${region}");
    }

    @Test
    void shouldRenderListInsideInClause() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("region", List.of("华东", "华南"));
        String result = DashboardDataService.renderSql("WHERE region IN (${region})", named);
        assertThat(result).isEqualTo("WHERE region IN ('华东', '华南')");
    }

    @Test
    void shouldRenderPlaceholderInsideWildcardQuotes() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("kw", "东");
        String result = DashboardDataService.renderSql("WHERE name LIKE '%${kw}%'", named);
        assertThat(result).isEqualTo("WHERE name LIKE '%东%'");
    }

    @Test
    void shouldRenderQuotedDateAndNumericLiteral() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("start", "2024-01-01");
        named.put("min", 100);
        assertThat(DashboardDataService.renderSql("WHERE d = '${start}'", named)).isEqualTo("WHERE d = '2024-01-01'");
        assertThat(DashboardDataService.renderSql("WHERE amt >= ${min}", named)).isEqualTo("WHERE amt >= 100");
    }

    @Test
    void shouldEscapeSingleQuoteToPreventInjection() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("city", "x' OR '1'='1");
        String result = DashboardDataService.renderSql("WHERE city = ${city}", named);
        assertThat(result).isEqualTo("WHERE city = 'x'' OR ''1''=''1'");
    }
}
