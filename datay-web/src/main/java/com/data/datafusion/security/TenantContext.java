package com.data.datafusion.security;

public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> TENANT_CODE_HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        TENANT_ID_HOLDER.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    public static void setTenantCode(String tenantCode) {
        TENANT_CODE_HOLDER.set(tenantCode);
    }

    public static String getTenantCode() {
        return TENANT_CODE_HOLDER.get();
    }

    public static void clear() {
        TENANT_ID_HOLDER.remove();
        TENANT_CODE_HOLDER.remove();
    }
}