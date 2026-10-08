package com.data.datafusion.ai.rag.embedding;

import com.data.datafusion.ai.llm.LlmConfig;
import com.data.datafusion.ai.llm.LlmConfigService;
import com.data.datafusion.ai.rag.MetricRagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指标知识库向量化客户端装配。
 *
 * <p>按配置选择本地哈希向量或 OpenAI 兼容 embedding；当选择外部接口但缺少
 * base-url / api-key 时自动降级为本地向量，保证检索链路可用。
 */
@Configuration
public class MetricRagEmbeddingConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MetricRagEmbeddingConfiguration.class);

    @Bean
    public EmbeddingClient metricRagEmbeddingClient(MetricRagProperties rag, LlmConfigService llmConfigService) {
        if (rag.isOpenAiProvider()) {
            LlmConfig llm = llmConfigService.getConfig();
            String baseUrl = firstNonBlank(rag.getEmbeddingBaseUrl(), llm.getBaseUrl());
            String apiKey = firstNonBlank(rag.getEmbeddingApiKey(), llm.getApiKey());
            if (baseUrl != null && apiKey != null && !apiKey.isBlank()) {
                LOG.info("Metric RAG embedding provider: openai, model={}, baseUrl={}", rag.getEmbeddingModel(), baseUrl);
                return new OpenAiEmbeddingClient(baseUrl, apiKey, rag.getEmbeddingModel(), rag.getEmbeddingTimeoutSeconds());
            }
            LOG.warn("Metric RAG 配置为 openai 但缺少 base-url/api-key，自动降级为本地向量");
        }
        LOG.info("Metric RAG embedding provider: local, dimensions={}", rag.getLocalDimensions());
        return new LocalHashEmbeddingClient(rag.getLocalDimensions());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
