package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.FlowFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class GenerateFlowFileTest extends ComponentTestBase {

    private GenerateFlowFile component;

    @BeforeEach
    public void setUp() {
        super.setUpBase();
        component = new GenerateFlowFile();
        initComponent(component, "test-generate-flowfile", "GenerateFlowFile");
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.SOURCE, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testGenerateTextFormat() {
        component.setInputData("Hello World");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateTextFormatWithParameter() {
        component.setInputData("#{NOW}");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateCsvFormat() {
        component.setInputData("name,age\nJohn,30\nJane,25");
        component.setDataFormat("CSV");
        component.setDelimiter(",");
        component.setHasHeader(true);
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateCsvFormatNoHeader() {
        component.setInputData("John,30\nJane,25");
        component.setDataFormat("CSV");
        component.setDelimiter(",");
        component.setHasHeader(false);
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateJsonArrayFormat() {
        component.setInputData("[{\"name\":\"John\",\"age\":30},{\"name\":\"Jane\",\"age\":25}]");
        component.setDataFormat("JSON_ARRAY");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateJsonArrayFormatFromLines() {
        component.setInputData("John\nJane\nMike");
        component.setDataFormat("JSON_ARRAY");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateJsonObjectFormat() {
        component.setInputData("{\"name\":\"John\",\"age\":30}");
        component.setDataFormat("JSON_OBJECT");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateJsonObjectFormatWithFieldMapping() {
        component.setInputData("John,30");
        component.setDataFormat("JSON_OBJECT");
        component.setFieldMapping("name,age");
        component.setDelimiter(",");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateWithAttribute() {
        component.setInputData("test data");
        component.setDataFormat("TEXT");
        component.setAttribute("{\"customAttr\":\"customValue\"}");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateWithLoopCount() {
        component.setInputData("loop data");
        component.setDataFormat("TEXT");
        component.setLoopCount(3);
        component.setWaitTime(10);
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateEmptyInputData() {
        component.setInputData("");
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testGenerateNullInputData() {
        component.setInputData(null);
        component.setDataFormat("TEXT");
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }

    @Test
    @Timeout(60000)
    public void testInvalidDataFormat() {
        component.setInputData("test");
        component.setDataFormat("INVALID_FORMAT");
        
        FlowFile flowFile = createStartFlowFile();
        
        assertThrows(RuntimeException.class, () -> {
            component.execute(flowFile);
        });
    }

    @Test
    @Timeout(60000)
    public void testCustomDelimiter() {
        component.setInputData("name;age\nJohn;30\nJane;25");
        component.setDataFormat("CSV");
        component.setDelimiter(";");
        component.setHasHeader(true);
        
        FlowFile flowFile = createStartFlowFile();
        component.execute(flowFile);
        
        assertNotNull(context.getConnections());
    }
}
