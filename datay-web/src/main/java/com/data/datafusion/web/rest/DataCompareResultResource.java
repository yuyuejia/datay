package com.data.datafusion.web.rest;

import com.data.datafusion.service.DataCompareResultService;
import com.data.datafusion.service.dto.DataCompareResultDTO;
import com.data.datafusion.service.dto.DataCompareTableResultDTO;
import com.data.file.FileStorageStrategy;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
 * REST controller for data compare results.
 *
 * <p>执行结果以 JobInstance.id 为主关联键；文件由 ETL 中的 DuckDBSql 组件写入本地或 MinIO。</p>
 */
@RestController
@RequestMapping("/api/data-compare-results")
public class DataCompareResultResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataCompareResultResource.class);

    private final DataCompareResultService resultService;

    private final FileStorageStrategy fileStorageStrategy;

    public DataCompareResultResource(DataCompareResultService resultService, FileStorageStrategy fileStorageStrategy) {
        this.resultService = resultService;
        this.fileStorageStrategy = fileStorageStrategy;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataCompareResultDTO> getResult(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(resultService.findOne(id));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> previewResult(
        @PathVariable("id") String id,
        @RequestParam(value = "limit", defaultValue = "100") int limit,
        @RequestParam(value = "tableIndex", defaultValue = "0") int tableIndex
    ) {
        try {
            return ResponseEntity.ok(resultService.preview(id, limit, tableIndex));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 按 JobInstance.id 查询结果。
     */
    @GetMapping("/by-instance-id/{jobInstanceId}")
    public ResponseEntity<DataCompareResultDTO> getResultByInstanceId(@PathVariable("jobInstanceId") String jobInstanceId) {
        return ResponseUtil.wrapOrNotFound(resultService.findByJobInstanceId(jobInstanceId));
    }

    @GetMapping("/by-instance-code/{instanceCode}")
    public ResponseEntity<DataCompareResultDTO> getResultByInstanceCode(@PathVariable("instanceCode") String instanceCode) {
        return ResponseUtil.wrapOrNotFound(resultService.findByInstanceCode(instanceCode));
    }

    /**
     * 按 JobInstance.id 下载差异结果文件。
     */
    @GetMapping("/by-instance-id/{jobInstanceId}/download")
    public ResponseEntity<Resource> downloadResultByInstanceId(
        @PathVariable("jobInstanceId") String jobInstanceId,
        @RequestParam(value = "tableIndex", defaultValue = "0") int tableIndex
    ) {
        DataCompareResultDTO result = resultService
            .findByJobInstanceId(jobInstanceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到任务执行实例对应的数据比对结果: " + jobInstanceId));
        return download(result, false, tableIndex);
    }

    /**
     * 按 JobInstance.id 下载汇总文件。
     */
    @GetMapping("/by-instance-id/{jobInstanceId}/summary")
    public ResponseEntity<Resource> downloadSummaryByInstanceId(
        @PathVariable("jobInstanceId") String jobInstanceId,
        @RequestParam(value = "tableIndex", defaultValue = "0") int tableIndex
    ) {
        DataCompareResultDTO result = resultService
            .findByJobInstanceId(jobInstanceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到任务执行实例对应的数据比对结果: " + jobInstanceId));
        return download(result, true, tableIndex);
    }

    private ResponseEntity<Resource> download(DataCompareResultDTO result, boolean summary, int tableIndex) {
        DataCompareTableResultDTO table = null;
        if (result.getTables() != null && !result.getTables().isEmpty()) {
            int index = Math.max(0, Math.min(tableIndex, result.getTables().size() - 1));
            table = result.getTables().get(index);
        }
        String path = table == null
            ? (summary ? result.getSummaryPath() : result.getResultPath())
            : (summary ? table.getSummaryPath() : table.getResultPath());
        String format = table == null ? result.getResultFormat() : table.getResultFormat();
        if (path == null || path.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            InputStream inputStream = fileStorageStrategy.download(path);
            Resource resource = new InputStreamResource(inputStream);
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            MediaType mediaType = summary || "CSV".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .body(resource);
        } catch (Exception e) {
            LOG.error("下载数据比对结果文件失败: {}", path, e);
            return ResponseEntity.notFound().build();
        }
    }
}
