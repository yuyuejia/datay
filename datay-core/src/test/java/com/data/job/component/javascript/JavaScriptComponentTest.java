package com.data.job.component.javascript;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component.ComponentType;
import com.data.job.Connection;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaScriptComponentTest {

    private static final String COMPONENT_ID = "test-javascript";
    private static final String SINK_ID = "test-sink";

    private JavaScriptComponent component;
    private ExecutionContext context;
    private LinkedBlockingQueue<Object> outputQueue;

    @BeforeEach
    public void setUp() {
        component = new JavaScriptComponent();

        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");

        component.setContext(context);
        component.setTaskLogger(new TaskLogger("test-job", "test-instance"));
        component.setId(COMPONENT_ID);
        component.setName("JavaScriptComponent");
        component.setActiveThreads(new AtomicInteger(1));

        component.setOutput(List.of(new Connection(COMPONENT_ID, SINK_ID, 0)));
        outputQueue = new LinkedBlockingQueue<>();
        context.getConnections().put(COMPONENT_ID + "_" + SINK_ID, outputQueue);
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testDefaultScriptProcessesJsonArrayForInsert() throws Exception {
        FlowFile flowFile = new FlowFile();
        JSONArray records = new JSONArray();
        JSONObject record = new JSONObject();
        record.put("order_amount", 100.0);
        records.add(record);
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");

        component.execute(flowFile);

        FlowFile result = (FlowFile) outputQueue.poll();
        assertNotNull(result);
        assertNotNull(result.getJsonArray());
        assertTrue(result.getJsonArray().getJSONObject(0).getBooleanValue("processed"));
    }

    @Test
    @Timeout(60000)
    public void testDefaultScriptHandlesUpdateBeforeRecord() throws Exception {
        FlowFile flowFile = new FlowFile();
        JSONArray records = new JSONArray();
        JSONObject record = new JSONObject();
        JSONObject before = new JSONObject();
        before.put("order_amount", 50.0);
        record.put("__before", before);
        records.add(record);
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "UPDATE");

        component.execute(flowFile);

        FlowFile result = (FlowFile) outputQueue.poll();
        assertNotNull(result);
        JSONArray processed = result.getJsonArray();
        assertNotNull(processed);
        assertEquals(2, processed.size());
    }

    @Test
    @Timeout(60000)
    public void testCustomJsonScript() throws Exception {
        component.setScriptCode(
            "import com.data.job.FlowFile;\n" +
                "import com.data.job.component.javascript.ScriptContext.LogFunction;\n" +
                "import com.alibaba.fastjson2.JSONObject;\n" +
                "import java.util.Map;\n\n" +
                "public class UserScript {\n" +
                "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
                "        LogFunction log = (LogFunction) context.get(\"log\");\n" +
                "        if (flowFile.getJsonObject() != null) {\n" +
                "            flowFile.getJsonObject().put(\"processed\", true);\n" +
                "            log.info(\"json object processed\");\n" +
                "        }\n" +
                "        return flowFile;\n" +
                "    }\n" +
                "}"
        );

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonObject(new JSONObject());
        component.execute(flowFile);

        FlowFile result = (FlowFile) outputQueue.poll();
        assertNotNull(result);
        assertTrue(result.getJsonObject().getBooleanValue("processed"));
    }

    @Test
    @Timeout(60000)
    public void testInvalidScriptFailsTask() {
        component.setScriptCode("this is not valid java code");

        FlowFile flowFile = new FlowFile();
        flowFile.setTextData("hello");
        assertThrows(RuntimeException.class, () -> component.execute(flowFile));
    }

    @Test
    @Timeout(60000)
    public void testExampleScriptsCompile() throws Exception {
        JavaScriptEngine engine = new JavaScriptEngine();
        engine.compileScript("UserScript", JavaScriptComponentExample.simpleAttributeScript());
        engine.compileScript("UserScript", JavaScriptComponentExample.jsonDataProcessingScript());
        engine.compileScript("UserScript", JavaScriptComponentExample.complexTransformationScript());
        engine.compileScript("UserScript", JavaScriptComponentExample.addFieldWithMetadataScript());
    }

    @Test
    @Timeout(60000)
    public void testSwitchOnEnumScriptExecutes() throws Exception {
        // 回归：switch-on-enum 会生成辅助类 UserScript$1，必须与主类一同加载，
        // 否则运行时抛 NoClassDefFoundError: UserScript$1。
        component.setScriptCode(JavaScriptComponentExample.complexTransformationScript());

        FlowFile flowFile = new FlowFile();
        JSONArray records = new JSONArray();
        records.add(new JSONObject());
        flowFile.setJsonArray(records);

        component.execute(flowFile);

        FlowFile result = (FlowFile) outputQueue.poll();
        assertNotNull(result);
        assertNotNull(result.getJsonArray());
    }

    @Test
    @Timeout(60000)
    public void testValidateJavaCode() {
        component.setScriptCode(
            "import com.data.job.FlowFile;\n" +
                "import java.util.Map;\n" +
                "public class UserScript {\n" +
                "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
                "        return flowFile;\n" +
                "    }\n" +
                "}"
        );
        assertTrue(component.validateJavaCode());
    }
}
