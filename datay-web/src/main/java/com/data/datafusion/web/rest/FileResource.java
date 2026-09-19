package com.data.datafusion.web.rest;

import com.data.datafusion.service.FileStorageConfigService;
import com.data.file.FileStorageStrategy;
import com.data.file.FileStorageStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileResource {

    private static final Logger LOG = LoggerFactory.getLogger(FileResource.class);

    private final FileStorageStrategy fileStorageStrategy;

    private final FileStorageConfigService fileStorageConfigService;

    public FileResource(FileStorageStrategy fileStorageStrategy, FileStorageConfigService fileStorageConfigService) {
        this.fileStorageStrategy = fileStorageStrategy;
        this.fileStorageConfigService = fileStorageConfigService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "path", defaultValue = "") String path
    ) {
        LOG.debug("REST request to upload file: {} to path: {}", file.getOriginalFilename(), path);
        try {
            String originalFilename = file.getOriginalFilename();
            String storedName = originalFilename != null ? originalFilename : UUID.randomUUID().toString();
            String fullPath = path.isEmpty() ? storedName : path + "/" + storedName;

            fileStorageStrategy.upload(
                fullPath,
                file.getInputStream(),
                file.getSize(),
                file.getContentType()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("path", fullPath);
            result.put("name", storedName);
            result.put("originalName", originalFilename);
            result.put("size", file.getSize());
            result.put("contentType", file.getContentType());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to upload file", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String path) {
        LOG.debug("REST request to download file: {}", path);
        try {
            InputStream inputStream = fileStorageStrategy.download(path);
            Resource resource = new InputStreamResource(inputStream);

            String fileName = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .body(resource);
        } catch (Exception e) {
            LOG.error("Failed to download file", e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<FileStorageStrategy.FileInfo>> listFiles(
        @RequestParam(value = "path", defaultValue = "") String path
    ) {
        LOG.debug("REST request to list files at path: {}", path);
        List<FileStorageStrategy.FileInfo> files = fileStorageStrategy.list(path);
        return ResponseEntity.ok(files);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestParam("path") String path) {
        LOG.debug("REST request to delete file: {}", path);
        try {
            fileStorageStrategy.delete(path);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to delete file", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/mkdir")
    public ResponseEntity<Map<String, Object>> createDirectory(@RequestParam("path") String path) {
        LOG.debug("REST request to create directory: {}", path);
        try {
            fileStorageStrategy.mkdir(path);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("path", path);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to create directory", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/exists")
    public ResponseEntity<Map<String, Object>> exists(@RequestParam("path") String path) {
        Map<String, Object> result = new HashMap<>();
        result.put("exists", fileStorageStrategy.exists(path));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/storage-config")
    public ResponseEntity<FileStorageConfigService.FileStorageConfig> getStorageConfig() {
        return ResponseEntity.ok(fileStorageConfigService.getConfig());
    }

    @PostMapping("/storage-config")
    public ResponseEntity<Map<String, Object>> updateStorageConfig(
        @RequestBody FileStorageConfigService.FileStorageConfig config
    ) {
        LOG.info("REST request to update file storage config: type={}", config.type);
        try {
            FileStorageStrategy newStrategy = fileStorageConfigService.updateAndReload(config);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("strategy", newStrategy.getStrategyName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to update file storage config", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/storage-config/test")
    public ResponseEntity<Map<String, Object>> testStorageConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            FileStorageStrategy testStrategy = fileStorageConfigService.buildStrategy(fileStorageConfigService.getConfig());
            result.put("success", true);
            result.put("strategy", testStrategy.getStrategyName());
        } catch (Exception e) {
            LOG.error("Failed to test file storage config", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}