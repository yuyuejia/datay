package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.mcp.McpTokenService;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.service.dto.DataApiDTO;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 已注册 HTTP API（含前置处理获取 Token）数据服务的单元测试。
 */
class DataApiExecuteServiceApiTest {

    private McpTokenService mcpTokenService;

    private TenantRepository tenantRepository;

    private DataApiService dataApiService;

    private DataApiExecuteService service;

    private HttpServer server;

    private int port;

    @BeforeEach
    void setUp() throws IOException {
        mcpTokenService = mock(McpTokenService.class);
        tenantRepository = mock(TenantRepository.class);
        dataApiService = mock(DataApiService.class);
        service = new DataApiExecuteService(mcpTokenService, tenantRepository, dataApiService, mock(DataSourceService.class));

        User user = new User();
        user.setId(1L);
        Tenant tenant = new Tenant();
        tenant.setId(9L);
        when(mcpTokenService.authenticateUser("mcp_token")).thenReturn(Optional.of(user));
        when(tenantRepository.findDefaultTenantByUserId(1L)).thenReturn(Optional.of(tenant));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> writeJson(exchange, 200, "{\"code\":0,\"data\":{\"accessToken\":\"TOKEN123\"}}"));
        server.createContext("/orders", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String query = exchange.getRequestURI().getQuery();
            JSONObject body = new JSONObject();
            body.put("auth", auth);
            body.put("query", query);
            writeJson(exchange, 200, body.toJSONString());
        });
        server.createContext("/nested", exchange ->
            writeJson(exchange, 200, "{\"code\":0,\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}")
        );
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldFetchTokenFromPreProcessAndReferenceInHeader() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" +
            port +
            "/orders\",\"method\":\"GET\",\"headers\":{\"Authorization\":\"Bearer ${token}\"},\"preProcess\":{\"enabled\":true,\"url\":\"http://127.0.0.1:" +
            port +
            "/token\",\"method\":\"POST\",\"tokenPath\":\"$.data.accessToken\"}}"
        )));

        Map<String, Object> result = service.invokeApi("mcp_token", "orders", Map.of());

        assertThat(result.get("code")).isEqualTo(0);
        JSONObject data = (JSONObject) result.get("data");
        assertThat(data.getString("auth")).isEqualTo("Bearer TOKEN123");
    }

    @Test
    void shouldSubstituteCallerParametersIntoRequest() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" + port + "/orders?status=${status}\",\"method\":\"GET\"}"
        )));

        Map<String, Object> result = service.invokeApi("mcp_token", "orders", Map.of("status", "PAID"));

        JSONObject data = (JSONObject) result.get("data");
        assertThat(data.getString("query")).isEqualTo("status=PAID");
    }

    @Test
    void shouldExtractPartialDataByJsonPath() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" + port + "/nested\",\"method\":\"GET\",\"jsonPath\":\"$.data.items\"}"
        )));

        Map<String, Object> result = service.invokeApi("mcp_token", "orders", Map.of());

        assertThat(result.get("total")).isEqualTo(2L);
        assertThat(result.get("columns")).isEqualTo(List.of("id"));
        JSONArray data = (JSONArray) result.get("data");
        assertThat(data.size()).isEqualTo(2);
    }

    @Test
    void shouldFailWhenRequiredParameterMissing() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" + port + "/orders?status=${status}\",\"method\":\"GET\"}"
        )));

        assertThatThrownBy(() -> service.invokeApi("mcp_token", "orders", Map.of()))
            .isInstanceOf(DataApiAccessException.class)
            .hasMessageContaining("缺少参数");
    }

    private static DataApiDTO api(String apiConfig) {
        DataApiDTO dto = new DataApiDTO();
        dto.setId(1L);
        dto.setCode("orders");
        dto.setStatus(DataApi.STATUS_ENABLED);
        dto.setSourceType(DataApi.SOURCE_TYPE_API);
        dto.setApiConfig(apiConfig);
        return dto;
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
