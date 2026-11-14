package com.data.datafusion.web.rest;

import com.data.datafusion.service.DataApiCallLogService;
import com.data.datafusion.service.dto.DataApiCallLogDTO;
import com.data.datafusion.service.dto.DataApiCallStatsDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * 数据服务调用日志与调用统计接口。
 */
@RestController
@RequestMapping("/api/data-apis/{apiId}")
public class DataApiCallLogResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiCallLogResource.class);

    private static final String ENTITY_NAME = "dataApiCallLog";

    private final DataApiCallLogService callLogService;

    public DataApiCallLogResource(DataApiCallLogService callLogService) {
        this.callLogService = callLogService;
    }

    /**
     * {@code GET /api/data-apis/:apiId/call-logs} : 分页查询数据服务调用日志。
     */
    @GetMapping("/call-logs")
    public ResponseEntity<List<DataApiCallLogDTO>> getCallLogs(
        @PathVariable("apiId") String apiId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LOG.debug("REST request to get DataApi call logs : {}, status={}, startDate={}, endDate={}", apiId, status, startDate, endDate);
        Page<DataApiCallLogDTO> page = callLogService.findLogs(apiId, pageable, status, startDate, endDate);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET /api/data-apis/:apiId/call-stats} : 查询数据服务调用统计。
     */
    @GetMapping("/call-stats")
    public ResponseEntity<DataApiCallStatsDTO> getCallStats(
        @PathVariable("apiId") String apiId,
        @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LOG.debug("REST request to get DataApi call stats : {}, startDate={}, endDate={}", apiId, startDate, endDate);
        try {
            return ResponseEntity.ok().body(callLogService.getStats(apiId, startDate, endDate));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidCallStats");
        }
    }

    /**
     * {@code DELETE /api/data-apis/:apiId/call-logs} : 清空数据服务调用日志。
     */
    @DeleteMapping("/call-logs")
    public ResponseEntity<Void> clearCallLogs(@PathVariable("apiId") String apiId) {
        LOG.debug("REST request to clear DataApi call logs : {}", apiId);
        callLogService.clear(apiId);
        return ResponseEntity.noContent().build();
    }
}
