package com.data.datafusion.ai.rag;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标知识库（RAG）接口：查看索引状态与手动重建。
 */
@RestController
@RequestMapping("/api/ai/metric-rag")
public class MetricRagResource {

    private static final Logger LOG = LoggerFactory.getLogger(MetricRagResource.class);

    private final MetricRagService metricRagService;

    public MetricRagResource(MetricRagService metricRagService) {
        this.metricRagService = metricRagService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(metricRagService.status());
    }

    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild() {
        try {
            return ResponseEntity.ok(metricRagService.rebuild());
        } catch (IllegalStateException e) {
            LOG.warn("Rebuild metric RAG index failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
