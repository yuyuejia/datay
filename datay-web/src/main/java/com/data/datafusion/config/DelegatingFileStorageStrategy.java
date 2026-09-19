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
        if (path == null || path.isEmpty()) {
            return prefix;
        }
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return prefix + "/" + trimmed;
    }

    private String stripTenantPrefix(String path) {
        String prefix = tenantPrefix();
        if (path == null) {
            return null;
        }
        if (path.startsWith(prefix + "/")) {
            return path.substring(prefix.length() + 1);
        }
        if (path.equals(prefix)) {
            return "";
        }
        return path;
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