package com.data.datafusion.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 指标知识库（RAG）配置。
 *
 * <p>默认使用本地字符 n-gram 向量，无需外部接口即可开箱可用；
 * 也可切换到 OpenAI 兼容的 {@code /embeddings} 接口提升语义效果。
 */
@Component
@ConfigurationProperties(prefix = "datay.ai.rag")
public class MetricRagProperties {

    /** 是否启用指标知识库检索。 */
    private boolean enabled = true;

    /** DuckDB 索引文件路径，默认与业务数仓隔离。 */
    private String dbFile = "./data/metric-rag.duckdb";

    /** 向量化提供方：local（本地字符 n-gram）/ openai（OpenAI 兼容接口）。 */
    private String provider = "local";

    /** 本地向量维度。 */
    private int localDimensions = 512;

    /** 外部 embedding 的 API 根地址，缺省回退主 base-url。 */
    private String embeddingBaseUrl;

    /** 外部 embedding 模型名。 */
    private String embeddingModel = "text-embedding-3-small";

    /** 外部 embedding 的 API Key，缺省回退主 api-key。 */
    private String embeddingApiKey;

    /** 外部 embedding 请求超时（秒）。 */
    private int embeddingTimeoutSeconds = 60;

    /** 默认返回的候选条数。 */
    private int topK = 8;

    /** 是否索引维度成员值（如「北京」）。 */
    private boolean memberIndexEnabled = true;

    /** 单字段成员抽样上限。 */
    private int memberSampleLimit = 10000;

    public boolean isOpenAiProvider() {
        return "openai".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDbFile() {
        return dbFile;
    }

    public void setDbFile(String dbFile) {
        this.dbFile = dbFile;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getLocalDimensions() {
        return localDimensions;
    }

    public void setLocalDimensions(int localDimensions) {
        this.localDimensions = localDimensions;
    }

    public String getEmbeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
        this.embeddingBaseUrl = embeddingBaseUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public int getEmbeddingTimeoutSeconds() {
        return embeddingTimeoutSeconds;
    }

    public void setEmbeddingTimeoutSeconds(int embeddingTimeoutSeconds) {
        this.embeddingTimeoutSeconds = embeddingTimeoutSeconds;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean isMemberIndexEnabled() {
        return memberIndexEnabled;
    }

    public void setMemberIndexEnabled(boolean memberIndexEnabled) {
        this.memberIndexEnabled = memberIndexEnabled;
    }

    public int getMemberSampleLimit() {
        return memberSampleLimit;
    }

    public void setMemberSampleLimit(int memberSampleLimit) {
        this.memberSampleLimit = memberSampleLimit;
    }
}
