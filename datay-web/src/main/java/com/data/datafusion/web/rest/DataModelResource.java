package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.DataModelMaterializeService;
import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.ModelFieldService;
import com.data.datafusion.service.dto.DataModelDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MaterializeFieldDTO;
import com.data.datafusion.service.dto.MaterializeRequestDTO;
import com.data.datafusion.service.dto.MaterializeResponseDTO;
import com.data.datafusion.service.dto.ModelFieldDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.impl.LogicConverter;
import com.data.metadata.util.DBUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/data-models")
public class DataModelResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataModelResource.class);

    private static final String ENTITY_NAME = "dataModel";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataModelService dataModelService;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldService modelFieldService;
    private final DataModelMaterializeService materializeService;
    private final DataSourceService dataSourceService;

    public DataModelResource(
        DataModelService dataModelService,
        DataModelRepository dataModelRepository,
        ModelFieldService modelFieldService,
        DataModelMaterializeService materializeService,
        DataSourceService dataSourceService
    ) {
        this.dataModelService = dataModelService;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldService = modelFieldService;
        this.materializeService = materializeService;
        this.dataSourceService = dataSourceService;
    }

    @PostMapping("")
    public ResponseEntity<DataModelDTO> createDataModel(@RequestBody DataModelDTO dataModelDTO) throws URISyntaxException {
        LOG.debug("REST request to save DataModel : {}", dataModelDTO);
        if (dataModelDTO.getId() != null) {
            throw new BadRequestAlertException("A new dataModel cannot already have an ID", ENTITY_NAME, "idexists");
        }
        dataModelDTO = dataModelService.save(dataModelDTO);
        return ResponseEntity.created(new URI("/api/data-models/" + dataModelDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dataModelDTO.getId().toString()))
            .body(dataModelDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataModelDTO> updateDataModel(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DataModelDTO dataModelDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update DataModel : {}, {}", id, dataModelDTO);
        if (dataModelDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataModelDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!dataModelRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        dataModelDTO = dataModelService.update(dataModelDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataModelDTO.getId().toString()))
            .body(dataModelDTO);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DataModelDTO> partialUpdateDataModel(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DataModelDTO dataModelDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update DataModel : {}, {}", id, dataModelDTO);
        if (dataModelDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataModelDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!dataModelRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        Optional<DataModelDTO> result = dataModelService.partialUpdate(dataModelDTO);
        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataModelDTO.getId().toString())
        );
    }

    @GetMapping("")
    public ResponseEntity<List<DataModelDTO>> getAllDataModels() {
        LOG.debug("REST request to get all DataModels");
        List<DataModelDTO> list = dataModelService.findAll();
        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/by-directory")
    public ResponseEntity<List<DataModelDTO>> getDataModelsByDirectory(@RequestParam Long directoryId) {
        LOG.debug("REST request to get DataModels by directoryId : {}", directoryId);
        List<DataModelDTO> list = dataModelService.findByDirectoryId(directoryId);
        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/by-type")
    public ResponseEntity<List<DataModelDTO>> getDataModelsByType(@RequestParam String modelType) {
        LOG.debug("REST request to get DataModels by modelType : {}", modelType);
        List<DataModelDTO> list = dataModelService.findByModelType(modelType);
        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataModelDTO> getDataModel(@PathVariable("id") Long id) {
        LOG.debug("REST request to get DataModel : {}", id);
        Optional<DataModelDTO> dataModelDTO = dataModelService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dataModelDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataModel(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete DataModel : {}", id);
        modelFieldService.deleteByModelId(id);
        dataModelService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @GetMapping("/{id}/fields")
    public ResponseEntity<List<ModelFieldDTO>> getModelFields(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ModelFields for DataModel : {}", id);
        List<ModelFieldDTO> fields = modelFieldService.findByModelId(id);
        return ResponseEntity.ok().body(fields);
    }

    @PostMapping("/{id}/fields")
    public ResponseEntity<List<ModelFieldDTO>> saveModelFields(
        @PathVariable("id") Long id,
        @RequestBody List<ModelFieldDTO> modelFieldDTOs
    ) {
        LOG.debug("REST request to save ModelFields for DataModel : {}", id);
        modelFieldService.deleteByModelId(id);
        for (ModelFieldDTO field : modelFieldDTOs) {
            field.setModelId(id);
            field.setId(null);
        }
        List<ModelFieldDTO> saved = modelFieldService.saveAll(modelFieldDTOs);
        return ResponseEntity.ok().body(saved);
    }

    @GetMapping("/materialize-types")
    public ResponseEntity<List<String>> getSupportedPhysicalTypes(@RequestParam Long dataSourceId) {
        LOG.debug("REST request to get supported physical types for DataSource : {}", dataSourceId);
        Optional<DataSourceDTO> dsOpt = dataSourceService.findOne(dataSourceId);
        if (dsOpt.isEmpty()) {
            throw new BadRequestAlertException("数据源不存在", ENTITY_NAME, "datasource not found");
        }
        String dbType = DBUtils.getDBType(dsOpt.get().getUrl());
        List<String> types = DatabaseConverter.getSupportedTypes(dbType);
        return ResponseEntity.ok().body(types);
    }

    @GetMapping("/logical-types")
    public ResponseEntity<List<Map<String, String>>> getSupportedLogicalTypes() {
        LOG.debug("REST request to get supported logical field types");
        List<String> typeNames = DatabaseConverter.getSupportedTypes("common");
        List<Map<String, String>> result = new ArrayList<>();
        for (String typeName : typeNames) {
            Map<String, String> item = new HashMap<>();
            item.put("type", typeName);
            String label = LogicConverter.LOGIC_TYPE_LABELS.getOrDefault(typeName, typeName);
            item.put("label", label + "(" + typeName + ")");
            result.add(item);
        }
        return ResponseEntity.ok().body(result);
    }

    @GetMapping("/{id}/materialize-fields")
    public ResponseEntity<List<MaterializeFieldDTO>> getMaterializeFields(
        @PathVariable("id") Long id,
        @RequestParam Long dataSourceId
    ) {
        LOG.debug("REST request to get materialize fields for DataModel : {} and DataSource : {}", id, dataSourceId);
        List<ModelFieldDTO> modelFields = modelFieldService.findByModelId(id);

        String dbType = "mysql";
        Optional<DataSourceDTO> dsDTO = dataSourceService.findOne(dataSourceId);
        if (dsDTO.isPresent()) {
            dbType = DBUtils.getDBType(dsDTO.get().getUrl());
        }

        List<MaterializeFieldDTO> fields = materializeService.prepareMaterializeFields(modelFields, dbType);
        return ResponseEntity.ok().body(fields);
    }

    @PostMapping("/{id}/materialize-check")
    public ResponseEntity<MaterializeResponseDTO> checkMaterialize(
        @PathVariable("id") Long id,
        @RequestBody MaterializeRequestDTO request
    ) {
        LOG.debug("REST request to check materialize status for DataModel : {}", id);
        MaterializeResponseDTO response = materializeService.checkTableExists(request);
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/{id}/materialize-ddl")
    public ResponseEntity<MaterializeResponseDTO> generateMaterializeDDL(
        @PathVariable("id") Long id,
        @RequestBody MaterializeRequestDTO request
    ) {
        LOG.debug("REST request to generate materialize DDL for DataModel : {}", id);
        MaterializeResponseDTO response = materializeService.generateDDL(request);
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/{id}/materialize")
    public ResponseEntity<MaterializeResponseDTO> materialize(
        @PathVariable("id") Long id,
        @RequestBody MaterializeRequestDTO request
    ) {
        LOG.debug("REST request to materialize DataModel : {} to table : {}", id, request.getTableName());
        MaterializeResponseDTO response = materializeService.materialize(request);
        if (response.isSuccess()) {
            dataModelService.findOne(id).ifPresent(existing -> {
                existing.setDataSourceId(request.getDataSourceId());
                existing.setSchemaName(request.getSchemaName());
                existing.setTableName(request.getTableName());
                dataModelService.partialUpdate(existing);
            });
            return ResponseEntity.ok().body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}