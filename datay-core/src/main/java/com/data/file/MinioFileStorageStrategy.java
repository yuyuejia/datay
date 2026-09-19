package com.data.file;

import io.minio.*;
import io.minio.http.Method;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MinioFileStorageStrategy implements FileStorageStrategy {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioFileStorageStrategy(String endpoint, String accessKey, String secretKey, String bucketName) {
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        this.bucketName = bucketName;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure MinIO bucket exists: " + bucketName, e);
        }
    }

    @Override
    public String getStrategyName() {
        return "minio";
    }

    @Override
    public void upload(String path, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + path, e);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from MinIO: " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            boolean isDirectory = path.endsWith("/");
            if (isDirectory) {
                String prefix = path;
                minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).build())
                    .forEach(itemResult -> {
                        try {
                            io.minio.messages.Item item = itemResult.get();
                            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(item.objectName()).build());
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to delete object: " + itemResult, e);
                        }
                    });
            } else {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(path).build());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from MinIO: " + path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(path).build());
            return true;
        } catch (Exception ignored) {}
        String withSlash = path.endsWith("/") ? path : path + "/";
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(withSlash).build());
            return true;
        } catch (Exception ignored) {}
        String prefix = path.endsWith("/") ? path : path + "/";
        try {
            return minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).build()
            ).iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<FileInfo> list(String prefix) {
        List<FileInfo> result = new ArrayList<>();
        try {
            String normalizedPrefix = prefix == null ? "" : (prefix.endsWith("/") ? prefix : prefix + "/");
            minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(normalizedPrefix)
                    .recursive(true)
                    .build()
            ).forEach(itemResult -> {
                try {
                    io.minio.messages.Item item = itemResult.get();
                    String objectName = item.objectName();
                    if (objectName.equals(normalizedPrefix)) {
                        return;
                    }
                    String relativeName = normalizedPrefix.isEmpty() ? objectName : objectName.substring(normalizedPrefix.length());
                    if (relativeName.isEmpty()) {
                        return;
                    }
                    boolean isDir;
                    String displayName;
                    int slashIdx = relativeName.indexOf('/');
                    if (slashIdx >= 0) {
                        isDir = true;
                        displayName = relativeName.substring(0, slashIdx);
                    } else {
                        isDir = objectName.endsWith("/");
                        displayName = isDir ? relativeName.substring(0, relativeName.length() - 1) : relativeName;
                    }
                    if (displayName.isEmpty()) {
                        return;
                    }
                    if (isDir && result.stream().anyMatch(f -> f.path().equals(normalizedPrefix + displayName + "/"))) {
                        return;
                    }
                    long lastMod = 0;
                    try {
                        if (item.lastModified() != null) {
                            lastMod = item.lastModified().toInstant().toEpochMilli();
                        }
                    } catch (Exception ignored) {}
                    result.add(new FileInfo(
                        normalizedPrefix + displayName + (isDir ? "/" : ""),
                        displayName,
                        isDir ? 0 : item.size(),
                        lastMod,
                        isDir
                    ));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process MinIO item", e);
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list files in MinIO: " + prefix, e);
        }
        return result;
    }

    @Override
    public void mkdir(String path) {
        try {
            String dirPath = path.endsWith("/") ? path : path + "/";
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(dirPath)
                    .stream(new java.io.ByteArrayInputStream(new byte[0]), 0, -1)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directory in MinIO: " + path, e);
        }
    }

    public String getPresignedUrl(String path, long expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(path)
                    .expiry((int) expirySeconds, TimeUnit.SECONDS)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + path, e);
        }
    }
}