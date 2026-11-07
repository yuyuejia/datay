package com.data.datafusion.web.rest;

import com.data.datafusion.service.DataQualityResultService;
import com.data.datafusion.service.dto.DataQualityResultDTO;
import com.data.datafusion.service.dto.DataQualityRuleResultDTO;
import com.data.file.FileStorageStrategy;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for data quality results.
 */
@RestController
@RequestMapping("/api/data-quality-results")
public class DataQualityResultResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataQualityResultResource.class);

    private final DataQualityResultService resultService;

    private final FileStorageStrategy fileStorageStrategy;

    public DataQualityResultResource(DataQualityResultService resultService, FileStorageStrategy fileStorageStrategy) {
        this.resultService = resultService;
        this.fileStorageStrategy = fileStorageStrategy;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataQualityResultDTO> getResult(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(resultService.findOne(id));
    }

    @GetMapping("/by-instance-id/{jobInstanceId}")
    public ResponseEntity<DataQualityResultDTO> getResultByInstanceId(@PathVariable("jobInstanceId") String jobInstanceId) {
        return ResponseUtil.wrapOrNotFound(resultService.findByJobInstanceId(jobInstanceId));
    }

    @GetMapping("/by-instance-code/{instanceCode}")
    public ResponseEntity<DataQualityResultDTO> getResultByInstanceCode(@PathVariable("instanceCode") String instanceCode) {
        return ResponseUtil.wrapOrNotFound(resultService.findByInstanceCode(instanceCode));
    }

    /**
     * 结果预览：返回规则级汇总，不返回原始问题数据。
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<List<DataQualityRuleResultDTO>> previewResult(@PathVariable("id") String id) {
        DataQualityResultDTO result = resultService
            .findOne(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据质量结果不存在: " + id));
        return ResponseEntity.ok(result.getRules());
    }

    @GetMapping("/by-instance-id/{jobInstanceId}/download")
    public ResponseEntity<Resource> downloadSummary(@PathVariable("jobInstanceId") String jobInstanceId) {
        DataQualityResultDTO result = resultService
            .findByJobInstanceId(jobInstanceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据质量结果不存在: " + jobInstanceId));
        String path = result.getSummaryPath();
        if (path == null || path.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return download(path, "data-quality-summary.csv");
    }

    @GetMapping("/by-instance-code/{instanceCode}/download")
    public ResponseEntity<Resource> downloadSummaryByCode(@PathVariable("instanceCode") String instanceCode) {
        DataQualityResultDTO result = resultService
            .findByInstanceCode(instanceCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据质量结果不存在: " + instanceCode));
        String path = result.getSummaryPath();
        if (path == null || path.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return download(path, "data-quality-summary.csv");
    }

    @GetMapping("/by-instance-id/{jobInstanceId}/detail")
    public ResponseEntity<Resource> downloadDetail(
        @PathVariable("jobInstanceId") String jobInstanceId,
        @RequestParam("ruleKey") String ruleKey
    ) {
        String path = resultService
            .findDetailPath(jobInstanceId, ruleKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "质量规则明细文件不存在: " + ruleKey));
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return download(path, fileName);
    }

    private ResponseEntity<Resource> download(String path, String defaultFileName) {
        try {
            InputStream inputStream = fileStorageStrategy.download(path);
            Resource resource = new InputStreamResource(inputStream);
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (fileName == null || fileName.isBlank()) {
                fileName = defaultFileName;
            }
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(resource);
        } catch (Exception e) {
            LOG.warn("下载数据质量结果失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "结果文件不存在: " + path, e);
        }
    }
}
