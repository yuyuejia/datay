package com.data.datafusion.service.etl;

import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.job.ComponentDescriptor;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 「数据质量检核」组件翻译器。
 *
 * <p>设计器只负责规则与检核模式配置；翻译器负责注入当前文件存储配置、租户前缀，
 * 并为每个节点生成唯一的源表名、输出目录和结果前缀。</p>
 */
@Component
public class DataQualityCheckNodeTranslator implements ETLNodeTranslator {

    public static final String COMPONENT_TYPE = "DataQualityCheck";

    private static final String DEFAULT_TENANT = "default";

    private final FileStorageConfigService fileStorageConfigService;

    public DataQualityCheckNodeTranslator(FileStorageConfigService fileStorageConfigService) {
        this.fileStorageConfigService = fileStorageConfigService;
    }

    @Override
    public String supportedType() {
        return COMPONENT_TYPE;
    }

    @Override
    public ComponentDescriptor descriptor() {
        return new ComponentDescriptor(
            COMPONENT_TYPE,
            "数据质量检核",
            "数据质量",
            "按表级/字段级规则检核上游数据，结果写入文件存储，质量不达标时任务失败。",
            getClass().getName(),
            10
        );
    }

    @Override
    public void translate(ETLNodeTranslationContext context) {
        Map<String, Object> unit = context.getUnit();
        unit.put(".name", COMPONENT_TYPE);
        for (String key : context.getConfig().keySet()) {
            unit.put(key, context.getConfig().get(key));
        }

        String nodeCode = context.getNode() != null && context.getNode().getCode() != null
            ? context.getNode().getCode()
            : "dq";
        String safeCode = safe(nodeCode);
        String checkMode = context.getConfigString("checkMode", "FLOW").trim().toUpperCase();
        String sourceTable = context.getConfigString("sourceTable", null);
        if (!"TABLE".equals(checkMode)) {
            checkMode = "FLOW";
            // FLOW 模式始终使用节点专属临时表，避免误用/清空上游已有的 DuckDB 表。
            sourceTable = "dq_data_" + safeCode;
        } else if (sourceTable == null || sourceTable.isBlank()) {
            throw new IllegalArgumentException("数据质量检核组件在 TABLE 模式下必须配置 DuckDB 源表");
        }
        unit.put("checkMode", checkMode);
        unit.put("sourceTable", sourceTable.trim());
        unit.put("outputDir", firstNonBlank(context.getConfigString("outputDir", null), "data-quality/etl/" + safeCode));
        unit.put("resultPrefix", firstNonBlank(context.getConfigString("resultPrefix", null), "quality_" + safeCode));
        unit.put("tenantPrefix", tenantPrefix());
        unit.putIfAbsent("failOnError", true);
        unit.putIfAbsent("failOnWarn", false);
        unit.putIfAbsent("writeResult", true);
        unit.putIfAbsent("sampleLimit", 100);
        unit.putIfAbsent("outputFormat", "CSV");

        FileStorageConfig storage = fileStorageConfigService.getConfig();
        unit.put("storageType", storage.type);
        if ("minio".equalsIgnoreCase(storage.type)) {
            unit.put("minioEndpoint", storage.minioEndpoint);
            unit.put("minioAccessKey", storage.minioAccessKey);
            unit.put("minioSecretKey", storage.minioSecretKey);
            unit.put("minioBucketName", storage.minioBucketName);
            unit.put("minioUrlStyle", "path");
        } else {
            unit.put("localBasePath", storage.localBasePath);
        }
    }

    private static String firstNonBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "dq";
        }
        return value.replaceAll("[^\\p{L}\\p{N}_-]", "_");
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
}
