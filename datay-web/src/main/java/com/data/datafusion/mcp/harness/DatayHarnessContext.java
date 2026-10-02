package com.data.datafusion.mcp.harness;

import com.data.datafusion.domain.User;

/**
 * DataY 管理 Harness 的工具执行上下文。
 *
 * <p>承载一次 MCP 调用中已鉴权的调用方与生效租户，工具实现可据此做审计记录或额外的数据范围判断。
 * 租户隔离本身已由 {@link com.data.datafusion.security.TenantContext} 生效，
 * 这里保留显式副本，便于工具在跨线程或异步场景下使用。
 */
public final class DatayHarnessContext {

    private final User user;
    private final Long tenantId;
    private final String tenantCode;

    public DatayHarnessContext(User user, Long tenantId, String tenantCode) {
        this.user = user;
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
    }

    /**
     * 已通过 MCP 令牌鉴权的用户。
     */
    public User getUser() {
        return user;
    }

    /**
     * 调用方登录名，未登录时返回 {@code unknown}。
     */
    public String getLogin() {
        return user == null || user.getLogin() == null ? "unknown" : user.getLogin();
    }

    /**
     * 本次调用生效的租户 ID。
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 本次调用生效的租户编码。
     */
    public String getTenantCode() {
        return tenantCode;
    }
}
