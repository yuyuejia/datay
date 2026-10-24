package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SqlInput组件 - 支持两种模式：
 * 1. 通过setSql方法直接设置SQL语句
 * 2. 从上游FlowFile中获取SQL语句
 *
 * <p>两种模式都支持动态参数：{@code ${attr:属性名}}、{@code ${属性名}}、
 * {@code ${FLOW_FILE_DATA}} 以及 {@code #{...}} 内置参数。参数值来自当前 FlowFile 的
 * 属性和数据，运行时替换后再执行 SQL。</p>
 */
@ComponentRegister(
    value = "SqlInput",
    name = "SQL输入",
    group = "数据输入",
    desc = "执行自定义 SQL 查询读取数据，SQL 可直接设置或从上游 FlowFile 获取。",
    order = 30
)
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
        // 结束信号只用于通知上游完成，不重复执行 SQL
        if (flowFile != null && flowFile.getAttribute("_end") != null) {
            return;
        }

        String sqlToExecute = resolveSqlToExecute(flowFile);
        if (sqlToExecute == null || sqlToExecute.trim().isEmpty()) {
            logInfo("FlowFile中未找到有效的SQL语句，跳过处理");
            return;
        }
        if (datasource == null) {
            throw new IllegalArgumentException("数据源信息不能为空");
        }

        warnUnresolvedParameters(sqlToExecute);
        String tableName = resolveResultTableName(flowFile);

        try (Connection srcConn = DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword())) {
            // 获取数据库类型
            String sourceDBType = DBUtils.getDBType(datasource.getUrl());

            logInfo("执行SQL语句: " + sqlToExecute);

            // 执行SQL查询并迁移数据
            executeSqlQuery(srcConn, sourceDBType, sqlToExecute, tableName, flowFile);
        } catch (SQLException e) {
            logInfo("SQL查询执行失败: " + e.getMessage());
            throw new RuntimeException("SQL查询执行失败", e);
        }
    }

    /**
     * 解析本次要执行的 SQL。
     *
     * <p>配置了 {@code sql} 时使用配置值，否则从上游 FlowFile 提取完整 SQL；
     * 两种方式都会执行动态参数替换。</p>
     *
     * @param flowFile 当前 FlowFile，提供动态参数取值
     * @return 替换参数后的 SQL；没有可用 SQL 时返回 {@code null}
     */
    String resolveSqlToExecute(FlowFile flowFile) {
        String resolved;
        if (sql == null || sql.trim().isEmpty()) {
            resolved = extractSqlFromFlowFile(flowFile);
            if (resolved == null || resolved.trim().isEmpty()) {
                return null;
            }
        } else {
            resolved = sql;
        }
        return applyDynamicParameters(resolved, flowFile);
    }

    /**
     * 使用当前 FlowFile 的属性/数据替换 SQL 中的动态参数。
     *
     * <p>支持 {@code ${attr:属性名}}、{@code ${属性名}}、{@code ${FLOW_FILE_DATA}}
     * 以及 {@code #{...}} 内置参数。当上游只传入一行数据时，{@code ${属性名}}
     * 也可以直接引用该行字段；参数替换失败时保留原 SQL，避免影响原有任务。</p>
     *
     * @param text     原始文本
     * @param flowFile 当前 FlowFile
     * @return 替换后的文本
     */
    String applyDynamicParameters(String text, FlowFile flowFile) {
        if (text == null || flowFile == null) {
            return text;
        }
        try {
            // 先替换属性、FLOW_FILE_DATA 和内置参数，保证 ${attr:...} 只取属性
            String replaced = ParameterUtil.replaceParameters(text, flowFile);
            Map<String, Object> variables = buildDynamicVariables(flowFile);
            if (variables == null || variables.isEmpty()) {
                return replaced;
            }
            // 再用单行数据字段补齐仍未被替换的 ${字段名}
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                replaced = replaced.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
            return replaced;
        } catch (Exception e) {
            logWarn("SQL 动态参数替换失败，按原 SQL 执行: " + e.getMessage());
            return text;
        }
    }

    /**
     * 构建单行数据变量，供 {@code ${字段名}} 引用。
     *
     * <p>SqlInput 按 FlowFile 整体执行 SQL，无法确定多行数据中应使用哪一行，
     * 因此仅在 FlowFile 为单个 JSON 对象或只包含一行 JSON 数组时提供变量。
     * 属性仍可通过 {@code ${attr:属性名}} 引用。</p>
     *
     * @param flowFile 当前 FlowFile
     * @return 单行字段变量；无法确定唯一行时返回 {@code null}
     */
    private Map<String, Object> buildDynamicVariables(FlowFile flowFile) {
        if (flowFile == null) {
            return null;
        }
        JSONObject row = flowFile.getJsonObject();
        if (row == null) {
            JSONArray rows = flowFile.getJsonArray();
            if (rows != null && rows.size() == 1 && rows.get(0) instanceof JSONObject) {
                row = rows.getJSONObject(0);
            }
        }
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new HashMap<>(row);
    }

    /**
     * 解析查询结果使用的表名。
     *
     * <p>配置 SQL 时固定为 {@code sql_query_result}；上游 SQL 优先使用 FlowFile 的
     * {@code _table} 属性，缺失时回退为 {@code sql_query_result}，避免空指针。</p>
     *
     * @param flowFile 当前 FlowFile
     * @return 结果表名
     */
    String resolveResultTableName(FlowFile flowFile) {
        if (sql != null && !sql.trim().isEmpty()) {
            return "sql_query_result";
        }
        Object tableAttr = flowFile == null ? null : flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE);
        return tableAttr == null || tableAttr.toString().trim().isEmpty()
            ? "sql_query_result"
            : tableAttr.toString().trim();
    }

    /**
     * 提示仍未替换的动态参数，便于排查上游属性缺失或参数名写错。
     */
    private void warnUnresolvedParameters(String sqlToExecute) {
        if (sqlToExecute != null && (sqlToExecute.contains("${") || sqlToExecute.contains("#{"))) {
            logWarn("SQL 中仍存在未替换的动态参数，请检查上游 FlowFile 的属性/数据: " + sqlToExecute);
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