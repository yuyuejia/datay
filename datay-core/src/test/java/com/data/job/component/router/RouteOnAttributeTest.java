package com.data.job.component.router;

import com.alibaba.fastjson2.JSONArray;
import com.data.job.Component.ComponentType;
import com.data.job.Connection;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RouteOnAttributeTest {

    private RouteOnAttribute component;
    private ExecutionContext context;
    private TaskLogger taskLogger;
    private LinkedBlockingQueue<Object> vipQueue;
    private LinkedBlockingQueue<Object> normalQueue;

    @BeforeEach
    public void setUp() {
        component = new RouteOnAttribute();

        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");

        taskLogger = new TaskLogger("test-job", "test-instance");

        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("route1");
        component.setName("RouteOnAttribute");
        component.setActiveThreads(new AtomicInteger(1));

        component.getOutput().add(new Connection("route1", "t1", 0, "vip"));
        component.getOutput().add(new Connection("route1", "t2", 0, "normal"));

        vipQueue = new LinkedBlockingQueue<>();
        normalQueue = new LinkedBlockingQueue<>();
        context.getConnections().put("route1_t1", vipQueue);
        context.getConnections().put("route1_t2", normalQueue);
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testMatchByAttributeName() {
        component.setMatchMode("attribute");

        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("vip", "true");
        component.execute(flowFile);

        assertEquals(1, vipQueue.size());
        assertEquals(0, normalQueue.size());
        assertNotNull(vipQueue.poll());
    }

    @Test
    @Timeout(60000)
    public void testMatchByAttributeValue() {
        component.setMatchMode("value");
        component.setAttributeName("level");

        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("level", "normal");
        component.execute(flowFile);

        assertEquals(0, vipQueue.size());
        assertEquals(1, normalQueue.size());
    }

    @Test
    @Timeout(60000)
    public void testDefaultLabelFallback() {
        component.setMatchMode("value");
        component.setAttributeName("level");
        component.setDefaultLabel("normal");

        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("level", "unknown");
        component.execute(flowFile);

        assertEquals(1, normalQueue.size());
    }

    @Test
    @Timeout(60000)
    public void testNoMatchDropped() {
        component.setMatchMode("value");
        component.setAttributeName("level");

        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("level", "unknown");
        component.execute(flowFile);

        assertEquals(0, vipQueue.size());
        assertEquals(0, normalQueue.size());
    }

    @Test
    @Timeout(60000)
    public void testEndSignalNotRouted() {
        component.setMatchMode("attribute");

        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        component.execute(flowFile);

        assertNull(vipQueue.poll());
        assertNull(normalQueue.poll());
    }

    @Test
    @Timeout(60000)
    public void testMatchByDataFieldSplitsRecords() {
        component.setMatchMode("data");
        component.setDataField("type");
        component.setDefaultLabel("other");

        Connection other = new Connection("route1", "t3", 0, "other");
        component.getOutput().add(other);
        LinkedBlockingQueue<Object> otherQueue = new LinkedBlockingQueue<>();
        context.getConnections().put("route1_t3", otherQueue);

        JSONArray array = JSONArray.parseArray(
            "[{\"id\":1,\"type\":\"vip\"},{\"id\":2,\"type\":\"normal\"},{\"id\":3,\"type\":\"x\"},{\"id\":4,\"type\":\"vip\"}]"
        );
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(array);
        component.execute(flowFile);

        assertEquals(1, vipQueue.size(), "vip 应收到 1 个 FlowFile");
        assertEquals(1, normalQueue.size(), "normal 应收到 1 个 FlowFile");
        assertEquals(1, otherQueue.size(), "other 兜底应收到 1 个 FlowFile");

        FlowFile vip = (FlowFile) vipQueue.poll();
        assertNotNull(vip);
        assertEquals(2, vip.getJsonArray().size());
        assertEquals(1, vip.getJsonArray().getJSONObject(0).getIntValue("id"));
        assertEquals(4, vip.getJsonArray().getJSONObject(1).getIntValue("id"));

        FlowFile otherOut = (FlowFile) otherQueue.poll();
        assertNotNull(otherOut);
        assertEquals(1, otherOut.getJsonArray().size());
        assertEquals(3, otherOut.getJsonArray().getJSONObject(0).getIntValue("id"));
    }
}
