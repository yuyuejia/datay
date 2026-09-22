package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.util.SQLUtils;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@ComponentRegister(
    value = "DuckDBSql",
    name = "DuckDB SQL",
    group = "DuckDB 组件",
    desc = "在 DuckDB 中执行 SQL 语句，支持多条语句并将查询结果输出到下游。",
    order = 20
)
public class DuckDBSql extends FlowComponent {

    // 对应job.json参数
    private String sql;

    // 可选配置：查询结果的输出表名
    private String outputTable;

    private static final String DEFAULT_OUTPUT_TABLE = "duckdb_query_result";

    private static final int FETCH_SIZE = 10000;

    public DuckDBSql() {
        setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (!upstreamFinish) {
            return;
        }
        try (Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            // 分割SQL语句（支持多条语句）
            List<String> sqlStatements = SQLUtils.splitSqlStatements(sql);

            if (sqlStatements.isEmpty()) {
                this.logInfo("没有有效的SQL语句需要执行");
                return;
            }

            this.logInfo("检测到 " + sqlStatements.size() + " 条SQL语句需要执行");

            // 判断第一条语句的类型来决定整体执行策略
            String firstStatement = sqlStatements.get(0).trim().toLowerCase();

            if (SQLUtils.isSelectStatement(firstStatement)) {
                // 如果是SELECT语句，只执行第一条（保持原有行为）
                executeSelectQuery(duckdbConn, sqlStatements.get(0));
            } else if (SQLUtils.isDmlStatement(firstStatement) || SQLUtils.isDdlStatement(firstStatement)) {
                // 如果是DDL或DML语句，执行所有语句
                executeBatchStatements(duckdbConn, sqlStatements);
            } else {
                // 其他语句也执行所有
                executeBatchStatements(duckdbConn, sqlStatements);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DuckDB SQL执行失败: " + e.getMessage(), e);
        }
    }




    /**
     * 执行SELECT查询语句（单条）
     */
    private void executeSelectQuery(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 配置流式读取
            stmt.setFetchSize(FETCH_SIZE);

            try (ResultSet rs = stmt.executeQuery(sql)) {
                // 获取结果集元数据
                TableMeta outputTableMeta = DBUtils.getTableMetaFromResultSet(rs.getMetaData());
                outputTableMeta.setDbType("duckdb");
                outputTableMeta.setTable(resolveOutputTable());
                // 分页处理结果集数据
                JSONArray resultRecords = new JSONArray();
                int recordCount = 0;
                int batchCount = 0;

                while (rs.next()) {
                    JSONObject jsonRecord = new JSONObject();
                    for (int i = 1; i <= outputTableMeta.columns().size(); i++) {
                        String columnName = rs.getMetaData().getColumnName(i);
                        Object value = rs.getObject(i);
                        jsonRecord.put(columnName, value);
                    }
                    resultRecords.add(jsonRecord);
                    recordCount++;

                    // 当记录数达到FETCH_SIZE时，输出当前批次
                    if (resultRecords.size() >= FETCH_SIZE) {
                        batchCount++;
                        this.logInfo("输出第 " + batchCount + " 批次数据，包含 " + resultRecords.size() + " 条记录");

                        // 创建输出FlowFile
                        FlowFile outFile = new FlowFile();
                        outFile.setJsonArray(resultRecords);
                        outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, outputTableMeta);
                        outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE,outputTableMeta.getTable());
                        outFile.setAttribute("_batchIndex", batchCount);
                        outFile.setAttribute("_isLastBatch", false);

                        // 输出当前批次
                        writeRecords(outFile);

                        // 清空当前批次，准备下一批次
                        resultRecords = new JSONArray();
                    }
                }

                // 处理剩余记录（最后一批）
                if (!resultRecords.isEmpty()) {
                    batchCount++;
                    this.logInfo("输出第 " + batchCount + " 批次数据（最后一批），包含 " + resultRecords.size() + " 条记录");

                    // 创建输出FlowFile
                    FlowFile outFile = new FlowFile();
                    outFile.setJsonArray(resultRecords);
                    outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, outputTableMeta);
                    outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE,outputTableMeta.getTable());
                    outFile.setAttribute("_batchIndex", batchCount);
                    outFile.setAttribute("_isLastBatch", true);

                    // 输出最后一批
                    writeRecords(outFile);
                }

                this.logInfo("SELECT查询执行完成，总共返回 " + recordCount + " 条记录，分 " + batchCount + " 批次输出");
            }
        }
    }

    /**
     * 批量执行DDL和DML语句
     */
    private void executeBatchStatements(Connection conn, List<String> sqlStatements) throws SQLException {
        int totalAffectedRows = 0;
        int successfulStatements = 0;

        try (Statement stmt = conn.createStatement()) {
            // 禁用自动提交，支持事务
            //            conn.setAutoCommit(false);

            for (int i = 0; i < sqlStatements.size(); i++) {
                String currentSql = sqlStatements.get(i);
                try {
                    this.logInfo(
                            "执行第 " +
                            (i + 1) +
                            " 条SQL语句: " +
                            (currentSql.length() > 100 ? currentSql.substring(0, 100) + "..." : currentSql)
                        );

                    boolean hasResultSet = stmt.execute(currentSql);
                    int affectedRows = stmt.getUpdateCount();

                    if (affectedRows >= 0) {
                        totalAffectedRows += affectedRows;
                    }
                    successfulStatements++;
                } catch (SQLException e) {
                    this.logInfo("第 " + (i + 1) + " 条SQL语句执行失败: " + e.getMessage());
                    throw e;
                    // 可以选择回滚或继续执行后续语句
                    // conn.rollback(); // 如果需要事务一致性，可以取消注释
                    //                     break; // 如果遇到错误就停止，可以取消注释
                }
            }
        }

        this.logInfo(
                "批量SQL执行完成，总共 " +
                sqlStatements.size() +
                " 条语句，成功 " +
                successfulStatements +
                " 条，总共影响行数: " +
                totalAffectedRows
            );
    }

    // 自动注入方法
    public void setSql(String sql) {
        this.sql = sql;
    }

    // 自动注入方法
    public void setOutputTable(String outputTable) {
        this.outputTable = outputTable;
    }

    public String getOutputTable() {
        return outputTable;
    }

    /**
     * 解析输出表名：配置了 outputTable 时使用配置值，否则使用默认值。
     */
    String resolveOutputTable() {
        if (outputTable != null && !outputTable.trim().isEmpty()) {
            return outputTable.trim();
        }
        return DEFAULT_OUTPUT_TABLE;
    }
}
