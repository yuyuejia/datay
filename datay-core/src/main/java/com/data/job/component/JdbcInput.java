package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JdbcInputDuckDB组件 - 支持通过JDBC方式从源数据库读取数据并写入到DuckDB中
 * 该组件结合了JdbcInput的数据读取功能和DuckDBWrite的数据写入功能
 */
@ComponentRegister("JdbcInput")
public class JdbcInput extends FlowComponent {

    // 源数据库配置
    private String table;
    private String incrColumn;
    private String where;
    private DatasourceInfo sourceId;
    private String ytenantId;  // 新增ytenantId属性

    // 流式读取配置
    private static final int FETCH_SIZE = 10000;

    // 增量同步状态存储（多表支持）
    private final Map<String, Object> lastIncrValues = new HashMap<>();

    // DuckDB表元数据缓存
    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    public JdbcInput() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 解析多表配置
        String[] tables = parseTables();
        Map<String, String> incrColumns = parseIncrColumns();

        // 加载增量同步状态
        loadIncrStatus(incrColumns);

        try (Connection srcConn = DBUtils.getConnection(sourceId.getUrl(), sourceId.getUsername(), sourceId.getPassword());
             Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            
            // 循环处理每个表
            for (String tableName : tables) {
                try {
                    // 获取源表元数据
                    String sourceDBType = DBUtils.getDBType(sourceId.getUrl());
                    TableMeta srcTable = DBUtils.getTableMetaData(srcConn, sourceId.getDbschema(), tableName.trim());
                    srcTable.setDbType(sourceDBType);

                    // 获取当前表的增量字段
                    String tableIncrColumn = incrColumns.get(tableName.trim());

                    // 迁移数据到DuckDB
                    migrateData(srcConn, duckdbConn, srcTable, tableIncrColumn);

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
     */
    private String[] parseTables() {
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("table参数不能为空");
        }
        String[] tables = table.split(",");
        if (tables.length == 0) {
            throw new IllegalArgumentException("table参数格式错误，应包含至少一个表名");
        }
        return tables;
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
                String[] tables = parseTables();
                if (tables.length == 1) {
                    incrColumns.put(tables[0].trim(), incrColumn.trim());
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

    /**
     * 迁移数据到DuckDB
     */
    private void migrateData(Connection srcConn, Connection duckdbConn, TableMeta srcTable, String tableIncrColumn) throws SQLException {
        String srcQuery = buildQuery(srcTable, tableIncrColumn);
        logInfo("执行查询: " + srcQuery);

        // 获取目标表元数据
        TableMeta targetTable = getTargetTableMeta(srcTable, duckdbConn);

        try (Statement srcStmt = DBUtils.createStreamStatement(srcTable.getDbType(), srcConn)) {
            try (ResultSet rs = srcStmt.executeQuery(srcQuery)) {
                int totalCount = 0;
                List<JSONObject> batchRecords = new ArrayList<>();
                Object currentTableLastIncrValue = lastIncrValues.get(srcTable.getTable());

                while (rs.next()) {
                    JSONObject jsonRecord = new JSONObject();
                    for (int i = 1; i <= srcTable.columns().size(); i++) {
                        String columnName = rs.getMetaData().getColumnName(i);
                        Object value = rs.getObject(i);
                        jsonRecord.put(columnName, value);

                        // 记录增量字段的最大值
                        if (tableIncrColumn != null && tableIncrColumn.equals(columnName)) {
                            if (currentTableLastIncrValue == null || value.toString().compareTo(currentTableLastIncrValue.toString()) > 0) {
                                currentTableLastIncrValue = value;
                                lastIncrValues.put(srcTable.getTable(), currentTableLastIncrValue);
                            }
                        }
                    }
                    batchRecords.add(jsonRecord);
                    totalCount++;

                    // 每FETCH_SIZE条记录作为一组写入DuckDB
                    if (batchRecords.size() >= FETCH_SIZE) {
                        writeBatchToDuckDB(duckdbConn, targetTable, batchRecords);
                        batchRecords.clear();
                        logInfo(srcTable.getTable() + "已写入 " + totalCount + " 条记录到DuckDB表 " + targetTable.getTable());
                    }

                    // 调试模式：达到采样行数上限后停止读取
                    if (getContext() != null && getContext().isDebugMode() && totalCount >= getContext().getDebugRowLimit()) {
                        logInfo("调试模式：表 " + srcTable.getTable() + " 达到采样行数上限 " + getContext().getDebugRowLimit());
                        break;
                    }
                }

                // 写入剩余记录
                if (!batchRecords.isEmpty()) {
                    writeBatchToDuckDB(duckdbConn, targetTable, batchRecords);
                }

                logInfo("表 " + srcTable.getTable() + " 同步完成，共处理 " + totalCount + " 条记录");

                // 保存增量状态
                if (currentTableLastIncrValue != null) {
                    String statusKey = "lastIncrValue." + srcTable.getTable();
                    getContext().getStatusMap().put(getId() + "." + statusKey, currentTableLastIncrValue.toString());
                    logInfo("保存表 " + srcTable.getTable() + " 的增量状态: " + currentTableLastIncrValue);
                }
            }
        }
    }

    /**
     * 构建查询语句，支持增量同步和ytenantId替换
     */
    private String buildQuery(TableMeta srcTable, String tableIncrColumn) {
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
     * 获取目标表元数据
     */
    private synchronized TableMeta getTargetTableMeta(TableMeta srcTable, Connection duckdbConn) throws SQLException {
        String targetTableName = getOutputTableName(0);
        TableMeta targetTable = this.tableMetaMap.get(targetTableName);
        
        if (targetTable == null) {
            if (!DBUtils.tableExists(duckdbConn, "main", targetTableName)) {
                // 如果表不存在，根据源表元数据创建表
                targetTable = DatabaseConverter.convert(srcTable.getDbType(), "duckdb", srcTable);
                targetTable.setDbType("duckdb");
                targetTable.setTable(targetTableName);
                targetTable.setSchema("main");
                
                // 生成DDL并创建表
                String ddl = DatabaseConverter.generateTableDDL("duckdb", targetTable);
                DBUtils.execute(duckdbConn, ddl);
                logInfo("已成功创建DuckDB表 " + targetTableName);
            } else {
                // 表已存在，获取表元数据
                logInfo("表存在，获取表元数据 " + targetTableName);
                targetTable = DBUtils.getTableMetaData(duckdbConn, "main", targetTableName);
                logInfo("清空表 " + targetTableName);
                DBUtils.execute(duckdbConn, String.format("TRUNCATE TABLE %s.%s", targetTable.getSchema(), targetTableName));
            }

            this.tableMetaMap.put(targetTableName, targetTable);
        }

        return targetTable;
    }

    /**
     * 批量写入数据到DuckDB
     */
    private void writeBatchToDuckDB(Connection duckdbConn, TableMeta targetTable, List<JSONObject> batchRecords) throws SQLException {
        DuckDBConnection duckDBConnection = (DuckDBConnection) DBUtils.getRawConnection((HikariProxyConnection) duckdbConn);
        
        try (DuckDBAppender appender = duckDBConnection.createAppender(targetTable.getSchema(), targetTable.getTable())) {
            for (JSONObject record : batchRecords) {
                appender.beginRow();
                for (int i = 0; i < targetTable.columns().size(); ++i) {
                    ColumnMeta column = targetTable.columns().get(i);
                    String fieldType = column.getType();
                    Object value = record.get(column.getName());
                    DBUtils.appendValue(appender, fieldType, value);
                }
                appender.endRow();
            }
        }
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

    public void setSourceId(DatasourceInfo sourceId) {
        this.sourceId = sourceId;
    }

    // 新增ytenantId的setter方法
    public void setYtenantId(String ytenantId) {
        this.ytenantId = ytenantId;
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