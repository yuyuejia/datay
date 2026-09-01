package com.data.job.component;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书多维表格输入组件测试
 * 使用系统属性可覆盖默认的飞书应用凭证与表格标识：
 *   -Dfeishu.appId=... -Dfeishu.appSecret=... -Dfeishu.appToken=... -Dfeishu.tableId=...
 */
public class FeishuBitableInputTest {

    private static final String APP_ID = System.getProperty("feishu.appId", "cli_aa1c93b88bf8dcb0");
    private static final String APP_SECRET = System.getProperty("feishu.appSecret", "");
    private static final String APP_TOKEN = System.getProperty("feishu.appToken", "EEmSbfNF8aD2RcsP26CcU3Bvnee");
    private static final String TABLE_ID = System.getProperty("feishu.tableId", "tblpjHsRMt8moPj8");

    private FeishuBitableInput component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new FeishuBitableInput();
        component.setAppId(APP_ID);
        component.setAppSecret(APP_SECRET);
        component.setAppToken(APP_TOKEN);
        component.setTableId(TABLE_ID);

        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");

        taskLogger = new TaskLogger("test-job", "test-instance");

        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("feishu-input");
        component.setName("FeishuBitableInput");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.SOURCE, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testGetTenantAccessToken() throws Exception {
        String token = component.getTenantAccessToken();
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @Timeout(60000)
    public void testFetchFieldNames() throws Exception {
        List<String> fieldNames = component.fetchFieldNames();
        assertNotNull(fieldNames);
        assertFalse(fieldNames.isEmpty());
        assertTrue(fieldNames.contains("文本"));
    }

    @Test
    @Timeout(60000)
    public void testFetchAllRecords() throws Exception {
        List<FeishuBitableInput.BitableRecord> records = component.fetchAllRecords();
        assertNotNull(records);
        assertFalse(records.isEmpty());
        assertEquals(10, records.size());

        JSONObject first = records.get(0).getData();
        assertNotNull(first.getString("record_id"));
        assertTrue(first.containsKey("文本"));
        assertTrue(first.containsKey("单选"));
        assertTrue(first.containsKey("created_time"));
        assertTrue(first.containsKey("last_modified_time"));
    }

    @Test
    public void testFlattenValue() {
        assertEquals("hello", component.flattenValue("hello"));
        assertEquals(123, component.flattenValue(123));
        assertEquals(true, component.flattenValue(true));

        JSONObject user = new JSONObject();
        user.put("id", "ou_xxx");
        user.put("name", "张三");
        assertEquals("张三", component.flattenValue(user));

        JSONObject url = new JSONObject();
        url.put("link", "https://example.com");
        url.put("text", "示例链接");
        assertEquals("示例链接", component.flattenValue(url));

        JSONArray multi = new JSONArray();
        multi.add("a");
        multi.add("b");
        multi.add("c");
        assertEquals("a, b, c", component.flattenValue(multi));

        JSONArray single = new JSONArray();
        single.add("only");
        assertEquals("only", component.flattenValue(single));
    }

    @Test
    @Timeout(60000)
    public void testExecute() throws Exception {
        LinkedBlockingQueue<Object> queue = setupOutput();
        component.setPageSize(5);

        component.execute(createStartFlowFile());

        assertEquals(10, countRecords(queue));
    }

    @Test
    @Timeout(60000)
    public void testIncrementalSync() throws Exception {
        component.setIncrColumn("last_modified_time");
        LinkedBlockingQueue<Object> queue = setupOutput();

        // 首次执行：全量同步
        component.execute(createStartFlowFile());
        String statusKey = "feishu-input.lastIncrValue." + TABLE_ID;
        Object[] result = drain(queue, statusKey);
        int firstCount = (Integer) result[0];
        String status = (String) result[1];
        assertEquals(10, firstCount);
        assertNotNull(status);

        // 第二次执行：无新数据，应同步 0 条
        context.put(statusKey, status);
        component.execute(createStartFlowFile());
        Object[] second = drain(queue, statusKey);
        assertEquals(0, (Integer) second[0]);
    }

    private LinkedBlockingQueue<Object> setupOutput() {
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        context.getConnections().put("feishu-input_sink", queue);
        Connection connection = new Connection("feishu-input", "sink", 0);
        List<Connection> outputs = new ArrayList<>();
        outputs.add(connection);
        component.setOutput(outputs);
        return queue;
    }

    private FlowFile createStartFlowFile() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        return flowFile;
    }

    private int countRecords(LinkedBlockingQueue<Object> queue) {
        return (Integer) drain(queue, null)[0];
    }

    private Object[] drain(LinkedBlockingQueue<Object> queue, String statusKey) {
        int totalRecords = 0;
        String status = null;
        FlowFile flowFile;
        while ((flowFile = (FlowFile) queue.poll()) != null) {
            JSONArray records = flowFile.getJsonArray();
            assertNotNull(records);
            totalRecords += records.size();
            assertEquals("feishu_" + TABLE_ID, flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE));
            if (statusKey != null) {
                Object s = flowFile.getStatusMap().get(statusKey);
                if (s != null) {
                    status = String.valueOf(s);
                }
            }
        }
        return new Object[]{totalRecords, status};
    }
}
