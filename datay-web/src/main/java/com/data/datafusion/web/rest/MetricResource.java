package com.data.datafusion.web.rest;

import com.data.datafusion.domain.Metric;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.MetricSqlService;
import com.data.datafusion.service.dto.MetricDTO;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link Metric}.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricResource {

    private static final Logger LOG = LoggerFactory.getLogger(MetricResource.class);

    private static final String ENTITY_NAME = "metric";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MetricService metricService;
    private final MetricSqlService metricSqlService;
    private final MetricQueryService metricQueryService;

    public MetricResource(MetricService metricService, MetricSqlService metricSqlService, MetricQueryService metricQueryService) {
        this.metricService = metricService;
        this.metricSqlService = metricSqlService;
        this.metricQueryService = metricQueryService;
    }

    @PostMapping("")
    public ResponseEntity<MetricDTO> createMetric(@RequestBody MetricDTO metricDTO) throws URISyntaxException {
        LOG.debug("REST request to save Metric : {}", metricDTO);
        if (metricDTO.getId() != null) {
            throw new BadRequestAlertException("A new metric cannot already have an ID", ENTITY_NAME, "idexists");
        }
        MetricDTO result = metricService.save(metricDTO);
        return ResponseEntity.created(new URI("/api/metrics/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MetricDTO> updateMetric(@PathVariable("id") Long id, @RequestBody MetricDTO metricDTO) {
        LOG.debug("REST request to update Metric : {}, {}", id, metricDTO);
        if (metricDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!id.equals(metricDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<MetricDTO> result = metricService.update(id, metricDTO);
        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, metricDTO.getId().toString())
        );
    }

    @GetMapping("")
    public ResponseEntity<List<MetricDTO>> getAllMetrics(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "metricType", required = false) String metricType
    ) {
        LOG.debug("REST request to get a page of Metrics with search: {}, metricType: {}", search, metricType);
        Page<MetricDTO> page = metricService.findAll(pageable, search, metricType);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/all")
    public ResponseEntity<List<MetricDTO>> getAllMetricsSimple() {
        LOG.debug("REST request to get all Metrics (simple)");
        return ResponseEntity.ok().body(metricService.findAllSimple());
    }

    @GetMapping("/by-directory")
    public ResponseEntity<List<MetricDTO>> getMetricsByDirectory(@RequestParam Long directoryId) {
        LOG.debug("REST request to get Metrics by directoryId : {}", directoryId);
        return ResponseEntity.ok().body(metricService.findByDirectoryId(directoryId));
    }

    @GetMapping("/by-type")
    public ResponseEntity<List<MetricDTO>> getMetricsByType(@RequestParam String metricType) {
        LOG.debug("REST request to get Metrics by metricType : {}", metricType);
        return ResponseEntity.ok().body(metricService.findByMetricType(metricType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetricDTO> getMetric(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Metric : {}", id);
        Optional<MetricDTO> metricDTO = metricService.findOne(id);
        return ResponseUtil.wrapOrNotFound(metricDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMetric(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Metric : {}", id);
        try {
            metricService.delete(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "metricInUse");
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PostMapping("/preview-sql")
    public ResponseEntity<Map<String, String>> previewSql(@RequestBody MetricDTO metricDTO) {
        LOG.debug("REST request to preview SQL for Metric : {}", metricDTO);
        try {
            String sql = metricSqlService.generateSql(metricDTO);
            return ResponseEntity.ok().body(Map.of("sql", sql));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "sqlGenerateError");
        }
    }

    @GetMapping("/{id}/preview-sql")
    public ResponseEntity<Map<String, String>> previewSqlById(@PathVariable("id") Long id) {
        LOG.debug("REST request to preview SQL for Metric : {}", id);
        MetricDTO metricDTO = metricService
            .findOne(id)
            .orElseThrow(() -> new BadRequestAlertException("指标不存在", ENTITY_NAME, "idnotfound"));
        try {
            String sql = metricSqlService.generateSql(metricDTO);
            return ResponseEntity.ok().body(Map.of("sql", sql));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "sqlGenerateError");
        }
    }

    @PostMapping("/query-meta")
    public ResponseEntity<Map<String, Object>> getQueryMeta(@RequestBody MetricQueryDTO queryDTO) {
        LOG.debug("REST request to get query meta : {}", queryDTO);
        try {
            return ResponseEntity.ok().body(metricQueryService.queryMeta(queryDTO));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "queryMetaError");
        }
    }

    @PostMapping("/query-sql")
    public ResponseEntity<Map<String, String>> buildQuerySql(@RequestBody MetricQueryDTO queryDTO) {
        LOG.debug("REST request to build query SQL : {}", queryDTO);
        try {
            return ResponseEntity.ok().body(Map.of("sql", metricQueryService.buildSqlFor(queryDTO)));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "querySqlError");
        }
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryMetricData(@RequestBody MetricQueryDTO queryDTO) {
        LOG.debug("REST request to query metric data : {}", queryDTO);
        try {
            return ResponseEntity.ok().body(metricQueryService.query(queryDTO));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "metricQueryError");
        } catch (java.sql.SQLException e) {
            throw new BadRequestAlertException("查询执行失败：" + e.getMessage(), ENTITY_NAME, "metricQueryError");
        }
    }
}
