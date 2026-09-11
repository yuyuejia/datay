package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SqlInput组件 - 支持两种模式：
 * 1. 通过setSql方法直接设置SQL语句
 * 2. 从上游FlowFile中获取SQL语句
 */
@ComponentRegister("SqlInput")
public class SqlInput extends FlowComponent {

    private String sql;
    private DatasourceInfo datasource;

    // 添加流式读取配置
    private static final int FETCH_SIZE = 10000;

    public SqlInput() {
        // 默认为Source类型，但会根据实际使用情况自动调整
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        
        try (Connection srcConn = DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword())) {
            // 获取数据库类型
            String sourceDBType = DBUtils.getDBType(datasource.getUrl());

            String sqlToExecute;
            String tableName;

            if (sql == null || sql.trim().isEmpty()) {
                sqlToExecute = extractSqlFromFlowFile(flowFile);
                if (sqlToExecute == null || sqlToExecute.trim().isEmpty()) {
                    logInfo("FlowFile中未找到有效的SQL语句，跳过处理");
                    return;
                }
                tableName = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE).toString();
            } else {
                // Source模式：使用直接设置的SQL
                sqlToExecute = sql;
                tableName = "sql_query_result";
            }

            logInfo("执行SQL语句: " + sqlToExecute);

            // 执行SQL查询并迁移数据
            executeSqlQuery(srcConn, sourceDBType, sqlToExecute, tableName, flowFile);
        } catch (SQLException e) {
            logInfo("SQL查询执行失败: " + e.getMessage());
            throw new RuntimeException("SQL查询执行失败", e);
        }
    }

    /**
     * 从FlowFile中提取SQL语句
     */
    private String extractSqlFromFlowFile(FlowFile flowFile) {
        if (flowFile == null) {
            return null;
        }

        // 检查是否为结束信号
        if (flowFile.getAttribute("_end") != null) {
            return null;
        }

        // 检查是否为开始信号
        if (flowFile.getAttribute("_start") != null) {
            logInfo("接收到开始信号，等待SQL语句");
            return null;
        }

        // 优先从文本数据中获取SQL
        String textData = flowFile.getTextData();
        if (textData != null && !textData.trim().isEmpty()) {
            return textData.trim();
        }

        // 从属性中获取SQL
        Object sqlAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_SQL);
        if (sqlAttr != null) {
            return sqlAttr.toString().trim();
        }

        // 从查询属性中获取SQL
        Object queryAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_QUERY);
        if (queryAttr != null) {
            return queryAttr.toString().trim();
        }

        return null;
    }

    /**
     * 执行SQL查询并处理结果集
     */
    private void executeSqlQuery(Connection srcConn, String sourceDBType, String sql, String tableName, FlowFile flowFile) throws SQLException {
        try (Statement srcStmt = DBUtils.createStreamStatement(sourceDBType, srcConn)) {

            try (ResultSet rs = srcStmt.executeQuery(sql)) {
                // 获取结果集元数据
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 构建表元数据
                TableMeta resultTableMeta = DBUtils.getTableMetaFromResultSet(metaData);
                resultTableMeta.setTable(tableName);
                resultTableMeta.setDbType(sourceDBType);
                if (flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA) != null) {
                    resultTableMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
                    resultTableMeta.setTable(tableName);
                    resultTableMeta.setDbType(sourceDBType);
                }
                int batchCount = 0;
                List<JSONObject> batchRecords = new ArrayList<>();

                while (rs.next()) {
                    JSONObject jsonRecord = new JSONObject();

                    // 遍历所有列
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        jsonRecord.put(columnName, value);
                    }

                    batchRecords.add(jsonRecord);
                    batchCount++;

                    // 每FETCH_SIZE条记录作为一组发送到队列
                    if (batchCount % FETCH_SIZE == 0) {
                        sendBatchRecords(batchRecords, resultTableMeta);
                        batchRecords.clear();
                        logInfo("已发送 " + batchCount + " 条记录到队列, 表名: " + tableName);
                    }

                    // 调试模式：达到采样行数上限后停止读取
                    if (getContext() != null && getContext().isDebugMode() && batchCount >= getContext().getDebugRowLimit()) {
                        logInfo("调试模式：达到采样行数上限 " + getContext().getDebugRowLimit());
                        break;
                    }
                }

                // 发送剩余记录
                if (!batchRecords.isEmpty()) {
                    sendBatchRecords(batchRecords, resultTableMeta);
                    logInfo("已发送剩余 " + batchRecords.size() + " 条记录到队列, 表名: " + tableName);
                    batchRecords.clear();
                }

                // 发送剩余记录,如果记录为 0 条,也发送一个空数据事件
                if (batchCount == 0) {
                    sendBatchRecords(batchRecords, resultTableMeta);
                }

                logInfo("SQL查询执行完成，共处理 " + batchCount + " 条记录, 表名: " + tableName);
            }
        }
    }

    /**
     * 发送批量记录到队列
     */
    private void sendBatchRecords(List<JSONObject> batchRecords, TableMeta tableMeta) {
        JSONArray jsonRecords = new JSONArray();
        jsonRecords.addAll(batchRecords);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonRecords);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        writeRecords(flowFile);
    }

    // 自动注入参数的setters
    public void setSql(String sql) {
        this.sql = sql;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    /**
     * 获取SQL语句
     */
    public String getSql() {
        return sql;
    }

    /**
     * 获取数据源信息
     */
    public DatasourceInfo getDatasource() {
        return datasource;
    }
}