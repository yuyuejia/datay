package com.data.job.component;

import com.data.job.ExecutionContext;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.atomic.AtomicInteger;

public class ComponentTestBase {

    protected ExecutionContext context;
    protected TaskLogger taskLogger;

    @BeforeEach
    public void setUpBase() {
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
    }

    protected void initComponent(FlowComponent component, String id, String name) {
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId(id);
        component.setName(name);
        component.setActiveThreads(new AtomicInteger(1));
    }

    protected FlowFile createStartFlowFile() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        return flowFile;
    }

    protected FlowFile createEndFlowFile() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        return flowFile;
    }

    protected FlowFile createTextFlowFile(String textData) {
        FlowFile flowFile = new FlowFile();
        flowFile.setTextData(textData);
        flowFile.setAttribute("_start", true);
        return flowFile;
    }

    protected FlowFile createJsonArrayFlowFile(String jsonData) {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_start", true);
        return flowFile;
    }
}
