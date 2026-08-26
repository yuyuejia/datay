package com.data.job;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// 组件接口定义
public abstract class Component {
    // 组件类型枚举
    public enum ComponentType {
        SOURCE,    // 数据源组件，负责数据输入
        OPERATOR,  // 操作组件，负责数据处理和转换
        SINK       // 数据输出组件，负责数据输出
    }

    private String id;
    private String name;
    private ComponentType type;  // 组件类型
    private int parallelism = 1; // 并发数，默认为1
    private AtomicInteger activeThreads;

    private List<Connection> input = new ArrayList<>();
    private List<Connection> output = new ArrayList<>();

    private TaskLogger taskLogger;

    private ExecutionContext context;

    public abstract void execute();

    public void stop() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ComponentType getType() {
        return type;
    }

    public void setType(ComponentType type) {
        this.type = type;
    }

    public String getOutputTableName(int index) {
        return "tmpview_" + this.getId().toLowerCase() + "_" + index;
    }

    public String getInputTableName(int index) {
        return "tmpview_" + this.getInput().get(index).getSourceId().toLowerCase() + "_0";
    }

    public List<Connection> getInput() {
        return input;
    }

    public void setInput(List<Connection> input) {
        this.input = input;
    }

    public List<Connection> getOutput() {
        return output;
    }

    public void setOutput(List<Connection> output) {
        this.output = output;
    }

    public void setTaskLogger(TaskLogger taskLogger) {
        this.taskLogger = taskLogger;
    }

    private void log(String... messages) {
        this.taskLogger.writeLog("[" + this.name + "-" + this.id + "] - " + String.join(" ", messages));
    }

    /**
     * 不同级别的日志方法
     */
    public void logDebug(String... messages) {
        log("[DEBUG] " + String.join(" ", messages));
    }

    public void logInfo(String... messages) {
        log("[INFO] " + String.join(" ", messages));
    }

    public void logWarn(String... messages) {
        log("[WARN] " + String.join(" ", messages));
    }

    public void logError(String... messages) {
        log("[ERROR] " + String.join(" ", messages));
    }

    public ExecutionContext getContext() {
        return context;
    }

    public void setContext(ExecutionContext context) {
        this.context = context;
    }

    public Object getStatus(String key) {
        return this.context.get(getId() + "." + key);
    }

    /**
     * 检查组件是否为Source类型
     */
    public boolean isSource() {
        return type == ComponentType.SOURCE;
    }

    /**
     * 检查组件是否为Operator类型
     */
    public boolean isOperator() {
        return type == ComponentType.OPERATOR;
    }

    /**
     * 检查组件是否为Sink类型
     */
    public boolean isSink() {
        return type == ComponentType.SINK;
    }

    /**
     * 获取组件类型的字符串表示
     */
    public String getTypeString() {
        return type.toString();
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public void setParallelism(String parallelism) {
        if (parallelism != null && !parallelism.trim().isEmpty()) {
            try {
                this.parallelism = Integer.parseInt(parallelism.trim());
            } catch (NumberFormatException e) {
                this.parallelism = 1; // 解析失败时使用默认值
            }
        }
    }


    public AtomicInteger getActiveThreads() {
        return activeThreads;
    }

    public void setActiveThreads(AtomicInteger activeThreads) {
        this.activeThreads = activeThreads;
    }
}