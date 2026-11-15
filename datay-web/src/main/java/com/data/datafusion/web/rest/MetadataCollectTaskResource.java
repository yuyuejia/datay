package com.data.datafusion.web.rest;

import com.data.datafusion.repository.MetadataCollectTaskRepository;
import com.data.datafusion.service.MetadataCollectTaskService;
import com.data.datafusion.service.dto.MetadataCollectRunDTO;
import com.data.datafusion.service.dto.MetadataCollectTaskDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing data source metadata collection tasks.
 */
@RestController
@RequestMapping("/api/metadata-collect-tasks")
public class MetadataCollectTaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataCollectTaskResource.class);

    private static final String ENTITY_NAME = "metadataCollectTask";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MetadataCollectTaskService taskService;

    private final MetadataCollectTaskRepository taskRepository;

    public MetadataCollectTaskResource(
        MetadataCollectTaskService taskService,
        MetadataCollectTaskRepository taskRepository
    ) {
        this.taskService = taskService;
        this.taskRepository = taskRepository;
    }

    @GetMapping("/by-data-source/{dataSourceId}")
    public ResponseEntity<MetadataCollectTaskDTO> getByDataSource(@PathVariable("dataSourceId") String dataSourceId) {
        return ResponseEntity.ok(taskService.findByDataSourceId(dataSourceId).orElse(null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetadataCollectTaskDTO> getTask(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(taskService.findOne(id));
    }

    @PostMapping("")
    public ResponseEntity<MetadataCollectTaskDTO> saveTask(@RequestBody MetadataCollectTaskDTO dto) {
        try {
            MetadataCollectTaskDTO result = taskService.save(dto);
            if (dto.getId() == null) {
                return ResponseEntity.status(HttpStatus.CREATED)
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
                    .body(result);
            }
            return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, result.getId()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<MetadataCollectRunDTO> executeTask(@PathVariable("id") String id) {
        if (!taskRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        try {
            return ResponseEntity.ok(taskService.execute(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/{id}/runs")
    public ResponseEntity<List<MetadataCollectRunDTO>> getRuns(
        @PathVariable("id") String id,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        Page<MetadataCollectRunDTO> page = taskService.findRuns(id, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable("id") String id) {
        if (!taskRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        taskService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
