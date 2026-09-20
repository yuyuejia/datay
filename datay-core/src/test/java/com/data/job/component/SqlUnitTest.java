package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component;
import com.data.job.ComponentFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlUnitTest {

    @Test
    public void testSetSqlWithPlainString() {
        SqlUnit unit = new SqlUnit();
        unit.setSql("SELECT * FROM dwd_fie_aai_voucher_detail");

        JSONObject sql = unit.resolveSqlConfig();
        assertNotNull(sql);
        assertEquals("SELECT * FROM dwd_fie_aai_voucher_detail", sql.getString("query"));
        assertNull(sql.getJSONObject("tablemap"));
    }

    @Test
    public void testSetSqlWithLegacyObject() {
        SqlUnit unit = new SqlUnit();

        JSONObject tableMap = new JSONObject();
        tableMap.put("TmpView_x_0", "t");
        JSONObject config = new JSONObject();
        config.put("query", "SELECT * FROM t");
        config.put("tablemap", tableMap);

        unit.setSql(config);

        assertEquals("SELECT * FROM t", unit.resolveSqlConfig().getString("query"));
        assertEquals("t", unit.resolveSqlConfig().getJSONObject("tablemap").getString("TmpView_x_0"));
    }

    @Test
    public void testFactoryBindsPlainStringSql() {
        Map<String, Object> params = new HashMap<>();
        params.put(".id", "su1");
        params.put(".name", "SqlUnit");
        params.put("sql", "SELECT 1");

        Component component = ComponentFactory.create("SqlUnit", params);

        assertTrue(component instanceof SqlUnit);
        JSONObject sql = ((SqlUnit) component).resolveSqlConfig();
        assertNotNull(sql, "纯字符串 sql 配置应能注入，不应为空");
        assertEquals("SELECT 1", sql.getString("query"));
    }
}
