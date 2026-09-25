package com.data.datafusion.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolContext;
import com.data.datafusion.ai.tool.AiToolRegistry;
import com.data.datafusion.domain.Authority;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
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
 * {@link MetricMcpToolExecutor} 的单元测试，重点校验鉴权、租户与数据权限上下文。
 */
class MetricMcpToolExecutorTest {

    private AiToolRegistry toolRegistry;
    private McpTokenService mcpTokenService;
    private TenantRepository tenantRepository;
    private UserRepository userRepository;
    private MetricMcpToolExecutor executor;

    @BeforeEach
    void setUp() {
        toolRegistry = mock(AiToolRegistry.class);
        mcpTokenService = mock(McpTokenService.class);
        tenantRepository = mock(TenantRepository.class);
        userRepository = mock(UserRepository.class);
        executor = new MetricMcpToolExecutor(toolRegistry, mcpTokenService, tenantRepository, userRepository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectInvalidToken() {
        when(mcpTokenService.authenticateUser("bad-token")).thenReturn(Optional.empty());

        CallToolResult result = executor.invoke(
            "metric_query_guide",
            context("bad-token"),
            Map.of()
        );

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("访问令牌");
    }

    @Test
    void shouldRejectUnknownTool() {
        stubValidUserAndTenant();
        when(toolRegistry.find("not_exists")).thenReturn(Optional.empty());

        CallToolResult result = executor.invoke("not_exists", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("未知的指标工具");
    }

    @Test
    void shouldRejectMutatingTool() {
        stubValidUserAndTenant();
        when(toolRegistry.find("write_tool")).thenReturn(Optional.of(mutatingTool()));

        CallToolResult result = executor.invoke("write_tool", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("写操作");
    }

    @Test
    void shouldExecuteReadToolWithinTenantAndSecurityContext() {
        stubValidUserAndTenant();

        AtomicReference<Long> tenantIdSeen = new AtomicReference<>();
        AtomicReference<Set<String>> authoritiesSeen = new AtomicReference<>();
        AiTool tool = new AiTool() {
            @Override
            public String name() {
                return "metric_query_guide";
            }

            @Override
            public String description() {
                return "guide";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "object");
            }

            @Override
            public Object execute(Map<String, Object> arguments, AiToolContext context) {
                tenantIdSeen.set(TenantContext.getTenantId());
                authoritiesSeen.set(SecurityUtils.getCurrentUserAuthorities());
                return Map.of("ok", true);
            }
        };
        when(toolRegistry.find("metric_query_guide")).thenReturn(Optional.of(tool));

        CallToolResult result = executor.invoke("metric_query_guide", context("mcp_7_x"), Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("\"ok\":true");
        assertThat(tenantIdSeen.get()).isEqualTo(3L);
        assertThat(authoritiesSeen.get()).contains("ROLE_USER");
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(SecurityUtils.getCurrentUserAuthorities()).isEmpty();
    }

    private void stubValidUserAndTenant() {
        User user = new User();
        user.setId(7L);
        user.setLogin("alice");
        when(mcpTokenService.authenticateUser("mcp_7_x")).thenReturn(Optional.of(user));

        User withAuthorities = new User();
        withAuthorities.setId(7L);
        withAuthorities.setLogin("alice");
        withAuthorities.setPassword("$2a$10$testpasswordhash");
        withAuthorities.getAuthorities().add(new Authority().name("ROLE_USER"));
        when(userRepository.findOneWithAuthoritiesById(7L)).thenReturn(Optional.of(withAuthorities));

        Tenant tenant = new Tenant();
        tenant.setId(3L);
        tenant.setCode("t3");
        when(tenantRepository.findDefaultTenantByUserId(7L)).thenReturn(Optional.of(tenant));
    }

    private static AiTool mutatingTool() {
        return new AiTool() {
            @Override
            public String name() {
                return "write_tool";
            }

            @Override
            public String description() {
                return "write";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "object");
            }

            @Override
            public boolean mutating() {
                return true;
            }

            @Override
            public Object execute(Map<String, Object> arguments, AiToolContext context) {
                return Map.of();
            }
        };
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
