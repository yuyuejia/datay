package com.data.datafusion.web.rest;

import com.data.datafusion.domain.RoleDatasetScope;
import com.data.datafusion.service.RoleDatasetScopeService;
import com.data.datafusion.service.dashboard.DatasetQueryService;
import com.data.datafusion.service.dto.RoleDatasetScopeDTO;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link RoleDatasetScope}（基于角色的数据集行级数据范围）。
 */
@RestController
@RequestMapping("/api/role-dataset-scopes")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RoleDatasetScopeResource {

    private static final Logger LOG = LoggerFactory.getLogger(RoleDatasetScopeResource.class);

    private static final String ENTITY_NAME = "roleDatasetScope";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final RoleDatasetScopeService roleDatasetScopeService;
    private final DatasetQueryService datasetQueryService;

    public RoleDatasetScopeResource(RoleDatasetScopeService roleDatasetScopeService, DatasetQueryService datasetQueryService) {
        this.roleDatasetScopeService = roleDatasetScopeService;
        this.datasetQueryService = datasetQueryService;
    }

    /**
     * 查询数据集输出字段的去重取值，供数据权限条件值选择使用。
     */
    @GetMapping("/field-values")
    public ResponseEntity<Map<String, Object>> getDatasetFieldValues(
        @RequestParam("datasetId") String datasetId,
        @RequestParam("fieldName") String fieldName,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        LOG.debug("REST request to get dataset field values : {}, {}", datasetId, fieldName);
        try {
            return ResponseEntity.ok().body(datasetQueryService.loadFieldValues(datasetId, fieldName, keyword, page, size));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "fieldValueError");
        } catch (SQLException e) {
            throw new BadRequestAlertException("数据集取值查询失败：" + e.getMessage(), ENTITY_NAME, "fieldValueError");
        }
    }

    @GetMapping("")
    public ResponseEntity<List<RoleDatasetScopeDTO>> getAllRoleDatasetScopes(
        @RequestParam(value = "roleName", required = false) String roleName
    ) {
        LOG.debug("REST request to get RoleDatasetScopes, roleName : {}", roleName);
        return ResponseEntity.ok().body(roleDatasetScopeService.findAll(roleName));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDatasetScopeDTO> getRoleDatasetScope(@PathVariable("id") String id) {
        LOG.debug("REST request to get RoleDatasetScope : {}", id);
        Optional<RoleDatasetScopeDTO> dto = roleDatasetScopeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    @PostMapping("")
    public ResponseEntity<RoleDatasetScopeDTO> createRoleDatasetScope(@RequestBody RoleDatasetScopeDTO dto) throws URISyntaxException {
        LOG.debug("REST request to save RoleDatasetScope : {}", dto);
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new roleDatasetScope cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            RoleDatasetScopeDTO result = roleDatasetScopeService.save(dto);
            return ResponseEntity.created(new URI("/api/role-dataset-scopes/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDatasetScopeDTO> updateRoleDatasetScope(
        @PathVariable("id") String id,
        @RequestBody RoleDatasetScopeDTO dto
    ) {
        LOG.debug("REST request to update RoleDatasetScope : {}, {}", id, dto);
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!id.equals(dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        try {
            RoleDatasetScopeDTO result = roleDatasetScopeService.update(dto);
            return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoleDatasetScope(@PathVariable("id") String id) {
        LOG.debug("REST request to delete RoleDatasetScope : {}", id);
        roleDatasetScopeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
