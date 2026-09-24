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
    private String code;
    private String explanation;
    private int rounds;
    private boolean converged;
    private List<Map<String, Object>> toolCalls;

    /**
     * 结构化产物：指标问数场景下承载一次指标查询的 columns / rows / sql，
     * 供前端在对话中直接渲染数据表；其它助手可能为空。
     */
    private Map<String, Object> data;
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
        // code 与 sql 同为「模型抽取出的最终产物」，脚本类助手用它承载生成的 Java 代码
        response.code = result.getSql();
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
        Object metricData = result.getArtifacts().get(AiResult.ARTIFACT_METRIC_QUERY);
        if (metricData instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            response.data = cast;
        }
        return response;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getSql() {
        return sql;
    }

    public String getCode() {
        return code;
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

    public Map<String, Object> getData() {
        return data;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }
}
