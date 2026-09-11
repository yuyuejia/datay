package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuckLakeWrite extends FlowComponent {

    // 对应job.json参数
    private String table;
    private String model; // 写入模式：append, update, replace, overwrite
    private String schema;
    
    private DatasourceInfo datasource;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    private boolean isAttach = false;

    public static String CATALOG = "ducklake";

     public DuckLakeWrite() {
        setType(ComponentType.SINK);  // 设置为Sink类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 调试模式：跳过实际写库
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入 DuckLake");
            return;
        }
        if (flowFile.getData() == null) {
            return;
        }

        // 处理query事件类型, 执行DDL语句
        if ("auto".equals(this.model) && ("QUERY".equals(getEventType(flowFile)) || "DDL".equals(getEventType(flowFile)))) {
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

        // 获取源表元数据
        TableMeta targetTable = getTableMeta(targetTableName, targetSchema, flowFile);
        targetTable.setCatalog(CATALOG);
        if (flowFile.getJsonArray().isEmpty()) {
            return;
        }

        try (Connection targetConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            setupDuckLakeConnection(targetConn);
            // 流式数据写入DuckLake
            migrateData(targetConn, targetTable, flowFile);
        } catch (SQLException e) {
            logError("DuckLake数据写入失败，" + e.getMessage());
            throw new RuntimeException("DuckLake数据写入失败", e);
        }
    }

    private void setupDuckLakeConnection(Connection conn) throws SQLException {
        if (!isAttach) {
            Map<String, String> extraParams = this.datasource.getExtraParams();
            String store = null;
            if (extraParams != null) {
                store = extraParams.get("s3.data_path");
            }

            if (store == null || store.trim().isEmpty()) {
                throw new IllegalArgumentException("DuckLake数据源配置中缺少 data_path 参数 (s3.data_path)");
            }

            boolean isS3Path = store.startsWith("s3://");

            if (isS3Path) {
                String keyId = extraParams.get("s3.key_id");
                String secret = extraParams.get("s3.secret");
                String endpoint = extraParams.get("s3.endpoint");
                String urlStyle = extraParams.get("s3.url_style");
                String useSsl = extraParams.get("s3.use_ssl");

                if (keyId == null || secret == null || endpoint == null || urlStyle == null || useSsl == null) {
                    throw new IllegalArgumentException("DuckLake数据源配置中S3配置不完整");
                }
                String s3Secret = String.format(
                        """
                                CREATE OR REPLACE SECRET (
                                      TYPE s3,
                                      KEY_ID '%s',
                                      SECRET '%s',
                                      ENDPOINT '%s',
                                      url_style '%s',
                                      USE_SSL '%s'
                                  );""",
                    keyId,
                    secret,
                    endpoint,
                    urlStyle,
                    useSsl
                );
                conn.createStatement().execute(s3Secret);
            }

            String attachSql = String.format("ATTACH '%s' AS %s (data_path '%s');", datasource.getUrl(), CATALOG, store);
            conn.createStatement().execute(attachSql);
            isAttach = true;
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
            this.table != null &&
            !this.table.trim().isEmpty() &&
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

    /**
     * 获取表元数据，如果表不存在则自动创建
     */
    private TableMeta getTableMeta(String table, String schema, FlowFile flowFile) {
        String tableKey = schema + "." + table;
        TableMeta targetTable = this.tableMetaMap.get(tableKey);
        if (targetTable == null) {
            try (Connection targetConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
                setupDuckLakeConnection(targetConn);
                if (!DBUtils.tableExists(targetConn, CATALOG, schema, table)) {
                    if (flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA) != null) {
                        logInfo("表 " + schema + "." + table + " 不存在，从flowfile属性获取表元数据");
                        TableMeta sourceTable = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
                        targetTable = DatabaseConverter.convert(sourceTable.getDbType(), DBType.DUCKLAKE.name(), sourceTable);
                        targetTable.setDbType(DBType.DUCKLAKE.name());
                        targetTable.setTable(table);
                        targetTable.setCatalog(CATALOG);
                        targetTable.setSchema(schema);
                    }
                    logInfo("表 " + schema + "." + table + " 不存在，创建表");
                    String createSchemaSql = String.format("CREATE SCHEMA IF NOT EXISTS %s.%s", CATALOG, schema);
                    logInfo("创建Schema: " + createSchemaSql);
                    DBUtils.execute(targetConn, createSchemaSql);
                    String ddl = DatabaseConverter.generateTableDDL(DBType.DUCKLAKE.name(), targetTable);
                    logInfo("创建表SQL: " + ddl);
                    DBUtils.execute(targetConn, ddl);
                } else {
                    logInfo("表 " + schema + "." + table + " 已存在");
                    targetTable = DBUtils.getTableMetaData(targetConn, CATALOG,schema, table);
                    // 处理overwrite模式
                    if ("overwrite".equals(this.model)) {
                        logInfo("清空表 " + table);
                        DBUtils.execute(targetConn, String.format("TRUNCATE TABLE %s.%s", CATALOG, table));
                    }
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
        String eventType = null;
        if ("auto".equals(this.model)) {
            eventType = getEventType(flowFile);
            if (eventType == null) {
                throw new IllegalArgumentException("无法从flowfile属性中获取事件类型");
            }
        } else {
            eventType = this.model.toUpperCase();
        }
    
        JSONArray records = flowFile.getJsonArray();
        if (records.isEmpty()) {
            return;
        }
    
        // 对于INSERT操作，使用多值INSERT语句
        if ("INSERT".equals(eventType) || "APPEND".equals(eventType) || "OVERWRITE".equals(eventType)) {
            executeBatchInsertWithValues(targetConn, targetTable, records);
        } else {
            // 对于其他操作类型，使用原来的批量执行方式
            String writeSQL = DatabaseConverter.generateWriteSQL(DBType.DUCKLAKE.name(), targetTable, eventType);
            try (PreparedStatement targetStmt = targetConn.prepareStatement(writeSQL);) {
                for (Object o : records) {
                    JSONObject record = (JSONObject) o;
                    setStatementParameters(targetStmt, targetTable, record, eventType);
                    targetStmt.addBatch();
                }
    
                targetStmt.executeBatch();
                this.logInfo("已成功写入 " + records.size() + " 条记录到表 " + targetTable.getTable() + "，写入模式：" + eventType);
            }
        }
    }
    
    /**
     * 执行批量INSERT语句，使用INSERT INTO ... VALUES (...), (...), (...)格式
     */
    private void executeBatchInsertWithValues(Connection targetConn, TableMeta targetTable, JSONArray records) throws SQLException {
        // 构建INSERT语句的列名部分
        List<String> columnNames = new ArrayList<>();
        for (ColumnMeta column : targetTable.columns()) {
            columnNames.add(column.getName());
        }
        String columns = String.join(", ", columnNames);
        
        // 构建表名
        String tableName = targetTable.getCatalog() + "." + targetTable.getSchema() + "." + targetTable.getTable();
//        String tableName = CATALOG + "." + targetTable.getTable();

        // 构建SQL语句
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("INSERT INTO ").append(tableName).append(" (").append(columns).append(") VALUES ");
        
        // 添加所有记录的VALUES部分
        List<Object> allParameters = new ArrayList<>();
        int columnCount = columnNames.size();
        
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append("(");
        
            // 添加当前记录的参数占位符
            for (int j = 0; j < columnCount; j++) {
                if (j > 0) {
                    sqlBuilder.append(", ");
                }
                sqlBuilder.append("?");
            }
            sqlBuilder.append(")");
        
            // 收集当前记录的所有参数值
            JSONObject record = records.getJSONObject(i);
            for (ColumnMeta column : targetTable.columns()) {
                Object value = record.get(column.getName());
                allParameters.add(value);
            }
        }
        
        String sql = sqlBuilder.toString();
        // 执行SQL语句
        try (PreparedStatement stmt = targetConn.prepareStatement(sql)) {
            // 设置所有参数
            for (int i = 0; i < allParameters.size(); i++) {
                stmt.setObject(i + 1, allParameters.get(i));
            }
            
            stmt.executeUpdate();
            this.logInfo("已成功写入 " + records.size() + " 条记录到表 " + targetTable.getTable() + "，使用多值INSERT格式");
        }
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

        // 执行SQL
        try (Statement stmt = targetConn.createStatement()) {
            stmt.execute(sql);
            int updateCount = stmt.getUpdateCount();
            logInfo("DDL语句执行成功，影响行数: " + updateCount);
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
            stmt.setObject(i + 1, value);
        }
    }

    /**
     * 设置INSERT/UPDATE/REPLACE语句的参数（设置所有列的值）
     */
    private void setInsertUpdateParameters(PreparedStatement stmt, TableMeta tableMeta, JSONObject record) throws SQLException {
        for (int i = 0; i < tableMeta.columns().size(); i++) {
            ColumnMeta column = tableMeta.columns().get(i);
            Object value = record.get(column.getName());
            stmt.setObject(i + 1, value);
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

    public boolean isAttach() {
        return isAttach;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}