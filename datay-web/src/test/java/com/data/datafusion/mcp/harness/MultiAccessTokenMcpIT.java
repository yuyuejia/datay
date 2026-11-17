package com.data.datafusion.mcp.harness;

import static org.assertj.core.api.Assertions.assertThat;
import com.data.datafusion.IntegrationTest;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.AuthoritiesConstants;
import com.data.datafusion.service.UserAccessTokenService;
import com.data.datafusion.service.UserService;
import com.data.datafusion.service.dto.AdminUserDTO;
import com.data.datafusion.service.dto.CreatedUserAccessTokenDTO;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 验证同一用户创建多个访问令牌后，均可用于 MCP；停用其中一个令牌后，
 * 已建立的 MCP 会话和新建会话都应立即失效，另一个令牌不受影响。
 */
@IntegrationTest
class MultiAccessTokenMcpIT {

    private static final String LOGIN = "multi-token-mcp-test";

    private static final long SYSTEM_TENANT_ID = 0L;

    @Autowired
    @Qualifier("datayHarnessMcpTransportProvider")
    private HttpServletStreamableServerTransportProvider servlet;

    @Autowired
    @Qualifier("datasourceMcpTransportProvider")
    private HttpServletStreamableServerTransportProvider datasourceServlet;

    @Autowired
    @Qualifier("metricMcpTransportProvider")
    private HttpServletStreamableServerTransportProvider metricServlet;

    @Autowired
    private UserAccessTokenService userAccessTokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private com.data.datafusion.service.TenantService tenantService;

    @BeforeEach
    void setUp() {
        var user = userRepository.findOneByLogin(LOGIN).orElseGet(() -> {
            AdminUserDTO dto = new AdminUserDTO();
            dto.setLogin(LOGIN);
            dto.setFirstName("Multi");
            dto.setLastName("Mcp");
            dto.setEmail("multi-token-mcp-test@example.com");
            dto.setLangKey("zh-cn");
            dto.setAuthorities(Set.of(AuthoritiesConstants.USER));
            return userService.createUser(dto);
        });
        tenantService.addUserToTenant(SYSTEM_TENANT_ID, user.getId());
        tenantRepository.setDefaultTenantForUser(user.getId(), SYSTEM_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        userRepository.findOneByLogin(LOGIN).ifPresent(user -> tenantService.removeUserFromTenant(SYSTEM_TENANT_ID, user.getId()));
        userService.deleteUser(LOGIN);
    }

    @Test
    @WithMockUser(LOGIN)
    void shouldAllowMultipleTokensAndRevokeOneWithoutAffectingTheOther() throws Exception {
        CreatedUserAccessTokenDTO first = userAccessTokenService.createCurrentUserToken("Claude Desktop", "MCP 客户端 A");
        CreatedUserAccessTokenDTO second = userAccessTokenService.createCurrentUserToken("Cursor", "MCP 客户端 B");
        assertThat(first.getToken()).isNotEqualTo(second.getToken());

        String firstSession = initialize(first.getToken());
        assertThat(callCatalog(firstSession, first.getToken()).getStatus()).isEqualTo(200);
        String secondSession = initialize(second.getToken());
        assertThat(callCatalog(secondSession, second.getToken()).getStatus()).isEqualTo(200);

        assertThat(userAccessTokenService.updateCurrentUserToken(first.getAccessToken().getId(), null, null, "DISABLED")).isPresent();

        assertThat(callCatalog(firstSession, first.getToken()).getStatus()).isEqualTo(401);
        assertThat(initializeStatus(first.getToken())).isEqualTo(401);
        assertThat(callCatalog(secondSession, second.getToken()).getStatus()).isEqualTo(200);
    }

    @Test
    @WithMockUser(LOGIN)
    void shouldAuthenticateMultipleTokensOnAllMcpEndpoints() throws Exception {
        CreatedUserAccessTokenDTO first = userAccessTokenService.createCurrentUserToken("MCP A", "datasource 与 metric");
        CreatedUserAccessTokenDTO second = userAccessTokenService.createCurrentUserToken("MCP B", "datasource 与 metric");

        assertThat(initializeStatus(datasourceServlet, "/mcp/datasource", first.getToken())).isEqualTo(200);
        assertThat(initializeStatus(datasourceServlet, "/mcp/datasource", second.getToken())).isEqualTo(200);
        assertThat(initializeStatus(metricServlet, "/mcp/metric", first.getToken())).isEqualTo(200);
        assertThat(initializeStatus(metricServlet, "/mcp/metric", second.getToken())).isEqualTo(200);

        assertThat(userAccessTokenService.updateCurrentUserToken(first.getAccessToken().getId(), null, null, "DISABLED")).isPresent();

        assertThat(initializeStatus(datasourceServlet, "/mcp/datasource", first.getToken())).isEqualTo(401);
        assertThat(initializeStatus(metricServlet, "/mcp/metric", first.getToken())).isEqualTo(401);
        assertThat(initializeStatus(datasourceServlet, "/mcp/datasource", second.getToken())).isEqualTo(200);
        assertThat(initializeStatus(metricServlet, "/mcp/metric", second.getToken())).isEqualTo(200);
    }

    private String initialize(String token) throws Exception {
        MockHttpServletResponse initialized = post(token, initializeRequest(), null);
        assertThat(initialized.getStatus()).isEqualTo(200);
        String sessionId = initialized.getHeader("mcp-session-id");
        assertThat(sessionId).isNotBlank();
        assertThat(initialized.getContentAsString()).contains("datay-harness-mcp-server");

        MockHttpServletResponse notifications = post(
            token,
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            sessionId
        );
        assertThat(notifications.getStatus()).isIn(200, 202);
        return sessionId;
    }

    private int initializeStatus(String token) throws Exception {
        return post(token, initializeRequest(), null).getStatus();
    }

    private int initializeStatus(HttpServletStreamableServerTransportProvider provider, String endpoint, String token) throws Exception {
        return post(provider, endpoint, token, initializeRequest(), null).getStatus();
    }

    private MockHttpServletResponse callCatalog(String sessionId, String token) throws Exception {
        MockHttpServletResponse response = post(
            token,
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"datay_harness_catalog\",\"arguments\":{}}}",
            sessionId
        );
        if (response.getStatus() == 200) {
            assertThat(response.getContentAsString()).contains("totalTools");
        }
        return response;
    }

    private static String initializeRequest() {
        return (
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
            "\"protocolVersion\":\"" +
            ProtocolVersions.MCP_2025_06_18 +
            "\",\"capabilities\":{},\"clientInfo\":{\"name\":\"multi-token-test\",\"version\":\"1.0.0\"}}}"
        );
    }

    private MockHttpServletResponse post(String token, String body, String sessionId) throws Exception {
        return post(servlet, DatayHarnessMcpServerConfig.MCP_ENDPOINT, token, body, sessionId);
    }

    private MockHttpServletResponse post(
        HttpServletStreamableServerTransportProvider provider,
        String endpoint,
        String token,
        String body,
        String sessionId
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", endpoint);
        request.setContentType("application/json");
        request.addHeader("Accept", "application/json, text/event-stream");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setAsyncSupported(true);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        if (sessionId != null) {
            request.addHeader("mcp-session-id", sessionId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        provider.service(request, response);
        return response;
    }
}
