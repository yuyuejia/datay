package com.data.datafusion.web.rest;

import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.TenantService;
import com.data.datafusion.service.dto.TenantDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the current tenant context for the tenant settings page.
 */
@RestController
@RequestMapping("/api/tenant-settings")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
public class TenantSettingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(TenantSettingsResource.class);

    private final TenantService tenantService;

    public TenantSettingsResource(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * {@code GET /tenant-settings/context} : current tenant id / code / name.
     *
     * @return the current tenant context, values may be null when unavailable.
     */
    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> getContext() {
        LOG.debug("REST request to get current tenant context");
        Long tenantId = SecurityUtils.getCurrentTenantId().orElse(null);
        String tenantCode = SecurityUtils.getCurrentTenantCode().orElse(null);

        Optional<TenantDTO> tenant = Optional.empty();
        if (tenantCode != null && !tenantCode.isBlank()) {
            tenant = tenantService.findByCode(tenantCode);
        }
        if (tenant.isEmpty() && tenantId != null) {
            tenant = tenantService.findOne(tenantId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenant.map(TenantDTO::getId).orElse(tenantId));
        result.put("tenantCode", tenant.map(TenantDTO::getCode).orElse(tenantCode));
        result.put("tenantName", tenant.map(TenantDTO::getName).orElse(null));
        return ResponseEntity.ok(result);
    }
}
