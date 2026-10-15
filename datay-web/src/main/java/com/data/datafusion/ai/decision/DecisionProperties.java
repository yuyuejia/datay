package com.data.datafusion.ai.decision;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 决策模型（Ollama Jev-style）默认配置。
 *
 * <p>对接 Ollama 的 {@code /v1/systemone} 接口，本地推理无需联网即可获得低延迟决策结果。
 *
 * <p>运行时以决策模型服务配置（{@code dp_service_config} 的 {@code decision} 分组，
 * 见 {@link DecisionConfigService}）为准；此处的 {@code datay.decision.*} 仅作为缺省值/回退，
 * 便于通过环境变量或配置文件提供初始配置。
 */
@Component
@ConfigurationProperties(prefix = "datay.decision")
public class DecisionProperties {

    /** 是否启用决策模型服务，关闭后「决策模型」组件不可用。 */
    private boolean enabled = true;

    /** Ollama 服务根地址，需包含版本段，例如 http://localhost:11434/v1。 */
    private String baseUrl = "http://localhost:11434/v1";

    /** API Key，Ollama 本地服务默认忽略，可留空或填写 ollama。 */
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
