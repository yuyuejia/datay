package com.data.job.component;

import com.data.job.Component;
import com.data.job.ComponentFactory;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    public void testMaskSensitiveSql() {
        assertEquals("***（包含敏感凭证的 SQL 已隐藏）***", DuckDBSql.maskSensitiveSql("CREATE SECRET s (TYPE s3, SECRET 'abc')"));
        assertEquals("***（包含敏感凭证的 SQL 已隐藏）***", DuckDBSql.maskSensitiveSql("SET s3_secret_access_key='abc'"));
        assertEquals("COPY (SELECT * FROM dc_result) TO 'x.csv' (FORMAT CSV, HEADER);", DuckDBSql.maskSensitiveSql("COPY (SELECT * FROM dc_result) TO 'x.csv' (FORMAT CSV, HEADER);"));
    }

    @Test
    public void testExecuteCopyStatementsToLocalFile(@TempDir Path tempDir) throws Exception {
        String instanceCode = "duckdb-sql-copy-" + System.currentTimeMillis();
        Path output = tempDir.resolve("diff.csv");
        DuckDBSql component = new DuckDBSql();
        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode(instanceCode);
        context.setJobCode("test-job");
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("test-job", instanceCode));
        component.setId("export_sql");
        component.setName("DuckDBSql");
        component.setActiveThreads(new AtomicInteger(1));
        component.upstreamFinish = true;
        component.setSql(
            "CREATE TABLE dc_result (diff_type VARCHAR, key__id BIGINT);" +
            "INSERT INTO dc_result VALUES ('VALUE_DIFF', 1);" +
            "COPY (SELECT * FROM dc_result) TO '" + output.toString().replace("'", "''") + "' (FORMAT CSV, HEADER);"
        );
        try {
            component.execute(new FlowFile());
            assertTrue(Files.exists(output));
            assertTrue(Files.readString(output).contains("VALUE_DIFF"));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
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
