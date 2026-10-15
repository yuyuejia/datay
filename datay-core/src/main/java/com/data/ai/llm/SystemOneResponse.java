package com.data.ai.llm;

import com.alibaba.fastjson2.JSONObject;

/**
 * 一次决策模型（Ollama {@code /v1/systemone}）调用的结果。
 */
public class SystemOneResponse {

    private final JSONObject answers;
    private final int inputTokens;
    private final int outputTokens;

    public SystemOneResponse(JSONObject answers, int inputTokens, int outputTokens) {
        this.answers = answers == null ? new JSONObject() : answers;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    /**
     * 各问题名的回答对象，形如 {@code {"team":{"type":"choice","choice":"billing",...}}}。
     */
    public JSONObject getAnswers() {
        return answers;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }
}
