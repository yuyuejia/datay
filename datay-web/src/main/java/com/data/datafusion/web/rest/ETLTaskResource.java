package com.data.datafusion.web.rest;

import static com.data.datafusion.job.TaskConstants.TASK_STATUS_OFFLINE;

import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.dto.ETLDebugDTO;
import com.data.datafusion.service.dto.ETLDebugResultDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.dto.JobInstanceDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
 * REST controller for managing {@link com.data.datafusion.domain.ETLTask}.
 */
@RestController
@RequestMapping("/api/etl-tasks")
public class ETLTaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTaskResource.class);

    private static final String ENTITY_NAME = "eTLTask";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ETLTaskService eTLTaskService;

    private final ETLTaskRepository eTLTaskRepository;

    private final JobInstanceService jobInstanceService;

    public ETLTaskResource(ETLTaskService eTLTaskService, ETLTaskRepository eTLTaskRepository, JobInstanceService jobInstanceService) {
        this.eTLTaskService = eTLTaskService;
        this.eTLTaskRepository = eTLTaskRepository;
        this.jobInstanceService = jobInstanceService;
    }

    /**
     * {@code POST  /etl-tasks} : Create a new eTLTask.
     *
     * @param eTLTaskDTO the eTLTaskDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new eTLTaskDTO, or with status {@code 400 (Bad Request)} if the eTLTask has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ETLTaskDTO> createETLTask(@RequestBody ETLTaskDTO eTLTaskDTO) throws URISyntaxException {
        LOG.debug("REST request to save ETLTask : {}", eTLTaskDTO);
        if (eTLTaskDTO.getId() != null) {
            throw new BadRequestAlertException("A new eTLTask cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (eTLTaskDTO.getStatus() == null) {
            eTLTaskDTO.setStatus(TASK_STATUS_OFFLINE);
        }
        eTLTaskDTO.setCreateTime(ZonedDateTime.now());
        eTLTaskDTO.setUpdateTime(ZonedDateTime.now());
        eTLTaskDTO.setStatus(TASK_STATUS_OFFLINE);
        eTLTaskDTO = eTLTaskService.save(eTLTaskDTO);
        return ResponseEntity.created(new URI("/api/etl-tasks/" + eTLTaskDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, eTLTaskDTO.getId().toString()))
            .body(eTLTaskDTO);
    }

    /**
     * {@code PUT  /etl-tasks/:id} : Updates an existing eTLTask.
     *
     * @param id the id of the eTLTaskDTO to save.
     * @param eTLTaskDTO the eTLTaskDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLTaskDTO,
     * or with status {@code 400 (Bad Request)} if the eTLTaskDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the eTLTaskDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ETLTaskDTO> updateETLTask(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody ETLTaskDTO eTLTaskDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ETLTask : {}, {}", id, eTLTaskDTO);
        if (eTLTaskDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLTaskDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLTaskRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        eTLTaskDTO.setUpdateTime(ZonedDateTime.now());
        eTLTaskDTO = eTLTaskService.update(eTLTaskDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLTaskDTO.getId().toString()))
            .body(eTLTaskDTO);
    }

    /**
     * {@code PATCH  /etl-tasks/:id} : Partial updates given fields of an existing eTLTask, field will ignore if it is null
     *
     * @param id the id of the eTLTaskDTO to save.
     * @param eTLTaskDTO the eTLTaskDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLTaskDTO,
     * or with status {@code 400 (Bad Request)} if the eTLTaskDTO is not valid,
     * or with status {@code 404 (Not Found)} if the eTLTaskDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the eTLTaskDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ETLTaskDTO> partialUpdateETLTask(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody ETLTaskDTO eTLTaskDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ETLTask partially : {}, {}", id, eTLTaskDTO);
        if (eTLTaskDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLTaskDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLTaskRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ETLTaskDTO> result = eTLTaskService.partialUpdate(eTLTaskDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLTaskDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /etl-tasks} : get all the eTLTasks.
     *
     * @param pageable the pagination information.
     * @param search   the optional keyword used to filter by task name or description.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of eTLTasks in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ETLTaskDTO>> getAllETLTasks(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search
    ) {
        LOG.debug("REST request to get a page of ETLTasks with search: {}", search);
        Page<ETLTaskDTO> page = eTLTaskService.findAll(pageable, search);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /etl-tasks/:id} : get the "id" eTLTask.
     *
     * @param id the id of the eTLTaskDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the eTLTaskDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ETLTaskDTO> getETLTask(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ETLTask : {}", id);
        Optional<ETLTaskDTO> eTLTaskDTO = eTLTaskService.findOne(id);
        return ResponseUtil.wrapOrNotFound(eTLTaskDTO);
    }

    /**
     * {@code DELETE  /etl-tasks/:id} : delete the "id" eTLTask.
     *
     * @param id the id of the eTLTaskDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteETLTask(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ETLTask : {}", id);
        eTLTaskService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    /**
     * {@code POST  /etl-tasks/:id/run} : Execute the ETLTask once.
     *
     * @param id the id of the ETLTask to execute once.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)}.
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<Void> runETLTask(@PathVariable("id") Long id) {
        LOG.debug("REST request to run ETLTask : {}", id);
        eTLTaskService.executeOnce(id);
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST  /etl-tasks/debug} : Debug-run the ETLTask graph with limited data and without writing to sinks.
     *
     * @param request the debug request containing the task graph, row limit and optional target node.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the sampled data per node.
     */
    @PostMapping("/debug")
    public ResponseEntity<ETLDebugResultDTO> debugETLTask(@RequestBody ETLDebugDTO request) {
        LOG.debug("REST request to debug ETLTask : {}", request != null && request.getTask() != null ? request.getTask().getId() : null);
        return ResponseEntity.ok(eTLTaskService.debug(request));
    }

    /**
     * {@code POST  /etl-tasks/:id/online} : Set ETLTask to online status.
     *
     * @param id the id of the ETLTaskDTO to set online.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ETLTaskDTO.
     */
    @PostMapping("/{id}/online")
    public ResponseEntity<ETLTaskDTO> onlineETLTask(@PathVariable("id") Long id) {
        LOG.debug("REST request to online ETLTask : {}", id);
        ETLTaskDTO result = eTLTaskService.online(id);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code POST  /etl-tasks/:id/offline} : Set ETLTask to offline status.
     *
     * @param id the id of the ETLTaskDTO to set offline.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ETLTaskDTO.
     */
    @PostMapping("/{id}/offline")
    public ResponseEntity<ETLTaskDTO> offlineETLTask(@PathVariable("id") Long id) {
        LOG.debug("REST request to offline ETLTask : {}", id);
        ETLTaskDTO result = eTLTaskService.offline(id);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /etl-tasks/:id/instances} : get the job instances for the ETL Task.
     *
     * @param id the id of the ETLTaskDTO.
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of job instances in body.
     */
    @GetMapping("/{id}/instances")
    public ResponseEntity<List<JobInstanceDTO>> getETLTaskInstances(
        @PathVariable("id") Long id,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get instances for ETLTask : {}", id);
        Optional<ETLTaskDTO> eTLTaskDTO = eTLTaskService.findOne(id);
        if (eTLTaskDTO.isEmpty()) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        Long jobId = eTLTaskDTO.get().getJobId();
        if (jobId == null) {
            return ResponseEntity.ok()
                .headers(PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), Page.empty()))
                .body(Collections.emptyList());
        }
        String jobCode = String.valueOf(jobId);
        Page<JobInstanceDTO> page = jobInstanceService.findAllByJobCode(jobCode, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}
