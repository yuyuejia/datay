package com.data.datafusion.web.rest;

import com.data.datafusion.repository.MetricDirectoryRepository;
import com.data.datafusion.service.MetricDirectoryService;
import com.data.datafusion.service.dto.MetricDirectoryDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.data.datafusion.domain.MetricDirectory}.
 */
@RestController
@RequestMapping("/api/metric-directories")
public class MetricDirectoryResource {

    private static final Logger LOG = LoggerFactory.getLogger(MetricDirectoryResource.class);

    private static final String ENTITY_NAME = "metricDirectory";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MetricDirectoryService metricDirectoryService;
    private final MetricDirectoryRepository metricDirectoryRepository;

    public MetricDirectoryResource(
        MetricDirectoryService metricDirectoryService,
        MetricDirectoryRepository metricDirectoryRepository
    ) {
        this.metricDirectoryService = metricDirectoryService;
        this.metricDirectoryRepository = metricDirectoryRepository;
    }

    @PostMapping("")
    public ResponseEntity<MetricDirectoryDTO> createMetricDirectory(@RequestBody MetricDirectoryDTO dto) throws URISyntaxException {
        LOG.debug("REST request to save MetricDirectory : {}", dto);
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new metricDirectory cannot already have an ID", ENTITY_NAME, "idexists");
        }
        dto = metricDirectoryService.save(dto);
        return ResponseEntity.created(new URI("/api/metric-directories/" + dto.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dto.getId().toString()))
            .body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MetricDirectoryDTO> updateMetricDirectory(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody MetricDirectoryDTO dto
    ) throws URISyntaxException {
        LOG.debug("REST request to update MetricDirectory : {}, {}", id, dto);
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!metricDirectoryRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        dto = metricDirectoryService.update(dto);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dto.getId().toString()))
            .body(dto);
    }

    @GetMapping("")
    public ResponseEntity<List<MetricDirectoryDTO>> getAllMetricDirectories() {
        LOG.debug("REST request to get all MetricDirectories");
        return ResponseEntity.ok().body(metricDirectoryService.findAll());
    }

    @GetMapping("/by-parent")
    public ResponseEntity<List<MetricDirectoryDTO>> getMetricDirectoriesByParent(@RequestParam(required = false) Long parentId) {
        LOG.debug("REST request to get MetricDirectories by parentId : {}", parentId);
        return ResponseEntity.ok().body(metricDirectoryService.findByParentId(parentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetricDirectoryDTO> getMetricDirectory(@PathVariable("id") Long id) {
        LOG.debug("REST request to get MetricDirectory : {}", id);
        Optional<MetricDirectoryDTO> dto = metricDirectoryService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMetricDirectory(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete MetricDirectory : {}", id);
        metricDirectoryService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
