package com.data.status;

import java.util.HashMap;
import java.util.Map;

/**
 * Status保存策略工厂
 */
public class StatusStorageStrategyFactory {

    private static final Map<String, StatusStorageStrategy> strategies = new HashMap<>();

    static {
        // 注册默认策略
        strategies.put("local", new LocalFileStatusStorageStrategy());
        registerMinioStrategy("http://127.0.0.1:9000", "IS45iEIUl0DolyJLD8ap", "roEGUK18Bd0qPkOU4ZBMCspB6PtJvwkHRCU11d5z", "test");
    }

    /**
     * 获取策略
     * @param strategyName 策略名称
     * @return 策略实例
     */
    public static StatusStorageStrategy getStrategy(String strategyName) {
        return strategies.get(strategyName);
    }

    /**
     * 注册策略
     * @param strategyName 策略名称
     * @param strategy 策略实例
     */
    public static void registerStrategy(String strategyName, StatusStorageStrategy strategy) {
        strategies.put(strategyName, strategy);
    }

    /**
     * 创建MinIO策略并注册
     * @param endpoint MinIO端点
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     * @param bucketName 存储桶名称
     */
    public static void registerMinioStrategy(String endpoint, String accessKey, String secretKey, String bucketName) {
        MinioStatusStorageStrategy minioStrategy = new MinioStatusStorageStrategy(endpoint, accessKey, secretKey, bucketName);
        strategies.put("minio", minioStrategy);
    }

    /**
     * 获取所有可用策略名称
     * @return 策略名称数组
     */
    public static String[] getAvailableStrategies() {
        return strategies.keySet().toArray(new String[0]);
    }
}
