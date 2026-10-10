package com.data.datafusion.web.rest;

import com.data.metadata.DBType;
import com.data.metadata.util.DriverDownloader;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing database driver downloads.
 *
 * <p>平台未内置部分数据库驱动（如达梦），提供查询驱动安装状态与按需从 Maven 仓库下载的能力。
 */
@RestController
@RequestMapping("/api/drivers")
public class DriverResource {

    private static final Logger LOG = LoggerFactory.getLogger(DriverResource.class);

    private static final String NOT_DOWNLOADABLE = "该数据库类型未配置驱动下载源";

    /**
     * {@code GET /drivers} : 列出所有支持下载的数据库类型及其驱动状态。
     */
    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listDrivers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DBType dbType : DBType.values()) {
            if (dbType.isDownloadable()) {
                result.add(buildStatus(dbType, null));
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET /drivers/{type}} : 查询指定类型（默认版本）的驱动状态。
     */
    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> getDriverStatus(
        @PathVariable("type") String type,
        @RequestParam(value = "version", required = false) String version
    ) {
        DBType dbType = DBType.fromString(type);
        if (dbType == null || !dbType.isDownloadable()) {
            return ResponseEntity.badRequest().body(error(NOT_DOWNLOADABLE + "：" + type));
        }
        return ResponseEntity.ok(buildStatus(dbType, version));
    }

    /**
     * {@code POST /drivers/{type}/download} : 下载指定类型的驱动到驱动目录（幂等）。
     */
    @PostMapping("/{type}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> downloadDriver(
        @PathVariable("type") String type,
        @RequestParam(value = "version", required = false) String version
    ) {
        DBType dbType = DBType.fromString(type);
        if (dbType == null || !dbType.isDownloadable()) {
            return ResponseEntity.badRequest().body(error(NOT_DOWNLOADABLE + "：" + type));
        }
        LOG.info("REST request to download driver for {} version {}", type, version);
        try {
            File jar = DriverDownloader.downloadDriver(dbType, version);
            Map<String, Object> result = buildStatus(dbType, version);
            result.put("success", true);
            result.put("path", jar.getAbsolutePath());
            result.put("message", "驱动下载成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to download driver for {}", type, e);
            Map<String, Object> result = error("驱动下载失败：" + e.getMessage());
            result.put("success", false);
            return ResponseEntity.status(500).body(result);
        }
    }

    private Map<String, Object> buildStatus(DBType dbType, String version) {
        String resolvedVersion = dbType.resolveVersion(version);
        boolean installed = DriverDownloader.isDriverInstalled(dbType, version);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("type", dbType.name());
        status.put("displayName", dbType.getDisplayName());
        status.put("version", resolvedVersion);
        status.put("defaultVersion", dbType.getDefaultVersion());
        status.put("supportedVersions", dbType.getSupportedVersions());
        status.put("driverClassName", dbType.getDriverClassName());
        status.put("downloadable", dbType.isDownloadable());
        status.put("installed", installed);
        status.put("driverDir", DriverDownloader.getDriverDir(dbType, version).getAbsolutePath());
        status.put("jarName", dbType.getDriverJarForVersion(resolvedVersion));
        status.put("repos", DriverDownloader.getRepoUrls());
        return status;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}
