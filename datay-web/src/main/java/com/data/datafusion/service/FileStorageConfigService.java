package com.data.datafusion.service;

import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.file.FileStorageStrategy;
import com.data.file.FileStorageStrategyFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FileStorageConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(FileStorageConfigService.class);

    public static final String CONFIG_GROUP = "file-storage";

    public static final String KEY_TYPE = "type";
    public static final String KEY_LOCAL_BASE_PATH = "local.base-path";
    public static final String KEY_MINIO_ENDPOINT = "minio.endpoint";
    public static final String KEY_MINIO_ACCESS_KEY = "minio.access-key";
    public static final String KEY_MINIO_SECRET_KEY = "minio.secret-key";
    public static final String KEY_MINIO_BUCKET_NAME = "minio.bucket-name";

    private static final String DEFAULT_TYPE = "local";
    private static final String DEFAULT_LOCAL_BASE_PATH = "./data/files";
    private static final String DEFAULT_MINIO_BUCKET = "datay-files";

    private final ServiceConfigRepository serviceConfigRepository;

    private final ObjectMapper objectMapper;

    private volatile FileStorageStrategy currentStrategy;

    public FileStorageConfigService(ServiceConfigRepository serviceConfigRepository, ObjectMapper objectMapper) {
        this.serviceConfigRepository = serviceConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FileStorageConfig getConfig() {
        FileStorageConfig cfg = new FileStorageConfig();
        cfg.type = getConfigValue(KEY_TYPE, DEFAULT_TYPE);
        cfg.localBasePath = getConfigValue(KEY_LOCAL_BASE_PATH, DEFAULT_LOCAL_BASE_PATH);
        cfg.minioEndpoint = getConfigValue(KEY_MINIO_ENDPOINT, "");
        cfg.minioAccessKey = getConfigValue(KEY_MINIO_ACCESS_KEY, "");
        cfg.minioSecretKey = getConfigValue(KEY_MINIO_SECRET_KEY, "");
        cfg.minioBucketName = getConfigValue(KEY_MINIO_BUCKET_NAME, DEFAULT_MINIO_BUCKET);
        return cfg;
    }

    private String getConfigValue(String key, String defaultValue) {
        return serviceConfigRepository.findByDfGroupAndDfKey(CONFIG_GROUP, key)
            .map(ServiceConfig::getDfValue)
            .filter(v -> v != null && !v.isEmpty())
            .orElse(defaultValue);
    }

    @Transactional
    public void saveConfig(FileStorageConfig config) {
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

    public FileStorageStrategy buildStrategy(FileStorageConfig config) {
        String type = config.type != null ? config.type.toLowerCase() : DEFAULT_TYPE;
        LOG.info("Building file storage strategy: {}", type);
        if ("minio".equals(type)) {
            return FileStorageStrategyFactory.createMinioStrategy(
                config.minioEndpoint,
                config.minioAccessKey,
                config.minioSecretKey,
                config.minioBucketName != null ? config.minioBucketName : DEFAULT_MINIO_BUCKET
            );
        }
        return FileStorageStrategyFactory.createLocalStrategy(
            config.localBasePath != null ? config.localBasePath : DEFAULT_LOCAL_BASE_PATH
        );
    }

    public synchronized FileStorageStrategy getOrInitializeStrategy() {
        if (currentStrategy == null) {
            currentStrategy = buildStrategy(getConfig());
        }
        return currentStrategy;
    }

    public synchronized FileStorageStrategy reloadStrategy() {
        currentStrategy = buildStrategy(getConfig());
        return currentStrategy;
    }

    public synchronized FileStorageStrategy updateAndReload(FileStorageConfig config) {
        saveConfig(config);
        currentStrategy = buildStrategy(config);
        return currentStrategy;
    }

    public static class FileStorageConfig {
        public String type = DEFAULT_TYPE;
        public String localBasePath = DEFAULT_LOCAL_BASE_PATH;
        public String minioEndpoint = "";
        public String minioAccessKey = "";
        public String minioSecretKey = "";
        public String minioBucketName = DEFAULT_MINIO_BUCKET;
    }
}