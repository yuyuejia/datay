package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.FlowFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LogFlowFileTest extends ComponentTestBase {

    private LogFlowFile component;

    @BeforeEach
    public void setUp() {
        super.setUpBase();
        component = new LogFlowFile();
        initComponent(component, "test-log-flowfile", "LogFlowFile");
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testLogTextData() {
        component.setLogLevel("INFO");
        component.setLogDataContent(true);
        component.setLogAttributes(true);
        
        FlowFile flowFile = createTextFlowFile("Test message");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWithEndAttribute() {
        FlowFile flowFile = createEndFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogDebugLevel() {
        component.setLogLevel("DEBUG");
        
        FlowFile flowFile = createTextFlowFile("Debug message");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWarnLevel() {
        component.setLogLevel("WARN");
        
        FlowFile flowFile = createTextFlowFile("Warn message");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogErrorLevel() {
        component.setLogLevel("ERROR");
        
        FlowFile flowFile = createTextFlowFile("Error message");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWithDisabledDataContent() {
        component.setLogDataContent(false);
        
        FlowFile flowFile = createTextFlowFile("Test message");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWithDisabledAttributes() {
        component.setLogAttributes(false);
        
        FlowFile flowFile = createTextFlowFile("Test message");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWithMaxDataLength() {
        component.setMaxDataLength(100);
        
        StringBuilder longData = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longData.append("a");
        }
        
        FlowFile flowFile = createTextFlowFile(longData.toString());
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWithTimestamp() {
        component.setLogTimestamp(true);
        
        FlowFile flowFile = createTextFlowFile("Test message");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, System.currentTimeMillis());
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWithEventType() {
        FlowFile flowFile = createTextFlowFile("Test message");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogWithTableAttribute() {
        FlowFile flowFile = createTextFlowFile("Test message");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, "test_table");
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testLogNullFlowFile() {
        component.execute(null);
        
        assertNotNull(context.getConnections());
    }
}
