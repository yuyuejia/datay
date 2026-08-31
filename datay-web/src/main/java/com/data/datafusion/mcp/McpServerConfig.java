package com.data.datafusion.mcp;

import static com.data.datafusion.job.TaskConstants.TASK_STATUS_OFFLINE;

import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DataSyncService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.DataSyncDTO;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;

/**
 * MCP (Model Context Protocol) server embedded in the DataY web application.
 *
 * <p>Exposes the DataY capabilities (data source registration, data query and sync task
 * creation) to AI clients through a streamable HTTP transport served at {@code /mcp}.
 */
@Configuration
public class McpServerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerConfig.class);

    private static final String MCP_ENDPOINT = "/mcp";

    private final ObjectMapper objectMapper;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;
    private final DataSyncService dataSyncService;

    public McpServerConfig(
        ObjectMapper objectMapper,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService,
        DataSyncService dataSyncService
    ) {
        this.objectMapper = objectMapper;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.dataSyncService = dataSyncService;
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint(MCP_ENDPOINT).build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
        HttpServletStreamableServerTransportProvider transportProvider
    ) {
        return new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT);
    }

    @Bean
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider) {
        return McpServer.sync(transportProvider)
            .serverInfo("datay-mcp-server", "1.0.0")
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .toolCall(datasourceRegisterTool(), this::registerDataSource)
            .toolCall(datasourceListTool(), this::listDataSources)
            .toolCall(datasourceQueryTool(), this::queryDataSource)
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
        properties.put("dataSourceId", integerProp("数据源 ID"));
        properties.put("sql", stringProp("要执行的 SQL 查询语句"));
        return Tool.builder("datasource_query", schema(properties, List.of("dataSourceId", "sql")))
            .description("在指定数据源上执行 SQL 查询，返回 columns、rows 与 affectedRows")
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
        properties.put("sourceId", integerProp("源数据源 ID"));
        properties.put("targetId", integerProp("目标数据源 ID"));
        properties.put("type", stringProp("同步类型：FULL_SYNC / DATA_ONLY / SCHEMA_ONLY，默认 FULL_SYNC"));
        properties.put("cron", stringProp("调度表达式（可选）"));
        properties.put("tables", tablesArray);

        return Tool.builder("sync_task_create", schema(properties, List.of("jobName", "sourceId", "targetId", "tables")))
            .description("创建一个数据同步任务，从源数据源同步表到目标数据源，返回任务 ID、jobCode 与状态")
            .build();
    }

    private Tool syncTaskExecuteTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("syncTaskId", integerProp("同步任务 ID（DataSync 的 id）"));
        return Tool.builder("sync_task_execute", schema(properties, List.of("syncTaskId")))
            .description("立即执行指定的数据同步任务")
            .build();
    }

    // ---------------------------------------------------------------- handlers

    private CallToolResult registerDataSource(McpSyncServerExchange exchange, CallToolRequest request) {
        try {
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
            ZonedDateTime now = ZonedDateTime.now();
            dto.setCreateTime(now);
            dto.setUpdateTime(now);

            DataSourceDTO saved = dataSourceService.save(dto);
            return jsonResult(toDataSourceView(saved));
        } catch (Exception e) {
            LOG.error("Failed to register datasource", e);
            return errorResult("数据源注册失败: " + e.getMessage());
        }
    }

    private CallToolResult listDataSources(McpSyncServerExchange exchange, CallToolRequest request) {
        try {
            Map<String, Object> args = request.arguments();
            String search = asString(args, "search");
            List<Map<String, Object>> result = new ArrayList<>();
            dataSourceService
                .findAll(PageRequest.of(0, 200), search)
                .getContent()
                .forEach(dto -> result.add(toDataSourceView(dto)));
            return jsonResult(result);
        } catch (Exception e) {
            LOG.error("Failed to list datasources", e);
            return errorResult("数据源列表查询失败: " + e.getMessage());
        }
    }

    private CallToolResult queryDataSource(McpSyncServerExchange exchange, CallToolRequest request) {
        try {
            Map<String, Object> args = request.arguments();
            Long id = asLong(args, "dataSourceId");
            String sql = asString(args, "sql");
            if (id == null) {
                return errorResult("缺少 dataSourceId 参数");
            }
            Optional<DataSourceDTO> dataSource = dataSourceService.findOne(id);
            if (dataSource.isEmpty()) {
                return errorResult("数据源不存在: " + id);
            }
            return jsonResult(dataSourceQueryService.executeQuery(dataSource.orElseThrow(), sql));
        } catch (Exception e) {
            LOG.error("Failed to execute query", e);
            return errorResult("查询失败: " + e.getMessage());
        }
    }

    private CallToolResult createSyncTask(McpSyncServerExchange exchange, CallToolRequest request) {
        try {
            Map<String, Object> args = request.arguments();
            Long sourceId = asLong(args, "sourceId");
            Long targetId = asLong(args, "targetId");
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
            dto.setSource(String.valueOf(sourceId));
            dto.setTarget(String.valueOf(targetId));
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
        } catch (Exception e) {
            LOG.error("Failed to create sync task", e);
            return errorResult("同步任务创建失败: " + e.getMessage());
        }
    }

    private CallToolResult executeSyncTask(McpSyncServerExchange exchange, CallToolRequest request) {
        try {
            Map<String, Object> args = request.arguments();
            Long id = asLong(args, "syncTaskId");
            if (id == null) {
                return errorResult("缺少 syncTaskId 参数");
            }
            dataSyncService.executeDataSyncNow(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("executed", true);
            return jsonResult(result);
        } catch (Exception e) {
            LOG.error("Failed to execute sync task", e);
            return errorResult("同步任务执行失败: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    private List<DataSyncTableConfigDTO> parseTables(Object tablesObj, Long sourceId, Long targetId) {
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
                    config.setSrcDatasource(String.valueOf(sourceId));
                    config.setDesDatasource(String.valueOf(targetId));
                    result.add(config);
                }
            }
        }
        return result;
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
