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
            Files.createDirectories(baseDir());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base directory: " + basePath, e);
        }
    }

    private Path baseDir() {
        return Paths.get(basePath).toAbsolutePath().normalize();
    }

    /**
     * 把存储相对路径解析为绝对路径，并防止通过 ../ 或绝对路径逃逸出存储根目录。
     */
    private Path resolve(String path) {
        Path base = baseDir();
        Path resolved = path == null || path.isEmpty() ? base : base.resolve(path).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Path is outside of storage root: " + path);
        }
        return resolved;
    }

    @Override
    public String getStrategyName() {
        return "local";
    }

    @Override
    public void upload(String path, InputStream inputStream, long size, String contentType) {
        try {
            Path fullPath = resolve(path);
            Files.createDirectories(fullPath.getParent());
            Files.copy(inputStream, fullPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to local storage: " + path, e);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            File file = resolve(path).toFile();
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
            Path fullPath = resolve(path);
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
        return resolve(path).toFile().exists();
    }

    @Override
    public List<FileInfo> list(String prefix) {
        List<FileInfo> result = new ArrayList<>();
        try {
            Path base = baseDir();
            Path rootPath = resolve(prefix == null ? "" : prefix);
            if (!Files.exists(rootPath)) {
                return result;
            }
            try (Stream<Path> paths = Files.list(rootPath)) {
                paths.forEach(p -> {
                    File file = p.toFile();
                    // 统一使用 / 作为返回路径分隔符，避免 Windows 下返回带盘符或反斜杠的物理路径
                    String relativePath = base.relativize(p).toString().replace(File.separatorChar, '/');
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
            Files.createDirectories(resolve(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }
}