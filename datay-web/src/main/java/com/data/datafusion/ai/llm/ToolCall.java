package com.data.datafusion.ai.llm;

/**
 * 模型返回的一次工具调用请求。
 */
public class ToolCall {

    private final String id;
    private final String name;
    private final String arguments;

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * 原始 JSON 字符串形式的入参，由调用方负责反序列化。
     */
    public String getArguments() {
        return arguments;
    }
}
