package com.data.datafusion.domain;

public interface TenantAware {

    String getTenantId();

    void setTenantId(String tenantId);
}