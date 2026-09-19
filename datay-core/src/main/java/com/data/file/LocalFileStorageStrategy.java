package com.data.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LocalFileStorageStrategy implements FileStorageStrategy {

    private final String basePath;

    public LocalFileStorageStrategy(String basePath) {
        this.basePath = basePath;
        try {
            Files.createDirectories(Paths.get(basePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base directory: " + basePath, e);
        }
    }

    @Override
    public String getStrategyName() {
        return "local";
    }

    @Override
    public void upload(String path, InputStream inputStream, long size, String contentType) {
        try {
            Path fullPath = Paths.get(basePath, path);
            Files.createDirectories(fullPath.getParent());
            Files.copy(inputStream, fullPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to local storage: " + path, e);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            File file = Paths.get(basePath, path).toFile();
            if (!file.exists()) {
                throw new RuntimeException("File not found: " + path);
            }
            return new FileInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download file from local storage: " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path fullPath = Paths.get(basePath, path);
            File file = fullPath.toFile();
            if (file.isDirectory()) {
                deleteDirectory(file);
            } else {
                Files.deleteIfExists(fullPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file from local storage: " + path, e);
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    @Override
    public boolean exists(String path) {
        return Paths.get(basePath, path).toFile().exists();
    }

    @Override
    public List<FileInfo> list(String prefix) {
        List<FileInfo> result = new ArrayList<>();
        try {
            Path rootPath = Paths.get(basePath, prefix == null ? "" : prefix);
            if (!Files.exists(rootPath)) {
                return result;
            }
            try (Stream<Path> paths = Files.list(rootPath)) {
                paths.forEach(p -> {
                    File file = p.toFile();
                    String relativePath = p.toString().replace(basePath, "").replaceFirst("^/", "");
                    result.add(new FileInfo(
                        relativePath,
                        file.getName(),
                        file.isDirectory() ? 0 : file.length(),
                        file.lastModified(),
                        file.isDirectory()
                    ));
                });
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list files in local storage: " + prefix, e);
        }
        return result;
    }

    @Override
    public void mkdir(String path) {
        try {
            Files.createDirectories(Paths.get(basePath, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }
}