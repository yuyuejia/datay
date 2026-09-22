package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
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

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * StreamSqlUnit组件 - 支持通过SQL语句从源数据库读取数据并写入到DuckDB中
 */
@ComponentRegister(
    value = "StreamSqlUnit",
    name = "SQL组件",
    group = "数据处理",
    desc = "执行自定义 SQL 从源数据库读取数据并写入 DuckDB，可用于复杂查询与数据加工。",
    order = 20
)
public class StreamSqlUnit extends FlowComponent {

    // 对应job.json参数
    private String sql;

    private static final int FETCH_SIZE = 5000;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    public StreamSqlUnit() {
        setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getJsonArray() == null || flowFile.getJsonArray().isEmpty()) {
            return;
        }

        try (Connection targetConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            TableMeta tempTable = getTableMeta(flowFile);

            String deleteSql = "TRUNCATE TABLE " + tempTable.getSchema() + "." + tempTable.getTable();
            DBUtils.execute(targetConn, deleteSql);
            // 流式数据迁移
            migrateData(targetConn, tempTable, flowFile);

            try (Statement srcStmt = targetConn.createStatement()) {
                // 配置流式读取
                srcStmt.setFetchSize(FETCH_SIZE);
                try (ResultSet rs = srcStmt.executeQuery(sql)) {
                    JSONArray batchRecords = new JSONArray();
                    //                    List<GenericRecord> records = new ArrayList<>();
                    TableMeta output = getTableMetaFromRsMeta(rs.getMetaData());
                    output.setTable("tmp_" + getId());
                    //                    Schema avroSchema = AvroUtils.createAvroSchema(output);
                    while (rs.next()) {
                        JSONObject jsonRecord = new JSONObject();
                        for (int i = 1; i <= output.columns().size(); i++) {
                            String columnName = rs.getMetaData().getColumnName(i);
                            Object value = rs.getObject(i);
                            jsonRecord.put(columnName, value);
                        }
                        batchRecords.add(jsonRecord);
                    }
                    FlowFile outFile = new FlowFile();
                    outFile.setJsonArray(batchRecords);
                    if(flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE) != null){
                        outFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE));
                    }
                    outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, output);
                    writeRecords(outFile);
                    logInfo("已发送剩余 " + batchRecords.size() + " 条记录到队列,table:" + tempTable.getTable());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据迁移失败", e);
        }
    }

    private TableMeta getTableMetaFromRsMeta(ResultSetMetaData metaData) throws SQLException {
        TableMeta tableMeta = new TableMeta();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            String columnType = metaData.getColumnTypeName(i);
            tableMeta.addColumn(new ColumnMeta(columnName, columnType));
        }
        return tableMeta;
    }

    private TableMeta getTableMeta(FlowFile flowFile) {
        TableMeta sourceTable = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        TableMeta targetTable = null;
        if (sourceTable != null && this.tableMetaMap.containsKey(sourceTable.getTable())) {
            return this.tableMetaMap.get(sourceTable.getTable());
        }
        if (sourceTable != null) {
            targetTable = DatabaseConverter.convert(sourceTable.getDbType(), "duckdb", sourceTable);
            targetTable.setDbType("duckdb");
            targetTable.setSchema("main");
        }
        if (targetTable == null) {
            targetTable = new TableMeta();
            targetTable.setTable("tmp_" + getId());
            targetTable.setSchema("main");
            //根据flowFile数据内容生成字段信息
            for (Map.Entry<String, Object> entry : flowFile.getJsonArray().getJSONObject(0).entrySet()) {
                String columnName = entry.getKey().toLowerCase();
                Object value = entry.getValue();
                targetTable.addColumn(new ColumnMeta(columnName, value.getClass().getSimpleName()));
            }
        }

        try (Connection targetConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            if (!DBUtils.schemaExists(targetConn, targetTable.getSchema())) {
                String ddl = DatabaseConverter.generateSchemaDDL(targetTable);
                DBUtils.execute(targetConn, ddl);
                logInfo("创建DuckDB schema: " + targetTable.getSchema());
            }
            if (!DBUtils.tableExists(targetConn, targetTable.getSchema(), targetTable.getTable())) {
                String ddl = DatabaseConverter.generateTableDDL("duckdb", targetTable);
                DBUtils.execute(targetConn, ddl);
                logInfo("创建DuckDB table: " + targetTable.getSchema() + "." + targetTable.getTable());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        this.tableMetaMap.put(targetTable.getTable(), targetTable);
        return targetTable;
    }

    // 在writeData方法中处理日期时间类型
    private void migrateData(Connection duckdbConn, TableMeta targetTable, FlowFile flowFile) throws SQLException {
        // 使用DatabaseConverter生成写入SQL
        //        String writeSQL = DatabaseConverter.generateWriteSQL("duckdb", targetTable, this.model);
        DuckDBConnection duckDBConnection = (DuckDBConnection) DBUtils.getRawConnection((HikariProxyConnection) duckdbConn);
        // using try-with-resources to automatically close the appender at the end of the scope
        try (DuckDBAppender appender = duckDBConnection.createAppender(targetTable.getSchema(), targetTable.getTable())) {
            JSONArray records = flowFile.getJsonArray();

            for (Object o : records) {
                appender.beginRow();
                JSONObject record = (JSONObject) o;
                for (int i = 0; i < targetTable.columns().size(); ++i) {
                    ColumnMeta column = targetTable.columns().get(i);
                    String fieldType = column.getType();
                    Object value = record.get(column.getName());
                    DBUtils.appendValue(appender, fieldType, value);
                }
                appender.endRow();
            }
            this.logInfo("已成功写入 " + records.size() + " 条记录到DuckDB表 " + targetTable.getTable());
        }
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
