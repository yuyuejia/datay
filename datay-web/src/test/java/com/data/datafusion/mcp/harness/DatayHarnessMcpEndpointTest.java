package com.data.datafusion.mcp.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.mcp.McpTokenService;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@code /mcp/datay} 端点的端到端测试：直接驱动真实的 MCP servlet，
 * 走一遍「initialize → tools/list → tools/call」的协议流程，
 * 确认鉴权、工具清单与调用分发都是通的，而不是只验证 Bean 装配。
 */
class DatayHarnessMcpEndpointTest {

    private static final String TOKEN = "mcp_1_e2etoken";

    private final DatayHarnessTool probeTool = DatayHarnessTool.read(
        "probe_echo",
        "回显入参的探针工具",
        DatayHarnessSchema.object(DatayHarnessSchema.properties("message", DatayHarnessSchema.string("要回显的内容")), "message"),
        (args, context) -> Map.of("echo", String.valueOf(args.get("message")))
    );

    @Test
    void shouldServeInitializeAndToolsListOverRealServlet() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DatayHarnessMcpServerConfig.class)
            .withBean(McpTokenService.class, () -> {
                McpTokenService tokenService = mock(McpTokenService.class);
                when(tokenService.authenticate(TOKEN)).thenReturn(true);
                return tokenService;
            })
            .withBean(DatayHarnessToolRegistry.class, () -> new DatayHarnessToolRegistry(List.of(probeTool)))
            .withBean(DatayHarnessExecutor.class, () -> mock(DatayHarnessExecutor.class));

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            HttpServletStreamableServerTransportProvider servlet = context.getBean(
                "datayHarnessMcpTransportProvider",
                HttpServletStreamableServerTransportProvider.class
            );

            MockHttpServletResponse unauthorized = post(
                servlet,
                null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null
            );
            assertThat(unauthorized.getStatus()).isEqualTo(401);

            MockHttpServletResponse initialized = post(servlet, TOKEN, initializeRequest(), null);
            assertThat(initialized.getStatus()).isEqualTo(200);
            String sessionId = initialized.getHeader("mcp-session-id");
            assertThat(sessionId).isNotBlank();
            assertThat(initialized.getContentAsString()).contains("datay-harness-mcp-server");

            MockHttpServletResponse notifications = post(
                servlet,
                TOKEN,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                sessionId
            );
            assertThat(notifications.getStatus()).isIn(200, 202);

            MockHttpServletResponse toolsList = post(servlet, TOKEN, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", sessionId);
            assertThat(toolsList.getStatus()).isEqualTo(200);
            String body = toolsList.getContentAsString();
            assertThat(body).contains("probe_echo").contains("回显入参的探针工具").contains("readOnlyHint");
        });
    }

    private static String initializeRequest() {
        return (
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
            "\"protocolVersion\":\"" +
            ProtocolVersions.MCP_2025_06_18 +
            "\",\"capabilities\":{},\"clientInfo\":{\"name\":\"datay-test\",\"version\":\"1.0.0\"}}}"
        );
    }

    private static MockHttpServletResponse post(
        HttpServletStreamableServerTransportProvider servlet,
        String token,
        String body,
        String sessionId
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", DatayHarnessMcpServerConfig.MCP_ENDPOINT);
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
        servlet.service(request, response);
        return response;
    }
}
