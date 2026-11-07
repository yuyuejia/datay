package com.data.job.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataQualityRuleCompilerTest {

    private final DataQualityRuleCompiler compiler = new DataQualityRuleCompiler();

    @Test
    void shouldCompileAndRunFieldRules() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            execute(
                connection,
                "CREATE TABLE dq_source (id BIGINT, name VARCHAR, amount DOUBLE, status VARCHAR, code VARCHAR, created_at TIMESTAMP);" +
                "INSERT INTO dq_source VALUES " +
                "(1, 'Alice', 10, 'ACTIVE', 'A-1', now())," +
                "(2, '  ', -5, 'INACTIVE', 'bad', now() - INTERVAL '48 hours')," +
                "(3, NULL, 100, 'UNKNOWN', 'B-2', now())"
            );

            List<CompiledQualityRule> rules = compiler.compile(
                "[" +
                "{\"ruleCode\":\"not_null\",\"ruleName\":\"name非空\",\"ruleType\":\"NOT_NULL\",\"column\":\"name\",\"config\":{\"trimWhitespace\":true}}," +
                "{\"ruleCode\":\"unique\",\"ruleName\":\"id唯一\",\"ruleType\":\"UNIQUE\",\"column\":\"id\"}," +
                "{\"ruleCode\":\"range\",\"ruleName\":\"金额范围\",\"ruleType\":\"RANGE\",\"column\":\"amount\",\"config\":{\"valueType\":\"NUMBER\",\"min\":0,\"max\":50}}," +
                "{\"ruleCode\":\"enum\",\"ruleName\":\"状态枚举\",\"ruleType\":\"ENUM\",\"column\":\"status\",\"config\":{\"allowedValues\":[\"ACTIVE\",\"INACTIVE\"]}}," +
                "{\"ruleCode\":\"regex\",\"ruleName\":\"编码格式\",\"ruleType\":\"REGEX\",\"column\":\"code\",\"config\":{\"pattern\":\"^[A-Z]-[0-9]+$\"}}," +
                "{\"ruleCode\":\"length\",\"ruleName\":\"名称长度\",\"ruleType\":\"LENGTH\",\"column\":\"name\",\"config\":{\"minLength\":1,\"maxLength\":3}}," +
                "{\"ruleCode\":\"fresh\",\"ruleName\":\"数据时效\",\"ruleType\":\"FRESHNESS\",\"column\":\"created_at\",\"config\":{\"maxAgeHours\":24}}" +
                "]",
                "main.dq_source"
            );

            assertEquals(7, rules.size());
            assertCounts(connection, rules.get(0), 3, 2);
            assertCounts(connection, rules.get(1), 3, 0);
            assertCounts(connection, rules.get(2), 3, 2);
            assertCounts(connection, rules.get(3), 3, 1);
            assertCounts(connection, rules.get(4), 3, 1);
            assertCounts(connection, rules.get(5), 3, 1);
            assertCounts(connection, rules.get(6), 3, 1);
        }
    }

    @Test
    void shouldCompileAndRunTableRules() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            execute(
                connection,
                "CREATE TABLE dq_source (id BIGINT, seq BIGINT);" +
                "INSERT INTO dq_source VALUES (1, 1), (1, 2), (2, 1)"
            );
            List<CompiledQualityRule> rules = compiler.compile(
                "[" +
                "{\"ruleCode\":\"count\",\"ruleType\":\"ROW_COUNT_MIN\",\"config\":{\"minRows\":3}}," +
                "{\"ruleCode\":\"range\",\"ruleType\":\"ROW_COUNT_RANGE\",\"config\":{\"minRows\":1,\"maxRows\":2}}," +
                "{\"ruleCode\":\"composite\",\"ruleType\":\"COMPOSITE_UNIQUE\",\"columns\":[\"id\",\"seq\"]}," +
                "{\"ruleCode\":\"custom\",\"ruleType\":\"CUSTOM_SQL\",\"config\":{\"sql\":\"SELECT COUNT(*) AS invalid_rows FROM dq_source WHERE id = 1\"}}" +
                "]",
                "main.dq_source"
            );

            assertCounts(connection, rules.get(0), 3, 0);
            assertCounts(connection, rules.get(1), 3, 1);
            assertCounts(connection, rules.get(2), 3, 0);
            assertCounts(connection, rules.get(3), 3, 2);
        }
    }

    @Test
    void shouldRejectMultiStatementCustomSql() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            compiler.compile(
                "[{\"ruleCode\":\"bad\",\"ruleType\":\"CUSTOM_SQL\",\"config\":{\"sql\":\"SELECT 1; DROP TABLE x\"}}]",
                "main.dq_source"
            )
        );
        assertTrue(exception.getMessage().contains("仅支持单条 SELECT"));
    }

    private void assertCounts(Connection connection, CompiledQualityRule rule, long checkedRows, long invalidRows) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(rule.getCheckSql())) {
            assertTrue(rs.next());
            assertEquals(checkedRows, rs.getLong("checked_rows"));
            assertEquals(invalidRows, rs.getLong("invalid_rows"));
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
