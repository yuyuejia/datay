package com.data.ai.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下发给模型的工具定义，对应 OpenAI 协议中的 {@code tools[].function}。
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * 转换为 OpenAI Chat Completions 的 tools 数组元素。
     */
    public Map<String, Object> toOpenAiFormat() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }
}
