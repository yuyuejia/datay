package com.data.datafusion.service;

import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.status.LocalFileStatusStorageStrategy;
import com.data.status.MinioStatusStorageStrategy;
import com.data.status.StatusStorageStrategy;
import com.data.status.StatusStorageStrategyFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * ETL 任务状态存储配置服务。
 *
 * <p>配置保存在 {@code dp_service_config} 的 {@code status-storage} 分组下。
 * 单机部署使用 local（本地文件）；master/worker 分离部署使用 minio，
 * 由于配置位于共享库，master 与 worker 会解析到同一后端，从而共享增量状态。
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

    private final ServiceConfigRepository serviceConfigRepository;

    private volatile String cachedSignature;
    private volatile StatusStorageStrategy cachedStrategy;

    public StatusStorageConfigService(ServiceConfigRepository serviceConfigRepository) {
        this.serviceConfigRepository = serviceConfigRepository;
    }

    @PostConstruct
    public void init() {
        try {
            getActiveStrategy();
        } catch (Exception e) {
            LOG.warn("初始化状态存储配置失败，回退到本地策略: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StatusStorageConfig getConfig() {
        StatusStorageConfig cfg = new StatusStorageConfig();
        cfg.type = getConfigValue(KEY_TYPE, DEFAULT_TYPE);
        cfg.localBasePath = getConfigValue(KEY_LOCAL_BASE_PATH, DEFAULT_LOCAL_BASE_PATH);
        cfg.minioEndpoint = getConfigValue(KEY_MINIO_ENDPOINT, "");
        cfg.minioAccessKey = getConfigValue(KEY_MINIO_ACCESS_KEY, "");
        cfg.minioSecretKey = getConfigValue(KEY_MINIO_SECRET_KEY, "");
        cfg.minioBucketName = getConfigValue(KEY_MINIO_BUCKET_NAME, DEFAULT_MINIO_BUCKET);
        return cfg;
    }

    private String getConfigValue(String key, String defaultValue) {
        return serviceConfigRepository
            .findByDfGroupAndDfKey(CONFIG_GROUP, key)
            .map(ServiceConfig::getDfValue)
            .filter(v -> v != null && !v.isEmpty())
            .orElse(defaultValue);
    }

    @Transactional
    public void saveConfig(StatusStorageConfig config) {
        saveConfigItem(KEY_TYPE, config.type != null ? config.type : DEFAULT_TYPE);
        saveConfigItem(KEY_LOCAL_BASE_PATH, config.localBasePath != null ? config.localBasePath : DEFAULT_LOCAL_BASE_PATH);
        saveConfigItem(KEY_MINIO_ENDPOINT, config.minioEndpoint != null ? config.minioEndpoint : "");
        saveConfigItem(KEY_MINIO_ACCESS_KEY, config.minioAccessKey != null ? config.minioAccessKey : "");
        saveConfigItem(KEY_MINIO_SECRET_KEY, config.minioSecretKey != null ? config.minioSecretKey : "");
        saveConfigItem(KEY_MINIO_BUCKET_NAME, config.minioBucketName != null ? config.minioBucketName : DEFAULT_MINIO_BUCKET);
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
        String type = config.type != null ? config.type.toLowerCase() : DEFAULT_TYPE;
        LOG.info("Building status storage strategy: {}", type);
        if (TYPE_MINIO.equals(type)) {
            return new MinioStatusStorageStrategy(
                config.minioEndpoint,
                config.minioAccessKey,
                config.minioSecretKey,
                config.minioBucketName != null ? config.minioBucketName : DEFAULT_MINIO_BUCKET
            );
        }
        return new LocalFileStatusStorageStrategy(
            config.localBasePath != null ? config.localBasePath : DEFAULT_LOCAL_BASE_PATH
        );
    }

    /**
     * 获取当前生效的状态存储策略。
     *
     * <p>每次调用都会读取最新配置（共享库），配置未变化时复用已构建的策略，
     * 这样 master 修改配置后 worker 在下次任务启动时即可感知。
     */
    public StatusStorageStrategy getActiveStrategy() {
        StatusStorageConfig config = getConfig();
        String signature = signature(config);
        StatusStorageStrategy strategy = cachedStrategy;
        if (strategy == null || !signature.equals(cachedSignature)) {
            synchronized (this) {
                if (cachedStrategy == null || !signature.equals(cachedSignature)) {
                    cachedStrategy = buildStrategy(config);
                    cachedSignature = signature;
                    StatusStorageStrategyFactory.setActiveStrategy(cachedStrategy);
                }
                strategy = cachedStrategy;
            }
        }
        return strategy;
    }

    /**
     * 保存配置并立即重新加载策略。
     */
    public synchronized StatusStorageStrategy updateAndReload(StatusStorageConfig config) {
        saveConfig(config);
        StatusStorageStrategy strategy = buildStrategy(config);
        cachedStrategy = strategy;
        cachedSignature = signature(config);
        StatusStorageStrategyFactory.setActiveStrategy(strategy);
        return strategy;
    }

    private String signature(StatusStorageConfig config) {
        return String.join(
            "\u0001",
            Objects.toString(config.type, ""),
            Objects.toString(config.localBasePath, ""),
            Objects.toString(config.minioEndpoint, ""),
            Objects.toString(config.minioAccessKey, ""),
            Objects.toString(config.minioSecretKey, ""),
            Objects.toString(config.minioBucketName, "")
        );
    }

    public static class StatusStorageConfig {

        public String type = DEFAULT_TYPE;
        public String localBasePath = DEFAULT_LOCAL_BASE_PATH;
        public String minioEndpoint = "";
        public String minioAccessKey = "";
        public String minioSecretKey = "";
        public String minioBucketName = DEFAULT_MINIO_BUCKET;
    }
}
