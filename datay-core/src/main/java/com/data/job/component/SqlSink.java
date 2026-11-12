package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldOption;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentFieldValueFormat;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 SQL 写入组件。
 *
 * <p>组件不生成数据库方言 SQL，而是执行用户配置的单条 DML 模板。模板中的
 * {@code ${字段名}}、{@code ${attr:属性名}}、{@code ${FLOW_FILE_DATA}}、
 * {@code ${ROW_DATA}} 以及 {@code #{...}} 会在运行时解析为 JDBC {@code ?} 占位符并通过
 * {@link PreparedStatement} 绑定，因此字符串和日期不需要手动加引号。绑定后的 SQL 可以通过
 * {@code INSERT ... ON DUPLICATE KEY UPDATE col = col + VALUES(col)}
 * （MySQL）、{@code INSERT ... ON CONFLICT ... DO UPDATE}（PostgreSQL/DuckDB）、
 * {@code MERGE}（Oracle/SQL Server）等语句实现汇总表的原子累加。
 */
@ComponentRegister(
    value = "SqlSink",
    name = "SQL 写入",
    group = "数据输出",
    desc = "按 SQL 模板执行 DML，支持动态参数 JDBC 绑定与 INSERT ... ON DUPLICATE KEY UPDATE、ON CONFLICT、MERGE 等原子累加场景。",
    order = 30
)
public class SqlSink extends FlowComponent {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int MAX_BATCH_SIZE = 10000;
    private static final List<String> ALLOWED_SQL_PREFIXES = Arrays.asList(
        "INSERT",
        "UPDATE",
        "DELETE",
        "REPLACE",
        "MERGE",
        "WITH"
    );

    @ComponentField(
        key = "sourceId",
        label = "目标数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        group = "基础配置",
        order = 10,
        schemaField = "schema",
        help = "选择 SQL 要写入的目标关系型数据库"
    )
    private DatasourceInfo datasource;

    @ComponentField(
        label = "SQL模板",
        type = ComponentFieldType.TEXTAREA,
        required = true,
        group = "基础配置",
        order = 20,
        placeholder = "INSERT INTO ... VALUES (${字段名})",
        help = "只支持单条 DML（INSERT / UPDATE / DELETE / MERGE），支持 ${字段名}、${attr:属性名}、${FLOW_FILE_DATA}、#{内置参数} 等动态参数"
    )
    private String sql;

    @ComponentField(
        label = "批量大小",
        type = ComponentFieldType.NUMBER,
        group = "基础配置",
        order = 30,
        defaultValue = "500",
        min = 1,
        max = 10000,
        step = 100,
        help = "每个 JDBC 批次的行数，默认 500"
    )
    private Integer batchSize = DEFAULT_BATCH_SIZE;

    @ComponentField(
        label = "事务",
        type = ComponentFieldType.SWITCH,
        group = "基础配置",
        order = 40,
        defaultValue = "true",
        help = "开启后一个 FlowFile 内的所有批次在同一事务中提交；Doris 等不支持事务的目标请关闭"
    )
    private Boolean transaction = true;

    @ComponentField(
        label = "忽略错误",
        type = ComponentFieldType.SWITCH,
        group = "高级配置",
        order = 40,
        defaultValue = "false",
        help = "开启后执行失败只记录日志并跳过"
    )
    private Boolean ignoreError = false;

    @ComponentField(
        label = "事件类型过滤",
        type = ComponentFieldType.MULTI_SELECT,
        group = "高级配置",
        order = 10,
        valueFormat = ComponentFieldValueFormat.ARRAY,
        options = {
            @ComponentFieldOption(value = "INSERT", label = "INSERT"),
            @ComponentFieldOption(value = "UPDATE", label = "UPDATE"),
            @ComponentFieldOption(value = "DELETE", label = "DELETE")
        },
        help = "只处理指定事件类型；不配置表示处理所有事件"
    )
    private List<String> eventTypes;

    @ComponentField(
        label = "查询超时（秒）",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 20,
        defaultValue = "0",
        min = 0,
        max = 3600,
        step = 10,
        help = "0 表示不设置超时"
    )
    private Integer queryTimeout = 0;

    public SqlSink() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        try {
            executeInternal(flowFile);
        } catch (RuntimeException e) {
            if (!Boolean.TRUE.equals(this.ignoreError)) {
                // CDC 合并 FlowFile 默认带 _error_ignore，Sink 默认应让异常暴露出来。
                flowFile.getAttributeMap().remove(FlowFile.ATTRIBUTE_ERROR_IGNORE);
            }
            throw e;
        }
    }

    private void executeInternal(FlowFile flowFile) {
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过 SQL 写入");
            return;
        }

        List<JSONObject> records = extractRecords(flowFile);
        if (records.isEmpty()) {
            return;
        }
        if (!matchEventType(flowFile)) {
            return;
        }

        String sqlToExecute = validateSql();
        ParameterUtil.ParameterizedSql parameterizedSql = ParameterUtil.prepareParameterizedSql(sqlToExecute);

        try (Connection connection = DBUtils.getConnection(this.datasource)) {
            boolean useTransaction = !Boolean.FALSE.equals(this.transaction);
            boolean originalAutoCommit = connection.getAutoCommit();
            if (useTransaction) {
                connection.setAutoCommit(false);
            }

            try {
                executeRecords(connection, records, parameterizedSql, flowFile, useTransaction);
                if (useTransaction) {
                    connection.commit();
                }
            } catch (SQLException | RuntimeException e) {
                if (useTransaction) {
                    rollbackQuietly(connection, e);
                }
                throw e;
            } finally {
                if (useTransaction && originalAutoCommit) {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException e) {
                        logWarn("恢复连接自动提交失败：" + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            logError("SqlSink 执行失败：" + e.getMessage());
            if (Boolean.TRUE.equals(this.ignoreError)) {
                logWarn("ignoreError=true，跳过本次 SQL 写入");
                return;
            }
            // CDC 合并 FlowFile 默认带 _error_ignore，Sink 默认应让异常暴露出来。
            flowFile.getAttributeMap().remove(FlowFile.ATTRIBUTE_ERROR_IGNORE);
            throw new RuntimeException("SqlSink 执行失败", e);
        }
    }

    private List<JSONObject> extractRecords(FlowFile flowFile) {
        JSONArray jsonArray = flowFile.getJsonArray();
        if (jsonArray != null) {
            List<JSONObject> records = new ArrayList<>(jsonArray.size());
            for (Object item : jsonArray) {
                records.add(toJsonObject(item));
            }
            return records;
        }

        JSONObject jsonObject = flowFile.getJsonObject();
        if (jsonObject != null) {
            List<JSONObject> records = new ArrayList<>(1);
            records.add(jsonObject);
            return records;
        }

        if (flowFile.getData() == null) {
            return Collections.emptyList();
        }
        throw new IllegalArgumentException("SqlSink 仅支持 JSON 数组或 JSON 对象数据");
    }

    @SuppressWarnings("unchecked")
    private JSONObject toJsonObject(Object item) {
        if (item instanceof JSONObject) {
            return (JSONObject) item;
        }
        if (item instanceof Map) {
            return new JSONObject((Map<String, Object>) item);
        }
        return JSON.parseObject(JSON.toJSONString(item));
    }

    private boolean matchEventType(FlowFile flowFile) {
        if (this.eventTypes == null || this.eventTypes.isEmpty()) {
            return true;
        }
        Object eventTypeAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE);
        String eventType = eventTypeAttr == null ? null : eventTypeAttr.toString().trim().toUpperCase(Locale.ROOT);
        if (eventType == null || eventType.isEmpty()) {
            logWarn("配置了 eventTypes，但 FlowFile 缺少 _eventType，跳过本次写入");
            return false;
        }
        for (String allowed : this.eventTypes) {
            if (allowed != null && eventType.equals(allowed.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        logInfo("事件类型 " + eventType + " 不在 SqlSink 配置范围内，跳过本次写入");
        return false;
    }

    private String validateSql() {
        if (this.datasource == null) {
            throw new IllegalArgumentException("SqlSink 未配置目标数据源");
        }
        if (this.sql == null || this.sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SqlSink 未配置 SQL 模板");
        }

        String sqlToExecute = this.sql.trim();
        String firstKeyword = firstKeyword(sqlToExecute);
        if (!ALLOWED_SQL_PREFIXES.contains(firstKeyword)) {
            throw new IllegalArgumentException("SqlSink 仅允许执行 DML 语句，当前 SQL 以 " + firstKeyword + " 开头");
        }
        return sqlToExecute;
    }

    private void executeRecords(
        Connection connection,
        List<JSONObject> records,
        ParameterUtil.ParameterizedSql parameterizedSql,
        FlowFile flowFile,
        boolean useTransaction
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(parameterizedSql.getSql())) {
            if (this.queryTimeout != null && this.queryTimeout > 0) {
                statement.setQueryTimeout(this.queryTimeout);
            }

            if (!parameterizedSql.hasParameters()) {
                statement.execute();
                logInfo("SqlSink 执行静态 SQL 完成，事务：" + useTransaction);
                return;
            }

            int size = normalizeBatchSize();
            int pending = 0;
            int total = 0;
            int batchCount = 0;
            long affected = 0;

            for (JSONObject record : records) {
                bindParameters(statement, resolveParameterValues(flowFile, record, parameterizedSql));
                statement.addBatch();
                pending++;
                total++;

                if (pending >= size) {
                    affected += sumAffectedRows(statement.executeBatch());
                    batchCount++;
                    pending = 0;
                }
            }

            if (pending > 0) {
                affected += sumAffectedRows(statement.executeBatch());
                batchCount++;
            }

            logInfo(
                "SqlSink 写入完成，记录数：" +
                total +
                "，批次数：" +
                batchCount +
                "，影响行数：" +
                affected +
                "，事务：" +
                useTransaction
            );
        }
    }

    private List<Object> resolveParameterValues(
        FlowFile flowFile,
        JSONObject record,
        ParameterUtil.ParameterizedSql parameterizedSql
    ) {
        Map<String, Object> variables = new HashMap<>(record);
        variables.put("ROW_DATA", record);

        ParameterUtil.ParameterValues parameterValues = ParameterUtil.resolveParameterValues(
            parameterizedSql.getParameters(),
            flowFile,
            variables
        );
        if (parameterValues.hasUnresolved()) {
            throw new IllegalArgumentException(
                "SqlSink SQL 存在未解析的动态参数：" + String.join("、", parameterValues.getUnresolvedParameters())
            );
        }
        return parameterValues.getValues();
    }

    private void bindParameters(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (value instanceof Map || value instanceof Collection) {
                value = String.valueOf(value);
            }
            if (value instanceof String) {
                // 部分驱动（如 TDengine WS）对 NCHAR 列的 setObject(String) 兼容性较差，
                // 统一使用标准 setString，避免类型转换异常。
                statement.setString(index + 1, (String) value);
            } else {
                statement.setObject(index + 1, value);
            }
        }
    }

    private long sumAffectedRows(int[] counts) {
        if (counts == null) {
            return 0;
        }
        long total = 0;
        for (int count : counts) {
            if (count > 0) {
                total += count;
            }
        }
        return total;
    }

    private int normalizeBatchSize() {
        if (this.batchSize == null || this.batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(this.batchSize, MAX_BATCH_SIZE);
    }

    private void rollbackQuietly(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
            logWarn("SqlSink 回滚失败：" + rollbackError.getMessage());
        }
    }

    /**
     * 跳过前导注释与空白，返回 SQL 的第一个关键字。
     */
    static String firstKeyword(String sql) {
        int index = 0;
        int length = sql.length();
        while (index < length) {
            char current = sql.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            char next = index + 1 < length ? sql.charAt(index + 1) : '\0';
            if (current == '-' && next == '-') {
                index += 2;
                while (index < length && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') {
                    index++;
                }
                continue;
            }
            if (current == '#') {
                index++;
                while (index < length && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') {
                    index++;
                }
                continue;
            }
            if (current == '/' && next == '*') {
                index += 2;
                while (index + 1 < length && !(sql.charAt(index) == '*' && sql.charAt(index + 1) == '/')) {
                    index++;
                }
                index = Math.min(index + 2, length);
                continue;
            }
            break;
        }

        int start = index;
        while (index < length && Character.isLetter(sql.charAt(index))) {
            index++;
        }
        return sql.substring(start, index).toUpperCase(Locale.ROOT);
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public void setTransaction(Boolean transaction) {
        this.transaction = transaction;
    }

    public void setIgnoreError(Boolean ignoreError) {
        this.ignoreError = ignoreError;
    }

    public void setEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public void setQueryTimeout(Integer queryTimeout) {
        this.queryTimeout = queryTimeout;
    }
}
