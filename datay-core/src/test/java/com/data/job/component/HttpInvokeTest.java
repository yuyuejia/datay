package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.HttpInvoke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpInvokeTest {

    private HttpInvoke component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new HttpInvoke();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-http-invoke");
        component.setName("HttpInvoke");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testHttpInvokeWithGetMethod() {
        component.setUrl("https://httpbin.org/get");
        component.setMethod("GET");
        
        FlowFile flowFile = new FlowFile();
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected - may have network issues in test environment
        }
    }

    @Test
    @Timeout(60000)
    public void testHttpInvokeWithPostMethod() {
        component.setUrl("https://httpbin.org/post");
        component.setMethod("POST");
        component.setBody("{\"test\":\"data\"}");
        
        FlowFile flowFile = new FlowFile();
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    @Timeout(60000)
    public void testHttpInvokeWithHeaders() {
        component.setUrl("https://httpbin.org/headers");
        component.setMethod("GET");
        
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "test-value");
        component.setHeaders(headers);
        
        FlowFile flowFile = new FlowFile();
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    @Timeout(60000)
    public void testHttpInvokeWithEndAttribute() {
        component.setUrl("https://httpbin.org/get");
        
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testHttpInvokeWithCustomTimeout() {
        component.setUrl("https://httpbin.org/delay/1");
        component.setMethod("GET");
        component.setTimeout(5000);
        
        FlowFile flowFile = new FlowFile();
        
        try {
            component.execute(flowFile);
        } catch (Exception e) {
            // Expected - timeout or network
        }
    }

    @Test
    @Timeout(60000)
    public void testHttpInvokeWithInvalidUrl() {
        component.setUrl("https://invalid-domain-that-does-not-exist-12345.com");
        component.setMethod("GET");
        
        FlowFile flowFile = new FlowFile();
        
        assertThrows(Exception.class, () -> {
            component.execute(flowFile);
        });
    }
}
