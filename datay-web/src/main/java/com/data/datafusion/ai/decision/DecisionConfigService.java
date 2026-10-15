package com.data.datafusion.ai.decision;

import com.alibaba.fastjson2.JSONObject;
import com.data.ai.llm.LlmClientConfig;
import com.data.ai.llm.SystemOneClient;
import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.security.TenantContext;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 决策模型服务配置。
 *
 * <p>配置按照「租户级覆盖、系统级兜底」的两级结构解析：
 * <ul>
 *     <li><b>租户级</b>：保存在 {@code dp_service_config} 的 {@code decision} 分组下，按租户隔离；</li>
 *     <li><b>系统级</b>：即配置文件 {@code datay.decision.*}（{@link DecisionProperties}）中的默认值。</li>
 * </ul>
 * 租户未配置的项自动回退到系统级配置，修改租户配置后无需重启即可生效。
 */
@Service
public class DecisionConfigService {

    public static final String CONFIG_GROUP = "decision";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_BASE_URL = "base-url";
    public static final String KEY_API_KEY = "api-key";
    public static final String KEY_MODEL = "model";
    public static final String KEY_TIMEOUT_SECONDS = "timeout-seconds";

    private final ServiceConfigRepository serviceConfigRepository;
    private final DecisionProperties defaults;

    public DecisionConfigService(ServiceConfigRepository serviceConfigRepository, DecisionProperties defaults) {
        this.serviceConfigRepository = serviceConfigRepository;
        this.defaults = defaults;
    }

    @Transactional(readOnly = true)
    public DecisionConfig getConfig() {
        DecisionConfig cfg = new DecisionConfig();
        cfg.setEnabled(getBoolean(KEY_ENABLED, defaults.isEnabled()));
        cfg.setBaseUrl(getString(KEY_BASE_URL, defaults.getBaseUrl()));
        cfg.setApiKey(getString(KEY_API_KEY, defaults.getApiKey()));
        cfg.setModel(getString(KEY_MODEL, defaults.getModel()));
        cfg.setTimeoutSeconds(getInt(KEY_TIMEOUT_SECONDS, defaults.getTimeoutSeconds()));
        return cfg;
    }

    /**
     * 获取用于界面展示的配置。
     *
     * <p>为避免泄露系统级密钥：当 API Key 沿用系统级配置（即当前租户未单独配置）时清空该字段，
     * 前端会提示「留空则使用系统级配置」。
     */
    @Transactional(readOnly = true)
    public DecisionConfig getConfigForDisplay() {
        DecisionConfig config = getConfig();
        if (findTenantItem(KEY_API_KEY).map(ServiceConfig::getDfValue).filter(v -> v != null && !v.isEmpty()).isEmpty()) {
            config.setApiKey("");
        }
        return config;
    }

    @Transactional
    public void saveConfig(DecisionConfig config) {
        saveItem(KEY_ENABLED, String.valueOf(config.isEnabled()));
        saveItem(KEY_BASE_URL, config.getBaseUrl() != null ? config.getBaseUrl() : "");
        saveItem(KEY_API_KEY, config.getApiKey() != null ? config.getApiKey() : "");
        saveItem(KEY_MODEL, config.getModel() != null ? config.getModel() : "");
        saveItem(KEY_TIMEOUT_SECONDS, String.valueOf(config.getTimeoutSeconds()));
    }

    /**
     * 保存配置，修改后无需重启即可生效。
     */
    public synchronized void updateAndReload(DecisionConfig config) {
        saveConfig(config);
    }

    /**
     * 使用当前配置发起一次最小决策请求，验证接口地址与模型是否可用。
     *
     * @return 实际使用的模型名
     */
    public String testConnection() throws Exception {
        DecisionConfig config = getConfig();
        SystemOneClient client = buildClient(config);
        try {
            JSONObject state = new JSONObject();
            state.put("input", "ping");
            JSONObject questions = new JSONObject();
            JSONObject alive = new JSONObject();
            alive.put("type", "noul");
            alive.put("instructions", "Is the service responding?");
            questions.put("alive", alive);
            client.decide(state, questions);
            return config.getModel();
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.contains("未安装")) {
                String installed = listInstalledModels(config.getBaseUrl());
                if (installed != null && !installed.isEmpty()) {
                    message = message + "；当前已安装：" + installed;
                }
            }
            throw new IllegalStateException(message == null ? "决策模型连接测试失败" : message, e);
        } finally {
            client.close();
        }
    }

    /**
     * 查询 Ollama 已安装的模型列表，用于在模型缺失时给出提示；查询失败时返回空串。
     */
    private String listInstalledModels(String baseUrl) {
        try {
            java.net.URI uri = java.net.URI.create(resolveRoot(baseUrl) + "/api/tags");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            try (java.io.InputStream in = conn.getInputStream()) {
                String body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                com.alibaba.fastjson2.JSONArray models = JSONObject.parseObject(body).getJSONArray("models");
                if (models == null) {
                    return "";
                }
                java.util.List<String> names = new java.util.ArrayList<>();
                for (int i = 0; i < models.size(); i++) {
                    JSONObject model = models.getJSONObject(i);
                    if (model != null && model.getString("name") != null) {
                        names.add(model.getString("name"));
                    }
                }
                return String.join(", ", names);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 由 baseUrl 推导 Ollama 根地址（去掉 /v1、/systemone 等路径段）。
     */
    private String resolveRoot(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:11434";
        }
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            if (scheme != null && authority != null) {
                return scheme + "://" + authority;
            }
        } catch (Exception ignored) {
            // 回退到字符串裁剪
        }
        String base = baseUrl;
        int idx = base.indexOf("/", base.indexOf("://") + 3);
        return idx > 0 ? base.substring(0, idx) : base;
    }

    private SystemOneClient buildClient(DecisionConfig config) {
        LlmClientConfig clientConfig = new LlmClientConfig();
        clientConfig.setBaseUrl(config.getBaseUrl());
        clientConfig.setApiKey(config.getApiKey());
        clientConfig.setModel(config.getModel());
        clientConfig.setTimeoutSeconds(config.getTimeoutSeconds());
        return new SystemOneClient(clientConfig);
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
     * 查找当前租户在 {@code decision} 分组下的配置项；无租户上下文时按过滤器作用域查找。
     */
    private Optional<ServiceConfig> findTenantItem(String key) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return serviceConfigRepository.findByDfGroupAndDfKey(CONFIG_GROUP, key);
        }
        return serviceConfigRepository.findByDfGroupAndDfKeyAndTenantId(CONFIG_GROUP, key, String.valueOf(tenantId));
    }
}
