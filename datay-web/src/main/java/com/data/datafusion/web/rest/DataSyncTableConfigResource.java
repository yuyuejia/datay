package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.service.DataSyncTableConfigService;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.DataSyncTableConfig}.
 */
@RestController
@RequestMapping("/api/data-sync-table-configs")
public class DataSyncTableConfigResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncTableConfigResource.class);

    private static final String ENTITY_NAME = "dataSyncTableConfig";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataSyncTableConfigService dataSyncTableConfigService;

    private final DataSyncTableConfigRepository dataSyncTableConfigRepository;

    public DataSyncTableConfigResource(
        DataSyncTableConfigService dataSyncTableConfigService,
        DataSyncTableConfigRepository dataSyncTableConfigRepository
    ) {
        this.dataSyncTableConfigService = dataSyncTableConfigService;
        this.dataSyncTableConfigRepository = dataSyncTableConfigRepository;
    }

    /**
     * {@code POST  /data-sync-table-configs} : Create a new dataSyncTableConfig.
     *
     * @param dataSyncTableConfigDTO the dataSyncTableConfigDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dataSyncTableConfigDTO, or with status {@code 400 (Bad Request)} if the dataSyncTableConfig has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<DataSyncTableConfigDTO> createDataSyncTableConfig(@RequestBody DataSyncTableConfigDTO dataSyncTableConfigDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save DataSyncTableConfig : {}", dataSyncTableConfigDTO);
        if (dataSyncTableConfigDTO.getId() != null) {
            throw new BadRequestAlertException("A new dataSyncTableConfig cannot already have an ID", ENTITY_NAME, "idexists");
        }
        dataSyncTableConfigDTO = dataSyncTableConfigService.save(dataSyncTableConfigDTO);
        return ResponseEntity.created(new URI("/api/data-sync-table-configs/" + dataSyncTableConfigDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dataSyncTableConfigDTO.getId().toString()))
            .body(dataSyncTableConfigDTO);
    }

    /**
     * {@code PUT  /data-sync-table-configs/:id} : Updates an existing dataSyncTableConfig.
     *
     * @param id the id of the dataSyncTableConfigDTO to save.
     * @param dataSyncTableConfigDTO the dataSyncTableConfigDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dataSyncTableConfigDTO,
     * or with status {@code 400 (Bad Request)} if the dataSyncTableConfigDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the dataSyncTableConfigDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DataSyncTableConfigDTO> updateDataSyncTableConfig(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody DataSyncTableConfigDTO dataSyncTableConfigDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update DataSyncTableConfig : {}, {}", id, dataSyncTableConfigDTO);
        if (dataSyncTableConfigDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataSyncTableConfigDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dataSyncTableConfigRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        dataSyncTableConfigDTO = dataSyncTableConfigService.update(dataSyncTableConfigDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataSyncTableConfigDTO.getId().toString()))
            .body(dataSyncTableConfigDTO);
    }

    /**
     * {@code PATCH  /data-sync-table-configs/:id} : Partial updates given fields of an existing dataSyncTableConfig, field will ignore if it is null
     *
     * @param id the id of the dataSyncTableConfigDTO to save.
     * @param dataSyncTableConfigDTO the dataSyncTableConfigDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dataSyncTableConfigDTO,
     * or with status {@code 400 (Bad Request)} if the dataSyncTableConfigDTO is not valid,
     * or with status {@code 404 (Not Found)} if the dataSyncTableConfigDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the dataSyncTableConfigDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DataSyncTableConfigDTO> partialUpdateDataSyncTableConfig(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody DataSyncTableConfigDTO dataSyncTableConfigDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update DataSyncTableConfig partially : {}, {}", id, dataSyncTableConfigDTO);
        if (dataSyncTableConfigDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataSyncTableConfigDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dataSyncTableConfigRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DataSyncTableConfigDTO> result = dataSyncTableConfigService.partialUpdate(dataSyncTableConfigDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataSyncTableConfigDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /data-sync-table-configs} : get all the dataSyncTableConfigs.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of dataSyncTableConfigs in body.
     */
    @GetMapping("")
    public ResponseEntity<List<DataSyncTableConfigDTO>> getAllDataSyncTableConfigs(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get a page of DataSyncTableConfigs");
        Page<DataSyncTableConfigDTO> page = dataSyncTableConfigService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /data-sync-table-configs/:id} : get the "id" dataSyncTableConfig.
     *
     * @param id the id of the dataSyncTableConfigDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dataSyncTableConfigDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataSyncTableConfigDTO> getDataSyncTableConfig(@PathVariable("id") String id) {
        LOG.debug("REST request to get DataSyncTableConfig : {}", id);
        Optional<DataSyncTableConfigDTO> dataSyncTableConfigDTO = dataSyncTableConfigService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dataSyncTableConfigDTO);
    }

    /**
     * {@code DELETE  /data-sync-table-configs/:id} : delete the "id" dataSyncTableConfig.
     *
     * @param id the id of the dataSyncTableConfigDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataSyncTableConfig(@PathVariable("id") String id) {
        LOG.debug("REST request to delete DataSyncTableConfig : {}", id);
        dataSyncTableConfigService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
