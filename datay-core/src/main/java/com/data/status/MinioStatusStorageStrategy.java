package com.data.status;

import com.alibaba.fastjson2.JSON;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MinIO 状态保存策略（master/worker 分离部署时使用，保证多节点共享增量状态）。
 */
public class MinioStatusStorageStrategy implements StatusStorageStrategy {

    private MinioClient minioClient;
    private final String bucketName;
    private static final String STATUS_OBJECT_PREFIX = "etl-status/";
    private static final String STATUS_OBJECT_SUFFIX = ".json";
    private static final String LEGACY_OBJECT = "default.json";

    public MinioStatusStorageStrategy(String endpoint, String accessKey, String secretKey, String bucketName) {
        try {
            this.minioClient = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            this.bucketName = bucketName;
            ensureBucketExists();
        } catch (Exception e) {
            throw new RuntimeException("MinIO客户端初始化失败", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    @Override
    public boolean saveStatus(Map<String, Object> status, String jobCode) {
        try {
            String jsonString = JSON.toJSONString(status);
            String objectName = getObjectName(jobCode);

            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(bytes);

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, bytes.length, -1)
                    .contentType("application/json")
                    .build()
            );
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
                return JSON.parseObject(jsonContent, Map.class);
            }
        } catch (Exception e) {
            System.err.println("从MinIO恢复状态失败: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean deleteStatus(String jobCode) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketName).object(getObjectName(jobCode)).build()
            );
            return true;
        } catch (Exception e) {
            System.err.println("从MinIO删除状态失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> listJobCodes() {
        List<String> jobCodes = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(STATUS_OBJECT_PREFIX).build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) {
                    continue;
                }
                String objectName = item.objectName();
                String name = objectName.substring(STATUS_OBJECT_PREFIX.length());
                if (LEGACY_OBJECT.equals(name) || !name.endsWith(STATUS_OBJECT_SUFFIX)) {
                    continue;
                }
                jobCodes.add(name.substring(0, name.length() - STATUS_OBJECT_SUFFIX.length()));
            }
        } catch (Exception e) {
            System.err.println("列出MinIO状态失败: " + e.getMessage());
        }
        return jobCodes;
    }

    @Override
    public String getStrategyName() {
        return "MinIO";
    }

    private String getObjectName(String jobCode) {
        if (jobCode == null || jobCode.trim().isEmpty()) {
            return STATUS_OBJECT_PREFIX + LEGACY_OBJECT;
        }
        return STATUS_OBJECT_PREFIX + jobCode + STATUS_OBJECT_SUFFIX;
    }
}
