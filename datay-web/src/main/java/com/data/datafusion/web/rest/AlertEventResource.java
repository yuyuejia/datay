package com.data.datafusion.web.rest;

import com.data.datafusion.service.alert.AlertEventService;
import com.data.datafusion.service.dto.AlertDeliveryDTO;
import com.data.datafusion.service.dto.AlertEventDTO;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * 告警事件查询与闭环操作。
 */
@RestController
@RequestMapping("/api/alert-events")
public class AlertEventResource {

    private static final Logger LOG = LoggerFactory.getLogger(AlertEventResource.class);

    private final AlertEventService alertEventService;

    public AlertEventResource(AlertEventService alertEventService) {
        this.alertEventService = alertEventService;
    }

    @GetMapping("")
    public ResponseEntity<List<AlertEventDTO>> getAllAlertEvents(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "severity", required = false) String severity,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "search", required = false) String search
    ) {
        LOG.debug("REST request to get alert events");
        Page<AlertEventDTO> page = alertEventService.findAll(pageable, status, severity, sourceType, search);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getSummary() {
        return ResponseEntity.ok(alertEventService.summary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertEventDTO> getAlertEvent(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(alertEventService.findOne(id));
    }

    @GetMapping("/{id}/deliveries")
    public ResponseEntity<List<AlertDeliveryDTO>> getDeliveries(@PathVariable("id") String id) {
        return ResponseEntity.ok(alertEventService.findDeliveries(id));
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<AlertEventDTO> acknowledge(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(alertEventService.acknowledge(id));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AlertEventDTO> resolve(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(alertEventService.resolve(id));
    }
}
