package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.DataApiCallLog;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.mcp.McpTokenService;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
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

    private AnalysisDatasetService analysisDatasetService;

    private DataApiCallLogService callLogService;

    private DataApiExecuteService service;

    private HttpServer server;

    private int port;

    @BeforeEach
    void setUp() throws IOException {
        mcpTokenService = mock(McpTokenService.class);
        tenantRepository = mock(TenantRepository.class);
        dataApiService = mock(DataApiService.class);
        analysisDatasetService = mock(AnalysisDatasetService.class);
        callLogService = mock(DataApiCallLogService.class);
        service = new DataApiExecuteService(
            mcpTokenService,
            tenantRepository,
            dataApiService,
            mock(DataSourceService.class),
            analysisDatasetService,
            mock(UserRepository.class),
            callLogService,
            new DataApiScriptPreProcessService(3000)
        );

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
        server.createContext("/fail", exchange -> writeJson(exchange, 500, "{\"error\":\"boom\"}"));
        String largeBody = "x".repeat(600 * 1024);
        server.createContext("/large", exchange -> writeJson(exchange, 200, largeBody));
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
    void shouldRunScriptPreProcessAndReferenceOutput() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" +
            port +
            "/orders?ts=${ts}\",\"method\":\"GET\",\"headers\":{\"Authorization\":\"${token}\"}," +
            "\"preProcess\":{\"enabled\":true,\"type\":\"script\"," +
            "\"script\":\"return { token: 'Bearer ' + params.appId, ts: 'T1' };\"}}"
        )));

        Map<String, Object> result = service.invokeApi("mcp_token", "orders", Map.of("appId", "A1"));

        assertThat(result.get("code")).isEqualTo(0);
        JSONObject data = (JSONObject) result.get("data");
        assertThat(data.getString("auth")).isEqualTo("Bearer A1");
        assertThat(data.getString("query")).isEqualTo("ts=T1");
    }

    @Test
    void shouldLetScriptOutputOverrideCallerParameter() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" +
            port +
            "/orders?status=${status}\",\"method\":\"GET\"," +
            "\"preProcess\":{\"enabled\":true,\"type\":\"script\",\"script\":\"return { status: 'SCRIPT' };\"}}"
        )));

        Map<String, Object> result = service.invokeApi("mcp_token", "orders", Map.of("status", "PAID"));

        JSONObject data = (JSONObject) result.get("data");
        assertThat(data.getString("query")).isEqualTo("status=SCRIPT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnScriptOutputWhenDebugging() {
        DataApiDTO dto = api(
            "{\"url\":\"http://127.0.0.1:" +
            port +
            "/orders\",\"method\":\"GET\",\"preProcess\":{\"enabled\":true,\"type\":\"script\"," +
            "\"script\":\"return { token: 'Bearer ' + params.appId, ts: 'T1' };\"}}"
        );

        Map<String, Object> result = service.debugInvoke(dto, Map.of("appId", "A1"));

        assertThat(result.get("code")).isEqualTo(0);
        Map<String, Object> preProcess = (Map<String, Object>) result.get("preProcess");
        assertThat(preProcess.get("type")).isEqualTo("script");
        assertThat((Map<String, Object>) preProcess.get("output"))
            .containsEntry("token", "Bearer A1")
            .containsEntry("ts", "T1");
        assertThat(preProcess.get("durationMs")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepScriptOutputWhenDebugUpstreamFails() {
        DataApiDTO dto = api(
            "{\"url\":\"http://127.0.0.1:" +
            port +
            "/fail\",\"method\":\"GET\",\"preProcess\":{\"enabled\":true,\"type\":\"script\"," +
            "\"script\":\"return { token: 'T' };\"}}"
        );

        Map<String, Object> result = service.debugInvoke(dto, Map.of());

        assertThat(result.get("code")).isEqualTo(502);
        Map<String, Object> preProcess = (Map<String, Object>) result.get("preProcess");
        assertThat((Map<String, Object>) preProcess.get("output")).containsEntry("token", "T");
    }

    @Test
    void shouldReturnScriptErrorWhenDebugging() {
        DataApiDTO dto = api(
            "{\"url\":\"http://127.0.0.1:" +
            port +
            "/orders\",\"method\":\"GET\",\"preProcess\":{\"enabled\":true,\"type\":\"script\",\"script\":\"return 1;\"}}"
        );

        Map<String, Object> result = service.debugInvoke(dto, Map.of());

        assertThat(result.get("code")).isEqualTo(500);
        assertThat(result.get("message")).asString().contains("必须返回一个对象");
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

    @Test
    void shouldExecuteDatasetApiWithPagination() throws Exception {
        DataApiDTO dto = datasetApi();
        AnalysisDatasetDTO dataset = new AnalysisDatasetDTO();
        dataset.setId("ds-1");
        dataset.setName("订单明细");
        dataset.setType("SQL");
        dataset.setStatus("ENABLED");
        Map<String, Object> params = Map.of("pageNum", "2", "pageSize", "10", "status", "PAID");
        when(dataApiService.findByCode("orders_dataset")).thenReturn(Optional.of(dto));
        when(analysisDatasetService.findOne("ds-1")).thenReturn(Optional.of(dataset));
        when(analysisDatasetService.executeForApi("ds-1", params, 2, 10)).thenReturn(
            Map.of("columns", List.of("id"), "rows", List.of(Map.of("id", 1)))
        );

        Map<String, Object> result = service.invokeApi("mcp_token", "orders_dataset", params);

        assertThat(result.get("code")).isEqualTo(0);
        assertThat(result.get("data")).isEqualTo(List.of(Map.of("id", 1)));
        assertThat(result.get("pageNum")).isEqualTo(2);
        assertThat(result.get("pageSize")).isEqualTo(10);
    }

    @Test
    void shouldTranslateDatasetParameterErrorsToClientError() throws Exception {
        AnalysisDatasetDTO dataset = new AnalysisDatasetDTO();
        dataset.setId("ds-1");
        dataset.setName("订单明细");
        dataset.setType("SQL");
        dataset.setStatus("ENABLED");
        when(dataApiService.findByCode("orders_dataset")).thenReturn(Optional.of(datasetApi()));
        when(analysisDatasetService.findOne("ds-1")).thenReturn(Optional.of(dataset));
        when(analysisDatasetService.executeForApi("ds-1", Map.of(), 1, 1000)).thenThrow(
            new IllegalArgumentException("缺少参数：status")
        );

        assertThatThrownBy(() -> service.invokeApi("mcp_token", "orders_dataset", Map.of()))
            .isInstanceOf(DataApiAccessException.class)
            .hasMessageContaining("缺少参数");
    }

    @Test
    void shouldRecordSuccessfulCallLog() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" + port + "/nested\",\"method\":\"GET\",\"jsonPath\":\"$.data.items\"}"
        )));

        service.invokeApi("mcp_token", "orders", Map.of());

        verify(callLogService).record(
            any(),
            eq("orders"),
            any(),
            any(),
            eq(DataApiCallLog.STATUS_SUCCESS),
            eq(200),
            isNull(),
            anyLong(),
            eq(2L)
        );
    }

    @Test
    void shouldRecordFailedCallLog() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(api(
            "{\"url\":\"http://127.0.0.1:" + port + "/orders?status=${status}\",\"method\":\"GET\"}"
        )));

        assertThatThrownBy(() -> service.invokeApi("mcp_token", "orders", Map.of()))
            .isInstanceOf(DataApiAccessException.class)
            .hasMessageContaining("缺少参数");

        verify(callLogService).record(
            any(),
            eq("orders"),
            any(),
            any(),
            eq(DataApiCallLog.STATUS_FAILED),
            eq(400),
            contains("缺少参数"),
            anyLong(),
            isNull()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDebugApiAndReturnUpstreamWithoutCallLog() {
        DataApiDTO dto = api(
            "{\"url\":\"http://127.0.0.1:" + port + "/nested\",\"method\":\"GET\",\"jsonPath\":\"$.data.items\"}"
        );

        Map<String, Object> result = service.debugInvoke(dto, Map.of());

        assertThat(result.get("code")).isEqualTo(0);
        assertThat(result.get("total")).isEqualTo(2L);
        assertThat(result.get("durationMs")).isNotNull();
        Map<String, Object> upstream = (Map<String, Object>) result.get("upstream");
        assertThat(upstream.get("status")).isEqualTo(200);
        assertThat(upstream.get("body")).asString().contains("\"items\"");
        verifyNoInteractions(callLogService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDebugApiReturnUpstreamWhenJsonPathInvalid() {
        DataApiDTO dto = api(
            "{\"url\":\"http://127.0.0.1:" + port + "/nested\",\"method\":\"GET\",\"jsonPath\":\"$.data.missing\"}"
        );

        Map<String, Object> result = service.debugInvoke(dto, Map.of());

        assertThat(result.get("code")).isEqualTo(502);
        assertThat(result.get("message")).asString().contains("未匹配");
        Map<String, Object> upstream = (Map<String, Object>) result.get("upstream");
        assertThat(upstream.get("status")).isEqualTo(200);
        assertThat(upstream.get("body")).asString().contains("\"items\"");
        verifyNoInteractions(callLogService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDebugApiReturnUpstreamWhenUpstreamFails() {
        DataApiDTO dto = api("{\"url\":\"http://127.0.0.1:" + port + "/fail\",\"method\":\"GET\"}");

        Map<String, Object> result = service.debugInvoke(dto, Map.of());

        assertThat(result.get("code")).isEqualTo(502);
        assertThat(result.get("message")).asString().contains("HTTP 500");
        Map<String, Object> upstream = (Map<String, Object>) result.get("upstream");
        assertThat(upstream.get("status")).isEqualTo(500);
        assertThat(upstream.get("body")).asString().contains("boom");
        verifyNoInteractions(callLogService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCompleteUpstreamBodyWhenDebugging() {
        DataApiDTO dto = api("{\"url\":\"http://127.0.0.1:" + port + "/large\",\"method\":\"GET\"}");

        Map<String, Object> result = service.debugInvoke(dto, Map.of());

        assertThat(result.get("code")).isEqualTo(0);
        Map<String, Object> upstream = (Map<String, Object>) result.get("upstream");
        assertThat(upstream.get("status")).isEqualTo(200);
        assertThat(upstream.get("body")).asString().hasSize(600 * 1024);
        assertThat(upstream).doesNotContainKey("truncated");
        verifyNoInteractions(callLogService);
    }

    @Test
    void shouldDebugWithoutNameAndCode() {
        DataApiDTO dto = api("{\"url\":\"http://127.0.0.1:" + port + "/nested\",\"method\":\"GET\"}");
        dto.setName(null);
        dto.setCode(null);

        Map<String, Object> result = service.debugInvoke(dto, Map.of());

        assertThat(result.get("code")).isEqualTo(0);
    }

    private static DataApiDTO datasetApi() {
        DataApiDTO dto = new DataApiDTO();
        dto.setId("10");
        dto.setCode("orders_dataset");
        dto.setStatus(DataApi.STATUS_ENABLED);
        dto.setSourceType(DataApi.SOURCE_TYPE_DATASET);
        dto.setDatasetId("ds-1");
        return dto;
    }

    private static DataApiDTO api(String apiConfig) {
        DataApiDTO dto = new DataApiDTO();
        dto.setId("1");
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
