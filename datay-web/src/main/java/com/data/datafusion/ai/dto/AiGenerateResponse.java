package com.data.datafusion.ai.dto;

import com.data.datafusion.ai.AiResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生成响应体。
 */
public class AiGenerateResponse {

    private boolean available;
    private String sql;
    private String explanation;
    private int rounds;
    private boolean converged;
    private List<Map<String, Object>> toolCalls;
    private int promptTokens;
    private int completionTokens;

    public static AiGenerateResponse unavailable(String reason) {
        AiGenerateResponse response = new AiGenerateResponse();
        response.available = false;
        response.explanation = reason;
        return response;
    }

    public static AiGenerateResponse from(AiResult result) {
        AiGenerateResponse response = new AiGenerateResponse();
        response.available = true;
        response.sql = result.getSql();
        response.explanation = result.getExplanation();
        response.rounds = result.getRounds();
        response.converged = result.isConverged();
        response.promptTokens = result.getPromptTokens();
        response.completionTokens = result.getCompletionTokens();
        response.toolCalls = result
            .getToolCalls()
            .stream()
            .map(trace -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", trace.getName());
                item.put("arguments", trace.getArguments());
                item.put("success", trace.isSuccess());
                item.put("elapsedMs", trace.getElapsedMs());
                return item;
            })
            .toList();
        return response;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getSql() {
        return sql;
    }

    public String getExplanation() {
        return explanation;
    }

    public int getRounds() {
        return rounds;
    }

    public boolean isConverged() {
        return converged;
    }

    public List<Map<String, Object>> getToolCalls() {
        return toolCalls;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }
}
