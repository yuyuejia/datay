package com.data.datafusion.web.rest;

import com.data.datafusion.security.AuthoritiesConstants;
import com.data.datafusion.service.TenantService;
import com.data.datafusion.service.UserService;
import com.data.datafusion.service.dto.AdminUserDTO;
import com.data.datafusion.service.dto.TenantDTO;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
public class TenantResource {

    private static final Logger LOG = LoggerFactory.getLogger(TenantResource.class);

    private static final String ENTITY_NAME = "tenant";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TenantService tenantService;

    private final UserService userService;

    public TenantResource(TenantService tenantService, UserService userService) {
        this.tenantService = tenantService;
        this.userService = userService;
    }

    @PostMapping("")
    public ResponseEntity<TenantDTO> createTenant(@RequestBody TenantDTO dto) throws URISyntaxException {
        LOG.debug("REST request to create Tenant : {}", dto);
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new tenant cannot already have an ID", ENTITY_NAME, "idexists");
        }
        TenantDTO result = tenantService.save(dto);
        return ResponseEntity.created(new URI("/api/admin/tenants/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantDTO> updateTenant(@PathVariable Long id, @RequestBody TenantDTO dto) throws URISyntaxException {
        LOG.debug("REST request to update Tenant : {}, {}", id, dto);
        if (dto.getId() == null || !dto.getId().equals(id)) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idinvalid");
        }
        TenantDTO result = tenantService.save(dto);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TenantDTO> partialUpdateTenant(@PathVariable Long id, @RequestBody TenantDTO dto) {
        LOG.debug("REST request to partial update Tenant : {}, {}", id, dto);
        if (dto.getId() == null || !dto.getId().equals(id)) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idinvalid");
        }
        Optional<TenantDTO> result = tenantService.partialUpdate(dto);
        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id.toString())
        );
    }

    @GetMapping("")
    public ResponseEntity<List<TenantDTO>> getAllTenants() {
        LOG.debug("REST request to get all Tenants");
        return ResponseEntity.ok(tenantService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDTO> getTenant(@PathVariable Long id) {
        LOG.debug("REST request to get Tenant : {}", id);
        return ResponseUtil.wrapOrNotFound(tenantService.findOne(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        LOG.debug("REST request to delete Tenant : {}", id);
        tenantService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PostMapping("/{tenantId}/users/{userId}")
    public ResponseEntity<Void> addUserToTenant(@PathVariable Long tenantId, @PathVariable Long userId) {
        LOG.debug("REST request to add user {} to tenant {}", userId, tenantId);
        tenantService.addUserToTenant(tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tenantId}/users/{userId}")
    public ResponseEntity<Void> removeUserFromTenant(@PathVariable Long tenantId, @PathVariable Long userId) {
        LOG.debug("REST request to remove user {} from tenant {}", userId, tenantId);
        tenantService.removeUserFromTenant(tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tenantId}/users")
    public ResponseEntity<List<AdminUserDTO>> getTenantUsers(@PathVariable Long tenantId, Pageable pageable) {
        LOG.debug("REST request to get users of tenant {}", tenantId);
        Page<AdminUserDTO> page = tenantService.getTenantUsers(tenantId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<AdminUserDTO>> searchUsers(@RequestParam String query, Pageable pageable) {
        LOG.debug("REST request to search users with query : {}", query);
        Page<AdminUserDTO> page = userService.searchByLogin(query, pageable);
        return ResponseEntity.ok().body(page.getContent());
    }
}