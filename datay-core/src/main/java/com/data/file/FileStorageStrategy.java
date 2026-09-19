package com.data.file;

import java.io.InputStream;
import java.util.List;

public interface FileStorageStrategy {

    String getStrategyName();

    void upload(String path, InputStream inputStream, long size, String contentType);

    InputStream download(String path);

    void delete(String path);

    boolean exists(String path);

    List<FileInfo> list(String prefix);

    default void mkdir(String path) {
    }

    record FileInfo(String path, String name, long size, long lastModified, boolean isDirectory) {
    }
}