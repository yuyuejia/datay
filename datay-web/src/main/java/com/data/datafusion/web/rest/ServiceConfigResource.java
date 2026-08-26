package com.data.datafusion.web.rest;

import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.service.ServiceConfigService;
import com.data.datafusion.service.dto.ServiceConfigDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.ServiceConfig}.
 */
@RestController
@RequestMapping("/api/service-configs")
public class ServiceConfigResource {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceConfigResource.class);

    private static final String ENTITY_NAME = "serviceConfig";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ServiceConfigService serviceConfigService;

    private final ServiceConfigRepository serviceConfigRepository;

    public ServiceConfigResource(ServiceConfigService serviceConfigService, ServiceConfigRepository serviceConfigRepository) {
        this.serviceConfigService = serviceConfigService;
        this.serviceConfigRepository = serviceConfigRepository;
    }

    /**
     * {@code POST  /service-configs} : Create a new serviceConfig.
     *
     * @param serviceConfigDTO the serviceConfigDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new serviceConfigDTO, or with status {@code 400 (Bad Request)} if the serviceConfig has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ServiceConfigDTO> createServiceConfig(@RequestBody ServiceConfigDTO serviceConfigDTO) throws URISyntaxException {
        LOG.debug("REST request to save ServiceConfig : {}", serviceConfigDTO);
        if (serviceConfigDTO.getId() != null) {
            throw new BadRequestAlertException("A new serviceConfig cannot already have an ID", ENTITY_NAME, "idexists");
        }
        serviceConfigDTO = serviceConfigService.save(serviceConfigDTO);
        return ResponseEntity.created(new URI("/api/service-configs/" + serviceConfigDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, serviceConfigDTO.getId().toString()))
            .body(serviceConfigDTO);
    }

    /**
     * {@code PUT  /service-configs/:id} : Updates an existing serviceConfig.
     *
     * @param id the id of the serviceConfigDTO to save.
     * @param serviceConfigDTO the serviceConfigDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated serviceConfigDTO,
     * or with status {@code 400 (Bad Request)} if the serviceConfigDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the serviceConfigDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceConfigDTO> updateServiceConfig(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody ServiceConfigDTO serviceConfigDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ServiceConfig : {}, {}", id, serviceConfigDTO);
        if (serviceConfigDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, serviceConfigDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!serviceConfigRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        serviceConfigDTO = serviceConfigService.update(serviceConfigDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, serviceConfigDTO.getId().toString()))
            .body(serviceConfigDTO);
    }

    /**
     * {@code PATCH  /service-configs/:id} : Partial updates given fields of an existing serviceConfig, field will ignore if it is null
     *
     * @param id the id of the serviceConfigDTO to save.
     * @param serviceConfigDTO the serviceConfigDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated serviceConfigDTO,
     * or with status {@code 400 (Bad Request)} if the serviceConfigDTO is not valid,
     * or with status {@code 404 (Not Found)} if the serviceConfigDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the serviceConfigDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ServiceConfigDTO> partialUpdateServiceConfig(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody ServiceConfigDTO serviceConfigDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ServiceConfig partially : {}, {}", id, serviceConfigDTO);
        if (serviceConfigDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, serviceConfigDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!serviceConfigRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ServiceConfigDTO> result = serviceConfigService.partialUpdate(serviceConfigDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, serviceConfigDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /service-configs} : get all the serviceConfigs.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of serviceConfigs in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ServiceConfigDTO>> getAllServiceConfigs(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of ServiceConfigs");
        Page<ServiceConfigDTO> page = serviceConfigService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /service-configs/:id} : get the "id" serviceConfig.
     *
     * @param id the id of the serviceConfigDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the serviceConfigDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceConfigDTO> getServiceConfig(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ServiceConfig : {}", id);
        Optional<ServiceConfigDTO> serviceConfigDTO = serviceConfigService.findOne(id);
        return ResponseUtil.wrapOrNotFound(serviceConfigDTO);
    }

    /**
     * {@code DELETE  /service-configs/:id} : delete the "id" serviceConfig.
     *
     * @param id the id of the serviceConfigDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServiceConfig(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ServiceConfig : {}", id);
        serviceConfigService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
