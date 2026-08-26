package com.data.job.component.router;

import com.data.job.Component.ComponentType;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.router.RandomRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RandomRouterTest {

    private RandomRouter component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new RandomRouter();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-random-router");
        component.setName("RandomRouter");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testRandomRouterBasic() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("testKey", "testValue");
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testRandomRouterWithEndAttribute() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testRandomRouterMultipleExecutions() {
        for (int i = 0; i < 10; i++) {
            FlowFile flowFile = new FlowFile();
            flowFile.setAttribute("index", i);
            component.execute(flowFile);
        }
    }

    @Test
    @Timeout(60000)
    public void testRandomRouterWithTextData() {
        FlowFile flowFile = new FlowFile();
        flowFile.setTextData("Random test data");
        
        component.execute(flowFile);
    }

    @Test
    @Timeout(60000)
    public void testRandomRouterWithNullData() {
        FlowFile flowFile = new FlowFile();
        
        component.execute(flowFile);
    }
}
