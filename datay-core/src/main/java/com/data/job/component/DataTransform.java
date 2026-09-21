package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.component.transform.TransformSqlBuilder;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据转换组件。
 *
 * <p>预置了常用的转换规则（类型转换、过滤、字符串/数学/日期函数等），根据用户配置的规则列表
 * 生成 DuckDB SQL 对数据进行转换。处理流程：
 * <ol>
 *   <li>接收上游 FlowFile，将其写入 DuckDB 临时表（{@code main.tmp_<组件id>}）；</li>
 *   <li>根据 {@code rules}、{@code selectColumns}、{@code filter} 生成转换 SQL；</li>
 *   <li>在临时表上执行转换，将结果集封装为 FlowFile 发送给下游。</li>
 * </ol>
 *
 * <p>每个 FlowFile 独立转换（切分批次时逐批处理，不做跨批次聚合）。
 *
 * <p>配置示例：
 * <pre>
 * {
 *   "rules": [
 *     {"type": "cast", "column": "age", "targetType": "INTEGER"},
 *     {"type": "upper", "column": "name", "targetColumn": "name_upper"}
 *   ],
 *   "filters": [
 *     {"column": "status", "operator": "=", "value": "ACTIVE"}
 *   ],
 *   "filterLogic": "AND",
 *   "selectColumns": "",
 *   "outputTable": "transform_result"
 * }
 * </pre>
 */
@ComponentRegister("DataTransform")
public class DataTransform extends FlowComponent {

    private static final String DUCKDB_SCHEMA = "main";
    private static final int FETCH_SIZE = 5000;

    // 转换规则列表
    private Object rules;

    // 结构化过滤规则列表
    private Object filters;

    // 过滤规则之间的逻辑关系：AND（默认）或 OR
    private String filterLogic;

    // 基础输出字段，为空时输出 * EXCLUDE(被覆盖/删除/重命名的字段)
    private String selectColumns;

    // 输出表名（仅用于下游的表元数据标识）
    private String outputTable;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    // 当前 FlowFile 的上游输入表名，缺省时用于作为输出表名
    private String currentInputTable;

    public DataTransform() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        JSONArray records = flowFile.getJsonArray();
        if (records == null || records.isEmpty()) {
            return;
        }

        this.currentInputTable = resolveInputTable(flowFile);
        String inputTableName = "tmp_" + getId();
        try (Connection conn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            TableMeta inputTable = getInputTableMeta(conn, flowFile, inputTableName);

            DBUtils.execute(conn, "TRUNCATE TABLE " + qualify(inputTable.getTable()));
            migrateData(conn, inputTable, records);

            String sql = TransformSqlBuilder.buildSelectSql(
                qualify(inputTable.getTable()),
                TransformSqlBuilder.resolveRules(rules),
                selectColumns,
                TransformSqlBuilder.resolveRules(filters),
                filterLogic
            );
            logInfo("执行数据转换 SQL: " + sql);
            emitResult(conn, sql, flowFile);
        } catch (SQLException e) {
            throw new RuntimeException("数据转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取（必要时创建）输入临时表元数据。
     */
    private synchronized TableMeta getInputTableMeta(Connection conn, FlowFile flowFile, String tableName) throws SQLException {
        TableMeta cached = tableMetaMap.get(tableName);
        if (cached != null && DBUtils.tableExists(conn, DUCKDB_SCHEMA, tableName)) {
            return cached;
        }

        TableMeta table = buildTableMeta(flowFile, tableName);
        if (!DBUtils.schemaExists(conn, DUCKDB_SCHEMA)) {
            DBUtils.execute(conn, DatabaseConverter.generateSchemaDDL(table));
            logInfo("创建DuckDB schema: " + DUCKDB_SCHEMA);
        }
        if (!DBUtils.tableExists(conn, DUCKDB_SCHEMA, tableName)) {
            DBUtils.execute(conn, DatabaseConverter.generateTableDDL("duckdb", table));
            logInfo("创建DuckDB临时表: " + DUCKDB_SCHEMA + "." + tableName);
        } else {
            table = DBUtils.getTableMetaData(conn, DUCKDB_SCHEMA, tableName);
            table.setSchema(DUCKDB_SCHEMA);
        }
        tableMetaMap.put(tableName, table);
        return table;
    }

    /**
     * 依据上游表元数据或数据内容构建 DuckDB 表结构。
     */
    private TableMeta buildTableMeta(FlowFile flowFile, String tableName) {
        Object meta = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        TableMeta table;
        if (meta instanceof TableMeta && !((TableMeta) meta).columns().isEmpty()) {
            TableMeta source = (TableMeta) meta;
            if (source.getDbType() != null && !"duckdb".equalsIgnoreCase(source.getDbType())) {
                table = DatabaseConverter.convert(source.getDbType(), "duckdb", source);
            } else {
                table = new TableMeta(source.getTable(), new ArrayList<>(source.columns()));
            }
        } else {
            table = inferTableMeta(flowFile.getJsonArray());
        }
        table.setDbType("duckdb");
        table.setSchema(DUCKDB_SCHEMA);
        table.setTable(tableName);
        return table;
    }

    /**
     * 根据 JSON 数据推断表结构。
     */
    private TableMeta inferTableMeta(JSONArray records) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setDbType("duckdb");
        JSONObject firstRecord = records.getJSONObject(0);
        Set<String> columnNames = new LinkedHashSet<>(firstRecord.keySet());
        for (String columnName : columnNames) {
            tableMeta.addColumn(new ColumnMeta(columnName, inferColumnType(firstRecord.get(columnName))));
        }
        return tableMeta;
    }

    private String inferColumnType(Object value) {
        if (value == null) {
            return "VARCHAR";
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return "INTEGER";
        }
        if (value instanceof Long) {
            return "BIGINT";
        }
        if (value instanceof Double || value instanceof Float) {
            return "DOUBLE";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof java.util.Date) {
            return "TIMESTAMP";
        }
        return "VARCHAR";
    }

    /**
     * 使用 Appender 将数据写入临时表。
     */
    private void migrateData(Connection duckdbConn, TableMeta targetTable, JSONArray records) throws SQLException {
        DuckDBConnection duckDBConnection = (DuckDBConnection) DBUtils.getRawConnection((HikariProxyConnection) duckdbConn);
        try (DuckDBAppender appender = duckDBConnection.createAppender(targetTable.getSchema(), targetTable.getTable())) {
            for (Object item : records) {
                appender.beginRow();
                JSONObject record = (JSONObject) item;
                for (ColumnMeta column : targetTable.columns()) {
                    DBUtils.appendValue(appender, column.getType(), record.get(column.getName()));
                }
                appender.endRow();
            }
            logInfo("已写入 " + records.size() + " 条记录到DuckDB临时表 " + targetTable.getTable());
        }
    }

    /**
     * 执行转换 SQL，将结果集作为 FlowFile 发送给下游。
     */
    private void emitResult(Connection conn, String sql, FlowFile sourceFile) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.setFetchSize(FETCH_SIZE);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                TableMeta outputMeta = DBUtils.getTableMetaFromResultSet(rs.getMetaData());
                outputMeta.setDbType("duckdb");
                outputMeta.setSchema(DUCKDB_SCHEMA);
                outputMeta.setTable(resolveOutputTable());

                JSONArray output = new JSONArray();
                while (rs.next()) {
                    JSONObject record = new JSONObject();
                    for (int i = 1; i <= outputMeta.columns().size(); i++) {
                        record.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                    }
                    output.add(record);
                }

                FlowFile outFile = new FlowFile();
                outFile.setJsonArray(output);
                Object eventType = sourceFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE);
                if (eventType != null) {
                    outFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, eventType);
                }
                outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, outputMeta);
                outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, outputMeta);
                outFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, DUCKDB_SCHEMA);
                writeRecords(outFile);
                logInfo("数据转换输出 " + output.size() + " 条记录");
            }
        }
    }

    /**
     * 解析 FlowFile 对应的上游输入表名：优先取表属性，其次取表元数据，均缺失时返回 null。
     */
    String resolveInputTable(FlowFile flowFile) {
        Object tableAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE);
        if (tableAttr != null && !tableAttr.toString().trim().isEmpty()) {
            return tableAttr.toString().trim();
        }
        Object meta = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (meta instanceof TableMeta) {
            String table = ((TableMeta) meta).getTable();
            if (table != null && !table.trim().isEmpty()) {
                return table.trim();
            }
        }
        return null;
    }

    /**
     * 输出表名：优先使用配置，缺省使用上游输入表名，上游无表名时使用临时表名。
     */
    String resolveOutputTable() {
        if (outputTable != null && !outputTable.trim().isEmpty()) {
            return outputTable.trim();
        }
        if (currentInputTable != null && !currentInputTable.trim().isEmpty()) {
            return currentInputTable.trim();
        }
        return "tmp_" + getId();
    }

    private String qualify(String tableName) {
        String name = tableName.trim();
        return name.contains(".") ? name : DUCKDB_SCHEMA + "." + name;
    }

    public void setRules(Object rules) {
        this.rules = rules;
    }

    public void setFilters(Object filters) {
        this.filters = filters;
    }

    public void setFilterLogic(String filterLogic) {
        this.filterLogic = filterLogic;
    }

    public void setSelectColumns(String selectColumns) {
        this.selectColumns = selectColumns;
    }

    public void setOutputTable(String outputTable) {
        this.outputTable = outputTable;
    }

    public String getOutputTable() {
        return outputTable;
    }
}
