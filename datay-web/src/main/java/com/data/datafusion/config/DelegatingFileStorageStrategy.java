package com.data.datafusion.config;

import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService;
import com.data.file.FileStorageStrategy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class DelegatingFileStorageStrategy implements FileStorageStrategy {

    private static final String DEFAULT_TENANT = "default";

    private final FileStorageConfigService configService;

    public DelegatingFileStorageStrategy(FileStorageConfigService configService) {
        this.configService = configService;
    }

    private FileStorageStrategy delegate() {
        return configService.getOrInitializeStrategy();
    }

    private String tenantPrefix() {
        String code = TenantContext.getTenantCode();
        if (code == null || code.isEmpty()) {
            Long id = TenantContext.getTenantId();
            if (id != null) {
                code = String.valueOf(id);
            }
        }
        if (code == null || code.isEmpty()) {
            code = DEFAULT_TENANT;
        }
        return "tenants/" + code;
    }

    private String toTenantPath(String path) {
        String prefix = tenantPrefix();
        String normalized = normalizePath(path);
        if (normalized.isEmpty()) {
            return prefix;
        }
        // 兼容历史数据中可能保存的物理路径（含反斜杠、盘符或 data/files 前缀）
        String marker = prefix + "/";
        int index = normalized.lastIndexOf(marker);
        if (index >= 0) {
            normalized = normalized.substring(index + marker.length());
        } else if (normalized.equals(prefix)) {
            return prefix;
        }
        return prefix + "/" + normalized;
    }

    /**
     * 统一路径分隔符并去掉开头的 ./ 和 /，保证 Windows/Linux 下路径格式一致。
     */
    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String stripTenantPrefix(String path) {
        String prefix = tenantPrefix();
        String normalized = normalizePath(path);
        if (normalized.startsWith(prefix + "/")) {
            return normalized.substring(prefix.length() + 1);
        }
        if (normalized.equals(prefix)) {
            return "";
        }
        return normalized;
    }

    @Override
    public String getStrategyName() {
        return delegate().getStrategyName();
    }

    @Override
    public void upload(String path, InputStream inputStream, long size, String contentType) {
        delegate().upload(toTenantPath(path), inputStream, size, contentType);
    }

    @Override
    public InputStream download(String path) {
        return delegate().download(toTenantPath(path));
    }

    @Override
    public void delete(String path) {
        delegate().delete(toTenantPath(path));
    }

    @Override
    public boolean exists(String path) {
        return delegate().exists(toTenantPath(path));
    }

    @Override
    public List<FileInfo> list(String prefix) {
        List<FileInfo> raw = delegate().list(toTenantPath(prefix));
        List<FileInfo> result = new ArrayList<>(raw.size());
        for (FileInfo info : raw) {
            result.add(new FileInfo(
                stripTenantPrefix(info.path()),
                info.name(),
                info.size(),
                info.lastModified(),
                info.isDirectory()
            ));
        }
        return result;
    }

    @Override
    public void mkdir(String path) {
        delegate().mkdir(toTenantPath(path));
    }
}