package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JdbcInputDuckDB组件 - 支持通过JDBC方式从源数据库读取数据并写入到DuckDB中。
 * <p>
 * 读取逻辑复用 {@link AbstractJdbcInput}，本类负责将每批记录物化到 DuckDB 临时表，
 * 增量状态直接写入执行上下文。
 */
@ComponentRegister("JdbcInput")
public class JdbcInput extends AbstractJdbcInput {

    // 目标 DuckDB 连接
    private Connection duckdbConn;

    // DuckDB表元数据缓存
    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    // 当前正在写入的目标表
    private TableMeta currentTargetTable;

    public JdbcInput() {
        super();
    }

    @Override
    protected void beforeProcess() throws SQLException {
        this.duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode());
    }

    @Override
    protected void afterProcess() throws SQLException {
        if (duckdbConn != null) {
            duckdbConn.close();
            duckdbConn = null;
        }
    }

    @Override
    protected void beforeMigrateTable(TableMeta srcTable) throws SQLException {
        this.currentTargetTable = getTargetTableMeta(srcTable, duckdbConn);
    }

    @Override
    protected void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) throws SQLException {
        writeBatchToDuckDB(duckdbConn, currentTargetTable, batchRecords);
    }

    @Override
    protected void persistIncrStatus(TableMeta srcTable, Object lastIncrValue) {
        String statusKey = "lastIncrValue." + srcTable.getTable();
        getContext().getStatusMap().put(getId() + "." + statusKey, lastIncrValue.toString());
        logInfo("保存表 " + srcTable.getTable() + " 的增量状态: " + lastIncrValue);
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
}
