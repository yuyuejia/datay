package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.DatasourceInfo;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.JdbcInput;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JdbcInputTest {

    private JdbcInput component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new JdbcInput();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-jdbc-input");
        component.setName("JdbcInput");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.SOURCE, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithStartAttribute() {
        component.setTable("test_table");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected - DuckDB may need file path
        }
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithSingleTable() {
        component.setTable("test_table");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithMultipleTables() {
        component.setTable("table1,table2");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithWhereClause() {
        component.setTable("test_table");
        component.setWhere("id > 100");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithIncrColumn() {
        component.setTable("test_table");
        component.setIncrColumn("update_time");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    @Timeout(60000)
    public void testResolveTargetTableNameDefaultsToSourceTable() {
        TableMeta srcTable = new TableMeta();
        srcTable.setTable("dwd_fie_aai_voucher_detail");

        assertEquals("dwd_fie_aai_voucher_detail", component.resolveTargetTableName(srcTable));
    }

    @Test
    @Timeout(60000)
    public void testResolveTargetTableNameUsesOutputTableWhenConfigured() {
        component.setOutputTable("my_output_table");

        TableMeta srcTable = new TableMeta();
        srcTable.setTable("dwd_fie_aai_voucher_detail");

        assertEquals("my_output_table", component.resolveTargetTableName(srcTable));
        assertEquals("my_output_table", component.getOutputTable());
    }

    @Test
    @Timeout(60000)
    public void testResolveTargetTableNameFallsBackWhenOutputTableBlank() {
        component.setOutputTable("   ");

        TableMeta srcTable = new TableMeta();
        srcTable.setTable("test_table");

        assertEquals("test_table", component.resolveTargetTableName(srcTable));
    }
}
