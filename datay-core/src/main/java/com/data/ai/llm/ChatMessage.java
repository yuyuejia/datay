package com.data.ai.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话消息。统一承载 system / user / assistant / tool 四种角色的消息形态。
 */
public class ChatMessage {

    private String role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;
    private String name;

    public ChatMessage() {}

    public static ChatMessage system(String content) {
        ChatMessage message = new ChatMessage();
        message.role = "system";
        message.content = content;
        return message;
    }

    public static ChatMessage user(String content) {
        ChatMessage message = new ChatMessage();
        message.role = "user";
        message.content = content;
        return message;
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        ChatMessage message = new ChatMessage();
        message.role = "assistant";
        message.content = content;
        message.toolCalls = toolCalls;
        return message;
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        ChatMessage message = new ChatMessage();
        message.role = "tool";
        message.toolCallId = toolCallId;
        message.name = name;
        message.content = content;
        return message;
    }

    /**
     * 转换为 OpenAI Chat Completions 的消息结构。
     */
    public Map<String, Object> toOpenAiFormat() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        if (content != null) {
            map.put("content", content);
        }
        if (toolCallId != null) {
            map.put("tool_call_id", toolCallId);
        }
        if (name != null) {
            map.put("name", name);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", call.getName());
                function.put("arguments", call.getArguments());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", call.getId());
                item.put("type", "function");
                item.put("function", function);
                calls.add(item);
            }
            map.put("tool_calls", calls);
        }
        return map;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getName() {
        return name;
    }
}
