package com.data.datafusion.ai.llm;

import com.data.ai.llm.LlmClientConfig;
import com.data.ai.llm.OpenAiCompatibleClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 大模型客户端装配。
 * <p>复用 DataY Core 的共享客户端 {@link OpenAiCompatibleClient}，把系统配置 {@link AiProperties}
 * 映射为 {@link LlmClientConfig} 后构建 Spring Bean，供 AI 助手与其它组件共用同一实现。
 */
@Configuration
public class AiClientConfiguration {

    @Bean
    public OpenAiCompatibleClient openAiCompatibleClient(AiProperties properties) {
        LlmClientConfig config = new LlmClientConfig();
        config.setBaseUrl(properties.getBaseUrl());
        config.setApiKey(properties.getApiKey());
        config.setModel(properties.getModel());
        config.setTemperature(properties.getTemperature());
        config.setTimeoutSeconds(properties.getTimeoutSeconds());
        return new OpenAiCompatibleClient(config);
    }
}
