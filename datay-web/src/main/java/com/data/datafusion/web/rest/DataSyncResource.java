package com.data.datafusion.web.rest;

import static com.data.datafusion.job.TaskConstants.TASK_STATUS_OFFLINE;

import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.service.DataSyncService;
import com.data.datafusion.service.dto.DataSyncDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
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
 * REST controller for managing {@link com.data.datafusion.domain.DataSync}.
 */
@RestController
@RequestMapping("/api/data-syncs")
public class DataSyncResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncResource.class);

    private static final String ENTITY_NAME = "dataSync";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataSyncService dataSyncService;

    private final DataSyncRepository dataSyncRepository;

    public DataSyncResource(DataSyncService dataSyncService, DataSyncRepository dataSyncRepository) {
        this.dataSyncService = dataSyncService;
        this.dataSyncRepository = dataSyncRepository;
    }

    /**
     * {@code POST  /data-syncs} : Create a new dataSync.
     *
     * @param dataSyncDTO the dataSyncDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dataSyncDTO, or with status {@code 400 (Bad Request)} if the dataSync has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<DataSyncDTO> createDataSync(@RequestBody DataSyncDTO dataSyncDTO) throws URISyntaxException {
        LOG.debug("REST request to save DataSync : {}", dataSyncDTO);
        if (dataSyncDTO.getId() != null) {
            throw new BadRequestAlertException("A new dataSync cannot already have an ID", ENTITY_NAME, "idexists");
        }
        dataSyncDTO.setStatus(TASK_STATUS_OFFLINE);
        dataSyncDTO.setCreateTime(ZonedDateTime.now());
        dataSyncDTO = dataSyncService.save(dataSyncDTO);
        dataSyncService.createDataSyncTask(dataSyncDTO);
        return ResponseEntity.created(new URI("/api/data-syncs/" + dataSyncDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dataSyncDTO.getId().toString()))
            .body(dataSyncDTO);
    }

    /**
     * {@code PUT  /data-syncs/:id} : Updates an existing dataSync.
     *
     * @param id the id of the dataSyncDTO to save.
     * @param dataSyncDTO the dataSyncDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dataSyncDTO,
     * or with status {@code 400 (Bad Request)} if the dataSyncDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the dataSyncDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DataSyncDTO> updateDataSync(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DataSyncDTO dataSyncDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update DataSync : {}, {}", id, dataSyncDTO);
        if (dataSyncDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataSyncDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dataSyncRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        dataSyncDTO = dataSyncService.update(dataSyncDTO);
        dataSyncService.createDataSyncTask(dataSyncDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataSyncDTO.getId().toString()))
            .body(dataSyncDTO);
    }

    /**
     * {@code PATCH  /data-syncs/:id} : Partial updates given fields of an existing dataSync, field will ignore if it is null
     *
     * @param id the id of the dataSyncDTO to save.
     * @param dataSyncDTO the dataSyncDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dataSyncDTO,
     * or with status {@code 400 (Bad Request)} if the dataSyncDTO is not valid,
     * or with status {@code 404 (Not Found)} if the dataSyncDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the dataSyncDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DataSyncDTO> partialUpdateDataSync(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DataSyncDTO dataSyncDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update DataSync partially : {}, {}", id, dataSyncDTO);
        if (dataSyncDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataSyncDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dataSyncRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DataSyncDTO> result = dataSyncService.partialUpdate(dataSyncDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataSyncDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /data-syncs} : get all the dataSyncs.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of dataSyncs in body.
     */
    @GetMapping("")
    public ResponseEntity<List<DataSyncDTO>> getAllDataSyncs(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of DataSyncs");
        Page<DataSyncDTO> page = dataSyncService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /data-syncs/:id} : get the "id" dataSync.
     *
     * @param id the id of the dataSyncDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dataSyncDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataSyncDTO> getDataSync(@PathVariable("id") Long id) {
        LOG.debug("REST request to get DataSync : {}", id);
        Optional<DataSyncDTO> dataSyncDTO = dataSyncService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dataSyncDTO);
    }

    /**
     * {@code DELETE  /data-syncs/:id} : delete the "id" dataSync.
     *
     * @param id the id of the dataSyncDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataSync(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete DataSync : {}", id);
        dataSyncService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
