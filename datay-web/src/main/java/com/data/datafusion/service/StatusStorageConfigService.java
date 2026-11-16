package com.data.datafusion.service;

import com.data.datafusion.config.DeploymentProperties;
import com.data.datafusion.config.SkipTenantFilter;
import com.data.datafusion.config.StatusStorageConfig;
import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.security.TenantContext;
import com.data.status.LocalFileStatusStorageStrategy;
import com.data.status.MinioStatusStorageStrategy;
import com.data.status.StatusStorageStrategy;
import com.data.status.StatusStorageStrategyFactory;
import jakarta.annotation.PostConstruct;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ETL 任务状态存储配置服务。
 *
 * <p>配置保存在 {@code dp_service_config} 的 {@code status-storage} 分组下。
 * 单机部署使用 local（本地文件）；master/worker 分离部署使用 minio。
 * master 在投递任务时会把当前租户的配置随任务事件下发，worker 执行时无需再读取配置库。
 */
@Service
public class StatusStorageConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(StatusStorageConfigService.class);

    public static final String CONFIG_GROUP = "status-storage";

    public static final String KEY_TYPE = "type";
    public static final String KEY_LOCAL_BASE_PATH = "local.base-path";
    public static final String KEY_MINIO_ENDPOINT = "minio.endpoint";
    public static final String KEY_MINIO_ACCESS_KEY = "minio.access-key";
    public static final String KEY_MINIO_SECRET_KEY = "minio.secret-key";
    public static final String KEY_MINIO_BUCKET_NAME = "minio.bucket-name";

    private static final String TYPE_LOCAL = "local";
    private static final String TYPE_MINIO = "minio";
    private static final String DEFAULT_TYPE = TYPE_LOCAL;
    private static final String DEFAULT_LOCAL_BASE_PATH = "./log";
    private static final String DEFAULT_MINIO_BUCKET = "datay-status";
    private static final String NO_TENANT_CACHE_KEY = "__no_tenant__";

    private final ServiceConfigRepository serviceConfigRepository;

    @Autowired(required = false)
    private DeploymentProperties deploymentProperties;

    /**
     * 配置缓存有效期。默认 1 天（86400 秒），设为 0 可关闭缓存。
     *
     * <p>多 master 部署时，配置在某个节点修改后，其他节点最迟在 TTL 后感知，
     * 避免节点间缓存永久不一致。</p>
     */
    @Value("${datay.status-storage.config-cache-ttl-seconds:86400}")
    private long configCacheTtlSeconds = 86400;

    private final Map<String, StatusStorageStrategy> strategyCache = new ConcurrentHashMap<>();

    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    private volatile String cachedSignature;

    public StatusStorageConfigService(ServiceConfigRepository serviceConfigRepository) {
        this.serviceConfigRepository = serviceConfigRepository;
    }

    @PostConstruct
    public void init() {
        // 纯 worker 不再预加载状态存储配置，执行时使用 master 随任务事件下发的配置。
        // 旧事件没有配置时 getActiveStrategy() 仍会按需读取数据库，保持兼容。
        DeploymentProperties deployment = deploymentProperties != null ? deploymentProperties : DeploymentProperties.get();
        if (!deployment.isSchedulerEnabled()) {
            LOG.debug("worker 角色跳过状态存储配置预加载");
            return;
        }
        try {
            getActiveStrategy();
        } catch (Exception e) {
            LOG.warn("初始化状态存储配置失败，回退到本地策略: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StatusStorageConfig getConfig() {
        String cacheKey = currentTenantCacheKey();
        StatusStorageConfig cached = getCachedConfig(cacheKey);
        if (cached != null) {
            return cached;
        }

        StatusStorageConfig config = loadConfigFromDb();
        cacheConfig(cacheKey, config);
        return copyConfig(config);
    }

    private StatusStorageConfig loadConfigFromDb() {
        Map<String, String> values = new HashMap<>();
        List<ServiceConfig> items = serviceConfigRepository.findByDfGroup(CONFIG_GROUP);
        if (items != null) {
            for (ServiceConfig item : items) {
                if (item.getDfKey() == null) {
                    continue;
                }
                String value = item.getDfValue();
                if (value != null && !value.isEmpty()) {
                    values.put(item.getDfKey(), value);
                }
            }
        }

        StatusStorageConfig cfg = new StatusStorageConfig();
        cfg.setType(values.getOrDefault(KEY_TYPE, DEFAULT_TYPE));
        cfg.setLocalBasePath(values.getOrDefault(KEY_LOCAL_BASE_PATH, DEFAULT_LOCAL_BASE_PATH));
        cfg.setMinioEndpoint(values.getOrDefault(KEY_MINIO_ENDPOINT, ""));
        cfg.setMinioAccessKey(values.getOrDefault(KEY_MINIO_ACCESS_KEY, ""));
        cfg.setMinioSecretKey(values.getOrDefault(KEY_MINIO_SECRET_KEY, ""));
        cfg.setMinioBucketName(values.getOrDefault(KEY_MINIO_BUCKET_NAME, DEFAULT_MINIO_BUCKET));
        return cfg;
    }

    /**
     * 按指定租户解析状态存储配置。
     *
     * <p>用于 master 投递任务时解析配置。若当前线程没有租户上下文或上下文与目标租户不一致，
     * 会临时切换租户并在结束后恢复，避免把 A 租户的配置下发给 B 租户的任务。
     *
     * <p>该查询使用独立事务（REQUIRES_NEW）并跳过服务层的租户过滤器，保证即使调用方已经
     * 在一个不同租户的 Hibernate Session 中，也能按目标租户开启正确的过滤器。
     */
    @SkipTenantFilter
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public StatusStorageConfig getConfigForTenant(String tenantId) {
        Long target = parseTenantId(tenantId);
        if (target == null) {
            return getConfig();
        }

        StatusStorageConfig cached = getCachedConfig(String.valueOf(target));
        if (cached != null) {
            return cached;
        }

        Long previousId = TenantContext.getTenantId();
        String previousCode = TenantContext.getTenantCode();
        try {
            TenantContext.setTenantId(target);
            return getConfig();
        } finally {
            TenantContext.clear();
            if (previousId != null) {
                TenantContext.setTenantId(previousId);
            }
            if (previousCode != null) {
                TenantContext.setTenantCode(previousCode);
            }
        }
    }

    private Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(tenantId.trim());
        } catch (NumberFormatException e) {
            LOG.warn("忽略非法的租户 ID: {}", tenantId);
            return null;
        }
    }

    @Transactional
    public void saveConfig(StatusStorageConfig config) {
        StatusStorageConfig effective = normalizeConfig(config);
        saveConfigItem(KEY_TYPE, effective.getType());
        saveConfigItem(KEY_LOCAL_BASE_PATH, effective.getLocalBasePath());
        saveConfigItem(KEY_MINIO_ENDPOINT, effective.getMinioEndpoint());
        saveConfigItem(KEY_MINIO_ACCESS_KEY, effective.getMinioAccessKey());
        saveConfigItem(KEY_MINIO_SECRET_KEY, effective.getMinioSecretKey());
        saveConfigItem(KEY_MINIO_BUCKET_NAME, effective.getMinioBucketName());
        invalidateConfigCache(currentTenantCacheKey());
    }

    private void saveConfigItem(String key, String value) {
        Optional<ServiceConfig> existing = serviceConfigRepository.findByDfGroupAndDfKey(CONFIG_GROUP, key);
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
        }
        serviceConfigRepository.save(sc);
    }

    /**
     * 根据配置构建状态存储策略。
     */
    public StatusStorageStrategy buildStrategy(StatusStorageConfig config) {
        String type = config.getType() != null ? config.getType().toLowerCase() : DEFAULT_TYPE;
        LOG.info("Building status storage strategy: {}", type);
        if (TYPE_MINIO.equals(type)) {
            return new MinioStatusStorageStrategy(
                config.getMinioEndpoint(),
                config.getMinioAccessKey(),
                config.getMinioSecretKey(),
                config.getMinioBucketName() != null ? config.getMinioBucketName() : DEFAULT_MINIO_BUCKET
            );
        }
        return new LocalFileStatusStorageStrategy(
            config.getLocalBasePath() != null ? config.getLocalBasePath() : DEFAULT_LOCAL_BASE_PATH
        );
    }

    /**
     * 根据下发到 worker 的配置获取策略，相同配置复用同一个策略实例。
     *
     * <p>MinIO 策略构造时会检查/创建 bucket，不能每个任务都重新创建，否则会引入不必要的网络开销。
     */
    public StatusStorageStrategy getStrategy(StatusStorageConfig config) {
        if (config == null) {
            return getActiveStrategy();
        }
        String signature = signature(config);
        return strategyCache.computeIfAbsent(signature, key -> buildStrategy(config));
    }

    /**
     * 获取当前生效的状态存储策略。
     *
     * <p>配置读取优先命中进程内缓存，缓存未命中或过期时才查询共享库；
     * 配置未变化时复用已构建的策略。多节点部署时配置变更最迟在缓存 TTL 后生效。
     */
    public StatusStorageStrategy getActiveStrategy() {
        StatusStorageConfig config = getConfig();
        String signature = signature(config);
        StatusStorageStrategy strategy = getStrategy(config);
        if (!signature.equals(cachedSignature)) {
            synchronized (this) {
                if (!signature.equals(cachedSignature)) {
                    cachedSignature = signature;
                    StatusStorageStrategyFactory.setActiveStrategy(strategy);
                }
            }
        }
        return strategy;
    }

    /**
     * 保存配置并立即重新加载策略。
     */
    public synchronized StatusStorageStrategy updateAndReload(StatusStorageConfig config) {
        StatusStorageConfig effective = normalizeConfig(config);
        saveConfig(effective);
        StatusStorageStrategy strategy = buildStrategy(effective);
        String signature = signature(effective);
        strategyCache.put(signature, strategy);
        cachedSignature = signature;
        StatusStorageStrategyFactory.setActiveStrategy(strategy);
        cacheConfig(currentTenantCacheKey(), effective);
        return strategy;
    }

    private String signature(StatusStorageConfig config) {
        if (config == null) {
            return "";
        }
        return String.join(
            "\u0001",
            Objects.toString(config.getType(), ""),
            Objects.toString(config.getLocalBasePath(), ""),
            Objects.toString(config.getMinioEndpoint(), ""),
            Objects.toString(config.getMinioAccessKey(), ""),
            Objects.toString(config.getMinioSecretKey(), ""),
            Objects.toString(config.getMinioBucketName(), "")
        );
    }

    private String currentTenantCacheKey() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId == null ? NO_TENANT_CACHE_KEY : String.valueOf(tenantId);
    }

    private StatusStorageConfig getCachedConfig(String cacheKey) {
        if (configCacheTtlSeconds <= 0) {
            return null;
        }
        CachedConfig cached = configCache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached.isExpired()) {
            configCache.remove(cacheKey, cached);
            return null;
        }
        return copyConfig(cached.config());
    }

    private void cacheConfig(String cacheKey, StatusStorageConfig config) {
        if (configCacheTtlSeconds <= 0) {
            return;
        }
        long expiresAtNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(configCacheTtlSeconds);
        configCache.put(cacheKey, new CachedConfig(copyConfig(config), expiresAtNanos));
    }

    private void invalidateConfigCache(String cacheKey) {
        configCache.remove(cacheKey);
    }

    private StatusStorageConfig normalizeConfig(StatusStorageConfig config) {
        StatusStorageConfig source = config != null ? config : new StatusStorageConfig();
        StatusStorageConfig normalized = new StatusStorageConfig();
        normalized.setType(source.getType() != null ? source.getType() : DEFAULT_TYPE);
        normalized.setLocalBasePath(source.getLocalBasePath() != null ? source.getLocalBasePath() : DEFAULT_LOCAL_BASE_PATH);
        normalized.setMinioEndpoint(source.getMinioEndpoint() != null ? source.getMinioEndpoint() : "");
        normalized.setMinioAccessKey(source.getMinioAccessKey() != null ? source.getMinioAccessKey() : "");
        normalized.setMinioSecretKey(source.getMinioSecretKey() != null ? source.getMinioSecretKey() : "");
        normalized.setMinioBucketName(source.getMinioBucketName() != null ? source.getMinioBucketName() : DEFAULT_MINIO_BUCKET);
        return normalized;
    }

    private StatusStorageConfig copyConfig(StatusStorageConfig source) {
        StatusStorageConfig copy = new StatusStorageConfig();
        copy.setType(source.getType());
        copy.setLocalBasePath(source.getLocalBasePath());
        copy.setMinioEndpoint(source.getMinioEndpoint());
        copy.setMinioAccessKey(source.getMinioAccessKey());
        copy.setMinioSecretKey(source.getMinioSecretKey());
        copy.setMinioBucketName(source.getMinioBucketName());
        return copy;
    }

    private record CachedConfig(StatusStorageConfig config, long expiresAtNanos) {
        private boolean isExpired() {
            return System.nanoTime() >= expiresAtNanos;
        }
    }
}
