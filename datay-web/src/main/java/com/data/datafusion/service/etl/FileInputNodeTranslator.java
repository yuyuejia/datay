package com.data.datafusion.service.etl;

import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 「文件数据源」组件翻译器。
 * <p>设计器侧 {@code FileInput} 组件通过 {@code filePath} 字段绑定文件管理中的文件；
 * 翻译器负责：
 * <ol>
 *   <li>补齐文件存储后端（local / minio）的连接参数；</li>
 *   <li>把设计器传来的租户相对路径补成 {@code tenants/{code}/...} 的物理路径，
 *       与 {@link com.data.datafusion.config.DelegatingFileStorageStrategy} 保持完全一致。</li>
 * </ol>
 */
@Component
public class FileInputNodeTranslator implements ETLNodeTranslator {

    public static final String COMPONENT_TYPE = "FileInput";

    public static final String TARGET_COMPONENT = "FileInput";

    private static final String DEFAULT_TENANT = "default";

    private final FileStorageConfigService fileStorageConfigService;

    public FileInputNodeTranslator(FileStorageConfigService fileStorageConfigService) {
        this.fileStorageConfigService = fileStorageConfigService;
    }

    @Override
    public String supportedType() {
        return COMPONENT_TYPE;
    }

    @Override
    public void translate(ETLNodeTranslationContext context) {
        FileStorageConfig storage = fileStorageConfigService.getConfig();

        Map<String, Object> unit = context.getUnit();
        unit.put(".name", TARGET_COMPONENT);
        unit.put("storageType", storage.type);

        String rawPath = context.getConfigString("filePath", null);
        if (rawPath == null || rawPath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件数据源组件未选择文件");
        }
        String physicalPath = toTenantPath(rawPath.trim());
        unit.put("filePath", physicalPath);

        String fmt = context.getConfigString("format", null);
        if (fmt != null && !fmt.isEmpty()) {
            unit.put("format", fmt);
        }

        if ("minio".equalsIgnoreCase(storage.type)) {
            unit.put("minioEndpoint", storage.minioEndpoint);
            unit.put("minioAccessKey", storage.minioAccessKey);
            unit.put("minioSecretKey", storage.minioSecretKey);
            unit.put("minioBucketName", storage.minioBucketName);
            unit.put("minioUrlStyle", "path");
        } else {
            unit.put("localBasePath", storage.localBasePath);
        }

        putIfPresent(context, unit, "csvDelimiter");
        putIfPresent(context, unit, "csvHasHeader");
        putIfPresent(context, unit, "excelSheet");
        putIfPresent(context, unit, "query");
    }

    /**
     * 与 {@link com.data.datafusion.config.DelegatingFileStorageStrategy}
     * 逻辑完全一致，保证翻译后的物理路径与实际文件存储路径对齐。
     */
    private static String toTenantPath(String path) {
        String prefix = tenantPrefix();
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return prefix + "/" + trimmed;
    }

    private static String tenantPrefix() {
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

    private static void putIfPresent(ETLNodeTranslationContext context, Map<String, Object> unit, String key) {
        Object value = context.getConfig().get(key);
        if (value != null) {
            unit.put(key, value);
        }
    }
}