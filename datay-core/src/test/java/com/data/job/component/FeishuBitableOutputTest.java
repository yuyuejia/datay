package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component.ComponentType;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书多维表格输出组件测试
 */
public class FeishuBitableOutputTest {

    private static final String APP_ID = System.getProperty("feishu.appId", "cli_aa1c93b88bf8dcb0");
    private static final String APP_SECRET = System.getProperty("feishu.appSecret", "");
    private static final String APP_TOKEN = System.getProperty("feishu.appToken", "EEmSbfNF8aD2RcsP26CcU3Bvnee");
    private static final String TABLE_ID = System.getProperty("feishu.tableId", "tblpjHsRMt8moPj8");

    private FeishuBitableOutput component;

    @BeforeEach
    public void setUp() {
        component = new FeishuBitableOutput();
        component.setAppId(APP_ID);
        component.setAppSecret(APP_SECRET);
        component.setAppToken(APP_TOKEN);
        component.setTableId(TABLE_ID);

        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        TaskLogger taskLogger = new TaskLogger("test-job", "test-instance");

        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("feishu-output");
        component.setName("FeishuBitableOutput");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    public void testComponentType() {
        assertEquals(ComponentType.SINK, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testGetTenantAccessToken() throws Exception {
        String token = component.getTenantAccessToken();
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    public void testBuildFields() {
        JSONObject record = new JSONObject();
        record.put("record_id", "recxxx");
        record.put("文本", "hello");
        record.put("单选", "11");
        record.put("日期", 1788192000000L);
        record.put("created_time", 1766501898000L);
        record.put("last_modified_time", 1788227897000L);

        component.setExcludeFields("created_time,last_modified_time");

        JSONObject fields = component.buildFields(record);
        assertFalse(fields.containsKey("record_id"));
        assertFalse(fields.containsKey("created_time"));
        assertFalse(fields.containsKey("last_modified_time"));
        assertTrue(fields.containsKey("文本"));
        assertTrue(fields.containsKey("单选"));
        assertTrue(fields.containsKey("日期"));
        assertEquals("hello", fields.getString("文本"));
    }

    @Test
    public void testBuildFieldsNoExclude() {
        JSONObject record = new JSONObject();
        record.put("文本", "hello");
        record.put("单选", "11");

        JSONObject fields = component.buildFields(record);
        assertEquals(2, fields.size());
        assertTrue(fields.containsKey("文本"));
        assertTrue(fields.containsKey("单选"));
    }
}
