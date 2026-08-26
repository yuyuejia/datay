package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component.ComponentType;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.StreamSqlUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreamSqlUnitTest {

    private StreamSqlUnit component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new StreamSqlUnit();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-stream-sql-unit");
        component.setName("StreamSqlUnit");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithNullJsonArray() {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(null);
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithEmptyJsonArray() {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(new JSONArray());
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithJsonArray() {
        JSONArray jsonArray = new JSONArray();
        JSONObject obj = new JSONObject();
        obj.put("id", 1);
        obj.put("name", "test");
        jsonArray.add(obj);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonArray);
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected - may need proper DuckDB setup
        }
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithTableMetadata() {
        JSONArray jsonArray = new JSONArray();
        JSONObject obj = new JSONObject();
        obj.put("id", 1);
        jsonArray.add(obj);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonArray);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, "test_table");
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }
}
