package com.data.datafusion.ai.decision;

/**
 * 决策模型服务配置。
 *
 * <p>由 {@link DecisionConfigService} 从 {@code dp_service_config} 的 {@code decision} 分组读写，
 * 未配置的项回退到 {@link DecisionProperties} 提供的默认值（{@code datay.decision.*}）。
 */
public class DecisionConfig {

    /** 是否启用决策模型服务。 */
    private boolean enabled = true;

    /** Ollama 服务根地址，需包含版本段，例如 http://localhost:11434/v1。 */
    private String baseUrl = "http://localhost:11434/v1";

    /** API Key，Ollama 本地服务默认忽略。 */
    private String apiKey = "ollama";

    /** 决策模型名，例如 nimble / tev1。 */
    private String model = "nimble";

    /** 单次决策请求超时（秒）。 */
    private int timeoutSeconds = 120;

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

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
