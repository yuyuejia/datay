package com.data.file;

public class FileStorageStrategyFactory {

    public static FileStorageStrategy createLocalStrategy(String basePath) {
        return new LocalFileStorageStrategy(basePath);
    }

    public static FileStorageStrategy createMinioStrategy(String endpoint, String accessKey, String secretKey, String bucketName) {
        return new MinioFileStorageStrategy(endpoint, accessKey, secretKey, bucketName);
    }
}