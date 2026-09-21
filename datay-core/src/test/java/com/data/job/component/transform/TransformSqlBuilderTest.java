package com.data.job.component.transform;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransformSqlBuilderTest {

    private static JSONArray rules(JSONObject... items) {
        JSONArray array = new JSONArray();
        for (JSONObject item : items) {
            array.add(item);
        }
        return array;
    }

    private static JSONObject rule(String type) {
        JSONObject rule = new JSONObject();
        rule.put("type", type);
        return rule;
    }

    @Test
    public void testEmptyRulesSelectAll() {
        assertEquals("SELECT * FROM main.t", TransformSqlBuilder.buildSelectSql("main.t", new JSONArray(), null, null));
    }

    @Test
    public void testCastReplacesColumn() {
        JSONObject cast = rule("cast");
        cast.put("column", "age");
        cast.put("targetType", "INTEGER");
        assertEquals(
            "SELECT * EXCLUDE (age), CAST(age AS INTEGER) AS age FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(cast), null, null)
        );
    }

    @Test
    public void testCastSupportsPrecision() {
        JSONObject cast = rule("cast");
        cast.put("column", "amount");
        cast.put("targetColumn", "amount_dec");
        cast.put("targetType", "DECIMAL(10,2)");
        assertEquals(
            "SELECT *, CAST(amount AS DECIMAL(10,2)) AS amount_dec FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(cast), null, null)
        );
    }

    @Test
    public void testCastDecimalWithPrecisionAndScale() {
        JSONObject cast = rule("cast");
        cast.put("column", "amount");
        cast.put("targetType", "DECIMAL");
        cast.put("precision", 12);
        cast.put("scale", 4);
        assertEquals(
            "SELECT * EXCLUDE (amount), CAST(amount AS DECIMAL(12,4)) AS amount FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(cast), null, null)
        );
    }

    @Test
    public void testCastDecimalDefaultsScaleToZero() {
        JSONObject cast = rule("cast");
        cast.put("column", "amount");
        cast.put("targetType", "NUMERIC");
        cast.put("precision", 18);
        assertEquals(
            "SELECT * EXCLUDE (amount), CAST(amount AS NUMERIC(18,0)) AS amount FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(cast), null, null)
        );
    }

    @Test
    public void testUpperToNewColumn() {
        JSONObject upper = rule("upper");
        upper.put("column", "name");
        upper.put("targetColumn", "name_upper");
        assertEquals(
            "SELECT *, UPPER(name) AS name_upper FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(upper), null, null)
        );
    }

    @Test
    public void testDropAndRename() {
        JSONObject drop = rule("drop");
        drop.put("column", "secret");
        JSONObject rename = rule("rename");
        rename.put("column", "old_name");
        rename.put("targetColumn", "new_name");
        assertEquals(
            "SELECT * EXCLUDE (secret, old_name), old_name AS new_name FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(drop, rename), null, null)
        );
    }

    @Test
    public void testFilterCondition() {
        JSONObject filter = rule("filter");
        filter.put("column", "status");
        filter.put("operator", "=");
        filter.put("value", "ACTIVE");
        assertEquals(
            "SELECT * FROM main.t WHERE status = 'ACTIVE'",
            TransformSqlBuilder.buildSelectSql("main.t", rules(filter), null, null)
        );
    }

    @Test
    public void testMultipleFiltersAndGlobalFilter() {
        JSONObject f1 = rule("filter");
        f1.put("column", "age");
        f1.put("operator", ">=");
        f1.put("value", 18);
        JSONObject f2 = rule("filter");
        f2.put("column", "dept");
        f2.put("operator", "IN");
        f2.put("value", values("IT", "HR"));
        assertEquals(
            "SELECT * FROM main.t WHERE (t.tenant_id = 1) AND age >= 18 AND dept IN ('IT', 'HR')",
            TransformSqlBuilder.buildSelectSql("main.t", rules(f1, f2), null, "t.tenant_id = 1")
        );
    }

    @Test
    public void testFilterBetweenAndNull() {
        JSONObject between = rule("filter");
        between.put("column", "age");
        between.put("operator", "BETWEEN");
        between.put("value", values(18, 60));
        JSONObject isNull = rule("filter");
        isNull.put("column", "email");
        isNull.put("operator", "IS NULL");
        assertEquals(
            "SELECT * FROM main.t WHERE age BETWEEN 18 AND 60 AND email IS NULL",
            TransformSqlBuilder.buildSelectSql("main.t", rules(between, isNull), null, null)
        );
    }

    @Test
    public void testStringFunctions() {
        JSONObject replace = rule("replace");
        replace.put("column", "phone");
        replace.put("search", "-");
        replace.put("replacement", "");
        JSONObject substring = rule("substring");
        substring.put("column", "code");
        substring.put("start", 1);
        substring.put("length", 3);
        substring.put("targetColumn", "prefix");
        JSONObject concat = rule("concat");
        concat.put("column", "first_name");
        concat.put("columns", values("last_name"));
        concat.put("separator", " ");
        concat.put("targetColumn", "full_name");
        assertEquals(
            "SELECT * EXCLUDE (phone), REPLACE(phone, '-', '') AS phone, SUBSTRING(code, 1, 3) AS prefix, " +
                "CONCAT_WS(' ', first_name, last_name) AS full_name FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(replace, substring, concat), null, null)
        );
    }

    @Test
    public void testMathAndDateFunctions() {
        JSONObject round = rule("round");
        round.put("column", "amount");
        round.put("decimals", 2);
        round.put("targetColumn", "amount_round");
        JSONObject extract = rule("extract");
        extract.put("column", "create_time");
        extract.put("part", "year");
        extract.put("targetColumn", "year");
        JSONObject dateFormat = rule("date_format");
        dateFormat.put("column", "create_time");
        dateFormat.put("format", "%Y-%m-%d");
        dateFormat.put("targetColumn", "create_date");
        assertEquals(
            "SELECT *, ROUND(amount, 2) AS amount_round, DATE_PART('year', create_time) AS year, " +
                "STRFTIME(create_time, '%Y-%m-%d') AS create_date FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(round, extract, dateFormat), null, null)
        );
    }

    @Test
    public void testArithmeticWithColumn() {
        JSONObject multiply = rule("multiply");
        multiply.put("column", "quantity");
        multiply.put("value", "price");
        multiply.put("valueIsColumn", true);
        multiply.put("targetColumn", "total");
        assertEquals(
            "SELECT *, (quantity * price) AS total FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(multiply), null, null)
        );
    }

    @Test
    public void testCustomExpression() {
        JSONObject custom = rule("custom");
        custom.put("column", "price");
        custom.put("targetColumn", "price_tax");
        custom.put("expression", "{column} * 1.13");
        assertEquals(
            "SELECT *, price * 1.13 AS price_tax FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(custom), null, null)
        );
    }

    @Test
    public void testSelectColumnsOverride() {
        JSONObject upper = rule("upper");
        upper.put("column", "name");
        upper.put("targetColumn", "name_upper");
        assertEquals(
            "SELECT id, age, UPPER(name) AS name_upper FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(upper), "id, age", null)
        );
    }

    @Test
    public void testUnusualColumnNamesAreQuoted() {
        JSONObject upper = rule("upper");
        upper.put("column", "first name");
        upper.put("targetColumn", "First Name");
        assertEquals(
            "SELECT *, UPPER(\"first name\") AS \"First Name\" FROM main.t",
            TransformSqlBuilder.buildSelectSql("main.t", rules(upper), null, null)
        );
    }

    @Test
    public void testInvalidCastTypeRejected() {
        JSONObject cast = rule("cast");
        cast.put("column", "age");
        cast.put("targetType", "INTEGER); DROP TABLE main.t; --");
        assertThrows(
            IllegalArgumentException.class,
            () -> TransformSqlBuilder.buildSelectSql("main.t", rules(cast), null, null)
        );
    }

    @Test
    public void testUnknownRuleTypeRejected() {
        JSONObject unknown = rule("bogus");
        unknown.put("column", "age");
        assertThrows(
            IllegalArgumentException.class,
            () -> TransformSqlBuilder.buildSelectSql("main.t", rules(unknown), null, null)
        );
    }

    @Test
    public void testSupportedRuleTypes() {
        assertTrue(TransformSqlBuilder.supportedRuleTypes().contains("cast"));
        assertTrue(TransformSqlBuilder.supportedRuleTypes().contains("filter"));
        assertTrue(TransformSqlBuilder.supportedRuleTypes().contains("upper"));
    }

    @Test
    public void testResolveRulesFromJsonString() {
        JSONArray resolved = TransformSqlBuilder.resolveRules("[{\"type\":\"upper\",\"column\":\"name\"}]");
        assertEquals(1, resolved.size());
        assertEquals("upper", resolved.getJSONObject(0).getString("type"));
    }

    private static JSONArray values(Object... items) {
        JSONArray array = new JSONArray();
        for (Object item : items) {
            array.add(item);
        }
        return array;
    }
}
