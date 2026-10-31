package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DataCompareTaskRepository;
import com.data.datafusion.service.DataCompareResultService;
import com.data.datafusion.service.DataCompareService;
import com.data.datafusion.service.dto.DataCompareResultDTO;
import com.data.datafusion.service.dto.DataCompareTaskDTO;
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
 * REST controller for managing data compare tasks.
 */
@RestController
@RequestMapping("/api/data-compare-tasks")
public class DataCompareTaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataCompareTaskResource.class);

    private static final String ENTITY_NAME = "dataCompareTask";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataCompareService dataCompareService;

    private final DataCompareResultService dataCompareResultService;

    private final DataCompareTaskRepository dataCompareTaskRepository;

    public DataCompareTaskResource(
        DataCompareService dataCompareService,
        DataCompareResultService dataCompareResultService,
        DataCompareTaskRepository dataCompareTaskRepository
    ) {
        this.dataCompareService = dataCompareService;
        this.dataCompareResultService = dataCompareResultService;
        this.dataCompareTaskRepository = dataCompareTaskRepository;
    }

    @PostMapping("")
    public ResponseEntity<DataCompareTaskDTO> createDataCompareTask(@RequestBody DataCompareTaskDTO dto) throws Exception {
        LOG.debug("REST request to save DataCompareTask : {}", dto);
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new data compare task cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DataCompareTaskDTO result;
        try {
            result = dataCompareService.save(dto);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.created(new URI("/api/data-compare-tasks/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataCompareTaskDTO> updateDataCompareTask(@PathVariable("id") String id, @RequestBody DataCompareTaskDTO dto) {
        LOG.debug("REST request to update DataCompareTask : {}, {}", id, dto);
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!dataCompareTaskRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        DataCompareTaskDTO result;
        try {
            result = dataCompareService.update(dto);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    @GetMapping("")
    public ResponseEntity<List<DataCompareTaskDTO>> getAllDataCompareTasks(
        @RequestParam(value = "search", required = false) String search,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get a page of DataCompareTasks, search={}", search);
        Page<DataCompareTaskDTO> page = dataCompareService.findAll(pageable, search);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataCompareTaskDTO> getDataCompareTask(@PathVariable("id") String id) {
        LOG.debug("REST request to get DataCompareTask : {}", id);
        Optional<DataCompareTaskDTO> dto = dataCompareService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataCompareTask(@PathVariable("id") String id) {
        LOG.debug("REST request to delete DataCompareTask : {}", id);
        dataCompareService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }

    /**
     * 立即执行数据比对任务。
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<DataCompareResultDTO> executeDataCompareTask(@PathVariable("id") String id) {
        LOG.debug("REST request to execute DataCompareTask : {}", id);
        try {
            return ResponseEntity.ok(dataCompareService.execute(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<DataCompareResultDTO>> getResults(
        @PathVariable("id") String id,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get data compare results for task : {}", id);
        Page<DataCompareResultDTO> page = dataCompareResultService.findByTaskId(id, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}
