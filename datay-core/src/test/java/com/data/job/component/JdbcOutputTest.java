package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.DatasourceInfo;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.JdbcOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JdbcOutputTest {

    private JdbcOutput component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new JdbcOutput();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-jdbc-output");
        component.setName("JdbcOutput");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.SINK, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testExecuteNotUpstreamFinish() {
        component.setTable("test_table");
        component.setSchema("test_schema");
        component.setModel("append");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
        component.setSourceId(ds);
        
        FlowFile flowFile = new FlowFile();
        component.upstreamFinish = false;
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithAppendMode() {
        component.setTable("test_table");
        component.setSchema("test_schema");
        component.setModel("append");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
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
    public void testExecuteWithOverwriteMode() {
        component.setTable("test_table");
        component.setSchema("test_schema");
        component.setModel("overwrite");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
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
    public void testExecuteWithUpdateMode() {
        component.setTable("test_table");
        component.setSchema("test_schema");
        component.setModel("update");
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
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
    public void testExecuteWithHeaderMap() {
        component.setTable("test_table");
        component.setSchema("test_schema");
        component.setModel("append");
        
        List<Map<String, Object>> headerMap = new ArrayList<>();
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("source", "id");
        mapping.put("target", "user_id");
        headerMap.add(mapping);
        component.setHeader_map(headerMap);
        
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl("jdbc:duckdb:");
        ds.setUsername("");
        ds.setPassword("");
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
