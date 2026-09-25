package com.data.datafusion.mcp;

import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolContext;
import com.data.datafusion.ai.tool.AiToolRegistry;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.DomainUserDetailsService.UserWithId;
import com.data.datafusion.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 指标问数 MCP 工具执行器。
 *
 * <p>统一处理一次 MCP 指标工具调用的横切逻辑：
 * <ol>
 *     <li>用 Bearer Token 解析用户；</li>
 *     <li>解析并校验租户（{@code X-Tenant-Id} / {@code X-Tenant-Code}，否则用户默认租户）；</li>
 *     <li>设置 {@link TenantContext} 与 {@code SecurityContextHolder}，使指标查询同时具备租户隔离与角色数据权限；</li>
 *     <li>按名查找只读 {@link AiTool} 并执行，结果序列化为 JSON 回传；</li>
 *     <li>无论成功失败都清理线程上下文，避免污染后续请求。</li>
 * </ol>
 */
@Service
public class MetricMcpToolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MetricMcpToolExecutor.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private final AiToolRegistry toolRegistry;
    private final McpTokenService mcpTokenService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public MetricMcpToolExecutor(
        AiToolRegistry toolRegistry,
        McpTokenService mcpTokenService,
        TenantRepository tenantRepository,
        UserRepository userRepository,
        ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.mcpTokenService = mcpTokenService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次指标问数工具调用。
     *
     * @param toolName 工具名，需在 {@link AiToolRegistry} 中存在且为只读工具
     * @param context  MCP 传输上下文，携带访问令牌与可选租户头
     * @param args     工具入参
     * @return MCP 工具调用结果，失败时返回 isError 结果而非抛异常
     */
    public CallToolResult invoke(String toolName, McpTransportContext context, Map<String, Object> args) {
        String token = contextValue(context, AUTHORIZATION_HEADER);
        User user = mcpTokenService.authenticateUser(token).orElse(null);
        if (user == null) {
            return errorResult("无效或缺失的访问令牌");
        }

        Tenant tenant;
        try {
            tenant = resolveTenant(user, context);
        } catch (TenantAccessException e) {
            return errorResult(e.getMessage());
        }
        if (tenant == null) {
            return errorResult("用户未关联任何租户");
        }

        AiTool tool = toolRegistry.find(toolName).orElse(null);
        if (tool == null) {
            return errorResult("未知的指标工具: " + toolName);
        }
        if (tool.mutating()) {
            return errorResult("该工具具备写操作，指标问数端点已禁用: " + toolName);
        }

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setTenantCode(tenant.getCode());
        bindSecurityContext(user);
        try {
            Object result = tool.execute(args == null ? Map.of() : args, new AiToolContext(null, null, null, null));
            return jsonResult(result);
        } catch (Exception e) {
            LOG.error("Metric MCP tool {} execution failed", toolName, e);
            return errorResult("指标工具执行失败: " + e.getMessage());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 把 token 对应用户（含角色权限）写入 SecurityContext，供 RoleDataScopeService 解析数据范围。
     */
    private void bindSecurityContext(User user) {
        UserWithId principal = userRepository
            .findOneWithAuthoritiesById(user.getId())
            .map(UserWithId::fromUser)
            .orElse(null);
        if (principal == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Tenant resolveTenant(User user, McpTransportContext context) {
        String headerTenantId = contextValue(context, TENANT_ID_HEADER);
        String headerTenantCode = contextValue(context, TENANT_CODE_HEADER);
        if (headerTenantId == null && headerTenantCode == null) {
            return tenantRepository.findDefaultTenantByUserId(user.getId()).orElse(null);
        }

        List<Tenant> tenants = tenantRepository.findAllByUserId(user.getId());
        if (headerTenantId != null) {
            Long tenantId;
            try {
                tenantId = Long.valueOf(headerTenantId);
            } catch (NumberFormatException e) {
                throw new TenantAccessException("无效的租户标识: " + headerTenantId);
            }
            return tenants
                .stream()
                .filter(t -> tenantId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new TenantAccessException("无权访问指定租户: " + headerTenantId));
        }
        return tenants
            .stream()
            .filter(t -> headerTenantCode.equals(t.getCode()))
            .findFirst()
            .orElseThrow(() -> new TenantAccessException("无权访问指定租户: " + headerTenantCode));
    }

    private CallToolResult jsonResult(Object value) {
        try {
            return CallToolResult.builder().textContent(List.of(objectMapper.writeValueAsString(value))).build();
        } catch (Exception e) {
            return errorResult("结果序列化失败: " + e.getMessage());
        }
    }

    private CallToolResult errorResult(String message) {
        return CallToolResult.builder().isError(true).textContent(List.of(message)).build();
    }

    private static String contextValue(McpTransportContext context, String key) {
        if (context == null) {
            return null;
        }
        Object value = context.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static class TenantAccessException extends RuntimeException {

        TenantAccessException(String message) {
            super(message);
        }
    }
}
