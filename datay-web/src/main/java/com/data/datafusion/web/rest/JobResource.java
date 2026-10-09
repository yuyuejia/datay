package com.data.datafusion.web.rest;

import com.data.datafusion.domain.Job;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.JobService;
import com.data.datafusion.service.dto.JobDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.Job}.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobResource {

    private static final Logger LOG = LoggerFactory.getLogger(JobResource.class);

    private static final String ENTITY_NAME = "job";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final JobService jobService;

    private final JobRepository jobRepository;

    public JobResource(JobService jobService, JobRepository jobRepository) {
        this.jobService = jobService;
        this.jobRepository = jobRepository;
    }

    /**
     * {@code POST  /jobs} : Create a new job.
     *
     * @param jobDTO the jobDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new jobDTO, or with status {@code 400 (Bad Request)} if the job has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<JobDTO> createJob(@RequestBody JobDTO jobDTO) throws URISyntaxException {
        LOG.debug("REST request to save Job : {}", jobDTO);
        if (jobDTO.getId() != null) {
            throw new BadRequestAlertException("A new job cannot already have an ID", ENTITY_NAME, "idexists");
        }
        jobDTO = jobService.save(jobDTO);
        return ResponseEntity.created(new URI("/api/jobs/" + jobDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, jobDTO.getId().toString()))
            .body(jobDTO);
    }

    /**
     * {@code PUT  /jobs/:id} : Updates an existing job.
     *
     * @param id the id of the jobDTO to save.
     * @param jobDTO the jobDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobDTO,
     * or with status {@code 400 (Bad Request)} if the jobDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the jobDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<JobDTO> updateJob(@PathVariable(value = "id", required = false) final String id, @RequestBody JobDTO jobDTO)
        throws URISyntaxException {
        LOG.debug("REST request to update Job : {}, {}", id, jobDTO);
        if (jobDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, jobDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!jobRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        jobDTO = jobService.update(jobDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, jobDTO.getId().toString()))
            .body(jobDTO);
    }

    /**
     * {@code PATCH  /jobs/:id} : Partial updates given fields of an existing job, field will ignore if it is null
     *
     * @param id the id of the jobDTO to save.
     * @param jobDTO the jobDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobDTO,
     * or with status {@code 400 (Bad Request)} if the jobDTO is not valid,
     * or with status {@code 404 (Not Found)} if the jobDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the jobDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<JobDTO> partialUpdateJob(@PathVariable(value = "id", required = false) final String id, @RequestBody JobDTO jobDTO)
        throws URISyntaxException {
        LOG.debug("REST request to partial update Job partially : {}, {}", id, jobDTO);
        if (jobDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, jobDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!jobRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<JobDTO> result = jobService.partialUpdate(jobDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, jobDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /jobs} : get all the jobs.
     *
     * @param pageable the pagination information.
     * @param search   the optional keyword used to filter by job name or type.
     * @param type     the job type to include (nullable).
     * @param typeNot  the job type to exclude (nullable).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of jobs in body.
     */
    @GetMapping("")
    public ResponseEntity<List<JobDTO>> getAllJobs(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "typeNot", required = false) String typeNot
    ) {
        LOG.debug("REST request to get a page of Jobs with search: {}, type: {}, typeNot: {}", search, type, typeNot);
        Page<JobDTO> page = jobService.findAll(pageable, search, type, typeNot);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /jobs/:id} : get the "id" job.
     *
     * @param id the id of the jobDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the jobDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobDTO> getJob(@PathVariable("id") String id) {
        LOG.debug("REST request to get Job : {}", id);
        Optional<JobDTO> jobDTO = jobService.findOne(id);
        return ResponseUtil.wrapOrNotFound(jobDTO);
    }

    /**
     * {@code DELETE  /jobs/:id} : delete the "id" job.
     *
     * @param id the id of the jobDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Job : {}", id);
        jobService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    /**
     * {@code POST  /jobs/:id/run} : run the "id" job.
     *
     * @param id the id of the jobDTO to run.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)}.
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<Void> runJob(@PathVariable("id") String id) {
        LOG.debug("REST request to run Job : {}", id);
        jobService.executeOnce(jobService.findOneJob(id).get());
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST  /jobs/:id/online} : Set the "id" job to online status and schedule it.
     *
     * @param id the id of the jobDTO to set online.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobDTO.
     */
    @PostMapping("/{id}/online")
    public ResponseEntity<JobDTO> onlineJob(@PathVariable("id") String id) {
        LOG.debug("REST request to online Job : {}", id);
        Job job = jobService
            .findOneJob(id)
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));
        jobService.online(job, false);
        JobDTO updated = jobService.findOne(id).get();
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, updated.getId().toString()))
            .body(updated);
    }

    /**
     * {@code POST  /jobs/:id/offline} : Set the "id" job to offline status and cancel its schedule.
     *
     * @param id the id of the jobDTO to set offline.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobDTO.
     */
    @PostMapping("/{id}/offline")
    public ResponseEntity<JobDTO> offlineJob(@PathVariable("id") String id) {
        LOG.debug("REST request to offline Job : {}", id);
        Job job = jobService
            .findOneJob(id)
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));
        jobService.offline(job);
        JobDTO updated = jobService.findOne(id).get();
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, updated.getId().toString()))
            .body(updated);
    }
}