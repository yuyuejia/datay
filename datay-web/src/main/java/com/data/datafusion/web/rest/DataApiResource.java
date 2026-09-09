package com.data.datafusion.web.rest;

import com.data.datafusion.service.DataApiService;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.data.datafusion.domain.DataApi}.
 */
@RestController
@RequestMapping("/api/data-apis")
public class DataApiResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiResource.class);

    private static final String ENTITY_NAME = "dataApi";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataApiService dataApiService;

    public DataApiResource(DataApiService dataApiService) {
        this.dataApiService = dataApiService;
    }

    /**
     * {@code POST  /api/data-apis} : Create a new dataApi.
     */
    @PostMapping("")
    public ResponseEntity<DataApiDTO> createDataApi(@RequestBody DataApiDTO dataApiDTO) throws URISyntaxException {
        LOG.debug("REST request to save DataApi : {}", dataApiDTO);
        if (dataApiDTO.getId() != null) {
            throw new BadRequestAlertException("A new dataApi cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            dataApiDTO = dataApiService.save(dataApiDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDataApi");
        }
        return ResponseEntity.created(new URI("/api/data-apis/" + dataApiDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dataApiDTO.getId().toString()))
            .body(dataApiDTO);
    }

    /**
     * {@code PUT  /api/data-apis/:id} : Updates an existing dataApi.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DataApiDTO> updateDataApi(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DataApiDTO dataApiDTO
    ) {
        LOG.debug("REST request to update DataApi : {}, {}", id, dataApiDTO);
        if (dataApiDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!id.equals(dataApiDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<DataApiDTO> result;
        try {
            result = dataApiService.update(id, dataApiDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDataApi");
        }
        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataApiDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /api/data-apis} : get all the dataApis.
     */
    @GetMapping("")
    public ResponseEntity<List<DataApiDTO>> getAllDataApis(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search
    ) {
        LOG.debug("REST request to get a page of DataApis with search: {}", search);
        Page<DataApiDTO> page = dataApiService.findAll(pageable, search);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /api/data-apis/:id} : get the "id" dataApi.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataApiDTO> getDataApi(@PathVariable("id") Long id) {
        LOG.debug("REST request to get DataApi : {}", id);
        Optional<DataApiDTO> dataApiDTO = dataApiService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dataApiDTO);
    }

    /**
     * {@code DELETE  /api/data-apis/:id} : delete the "id" dataApi.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataApi(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete DataApi : {}", id);
        dataApiService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
