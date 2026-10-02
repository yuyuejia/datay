package com.data.datafusion.mcp.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Authority;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.mcp.McpTokenService;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link DatayHarnessExecutor} 的单元测试，重点校验鉴权、租户隔离、写开关与权限约束。
 */
class DatayHarnessExecutorTest {

    private DatayHarnessToolRegistry toolRegistry;
    private McpTokenService mcpTokenService;
    private TenantRepository tenantRepository;
    private UserRepository userRepository;
    private DatayHarnessExecutor executor;

    @BeforeEach
    void setUp() {
        toolRegistry = mock(DatayHarnessToolRegistry.class);
        mcpTokenService = mock(McpTokenService.class);
        tenantRepository = mock(TenantRepository.class);
        userRepository = mock(UserRepository.class);
        executor = new DatayHarnessExecutor(toolRegistry, mcpTokenService, tenantRepository, userRepository, new ObjectMapper(), true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectInvalidToken() {
        when(mcpTokenService.authenticateUser("bad-token")).thenReturn(Optional.empty());

        CallToolResult result = executor.invoke("datasource_list", context("bad-token"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("访问令牌");
    }

    @Test
    void shouldRejectUserWithoutTenant() {
        User user = new User();
        user.setId(7L);
        when(mcpTokenService.authenticateUser("mcp_7_x")).thenReturn(Optional.of(user));
        when(tenantRepository.findDefaultTenantByUserId(7L)).thenReturn(Optional.empty());

        CallToolResult result = executor.invoke("datasource_list", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("未关联任何租户");
    }

    @Test
    void shouldRejectUnknownTool() {
        stubValidUserAndTenant("ROLE_USER");
        when(toolRegistry.find("not_exists")).thenReturn(Optional.empty());

        CallToolResult result = executor.invoke("not_exists", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("未知的 harness 工具");
    }

    @Test
    void shouldRejectMutatingToolWhenWritesDisabled() {
        stubValidUserAndTenant("ROLE_USER");
        DatayHarnessExecutor readOnlyExecutor = new DatayHarnessExecutor(
            toolRegistry,
            mcpTokenService,
            tenantRepository,
            userRepository,
            new ObjectMapper(),
            false
        );
        when(toolRegistry.find("datasource_create")).thenReturn(Optional.of(stubTool("datasource_create", true, Set.of())));

        CallToolResult result = readOnlyExecutor.invoke("datasource_create", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("写操作已关闭");
    }

    @Test
    void shouldRejectToolWhenRequiredAuthorityMissing() {
        stubValidUserAndTenant("ROLE_USER");
        when(toolRegistry.find("datasource_set_default_warehouse")).thenReturn(
            Optional.of(stubTool("datasource_set_default_warehouse", true, Set.of("ROLE_ADMIN")))
        );

        CallToolResult result = executor.invoke("datasource_set_default_warehouse", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("缺少调用").contains("ROLE_ADMIN");
    }

    @Test
    void shouldExecuteToolWithinTenantAndSecurityContextAndCleanUp() {
        stubValidUserAndTenant("ROLE_ADMIN");
        AtomicReference<Long> tenantSeen = new AtomicReference<>();
        AtomicReference<Set<String>> authoritiesSeen = new AtomicReference<>();
        AtomicReference<String> loginSeen = new AtomicReference<>();
        when(toolRegistry.find("datasource_list")).thenReturn(
            Optional.of(
                DatayHarnessTool.read("datasource_list", "list", Map.of(), (args, context) -> {
                    tenantSeen.set(TenantContext.getTenantId());
                    authoritiesSeen.set(SecurityUtils.getCurrentUserAuthorities());
                    loginSeen.set(context.getLogin());
                    return Map.of("ok", true);
                })
            )
        );

        CallToolResult result = executor.invoke("datasource_list", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("\"ok\":true");
        assertThat(tenantSeen.get()).isEqualTo(3L);
        assertThat(authoritiesSeen.get()).contains("ROLE_ADMIN");
        assertThat(loginSeen.get()).isEqualTo("alice");
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(SecurityUtils.getCurrentUserAuthorities()).isEmpty();
    }

    @Test
    void shouldMapIllegalArgumentExceptionToArgumentError() {
        stubValidUserAndTenant("ROLE_USER");
        when(toolRegistry.find("datasource_get")).thenReturn(
            Optional.of(
                DatayHarnessTool.read("datasource_get", "get", Map.of(), (args, context) -> {
                    throw new IllegalArgumentException("缺少必填参数: id");
                })
            )
        );

        CallToolResult result = executor.invoke("datasource_get", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).startsWith("参数错误").contains("缺少必填参数: id");
    }

    @Test
    void shouldWrapNullResultAsSuccess() {
        stubValidUserAndTenant("ROLE_USER");
        when(toolRegistry.find("datasource_delete")).thenReturn(Optional.of(stubTool("datasource_delete", true, Set.of())));

        CallToolResult result = executor.invoke("datasource_delete", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("\"success\":true").contains("datasource_delete");
    }

    @Test
    void shouldHonourTenantHeaderWhenTenantBelongsToUser() {
        stubValidUserAndTenant("ROLE_USER");
        Tenant other = new Tenant();
        other.setId(9L);
        other.setCode("t9");
        when(tenantRepository.findAllByUserId(7L)).thenReturn(List.of(other));
        AtomicReference<Long> tenantSeen = new AtomicReference<>();
        when(toolRegistry.find("datasource_list")).thenReturn(
            Optional.of(
                DatayHarnessTool.read("datasource_list", "list", Map.of(), (args, context) -> {
                    tenantSeen.set(TenantContext.getTenantId());
                    return Map.of();
                })
            )
        );

        CallToolResult result = executor.invoke(
            "datasource_list",
            McpTransportContext.create(Map.of("Authorization", "mcp_7_x", "X-Tenant-Id", "9")),
            Map.of()
        );

        assertThat(result.isError()).isFalse();
        assertThat(tenantSeen.get()).isEqualTo(9L);
    }

    @Test
    void shouldRejectTenantHeaderOutsideUserScope() {
        stubValidUserAndTenant("ROLE_USER");
        when(tenantRepository.findAllByUserId(7L)).thenReturn(List.of());

        CallToolResult result = executor.invoke(
            "datasource_list",
            McpTransportContext.create(Map.of("Authorization", "mcp_7_x", "X-Tenant-Code", "t9")),
            Map.of()
        );

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("无权访问指定租户");
    }

    private void stubValidUserAndTenant(String authority) {
        User user = new User();
        user.setId(7L);
        user.setLogin("alice");
        when(mcpTokenService.authenticateUser("mcp_7_x")).thenReturn(Optional.of(user));

        User withAuthorities = new User();
        withAuthorities.setId(7L);
        withAuthorities.setLogin("alice");
        withAuthorities.setPassword("$2a$10$testpasswordhash");
        withAuthorities.getAuthorities().add(new Authority().name(authority));
        when(userRepository.findOneWithAuthoritiesById(7L)).thenReturn(Optional.of(withAuthorities));

        Tenant tenant = new Tenant();
        tenant.setId(3L);
        tenant.setCode("t3");
        when(tenantRepository.findDefaultTenantByUserId(7L)).thenReturn(Optional.of(tenant));
    }

    private static DatayHarnessTool stubTool(String name, boolean mutating, Set<String> authorities) {
        return DatayHarnessTool.of(name, name, Map.of("type", "object"), mutating, authorities, (args, context) -> null);
    }

    private static McpTransportContext context(String token) {
        return McpTransportContext.create(Map.<String, Object>of("Authorization", token));
    }

    private static String text(CallToolResult result) {
        return result
            .content()
            .stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining());
    }
}
