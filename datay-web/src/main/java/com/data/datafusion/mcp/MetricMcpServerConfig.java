package com.data.datafusion.mcp;

import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolRegistry;
import com.data.datafusion.ai.tool.MetricCatalogTool;
import com.data.datafusion.ai.tool.MetricDimensionValueTool;
import com.data.datafusion.ai.tool.MetricMetaTool;
import com.data.datafusion.ai.tool.MetricQueryDataTool;
import com.data.datafusion.ai.tool.MetricQueryGuideTool;
import com.data.datafusion.ai.tool.MetricRagSearchTool;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指标智能问数 MCP 服务端，独立挂载在 {@code /mcp/metric}。
 *
 * <p>与 {@code /mcp/datasource} 一样使用 {@code Authorization: Bearer <mcp-token>} 鉴权，可用
 * {@code X-Tenant-Id} / {@code X-Tenant-Code} 指定租户。它只暴露只读的指标语义层工具，
 * 由 MCP 客户端（Claude Desktop、Cursor 等）的模型完成「指标 / 维度 / 业务限定 / 时间范围」的拆解与推理，
 * 服务端无需配置 LLM Key。
 *
 * <p>工具实现直接复用 {@link AiToolRegistry} 中已注册的 {@link AiTool}，
 * 因此指标元数据、RAG 检索与查询 SQL 的生成逻辑与内置智能问数助手完全一致。
 */
@Configuration
public class MetricMcpServerConfig {

    private static final String MCP_ENDPOINT = "/mcp/metric";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private final AiToolRegistry toolRegistry;
    private final MetricMcpToolExecutor toolExecutor;
    private final McpTokenService mcpTokenService;

    public MetricMcpServerConfig(
        AiToolRegistry toolRegistry,
        MetricMcpToolExecutor toolExecutor,
        McpTokenService mcpTokenService
    ) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.mcpTokenService = mcpTokenService;
    }

    @Bean("metricMcpTransportProvider")
    public HttpServletStreamableServerTransportProvider metricMcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(MCP_ENDPOINT)
            .contextExtractor(metricContextExtractor())
            .securityValidator(metricSecurityValidator())
            .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> metricMcpServletRegistration(
        @Qualifier("metricMcpTransportProvider") HttpServletStreamableServerTransportProvider transportProvider
    ) {
        return new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT);
    }

    @Bean("metricMcpSyncServer")
    public McpSyncServer metricMcpSyncServer(
        @Qualifier("metricMcpTransportProvider") HttpServletStreamableServerTransportProvider transportProvider
    ) {
        return McpServer.sync(transportProvider)
            .serverInfo("datay-metric-mcp-server", "1.0.0")
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .toolCall(tool(MetricQueryGuideTool.NAME), (exchange, request) ->
                toolExecutor.invoke(MetricQueryGuideTool.NAME, exchange.transportContext(), request.arguments())
            )
            .toolCall(tool(MetricRagSearchTool.NAME), (exchange, request) ->
                toolExecutor.invoke(MetricRagSearchTool.NAME, exchange.transportContext(), request.arguments())
            )
            .toolCall(tool(MetricCatalogTool.NAME), (exchange, request) ->
                toolExecutor.invoke(MetricCatalogTool.NAME, exchange.transportContext(), request.arguments())
            )
            .toolCall(tool(MetricMetaTool.NAME), (exchange, request) ->
                toolExecutor.invoke(MetricMetaTool.NAME, exchange.transportContext(), request.arguments())
            )
            .toolCall(tool(MetricDimensionValueTool.NAME), (exchange, request) ->
                toolExecutor.invoke(MetricDimensionValueTool.NAME, exchange.transportContext(), request.arguments())
            )
            .toolCall(tool(MetricQueryDataTool.NAME), (exchange, request) ->
                toolExecutor.invoke(MetricQueryDataTool.NAME, exchange.transportContext(), request.arguments())
            )
            .build();
    }

    private Tool tool(String name) {
        AiTool aiTool = toolRegistry
            .find(name)
            .orElseThrow(() -> new IllegalStateException("指标 MCP 工具未注册: " + name));
        return Tool.builder(aiTool.name(), aiTool.parametersSchema()).description(aiTool.description()).build();
    }

    /**
     * 把访问令牌与可选租户头写入 {@link McpTransportContext}，供执行器解析。
     */
    private McpTransportContextExtractor<HttpServletRequest> metricContextExtractor() {
        return request -> {
            Map<String, Object> values = new LinkedHashMap<>();
            putIfPresent(values, AUTHORIZATION_HEADER, stripBearerPrefix(request.getHeader(AUTHORIZATION_HEADER)));
            putIfPresent(values, TENANT_ID_HEADER, request.getHeader(TENANT_ID_HEADER));
            putIfPresent(values, TENANT_CODE_HEADER, request.getHeader(TENANT_CODE_HEADER));
            return McpTransportContext.create(values);
        };
    }

    private static void putIfPresent(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value.trim());
        }
    }

    private ServerTransportSecurityValidator metricSecurityValidator() {
        return headers -> {
            String authorization = getHeaderIgnoreCase(headers, AUTHORIZATION_HEADER);
            if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
                throw new ServerTransportSecurityException(HttpServletResponse.SC_UNAUTHORIZED, "Missing MCP Bearer token");
            }
            String token = stripBearerPrefix(authorization);
            if (!mcpTokenService.authenticate(token)) {
                throw new ServerTransportSecurityException(HttpServletResponse.SC_UNAUTHORIZED, "Invalid MCP token");
            }
        };
    }

    private static String stripBearerPrefix(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.startsWith(BEARER_PREFIX)) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    private static String getHeaderIgnoreCase(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }
}
