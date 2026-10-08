package com.data.datafusion.ai.llm;

import com.data.ai.llm.ChatMessage;
import com.data.ai.llm.LlmClientConfig;
import com.data.ai.llm.OpenAiCompatibleClient;
import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.security.TenantContext;
import jakarta.annotation.PostConstruct;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 大模型服务配置。
 *
 * <p>配置按照「租户级覆盖、系统级兜底」的两级结构解析：
 * <ul>
 *     <li><b>租户级</b>：保存在 {@code dp_service_config} 的 {@code llm} 分组下，按租户隔离；</li>
 *     <li><b>系统级</b>：即配置文件 {@code datay.ai.*}（{@link AiProperties}）中的默认值。</li>
 * </ul>
 * 租户未配置的项自动回退到系统级配置，修改租户配置后无需重启即可生效。
 */
@Service
public class LlmConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(LlmConfigService.class);

    public static final String CONFIG_GROUP = "llm";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_BASE_URL = "base-url";
    public static final String KEY_API_KEY = "api-key";
    public static final String KEY_MODEL = "model";
    public static final String KEY_TEMPERATURE = "temperature";
    public static final String KEY_TIMEOUT_SECONDS = "timeout-seconds";
    public static final String KEY_MAX_TOOL_ROUNDS = "max-tool-rounds";
    public static final String KEY_MAX_TOOL_CALLS_PER_ROUND = "max-tool-calls-per-round";
    public static final String KEY_ALLOW_MUTATING_TOOLS = "allow-mutating-tools";

    private final ServiceConfigRepository serviceConfigRepository;
    private final AiProperties defaults;

    private volatile String cachedSignature;
    private volatile OpenAiCompatibleClient cachedClient;

    public LlmConfigService(ServiceConfigRepository serviceConfigRepository, AiProperties defaults) {
        this.serviceConfigRepository = serviceConfigRepository;
        this.defaults = defaults;
    }

    @PostConstruct
    public void init() {
        try {
            getActiveClient();
        } catch (Exception e) {
            LOG.warn("初始化大模型配置失败: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public LlmConfig getConfig() {
        LlmConfig cfg = new LlmConfig();
        cfg.setEnabled(getBoolean(KEY_ENABLED, defaults.isEnabled()));
        cfg.setBaseUrl(getString(KEY_BASE_URL, defaults.getBaseUrl()));
        cfg.setApiKey(getString(KEY_API_KEY, defaults.getApiKey()));
        cfg.setModel(getString(KEY_MODEL, defaults.getModel()));
        cfg.setTemperature(getDouble(KEY_TEMPERATURE, defaults.getTemperature()));
        cfg.setTimeoutSeconds(getInt(KEY_TIMEOUT_SECONDS, defaults.getTimeoutSeconds()));
        cfg.setMaxToolRounds(getInt(KEY_MAX_TOOL_ROUNDS, defaults.getMaxToolRounds()));
        cfg.setMaxToolCallsPerRound(getInt(KEY_MAX_TOOL_CALLS_PER_ROUND, defaults.getMaxToolCallsPerRound()));
        cfg.setAllowMutatingTools(getBoolean(KEY_ALLOW_MUTATING_TOOLS, defaults.isAllowMutatingTools()));
        return cfg;
    }

    /**
     * 获取用于界面展示的配置。
     *
     * <p>为避免泄露系统级密钥：当 API Key 沿用系统级配置（即当前租户未单独配置）时清空该字段，
     * 前端会提示「留空则使用系统级配置」。其余字段返回包含系统级兜底的有效值。
     */
    @Transactional(readOnly = true)
    public LlmConfig getConfigForDisplay() {
        LlmConfig config = getConfig();
        if (findTenantItem(KEY_API_KEY).map(ServiceConfig::getDfValue).filter(v -> v != null && !v.isEmpty()).isEmpty()) {
            config.setApiKey("");
        }
        return config;
    }

    @Transactional
    public void saveConfig(LlmConfig config) {
        saveItem(KEY_ENABLED, String.valueOf(config.isEnabled()));
        saveItem(KEY_BASE_URL, config.getBaseUrl() != null ? config.getBaseUrl() : "");
        saveItem(KEY_API_KEY, config.getApiKey() != null ? config.getApiKey() : "");
        saveItem(KEY_MODEL, config.getModel() != null ? config.getModel() : "");
        saveItem(KEY_TEMPERATURE, String.valueOf(config.getTemperature()));
        saveItem(KEY_TIMEOUT_SECONDS, String.valueOf(config.getTimeoutSeconds()));
        saveItem(KEY_MAX_TOOL_ROUNDS, String.valueOf(config.getMaxToolRounds()));
        saveItem(KEY_MAX_TOOL_CALLS_PER_ROUND, String.valueOf(config.getMaxToolCallsPerRound()));
        saveItem(KEY_ALLOW_MUTATING_TOOLS, String.valueOf(config.isAllowMutatingTools()));
    }

    /**
     * 保存配置并使客户端缓存失效，下次调用即可使用最新配置。
     */
    public synchronized void updateAndReload(LlmConfig config) {
        saveConfig(config);
        cachedClient = null;
        cachedSignature = null;
    }

    /**
     * 获取当前生效的 OpenAI 兼容客户端。
     *
     * <p>每次调用都会读取最新配置，配置未变化时复用已构建的客户端，
     * 这样在服务配置页修改后无需重启即可生效。
     */
    public OpenAiCompatibleClient getActiveClient() {
        LlmConfig config = getConfig();
        String signature = signature(config);
        OpenAiCompatibleClient client = cachedClient;
        if (client == null || !signature.equals(cachedSignature)) {
            synchronized (this) {
                if (cachedClient == null || !signature.equals(cachedSignature)) {
                    cachedClient = buildClient(config);
                    cachedSignature = signature;
                }
                client = cachedClient;
            }
        }
        return client;
    }

    /**
     * 使用当前配置发起一次最小请求，验证接口地址、密钥与模型是否可用。
     *
     * @return 实际使用的模型名
     */
    public String testConnection() throws Exception {
        LlmConfig config = getConfig();
        if (!config.isConfigured()) {
            throw new IllegalStateException("未配置大模型 API Key");
        }
        OpenAiCompatibleClient client = buildClient(config);
        client.chat(List.of(ChatMessage.user("ping")), List.of(), false);
        return config.getModel();
    }

    private OpenAiCompatibleClient buildClient(LlmConfig config) {
        LlmClientConfig clientConfig = new LlmClientConfig();
        clientConfig.setBaseUrl(config.getBaseUrl());
        clientConfig.setApiKey(config.getApiKey());
        clientConfig.setModel(config.getModel());
        clientConfig.setTemperature(config.getTemperature());
        clientConfig.setTimeoutSeconds(config.getTimeoutSeconds());
        return new OpenAiCompatibleClient(clientConfig);
    }

    private String getString(String key, String systemDefault) {
        return findTenantItem(key)
            .map(ServiceConfig::getDfValue)
            .filter(v -> v != null && !v.isEmpty())
            .orElse(systemDefault);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void saveItem(String key, String value) {
        Long tenantId = TenantContext.getTenantId();
        String tenant = tenantId == null ? null : String.valueOf(tenantId);
        Optional<ServiceConfig> existing = findTenantItem(key);
        ServiceConfig sc;
        if (existing.isPresent()) {
            sc = existing.get();
            sc.setDfValue(value);
        } else {
            sc = new ServiceConfig();
            sc.setDfGroup(CONFIG_GROUP);
            sc.setDfKey(key);
            sc.setDfValue(value);
            sc.setCreateTime(ZonedDateTime.now());
            if (tenant != null) {
                sc.setTenantId(tenant);
            }
        }
        serviceConfigRepository.save(sc);
    }

    /**
     * 查找当前租户在 {@code llm} 分组下的配置项；无租户上下文时按过滤器作用域查找。
     */
    private Optional<ServiceConfig> findTenantItem(String key) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return serviceConfigRepository.findByDfGroupAndDfKey(CONFIG_GROUP, key);
        }
        return serviceConfigRepository.findByDfGroupAndDfKeyAndTenantId(CONFIG_GROUP, key, String.valueOf(tenantId));
    }

    private String signature(LlmConfig config) {
        return String.join(
            "\u0001",
            Objects.toString(config.isEnabled(), ""),
            Objects.toString(config.getBaseUrl(), ""),
            Objects.toString(config.getApiKey(), ""),
            Objects.toString(config.getModel(), ""),
            Objects.toString(config.getTemperature(), ""),
            Objects.toString(config.getTimeoutSeconds(), ""),
            Objects.toString(config.getMaxToolRounds(), ""),
            Objects.toString(config.getMaxToolCallsPerRound(), ""),
            Objects.toString(config.isAllowMutatingTools(), "")
        );
    }
}
