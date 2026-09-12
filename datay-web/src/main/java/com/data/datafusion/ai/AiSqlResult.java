package com.data.datafusion.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 生成 SQL 的结果，附带 tool-calling loop 的执行轨迹，便于前端展示与问题排查。
 */
public class AiSqlResult {

    private String sql;
    private String explanation;
    private int rounds;
    private boolean converged;
    private List<ToolTrace> toolCalls = new ArrayList<>();
    private int promptTokens;
    private int completionTokens;

    /**
     * 单次工具调用的轨迹记录。
     */
    public static class ToolTrace {

        private String name;
        private Map<String, Object> arguments;
        private boolean success;
        private long elapsedMs;

        public ToolTrace(String name, Map<String, Object> arguments, boolean success, long elapsedMs) {
            this.name = name;
            this.arguments = arguments;
            this.success = success;
            this.elapsedMs = elapsedMs;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public boolean isSuccess() {
            return success;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    public boolean isConverged() {
        return converged;
    }

    public void setConverged(boolean converged) {
        this.converged = converged;
    }

    public List<ToolTrace> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolTrace> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }
}
