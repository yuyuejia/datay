package com.data.datafusion.mcp.harness;

import static com.data.datafusion.mcp.harness.DatayHarnessArgs.clamp;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.intVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.mapVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.reqLong;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.reqStr;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.str;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.integer;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.object;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.properties;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.string;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.stringMap;

import com.data.datafusion.service.DefaultWarehouseConfigService;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 数据源对象的 Harness 工具族。
 *
 * <p>覆盖数据源的增删改查、连通性测试、库表元数据浏览、SQL 查询与默认数仓设置，
 * 行为与 {@code /api/data-sources} 下的 REST 接口保持一致（含管理员权限约束）。
 */
@Configuration
public class DataSourceHarnessTools {

    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;
    private final DefaultWarehouseConfigService defaultWarehouseConfigService;

    public DataSourceHarnessTools(
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService,
        DefaultWarehouseConfigService defaultWarehouseConfigService
    ) {
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.defaultWarehouseConfigService = defaultWarehouseConfigService;
    }

    @Bean
    DatayHarnessTool datasourceListTool() {
        return DatayHarnessTool.read(
            "datasource_list",
            "分页列出当前租户下的数据源，返回名称、类型、连接地址等基本信息（不含密码）。",
            object(properties("page", integer("页码，从 0 开始，默认 0"), "size", integer("每页条数，默认 20，最大 200"), "search", string("可选关键字，按名称/主机/端口/URL/schema/用户名模糊过滤"))),
            (args, context) -> {
                int pageIndex = Math.max(0, intVal(args, "page", 0));
                int size = clamp(intVal(args, "size", 20), 1, 200);
                Page<DataSourceDTO> page = dataSourceService.findAll(PageRequest.of(pageIndex, size), str(args, "search"));
                return DatayHarnessViews.page(page, DatayHarnessViews::dataSource);
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceGetTool() {
        return DatayHarnessTool.read(
            "datasource_get",
            "按 ID 查询单个数据源的详细信息（不含密码）。",
            object(properties("id", integer("数据源 ID")), "id"),
            (args, context) -> {
                DataSourceDTO dto = requireDataSource(reqLong(args, "id"));
                Map<String, Object> view = DatayHarnessViews.dataSource(dto);
                view.put("extraParams", dto.getExtraParams());
                return view;
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceCreateTool() {
        return DatayHarnessTool.write(
            "datasource_create",
            "注册一个新的数据源，返回创建后的数据源信息（不含密码）。",
            object(
                properties(
                    "name", string("数据源名称"),
                    "type", string("数据库类型，如 mysql、postgresql、oracle、sqlserver、duckdb、doris、clickhouse"),
                    "url", string("JDBC 连接 URL，如 jdbc:mysql://127.0.0.1:3306/dbname"),
                    "hostname", string("主机名（可选，便于展示）"),
                    "port", string("端口（可选，便于展示）"),
                    "schemaName", string("默认 schema / 数据库名（可选）"),
                    "database", string("默认数据库名（可选）"),
                    "username", string("用户名"),
                    "password", string("密码"),
                    "connectionMode", string("连接模式（可选，如 simple / custom）"),
                    "description", string("描述（可选）"),
                    "extraParams", stringMap("扩展连接参数（可选），如 {\"quack.token\":\"...\"}")
                ),
                "name",
                "type",
                "url"
            ),
            (args, context) -> {
                DataSourceDTO dto = new DataSourceDTO();
                dto.setName(reqStr(args, "name"));
                dto.setType(reqStr(args, "type"));
                dto.setUrl(reqStr(args, "url"));
                applyOptionalFields(dto, args);
                DataSourceDTO saved = dataSourceService.save(dto);
                return DatayHarnessViews.dataSource(saved);
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceUpdateTool() {
        return DatayHarnessTool.write(
            "datasource_update",
            "按 ID 更新数据源，仅提交需要修改的字段，未提交的字段保持原值。",
            object(
                properties(
                    "id", integer("数据源 ID"),
                    "name", string("数据源名称"),
                    "type", string("数据库类型"),
                    "url", string("JDBC 连接 URL"),
                    "hostname", string("主机名"),
                    "port", string("端口"),
                    "schemaName", string("默认 schema / 数据库名"),
                    "database", string("默认数据库名"),
                    "username", string("用户名"),
                    "password", string("密码"),
                    "connectionMode", string("连接模式"),
                    "description", string("描述"),
                    "extraParams", stringMap("扩展连接参数")
                ),
                "id"
            ),
            (args, context) -> {
                Long id = reqLong(args, "id");
                requireDataSource(id);
                DataSourceDTO dto = new DataSourceDTO();
                dto.setId(id);
                dto.setName(str(args, "name"));
                dto.setType(str(args, "type"));
                dto.setUrl(str(args, "url"));
                applyOptionalFields(dto, args);
                return dataSourceService
                    .partialUpdate(dto)
                    .map(DatayHarnessViews::dataSource)
                    .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceDeleteTool() {
        return DatayHarnessTool.write(
            "datasource_delete",
            "按 ID 删除数据源。删除前请确认没有 ETL 任务或数据模型仍在引用它。",
            object(properties("id", integer("数据源 ID")), "id"),
            (args, context) -> {
                Long id = reqLong(args, "id");
                DataSourceDTO dto = requireDataSource(id);
                dataSourceService.delete(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("deletedId", id);
                result.put("name", dto.getName());
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceTestConnectionTool() {
        return DatayHarnessTool.read(
            "datasource_test_connection",
            "测试数据库连通性。传 id 时用已保存的数据源配置测试，否则用本次给出的连接参数测试。",
            object(
                properties(
                    "id", integer("数据源 ID（与连接参数二选一）"),
                    "type", string("数据库类型"),
                    "url", string("JDBC 连接 URL"),
                    "username", string("用户名"),
                    "password", string("密码"),
                    "extraParams", stringMap("扩展连接参数")
                )
            ),
            (args, context) -> {
                Long id = DatayHarnessArgs.longVal(args, "id");
                DataSourceDTO dto;
                if (id != null) {
                    dto = requireDataSource(id);
                } else {
                    dto = new DataSourceDTO();
                    dto.setType(reqStr(args, "type"));
                    dto.setUrl(reqStr(args, "url"));
                    dto.setUsername(str(args, "username"));
                    dto.setPassword(str(args, "password"));
                    dto.setExtraParams(asStringMap(mapVal(args, "extraParams")));
                }
                boolean success = dataSourceService.testConnection(dto);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", success);
                result.put("message", success ? "连接测试成功" : "连接测试失败");
                result.put("target", id == null ? dto.getUrl() : "datasource#" + id);
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceSchemasTool() {
        return DatayHarnessTool.read(
            "datasource_schemas",
            "列出指定数据源下的 schema / 数据库清单。",
            object(properties("id", integer("数据源 ID")), "id"),
            (args, context) -> {
                DataSourceDTO dto = requireDataSource(reqLong(args, "id"));
                try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dto))) {
                    return DBUtils.getSchemas(connection);
                }
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceTablesTool() {
        return DatayHarnessTool.read(
            "datasource_tables",
            "列出指定数据源下某个 schema 的表清单，返回表名与注释，便于后续配置 ETL 任务。",
            object(
                properties(
                    "id", integer("数据源 ID"),
                    "schema", string("schema / 数据库名，缺省时使用数据源配置的 schema"),
                    "search", string("可选关键字，按表名模糊过滤"),
                    "limit", integer("最大返回表数量，默认 200，最大 1000")
                ),
                "id"
            ),
            (args, context) -> {
                DataSourceDTO dto = requireDataSource(reqLong(args, "id"));
                String schema = Optional.ofNullable(str(args, "schema")).orElse(dto.getSchemaName());
                Integer limit = clamp(intVal(args, "limit", 200), 1, 1000);
                try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dto))) {
                    List<TableMeta> tables = DBUtils.getTableList(connection, schema, limit, str(args, "search"));
                    return DatayHarnessViews.list(tables, DataSourceHarnessTools::tableMetaView);
                }
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceColumnsTool() {
        return DatayHarnessTool.read(
            "datasource_columns",
            "列出指定数据源下某张表的字段清单（字段名、类型、是否主键等）。",
            object(properties("id", integer("数据源 ID"), "schema", string("schema / 数据库名"), "table", string("表名")), "id", "table"),
            (args, context) -> {
                DataSourceDTO dto = requireDataSource(reqLong(args, "id"));
                String schema = Optional.ofNullable(str(args, "schema")).orElse(dto.getSchemaName());
                String table = reqStr(args, "table");
                try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dto))) {
                    List<ColumnMeta> columns = DBUtils.getTableMetaData(connection, schema, table).columns();
                    return DatayHarnessViews.list(columns, DataSourceHarnessTools::columnMetaView);
                }
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceQueryTool() {
        return DatayHarnessTool.write(
            "datasource_query",
            "在指定数据源上执行 SQL。可用于取数校验，也可执行 DDL/DML，因此按写操作对待：服务端关闭写操作时该工具不可用。",
            object(properties("id", integer("数据源 ID"), "sql", string("要执行的 SQL 语句")), "id", "sql"),
            (args, context) -> dataSourceQueryService.executeQuery(requireDataSource(reqLong(args, "id")), reqStr(args, "sql"))
        );
    }

    @Bean
    DatayHarnessTool datasourceDefaultWarehouseTool() {
        return DatayHarnessTool.read(
            "datasource_default_warehouse",
            "查询当前租户的默认数仓数据源 ID。",
            object(properties()),
            (args, context) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("dataSourceId", defaultWarehouseConfigService.getDefaultDataSourceId().orElse(null));
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool datasourceSetDefaultWarehouseTool() {
        return DatayHarnessTool.write(
            "datasource_set_default_warehouse",
            "把指定数据源设置为当前租户的默认数仓。需要管理员权限。",
            object(properties("id", integer("数据源 ID")), "id"),
            Set.of("ROLE_ADMIN", "ROLE_TENANT_ADMIN"),
            (args, context) -> {
                Long id = reqLong(args, "id");
                requireDataSource(id);
                defaultWarehouseConfigService.setDefaultDataSourceId(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("dataSourceId", id);
                return result;
            }
        );
    }

    /**
     * 把可选的连接字段从入参搬到 DTO 上；缺省字段保持 {@code null}，
     * 配合 {@code partialUpdate} 实现「只改提交的字段」。
     */
    private static void applyOptionalFields(DataSourceDTO dto, Map<String, Object> args) {
        dto.setHostname(str(args, "hostname"));
        dto.setPort(str(args, "port"));
        dto.setSchemaName(str(args, "schemaName"));
        dto.setDatabase(str(args, "database"));
        dto.setUsername(str(args, "username"));
        dto.setPassword(str(args, "password"));
        dto.setConnectionMode(str(args, "connectionMode"));
        dto.setDescription(str(args, "description"));
        dto.setExtraParams(asStringMap(mapVal(args, "extraParams")));
    }

    private static Map<String, String> asStringMap(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(key, value == null ? null : String.valueOf(value)));
        return result;
    }

    private DataSourceDTO requireDataSource(Long id) {
        return dataSourceService.findOne(id).orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));
    }

    private static Map<String, Object> tableMetaView(TableMeta table) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("schema", table.getSchema());
        view.put("catalog", table.getCatalog());
        view.put("tableName", table.getTable());
        view.put("comment", table.getComment());
        return view;
    }

    private static Map<String, Object> columnMetaView(ColumnMeta column) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("columnName", column.getName());
        view.put("typeName", column.getType());
        view.put("length", column.getLength());
        view.put("precision", column.getPrecision());
        view.put("scale", column.getScale());
        view.put("nullable", column.isNullable());
        view.put("primaryKey", column.isPrimaryKey());
        view.put("comment", column.getComment());
        return view;
    }
}
