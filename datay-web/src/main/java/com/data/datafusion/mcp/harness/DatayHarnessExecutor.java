package com.data.datafusion.mcp.harness;

import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.mcp.McpTokenService;
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
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * DataY 管理 Harness 的工具执行器。
 *
 * <p>统一处理一次 MCP 管理工具调用的横切逻辑：
 * <ol>
 *     <li>用 {@code Authorization: Bearer mcp_xxx} 解析用户；</li>
 *     <li>解析并校验租户（{@code X-Tenant-Id} / {@code X-Tenant-Code}，否则用户默认租户），使所有领域服务自动带上租户隔离；</li>
 *     <li>把用户及其权限写入 {@code SecurityContextHolder}，使角色数据权限与 {@code @PreAuthorize} 语义保持一致；</li>
 *     <li>校验写操作开关（{@code datay.mcp.harness.allow-mutating-tools}）与工具声明的 {@link DatayHarnessTool#requiredAuthorities()}；</li>
 *     <li>执行工具并把结果序列化为 JSON，失败时返回 MCP 错误结果而非抛异常；</li>
 *     <li>无论成功失败都清理线程上下文，避免污染后续请求。</li>
 * </ol>
 */
@Service
public class DatayHarnessExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DatayHarnessExecutor.class);

    static final String AUTHORIZATION_HEADER = "Authorization";

    static final String TENANT_ID_HEADER = "X-Tenant-Id";

    static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private final DatayHarnessToolRegistry toolRegistry;
    private final McpTokenService mcpTokenService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final boolean allowMutatingTools;

    public DatayHarnessExecutor(
        DatayHarnessToolRegistry toolRegistry,
        McpTokenService mcpTokenService,
        TenantRepository tenantRepository,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        @Value("${datay.mcp.harness.allow-mutating-tools:true}") boolean allowMutatingTools
    ) {
        this.toolRegistry = toolRegistry;
        this.mcpTokenService = mcpTokenService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.allowMutatingTools = allowMutatingTools;
    }

    /**
     * 执行一次 DataY 管理工具调用。
     *
     * @param toolName 工具名，需已在 {@link DatayHarnessToolRegistry} 注册
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

        DatayHarnessTool tool = toolRegistry.find(toolName).orElse(null);
        if (tool == null) {
            return errorResult("未知的 harness 工具: " + toolName);
        }
        if (tool.mutating() && !allowMutatingTools) {
            return errorResult("写操作已关闭，无法调用: " + toolName + "（可通过 datay.mcp.harness.allow-mutating-tools 开启）");
        }

        Set<String> authorities = bindSecurityContext(user);
        Set<String> missing = tool
            .requiredAuthorities()
            .stream()
            .filter(required -> !authorities.contains(required))
            .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            SecurityContextHolder.clearContext();
            return errorResult("当前用户缺少调用 " + toolName + " 所需权限: " + String.join(", ", missing));
        }

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setTenantCode(tenant.getCode());
        try {
            DatayHarnessContext toolContext = new DatayHarnessContext(user, tenant.getId(), tenant.getCode());
            Object result = tool.execute(args == null ? Map.of() : args, toolContext);
            return jsonResult(summarize(tool, result));
        } catch (IllegalArgumentException e) {
            LOG.warn("Harness tool {} rejected invalid arguments: {}", toolName, e.getMessage());
            return errorResult("参数错误: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Harness tool {} execution failed", toolName, e);
            return errorResult("工具执行失败: " + e.getMessage());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 把 token 对应用户（含角色权限）写入 SecurityContext，供 RoleDataScopeService 解析数据范围。
     *
     * @return 该用户实际拥有的权限集合
     */
    private Set<String> bindSecurityContext(User user) {
        UserWithId principal = userRepository
            .findOneWithAuthoritiesById(user.getId())
            .map(UserWithId::fromUser)
            .orElse(null);
        if (principal == null) {
            SecurityContextHolder.clearContext();
            return Set.of();
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
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

    /**
     * 统一包装工具返回值：{@code null} 表示操作成功但无返回体，避免模型把 {@code null} 误读为失败。
     */
    private Object summarize(DatayHarnessTool tool, Object result) {
        if (result != null) {
            return result;
        }
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("success", true);
        success.put("tool", tool.name());
        return success;
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

    static String contextValue(McpTransportContext context, String key) {
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
