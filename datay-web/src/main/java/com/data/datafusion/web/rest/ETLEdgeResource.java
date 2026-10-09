package com.data.datafusion.web.rest;

import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.service.ETLEdgeService;
import com.data.datafusion.service.dto.ETLEdgeDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.ETLEdge}.
 */
@RestController
@RequestMapping("/api/etl-edges")
public class ETLEdgeResource {

    private static final Logger LOG = LoggerFactory.getLogger(ETLEdgeResource.class);

    private static final String ENTITY_NAME = "eTLEdge";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ETLEdgeService eTLEdgeService;

    private final ETLEdgeRepository eTLEdgeRepository;

    public ETLEdgeResource(ETLEdgeService eTLEdgeService, ETLEdgeRepository eTLEdgeRepository) {
        this.eTLEdgeService = eTLEdgeService;
        this.eTLEdgeRepository = eTLEdgeRepository;
    }

    /**
     * {@code POST  /etl-edges} : Create a new eTLEdge.
     *
     * @param eTLEdgeDTO the eTLEdgeDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new eTLEdgeDTO, or with status {@code 400 (Bad Request)} if the eTLEdge has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ETLEdgeDTO> createETLEdge(@RequestBody ETLEdgeDTO eTLEdgeDTO) throws URISyntaxException {
        LOG.debug("REST request to save ETLEdge : {}", eTLEdgeDTO);
        if (eTLEdgeDTO.getId() != null) {
            throw new BadRequestAlertException("A new eTLEdge cannot already have an ID", ENTITY_NAME, "idexists");
        }
        eTLEdgeDTO = eTLEdgeService.save(eTLEdgeDTO);
        return ResponseEntity.created(new URI("/api/etl-edges/" + eTLEdgeDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, eTLEdgeDTO.getId().toString()))
            .body(eTLEdgeDTO);
    }

    /**
     * {@code PUT  /etl-edges/:id} : Updates an existing eTLEdge.
     *
     * @param id the id of the eTLEdgeDTO to save.
     * @param eTLEdgeDTO the eTLEdgeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLEdgeDTO,
     * or with status {@code 400 (Bad Request)} if the eTLEdgeDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the eTLEdgeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ETLEdgeDTO> updateETLEdge(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ETLEdgeDTO eTLEdgeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ETLEdge : {}, {}", id, eTLEdgeDTO);
        if (eTLEdgeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLEdgeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLEdgeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        eTLEdgeDTO = eTLEdgeService.update(eTLEdgeDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLEdgeDTO.getId().toString()))
            .body(eTLEdgeDTO);
    }

    /**
     * {@code PATCH  /etl-edges/:id} : Partial updates given fields of an existing eTLEdge, field will ignore if it is null
     *
     * @param id the id of the eTLEdgeDTO to save.
     * @param eTLEdgeDTO the eTLEdgeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLEdgeDTO,
     * or with status {@code 400 (Bad Request)} if the eTLEdgeDTO is not valid,
     * or with status {@code 404 (Not Found)} if the eTLEdgeDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the eTLEdgeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ETLEdgeDTO> partialUpdateETLEdge(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ETLEdgeDTO eTLEdgeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ETLEdge partially : {}, {}", id, eTLEdgeDTO);
        if (eTLEdgeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLEdgeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLEdgeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ETLEdgeDTO> result = eTLEdgeService.partialUpdate(eTLEdgeDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLEdgeDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /etl-edges} : get all the eTLEdges.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of eTLEdges in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ETLEdgeDTO>> getAllETLEdges(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of ETLEdges");
        Page<ETLEdgeDTO> page = eTLEdgeService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /etl-edges/:id} : get the "id" eTLEdge.
     *
     * @param id the id of the eTLEdgeDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the eTLEdgeDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ETLEdgeDTO> getETLEdge(@PathVariable("id") String id) {
        LOG.debug("REST request to get ETLEdge : {}", id);
        Optional<ETLEdgeDTO> eTLEdgeDTO = eTLEdgeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(eTLEdgeDTO);
    }

    /**
     * {@code DELETE  /etl-edges/:id} : delete the "id" eTLEdge.
     *
     * @param id the id of the eTLEdgeDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteETLEdge(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ETLEdge : {}", id);
        eTLEdgeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
