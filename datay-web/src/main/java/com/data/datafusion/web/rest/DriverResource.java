package com.data.datafusion.web.rest;

import com.data.metadata.DBType;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DriverStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for managing local database JDBC drivers.
 *
 * <p>平台不再自动从 Maven 仓库下载驱动。未预置驱动的数据源由用户上传 jar 到
 * {@code drivers/<type>/default}，上传后自动刷新该类型的驱动类加载器与连接池。
 * 通用 JDBC 驱动按数据源隔离到 {@code drivers/jdbc/<owner>/default}，上传后由前端自动
 * 填充数据源的 {@code jdbc.driverJar}。
 */
@RestController
@RequestMapping("/api/drivers")
public class DriverResource {

    private static final Logger LOG = LoggerFactory.getLogger(DriverResource.class);

    private static final String INVALID_TYPE = "不支持的数据源类型";
    private static final int MAX_UPLOAD_FILES = 10;

    /**
     * {@code GET /drivers} : 列出所有 JDBC 数据源类型及其驱动状态。
     */
    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listDrivers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DBType dbType : DBType.values()) {
            result.add(buildStatus(dbType, null));
        }
        result.add(buildGenericStatus(null, null));
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET /drivers/{type}} : 查询指定类型的驱动状态。
     *
     * <p>通用 JDBC 的驱动按数据源隔离，可通过 {@code owner} 查询指定数据源已上传的驱动。
     */
    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> getDriverStatus(
        @PathVariable("type") String type,
        @RequestParam(value = "version", required = false) String version,
        @RequestParam(value = "owner", required = false) String owner
    ) {
        if (isGeneric(type)) {
            try {
                return ResponseEntity.ok(buildGenericStatus(version, owner));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(error(e.getMessage()));
            }
        }
        DBType dbType = DBType.fromString(type);
        if (dbType == null) {
            return ResponseEntity.badRequest().body(error(INVALID_TYPE + "：" + type));
        }
        return ResponseEntity.ok(buildStatus(dbType, version));
    }

    /**
     * {@code POST /drivers/{type}/upload} : 上传指定类型的驱动 jar（默认替换已有驱动）。
     *
     * <p>通用 JDBC 数据源通过 {@code owner} 区分归属；未传时自动生成唯一归属，
     * 保证不同数据源之间驱动互不共享。
     */
    @PostMapping("/{type}/upload")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadDrivers(
        @PathVariable("type") String type,
        @RequestParam("files") MultipartFile[] files,
        @RequestParam(value = "replace", defaultValue = "true") boolean replace,
        @RequestParam(value = "owner", required = false) String owner
    ) {
        if (isGeneric(type)) {
            return doUpload(DriverStore.GENERIC_JDBC_TYPE, type, null, files, replace, owner);
        }
        DBType dbType = DBType.fromString(type);
        if (dbType == null) {
            return ResponseEntity.badRequest().body(error(INVALID_TYPE + "：" + type));
        }
        return doUpload(dbType.name(), type, dbType, files, replace, null);
    }

    private ResponseEntity<Map<String, Object>> doUpload(
        String driverDirType,
        String requestedType,
        DBType dbType,
        MultipartFile[] files,
        boolean replace,
        String owner
    ) {
        LOG.info("REST request to upload {} driver file(s) for {}", files == null ? 0 : files.length, requestedType);
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(error("请选择要上传的驱动文件"));
        }
        if (files.length > MAX_UPLOAD_FILES) {
            return ResponseEntity.badRequest().body(error("一次最多上传 " + MAX_UPLOAD_FILES + " 个驱动文件"));
        }

        try {
            String driverOwner = dbType == null ? resolveGenericOwner(owner) : null;
            Path tempDir = null;
            try {
                tempDir = Files.createTempDirectory("datay-driver-upload-");
                List<Path> staged = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (MultipartFile file : files) {
                    if (file == null || file.isEmpty()) {
                        return ResponseEntity.badRequest().body(error("上传的驱动文件不能为空"));
                    }
                    String name = DriverStore.sanitizeJarName(file.getOriginalFilename());
                    if (names.contains(name)) {
                        return ResponseEntity.badRequest().body(error("存在同名驱动文件：" + name));
                    }
                    names.add(name);
                    Path target = tempDir.resolve(name).toAbsolutePath();
                    file.transferTo(target);
                    staged.add(target);
                }

                List<File> jars = DriverStore.installDrivers(driverDirType, driverOwner, staged, replace);
                ConnectionPoolManager.invalidateDriver(driverDirType);

                Map<String, Object> result = dbType == null
                    ? buildGenericStatus(null, driverOwner)
                    : buildStatus(dbType, null);
                result.put("success", true);
                result.put(
                    "message",
                    jars.isEmpty() ? "驱动上传成功" : "驱动上传成功，已安装：" + jars.stream().map(File::getName).toList()
                );
                return ResponseEntity.ok(result);
            } finally {
                deleteDirectoryQuietly(tempDir);
            }
        } catch (IOException e) {
            LOG.warn("Invalid driver upload for {}: {}", requestedType, e.getMessage());
            Map<String, Object> result = error("驱动上传失败：" + e.getMessage());
            result.put("success", false);
            return ResponseEntity.badRequest().body(result);
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid driver upload parameters for {}: {}", requestedType, e.getMessage());
            Map<String, Object> result = error("驱动上传失败：" + e.getMessage());
            result.put("success", false);
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            LOG.error("Failed to upload driver for {}", requestedType, e);
            Map<String, Object> result = error("驱动上传失败：" + e.getMessage());
            result.put("success", false);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Map<String, Object> buildStatus(DBType dbType, String version) {
        String resolvedVersion = dbType.resolveVersion(version);
        List<File> jars = DriverStore.listJars(dbType);
        boolean preinstalled = dbType.isDriverPreinstalled();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("type", dbType.name());
        status.put("displayName", dbType.getDisplayName());
        status.put("jdbc", true);
        status.put("version", resolvedVersion);
        status.put("defaultVersion", dbType.getDefaultVersion());
        status.put("supportedVersions", dbType.getSupportedVersions());
        status.put("driverClassName", dbType.getDriverClassName());
        status.put("preinstalled", preinstalled);
        status.put("installed", preinstalled || !jars.isEmpty());
        status.put("jars", jars.stream().map(File::getName).toList());
        status.put("driverDir", DriverStore.getDriverDir(dbType).getAbsolutePath());
        status.put("suggestedJar", dbType.getDriverJarForVersion(resolvedVersion));
        return status;
    }

    private Map<String, Object> buildGenericStatus(String version, String owner) {
        String resolvedOwner = DriverStore.normalizeOwner(owner);
        List<File> jars = resolvedOwner == null
            ? List.of()
            : DriverStore.listJars(DriverStore.GENERIC_JDBC_TYPE, resolvedOwner);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("type", DriverStore.GENERIC_JDBC_TYPE.toUpperCase(Locale.ROOT));
        status.put("displayName", "通用 JDBC");
        status.put("jdbc", true);
        status.put("version", version == null || version.isBlank() ? "default" : version);
        status.put("defaultVersion", "default");
        status.put("supportedVersions", List.of());
        status.put("driverClassName", null);
        status.put("preinstalled", false);
        status.put("installed", !jars.isEmpty());
        status.put("jars", jars.stream().map(File::getName).toList());
        status.put(
            "driverDir",
            resolvedOwner == null ? null : DriverStore.getDriverDir(DriverStore.GENERIC_JDBC_TYPE, resolvedOwner).getAbsolutePath()
        );
        // 填入 jdbc.driverJar 的路径保持相对路径，便于 Web 与 Docker worker 共用同一驱动目录
        status.put(
            "driverPath",
            resolvedOwner == null ? null : DriverStore.getDriverDir(DriverStore.GENERIC_JDBC_TYPE, resolvedOwner).getPath()
        );
        status.put("owner", resolvedOwner);
        status.put("suggestedJar", null);
        return status;
    }

    /**
     * 通用 JDBC 驱动按数据源隔离：沿用已有归属，否则生成新的唯一归属。
     */
    private String resolveGenericOwner(String owner) {
        String normalized = DriverStore.normalizeOwner(owner);
        if (normalized != null) {
            return normalized;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isGeneric(String type) {
        return type != null && DriverStore.GENERIC_JDBC_TYPE.equalsIgnoreCase(type.trim());
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // ignore cleanup failure
                }
            });
        } catch (IOException e) {
            LOG.debug("Failed to clean temp driver upload directory {}", dir, e);
        }
    }
}
