package com.data.datafusion.ai.tool;

import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数据库元数据查询工具（只读）。
 *
 * <p>让模型在生成 SQL 前先「看表」：可以列 schema、列表、列字段。
 * 相比把整库 schema 一次性塞进提示词，按需查询能显著降低 token 消耗，
 * 也支持在表非常多时逐级下钻。
 */
@Component
public class DatabaseSchemaQueryTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "list_database_objects";

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSchemaQueryTool.class);

    /** 单次最多返回的表/字段数量，避免上下文爆炸。 */
    private static final int MAX_LIST_SIZE = 200;

    private final DataSourceQueryService dataSourceQueryService;
    private final DataSourceService dataSourceService;

    public DatabaseSchemaQueryTool(DataSourceQueryService dataSourceQueryService, DataSourceService dataSourceService) {
        this.dataSourceQueryService = dataSourceQueryService;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            查询当前数据源的数据库结构元数据，用于在编写 SQL 前确认可用的 schema、表和字段。
            通过 objectType 参数选择查询层级：
            - schemas：列出所有 schema；
            - tables：列出指定 schema 下的表（需提供 schema）；
            - columns：列出指定表和 schema 下的字段及类型（需提供 schema 和 table）。
            建议先查 schemas，再查 tables，最后查 columns，逐级下钻。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> objectType = new LinkedHashMap<>();
        objectType.put("type", "string");
        objectType.put("enum", List.of("schemas", "tables", "columns"));
        objectType.put("description", "查询层级：schemas 列 schema，tables 列表，columns 列字段");
        props.put("objectType", objectType);

        Map<String, Object> schemaName = new LinkedHashMap<>();
        schemaName.put("type", "string");
        schemaName.put("description", "schema/catalog 名称，objectType 为 tables 或 columns 时必填");
        props.put("schema", schemaName);

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "string");
        table.put("description", "表名，objectType 为 columns 时必填");
        props.put("table", table);

        Map<String, Object> search = new LinkedHashMap<>();
        search.put("type", "string");
        search.put("description", "表名模糊搜索关键字，可选，仅对 objectType=tables 生效");
        props.put("search", search);

        Map<String, Object> dataSourceId = new LinkedHashMap<>();
        dataSourceId.put("type", "integer");
        dataSourceId.put("description", "目标数据源 id，可选；缺省使用当前会话数据源。可用 list_data_sources 获取");
        props.put("dataSourceId", dataSourceId);

        schema.put("properties", props);
        schema.put("required", List.of("objectType"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) throws Exception {
        DataSourceDTO dataSource = resolveDataSource(arguments, context);
        if (dataSource == null) {
            return Map.of("error", "当前会话未绑定数据源，且未提供有效的 dataSourceId");
        }

        String objectType = asString(arguments.get("objectType"));
        String schema = asString(arguments.get("schema"));
        String table = asString(arguments.get("table"));
        String search = asString(arguments.get("search"));

        if (objectType == null) {
            return Map.of("error", "缺少参数 objectType");
        }

        try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dataSource))) {
            switch (objectType) {
                case "schemas":
                    return Map.of("schemas", limit(DBUtils.getSchemas(connection)));
                case "tables": {
                    if (isBlank(schema)) {
                        return Map.of("error", "objectType=tables 时必须提供 schema");
                    }
                    List<TableMeta> tables = DBUtils.getTableList(connection, schema, MAX_LIST_SIZE, search);
                    List<Map<String, Object>> simplified = new ArrayList<>();
                    for (TableMeta meta : tables) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("table", meta.getTable());
                        if (meta.getSchema() != null) {
                            item.put("schema", meta.getSchema());
                        }
                        if (meta.getCatalog() != null) {
                            item.put("catalog", meta.getCatalog());
                        }
                        if (meta.getComment() != null && !meta.getComment().isBlank()) {
                            item.put("comment", meta.getComment());
                        }
                        simplified.add(item);
                    }
                    return Map.of("schema", schema, "total", simplified.size(), "tables", simplified);
                }
                case "columns": {
                    if (isBlank(schema) || isBlank(table)) {
                        return Map.of("error", "objectType=columns 时必须提供 schema 和 table");
                    }
                    TableMeta meta = DBUtils.getTableMetaData(connection, schema, table);
                    List<Map<String, Object>> columns = new ArrayList<>();
                    for (ColumnMeta column : meta.columns()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", column.getName());
                        item.put("type", column.getType());
                        item.put("nullable", column.isNullable());
                        if (column.isPrimaryKey()) {
                            item.put("primaryKey", true);
                        }
                        if (column.getComment() != null && !column.getComment().isBlank()) {
                            item.put("comment", column.getComment());
                        }
                        columns.add(item);
                    }
                    return Map.of("schema", schema, "table", table, "total", columns.size(), "columns", columns);
                }
                default:
                    return Map.of("error", "不支持的 objectType: " + objectType);
            }
        } catch (SQLException e) {
            LOG.warn("AI tool list_database_objects failed", e);
            return Map.of("error", "查询数据库元数据失败: " + e.getMessage());
        }
    }

    private List<String> limit(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.size() > MAX_LIST_SIZE ? new ArrayList<>(values.subList(0, MAX_LIST_SIZE)) : values;
    }

    private DataSourceDTO resolveDataSource(Map<String, Object> arguments, AiToolContext context) {
        Object raw = arguments.get("dataSourceId");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            try {
                Long id = Long.parseLong(String.valueOf(raw).trim());
                return dataSourceService.findOne(id).orElse(null);
            } catch (NumberFormatException ignored) {
                // 非法 id 时回退到会话数据源
            }
        }
        return context.getDataSource();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
