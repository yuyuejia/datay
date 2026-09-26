package com.data.datafusion.web.rest;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.service.AnalysisDashboardService;
import com.data.datafusion.service.dashboard.DashboardDataService;
import com.data.datafusion.service.dashboard.DashboardDimensionValidator;
import com.data.datafusion.service.dashboard.DashboardSpecValidator;
import com.data.datafusion.service.dto.AnalysisDashboardDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link AnalysisDashboard} and querying its datasets.
 */
@RestController
@RequestMapping("/api/analysis-dashboards")
public class AnalysisDashboardResource {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisDashboardResource.class);

    private static final String ENTITY_NAME = "analysisDashboard";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AnalysisDashboardService dashboardService;
    private final DashboardDataService dashboardDataService;
    private final DashboardSpecValidator specValidator;
    private final DashboardDimensionValidator dimensionValidator;

    public AnalysisDashboardResource(
        AnalysisDashboardService dashboardService,
        DashboardDataService dashboardDataService,
        DashboardSpecValidator specValidator,
        DashboardDimensionValidator dimensionValidator
    ) {
        this.dashboardService = dashboardService;
        this.dashboardDataService = dashboardDataService;
        this.specValidator = specValidator;
        this.dimensionValidator = dimensionValidator;
    }

    @PostMapping("")
    public ResponseEntity<AnalysisDashboardDTO> createDashboard(@RequestBody AnalysisDashboardDTO dto) throws URISyntaxException {
        LOG.debug("REST request to save AnalysisDashboard : {}", dto);
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new dashboard cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            AnalysisDashboardDTO result = dashboardService.save(dto);
            return ResponseEntity.created(new URI("/api/analysis-dashboards/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidSpec");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnalysisDashboardDTO> updateDashboard(@PathVariable("id") Long id, @RequestBody AnalysisDashboardDTO dto) {
        LOG.debug("REST request to update AnalysisDashboard : {}, {}", id, dto);
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!id.equals(dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        try {
            Optional<AnalysisDashboardDTO> result = dashboardService.update(id, dto);
            return ResponseUtil.wrapOrNotFound(
                result,
                HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dto.getId().toString())
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidSpec");
        }
    }

    @GetMapping("")
    public ResponseEntity<List<AnalysisDashboardDTO>> getAllDashboards(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search
    ) {
        LOG.debug("REST request to get a page of AnalysisDashboards with search: {}", search);
        Page<AnalysisDashboardDTO> page = dashboardService.findAll(pageable, search);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/all")
    public ResponseEntity<List<AnalysisDashboardDTO>> getAllDashboardsSimple() {
        LOG.debug("REST request to get all AnalysisDashboards (simple)");
        return ResponseEntity.ok().body(dashboardService.findAllSimple());
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<AnalysisDashboardDTO> getDashboardByCode(@PathVariable("code") String code) {
        LOG.debug("REST request to get AnalysisDashboard by code : {}", code);
        return ResponseUtil.wrapOrNotFound(dashboardService.findByCode(code));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisDashboardDTO> getDashboard(@PathVariable("id") Long id) {
        LOG.debug("REST request to get AnalysisDashboard : {}", id);
        return ResponseUtil.wrapOrNotFound(dashboardService.findOne(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDashboard(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete AnalysisDashboard : {}", id);
        dashboardService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    /**
     * 执行看板数据集。请求体为运行时筛选器值（键为筛选器 id），可为空。
     */
    @PostMapping("/{id}/datasets/{datasetId}/query")
    public ResponseEntity<Map<String, Object>> queryDataset(
        @PathVariable("id") Long id,
        @PathVariable("datasetId") String datasetId,
        @RequestBody(required = false) Map<String, Object> filterValues
    ) {
        LOG.debug("REST request to query dashboard {} dataset {}", id, datasetId);
        try {
            return ResponseEntity.ok().body(dashboardDataService.queryDataset(id, datasetId, filterValues));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "datasetQueryError");
        } catch (SQLException e) {
            throw new BadRequestAlertException("查询执行失败：" + e.getMessage(), ENTITY_NAME, "datasetQueryError");
        }
    }

    /**
     * 加载筛选器选项（动态维度成员或静态值）。
     */
    @GetMapping("/{id}/filters/{filterId}/options")
    public ResponseEntity<Map<String, Object>> loadFilterOptions(
        @PathVariable("id") Long id,
        @PathVariable("filterId") String filterId,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        LOG.debug("REST request to load filter options for dashboard {} filter {}", id, filterId);
        try {
            return ResponseEntity.ok().body(dashboardDataService.loadFilterOptions(id, filterId, keyword, limit));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "filterOptionsError");
        }
    }

    /**
     * 校验并规范化看板 spec（不落库），供设计页在保存前预检。
     */
    @PostMapping("/validate-spec")
    public ResponseEntity<Map<String, Object>> validateSpec(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> spec = specValidator.validateAndNormalize(toSpecJson(body.get("spec")));
            dimensionValidator.sanitizeFilters(spec);
            return ResponseEntity.ok().body(spec);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidSpec");
        }
    }

    /**
     * 未保存看板的实时预览：基于内存 spec 执行数据集。
     */
    @PostMapping("/preview-dataset")
    public ResponseEntity<Map<String, Object>> previewDataset(@RequestBody Map<String, Object> body) {
        LOG.debug("REST request to preview dashboard dataset {}", body.get("datasetId"));
        try {
            Map<String, Object> spec = parseSpecObject(body.get("spec"));
            String datasetId = String.valueOf(body.get("datasetId"));
            @SuppressWarnings("unchecked")
            Map<String, Object> filterValues = body.get("filterValues") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;
            Long dataSourceId = DashboardDataService.asLong(body.get("dataSourceId"));
            return ResponseEntity.ok().body(dashboardDataService.queryDataset(spec, dataSourceId, datasetId, filterValues));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "datasetQueryError");
        } catch (SQLException e) {
            throw new BadRequestAlertException("查询执行失败：" + e.getMessage(), ENTITY_NAME, "datasetQueryError");
        }
    }

    /**
     * 未保存看板的实时预览：加载筛选器选项。
     */
    @PostMapping("/preview-filter-options")
    public ResponseEntity<Map<String, Object>> previewFilterOptions(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> spec = parseSpecObject(body.get("spec"));
            String filterId = String.valueOf(body.get("filterId"));
            String keyword = body.get("keyword") == null ? null : String.valueOf(body.get("keyword"));
            Integer limit = DashboardDataService.asLong(body.get("limit")) == null ? null : DashboardDataService.asLong(body.get("limit")).intValue();
            return ResponseEntity.ok().body(dashboardDataService.loadFilterOptions(spec, filterId, keyword, limit));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "filterOptionsError");
        }
    }

    private String toSpecJson(Object spec) {
        return spec instanceof String text ? text : specValidator.toJson(DashboardDataService.asMap(spec));
    }

    private Map<String, Object> parseSpecObject(Object spec) {
        String json = toSpecJson(spec);
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("看板定义不能为空");
        }
        return specValidator.parse(json);
    }
}
