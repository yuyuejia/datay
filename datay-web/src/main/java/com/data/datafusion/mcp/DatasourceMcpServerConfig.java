package com.data.datafusion.mcp;

import static com.data.datafusion.job.TaskConstants.TASK_STATUS_OFFLINE;

import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DataSyncService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.DataSyncDTO;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;

/**
 * 数据源 MCP (Model Context Protocol) 服务端，挂载在 {@code /mcp/datasource}。
 *
 * <p>把 DataY 的数据源能力（数据源注册、SQL 查询、表清单、同步任务创建与执行）通过
 * streamable HTTP transport 暴露给 AI 客户端，与指标问数 MCP 服务（{@code /mcp/metric}）相互独立。
 *
 * <p>客户端使用 {@code Authorization: Bearer <mcp-token>} 鉴权，可用可选的
 * {@code X-Tenant-Id} 或 {@code X-Tenant-Code} 头把操作限定到指定租户；
 * 两者都缺省时使用已认证用户的默认租户。
 */
@Configuration
public class DatasourceMcpServerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DatasourceMcpServerConfig.class);

    private static final String MCP_ENDPOINT = "/mcp/datasource";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private static final int DEFAULT_TABLE_LIMIT = 200;

    private static final int MAX_TABLE_LIMIT = 1000;

    private final ObjectMapper objectMapper;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;
    private final DataSyncService dataSyncService;
    private final McpTokenService mcpTokenService;
    private final TenantRepository tenantRepository;

    public DatasourceMcpServerConfig(
        ObjectMapper objectMapper,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService,
        DataSyncService dataSyncService,
        McpTokenService mcpTokenService,
        TenantRepository tenantRepository
    ) {
        this.objectMapper = objectMapper;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.dataSyncService = dataSyncService;
        this.mcpTokenService = mcpTokenService;
        this.tenantRepository = tenantRepository;
    }

    @Bean("datasourceMcpTransportProvider")
    public HttpServletStreamableServerTransportProvider datasourceMcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(MCP_ENDPOINT)
            .contextExtractor(mcpContextExtractor())
            .securityValidator(mcpSecurityValidator())
            .build();
    }

    /**
     * Extract the bearer token and the optional tenant headers from the HTTP request into the
     * {@link McpTransportContext} so that tool handlers can scope the data operations to the
     * requested tenant. The tenant headers are optional: when absent the user's default tenant is used.
     *
     * @return the context extractor reading {@code Authorization}, {@code X-Tenant-Id} and {@code X-Tenant-Code}.
     */
    private McpTransportContextExtractor<HttpServletRequest> mcpContextExtractor() {
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

    private ServerTransportSecurityValidator mcpSecurityValidator() {
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

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> datasourceMcpServletRegistration(
        @Qualifier("datasourceMcpTransportProvider") HttpServletStreamableServerTransportProvider transportProvider
    ) {
        return new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT);
    }

    @Bean("datasourceMcpSyncServer")
    public McpSyncServer datasourceMcpSyncServer(
        @Qualifier("datasourceMcpTransportProvider") HttpServletStreamableServerTransportProvider transportProvider
    ) {
        return McpServer.sync(transportProvider)
            .serverInfo("datay-datasource-mcp-server", "1.0.0")
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .toolCall(datasourceRegisterTool(), this::registerDataSource)
            .toolCall(datasourceListTool(), this::listDataSources)
            .toolCall(datasourceQueryTool(), this::queryDataSource)
            .toolCall(datasourceTablesTool(), this::listDataSourceTables)
            .toolCall(syncTaskCreateTool(), this::createSyncTask)
            .toolCall(syncTaskExecuteTool(), this::executeSyncTask)
            .build();
    }

    // ------------------------------------------------------------------ tools

    private Tool datasourceRegisterTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", stringProp("数据源名称"));
        properties.put("type", stringProp("数据库类型，如 mysql、postgresql、oracle、duckdb"));
        properties.put("url", stringProp("JDBC 连接 URL，如 jdbc:mysql://127.0.0.1:3306/dbname"));
        properties.put("username", stringProp("用户名"));
        properties.put("password", stringProp("密码"));
        properties.put("hostname", stringProp("主机名"));
        properties.put("port", stringProp("端口"));
        properties.put("schemaName", stringProp("默认 schema / 数据库名"));
        properties.put("description", stringProp("描述"));
        Map<String, Object> extraParamsProp = new LinkedHashMap<>();
        extraParamsProp.put("type", "object");
        extraParamsProp.put("additionalProperties", Map.of("type", "string"));
        extraParamsProp.put("description", "扩展参数，如 Quack 的 {\"quack.token\":\"...\"}");
        properties.put("extraParams", extraParamsProp);
        return Tool.builder("datasource_register", schema(properties, List.of("name", "type", "url", "username", "password")))
            .description("注册一个新的数据源，返回数据源 ID 及基本信息（不含密码）")
            .build();
    }

    private Tool datasourceListTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("search", stringProp("可选关键字，按名称、主机、端口、URL、schema 或用户名过滤"));
        return Tool.builder("datasource_list", schema(properties, List.of())).description("列出已注册的数据源（不含密码）").build();
    }

    private Tool datasourceQueryTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dataSourceId", stringProp("数据源 ID"));
        properties.put("sql", stringProp("要执行的 SQL 查询语句"));
        return Tool.builder("datasource_query", schema(properties, List.of("dataSourceId", "sql")))
            .description("在指定数据源上执行 SQL 查询，返回 columns、rows 与 affectedRows")
            .build();
    }

    private Tool datasourceTablesTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dataSourceId", stringProp("数据源 ID"));
        properties.put("schema", stringProp("schema / 数据库名，可选，默认使用数据源配置的 schema"));
        properties.put("search", stringProp("可选关键字，按表名模糊过滤"));
        properties.put("limit", integerProp("最大返回表数量，默认 200，最大 1000"));
        return Tool.builder("datasource_tables", schema(properties, List.of("dataSourceId")))
            .description("列出指定数据源下某个 schema 的表清单，返回 schema、表名与注释")
            .build();
    }

    private Tool syncTaskCreateTool() {
        Map<String, Object> tableItem = new LinkedHashMap<>();
        Map<String, Object> tableProps = new LinkedHashMap<>();
        tableProps.put("srcSchemaName", stringProp("源 schema"));
        tableProps.put("srcTableName", stringProp("源表名"));
//        tableProps.put("srcColPks", stringProp("源主键/增量列，逗号分隔，增量同步需要"));
        tableProps.put("desSchemaName", stringProp("目标 schema"));
        tableProps.put("desTableName", stringProp("目标表名"));
        tableItem.put("type", "object");
        tableItem.put("properties", tableProps);
        tableItem.put("required", List.of("srcSchemaName", "srcTableName", "desSchemaName", "desTableName"));

        Map<String, Object> tablesArray = new LinkedHashMap<>();
        tablesArray.put("type", "array");
        tablesArray.put("items", tableItem);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jobName", stringProp("同步任务名称"));
        properties.put("jobDesc", stringProp("任务描述"));
        properties.put("sourceId", stringProp("源数据源 ID"));
        properties.put("targetId", stringProp("目标数据源 ID"));
        properties.put("type", stringProp("同步类型：FULL_SYNC / DATA_ONLY / SCHEMA_ONLY，默认 FULL_SYNC"));
        properties.put("cron", stringProp("调度表达式（可选）"));
        properties.put("tables", tablesArray);

        return Tool.builder("sync_task_create", schema(properties, List.of("jobName", "sourceId", "targetId", "tables")))
            .description("创建一个数据同步任务，从源数据源同步表到目标数据源，返回任务 ID、jobCode 与状态")
            .build();
    }

    private Tool syncTaskExecuteTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("syncTaskId", stringProp("同步任务 ID（DataSync 的 id）"));
        return Tool.builder("sync_task_execute", schema(properties, List.of("syncTaskId")))
            .description("立即执行指定的数据同步任务")
            .build();
    }

    // ---------------------------------------------------------------- handlers

    private CallToolResult registerDataSource(McpSyncServerExchange exchange, CallToolRequest request) {
        return runWithTenant(exchange, "数据源注册失败", () -> {
            Map<String, Object> args = request.arguments();
            DataSourceDTO dto = new DataSourceDTO();
            dto.setName(asString(args, "name"));
            dto.setType(asString(args, "type"));
            dto.setUrl(asString(args, "url"));
            dto.setUsername(asString(args, "username"));
            dto.setPassword(asString(args, "password"));
            dto.setHostname(asString(args, "hostname"));
            dto.setPort(asString(args, "port"));
            dto.setSchemaName(asString(args, "schemaName"));
            dto.setDescription(asString(args, "description"));
            Object extraParams = args.get("extraParams");
            if (extraParams instanceof Map<?, ?> map) {
                Map<String, String> extra = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        extra.put(k.toString(), v.toString());
                    }
                });
                dto.setExtraParams(extra.isEmpty() ? null : extra);
            }
            ZonedDateTime now = ZonedDateTime.now();
            dto.setCreateTime(now);
            dto.setUpdateTime(now);

            DataSourceDTO saved = dataSourceService.save(dto);
            return jsonResult(toDataSourceView(saved));
        });
    }

    private CallToolResult listDataSources(McpSyncServerExchange exchange, CallToolRequest request) {
        return runWithTenant(exchange, "数据源列表查询失败", () -> {
            Map<String, Object> args = request.arguments();
            String search = asString(args, "search");
            List<Map<String, Object>> result = new ArrayList<>();
            dataSourceService
                .findAll(PageRequest.of(0, 200), search)
                .getContent()
                .forEach(dto -> result.add(toDataSourceView(dto)));
            return jsonResult(result);
        });
    }

    private CallToolResult queryDataSource(McpSyncServerExchange exchange, CallToolRequest request) {
        return runWithTenant(exchange, "查询失败", () -> {
            Map<String, Object> args = request.arguments();
            String id = asString(args, "dataSourceId");
            String sql = asString(args, "sql");
            if (id == null) {
                return errorResult("缺少 dataSourceId 参数");
            }
            Optional<DataSourceDTO> dataSource = dataSourceService.findOne(id);
            if (dataSource.isEmpty()) {
                return errorResult("数据源不存在: " + id);
            }
            return jsonResult(dataSourceQueryService.executeQuery(dataSource.orElseThrow(), sql));
        });
    }

    private CallToolResult listDataSourceTables(McpSyncServerExchange exchange, CallToolRequest request) {
        return runWithTenant(exchange, "数据源表清单查询失败", () -> {
            Map<String, Object> args = request.arguments();
            String id = asString(args, "dataSourceId");
            if (id == null) {
                return errorResult("缺少 dataSourceId 参数");
            }
            Optional<DataSourceDTO> dataSource = dataSourceService.findOne(id);
            if (dataSource.isEmpty()) {
                return errorResult("数据源不存在: " + id);
            }
            DataSourceDTO dto = dataSource.orElseThrow();
            String schema = asString(args, "schema");
            if (schema == null || schema.isBlank()) {
                schema = dto.getSchemaName();
            }
            String search = asString(args, "search");
            int limit = resolveTableLimit(asLong(args, "limit"));

            List<Map<String, Object>> tables = new ArrayList<>();
            try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dto))) {
                for (TableMeta table : DBUtils.getTableList(connection, schema, limit, search)) {
                    tables.add(toTableMetaView(table));
                }
            }
            return jsonResult(tables);
        });
    }

    private CallToolResult createSyncTask(McpSyncServerExchange exchange, CallToolRequest request) {
        return runWithTenant(exchange, "同步任务创建失败", () -> {
            Map<String, Object> args = request.arguments();
            String sourceId = asString(args, "sourceId");
            String targetId = asString(args, "targetId");
            if (sourceId == null || targetId == null) {
                return errorResult("缺少 sourceId 或 targetId 参数");
            }
            if (dataSourceService.findOne(sourceId).isEmpty()) {
                return errorResult("源数据源不存在: " + sourceId);
            }
            if (dataSourceService.findOne(targetId).isEmpty()) {
                return errorResult("目标数据源不存在: " + targetId);
            }

            DataSyncDTO dto = new DataSyncDTO();
            dto.setJobName(asString(args, "jobName"));
            dto.setJobDesc(asString(args, "jobDesc"));
            dto.setSource(sourceId);
            dto.setTarget(targetId);
            String type = asString(args, "type");
            dto.setType(type == null || type.isBlank() ? "FULL_SYNC" : type);
            dto.setCron(asString(args, "cron"));
            dto.setStatus(TASK_STATUS_OFFLINE);
            ZonedDateTime now = ZonedDateTime.now();
            dto.setCreateTime(now);
            dto.setUpdateTime(now);
            dto.setSelectedTables(parseTables(args.get("tables"), sourceId, targetId));

            if (dto.getSelectedTables() == null || dto.getSelectedTables().isEmpty()) {
                return errorResult("tables 参数不能为空");
            }

            DataSyncDTO saved = dataSyncService.save(dto);
            dataSyncService.createDataSyncTask(saved);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", saved.getId());
            result.put("jobName", saved.getJobName());
            result.put("jobCode", saved.getJobCode());
            result.put("status", saved.getStatus());
            result.put("type", saved.getType());
            result.put("source", saved.getSource());
            result.put("target", saved.getTarget());
            result.put("cron", saved.getCron());
            return jsonResult(result);
        });
    }

    private CallToolResult executeSyncTask(McpSyncServerExchange exchange, CallToolRequest request) {
        return runWithTenant(exchange, "同步任务执行失败", () -> {
            Map<String, Object> args = request.arguments();
            String id = asString(args, "syncTaskId");
            if (id == null) {
                return errorResult("缺少 syncTaskId 参数");
            }
            dataSyncService.executeDataSyncNow(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("executed", true);
            return jsonResult(result);
        });
    }

    // ---------------------------------------------------------------- tenant

    /**
     * Resolve the tenant of the current tool call and run the operation within that tenant scope.
     *
     * <p>The tenant is taken from the {@code X-Tenant-Id} or {@code X-Tenant-Code} header when present,
     * otherwise the authenticated user's default tenant is used. A requested tenant must belong to the
     * authenticated user, otherwise the call is rejected.
     */
    private CallToolResult runWithTenant(McpSyncServerExchange exchange, String errorMessage, ToolOperation operation) {
        McpTransportContext context = exchange.transportContext();
        String token = contextValue(context, AUTHORIZATION_HEADER);
        User user = mcpTokenService.authenticateUser(token).orElse(null);
        if (user == null) {
            return errorResult("无效或缺失的访问令牌");
        }

        Tenant tenant;
        try {
            tenant = resolveTenant(user, context);
        } catch (TenantAccessException e) {
            return errorResult(e.getMessage());
        }
        if (tenant == null) {
            return errorResult("用户未关联任何租户");
        }

        TenantContext.setTenantId(tenant.getId());
        TenantContext.setTenantCode(tenant.getCode());
        try {
            return operation.run();
        } catch (Exception e) {
            LOG.error(errorMessage, e);
            return errorResult(errorMessage + ": " + e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private Tenant resolveTenant(User user, McpTransportContext context) {
        String headerTenantId = contextValue(context, TENANT_ID_HEADER);
        String headerTenantCode = contextValue(context, TENANT_CODE_HEADER);
        if (headerTenantId == null && headerTenantCode == null) {
            return tenantRepository.findDefaultTenantByUserId(user.getId()).orElse(null);
        }

        List<Tenant> tenants = tenantRepository.findAllByUserId(user.getId());
        if (headerTenantId != null) {
            Long tenantId;
            try {
                tenantId = Long.valueOf(headerTenantId);
            } catch (NumberFormatException e) {
                throw new TenantAccessException("无效的租户标识: " + headerTenantId);
            }
            return tenants
                .stream()
                .filter(t -> tenantId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new TenantAccessException("无权访问指定租户: " + headerTenantId));
        }
        return tenants
            .stream()
            .filter(t -> headerTenantCode.equals(t.getCode()))
            .findFirst()
            .orElseThrow(() -> new TenantAccessException("无权访问指定租户: " + headerTenantCode));
    }

    private static String contextValue(McpTransportContext context, String key) {
        if (context == null) {
            return null;
        }
        Object value = context.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @FunctionalInterface
    private interface ToolOperation {
        CallToolResult run() throws Exception;
    }

    private static class TenantAccessException extends RuntimeException {

        TenantAccessException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- helpers

    private List<DataSyncTableConfigDTO> parseTables(Object tablesObj, String sourceId, String targetId) {
        List<DataSyncTableConfigDTO> result = new ArrayList<>();
        if (tablesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    DataSyncTableConfigDTO config = new DataSyncTableConfigDTO();
                    config.setSrcSchemaName(strOf(map.get("srcSchemaName")));
                    config.setSrcTableName(strOf(map.get("srcTableName")));
                    config.setSrcColPks(strOf(map.get("srcColPks")));
                    config.setDesSchemaName(strOf(map.get("desSchemaName")));
                    config.setDesTableName(strOf(map.get("desTableName")));
                    config.setSrcDatasource(sourceId);
                    config.setDesDatasource(targetId);
                    result.add(config);
                }
            }
        }
        return result;
    }

    private Map<String, Object> toTableMetaView(TableMeta table) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("schema", table.getSchema());
        view.put("table", table.getTable());
        view.put("catalog", table.getCatalog());
        view.put("comment", table.getComment());
        return view;
    }

    private static int resolveTableLimit(Long limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TABLE_LIMIT;
        }
        return (int) Math.min(limit, MAX_TABLE_LIMIT);
    }

    private Map<String, Object> toDataSourceView(DataSourceDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("name", dto.getName());
        view.put("type", dto.getType());
        view.put("url", dto.getUrl());
        view.put("hostname", dto.getHostname());
        view.put("port", dto.getPort());
        view.put("schemaName", dto.getSchemaName());
        view.put("username", dto.getUsername());
        view.put("description", dto.getDescription());
        return view;
    }

    private CallToolResult jsonResult(Object value) {
        try {
            return CallToolResult.builder().textContent(List.of(objectMapper.writeValueAsString(value))).build();
        } catch (Exception e) {
            return errorResult("结果序列化失败: " + e.getMessage());
        }
    }

    private CallToolResult errorResult(String message) {
        return CallToolResult.builder().isError(true).textContent(List.of(message)).build();
    }

    private static String asString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static String strOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> stringProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        if (description != null) {
            prop.put("description", description);
        }
        return prop;
    }

    private static Map<String, Object> integerProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "integer");
        if (description != null) {
            prop.put("description", description);
        }
        return prop;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
