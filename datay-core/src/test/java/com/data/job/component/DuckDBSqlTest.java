package com.data.job.component;

import com.data.job.Component;
import com.data.job.ComponentFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DuckDBSqlTest {

    @Test
    public void testResolveOutputTableDefaults() {
        DuckDBSql component = new DuckDBSql();
        assertEquals("duckdb_query_result", component.resolveOutputTable());
    }

    @Test
    public void testResolveOutputTableUsesConfiguredValue() {
        DuckDBSql component = new DuckDBSql();
        component.setOutputTable("my_result");
        assertEquals("my_result", component.resolveOutputTable());
        assertEquals("my_result", component.getOutputTable());
    }

    @Test
    public void testResolveOutputTableFallsBackWhenBlank() {
        DuckDBSql component = new DuckDBSql();
        component.setOutputTable("   ");
        assertEquals("duckdb_query_result", component.resolveOutputTable());
    }

    @Test
    public void testFactoryBindsOutputTable() {
        Map<String, Object> params = new HashMap<>();
        params.put(".id", "sql1");
        params.put(".name", "DuckDBSql");
        params.put("sql", "SELECT 1");
        params.put("outputTable", "configured_result");

        Component component = ComponentFactory.create("DuckDBSql", params);

        assertTrue(component instanceof DuckDBSql);
        assertEquals("configured_result", ((DuckDBSql) component).resolveOutputTable());
    }
}
