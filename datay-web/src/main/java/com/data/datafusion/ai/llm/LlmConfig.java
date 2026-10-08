package com.data.datafusion.ai.llm;

/**
 * 大模型服务配置。
 *
 * <p>由 {@link LlmConfigService} 从 {@code dp_service_config} 的 {@code llm} 分组读写，
 * 未配置的项回退到 {@link AiProperties} 提供的默认值（{@code datay.ai.*}）。
 */
public class LlmConfig {

    /** 是否启用大模型服务，关闭后 AI 助手与「大模型」组件均不可用。 */
    private boolean enabled = true;

    /** OpenAI 兼容的 API 根地址，需包含版本段，例如 https://api.deepseek.com/v1。 */
    private String baseUrl = "https://api.openai.com/v1";

    /** API Key，留空时视为未配置。 */
    private String apiKey;

    /** 模型名，例如 gpt-4o-mini / deepseek-chat / qwen-plus。 */
    private String model = "gpt-4o-mini";

    /** 采样温度，生成 SQL 场景建议保持较低值。 */
    private double temperature = 0.2;

    /** 单次 LLM 请求超时（秒）。 */
    private int timeoutSeconds = 120;

    /** Agent 循环最大轮次。 */
    private int maxToolRounds = 6;

    /** 单轮工具调用最大次数。 */
    private int maxToolCallsPerRound = 8;

    /** 是否允许模型调用具备副作用的工具。 */
    private boolean allowMutatingTools = false;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getMaxToolCallsPerRound() {
        return maxToolCallsPerRound;
    }

    public void setMaxToolCallsPerRound(int maxToolCallsPerRound) {
        this.maxToolCallsPerRound = maxToolCallsPerRound;
    }

    public boolean isAllowMutatingTools() {
        return allowMutatingTools;
    }

    public void setAllowMutatingTools(boolean allowMutatingTools) {
        this.allowMutatingTools = allowMutatingTools;
    }
}
