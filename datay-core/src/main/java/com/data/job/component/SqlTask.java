package com.data.job.component;

import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.util.SQLUtils;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * SqlTask组件 - 支持在指定的数据库连接中执行SQL语句
 * 参考DuckDBSql的实现，但支持多种数据库类型
 */
//@ComponentRegister(
//    value = "SqlTask",
//    name = "SQL任务",
//    group = "数据处理",
//    desc = "在指定数据库连接中执行 SQL 语句，支持多种数据库类型。",
//    order = 30
//)
public class SqlTask extends FlowComponent {

    // 对应job.json参数
    private String sql;
    private DatasourceInfo sourceId;

    public SqlTask() {
        setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (!upstreamFinish) {
            return;
        }
        
        if (sourceId == null) {
            throw new IllegalArgumentException("数据源信息不能为空");
        }

        try (Connection conn = DBUtils.getConnection(sourceId.getUrl(), sourceId.getUsername(), sourceId.getPassword())) {
            // 分割SQL语句（支持多条语句）
            List<String> sqlStatements = SQLUtils.splitSqlStatements(sql);

            if (sqlStatements.isEmpty()) {
                this.logInfo("没有有效的SQL语句需要执行");
                return;
            }

            this.logInfo("检测到 " + sqlStatements.size() + " 条SQL语句需要执行");

            executeBatchStatements(conn, sqlStatements);
        } catch (SQLException e) {
            throw new RuntimeException("SQL执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量执行DDL和DML语句
     */
    private void executeBatchStatements(Connection conn, List<String> sqlStatements) throws SQLException {
        int totalAffectedRows = 0;
        int successfulStatements = 0;

        try (Statement stmt = conn.createStatement()) {
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
                    // break; // 如果遇到错误就停止，可以取消注释
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

    public void setSourceId(DatasourceInfo sourceId) {
        this.sourceId = sourceId;
    }
}