package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ComponentRegister("StreamJdbcInput")
public class StreamJdbcInput extends FlowComponent {

    private String schema;
    private String table;
    private String incrColumn;
    private String where;

    private DatasourceInfo datasource;

    // 添加流式读取配置
    private static final int FETCH_SIZE = 10000;

    // 增量同步状态存储（多表支持）
    private final Map<String, Object> lastIncrValues = new HashMap<>();

    public StreamJdbcInput() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 解析多表配置
        List<String> tables = parseTables();
        Map<String, String> incrColumns = parseIncrColumns();

        // 加载增量同步状态
        loadIncrStatus(incrColumns);

        try (Connection srcConn = DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword())) {
            // 循环处理每个表
            for (String tableName : tables) {
                try {
                    // 获取源表元数据
                    String sourceDBType = DBUtils.getDBType(datasource.getUrl());
                    // 使用schema参数，如果为空则使用数据源的dbschema
                    String targetSchema = (schema != null && !schema.trim().isEmpty()) ? schema : datasource.getDbschema();
                    TableMeta srcTable = DBUtils.getTableMetaData(srcConn, targetSchema, tableName.trim());
                    srcTable.setDbType(sourceDBType);

                    // 获取当前表的增量字段
                    String tableIncrColumn = incrColumns.get(tableName.trim());

                    migrateData(srcConn, srcTable, tableIncrColumn);

                } catch (SQLException e) {
                    logInfo("处理表 " + tableName + " 时发生错误: " + e.getMessage());
                    throw new RuntimeException("数据迁移失败", e);
                }
            }
        } catch (SQLException e) {
            logInfo("数据库连接失败: " + e.getMessage());
            throw new RuntimeException("数据迁移失败", e);
        }
    }

    /**
     * 解析多表配置
     * 当table为空但schema不为空时，获取当前schema下的所有表进行同步
     */
    private List<String> parseTables() {
        // 如果table为空但schema不为空，获取schema下的所有表
        if ((table == null || table.trim().isEmpty()) &&
            (schema != null && !schema.trim().isEmpty())) {
            try (Connection conn = DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword())) {
                List<TableMeta> tableList = DBUtils.getTableList(conn, schema);
                if (tableList.isEmpty()) {
                    throw new IllegalArgumentException("schema " + schema + " 下没有找到任何表");
                }
                ArrayList<String> tables = new ArrayList<>();
                for (TableMeta tableMeta : tableList) {
                    String tableName = tableMeta.getTable();
                    //如果表名已backup结尾，则跳过
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

        // 原有的table参数处理逻辑
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("table参数不能为空");
        }
        String[] tables = table.split(",");
        if (tables.length == 0) {
            throw new IllegalArgumentException("table参数格式错误，应包含至少一个表名");
        }
        return List.of(tables);
    }

    /**
     * 解析增量字段配置（JSON格式）
     * 格式示例：{"table1": "id", "table2": "update_time"}
     */
    private Map<String, String> parseIncrColumns() {
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
                List<String> tables = parseTables();
                if (tables.size() == 1) {
                    incrColumns.put(tables.get(0).trim(), incrColumn.trim());
                } else {
                    logInfo("警告：多表同步时incrColumn应为JSON格式，当前使用默认配置");
                }
            }
        }
        return incrColumns;
    }

    /**
     * 加载增量同步状态
     */
    private void loadIncrStatus(Map<String, String> incrColumns) {
        for (String tableName : incrColumns.keySet()) {
            String statusKey = "lastIncrValue." + tableName;
            Object lastValue = getStatus(statusKey);
            if (lastValue != null) {
                lastIncrValues.put(tableName, lastValue);
                logInfo("加载表 " + tableName + " 的上次增量值: " + lastValue);
            }
        }
    }

    private void migrateData(Connection srcConn, TableMeta srcTable, String tableIncrColumn) throws SQLException {
        String srcQuery = buildQuery(srcTable, tableIncrColumn);
        logInfo("执行查询: " + srcQuery);

        try (Statement srcStmt = DBUtils.createStreamStatement(srcTable.getDbType(), srcConn)) {
            try (ResultSet rs = srcStmt.executeQuery(srcQuery)) {
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

                    // 每FETCH_SIZE条记录作为一组发送到队列
                    if (batchCount % FETCH_SIZE == 0) {
                        sendBatchRecords(batchRecords, srcTable);
                        batchRecords.clear();
                        logInfo("已发送 " + batchCount + " 条记录到队列, table: " + srcTable.getTable());
                    }
                }

                // 发送剩余记录,如果记录为 0 条,也发送一个空数据事件
                if (!batchRecords.isEmpty() || (batchCount == 0)) {
                    sendBatchRecords(batchRecords, srcTable);
                }

                logInfo("表 " + srcTable.getTable() + " 同步完成，共处理 " + batchCount + " 条记录");
            }
        }
    }

    /**
     * 根据增量字段的 JDBC 类型比较两个值的大小，用于确定最大值。
     * <p>整数、浮点、定点、时间类型分别走对应的快速比较，避免统一 toString 后字符串/字典序比较导致的
     * 错误（如「99999 > 100000」）以及每行重复的字符串解析开销。
     */
    private int compareIncrValue(Object a, Object b, int sqlType) {
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
     * 构建查询语句，支持增量同步（多表版本）
     */
    private String buildQuery(TableMeta srcTable, String tableIncrColumn) {
        StringBuilder query = new StringBuilder();
        query.append("/* bip_streaming_export */ SELECT * FROM ").append(srcTable.getSchema()).append(".").append(srcTable.getTable());

        // 如果where条件不为空，添加where条件
        if (where != null && !where.trim().isEmpty()) {
            query.append(" WHERE ").append(where);
        }

        // 如果增量字段不为空，添加增量条件
        if (tableIncrColumn != null && !tableIncrColumn.trim().isEmpty()) {
            Object lastIncrValue = lastIncrValues.get(srcTable.getTable());
            if (lastIncrValue != null) {
                if (where != null && !where.trim().isEmpty()) {
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

    /**
     * 发送批量记录到队列
     */
    private void sendBatchRecords(List<JSONObject> batchRecords, TableMeta srcTable) {
        JSONArray jsonRecords = new JSONArray();
        jsonRecords.addAll(batchRecords);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonRecords);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, srcTable);
        // 添加表名标识，便于后续处理
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, srcTable.getTable());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, srcTable.getSchema());
        // 保存当前表的增量状态
        Object lastValue = lastIncrValues.get(srcTable.getTable());
        if (lastValue != null) {
            flowFile.setStatus(getId() + "." + "lastIncrValue." + srcTable.getTable(), lastValue.toString());
        }
        writeRecords(flowFile);
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

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}