package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.util.SQLUtils;
import com.data.metadata.TableMeta;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SqlUnit组件 - 根据sql配置中的tablemap替换query中的表名，并将select语句转换为create table as select并执行
 */
@ComponentRegister("SqlUnit")
public class SqlUnit extends FlowComponent {

    // 对应job.json参数（兼容字符串与 {query, tablemap} 对象两种形式）
    private Object sql;

    private static final int FETCH_SIZE = 5000;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    public SqlUnit() {
        setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (!upstreamFinish) {
            return;
        }

        try (Connection targetConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            // 创建临时表存储输入数据
//            TableMeta tempTable = getTableMeta(flowFile);
            // 处理SQL查询
            processSqlQuery(targetConn);

        } catch (SQLException e) {
            logError("SQL处理失败: " + e.getMessage());
            throw new RuntimeException("SQL处理失败", e);
        }
    }

    @Override
    protected boolean materializesToDuckDB() {
        return true;
    }

    /**
     * 处理SQL查询，包括表名替换和CTAS执行
     */
    private void processSqlQuery(Connection targetConn) throws SQLException {
        JSONObject sqlConfig = resolveSqlConfig();
        if (sqlConfig == null) {
            throw new IllegalArgumentException("sql参数不能为空");
        }

        // 获取查询语句和表映射
        String query = sqlConfig.getString("query");
        JSONObject tableMap = sqlConfig.getJSONObject("tablemap");

        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query参数不能为空");
        }
        // 移除注释
        String removeCommentQuery = SQLUtils.removeComments(query);
        // 替换表名
        String replacedQuery = replaceTableNames(removeCommentQuery, tableMap);
//        logInfo("替换后的查询语句: " + replacedQuery);

        //转换sql的语法从spark的语法转换为duckdb的语法
        String convertedQuery = SQLUtils.convertSparkToDuckDb(replacedQuery);
//        logInfo("转换为DuckDB语法后的查询语句: " + convertedQuery);
        
        // 将SELECT语句转换为CREATE TABLE AS SELECT
        String createTableSql = convertToCreateTableAsSelect(convertedQuery);
        logInfo("执行的CTAS语句: " + createTableSql);

        // 执行CTAS语句
        try (Statement stmt = targetConn.createStatement()) {
            //设置duckdb 内存限制为100MB
            stmt.execute("SET memory_limit = '1GB'");
            //设置duckdb 线程数为4
            stmt.execute("SET threads = 1");
            //设置duckdb 保持插入顺序为false
            stmt.execute("SET preserve_insertion_order=false");
            //设置duckdb 临时目录为/tmp/duckdb_temp
            stmt.execute("SET temp_directory = '/tmp/duckdb_temp'");
            stmt.execute(createTableSql);
        }
    }



    /**
     * 根据tablemap替换查询中的表名
     */
    private String replaceTableNames(String query, JSONObject tableMap) {
        if (tableMap == null || tableMap.isEmpty()) {
            return query;
        }

        String replacedQuery = query;
        for (String key : tableMap.keySet()) {
            String value = tableMap.getString(key);
            // 使用正则表达式替换表名，确保只替换完整的表名
            String regex = "\\b" + Pattern.quote(value) + "\\b";
            replacedQuery = replacedQuery.replaceAll(regex, key.toLowerCase());
            logInfo("替换表名: " + key + " -> " + value);
        }

        return replacedQuery;
    }

    /**
     * 将SELECT语句转换为CREATE TABLE AS SELECT
     */
    private String convertToCreateTableAsSelect(String selectQuery) {
        // 生成目标表名
        String targetTableName = getOutputTableName(0);
        
        // 构建CTAS语句
        return "CREATE OR REPLACE TABLE " + targetTableName + " AS " + selectQuery;
    }

    // 自动注入参数的setters
    /**
     * 兼容两种 sql 配置：
     * <p>
     * 1. 设计器配置：sql 为纯 SQL 字符串；<br>
     * 2. 旧配置：sql 为 {@code {query, tablemap}} 对象。
     */
    public void setSql(Object sql) {
        this.sql = sql;
    }

    public Object getSql() {
        return sql;
    }

    /**
     * 将 sql 配置统一解析为 {@code {query, tablemap}} 对象。
     */
    JSONObject resolveSqlConfig() {
        if (sql instanceof JSONObject) {
            return (JSONObject) sql;
        }
        if (sql == null) {
            return null;
        }
        JSONObject sqlConfig = new JSONObject();
        sqlConfig.put("query", sql.toString());
        return sqlConfig;
    }
}
