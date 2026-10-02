package com.data.datafusion.mcp.harness;

import com.data.datafusion.mcp.McpTokenService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
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
 * DataY 平台管理 Harness 的 MCP 服务端，独立挂载在 {@code /mcp/datay}。
 *
 * <p>与 {@code /mcp/datasource}、{@code /mcp/metric} 保持一致的接入方式：
 * 使用 {@code Authorization: Bearer <mcp-token>} 鉴权，可用 {@code X-Tenant-Id} / {@code X-Tenant-Code} 指定租户。
 *
 * <p>区别在于能力定位：既有端点分别面向「数据源取数」与「指标问数」，本端点面向
 * <strong>平台对象管理</strong>，把数据源、ETL 任务、数据模型等对象的增删改查与运行类动作
 * 暴露给 MCP 客户端（Claude Desktop、Cursor、DSH 等）。
 *
 * <p>工具清单完全由 {@link DatayHarnessToolRegistry} 驱动，新增工具只需注册一个 Bean，
 * 本类无需改动。
 */
@Configuration
public class DatayHarnessMcpServerConfig {

    /**
     * Harness MCP 端点路径。
     */
    public static final String MCP_ENDPOINT = "/mcp/datay";

    private static final String SERVER_NAME = "datay-harness-mcp-server";

    private static final String SERVER_VERSION = "1.0.0";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private static final String INSTRUCTIONS =
        "DataY 平台管理工具集：可对数据源、ETL 任务、数据模型等对象执行查询、创建、修改、删除与运行类操作，" +
        "并提供指标智能问数能力（metric 分组：metric_query_guide / list_metrics / describe_metrics / " +
        "sample_dimension_values / query_metric_data / retrieve_metric_context）。" +
        "遇到指标数据类问题（如「上个月各门店销售额」）时，先调用 metric_query_guide 获取推荐流程，再按提示逐步拆解。" +
        "所有调用都需要 Bearer Token 鉴权，默认作用于令牌所属用户的默认租户，也可以通过 X-Tenant-Id / X-Tenant-Code 指定租户。" +
        "带副作用（mutating=true）的工具受服务端 datay.mcp.harness.allow-mutating-tools 开关控制；指标问数工具全部只读，不受该开关影响。";

    private final DatayHarnessToolRegistry toolRegistry;
    private final DatayHarnessExecutor toolExecutor;
    private final McpTokenService mcpTokenService;

    public DatayHarnessMcpServerConfig(
        DatayHarnessToolRegistry toolRegistry,
        DatayHarnessExecutor toolExecutor,
        McpTokenService mcpTokenService
    ) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.mcpTokenService = mcpTokenService;
    }

    @Bean("datayHarnessMcpTransportProvider")
    public HttpServletStreamableServerTransportProvider datayHarnessMcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(MCP_ENDPOINT)
            .contextExtractor(datayHarnessContextExtractor())
            .securityValidator(datayHarnessSecurityValidator())
            .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> datayHarnessMcpServletRegistration(
        @Qualifier("datayHarnessMcpTransportProvider") HttpServletStreamableServerTransportProvider transportProvider
    ) {
        return new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT);
    }

    /**
     * 依据注册中心里的全部 {@link DatayHarnessTool} 动态装配 MCP 能力。
     */
    @Bean("datayHarnessMcpSyncServer")
    public McpSyncServer datayHarnessMcpSyncServer(
        @Qualifier("datayHarnessMcpTransportProvider") HttpServletStreamableServerTransportProvider transportProvider
    ) {
        McpServer.SyncSpecification<?> specification = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .instructions(INSTRUCTIONS)
            .capabilities(ServerCapabilities.builder().tools(true).build());

        for (DatayHarnessTool tool : toolRegistry.all()) {
            specification = specification.toolCall(toMcpTool(tool), (exchange, request) ->
                toolExecutor.invoke(tool.name(), exchange.transportContext(), request.arguments())
            );
        }
        return specification.build();
    }

    /**
     * 把 Harness 工具契约映射为 MCP 工具定义，并用注解向客户端声明只读/写属性。
     */
    private Tool toMcpTool(DatayHarnessTool tool) {
        return Tool.builder(tool.name(), tool.parametersSchema())
            .description(tool.description())
            .annotations(ToolAnnotations.builder().readOnlyHint(!tool.mutating()).build())
            .build();
    }

    /**
     * 把访问令牌与可选租户头写入 {@link McpTransportContext}，供执行器解析。
     */
    private McpTransportContextExtractor<HttpServletRequest> datayHarnessContextExtractor() {
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

    private ServerTransportSecurityValidator datayHarnessSecurityValidator() {
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
