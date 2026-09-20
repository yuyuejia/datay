package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC 输入组件抽象基类。
 * <p>
 * 负责源库流式读取的公共逻辑：多表解析（含 schema 全表发现）、增量字段与状态、查询构建、
 * 只进游标流式读取与分批、增量值类型化比较。子类只需通过 {@link #emitBatch} 决定每批记录的去向
 * （下沉为 FlowFile 或物化到 DuckDB），并按需覆写增量状态持久化与物化资源准备。
 */
public abstract class AbstractJdbcInput extends FlowComponent {

    protected String schema;
    protected String table;
    protected String incrColumn;
    protected String where;
    protected String ytenantId;
    protected DatasourceInfo datasource;

    // 流式读取配置
    protected static final int FETCH_SIZE = 10000;

    // 增量同步状态存储（多表支持）
    protected final Map<String, Object> lastIncrValues = new HashMap<>();

    protected AbstractJdbcInput() {
        setType(ComponentType.SOURCE);
    }

    /**
     * 子类实现：处理一批记录。
     */
    protected abstract void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) throws SQLException;

    /**
     * 子类实现：读取完成后持久化增量状态。默认不处理（由子类在下沉数据时随 FlowFile 携带状态）。
     */
    protected void persistIncrStatus(TableMeta srcTable, Object lastIncrValue) {
    }

    /**
     * 无数据时是否发送一个空批次。流式模式需要，以便下游感知表边界；物化模式不需要。
     */
    protected boolean emitEmptyBatch() {
        return false;
    }

    /**
     * 处理开始前的钩子，便于子类准备额外资源（如打开 DuckDB 连接）。
     */
    protected void beforeProcess() throws SQLException {
    }

    /**
     * 处理结束后的钩子，便于子类释放额外资源。
     */
    protected void afterProcess() throws SQLException {
    }

    /**
     * 读取单表前的钩子，便于子类准备目标资源（如解析 DuckDB 目标表元数据）。
     */
    protected void beforeMigrateTable(TableMeta srcTable) throws SQLException {
    }

    @Override
    public void execute(FlowFile flowFile) {
        List<String> tables = parseTables();
        Map<String, String> incrColumns = parseIncrColumns(tables);

        // 加载增量同步状态
        loadIncrStatus(incrColumns);

        try (Connection srcConn = getSourceConnection()) {
            beforeProcess();
            try {
                for (String tableName : tables) {
                    try {
                        String sourceDBType = DBUtils.getDBType(datasource.getUrl());
                        TableMeta srcTable = DBUtils.getTableMetaData(srcConn, resolveSchema(), tableName.trim());
                        srcTable.setDbType(sourceDBType);

                        String tableIncrColumn = incrColumns.get(tableName.trim());

                        beforeMigrateTable(srcTable);
                        migrateData(srcConn, srcTable, tableIncrColumn);
                    } catch (SQLException e) {
                        logInfo("处理表 " + tableName + " 时发生错误: " + e.getMessage());
                        throw new RuntimeException("数据迁移失败", e);
                    }
                }
            } finally {
                afterProcess();
            }
        } catch (SQLException e) {
            logInfo("数据库连接失败: " + e.getMessage());
            throw new RuntimeException("数据迁移失败", e);
        }
    }

    /**
     * 打开源数据库连接。
     */
    protected Connection getSourceConnection() throws SQLException {
        return DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword());
    }

    /**
     * 解析本次读取使用的 schema：优先使用配置的 schema，否则回退到数据源的 dbschema。
     */
    protected String resolveSchema() {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema;
        }
        return datasource.getDbschema();
    }

    /**
     * 解析多表配置。
     * 当 table 为空但 schema 不为空时，获取当前 schema 下的所有表进行同步。
     */
    protected List<String> parseTables() {
        if ((table == null || table.trim().isEmpty())
                && schema != null && !schema.trim().isEmpty()) {
            try (Connection conn = getSourceConnection()) {
                List<TableMeta> tableList = DBUtils.getTableList(conn, schema);
                if (tableList.isEmpty()) {
                    throw new IllegalArgumentException("schema " + schema + " 下没有找到任何表");
                }
                List<String> tables = new ArrayList<>();
                for (TableMeta tableMeta : tableList) {
                    String tableName = tableMeta.getTable();
                    // 备份表、临时表不参与同步
                    if (tableName.endsWith("_backup") || tableName.endsWith("_tmp")) {
                        continue;
                    }
                    tables.add(tableName);
                }
                logInfo("获取schema " + schema + " 下的所有表，共 " + tables.size() + " 个表.");
                return tables;
            } catch (SQLException e) {
                throw new RuntimeException("获取schema下表列表失败: " + e.getMessage(), e);
            }
        }

        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("table参数不能为空");
        }
        String[] tables = table.split(",");
        if (tables.length == 0) {
            throw new IllegalArgumentException("table参数格式错误，应包含至少一个表名");
        }
        List<String> result = new ArrayList<>(tables.length);
        for (String tableName : tables) {
            result.add(tableName.trim());
        }
        return result;
    }

    /**
     * 解析增量字段配置（JSON格式）。
     * 格式示例：{"table1": "id", "table2": "update_time"}
     */
    protected Map<String, String> parseIncrColumns(List<String> tables) {
        Map<String, String> incrColumns = new HashMap<>();
        if (incrColumn != null && !incrColumn.trim().isEmpty()) {
            try {
                // 尝试解析为JSON
                JSONObject json = JSONObject.parseObject(incrColumn);
                for (String key : json.keySet()) {
                    incrColumns.put(key, json.getString(key));
                }
            } catch (Exception e) {
                // 如果不是JSON格式，则按旧方式处理（单表）
                if (tables.size() == 1) {
                    incrColumns.put(tables.get(0), incrColumn.trim());
                } else {
                    logInfo("警告：多表同步时incrColumn应为JSON格式，当前使用默认配置");
                }
            }
        }
        return incrColumns;
    }

    /**
     * 加载增量同步状态。
     */
    protected void loadIncrStatus(Map<String, String> incrColumns) {
        for (String tableName : incrColumns.keySet()) {
            String statusKey = "lastIncrValue." + tableName;
            Object lastValue = getStatus(statusKey);
            if (lastValue != null) {
                lastIncrValues.put(tableName, lastValue);
                logInfo("加载表 " + tableName + " 的上次增量值: " + lastValue);
            }
        }
    }

    /**
     * 流式读取单表数据并分批交给子类处理。
     */
    protected void migrateData(Connection srcConn, TableMeta srcTable, String tableIncrColumn) throws SQLException {
        String srcQuery = buildQuery(srcTable, tableIncrColumn);
        logInfo("执行查询: " + srcQuery);

        try (Statement srcStmt = DBUtils.createStreamStatement(srcTable.getDbType(), srcConn);
             ResultSet rs = srcStmt.executeQuery(srcQuery)) {
            int batchCount = 0;
            List<JSONObject> batchRecords = new ArrayList<>();
            Object currentTableLastIncrValue = lastIncrValues.get(srcTable.getTable());

            while (rs.next()) {
                JSONObject jsonRecord = new JSONObject();
                for (int i = 1; i <= srcTable.columns().size(); i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    int incrColumnSqlType = rs.getMetaData().getColumnType(i);
                    Object value = rs.getObject(i);
                    jsonRecord.put(columnName, value);

                    // 记录增量字段的最大值
                    if (tableIncrColumn != null && tableIncrColumn.equals(columnName)) {
                        if (currentTableLastIncrValue == null || compareIncrValue(value, currentTableLastIncrValue, incrColumnSqlType) > 0) {
                            currentTableLastIncrValue = value;
                            lastIncrValues.put(srcTable.getTable(), currentTableLastIncrValue);
                        }
                    }
                }
                batchRecords.add(jsonRecord);
                batchCount++;

                // 每 FETCH_SIZE 条记录作为一组交给子类处理
                if (batchCount % FETCH_SIZE == 0) {
                    emitBatch(batchRecords, srcTable);
                    batchRecords.clear();
                    logInfo("已处理 " + batchCount + " 条记录, table: " + srcTable.getTable());
                }

                // 调试模式：达到采样行数上限后停止读取
                if (getContext() != null && getContext().isDebugMode() && batchCount >= getContext().getDebugRowLimit()) {
                    logInfo("调试模式：表 " + srcTable.getTable() + " 达到采样行数上限 " + getContext().getDebugRowLimit());
                    break;
                }
            }

            // 处理剩余记录；流式模式下记录为 0 时也发送一个空数据事件
            if (!batchRecords.isEmpty() || (emitEmptyBatch() && batchCount == 0)) {
                emitBatch(batchRecords, srcTable);
            }

            logInfo("表 " + srcTable.getTable() + " 同步完成，共处理 " + batchCount + " 条记录");

            // 持久化增量状态
            if (currentTableLastIncrValue != null) {
                persistIncrStatus(srcTable, currentTableLastIncrValue);
            }
        }
    }

    /**
     * 根据增量字段的 JDBC 类型比较两个值的大小，用于确定最大值。
     * <p>
     * 整数、浮点、定点、时间类型分别走对应的快速比较，避免统一 toString 后字符串/字典序比较导致的
     * 错误（如「99999 > 100000」）以及每行重复的字符串解析开销。
     */
    protected int compareIncrValue(Object a, Object b, int sqlType) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }

        switch (sqlType) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                return Long.compare(toLong(a), toLong(b));
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
                return Double.compare(toDouble(a), toDouble(b));
            case Types.NUMERIC:
            case Types.DECIMAL:
                return toBigDecimal(a).compareTo(toBigDecimal(b));
            case Types.DATE:
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return Long.compare(toEpochMillis(a), toEpochMillis(b));
            default:
                return a.toString().compareTo(b.toString());
        }
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
    }

    private long toEpochMillis(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).getTime();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).getTime();
        }
        if (value instanceof java.sql.Time) {
            return ((java.sql.Time) value).getTime();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).getTime();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * 构建查询语句，支持增量同步与 ytenantId 占位符替换（多表版本）。
     */
    protected String buildQuery(TableMeta srcTable, String tableIncrColumn) {
        StringBuilder query = new StringBuilder();
        query.append("/* bip_streaming_export */ SELECT * FROM ").append(srcTable.getSchema()).append(".").append(srcTable.getTable());

        // 处理where条件，替换${ytenantid}为实际的ytenantId值
        String processedWhere = where;
        if (where != null && ytenantId != null) {
            processedWhere = where.replace("${ytenantid}", ytenantId);
        }

        // 如果where条件不为空，添加where条件
        if (processedWhere != null && !processedWhere.trim().isEmpty()) {
            query.append(" WHERE ").append(processedWhere);
        }

        // 如果增量字段不为空，添加增量条件
        if (tableIncrColumn != null && !tableIncrColumn.trim().isEmpty()) {
            Object lastIncrValue = lastIncrValues.get(srcTable.getTable());
            if (lastIncrValue != null) {
                if (processedWhere != null && !processedWhere.trim().isEmpty()) {
                    query.append(" AND ");
                } else {
                    query.append(" WHERE ");
                }
                // 如果上次有保存的增量值，查询大于该值的记录
                if (lastIncrValue instanceof String) {
                    query.append(tableIncrColumn).append(" > '").append(lastIncrValue).append("'");
                } else {
                    query.append(tableIncrColumn).append(" > ").append(lastIncrValue);
                }
                logInfo("表 " + srcTable.getTable() + " 使用增量同步，查询条件: " + tableIncrColumn + " > " + lastIncrValue);
            } else {
                // 第一次执行，查询所有记录
                logInfo("表 " + srcTable.getTable() + " 首次增量同步，查询所有记录");
            }
        }

        return query.toString();
    }

    // 自动注入参数的setters
    public void setTable(String table) {
        this.table = table;
    }

    public void setIncrColumn(String incrColumn) {
        this.incrColumn = incrColumn;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSourceId(DatasourceInfo sourceId) {
        this.datasource = sourceId;
    }

    public void setYtenantId(String ytenantId) {
        this.ytenantId = ytenantId;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getSchema() {
        return schema;
    }

    /**
     * 设置上次增量值（用于测试或手动设置）- 多表版本
     */
    public void setLastIncrValue(String tableName, Object lastIncrValue) {
        this.lastIncrValues.put(tableName, lastIncrValue);
    }

    /**
     * 获取上次增量值 - 多表版本
     */
    public Object getLastIncrValue(String tableName) {
        return lastIncrValues.get(tableName);
    }

    /**
     * 获取所有表的增量状态
     */
    public Map<String, Object> getLastIncrValues() {
        return new HashMap<>(lastIncrValues);
    }
}
