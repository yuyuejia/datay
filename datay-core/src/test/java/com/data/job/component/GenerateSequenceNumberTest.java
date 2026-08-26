package com.data.job.component;

import com.data.job.Component;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.GenerateSequenceNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class GenerateSequenceNumberTest {

    private GenerateSequenceNumber component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new GenerateSequenceNumber();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-sequence");
        component.setName("GenerateSequenceNumber");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testGenerateSequenceDefault() {
        component.setStartValue("1");
        component.setCountValue("1");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateSequenceCustomRange() {
        component.setStartValue("5");
        component.setCountValue("10");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateSequenceWithLength() {
        component.setStartValue("1");
        component.setCountValue("5");
        component.setDataFormat("TEXT");
        component.setLength(5);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateSequenceTextFormat() {
        component.setStartValue("1");
        component.setCountValue("3");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateSequenceJsonArrayFormat() {
        component.setStartValue("1");
        component.setCountValue("2");
        component.setDataFormat("JSON_ARRAY");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testExecuteWithEndAttribute() {
        component.setStartValue("1");
        component.setCountValue("5");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testFormatSequenceValueWithoutLength() {
        component.setStartValue("1");
        component.setCountValue("1");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testFormatSequenceValueWithLength() {
        component.setStartValue("1");
        component.setCountValue("1");
        component.setLength(4);
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testParseNumberValue() {
        component.setStartValue("100");
        component.setCountValue("50");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(Component.ComponentType.SOURCE, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testInvalidDataFormat() {
        component.setStartValue("1");
        component.setCountValue("1");
        component.setDataFormat("INVALID_FORMAT");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        assertThrows(RuntimeException.class, () -> {
            component.execute(flowFile);
        });
    }

    @Test
    @Timeout(60000)
    public void testZeroCount() {
        component.setStartValue("1");
        component.setCountValue("0");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testNegativeStartValue() {
        component.setStartValue("-5");
        component.setCountValue("3");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }
}
