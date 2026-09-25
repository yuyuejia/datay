package com.data.datafusion.config;

import com.data.datafusion.domain.TenantAware;
import com.data.datafusion.security.SecurityUtils;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

@Component
public class TenantAwareEntityListener {

    @PrePersist
    public void onPrePersist(Object entity) {
        fillTenantId(entity);
    }

    /**
     * 更新时若 tenant_id 为空则回填当前租户，避免 DTO 更新丢失租户后数据被租户过滤器隐藏。
     */
    @PreUpdate
    public void onPreUpdate(Object entity) {
        fillTenantId(entity);
    }

    private void fillTenantId(Object entity) {
        if (entity instanceof TenantAware tenantAware && tenantAware.getTenantId() == null) {
            SecurityUtils
                .getCurrentTenantId()
                .ifPresent(tenantId -> tenantAware.setTenantId(String.valueOf(tenantId)));
        }
    }
}