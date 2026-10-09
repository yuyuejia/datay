package com.data.datafusion.web.rest;

import com.data.datafusion.repository.ModelDirectoryRepository;
import com.data.datafusion.service.ModelDirectoryService;
import com.data.datafusion.service.dto.ModelDirectoryDTO;
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

@RestController
@RequestMapping("/api/model-directories")
public class ModelDirectoryResource {

    private static final Logger LOG = LoggerFactory.getLogger(ModelDirectoryResource.class);

    private static final String ENTITY_NAME = "modelDirectory";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ModelDirectoryService modelDirectoryService;
    private final ModelDirectoryRepository modelDirectoryRepository;

    public ModelDirectoryResource(ModelDirectoryService modelDirectoryService, ModelDirectoryRepository modelDirectoryRepository) {
        this.modelDirectoryService = modelDirectoryService;
        this.modelDirectoryRepository = modelDirectoryRepository;
    }

    @PostMapping("")
    public ResponseEntity<ModelDirectoryDTO> createModelDirectory(@RequestBody ModelDirectoryDTO modelDirectoryDTO) throws URISyntaxException {
        LOG.debug("REST request to save ModelDirectory : {}", modelDirectoryDTO);
        if (modelDirectoryDTO.getId() != null) {
            throw new BadRequestAlertException("A new modelDirectory cannot already have an ID", ENTITY_NAME, "idexists");
        }
        modelDirectoryDTO = modelDirectoryService.save(modelDirectoryDTO);
        return ResponseEntity.created(new URI("/api/model-directories/" + modelDirectoryDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, modelDirectoryDTO.getId().toString()))
            .body(modelDirectoryDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelDirectoryDTO> updateModelDirectory(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ModelDirectoryDTO modelDirectoryDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ModelDirectory : {}, {}", id, modelDirectoryDTO);
        if (modelDirectoryDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, modelDirectoryDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!modelDirectoryRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        modelDirectoryDTO = modelDirectoryService.update(modelDirectoryDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, modelDirectoryDTO.getId().toString()))
            .body(modelDirectoryDTO);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ModelDirectoryDTO> partialUpdateModelDirectory(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ModelDirectoryDTO modelDirectoryDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ModelDirectory : {}, {}", id, modelDirectoryDTO);
        if (modelDirectoryDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, modelDirectoryDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!modelDirectoryRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        Optional<ModelDirectoryDTO> result = modelDirectoryService.partialUpdate(modelDirectoryDTO);
        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, modelDirectoryDTO.getId().toString())
        );
    }

    @GetMapping("")
    public ResponseEntity<List<ModelDirectoryDTO>> getAllModelDirectories() {
        LOG.debug("REST request to get all ModelDirectories");
        List<ModelDirectoryDTO> list = modelDirectoryService.findAll();
        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/by-parent")
    public ResponseEntity<List<ModelDirectoryDTO>> getModelDirectoriesByParent(@RequestParam(required = false) String parentId) {
        LOG.debug("REST request to get ModelDirectories by parentId : {}", parentId);
        List<ModelDirectoryDTO> list = modelDirectoryService.findByParentId(parentId);
        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelDirectoryDTO> getModelDirectory(@PathVariable("id") String id) {
        LOG.debug("REST request to get ModelDirectory : {}", id);
        Optional<ModelDirectoryDTO> modelDirectoryDTO = modelDirectoryService.findOne(id);
        return ResponseUtil.wrapOrNotFound(modelDirectoryDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteModelDirectory(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ModelDirectory : {}", id);
        modelDirectoryService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}