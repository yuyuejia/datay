package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DataQualityRuleRepository;
import com.data.datafusion.service.DataQualityRuleService;
import com.data.datafusion.service.dto.DataQualityRuleDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing data quality rules.
 */
@RestController
@RequestMapping("/api/data-quality-rules")
public class DataQualityRuleResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataQualityRuleResource.class);

    private static final String ENTITY_NAME = "dataQualityRule";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataQualityRuleService ruleService;

    private final DataQualityRuleRepository ruleRepository;

    public DataQualityRuleResource(DataQualityRuleService ruleService, DataQualityRuleRepository ruleRepository) {
        this.ruleService = ruleService;
        this.ruleRepository = ruleRepository;
    }

    @PostMapping("")
    public ResponseEntity<DataQualityRuleDTO> createRule(@RequestBody DataQualityRuleDTO dto) throws URISyntaxException {
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new data quality rule cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DataQualityRuleDTO result;
        try {
            result = ruleService.save(dto);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.created(new URI("/api/data-quality-rules/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataQualityRuleDTO> updateRule(@PathVariable("id") String id, @RequestBody DataQualityRuleDTO dto) {
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!ruleRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        try {
            return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id))
                .body(ruleService.update(dto));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("")
    public ResponseEntity<List<DataQualityRuleDTO>> getAllRules(
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "level", required = false) String level,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "enabled", required = false) Boolean enabled,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        Page<DataQualityRuleDTO> page = ruleService.findAll(pageable, search, level, type, enabled);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/types")
    public ResponseEntity<List<java.util.Map<String, Object>>> getRuleTypes() {
        return ResponseEntity.ok(ruleService.listTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataQualityRuleDTO> getRule(@PathVariable("id") String id) {
        Optional<DataQualityRuleDTO> dto = ruleService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable("id") String id) {
        try {
            ruleService.delete(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
