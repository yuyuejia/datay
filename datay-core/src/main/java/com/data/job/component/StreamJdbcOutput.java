package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ComponentRegister(
    value = "StreamJdbcOutput",
    name = "数据源输出",
    group = "数据输出",
    desc = "将上游数据流按批写入关系型数据库，支持 append/update/replace 写入模式与字段映射。",
    order = 10
)
public class StreamJdbcOutput extends FlowComponent {

    // 对应job.json参数
    private String schema;
    private String table;
    private String model; // 写入模式：append, update, replace
    private Boolean skipIfTableExists; // 当目标表已存在时是否跳过数据导入
    private Boolean dropIfTableExists; // 当目标表已存在时是否先删除
    private Boolean ignoreError = false;

    private List<Map<String, Object>> header_map; // 字段映射配置

    private DatasourceInfo datasource;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();
    private final Map<String, Boolean> tableExistsCache = new HashMap<>(); // 表存在性缓存

    public StreamJdbcOutput() {
        setType(ComponentType.SINK);  // 设置为Sink类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 调试模式：跳过实际写库，仅让数据流经组件以便上游采样
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入目标库");
            return;
        }
        if (flowFile.getData() == null) {
            return;
        }

        // 处理query事件类型, 执行DDL语句
        if ("auto".equals(this.model) &&("QUERY".equals(getEventType(flowFile)) || "DDL".equals(getEventType(flowFile)))) {
            try (Connection targetConn = DBUtils.getConnection(this.datasource)) {
                executeDDLSQL(targetConn, flowFile);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        // 获取目标表名和schema（优先使用配置的table，未配置时从flowfile属性获取）
        String targetTableName = getTargetTableName(flowFile);
        String targetSchema = getTargetSchema(flowFile);

        // 检查是否需要跳过数据导入
        if (Boolean.TRUE.equals(this.skipIfTableExists)) {
            String tableKey = targetSchema + "." + targetTableName;
            
            // 先从缓存中获取表存在性
            Boolean tableExists = tableExistsCache.get(tableKey);
            
            // 如果缓存中没有，再从数据库中查询
            if (tableExists == null) {
                try (Connection targetConn = DBUtils.getConnection(this.datasource)) {
                    tableExists = DBUtils.tableExists(targetConn, targetSchema, targetTableName);
                    // 将结果放入缓存
                    tableExistsCache.put(tableKey, tableExists);
                } catch (SQLException e) {
                    logError("检查表是否存在失败，" + e.getMessage());
                    if (Boolean.FALSE.equals(this.ignoreError)) {
                        throw new RuntimeException("数据迁移失败", e);
                    }
                    return;
                }
            }
            
            if (tableExists) {
                logInfo("表 " + tableKey + " 已存在，跳过数据导入");
                return;
            }
        }

        // 获取源表元数据
        TableMeta targetTable = getTableMeta(targetTableName, targetSchema, flowFile);
        if (flowFile.getJsonArray().isEmpty()) {
            return;
        }
        try (Connection targetConn = DBUtils.getConnection(this.datasource)) {
            // 流式数据迁移
            migrateData(targetConn, targetTable, flowFile);
        } catch (SQLException e) {
            logError("数据迁移失败，表名：" + targetTable.getTable() + "，" + e.getMessage());
            if (Boolean.FALSE.equals(this.ignoreError)) {
                throw new RuntimeException("数据迁移失败", e);
            }
        }
    }

    /**
     * 获取目标表名
     * 优先使用配置的table参数，未配置时从flowfile属性获取
     */
    private String getTargetTableName(FlowFile flowFile) {
        if (this.table != null && !this.table.trim().isEmpty()) {
            return this.table;
        }

        // 从flowfile属性获取表名
        Object tableAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE);
        if (tableAttr != null) {
            return tableAttr.toString();
        }

        // 从表元数据获取表名
        TableMeta tableMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (tableMeta != null && tableMeta.getTable() != null) {
            return tableMeta.getTable();
        }

        throw new IllegalArgumentException("未配置目标表名，且无法从flowfile属性中获取表名");
    }

    /**
     * 获取目标schema
     * 优先使用数据源的schema，未配置时从flowfile属性获取
     */
    private String getTargetSchema(FlowFile flowFile) {
        if (this.schema != null && !this.schema.trim().isEmpty()) {
            return this.schema;
        }

        if (
            this.datasource != null &&
            this.datasource.getDbschema() != null &&
            !this.datasource.getDbschema().trim().isEmpty()
        ) {
            return this.datasource.getDbschema();
        }

        // 从flowfile属性获取数据库名作为schema
        Object databaseAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_DATABASE);
        if (databaseAttr != null) {
            return databaseAttr.toString();
        }

        if (this.datasource != null && this.datasource.getDbschema() != null) {
            return this.datasource.getDbschema();
        }

        // 从表元数据获取schema
        TableMeta tableMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (tableMeta != null && tableMeta.getSchema() != null) {
            return tableMeta.getSchema();
        }

        // 如果都没有设置，使用默认schema
        return this.datasource.getDbschema();
    }

    private synchronized TableMeta getTableMeta(String table, String schema, FlowFile flowFile) {
        String tableKey = schema + "." + table;
        TableMeta targetTable = this.tableMetaMap.get(tableKey);
        if (targetTable == null) {
            try (Connection targetConn = DBUtils.getConnection(datasource)) {
                // 先从缓存中获取表存在性
                Boolean tableExists = tableExistsCache.get(tableKey);
                
                // 如果缓存中没有，再从数据库中查询
                if (tableExists == null) {
                    tableExists = DBUtils.tableExists(targetConn, schema, table);
                    // 将结果放入缓存
                    tableExistsCache.put(tableKey, tableExists);
                }
                
                // 处理overwrite模式且dropIfTableExists为true的情况
                if ("overwrite".equals(this.model) && Boolean.TRUE.equals(this.dropIfTableExists) && tableExists) {
                    logInfo("模式为overwrite且dropIfTableExists为true，删除表 " + schema + "." + table);
                    DBUtils.execute(targetConn, String.format("DROP TABLE %s", qualifiedTableName(schema, table)));
                    tableExists = false; // 表已被删除
                    tableExistsCache.put(tableKey, false); // 更新缓存
                }
                
                if (!tableExists) {
                    String targetDbType = DBUtils.getDBType(this.datasource.getUrl());
                    if (flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA) != null) {
                        logInfo("表 " + schema + "." + table + " 不存在，从flowfile属性获取表元数据");
                        TableMeta sourceTable = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
                        targetTable = DatabaseConverter.convert(sourceTable.getDbType(), targetDbType, sourceTable);
                        targetTable.setDbType(targetDbType);
                        targetTable.setTable(table);
                        targetTable.setSchema(schema);
                        targetTable.setCatalog(resolveTargetCatalog());
                    }
                    logInfo("表 " + schema + "." + table + " 不存在，创建表");
                    String ddl = DatabaseConverter.generateTableDDL(targetDbType, targetTable);
                    DBUtils.execute(targetConn, ddl);
                } else {
                    logInfo("表 " + schema + "." + table + " 已存在");
                    targetTable = DBUtils.getTableMetaData(targetConn, schema, table);
                }
                if (targetTable != null && resolveTargetCatalog() != null) {
                    // Quack/DuckLake 目标需带上远程 catalog 限定，写入才会落到远端
                    targetTable.setCatalog(resolveTargetCatalog());
                }

                // 处理overwrite模式（非dropIfTableExists为true的情况）
                if ("overwrite".equals(this.model) && !Boolean.TRUE.equals(this.dropIfTableExists)) {
                    logInfo("清空表 " + schema + "." + table);
                    DBUtils.execute(targetConn, String.format("DELETE FROM %s", qualifiedTableName(schema, table)));
                }
            } catch (SQLException e) {
                logError("获取表元数据失败，" + e.getMessage());
                throw new RuntimeException(e);
            }

            this.tableMetaMap.put(tableKey, targetTable);
        }

        return targetTable;
    }

    private void migrateData(Connection targetConn, TableMeta targetTable, FlowFile flowFile) throws SQLException {
        String targetDbType = DBUtils.getDBType(this.datasource.getUrl());
        String eventType;
        if ("auto".equals(this.model)) {
            eventType = getEventType(flowFile);
            if (eventType == null) {
                throw new IllegalArgumentException("无法从flowfile属性中获取事件类型");
            }
        } else {
            eventType = this.model.toUpperCase();
        }

        if ("duckdb".equalsIgnoreCase(targetDbType) && !isQuackTarget() && canUseDuckDBAppender(eventType)) {
            migrateDataWithDuckDBAppender(targetConn, targetTable, flowFile);
        } else {
            migrateDataWithJDBC(targetConn, targetTable, flowFile, targetDbType, eventType);
        }
    }

    private boolean canUseDuckDBAppender(String eventType) {
        return "INSERT".equals(eventType) || "APPEND".equals(eventType) || "OVERWRITE".equals(eventType);
    }

    private boolean isQuackTarget() {
        return this.datasource != null && this.datasource.getUrl() != null && this.datasource.getUrl().startsWith("quack:");
    }

    private boolean isDuckLakeTarget() {
        return this.datasource != null && this.datasource.getUrl() != null && this.datasource.getUrl().startsWith("ducklake:");
    }

    /**
     * 目标 catalog：Quack/DuckLake 需要显式限定 catalog，否则会落到默认的 memory catalog。
     */
    private String resolveTargetCatalog() {
        if (isQuackTarget()) {
            return DBUtils.QUACK_CATALOG;
        }
        if (isDuckLakeTarget()) {
            return DBUtils.DUCKLAKE_CATALOG;
        }
        return null;
    }

    /**
     * 生成带 catalog 限定的表名（仅 DuckLake 需要，Quack 有独立的远程执行逻辑）。
     */
    private String qualifiedTableName(String schema, String table) {
        if (isDuckLakeTarget()) {
            return DBUtils.DUCKLAKE_CATALOG + "." + schema + "." + table;
        }
        return schema + "." + table;
    }

    private void migrateDataWithJDBC(Connection targetConn, TableMeta targetTable, FlowFile flowFile,
                                      String targetDbType, String eventType) throws SQLException {
        String writeSQL = DatabaseConverter.generateWriteSQL(targetDbType, targetTable, eventType);
        try (PreparedStatement targetStmt = targetConn.prepareStatement(writeSQL)) {
            JSONArray records = flowFile.getJsonArray();

            for (Object o : records) {
                JSONObject record = (JSONObject) o;
                setStatementParameters(targetStmt, targetTable, record, eventType);
                targetStmt.addBatch();
            }

            targetStmt.executeBatch();
            this.logInfo("已成功写入 " + records.size() + " 条记录到表 " + targetTable.getTable() + "，写入模式：" + eventType);
        }
    }

    private void migrateDataWithDuckDBAppender(Connection targetConn, TableMeta targetTable, FlowFile flowFile) throws SQLException {
        DuckDBConnection duckDBConnection = (DuckDBConnection) DBUtils.getRawConnection((HikariProxyConnection) targetConn);
        JSONArray records = flowFile.getJsonArray();

        Map<String, String> columnMapping = null;
        if (header_map != null && !header_map.isEmpty()) {
            columnMapping = new HashMap<>();
            for (Map<String, Object> mapping : header_map) {
                String inColumn = (String) mapping.get("source");
                String outColumn = (String) mapping.get("target");
                if (inColumn != null && outColumn != null) {
                    columnMapping.put(outColumn, inColumn);
                }
            }
        }

        try (DuckDBAppender appender = duckDBConnection.createAppender(targetTable.getSchema(), targetTable.getTable())) {
            for (Object o : records) {
                appender.beginRow();
                JSONObject record = (JSONObject) o;
                for (int i = 0; i < targetTable.columns().size(); ++i) {
                    ColumnMeta column = targetTable.columns().get(i);
                    String fieldType = column.getType();
                    Object value;
                    if (columnMapping != null) {
                        String sourceColumnName = columnMapping.getOrDefault(column.getName(), column.getName());
                        value = record.get(sourceColumnName);
                    } else {
                        value = record.get(column.getName());
                    }
                    DBUtils.appendValue(appender, fieldType, value);
                }
                appender.endRow();
            }
        }
        this.logInfo("已成功通过 DuckDBAppender 写入 " + records.size() + " 条记录到表 " + targetTable.getTable());
    }

    /**
     * 执行query事件类型的SQL语句
     */
    private void executeDDLSQL(Connection targetConn, FlowFile flowFile) throws SQLException {
        // 从flowfile属性获取SQL语句
        Object sqlAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_QUERY);
        if (sqlAttr == null) {
            throw new IllegalArgumentException("query事件类型需要提供_query属性");
        }

        String sql = sqlAttr.toString();

        // 如果没有记录数据，直接执行SQL
        try (Statement stmt = targetConn.createStatement()) {
            stmt.execute(sql);
            int updateCount = stmt.getUpdateCount();
            this.logInfo("已成功执行DDL语句，影响行数：" + updateCount);
        }
    }

    /**
     * 从FlowFile中获取事件类型
     */
    private String getEventType(FlowFile flowFile) {
        Object eventTypeAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE);
        if (eventTypeAttr != null) {
            return eventTypeAttr.toString().toUpperCase();
        }
        return null;
    }

    /**
     * 根据事件类型设置PreparedStatement参数
     */
    private void setStatementParameters(PreparedStatement stmt, TableMeta tableMeta, JSONObject record, String eventType)
        throws SQLException {
        if ("DELETE".equals(eventType)) {
            // DELETE语句只需要设置WHERE条件中的参数（通常是主键）
            setDeleteParameters(stmt, tableMeta, record);
        } else {
            // INSERT/UPDATE/REPLACE语句需要设置所有列的值
            setInsertUpdateParameters(stmt, tableMeta, record);
        }
    }

    /**
     * 设置DELETE语句的参数（只设置WHERE条件中的列）
     */
    private void setDeleteParameters(PreparedStatement stmt, TableMeta tableMeta, JSONObject record) throws SQLException {
        List<ColumnMeta> primaryKeys = getPrimaryKeyColumns(tableMeta);
        if (primaryKeys.isEmpty()) {
            // 如果没有主键，使用所有列作为WHERE条件
            primaryKeys = tableMeta.columns();
        }

        for (int i = 0; i < primaryKeys.size(); i++) {
            ColumnMeta column = primaryKeys.get(i);
            Object value = record.get(column.getName());
            stmt.setObject(i + 1, DBUtils.normalizeParameter(column.getType(), value));
        }
    }

    /**
     * 设置INSERT/UPDATE/REPLACE语句的参数（设置所有列的值）
     */
    private void setInsertUpdateParameters(PreparedStatement stmt, TableMeta tableMeta, JSONObject record) throws SQLException {
        // 如果设置了字段映射，根据字段映射设置value
        if (header_map != null && !header_map.isEmpty()) {
            setInsertUpdateParametersWithMapping(stmt, tableMeta, record);
        } else {
            // 没有字段映射时使用原来的逻辑
            for (int i = 0; i < tableMeta.columns().size(); i++) {
                ColumnMeta column = tableMeta.columns().get(i);
                Object value = record.get(column.getName());
                stmt.setObject(i + 1, DBUtils.normalizeParameter(column.getType(), value));
            }
        }
    }

    /**
     * 根据字段映射设置INSERT/UPDATE/REPLACE语句的参数
     */
    private void setInsertUpdateParametersWithMapping(PreparedStatement stmt, TableMeta tableMeta, JSONObject record) throws SQLException {
        // 创建字段映射关系：目标字段名 -> 源字段名
        Map<String, String> columnMapping = new HashMap<>();
        for (Map<String, Object> mapping : header_map) {
            String inColumn = (String) mapping.get("source");
            String outColumn = (String) mapping.get("target");
            if (inColumn != null && outColumn != null) {
                columnMapping.put(outColumn, inColumn);
            }
        }
        
        // 根据目标表的列顺序设置参数值
        for (int i = 0; i < tableMeta.columns().size(); i++) {
            ColumnMeta targetColumn = tableMeta.columns().get(i);
            String targetColumnName = targetColumn.getName();
            
            // 查找对应的源字段名
            String sourceColumnName = columnMapping.get(targetColumnName);
            if (sourceColumnName == null) {
                // 如果没有映射关系，使用目标字段名作为源字段名
                sourceColumnName = targetColumnName;
            }
            
            // 从记录中获取源字段的值
            Object value = record.get(sourceColumnName);
            stmt.setObject(i + 1, DBUtils.normalizeParameter(targetColumn.getType(), value));
        }
    }

    /**
     * 获取表的主键列
     */
    private List<ColumnMeta> getPrimaryKeyColumns(TableMeta tableMeta) {
        List<ColumnMeta> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.isPrimaryKey()) {
                primaryKeys.add(column);
            }
        }
        return primaryKeys;
    }

    // 自动注入方法
    public void setTable(String table) {
        this.table = table;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setSkipIfTableExists(Boolean skipIfTableExists) {
        this.skipIfTableExists = skipIfTableExists;
    }

    public Boolean getDropIfTableExists() {
        return dropIfTableExists;
    }

    public void setDropIfTableExists(Boolean dropIfTableExists) {
        this.dropIfTableExists = dropIfTableExists;
    }

    public void setIgnoreError(Boolean ignoreError) {
        this.ignoreError = ignoreError;
    }

    public void setHeader_map(List<Map<String, Object>> header_map) {
        this.header_map = header_map;
    }

    public List<Map<String, Object>> getHeader_map() {
        return header_map;
    }
}