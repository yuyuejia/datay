package com.data.datafusion.config;

import com.data.datafusion.domain.TenantAware;
import com.data.datafusion.security.SecurityUtils;
import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

@Component
public class TenantAwareEntityListener {

    @PrePersist
    public void onPrePersist(Object entity) {
        if (entity instanceof TenantAware tenantAware && tenantAware.getTenantId() == null) {
            SecurityUtils
                .getCurrentTenantId()
                .ifPresent(tenantId -> tenantAware.setTenantId(String.valueOf(tenantId)));
        }
    }
}