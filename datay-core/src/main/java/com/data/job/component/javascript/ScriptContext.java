package com.data.job.component.javascript;

import com.data.job.FlowComponent;
import com.data.job.FlowFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 脚本执行上下文
 * 提供脚本执行时需要的上下文信息
 */
public class ScriptContext {

    private FlowFile flowFile;
    private FlowComponent component;
    private Map<String, Object> context;

    public ScriptContext(FlowFile flowFile, FlowComponent component) {
        this.flowFile = flowFile;
        this.component = component;
        this.context = new HashMap<>();
        initializeAttributes();
    }

    /**
     * 初始化上下文属性
     */
    private void initializeAttributes() {
        // 添加组件信息
        context.put("componentId", component.getId());
        context.put("componentName", component.getName());

        // 添加流程上下文信息
        if (component.getContext() != null) {
            context.put("executionContext", component.getContext());
        }

        // 添加日志方法
        context.put("log", new LogFunction(component));
    }

    public FlowFile getFlowFile() {
        return flowFile;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public FlowComponent getComponent() {
        return component;
    }

    /**
     * 日志函数接口（供脚本使用）
     */
    public static class LogFunction {

        private FlowComponent component;

        public LogFunction(FlowComponent component) {
            this.component = component;
        }

        public void info(String message) {
            component.logInfo("[INFO] " + message);
        }

        public void warn(String message) {
            component.logInfo("[WARN] " + message);
        }

        public void error(String message) {
            component.logInfo("[ERROR] " + message);
        }
    }
}
