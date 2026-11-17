package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.AuthoritiesConstants;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.DataApiService;
import com.data.datafusion.service.TenantService;
import com.data.datafusion.service.UserService;
import com.data.datafusion.service.dto.AdminUserDTO;
import com.data.datafusion.service.dto.DataApiDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证一个用户创建多个访问令牌，并分别通过令牌调用数据服务的完整链路。
 */
@AutoConfigureMockMvc
@IntegrationTest
class MultiAccessTokenDataApiIT {

    private static final String LOGIN = "multi-token-api-test";

    private static final String API_CODE = "multi_token_test_api";

    private static final long SYSTEM_TENANT_ID = 0L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private DataApiService dataApiService;

    private HttpServer upstream;

    private int upstreamPort;

    @BeforeEach
    void setUp() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/hello", exchange -> {
            byte[] body = "{\"data\":{\"message\":\"ok\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        upstream.start();
        upstreamPort = upstream.getAddress().getPort();

        var user = userRepository.findOneByLogin(LOGIN).orElseGet(() -> {
            AdminUserDTO dto = new AdminUserDTO();
            dto.setLogin(LOGIN);
            dto.setFirstName("Multi");
            dto.setLastName("Token");
            dto.setEmail("multi-token-api-test@example.com");
            dto.setLangKey("zh-cn");
            dto.setAuthorities(Set.of(AuthoritiesConstants.USER));
            return userService.createUser(dto);
        });
        tenantService.addUserToTenant(SYSTEM_TENANT_ID, user.getId());
        tenantRepository.setDefaultTenantForUser(user.getId(), SYSTEM_TENANT_ID);

        TenantContext.setTenantId(SYSTEM_TENANT_ID);
        try {
            dataApiService.findByCode(API_CODE).ifPresent(api -> dataApiService.delete(api.getId()));
            DataApiDTO dataApi = new DataApiDTO();
            dataApi.setName("多令牌测试数据服务");
            dataApi.setCode(API_CODE);
            dataApi.setSourceType(DataApi.SOURCE_TYPE_API);
            dataApi.setStatus(DataApi.STATUS_ENABLED);
            dataApi.setTenantId(String.valueOf(SYSTEM_TENANT_ID));
            dataApi.setApiConfig(
                "{\"url\":\"http://127.0.0.1:" + upstreamPort + "/hello\",\"method\":\"GET\",\"jsonPath\":\"$.data\"}"
            );
            dataApiService.save(dataApi);
            assertThat(dataApiService.findByCode(API_CODE)).isPresent();
            assertThat(tenantRepository.findDefaultTenantByUserId(user.getId())).isPresent();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.setTenantId(SYSTEM_TENANT_ID);
        try {
            dataApiService.findByCode(API_CODE).ifPresent(api -> dataApiService.delete(api.getId()));
        } finally {
            TenantContext.clear();
        }
        userRepository.findOneByLogin(LOGIN).ifPresent(user -> tenantService.removeUserFromTenant(SYSTEM_TENANT_ID, user.getId()));
        userService.deleteUser(LOGIN);
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    @WithMockUser(LOGIN)
    void shouldUseTwoTokensToCallSameApiAndRevokeOneIndependently() throws Exception {
        Token first = createToken("Claude Desktop");
        Token second = createToken("Cursor");
        assertThat(first.token()).isNotEqualTo(second.token());

        invokeWithToken(first.token()).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(
            jsonPath("$.data.message").value("ok")
        );
        invokeWithToken(second.token()).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(
            jsonPath("$.data.message").value("ok")
        );

        mockMvc
            .perform(
                patch("/api/account/access-tokens/" + first.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"DISABLED\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISABLED"));

        invokeWithToken(first.token()).andExpect(status().isUnauthorized());
        invokeWithToken(second.token()).andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("ok"));
    }

    private Token createToken(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"集成测试\"}";
        String response = mockMvc
            .perform(post("/api/account/access-tokens").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return new Token(json.get("token").asText(), json.get("accessToken").get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions invokeWithToken(String token) throws Exception {
        return mockMvc.perform(get("/open-api/data/" + API_CODE).header("Authorization", "Bearer " + token));
    }

    private record Token(String token, String id) {}
}
