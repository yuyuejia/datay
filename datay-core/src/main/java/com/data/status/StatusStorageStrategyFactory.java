package com.data.status;

import java.util.HashMap;
import java.util.Map;

/**
 * Status保存策略工厂。
 *
 * <p>维护命名策略注册表，并暴露一个当前生效的策略 {@link #getActiveStrategy()}。
 * 单机部署默认使用 local；master/worker 分离部署时由上层（datay-web）根据配置
 * 注册并使用 minio，从而让多节点共享增量状态。
 */
public class StatusStorageStrategyFactory {

    private static final Map<String, StatusStorageStrategy> strategies = new HashMap<>();

    private static volatile StatusStorageStrategy activeStrategy;

    static {
        // 注册默认策略
        strategies.put("local", new LocalFileStatusStorageStrategy());
    }

    /**
     * 获取指定名称的策略。
     *
     * @param strategyName 策略名称
     * @return 策略实例，不存在时返回 null
     */
    public static StatusStorageStrategy getStrategy(String strategyName) {
        return strategies.get(strategyName);
    }

    /**
     * 注册策略。
     *
     * @param strategyName 策略名称
     * @param strategy 策略实例
     */
    public static void registerStrategy(String strategyName, StatusStorageStrategy strategy) {
        strategies.put(strategyName, strategy);
    }

    /**
     * 创建MinIO策略并注册。
     *
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
     * 获取当前生效的策略，未显式设置时回退到 local。
     *
     * @return 当前生效的策略
     */
    public static StatusStorageStrategy getActiveStrategy() {
        StatusStorageStrategy current = activeStrategy;
        if (current != null) {
            return current;
        }
        return strategies.get("local");
    }

    /**
     * 设置当前生效的策略。
     *
     * @param strategy 策略实例，为 null 时回退到 local
     */
    public static void setActiveStrategy(StatusStorageStrategy strategy) {
        activeStrategy = strategy;
    }

    /**
     * 获取所有可用策略名称。
     *
     * @return 策略名称数组
     */
    public static String[] getAvailableStrategies() {
        return strategies.keySet().toArray(new String[0]);
    }
}
