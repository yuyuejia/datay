package com.data.datafusion.web.rest;

import com.data.datafusion.repository.ETLComponentRepository;
import com.data.datafusion.service.ETLComponentCatalogService;
import com.data.datafusion.service.ETLComponentService;
import com.data.datafusion.service.dto.ETLComponentDTO;
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
 * REST controller for managing {@link com.data.datafusion.domain.ETLComponent}.
 */
@RestController
@RequestMapping("/api/etl-components")
public class ETLComponentResource {

    private static final Logger LOG = LoggerFactory.getLogger(ETLComponentResource.class);

    private static final String ENTITY_NAME = "eTLComponent";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ETLComponentService eTLComponentService;

    private final ETLComponentRepository eTLComponentRepository;

    private final ETLComponentCatalogService eTLComponentCatalogService;

    public ETLComponentResource(
        ETLComponentService eTLComponentService,
        ETLComponentRepository eTLComponentRepository,
        ETLComponentCatalogService eTLComponentCatalogService
    ) {
        this.eTLComponentService = eTLComponentService;
        this.eTLComponentRepository = eTLComponentRepository;
        this.eTLComponentCatalogService = eTLComponentCatalogService;
    }

    /**
     * {@code POST  /etl-components} : Create a new eTLComponent.
     *
     * @param eTLComponentDTO the eTLComponentDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new eTLComponentDTO, or with status {@code 400 (Bad Request)} if the eTLComponent has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ETLComponentDTO> createETLComponent(@RequestBody ETLComponentDTO eTLComponentDTO) throws URISyntaxException {
        LOG.debug("REST request to save ETLComponent : {}", eTLComponentDTO);
        if (eTLComponentDTO.getId() != null) {
            throw new BadRequestAlertException("A new eTLComponent cannot already have an ID", ENTITY_NAME, "idexists");
        }
        eTLComponentDTO = eTLComponentService.save(eTLComponentDTO);
        return ResponseEntity.created(new URI("/api/etl-components/" + eTLComponentDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, eTLComponentDTO.getId().toString()))
            .body(eTLComponentDTO);
    }

    /**
     * {@code PUT  /etl-components/:id} : Updates an existing eTLComponent.
     *
     * @param id the id of the eTLComponentDTO to save.
     * @param eTLComponentDTO the eTLComponentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLComponentDTO,
     * or with status {@code 400 (Bad Request)} if the eTLComponentDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the eTLComponentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ETLComponentDTO> updateETLComponent(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ETLComponentDTO eTLComponentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ETLComponent : {}, {}", id, eTLComponentDTO);
        if (eTLComponentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLComponentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLComponentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        eTLComponentDTO = eTLComponentService.update(eTLComponentDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLComponentDTO.getId().toString()))
            .body(eTLComponentDTO);
    }

    /**
     * {@code PATCH  /etl-components/:id} : Partial updates given fields of an existing eTLComponent, field will ignore if it is null
     *
     * @param id the id of the eTLComponentDTO to save.
     * @param eTLComponentDTO the eTLComponentDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated eTLComponentDTO,
     * or with status {@code 400 (Bad Request)} if the eTLComponentDTO is not valid,
     * or with status {@code 404 (Not Found)} if the eTLComponentDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the eTLComponentDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ETLComponentDTO> partialUpdateETLComponent(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ETLComponentDTO eTLComponentDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ETLComponent partially : {}, {}", id, eTLComponentDTO);
        if (eTLComponentDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, eTLComponentDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!eTLComponentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ETLComponentDTO> result = eTLComponentService.partialUpdate(eTLComponentDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, eTLComponentDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /etl-components/catalog} : get all auto-discovered ETL components.
     * <p>组件元数据来源于组件实现上的 {@code @ComponentRegister} 注解，无需注册到数据库。
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the component catalog in body.
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<ETLComponentDTO>> getETLComponentCatalog() {
        LOG.debug("REST request to get ETL component catalog");
        return ResponseEntity.ok().body(eTLComponentCatalogService.listCatalog());
    }

    /**
     * {@code GET  /etl-components} : get all the eTLComponents.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of eTLComponents in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ETLComponentDTO>> getAllETLComponents(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of ETLComponents");
        Page<ETLComponentDTO> page = eTLComponentService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /etl-components/:id} : get the "id" eTLComponent.
     *
     * @param id the id of the eTLComponentDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the eTLComponentDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ETLComponentDTO> getETLComponent(@PathVariable("id") String id) {
        LOG.debug("REST request to get ETLComponent : {}", id);
        Optional<ETLComponentDTO> eTLComponentDTO = eTLComponentService.findOne(id);
        return ResponseUtil.wrapOrNotFound(eTLComponentDTO);
    }

    /**
     * {@code DELETE  /etl-components/:id} : delete the "id" eTLComponent.
     *
     * @param id the id of the eTLComponentDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteETLComponent(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ETLComponent : {}", id);
        eTLComponentService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
