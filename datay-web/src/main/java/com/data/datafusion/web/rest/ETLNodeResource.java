package com.data.datafusion.web.rest;

import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.service.ETLNodeService;
import com.data.datafusion.service.dto.ETLNodeDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.ETLNode}.
 */
@RestController
@RequestMapping("/api/etl-nodes")
public class ETLNodeResource {

    private static final Logger LOG = LoggerFactory.getLogger(ETLNodeResource.class);

    private static final String ENTITY_NAME = "eTLNode";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ETLNodeService eTLNodeService;

    private final ETLNodeRepository eTLNodeRepository;

    public ETLNodeResource(ETLNodeService eTLNodeService, ETLNodeRepository eTLNodeRepository) {
        this.eTLNodeService = eTLNodeService;
        this.eTLNodeRepository = eTLNodeRepository;
    }

    /**
     * {@code POST  /etl-nodes} : Create a new eTLNode.
     *
     * @param eTLNodeDTO the eTLNodeDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new eTLNodeDTO, or with status {@code 400 (Bad Request)} if the eTLNode has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ETLNodeDTO> createETLNode(@RequestBody ETLNodeDTO eTLNodeDTO) throws URISyntaxException {
        LOG.debug("REST request to save ETLNode : {}", eTLNodeDTO);
        if (eTLNodeDTO.getId() != null) {
            throw new BadRequestAlertException("A new eTLNode cannot already have an ID", ENTITY_NAME, "idexists");
        }
        eTLNodeDTO = eTLNodeService.save(eTLNodeDTO);
        return ResponseEntity.created(new URI("/api/etl-nodes/" + eTLNodeDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, eTLNodeDTO.getId().toString()))
            .body(eTLNodeDTO);
    }

    /**
     * {@code PUT  /etl-nodes/:id} : Updates an existing eTLNode.
     *
     * @param id the id of the eTLNodeDTO to save.
     * @param eTLNodeDTO the eTLNodeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLNodeDTO,
     * or with status {@code 400 (Bad Request)} if the eTLNodeDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the eTLNodeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ETLNodeDTO> updateETLNode(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ETLNodeDTO eTLNodeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ETLNode : {}, {}", id, eTLNodeDTO);
        if (eTLNodeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLNodeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLNodeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        eTLNodeDTO = eTLNodeService.update(eTLNodeDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLNodeDTO.getId().toString()))
            .body(eTLNodeDTO);
    }

    /**
     * {@code PATCH  /etl-nodes/:id} : Partial updates given fields of an existing eTLNode, field will ignore if it is null
     *
     * @param id the id of the eTLNodeDTO to save.
     * @param eTLNodeDTO the eTLNodeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLNodeDTO,
     * or with status {@code 400 (Bad Request)} if the eTLNodeDTO is not valid,
     * or with status {@code 404 (Not Found)} if the eTLNodeDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the eTLNodeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ETLNodeDTO> partialUpdateETLNode(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ETLNodeDTO eTLNodeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ETLNode partially : {}, {}", id, eTLNodeDTO);
        if (eTLNodeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLNodeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLNodeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ETLNodeDTO> result = eTLNodeService.partialUpdate(eTLNodeDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLNodeDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /etl-nodes} : get all the eTLNodes.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of eTLNodes in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ETLNodeDTO>> getAllETLNodes(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of ETLNodes");
        Page<ETLNodeDTO> page = eTLNodeService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /etl-nodes/:id} : get the "id" eTLNode.
     *
     * @param id the id of the eTLNodeDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the eTLNodeDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ETLNodeDTO> getETLNode(@PathVariable("id") String id) {
        LOG.debug("REST request to get ETLNode : {}", id);
        Optional<ETLNodeDTO> eTLNodeDTO = eTLNodeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(eTLNodeDTO);
    }

    /**
     * {@code DELETE  /etl-nodes/:id} : delete the "id" eTLNode.
     *
     * @param id the id of the eTLNodeDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteETLNode(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ETLNode : {}", id);
        eTLNodeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
