package com.data.datafusion.web.rest;

import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.service.JobDependService;
import com.data.datafusion.service.dto.JobDependDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.JobDepend}.
 */
@RestController
@RequestMapping("/api/job-depends")
public class JobDependResource {

    private static final Logger LOG = LoggerFactory.getLogger(JobDependResource.class);

    private static final String ENTITY_NAME = "jobDepend";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final JobDependService jobDependService;

    private final JobDependRepository jobDependRepository;

    public JobDependResource(JobDependService jobDependService, JobDependRepository jobDependRepository) {
        this.jobDependService = jobDependService;
        this.jobDependRepository = jobDependRepository;
    }

    /**
     * {@code POST  /job-depends} : Create a new jobDepend.
     *
     * @param jobDependDTO the jobDependDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new jobDependDTO, or with status {@code 400 (Bad Request)} if the jobDepend has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<JobDependDTO> createJobDepend(@RequestBody JobDependDTO jobDependDTO) throws URISyntaxException {
        LOG.debug("REST request to save JobDepend : {}", jobDependDTO);
        if (jobDependDTO.getId() != null) {
            throw new BadRequestAlertException("A new jobDepend cannot already have an ID", ENTITY_NAME, "idexists");
        }
        jobDependDTO = jobDependService.save(jobDependDTO);
        return ResponseEntity.created(new URI("/api/job-depends/" + jobDependDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, jobDependDTO.getId().toString()))
            .body(jobDependDTO);
    }

    /**
     * {@code PUT  /job-depends/:id} : Updates an existing jobDepend.
     *
     * @param id the id of the jobDependDTO to save.
     * @param jobDependDTO the jobDependDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobDependDTO,
     * or with status {@code 400 (Bad Request)} if the jobDependDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the jobDependDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<JobDependDTO> updateJobDepend(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody JobDependDTO jobDependDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update JobDepend : {}, {}", id, jobDependDTO);
        if (jobDependDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, jobDependDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!jobDependRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        jobDependDTO = jobDependService.update(jobDependDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, jobDependDTO.getId().toString()))
            .body(jobDependDTO);
    }

    /**
     * {@code PATCH  /job-depends/:id} : Partial updates given fields of an existing jobDepend, field will ignore if it is null
     *
     * @param id the id of the jobDependDTO to save.
     * @param jobDependDTO the jobDependDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated jobDependDTO,
     * or with status {@code 400 (Bad Request)} if the jobDependDTO is not valid,
     * or with status {@code 404 (Not Found)} if the jobDependDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the jobDependDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<JobDependDTO> partialUpdateJobDepend(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody JobDependDTO jobDependDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update JobDepend partially : {}, {}", id, jobDependDTO);
        if (jobDependDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, jobDependDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!jobDependRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<JobDependDTO> result = jobDependService.partialUpdate(jobDependDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, jobDependDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /job-depends} : get all the jobDepends.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of jobDepends in body.
     */
    @GetMapping("")
    public ResponseEntity<List<JobDependDTO>> getAllJobDepends(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of JobDepends");
        Page<JobDependDTO> page = jobDependService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /job-depends/:id} : get the "id" jobDepend.
     *
     * @param id the id of the jobDependDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the jobDependDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobDependDTO> getJobDepend(@PathVariable("id") String id) {
        LOG.debug("REST request to get JobDepend : {}", id);
        Optional<JobDependDTO> jobDependDTO = jobDependService.findOne(id);
        return ResponseUtil.wrapOrNotFound(jobDependDTO);
    }

    /**
     * {@code DELETE  /job-depends/:id} : delete the "id" jobDepend.
     *
     * @param id the id of the jobDependDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJobDepend(@PathVariable("id") String id) {
        LOG.debug("REST request to delete JobDepend : {}", id);
        jobDependService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
