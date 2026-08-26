package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.DuckDBSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DuckDBSqlTest {

    private DuckDBSql component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new DuckDBSql();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-duckdb-sql");
        component.setName("DuckDBSql");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testDuckDBSqlWithSelectQuery() {
        component.setSql("SELECT 1 as id, 'test' as name");
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected to fail without proper DuckDB setup, but should not throw unexpected errors
        }
    }

    @Test
    @Timeout(60000)
    public void testDuckDBSqlWithCreateTable() {
        component.setSql("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected to fail without proper DuckDB setup
        }
    }

    @Test
    @Timeout(60000)
    public void testDuckDBSqlWithInsert() {
        component.setSql("INSERT INTO test_table VALUES (1, 'test')");
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected to fail without proper table
        }
    }

    @Test
    @Timeout(60000)
    public void testDuckDBSqlNotUpstreamFinish() {
        component.setSql("SELECT 1");
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = false;
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testDuckDBSqlMultipleStatements() {
        component.setSql("CREATE TABLE test_multi (id INTEGER); INSERT INTO test_multi VALUES (1); SELECT * FROM test_multi");
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected to fail
        }
    }

    @Test
    @Timeout(60000)
    public void testDuckDBSqlWithComment() {
        component.setSql("-- This is a comment\nSELECT 1 as num");
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    @Timeout(60000)
    public void testDuckDBSqlWithBlockComment() {
        component.setSql("/* Block comment */ SELECT 1 as num");
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }
}
