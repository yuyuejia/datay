package com.data.datafusion.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手配置。
 *
 * <p>兼容 OpenAI Chat Completions 协议，OpenAI / DeepSeek / 通义千问 / Ollama 等
 * 只需替换 base-url 与 model 即可对接。
 */
@Component
@ConfigurationProperties(prefix = "datay.ai")
public class AiProperties {

    /** 是否启用 AI 助手，关闭后接口返回不可用并自动降级。 */
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

    /** Agent 循环最大轮次，达到上限后强制要求模型输出最终 SQL。 */
    private int maxToolRounds = 6;

    /** 是否允许模型调用具备副作用的工具，默认关闭以保证只读安全。 */
    private boolean allowMutatingTools = false;

    /** 单轮工具调用最大并发/次数上限，防止模型疯狂刷工具。 */
    private int maxToolCallsPerRound = 8;

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

    public boolean isAllowMutatingTools() {
        return allowMutatingTools;
    }

    public void setAllowMutatingTools(boolean allowMutatingTools) {
        this.allowMutatingTools = allowMutatingTools;
    }

    public int getMaxToolCallsPerRound() {
        return maxToolCallsPerRound;
    }

    public void setMaxToolCallsPerRound(int maxToolCallsPerRound) {
        this.maxToolCallsPerRound = maxToolCallsPerRound;
    }
}
