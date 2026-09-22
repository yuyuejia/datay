package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Join 组件 - 支持配置多表关联并生成 DuckDB SQL，将结果输出到结果集表中。
 * <p>
 * 上游为流式组件（如 {@link StreamJdbcInput}）时，会先把收到的 FlowFile 按表名写入 DuckDB；
 * 待所有上游完成后，再执行生成的 Join SQL，并把结果集作为 FlowFile 继续发送给下游。
 * <p>
 * 上游若为已物化到 DuckDB 的组件（如 {@link JdbcInput}、{@link SqlUnit}），则直接使用其
 * 已建好的表，Join 组件收到结束信号后执行即可。
 * <p>
 * 配置示例：
 * <pre>
 * {
 *   "outputTable": "join_result",
 *   "selectColumns": "orders.id, users.name",
 *   "fromTable": "orders",
 *   "joins": [
 *     {"type": "LEFT", "table": "users", "leftField": "user_id", "rightField": "id"}
 *   ]
 * }
 * </pre>
 */
@ComponentRegister(
    value = "Join",
    name = "多表关联",
    group = "数据处理",
    desc = "配置多表关联并生成 DuckDB SQL，结果输出到结果集表。支持流式上游先写入 DuckDB，待上游完成后执行 Join。",
    order = 18
)
public class Join extends FlowComponent {

    private static final String DEFAULT_OUTPUT_TABLE = "join_result";
    private static final String DUCKDB_SCHEMA = "main";
    private static final int FETCH_SIZE = 10000;

    // 结果集输出表名
    private String outputTable;

    // 输出字段，为空表示 SELECT *
    private String selectColumns;

    // 主表
    private String fromTable;

    // 关联表配置，元素为 {type, table, leftTable, leftField, rightField}
    private Object joins;

    // 已写入 DuckDB 的输入表元数据缓存
    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    public Join() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 1. 流式接收上游数据，按表名写入 DuckDB
        materialize(flowFile);
        // 2. 等所有上游完成后，再执行 Join
        if (!upstreamFinish) {
            return;
        }
        runJoin();
    }

    @Override
    protected boolean materializesToDuckDB() {
        return true;
    }

    /**
     * 将上游 FlowFile 写入 DuckDB，表名取 FlowFile 携带的表名。
     * <p>
     * 流式组件在表为空时也可能发送空批次（仅携带表元数据），此时仍需建表，保证后续 Join 能引用到该表。
     */
    private void materialize(FlowFile flowFile) {
        JSONArray records = flowFile.getJsonArray();
        boolean hasRecords = records != null && !records.isEmpty();
        boolean hasMetadataColumns = hasMetadataColumns(flowFile);
        if (!hasRecords && !hasMetadataColumns) {
            return;
        }
        String tableName = resolveInputTable(flowFile);
        try (Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            TableMeta targetTable = tableMetaMap.get(tableName);
            if (targetTable == null) {
                if (DBUtils.tableExists(duckdbConn, DUCKDB_SCHEMA, tableName)) {
                    targetTable = DBUtils.getTableMetaData(duckdbConn, DUCKDB_SCHEMA, tableName);
                    logInfo("DuckDB 表已存在，直接追加数据: " + tableName);
                } else {
                    targetTable = buildTargetTableMeta(flowFile, tableName);
                    DBUtils.execute(duckdbConn, DatabaseConverter.generateTableDDL("duckdb", targetTable));
                    logInfo("已创建 DuckDB 表: " + DUCKDB_SCHEMA + "." + tableName);
                }
                tableMetaMap.put(tableName, targetTable);
            }
            if (records != null && !records.isEmpty()) {
                appendRecords(duckdbConn, targetTable, records);
                logInfo("已写入 " + records.size() + " 条记录到 DuckDB 表 " + tableName);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Join 组件写入 DuckDB 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断 FlowFile 是否携带可用于建表的列信息。
     */
    private boolean hasMetadataColumns(FlowFile flowFile) {
        Object meta = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        return meta instanceof TableMeta && !((TableMeta) meta).columns().isEmpty();
    }

    /**
     * 解析 FlowFile 对应的 DuckDB 表名：优先取表属性，其次取表元数据，最后使用临时表名。
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
        return "tmp_" + getId();
    }

    /**
     * 构建写入 DuckDB 的表元数据：优先使用 FlowFile 携带的源表元数据，缺失时根据数据推断。
     */
    private TableMeta buildTargetTableMeta(FlowFile flowFile, String tableName) {
        Object meta = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        TableMeta targetTable;
        if (meta instanceof TableMeta
                && !((TableMeta) meta).columns().isEmpty()
                && ((TableMeta) meta).getDbType() != null) {
            TableMeta sourceTable = (TableMeta) meta;
            targetTable = DatabaseConverter.convert(sourceTable.getDbType(), "duckdb", sourceTable);
            targetTable.setDbType("duckdb");
        } else {
            targetTable = inferTableMeta(flowFile.getJsonArray());
        }
        targetTable.setTable(tableName);
        targetTable.setSchema(DUCKDB_SCHEMA);
        return targetTable;
    }

    /**
     * 根据 JSON 数据推断表结构。
     */
    private TableMeta inferTableMeta(JSONArray records) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setDbType("duckdb");
        JSONObject firstRecord = records.getJSONObject(0);
        Set<String> columnNames = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : firstRecord.entrySet()) {
            columnNames.add(entry.getKey());
        }
        for (String columnName : columnNames) {
            Object value = firstRecord.get(columnName);
            tableMeta.addColumn(new ColumnMeta(columnName, inferColumnType(value)));
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
     * 使用 Appender 批量追加数据到 DuckDB 表。
     */
    private void appendRecords(Connection duckdbConn, TableMeta targetTable, JSONArray records) throws SQLException {
        DuckDBConnection duckDBConnection = (DuckDBConnection) DBUtils.getRawConnection((HikariProxyConnection) duckdbConn);
        try (DuckDBAppender appender = duckDBConnection.createAppender(targetTable.getSchema(), targetTable.getTable())) {
            for (Object o : records) {
                appender.beginRow();
                JSONObject record = (JSONObject) o;
                for (int i = 0; i < targetTable.columns().size(); i++) {
                    ColumnMeta column = targetTable.columns().get(i);
                    Object value = record.get(column.getName());
                    DBUtils.appendValue(appender, column.getType(), value);
                }
                appender.endRow();
            }
        }
    }

    /**
     * 生成 Join SQL 并执行，将结果写入结果集表，再按批次发送给下游。
     */
    private void runJoin() {
        String createTableSql = buildCreateTableSql();
        logInfo("执行 Join SQL: " + createTableSql);
        try (Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            try (Statement stmt = duckdbConn.createStatement()) {
                stmt.execute(createTableSql);
            }
            logInfo("Join 结果已写入表: " + resolveOutputTable());
            if (!getOutput().isEmpty()) {
                emitResult(duckdbConn);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Join SQL 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取结果集表并分批发送 FlowFile 到下游。
     */
    private void emitResult(Connection duckdbConn) throws SQLException {
        String query = "SELECT * FROM " + qualify(resolveOutputTable());
        try (Statement stmt = duckdbConn.createStatement()) {
            stmt.setFetchSize(FETCH_SIZE);
            try (ResultSet rs = stmt.executeQuery(query)) {
                TableMeta outputMeta = DBUtils.getTableMetaFromResultSet(rs.getMetaData());
                outputMeta.setDbType("duckdb");
                outputMeta.setSchema(DUCKDB_SCHEMA);
                outputMeta.setTable(resolveOutputTable());

                JSONArray batch = new JSONArray();
                int total = 0;
                int batchIndex = 0;
                while (rs.next()) {
                    JSONObject record = new JSONObject();
                    for (int i = 1; i <= outputMeta.columns().size(); i++) {
                        record.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                    }
                    batch.add(record);
                    total++;
                    if (batch.size() >= FETCH_SIZE) {
                        batchIndex++;
                        writeResultBatch(outputMeta, batch, batchIndex, false);
                        batch = new JSONArray();
                    }
                }
                if (!batch.isEmpty()) {
                    batchIndex++;
                    writeResultBatch(outputMeta, batch, batchIndex, true);
                }
                logInfo("Join 结果共 " + total + " 条记录，分 " + batchIndex + " 批次发送下游");
            }
        }
    }

    private void writeResultBatch(TableMeta outputMeta, JSONArray batch, int batchIndex, boolean isLastBatch) {
        FlowFile outFile = new FlowFile();
        outFile.setJsonArray(batch);
        outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, outputMeta);
        outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, outputMeta.getTable());
        outFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, DUCKDB_SCHEMA);
        outFile.setAttribute("_batchIndex", batchIndex);
        outFile.setAttribute("_isLastBatch", isLastBatch);
        writeRecords(outFile);
    }

    /**
     * 生成 CREATE OR REPLACE TABLE ... AS SELECT ... 语句。
     */
    String buildCreateTableSql() {
        return "CREATE OR REPLACE TABLE " + qualify(resolveOutputTable()) + " AS " + buildSelectSql();
    }

    /**
     * 根据结构化配置生成 SELECT 语句。
     */
    String buildSelectSql() {
        if (fromTable == null || fromTable.trim().isEmpty()) {
            throw new IllegalArgumentException("Join 组件 fromTable 不能为空");
        }
        StringBuilder sql = new StringBuilder();
        String select = (selectColumns == null || selectColumns.trim().isEmpty()) ? "*" : selectColumns.trim();
        String fromName = fromTable.trim();
        sql.append("SELECT ").append(select).append(" FROM ").append(qualify(fromName));

        for (Object item : resolveJoins()) {
            JSONObject join = toJsonObject(item);
            String table = join.getString("table");
            if (table == null || table.trim().isEmpty()) {
                throw new IllegalArgumentException("Join 组件关联表 table 不能为空");
            }
            String tableName = table.trim();

            String on = join.getString("on");
            if (on == null || on.trim().isEmpty()) {
                on = buildOnClause(join, fromName, tableName);
            }
            if (on == null || on.trim().isEmpty()) {
                throw new IllegalArgumentException("Join 组件关联条件 on 不能为空, table: " + tableName);
            }

            sql.append(" ").append(normalizeJoinType(join.getString("type"))).append(" ").append(qualify(tableName));
            sql.append(" ON ").append(on.trim());
        }
        return sql.toString();
    }

    /**
     * 根据结构化字段生成 ON 条件：仅支持单字段关联，{@code leftTable.leftField = rightTable.rightField}。
     * <p>
     * 兼容旧的 {@code on} 字符串配置：仅当未配置 on 时才会走字段配置。
     */
    private String buildOnClause(JSONObject join, String fromName, String rightTable) {
        String leftField = join.getString("leftField");
        String rightField = join.getString("rightField");
        if (leftField == null || leftField.trim().isEmpty()) {
            throw new IllegalArgumentException("Join 组件关联字段 leftField 不能为空, table: " + rightTable);
        }
        if (rightField == null || rightField.trim().isEmpty()) {
            throw new IllegalArgumentException("Join 组件关联字段 rightField 不能为空, table: " + rightTable);
        }
        String leftTable = join.getString("leftTable");
        if (leftTable == null || leftTable.trim().isEmpty()) {
            leftTable = fromName;
        }
        return simpleName(leftTable) + "." + leftField.trim() + " = " + simpleName(rightTable) + "." + rightField.trim();
    }

    /**
     * 去掉 schema 前缀，取表名作为限定符。
     */
    private String simpleName(String tableName) {
        String name = tableName.trim();
        return name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
    }

    /**
     * 规整关联类型：允许配置 INNER/LEFT/RIGHT/FULL，也允许直接配置 "LEFT JOIN"。
     */
    private String normalizeJoinType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "INNER JOIN";
        }
        String normalized = type.trim().toUpperCase();
        if (!normalized.endsWith("JOIN")) {
            normalized = normalized + " JOIN";
        }
        return normalized;
    }

    /**
     * 解析 joins 配置为 JSONArray，兼容 JSONArray / List / JSON 字符串。
     */
    JSONArray resolveJoins() {
        if (joins == null) {
            return new JSONArray();
        }
        if (joins instanceof JSONArray) {
            return (JSONArray) joins;
        }
        if (joins instanceof String) {
            String text = ((String) joins).trim();
            if (text.isEmpty()) {
                return new JSONArray();
            }
            return JSONArray.parseArray(text);
        }
        if (joins instanceof List) {
            JSONArray array = new JSONArray();
            array.addAll((List<?>) joins);
            return array;
        }
        throw new IllegalArgumentException("Join 组件 joins 配置格式错误: " + joins.getClass());
    }

    private JSONObject toJsonObject(Object item) {
        if (item instanceof JSONObject) {
            return (JSONObject) item;
        }
        if (item instanceof Map) {
            return new JSONObject((Map<String, Object>) item);
        }
        if (item instanceof String) {
            return JSONObject.parseObject((String) item);
        }
        throw new IllegalArgumentException("Join 组件关联表配置格式错误: " + item);
    }

    /**
     * 表名限定：未带 schema 时默认使用 main。
     */
    private String qualify(String tableName) {
        String name = tableName.trim();
        if (name.contains(".")) {
            return name;
        }
        return DUCKDB_SCHEMA + "." + name;
    }

    /**
     * 解析结果集输出表名。
     */
    String resolveOutputTable() {
        if (outputTable != null && !outputTable.trim().isEmpty()) {
            return outputTable.trim();
        }
        return DEFAULT_OUTPUT_TABLE;
    }

    // 自动注入参数的 setters
    public void setOutputTable(String outputTable) {
        this.outputTable = outputTable;
    }

    public String getOutputTable() {
        return outputTable;
    }

    public void setSelectColumns(String selectColumns) {
        this.selectColumns = selectColumns;
    }

    public void setFromTable(String fromTable) {
        this.fromTable = fromTable;
    }

    public void setJoins(Object joins) {
        this.joins = joins;
    }
}
