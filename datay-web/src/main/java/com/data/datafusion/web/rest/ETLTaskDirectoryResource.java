package com.data.datafusion.web.rest;

import com.data.datafusion.repository.ETLTaskDirectoryRepository;
import com.data.datafusion.service.ETLTaskDirectoryService;
import com.data.datafusion.service.dto.ETLTaskDirectoryDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.data.datafusion.domain.ETLTaskDirectory}.
 */
@RestController
@RequestMapping("/api/etl-task-directories")
public class ETLTaskDirectoryResource {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTaskDirectoryResource.class);

    private static final String ENTITY_NAME = "eTLTaskDirectory";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ETLTaskDirectoryService directoryService;

    private final ETLTaskDirectoryRepository directoryRepository;

    public ETLTaskDirectoryResource(ETLTaskDirectoryService directoryService, ETLTaskDirectoryRepository directoryRepository) {
        this.directoryService = directoryService;
        this.directoryRepository = directoryRepository;
    }

    @PostMapping("")
    public ResponseEntity<ETLTaskDirectoryDTO> createDirectory(@RequestBody ETLTaskDirectoryDTO dto) throws URISyntaxException {
        LOG.debug("REST request to save ETLTaskDirectory : {}", dto);
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new eTLTaskDirectory cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            dto = directoryService.save(dto);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/etl-task-directories/" + dto.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dto.getId()))
            .body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ETLTaskDirectoryDTO> updateDirectory(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ETLTaskDirectoryDTO dto
    ) {
        LOG.debug("REST request to update ETLTaskDirectory : {}, {}", id, dto);
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!directoryRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        try {
            dto = directoryService.update(dto);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dto.getId()))
            .body(dto);
    }

    @GetMapping("")
    public ResponseEntity<List<ETLTaskDirectoryDTO>> getAllDirectories(@RequestParam(value = "taskType", required = false) String taskType) {
        LOG.debug("REST request to get ETLTaskDirectories by taskType : {}", taskType);
        return ResponseEntity.ok().body(directoryService.findAll(taskType));
    }

    @GetMapping("/by-parent")
    public ResponseEntity<List<ETLTaskDirectoryDTO>> getDirectoriesByParent(
        @RequestParam(value = "taskType", required = false) String taskType,
        @RequestParam(required = false) String parentId
    ) {
        LOG.debug("REST request to get ETLTaskDirectories by taskType : {}, parentId : {}", taskType, parentId);
        return ResponseEntity.ok().body(directoryService.findByParent(taskType, parentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ETLTaskDirectoryDTO> getDirectory(@PathVariable("id") String id) {
        LOG.debug("REST request to get ETLTaskDirectory : {}", id);
        Optional<ETLTaskDirectoryDTO> dto = directoryService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDirectory(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ETLTaskDirectory : {}", id);
        directoryService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
