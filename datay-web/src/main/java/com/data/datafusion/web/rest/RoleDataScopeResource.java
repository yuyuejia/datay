package com.data.datafusion.web.rest;

import com.data.datafusion.domain.RoleDataScope;
import com.data.datafusion.service.RoleDataScopeService;
import com.data.datafusion.service.dto.RoleDataScopeDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
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
 * REST controller for managing {@link RoleDataScope}（基于角色的维度成员数据范围）。
 */
@RestController
@RequestMapping("/api/role-data-scopes")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RoleDataScopeResource {

    private static final Logger LOG = LoggerFactory.getLogger(RoleDataScopeResource.class);

    private static final String ENTITY_NAME = "roleDataScope";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final RoleDataScopeService roleDataScopeService;

    public RoleDataScopeResource(RoleDataScopeService roleDataScopeService) {
        this.roleDataScopeService = roleDataScopeService;
    }

    @GetMapping("")
    public ResponseEntity<List<RoleDataScopeDTO>> getAllRoleDataScopes(@RequestParam(value = "roleName", required = false) String roleName) {
        LOG.debug("REST request to get RoleDataScopes, roleName : {}", roleName);
        return ResponseEntity.ok().body(roleDataScopeService.findAll(roleName));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDataScopeDTO> getRoleDataScope(@PathVariable("id") String id) {
        LOG.debug("REST request to get RoleDataScope : {}", id);
        Optional<RoleDataScopeDTO> dto = roleDataScopeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    @PostMapping("")
    public ResponseEntity<RoleDataScopeDTO> createRoleDataScope(@RequestBody RoleDataScopeDTO dto) throws URISyntaxException {
        LOG.debug("REST request to save RoleDataScope : {}", dto);
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new roleDataScope cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            RoleDataScopeDTO result = roleDataScopeService.save(dto);
            return ResponseEntity.created(new URI("/api/role-data-scopes/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDataScopeDTO> updateRoleDataScope(
        @PathVariable("id") String id,
        @RequestBody RoleDataScopeDTO dto
    ) {
        LOG.debug("REST request to update RoleDataScope : {}, {}", id, dto);
        if (dto.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!id.equals(dto.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        try {
            RoleDataScopeDTO result = roleDataScopeService.update(dto);
            return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoleDataScope(@PathVariable("id") String id) {
        LOG.debug("REST request to delete RoleDataScope : {}", id);
        roleDataScopeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
