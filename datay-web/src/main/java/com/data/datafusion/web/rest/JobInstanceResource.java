package com.data.datafusion.web.rest;

import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.dto.JobInstanceDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
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
 * REST controller for managing {@link com.data.datafusion.domain.JobInstance}.
 */
@RestController
@RequestMapping("/api/job-instances")
public class JobInstanceResource {

    private static final Logger LOG = LoggerFactory.getLogger(JobInstanceResource.class);

    private static final String ENTITY_NAME = "jobInstance";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final JobInstanceService jobInstanceService;

    private final JobInstanceRepository jobInstanceRepository;

    public JobInstanceResource(JobInstanceService jobInstanceService, JobInstanceRepository jobInstanceRepository) {
        this.jobInstanceService = jobInstanceService;
        this.jobInstanceRepository = jobInstanceRepository;
    }

    /**
     * {@code POST  /job-instances} : Create a new jobInstance.
     *
     * @param jobInstanceDTO the jobInstanceDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new jobInstanceDTO, or with status {@code 400 (Bad Request)} if the jobInstance has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<JobInstanceDTO> createJobInstance(@RequestBody JobInstanceDTO jobInstanceDTO) throws URISyntaxException {
        LOG.debug("REST request to save JobInstance : {}", jobInstanceDTO);
        if (jobInstanceDTO.getId() != null) {
            throw new BadRequestAlertException("A new jobInstance cannot already have an ID", ENTITY_NAME, "idexists");
        }
        jobInstanceDTO = jobInstanceService.save(jobInstanceDTO);
        return ResponseEntity.created(new URI("/api/job-instances/" + jobInstanceDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, jobInstanceDTO.getId().toString()))
            .body(jobInstanceDTO);
    }

    /**
     * {@code PUT  /job-instances/:id} : Updates an existing jobInstance.
     *
     * @param id the id of the jobInstanceDTO to save.
     * @param jobInstanceDTO the jobInstanceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobInstanceDTO,
     * or with status {@code 400 (Bad Request)} if the jobInstanceDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the jobInstanceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<JobInstanceDTO> updateJobInstance(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody JobInstanceDTO jobInstanceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update JobInstance : {}, {}", id, jobInstanceDTO);
        if (jobInstanceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, jobInstanceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!jobInstanceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        jobInstanceDTO = jobInstanceService.update(jobInstanceDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, jobInstanceDTO.getId().toString()))
            .body(jobInstanceDTO);
    }

    /**
     * {@code PATCH  /job-instances/:id} : Partial updates given fields of an existing jobInstance, field will ignore if it is null
     *
     * @param id the id of the jobInstanceDTO to save.
     * @param jobInstanceDTO the jobInstanceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobInstanceDTO,
     * or with status {@code 400 (Bad Request)} if the jobInstanceDTO is not valid,
     * or with status {@code 404 (Not Found)} if the jobInstanceDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the jobInstanceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<JobInstanceDTO> partialUpdateJobInstance(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody JobInstanceDTO jobInstanceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update JobInstance partially : {}, {}", id, jobInstanceDTO);
        if (jobInstanceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, jobInstanceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!jobInstanceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<JobInstanceDTO> result = jobInstanceService.partialUpdate(jobInstanceDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, jobInstanceDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /job-instances} : get all the jobInstances.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of jobInstances in body.
     */
    @GetMapping("")
    public ResponseEntity<List<JobInstanceDTO>> getAllJobInstances(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of JobInstances");
        Page<JobInstanceDTO> page = jobInstanceService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /job-instances/:id} : get the "id" jobInstance.
     *
     * @param id the id of the jobInstanceDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the jobInstanceDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobInstanceDTO> getJobInstance(@PathVariable("id") Long id) {
        LOG.debug("REST request to get JobInstance : {}", id);
        Optional<JobInstanceDTO> jobInstanceDTO = jobInstanceService.findOne(id);
        return ResponseUtil.wrapOrNotFound(jobInstanceDTO);
    }

    /**
     * {@code DELETE  /job-instances/:id} : delete the "id" jobInstance.
     *
     * @param id the id of the jobInstanceDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJobInstance(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete JobInstance : {}", id);
        jobInstanceService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
