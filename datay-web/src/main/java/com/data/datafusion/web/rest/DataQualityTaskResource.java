package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.service.DataQualityResultService;
import com.data.datafusion.service.DataQualityTaskService;
import com.data.datafusion.service.dto.DataQualityResultDTO;
import com.data.datafusion.service.dto.DataQualityTaskDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * REST controller for managing data quality tasks.
 */
@RestController
@RequestMapping("/api/data-quality-tasks")
public class DataQualityTaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataQualityTaskResource.class);

    private static final String ENTITY_NAME = "dataQualityTask";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataQualityTaskService taskService;

    private final DataQualityResultService resultService;

    private final DataQualityTaskRepository taskRepository;

    public DataQualityTaskResource(
        DataQualityTaskService taskService,
        DataQualityResultService resultService,
        DataQualityTaskRepository taskRepository
    ) {
        this.taskService = taskService;
        this.resultService = resultService;
        this.taskRepository = taskRepository;
    }

    @PostMapping("")
    public ResponseEntity<DataQualityTaskDTO> createTask(@RequestBody DataQualityTaskDTO dto) throws Exception {
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new data quality task cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DataQualityTaskDTO result;
        try {
            result = taskService.save(dto);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.created(new URI("/api/data-quality-tasks/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataQualityTaskDTO> updateTask(@PathVariable("id") String id, @RequestBody DataQualityTaskDTO dto) {
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!taskRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        try {
            return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id))
                .body(taskService.update(dto));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("")
    public ResponseEntity<List<DataQualityTaskDTO>> getAllTasks(
        @RequestParam(value = "search", required = false) String search,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        Page<DataQualityTaskDTO> page = taskService.findAll(pageable, search);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataQualityTaskDTO> getTask(@PathVariable("id") String id) {
        Optional<DataQualityTaskDTO> dto = taskService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable("id") String id) {
        taskService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<DataQualityResultDTO> executeTask(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok(taskService.execute(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<DataQualityResultDTO>> getResults(
        @PathVariable("id") String id,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        Page<DataQualityResultDTO> page = resultService.findByTaskId(id, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}
