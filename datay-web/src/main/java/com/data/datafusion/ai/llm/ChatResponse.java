package com.data.datafusion.ai.llm;

import java.util.List;

/**
 * 一次 LLM 调用的结果。
 */
public class ChatResponse {

    private final String content;
    private final List<ToolCall> toolCalls;
    private final boolean toolCallRequested;
    private final int promptTokens;
    private final int completionTokens;

    public ChatResponse(String content, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {
        this.content = content;
        this.toolCalls = toolCalls == null ? List.of() : toolCalls;
        this.toolCallRequested = !this.toolCalls.isEmpty();
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean isToolCallRequested() {
        return toolCallRequested;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }
}
