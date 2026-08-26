package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DuckDBWrite extends FlowComponent {

    // 对应job.json参数
    private String table;
    private String model;
    private String dbFile;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

     public DuckDBWrite() {
        setType(ComponentType.SINK);  // 设置为Sink类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getJsonArray() == null) {
            return;
        }
        String dbFileName = getDbFileName();
        // 获取目标表元数据
        TableMeta targetTable = getTableMeta(this.table, flowFile, dbFileName);
        if (flowFile.getJsonArray().isEmpty()) {
            return;
        }
        try (Connection duckdbConn = DuckDBEngine.getInstance().getConnection(dbFileName)) {
            // 流式数据写入
            writeData(duckdbConn, targetTable, flowFile);
        } catch (SQLException e) {
            logError("DuckDB数据写入失败", e.getMessage());
            throw new RuntimeException("DuckDB数据写入失败", e);
        }
    }
    
    private String getDbFileName() {
        if (dbFile != null && !dbFile.trim().isEmpty()) {
            return dbFile.trim();
        }
        return getContext().getJobInstanceCode();
    }
    
    // 从FlowFile中获取目标表元数据,需要防止并发问题
    private synchronized TableMeta getTableMeta(String table, FlowFile flowFile, String dbFileName) {
        TableMeta targetTable = this.tableMetaMap.get(table);
        if (targetTable == null) {
            try (Connection duckdbConn = DuckDBEngine.getInstance().getConnection(dbFileName)) {
                if (!DBUtils.tableExists(duckdbConn, "main", this.table)) {
                    // 如果表不存在，根据源表元数据创建表
                    if (flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA) != null) {
                        TableMeta sourceTable = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
                        targetTable = DatabaseConverter.convert(sourceTable.getDbType(), "duckdb", sourceTable);
                        targetTable.setDbType("duckdb");
                        targetTable.setTable(table);
                        targetTable.setSchema("main");
                    }else {
                        // 如果表元数据为空，根据JSONArray数据内容反推表元数据
                        targetTable = inferTableMetaFromJsonArray(flowFile, table);
                    }
                    // 生成DDL并创建表
                    String ddl = DatabaseConverter.generateTableDDL("duckdb", targetTable);
                    DBUtils.execute(duckdbConn, ddl);
                    logInfo("已成功创建DuckDB表 " + table);
                } else {
                    // 表已存在，获取表元数据
                    logInfo("表存在，获取表元数据 " + table);
                    targetTable = DBUtils.getTableMetaData(duckdbConn, "main", table);
                }

                // 处理overwrite模式
                if ("overwrite".equals(this.model)) {
                    logInfo("清空表 " + table);
                    DBUtils.execute(duckdbConn, String.format("TRUNCATE TABLE %s.%s", targetTable.getSchema(), table));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            this.tableMetaMap.put(table, targetTable);
        }

        return targetTable;
    }

    // 在writeData方法中处理日期时间类型
    private void writeData(Connection duckdbConn, TableMeta targetTable, FlowFile flowFile) throws SQLException {
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

    /**
     * 根据JSONArray数据推断表元数据
     * 从数据中提取字段名和类型信息，生成对应的表结构
     */
    private TableMeta inferTableMetaFromJsonArray(FlowFile flowFile, String tableName) {
        JSONArray jsonArray = flowFile.getJsonArray();
        if (jsonArray == null || jsonArray.isEmpty()) {
            throw new IllegalArgumentException("无法从空的JSONArray数据推断表元数据");
        }
        
        TableMeta tableMeta = new TableMeta();
        tableMeta.setDbType("duckdb");
        tableMeta.setTable(tableName);
        tableMeta.setSchema("main");
        
        // 获取第一条记录作为字段参考
        JSONObject firstRecord = jsonArray.getJSONObject(0);
        
        for (Map.Entry<String, Object> entry : firstRecord.entrySet()) {
            String columnName = entry.getKey();
            Object value = entry.getValue();
            String columnType = inferColumnType(value);
            
            ColumnMeta column = new ColumnMeta(columnName, columnType);
            tableMeta.addColumn(column);
        }
        
        logInfo("根据JSONArray数据推断表结构，表名: " + tableName + ", 字段数: " + tableMeta.columns().size());
        return tableMeta;
    }
    
    /**
     * 根据值推断字段类型
     */
    private String inferColumnType(Object value) {
        if (value == null) {
            return "VARCHAR"; // 默认字符串类型
        }
        
        if (value instanceof Integer) {
            return "INT";
        } else if (value instanceof Long) {
            return "BIGINT";
        } else if (value instanceof Double || value instanceof Float) {
            return "DOUBLE";
        } else if (value instanceof Boolean) {
            return "BOOLEAN";
        } else if (value instanceof java.util.Date) {
            return "TIMESTAMP";
        } else {
            return "VARCHAR"; // 默认字符串类型
        }
    }
    
    /**
     * 检查字符串是否为日期时间格式
     */
    private boolean isDateTimeFormat(String value) {
        // 简单的日期时间格式检查
        String[] dateTimePatterns = {
            "\\d{4}-\\d{2}-\\d{2}",
            "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
            "\\d{4}/\\d{2}/\\d{2}",
            "\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}"
        };
        
        for (String pattern : dateTimePatterns) {
            if (value.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    // 自动注入方法
    public void setTable(String table) {
        this.table = table;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setDbFile(String dbFile) {
        this.dbFile = dbFile;
    }
    
    public String getDbFile() {
        return dbFile;
    }
}