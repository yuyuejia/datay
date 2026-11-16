package com.data.datafusion.config;

import java.io.Serializable;

/**
 * ETL 状态存储配置。
 *
 * <p>该对象会在 master 投递任务时随 {@code JobInstance} 一起下发到 worker，
 * 因此需要支持序列化。敏感字段（MinIO 密钥）不会出现在 {@link #toString()} 中。
 */
public class StatusStorageConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String localBasePath;

    private String minioEndpoint;

    private String minioAccessKey;

    private String minioSecretKey;

    private String minioBucketName;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocalBasePath() {
        return localBasePath;
    }

    public void setLocalBasePath(String localBasePath) {
        this.localBasePath = localBasePath;
    }

    public String getMinioEndpoint() {
        return minioEndpoint;
    }

    public void setMinioEndpoint(String minioEndpoint) {
        this.minioEndpoint = minioEndpoint;
    }

    public String getMinioAccessKey() {
        return minioAccessKey;
    }

    public void setMinioAccessKey(String minioAccessKey) {
        this.minioAccessKey = minioAccessKey;
    }

    public String getMinioSecretKey() {
        return minioSecretKey;
    }

    public void setMinioSecretKey(String minioSecretKey) {
        this.minioSecretKey = minioSecretKey;
    }

    public String getMinioBucketName() {
        return minioBucketName;
    }

    public void setMinioBucketName(String minioBucketName) {
        this.minioBucketName = minioBucketName;
    }

    @Override
    public String toString() {
        return (
            "StatusStorageConfig{" +
            "type='" +
            type +
            '\'' +
            ", localBasePath='" +
            localBasePath +
            '\'' +
            ", minioEndpoint='" +
            minioEndpoint +
            '\'' +
            ", minioAccessKey='" +
            minioAccessKey +
            '\'' +
            ", minioBucketName='" +
            minioBucketName +
            '\'' +
            '}'
        );
    }
}
