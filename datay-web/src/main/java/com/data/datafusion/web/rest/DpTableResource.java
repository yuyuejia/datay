package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DpTableRepository;
import com.data.datafusion.service.DpTableService;
import com.data.datafusion.service.dto.DpTableDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.DpTable}.
 */
@RestController
@RequestMapping("/api/dp-tables")
public class DpTableResource {

    private static final Logger LOG = LoggerFactory.getLogger(DpTableResource.class);

    private static final String ENTITY_NAME = "dpTable";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DpTableService dpTableService;

    private final DpTableRepository dpTableRepository;

    public DpTableResource(DpTableService dpTableService, DpTableRepository dpTableRepository) {
        this.dpTableService = dpTableService;
        this.dpTableRepository = dpTableRepository;
    }

    /**
     * {@code POST  /dp-tables} : Create a new dpTable.
     *
     * @param dpTableDTO the dpTableDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dpTableDTO, or with status {@code 400 (Bad Request)} if the dpTable has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<DpTableDTO> createDpTable(@RequestBody DpTableDTO dpTableDTO) throws URISyntaxException {
        LOG.debug("REST request to save DpTable : {}", dpTableDTO);
        if (dpTableDTO.getId() != null) {
            throw new BadRequestAlertException("A new dpTable cannot already have an ID", ENTITY_NAME, "idexists");
        }
        dpTableDTO = dpTableService.save(dpTableDTO);
        return ResponseEntity.created(new URI("/api/dp-tables/" + dpTableDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dpTableDTO.getId().toString()))
            .body(dpTableDTO);
    }

    /**
     * {@code PUT  /dp-tables/:id} : Updates an existing dpTable.
     *
     * @param id the id of the dpTableDTO to save.
     * @param dpTableDTO the dpTableDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dpTableDTO,
     * or with status {@code 400 (Bad Request)} if the dpTableDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the dpTableDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DpTableDTO> updateDpTable(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody DpTableDTO dpTableDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update DpTable : {}, {}", id, dpTableDTO);
        if (dpTableDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dpTableDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dpTableRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        dpTableDTO = dpTableService.update(dpTableDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dpTableDTO.getId().toString()))
            .body(dpTableDTO);
    }

    /**
     * {@code PATCH  /dp-tables/:id} : Partial updates given fields of an existing dpTable, field will ignore if it is null
     *
     * @param id the id of the dpTableDTO to save.
     * @param dpTableDTO the dpTableDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dpTableDTO,
     * or with status {@code 400 (Bad Request)} if the dpTableDTO is not valid,
     * or with status {@code 404 (Not Found)} if the dpTableDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the dpTableDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DpTableDTO> partialUpdateDpTable(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody DpTableDTO dpTableDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update DpTable partially : {}, {}", id, dpTableDTO);
        if (dpTableDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dpTableDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dpTableRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DpTableDTO> result = dpTableService.partialUpdate(dpTableDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dpTableDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /dp-tables} : get all the dpTables.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of dpTables in body.
     */
    @GetMapping("")
    public ResponseEntity<List<DpTableDTO>> getAllDpTables(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of DpTables");
        Page<DpTableDTO> page = dpTableService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /dp-tables/:id} : get the "id" dpTable.
     *
     * @param id the id of the dpTableDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dpTableDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DpTableDTO> getDpTable(@PathVariable("id") String id) {
        LOG.debug("REST request to get DpTable : {}", id);
        Optional<DpTableDTO> dpTableDTO = dpTableService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dpTableDTO);
    }

    /**
     * {@code DELETE  /dp-tables/:id} : delete the "id" dpTable.
     *
     * @param id the id of the dpTableDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDpTable(@PathVariable("id") String id) {
        LOG.debug("REST request to delete DpTable : {}", id);
        dpTableService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
