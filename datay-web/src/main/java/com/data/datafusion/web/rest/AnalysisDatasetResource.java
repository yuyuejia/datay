package com.data.datafusion.web.rest;

import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.data.datafusion.service.dashboard.DatasetQueryService;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
 * 分析数据集管理接口。数据集可被多个分析看板引用。
 */
@RestController
@RequestMapping("/api/analysis-datasets")
public class AnalysisDatasetResource {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisDatasetResource.class);

    private static final String ENTITY_NAME = "analysisDataset";
    private static final String DEFAULT_PREVIEW_NAME = "数据集预览";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AnalysisDatasetService datasetService;
    private final ObjectMapper objectMapper;

    public AnalysisDatasetResource(AnalysisDatasetService datasetService, ObjectMapper objectMapper) {
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("")
    public ResponseEntity<List<AnalysisDatasetDTO>> getAllDatasets(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search
    ) {
        LOG.debug("REST request to get a page of AnalysisDatasets with search: {}", search);
        Page<AnalysisDatasetDTO> page = datasetService.findAll(pageable, search);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/all")
    public ResponseEntity<List<AnalysisDatasetDTO>> getAllDatasetsSimple() {
        return ResponseEntity.ok().body(datasetService.findAllSimple());
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<AnalysisDatasetDTO> getDatasetByCode(@PathVariable("code") String code) {
        return ResponseUtil.wrapOrNotFound(datasetService.findByCode(code));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisDatasetDTO> getDataset(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(datasetService.findOne(id));
    }

    @PostMapping("")
    public ResponseEntity<AnalysisDatasetDTO> createDataset(@RequestBody AnalysisDatasetDTO dto) throws URISyntaxException {
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new dataset cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            AnalysisDatasetDTO result = datasetService.save(dto);
            return ResponseEntity.created(new URI("/api/analysis-datasets/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, String.valueOf(result.getId())))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDataset");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnalysisDatasetDTO> updateDataset(@PathVariable("id") String id, @RequestBody AnalysisDatasetDTO dto) {
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!id.equals(dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        try {
            Optional<AnalysisDatasetDTO> result = datasetService.update(id, dto);
            return ResponseUtil.wrapOrNotFound(
                result,
                HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDataset");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataset(@PathVariable("id") String id) {
        LOG.debug("REST request to delete AnalysisDataset : {}", id);
        try {
            datasetService.delete(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "datasetInUse");
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }

    /**
     * 校验数据集定义（不落库）。
     */
    @PostMapping("/validate")
    public ResponseEntity<AnalysisDatasetDTO> validateDataset(@RequestBody AnalysisDatasetDTO dto) {
        try {
            return ResponseEntity.ok().body(datasetService.validateDefinition(dto));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDataset");
        }
    }

    /**
     * 预览未保存的数据集定义。请求体：type / dataSourceId / definition(JSON 字符串) / filterValues。
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewDefinition(@RequestBody Map<String, Object> body) {
        try {
            AnalysisDatasetDTO dto = new AnalysisDatasetDTO();
            dto.setName(body.get("name") == null ? DEFAULT_PREVIEW_NAME : String.valueOf(body.get("name")));
            dto.setType(body.get("type") == null ? null : String.valueOf(body.get("type")));
            dto.setDataSourceId(body.get("dataSourceId") == null ? null : String.valueOf(body.get("dataSourceId")));
            dto.setDefinition(toDefinitionJson(body.get("definition")));
            Map<String, Object> filterValues = body.get("filterValues") instanceof Map<?, ?> map
                ? DatasetQueryService.asMap(map)
                : null;
            return ResponseEntity.ok().body(datasetService.previewDefinition(dto, filterValues));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "datasetPreviewError");
        } catch (SQLException e) {
            throw new BadRequestAlertException("查询执行失败：" + e.getMessage(), ENTITY_NAME, "datasetPreviewError");
        }
    }

    /**
     * 预览已保存数据集。请求体为运行时参数值，可为空。
     */
    @PostMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> previewDataset(
        @PathVariable("id") String id,
        @RequestBody(required = false) Map<String, Object> filterValues
    ) {
        try {
            return ResponseEntity.ok().body(datasetService.preview(id, filterValues));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "datasetPreviewError");
        } catch (SQLException e) {
            throw new BadRequestAlertException("查询执行失败：" + e.getMessage(), ENTITY_NAME, "datasetPreviewError");
        }
    }

    private String toDefinitionJson(Object definition) {
        if (definition == null) {
            return null;
        }
        if (definition instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据集定义序列化失败");
        }
    }
}
