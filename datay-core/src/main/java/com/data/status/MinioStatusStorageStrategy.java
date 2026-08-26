package com.data.status;

import com.alibaba.fastjson2.JSON;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * MinIO保存策略
 */
public class MinioStatusStorageStrategy implements StatusStorageStrategy {

    private MinioClient minioClient;
    private String bucketName;
    private static final String STATUS_OBJECT_PREFIX = "etl-status/";

    public MinioStatusStorageStrategy(String endpoint, String accessKey, String secretKey, String bucketName) {
        try {
            this.minioClient = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            this.bucketName = bucketName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO客户端初始化失败", e);
        }
    }

    @Override
    public boolean saveStatus(Map<String, Object> status, String jobCode) {
        try {
            String jsonString = JSON.toJSONString(status);
            String objectName = getObjectName(jobCode);

            InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, jsonString.getBytes(StandardCharsets.UTF_8).length, -1)
                    .contentType("application/json")
                    .build()
            );

            System.out.println("状态保存成功到MinIO，保存状态数量: " + status.size());
            System.out.println("保存的对象路径: " + objectName);
            return true;
        } catch (Exception e) {
            System.err.println("状态保存到MinIO失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> loadStatus(String jobCode) {
        try {
            String objectName = getObjectName(jobCode);

            InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());

            String jsonContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            stream.close();

            if (jsonContent.length() > 0) {
                Map<String, Object> savedStatus = JSON.parseObject(jsonContent, Map.class);
                System.out.println("从MinIO恢复状态成功，恢复状态数量: " + savedStatus.size());
                return savedStatus;
            }
        } catch (Exception e) {
            System.err.println("从MinIO恢复状态失败: " + e.getMessage());
        }
        return null;
    }

    @Override
    public String getStrategyName() {
        return "MinIO";
    }

    private String getObjectName(String jobInstanceId) {
        if (jobInstanceId == null || jobInstanceId.trim().isEmpty()) {
            return STATUS_OBJECT_PREFIX + "default.json";
        }
        return STATUS_OBJECT_PREFIX + jobInstanceId + ".json";
    }
}
