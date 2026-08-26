package com.data.job.component;

import com.data.job.*;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ComponentRegister("JdbcOutput")
public class JdbcOutput extends FlowComponent {

    // 对应job.json参数
    private String schema;
    private String table;
    private String model; // 写入模式：append, overwrite, update

    private DatasourceInfo sourceId;
    private List<Map<String, Object>> header_map; // 字段映射配置

    private static final int FETCH_SIZE = 10000;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    public JdbcOutput() {
        setType(ComponentType.SINK);  // 设置为Sink类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (!upstreamFinish) {
            return;
        }

        // 获取目标表名和schema
        String targetTableName = getTargetTableName();
        String targetSchema = getTargetSchema();

        try (Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode());
             Connection targetConn = DBUtils.getConnection(this.sourceId)) {
            
            // 获取DuckDB表的元数据
            TableMeta sourceTableMeta = DBUtils.getTableMetaData(duckdbConn, "main", getInputTableName(0));
            
            // 获取或创建目标表元数据
            TableMeta targetTableMeta = getOrCreateTargetTable(targetConn, targetSchema, targetTableName, sourceTableMeta);
            
            // 流式读取DuckDB数据并写入目标表
            streamDataFromDuckDBToTarget(duckdbConn, targetConn, targetTableMeta, sourceTableMeta.getTable());
            
        } catch (SQLException e) {
            logInfo("数据写入失败，" + e.getMessage());
            throw new RuntimeException("数据写入失败", e);
        }
    }

    /**
     * 流式读取DuckDB数据并写入目标表
     */
    private void streamDataFromDuckDBToTarget(Connection duckdbConn, Connection targetConn, 
                                            TableMeta targetTableMeta, String tableName) throws SQLException {
        String selectSql = "SELECT * FROM " + tableName;
        String insertSql = buildInsertSql(targetTableMeta);
        
        this.logInfo("开始流式数据迁移，读取SQL: " + selectSql);
        this.logInfo("写入SQL: " + insertSql);
        
        try (Statement stmt = duckdbConn.createStatement();
             PreparedStatement pstmt = targetConn.prepareStatement(insertSql)) {
            
            // 配置流式读取
            stmt.setFetchSize(FETCH_SIZE);
            
            try (ResultSet rs = stmt.executeQuery(selectSql)) {
                int batchSize = 0;
                int totalRecords = 0;
                int batchCount = 0;
                
                while (rs.next()) {
                    // 设置PreparedStatement参数
                    setPreparedStatementParameters(pstmt, targetTableMeta, rs);
                    pstmt.addBatch();
                    batchSize++;
                    totalRecords++;
                    
                    // 批量提交
                    if (batchSize >= FETCH_SIZE) {
                        batchCount++;
                        pstmt.executeBatch();
                        this.logInfo("批量写入第 " + batchCount + " 批次，包含 " + batchSize + " 条记录，累计 " + totalRecords + " 条");
                        batchSize = 0;
                    }
                }

                // 执行剩余批次
                if (batchSize > 0) {
                    batchCount++;
                    pstmt.executeBatch();
                    this.logInfo("批量写入第 " + batchCount + " 批次（最后一批），包含 " + batchSize + " 条记录，累计 " + totalRecords + " 条");
                }
                
                this.logInfo("流式数据迁移完成，总共写入 " + totalRecords + " 条记录到表 " + targetTableMeta.getTable());
            }
        }
    }

    /**
     * 获取或创建目标表
     */
    private TableMeta getOrCreateTargetTable(Connection targetConn, String schema, String tableName, TableMeta sourceTableMeta) throws SQLException {
        String tableKey = schema + "." + tableName;
        TableMeta targetTableMeta = this.tableMetaMap.get(tableKey);
        
        if (targetTableMeta == null) {
            if (!DBUtils.tableExists(targetConn, schema, tableName)) {
                // 表不存在，根据源表元数据创建目标表
                String targetDbType = DBUtils.getDBType(this.sourceId.getUrl());
                targetTableMeta = DatabaseConverter.convert(sourceTableMeta.getDbType(), targetDbType, sourceTableMeta);
                targetTableMeta.setDbType(targetDbType);
                targetTableMeta.setTable(tableName);
                targetTableMeta.setSchema(schema);
                
                // 应用字段映射配置
                if (header_map != null && !header_map.isEmpty()) {
                    applyHeaderMapping(targetTableMeta);
                }
                
                this.logInfo("表 " + schema + "." + tableName + " 不存在，创建表");
                String ddl = DatabaseConverter.generateTableDDL(targetDbType, targetTableMeta);
                this.logInfo("创建表 " + schema + "." + tableName + " 的DDL: " + ddl);
                DBUtils.execute(targetConn, ddl);
            } else {
                this.logInfo("表 " + schema + "." + tableName + " 已存在");
                targetTableMeta = DBUtils.getTableMetaData(targetConn, schema, tableName);
                // 根据写入模式处理数据
                if ("overwrite".equals(model)) {
                    // 清空目标表
                    truncateTargetTable(targetConn, schema, tableName);
                }
            }
            this.tableMetaMap.put(tableKey, targetTableMeta);
        }
        return targetTableMeta;
    }

    /**
     * 应用字段映射配置
     */
    private void applyHeaderMapping(TableMeta tableMeta) {
        List<ColumnMeta> columns = tableMeta.columns();
        List<ColumnMeta> mappedColumns = new ArrayList<>();
        
        for (Map<String, Object> mapping : header_map) {
            String inColumn = (String) mapping.get("inColumns");
            String outColumn = (String) mapping.get("outColumns");
            Boolean primaryKey = (Boolean) mapping.get("primaryKey");
            
            // 查找对应的源字段
            ColumnMeta sourceColumn = columns.stream()
                .filter(col -> col.getName().equals(inColumn))
                .findFirst()
                .orElse(null);
                
            if (sourceColumn != null) {
                ColumnMeta targetColumn = new ColumnMeta(outColumn, sourceColumn.getType());
                targetColumn.setLength(sourceColumn.getLength());
                targetColumn.setPrecision(sourceColumn.getPrecision());
                targetColumn.setScale(sourceColumn.getScale());
                targetColumn.setNullable(sourceColumn.isNullable());
                targetColumn.setPrimaryKey(primaryKey != null && primaryKey);
                mappedColumns.add(targetColumn);
            }
        }
        
        if (!mappedColumns.isEmpty()) {
            tableMeta.setColumns(mappedColumns);
            this.logInfo("应用字段映射配置，映射 " + mappedColumns.size() + " 个字段");
        }
    }

    /**
     * 清空目标表
     */
    private void truncateTargetTable(Connection targetConn, String schema, String tableName) throws SQLException {
        String truncateSql = "TRUNCATE TABLE " + schema + "." + tableName;
        try (Statement stmt = targetConn.createStatement()) {
            stmt.execute(truncateSql);
            this.logInfo("清空目标表 " + schema + "." + tableName);
        }
    }

    /**
     * 构建插入SQL语句
     */
    private String buildInsertSql(TableMeta tableMeta) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableMeta.getSchema()).append(".").append(tableMeta.getTable()).append(" (");

        if (header_map != null && !header_map.isEmpty()) {
            List<String> mappedColumnNames = new ArrayList<>();
            for (Map<String, Object> mapping : header_map) {
                String outColumn = (String) mapping.get("outColumns");
                mappedColumnNames.add(outColumn);
            }
            sql.append(String.join(", ", mappedColumnNames));
        } else {
            List<String> columnNames = new ArrayList<>();
            for (ColumnMeta column : tableMeta.columns()) {
                columnNames.add(column.getName());
            }
            sql.append(String.join(", ", columnNames));
        }

        sql.append(") VALUES (");
        if (header_map != null && !header_map.isEmpty()) {
            for (int i = 0; i < header_map.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
            }
        } else {
            for (int i = 0; i < tableMeta.columns().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
            }
        }
        sql.append(")");
        
        return sql.toString();
    }

    /**
     * 设置PreparedStatement参数（从ResultSet直接设置）
     */
    private void setPreparedStatementParameters(PreparedStatement pstmt, TableMeta tableMeta, ResultSet rs) throws SQLException {
        int paramIndex = 1;

        if (header_map != null && !header_map.isEmpty()) {
            for (Map<String, Object> mapping : header_map) {
                String inColumn = (String) mapping.get("inColumns");
                Object value = rs.getObject(inColumn);
                pstmt.setObject(paramIndex++, value);
            }
        } else {
            for (int i = 0; i < tableMeta.columns().size(); i++) {
                ColumnMeta column = tableMeta.columns().get(i);
                Object value = rs.getObject(column.getName());
                pstmt.setObject(paramIndex++, value);
            }
        }
    }

    /**
     * 获取目标表名
     */
    private String getTargetTableName() {
        if (this.table != null && !this.table.trim().isEmpty()) {
            return this.table;
        }
        throw new IllegalArgumentException("未配置目标表名");
    }

    /**
     * 获取目标schema
     */
    private String getTargetSchema() {
        if (this.schema != null && !this.schema.trim().isEmpty()) {
            return this.schema;
        }
        if (this.sourceId != null && this.sourceId.getDbschema() != null) {
            return this.sourceId.getDbschema();
        }
        return "public"; // 默认schema
    }

    // 自动注入参数的setters
    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setSourceId(DatasourceInfo sourceId) {
        this.sourceId = sourceId;
    }

    public void setHeader_map(List<Map<String, Object>> header_map) {
        this.header_map = header_map;
    }
}