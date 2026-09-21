package com.data.ai.llm;

/**
 * OpenAI 兼容客户端的连接配置。
 *
 * <p>由调用方（如 DataY Web 的系统配置或组件的任务单元配置）提供，客户端本身不感知配置来源。
 */
public class LlmClientConfig {

    private String baseUrl = "https://api.openai.com/v1";

    private String apiKey;

    private String model = "gpt-4o-mini";

    private double temperature = 0.2;

    private int timeoutSeconds = 120;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
