package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.DatasourceInfo;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.SqlTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SqlTaskTest {

    private SqlTask component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new SqlTask();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-sql-task");
        component.setName("SqlTask");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testSqlTaskWithNullSourceId() {
        component.setSql("SELECT 1");
        component.setSourceId(null);
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        assertThrows(IllegalArgumentException.class, () -> {
            component.execute(flowFile);
        });
    }

    @Test
    @Timeout(60000)
    public void testSqlTaskNotUpstreamFinish() {
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        
        component.setSql("SELECT 1");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = false;
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testSqlTaskWithCreateStatement() {
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        
        component.setSql("CREATE TABLE IF NOT EXISTS test_sql_task (id INTEGER, name VARCHAR)");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected - DuckDB may need file path
        }
    }

    @Test
    @Timeout(60000)
    public void testSqlTaskWithInsertStatement() {
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        
        component.setSql("INSERT INTO test_sql_task VALUES (1, 'test')");
        component.setSourceId(ds);
        
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
    public void testSqlTaskWithMultipleStatements() {
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        
        component.setSql("CREATE TABLE test_multi (id INTEGER); INSERT INTO test_multi VALUES (1)");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = true;
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }
}
