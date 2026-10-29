package com.data.datafusion.mcp.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataApi;
import com.data.datafusion.service.DataApiAccessException;
import com.data.datafusion.service.DataApiExecuteService;
import com.data.datafusion.service.DataApiService;
import com.data.datafusion.service.dto.DataApiDTO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 数据服务工具族的行为测试。
 *
 * <p>重点覆盖三件事：出参不泄露 SQL / API 配置中的敏感信息；只读与写操作按来源类型严格分流；
 * 调用最终走 {@link DataApiExecuteService#invokeApiInCurrentTenant}，与 REST 入口共用同一套执行逻辑。
 */
class DataApiHarnessToolsTest {

    private DataApiService dataApiService;

    private DataApiExecuteService dataApiExecuteService;

    private DataApiHarnessTools tools;

    @BeforeEach
    void setUp() {
        dataApiService = mock(DataApiService.class);
        dataApiExecuteService = mock(DataApiExecuteService.class);
        tools = new DataApiHarnessTools(dataApiService, dataApiExecuteService);
    }

    @Test
    void shouldExposeCatalogToolsWithExpectedSafetyFlags() {
        assertThat(tools.dataapiListTool().name()).isEqualTo("dataapi_list");
        assertThat(tools.dataapiDescribeTool().name()).isEqualTo("dataapi_describe");
        assertThat(tools.dataapiQueryTool().name()).isEqualTo("dataapi_query");
        assertThat(tools.dataapiCallTool().name()).isEqualTo("dataapi_call");

        assertThat(tools.dataapiListTool().mutating()).isFalse();
        assertThat(tools.dataapiDescribeTool().mutating()).isFalse();
        // 表 / SQL 来源是取数，关闭写开关时也应可用
        assertThat(tools.dataapiQueryTool().mutating()).isFalse();
        // API 来源会代理上游请求，可能产生外部副作用
        assertThat(tools.dataapiCallTool().mutating()).isTrue();

        List.of(
            tools.dataapiListTool(),
            tools.dataapiDescribeTool(),
            tools.dataapiQueryTool(),
            tools.dataapiCallTool()
        ).forEach(tool -> {
            assertThat(tool.group()).isEqualTo(DataApiHarnessTools.GROUP);
            assertThat(tool.parametersSchema()).containsEntry("type", "object");
        });
    }

    @Test
    void shouldListServicesWithoutDefinitionsOrCredentials() throws Exception {
        DataApiDTO sqlApi = dataApi("1", "sales_by_day", DataApi.SOURCE_TYPE_SQL);
        sqlApi.setSqlText("SELECT * FROM t WHERE day = ${day}");
        when(dataApiService.findAll(PageRequest.of(0, 20), null)).thenReturn(new PageImpl<>(List.of(sqlApi)));

        Object result = tools.dataapiListTool().execute(Map.of(), context());

        Map<String, Object> page = asMap(result);
        assertThat(page).containsEntry("totalElements", 1L);
        Map<String, Object> item = asMap(asList(page.get("items")).get(0));
        assertThat(item).containsEntry("code", "sales_by_day").containsEntry("sourceType", "SQL");
        assertThat(item).doesNotContainKeys("sqlText", "apiConfig");
    }

    @Test
    void shouldDescribeSqlServiceWithPlaceholderParameters() throws Exception {
        DataApiDTO dto = dataApi("1", "sales_by_day", DataApi.SOURCE_TYPE_SQL);
        dto.setSqlText("SELECT * FROM t WHERE day = ${day} AND city = ${city}");
        when(dataApiService.findByCode("sales_by_day")).thenReturn(Optional.of(dto));

        Map<String, Object> result = asMap(tools.dataapiDescribeTool().execute(Map.of("code", "sales_by_day"), context()));

        assertThat(asList(result.get("parameters"))).containsExactly("day", "city");
        assertThat(result.get("sqlText")).isEqualTo(dto.getSqlText());
    }

    @Test
    void shouldDescribeApiServiceWithoutExposingUrlHeadersOrToken() throws Exception {
        DataApiDTO dto = dataApi("2", "weather", DataApi.SOURCE_TYPE_API);
        dto.setApiConfig(
            "{\"url\":\"https://up.example.com/data?day=${day}\",\"method\":\"POST\"," +
            "\"headers\":{\"X-Tenant\":\"${tenant}\",\"Authorization\":\"Bearer ${token}\"}," +
            "\"body\":\"{\\\"city\\\":\\\"${city}\\\"}\",\"jsonPath\":\"$.result.rows\"," +
            "\"preProcess\":{\"enabled\":true,\"url\":\"https://up.example.com/token\"}}"
        );
        when(dataApiService.findByCode("weather")).thenReturn(Optional.of(dto));

        Map<String, Object> result = asMap(tools.dataapiDescribeTool().execute(Map.of("code", "weather"), context()));

        assertThat(asList(result.get("parameters"))).containsExactly("day", "tenant", "city");
        assertThat(result).containsEntry("httpMethod", "POST").containsEntry("jsonPath", "$.result.rows");
        assertThat(result).containsEntry("preProcessEnabled", true).containsEntry("hasRequestBody", true);
        assertThat(result).doesNotContainKeys("url", "headers", "apiConfig", "sqlText");
    }

    @Test
    void shouldDescribeTableServiceWithParameterHint() throws Exception {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(dataApi("3", "orders", DataApi.SOURCE_TYPE_TABLE)));

        Map<String, Object> result = asMap(tools.dataapiDescribeTool().execute(Map.of("code", "orders"), context()));

        assertThat(asList(result.get("parameters"))).isEmpty();
        assertThat(result).containsKey("parameterHint");
        assertThat(result).doesNotContainKeys("sqlText", "apiConfig");
    }

    @Test
    void shouldRejectUnknownService() {        when(dataApiService.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.dataapiDescribeTool().execute(Map.of("code", "missing"), context()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据服务不存在");
    }

    @Test
    void shouldQueryTableServiceAndMergeFlattenedParams() throws Exception {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(dataApi("3", "orders", DataApi.SOURCE_TYPE_TABLE)));
        when(dataApiExecuteService.invokeApiInCurrentTenant(eq("orders"), any())).thenReturn(Map.of("code", 0, "total", 2));

        Object result = tools
            .dataapiQueryTool()
            .execute(Map.of("code", "orders", "params", Map.of("pageSize", 10), "city", "北京"), context());

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(dataApiExecuteService).invokeApiInCurrentTenant(eq("orders"), params.capture());
        assertThat(params.getValue()).containsEntry("pageSize", 10).containsEntry("city", "北京");
        assertThat(asMap(result)).containsEntry("total", 2);
    }

    @Test
    void shouldRejectApiSourceFromQueryTool() {
        when(dataApiService.findByCode("weather")).thenReturn(Optional.of(dataApi("2", "weather", DataApi.SOURCE_TYPE_API)));

        assertThatThrownBy(() -> tools.dataapiQueryTool().execute(Map.of("code", "weather"), context()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dataapi_call");
        verifyNoInteractions(dataApiExecuteService);
    }

    @Test
    void shouldRejectNonApiSourceFromCallTool() {
        when(dataApiService.findByCode("sales")).thenReturn(Optional.of(dataApi("1", "sales", DataApi.SOURCE_TYPE_SQL)));

        assertThatThrownBy(() -> tools.dataapiCallTool().execute(Map.of("code", "sales"), context()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dataapi_query");
        verifyNoInteractions(dataApiExecuteService);
    }

    @Test
    void shouldCallApiSourceAndTranslateClientErrors() throws Exception {
        when(dataApiService.findByCode("weather")).thenReturn(Optional.of(dataApi("2", "weather", DataApi.SOURCE_TYPE_API)));
        when(dataApiExecuteService.invokeApiInCurrentTenant(eq("weather"), any()))
            .thenReturn(Map.of("code", 0, "total", 1))
            .thenThrow(new DataApiAccessException(400, "缺少参数：day"));

        Object result = tools.dataapiCallTool().execute(Map.of("code", "weather", "day", "2026-10-01"), context());
        assertThat(asMap(result)).containsEntry("total", 1);

        assertThatThrownBy(() -> tools.dataapiCallTool().execute(Map.of("code", "weather"), context()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("缺少参数");
    }

    @Test
    void shouldTranslateServerErrorsToIllegalState() {
        when(dataApiService.findByCode("orders")).thenReturn(Optional.of(dataApi("3", "orders", DataApi.SOURCE_TYPE_TABLE)));
        when(dataApiExecuteService.invokeApiInCurrentTenant(eq("orders"), any()))
            .thenThrow(new DataApiAccessException(500, "服务执行失败：连接超时"));

        assertThatThrownBy(() -> tools.dataapiQueryTool().execute(Map.of("code", "orders"), context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("连接超时");
    }

    private static DataApiDTO dataApi(String id, String code, String sourceType) {
        DataApiDTO dto = new DataApiDTO();
        dto.setId(id);
        dto.setCode(code);
        dto.setName(code);
        dto.setSourceType(sourceType);
        dto.setStatus(DataApi.STATUS_ENABLED);
        dto.setDataSourceId("ds-1");
        return dto;
    }

    private static DatayHarnessContext context() {
        return new DatayHarnessContext(null, 1L, "t1");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}
