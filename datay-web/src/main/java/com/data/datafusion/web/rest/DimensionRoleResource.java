package com.data.datafusion.web.rest;

import com.data.datafusion.service.DimensionRoleService;
import com.data.datafusion.service.dto.DimensionRoleDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 维度角色管理接口：角色隶属于维度模型。
 */
@RestController
@RequestMapping("/api/data-models/{modelId}/dimension-roles")
public class DimensionRoleResource {

    private static final Logger LOG = LoggerFactory.getLogger(DimensionRoleResource.class);

    private static final String ENTITY_NAME = "dimensionRole";

    private final DimensionRoleService dimensionRoleService;

    public DimensionRoleResource(DimensionRoleService dimensionRoleService) {
        this.dimensionRoleService = dimensionRoleService;
    }

    @GetMapping("")
    public List<DimensionRoleDTO> list(@PathVariable String modelId) {
        LOG.debug("REST request to list dimension roles for model {}", modelId);
        return dimensionRoleService.findByDimensionModelId(modelId);
    }

    @PostMapping("")
    public ResponseEntity<DimensionRoleDTO> create(@PathVariable String modelId, @RequestBody(required = false) DimensionRoleDTO dto) {
        LOG.debug("REST request to create dimension role for model {}", modelId);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(dimensionRoleService.create(modelId, dto));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDimensionRole");
        }
    }

    /**
     * 批量创建角色：用于事实表关联同一维度时快速生成角色，已存在的编码直接复用。
     */
    @PostMapping("/batch")
    public List<DimensionRoleDTO> batchCreate(@PathVariable String modelId, @RequestBody(required = false) List<DimensionRoleDTO> roles) {
        LOG.debug("REST request to batch create dimension roles for model {}", modelId);
        try {
            return dimensionRoleService.batchCreate(modelId, roles);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDimensionRole");
        }
    }

    /**
     * 替换角色清单：维度编辑页统一保存时使用。
     */
    @PutMapping("")
    public List<DimensionRoleDTO> replace(@PathVariable String modelId, @RequestBody(required = false) List<DimensionRoleDTO> roles) {
        LOG.debug("REST request to replace dimension roles for model {}", modelId);
        try {
            return dimensionRoleService.replace(modelId, roles);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDimensionRole");
        }
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<DimensionRoleDTO> update(
        @PathVariable String modelId,
        @PathVariable String roleId,
        @RequestBody(required = false) DimensionRoleDTO dto
    ) {
        LOG.debug("REST request to update dimension role {} for model {}", roleId, modelId);
        try {
            return ResponseEntity.ok(dimensionRoleService.update(modelId, roleId, dto));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDimensionRole");
        }
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> delete(@PathVariable String modelId, @PathVariable String roleId) {
        LOG.debug("REST request to delete dimension role {} for model {}", roleId, modelId);
        try {
            dimensionRoleService.delete(modelId, roleId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
            }
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidDimensionRole");
        }
    }
}
