package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 目标表名映射：key 为上游表名（通常是源表名），value 为目标表名。
     * 用于数据同步任务中「目标表重命名」的场景，未命中时沿用上游表名。
     */
    private Map<String, String> tableMapping;

    private DatasourceInfo datasource;

    private final Map<String, TableMeta> tableMetaMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>(); // 表存在性缓存

    /**
     * 目标字段 -> 源字段映射缓存：schema.table -> (目标字段实际名 -> 源数据实际 key)。
     * 配置了 header_map 时以配置为准；未配置时按同名忽略大小写自动匹配。
     */
    private final Map<String, Map<String, String>> columnMappingCache = new ConcurrentHashMap<>();

    public StreamJdbcOutput() {
        setType(ComponentType.SINK);  // 设置为Sink类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        try {
            executeInternal(flowFile);
        } catch (RuntimeException e) {
            if (!Boolean.TRUE.equals(this.ignoreError)) {
                // CDC 合并 FlowFile 默认带 _error_ignore，Sink 默认应让异常暴露出来，
                // 否则写入失败会被 FlowComponent 静默忽略，流任务继续运行并丢失该批次数据。
                flowFile.getAttributeMap().remove(FlowFile.ATTRIBUTE_ERROR_IGNORE);
            }
            throw e;
        }
    }

    private void executeInternal(FlowFile flowFile) {
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
     * 优先使用配置的table参数，未配置时从flowfile属性获取；
     * 之后按 tableMapping 做表名重命名（源表名 -> 目标表名）。
     */
    private String getTargetTableName(FlowFile flowFile) {
        String sourceTableName = null;
        if (this.table != null && !this.table.trim().isEmpty()) {
            sourceTableName = this.table;
        } else {
            // 从flowfile属性获取表名
            Object tableAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE);
            if (tableAttr != null) {
                sourceTableName = tableAttr.toString();
            } else {
                // 从表元数据获取表名
                TableMeta tableMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
                if (tableMeta != null && tableMeta.getTable() != null) {
                    sourceTableName = tableMeta.getTable();
                }
            }
        }

        if (sourceTableName == null || sourceTableName.trim().isEmpty()) {
            throw new IllegalArgumentException("未配置目标表名，且无法从flowfile属性中获取表名");
        }

        String targetTableName = resolveMappedTableName(sourceTableName);
        JdbcDataSourceProvider provider = jdbcProvider();
        if (provider != null) {
            targetTableName = provider.normalizeTargetTableName(datasource, targetTableName);
        }
        return targetTableName;
    }

    /**
     * 按 tableMapping 解析目标表名。
     * 未配置映射或未命中时返回原表名；同时兼容大小写不一致的表名（如 Oracle 大写、MySQL 小写）。
     */
    private String resolveMappedTableName(String tableName) {
        if (this.tableMapping == null || this.tableMapping.isEmpty()) {
            return tableName;
        }

        String mapped = this.tableMapping.get(tableName);
        if (mapped != null && !mapped.trim().isEmpty()) {
            return mapped;
        }

        for (Map.Entry<String, String> entry : this.tableMapping.entrySet()) {
            if (
                entry.getKey() != null &&
                entry.getKey().equalsIgnoreCase(tableName) &&
                entry.getValue() != null &&
                !entry.getValue().trim().isEmpty()
            ) {
                return entry.getValue();
            }
        }

        return tableName;
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
                
                String catalog = targetCatalog();
                if (!tableExists) {
                    String targetDbType = DBUtils.getDBType(this.datasource.getType(), this.datasource.getUrl());
                    if (flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA) != null) {
                        logInfo("表 " + schema + "." + table + " 不存在，从flowfile属性获取表元数据");
                        TableMeta sourceTable = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
                        targetTable = DatabaseConverter.convert(sourceTable.getDbType(), targetDbType, sourceTable);
                        targetTable.setDbType(targetDbType);
                        targetTable.setTable(table);
                        targetTable.setSchema(schema);
                        targetTable.setCatalog(catalog);
                    }
                    // 目标命名空间准备（如 Iceberg namespace 不存在时自动创建）
                    JdbcDataSourceProvider targetProvider = jdbcProvider();
                    if (targetProvider != null) {
                        targetProvider.prepareTableCreate(datasource, targetConn, catalog, schema);
                    }
                    logInfo("表 " + schema + "." + table + " 不存在，创建表");
                    String ddl = DatabaseConverter.generateTableDDL(targetDbType, targetTable);
                    DBUtils.execute(targetConn, ddl);
                    // 建表后回读实际元数据：部分数据库会规范化标识符大小写（如 PostgreSQL 统一小写），
                    // 后续 DML 必须按真实字段名生成，不能继续使用源表转换结果。
                    targetTable = refreshCreatedTableMeta(targetConn, schema, table, targetTable, catalog);
                } else {
                    logInfo("表 " + schema + "." + table + " 已存在");
                    targetTable = DBUtils.getTableMetaData(this.datasource, targetConn, schema, table);
                }
                if (targetTable != null && catalog != null) {
                    // 需要显式 catalog 的数据源（由 Provider 决定）在建表后补上 catalog 限定
                    targetTable.setCatalog(catalog);
                }

                // 处理overwrite模式（非dropIfTableExists为true的情况）
                if ("overwrite".equals(this.model) && !Boolean.TRUE.equals(this.dropIfTableExists)) {
                    logInfo("清空表 " + schema + "." + table);
                    String clearSql = useTruncateForOverwrite()
                        ? String.format("TRUNCATE TABLE %s", qualifiedTableName(schema, table))
                        : String.format("DELETE FROM %s", qualifiedTableName(schema, table));
                    DBUtils.execute(targetConn, clearSql);
                }
            } catch (SQLException e) {
                logError("获取表元数据失败，" + e.getMessage());
                throw new RuntimeException(e);
            }

            this.tableMetaMap.put(tableKey, targetTable);
        }

        return targetTable;
    }

    /**
     * 自动建表后回读目标表实际元数据，确保字段名大小写与数据库一致。
     * <p>读取失败时沿用建表前按源表转换出的元数据。</p>
     */
    private TableMeta refreshCreatedTableMeta(Connection targetConn, String schema, String table, TableMeta fallback,
                                              String catalog) {
        try {
            TableMeta actual = DBUtils.getTableMetaData(this.datasource, targetConn, schema, table);
            if (actual != null && actual.columns() != null && !actual.columns().isEmpty()) {
                if (catalog != null) {
                    actual.setCatalog(catalog);
                }
                // 目标库可能不返回主键元数据（如 Doris 的 UNIQUE KEY 表走 MySQL 协议时），
                // 但目标表是按上游元数据创建的，主键信息必须沿用上游，否则 DELETE / 主键变更
                // 会退化为按全部字段匹配，导致删除不到数据或误删。
                if (!hasPrimaryKey(actual) && fallback != null) {
                    DBUtils.applyPrimaryKeys(actual, fallback);
                }
                return actual;
            }
        } catch (SQLException e) {
            logDebug("读取新建表元数据失败，沿用源表转换结果：" + e.getMessage());
        }
        return fallback;
    }

    private boolean hasPrimaryKey(TableMeta table) {
        if (table == null || table.columns() == null) {
            return false;
        }
        for (ColumnMeta column : table.columns()) {
            if (column.isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }

    private void migrateData(Connection targetConn, TableMeta targetTable, FlowFile flowFile) throws SQLException {
        String targetDbType = DBUtils.getDBType(this.datasource.getType(), this.datasource.getUrl());
        String eventType;
        if ("auto".equals(this.model)) {
            eventType = getEventType(flowFile);
            if (eventType == null) {
                throw new IllegalArgumentException("无法从flowfile属性中获取事件类型");
            }
        } else {
            eventType = this.model.toUpperCase();
        }

        JdbcDataSourceProvider provider = jdbcProvider();
        if (provider != null) {
            // 写入前校验（如 TDengine 超级表不能直接普通写入）
            provider.validateWriteTarget(datasource, targetConn, targetTable, eventType);
        }
        Map<String, String> columnMapping = resolveColumnMapping(targetTable, flowFile);
        if (provider != null && provider.supportsTableWrite(datasource, targetTable, eventType)) {
            String writeMode = provider.writeTable(
                datasource,
                targetConn,
                targetTable,
                flowFile.getJsonArray(),
                eventType,
                columnMapping
            );
            if (writeMode != null) {
                this.logInfo(
                    "已成功通过 " + writeMode + " 写入 " + flowFile.getJsonArray().size() + " 条记录到表 " +
                    targetTable.getTable() + "，写入模式：" + eventType
                );
                return;
            }
            // 专用写入方式本次不可用（如 Appender 创建失败且尚未写入任何行），回退通用 JDBC 写入
            this.logDebug("专用写入方式不可用，回退通用 JDBC 写入，表：" + targetTable.getTable());
        }
        migrateDataWithJDBC(targetConn, targetTable, flowFile, targetDbType, eventType, columnMapping);
    }

    private JdbcDataSourceProvider jdbcProvider() {
        return DBUtils.resolveJdbcProvider(datasource);
    }

    private String targetCatalog() {
        JdbcDataSourceProvider provider = jdbcProvider();
        return provider == null ? null : provider.targetCatalog(datasource);
    }

    private boolean useTruncateForOverwrite() {
        JdbcDataSourceProvider provider = jdbcProvider();
        return provider != null && provider.useTruncateForOverwrite();
    }

    private String qualifiedTableName(String schema, String table) {
        JdbcDataSourceProvider provider = jdbcProvider();
        if (provider == null) {
            return schema == null || schema.isBlank() ? table : schema + "." + table;
        }
        return provider.qualifiedTableName(datasource, schema, table);
    }

    private void migrateDataWithJDBC(Connection targetConn, TableMeta targetTable, FlowFile flowFile,
                                      String targetDbType, String eventType, Map<String, String> columnMapping)
        throws SQLException {
        String writeSQL = DatabaseConverter.generateWriteSQL(targetDbType, targetTable, eventType);
        PreparedStatement deleteStmt = null;
        try {
            // 只有 UPDATE 才需要处理「主键变更」，避免 INSERT/DELETE 额外 prepared statement 开销
            if ("UPDATE".equals(eventType)) {
                deleteStmt = targetConn.prepareStatement(DatabaseConverter.generateWriteSQL(targetDbType, targetTable, "delete"));
            }
            try (PreparedStatement targetStmt = targetConn.prepareStatement(writeSQL)) {
                JSONArray records = flowFile.getJsonArray();
                boolean hasPendingBatch = false;

                for (Object o : records) {
                    JSONObject record = (JSONObject) o;
                    // UPDATE 修改主键时，after 镜像会落到新主键上；先按 before 镜像删除旧主键，
                    // 否则目标端会残留一条旧记录，导致结果集与源库不一致。
                    if (deleteStmt != null && primaryKeyChanged(targetTable, record, columnMapping)) {
                        if (hasPendingBatch) {
                            targetStmt.executeBatch();
                            hasPendingBatch = false;
                        }
                        setDeleteParameters(deleteStmt, targetTable, record.getJSONObject("__before"), targetDbType, columnMapping);
                        deleteStmt.executeUpdate();
                    }
                    setStatementParameters(targetStmt, targetTable, record, eventType, targetDbType, columnMapping);
                    targetStmt.addBatch();
                    hasPendingBatch = true;
                }

                targetStmt.executeBatch();
                this.logInfo("已成功写入 " + records.size() + " 条记录到表 " + targetTable.getTable() + "，写入模式：" + eventType);
            }
        } finally {
            if (deleteStmt != null) {
                deleteStmt.close();
            }
        }
    }

    /**
     * 解析目标字段 -> 源字段映射（忽略大小写）。
     * <p>配置了 header_map 时以配置为准；未配置时按上下游同名字段自动匹配。
     * 映射结果按目标表缓存，后续批次直接复用。</p>
     */
    private Map<String, String> resolveColumnMapping(TableMeta targetTable, FlowFile flowFile) {
        String tableKey = targetTable.getCatalog() + "." + targetTable.getSchema() + "." + targetTable.getTable();
        Map<String, String> cached = this.columnMappingCache.get(tableKey);
        if (cached != null) {
            return cached;
        }
        Map<String, String> columnMapping = buildColumnMapping(targetTable, flowFile);
        this.columnMappingCache.put(tableKey, columnMapping);
        if (header_map == null || header_map.isEmpty()) {
            this.logDebug("未配置 header_map，按字段名忽略大小写自动匹配，表：" + tableKey + "，映射：" + columnMapping);
        }
        return columnMapping;
    }

    /**
     * 构造目标字段 -> 源字段映射。
     * <p>header_map 中配置的字段名、目标表字段名、源数据字段名均忽略大小写匹配；
     * header_map 未配置映射的目标字段，回退为按同名字段（忽略大小写）自动匹配。</p>
     */
    private Map<String, String> buildColumnMapping(TableMeta targetTable, FlowFile flowFile) {
        Map<String, String> sourceColumns = resolveSourceColumns(flowFile);
        Map<String, String> configuredByTarget = configuredSourceByTarget();

        Map<String, String> columnMapping = new HashMap<>();
        for (ColumnMeta targetColumn : targetTable.columns()) {
            String targetColumnName = targetColumn.getName();
            String sourceColumnName = configuredByTarget.get(normalizeColumnName(targetColumnName));
            if (sourceColumnName == null) {
                // 未配置映射时按目标字段名查找同名源字段
                sourceColumnName = targetColumnName;
            }
            String actualSourceColumn = sourceColumns.get(normalizeColumnName(sourceColumnName));
            columnMapping.put(targetColumnName, actualSourceColumn == null ? sourceColumnName : actualSourceColumn);
        }
        return columnMapping;
    }

    /**
     * header_map 配置：目标字段（小写） -> 源字段。
     */
    private Map<String, String> configuredSourceByTarget() {
        Map<String, String> mapping = new HashMap<>();
        if (header_map == null || header_map.isEmpty()) {
            return mapping;
        }
        for (Map<String, Object> entry : header_map) {
            if (entry == null) {
                continue;
            }
            Object source = entry.get("source");
            Object target = entry.get("target");
            if (source == null || target == null) {
                continue;
            }
            String sourceColumn = source.toString().trim();
            String targetColumn = target.toString().trim();
            if (sourceColumn.isEmpty() || targetColumn.isEmpty()) {
                continue;
            }
            mapping.put(normalizeColumnName(targetColumn), sourceColumn);
        }
        return mapping;
    }

    /**
     * 源字段索引：小写字段名 -> 实际字段名。
     * <p>优先使用记录中的实际 key（保证大小写与数据一致），再补充源表元数据。</p>
     */
    private Map<String, String> resolveSourceColumns(FlowFile flowFile) {
        Map<String, String> sourceColumns = new LinkedHashMap<>();
        JSONArray records = flowFile == null ? null : flowFile.getJsonArray();
        if (records != null && !records.isEmpty() && records.get(0) instanceof JSONObject) {
            for (String key : ((JSONObject) records.get(0)).keySet()) {
                // CDC 内部字段（如 __before）不参与源字段匹配
                if (!key.startsWith("__")) {
                    sourceColumns.put(normalizeColumnName(key), key);
                }
            }
        }

        Object meta = flowFile == null ? null : flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (meta instanceof TableMeta) {
            for (ColumnMeta column : ((TableMeta) meta).columns()) {
                if (column.getName() != null) {
                    sourceColumns.putIfAbsent(normalizeColumnName(column.getName()), column.getName());
                }
            }
        }
        return sourceColumns;
    }

    private String normalizeColumnName(String columnName) {
        return columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
    }

    /**
     * 按源字段名取值：先精确匹配，再忽略大小写匹配，兼容上下游字段大小写不一致。
     */
    private Object getRecordValue(JSONObject record, String sourceColumnName) {
        if (record == null || sourceColumnName == null) {
            return null;
        }
        if (record.containsKey(sourceColumnName)) {
            return record.get(sourceColumnName);
        }
        for (String key : record.keySet()) {
            if (key.equalsIgnoreCase(sourceColumnName)) {
                return record.get(key);
            }
        }
        return null;
    }

    /**
     * 读取目标字段对应的源字段值。
     */
    private Object getMappedValue(JSONObject record, ColumnMeta targetColumn, Map<String, String> columnMapping) {
        String sourceColumnName = columnMapping == null
            ? targetColumn.getName()
            : columnMapping.getOrDefault(targetColumn.getName(), targetColumn.getName());
        return getRecordValue(record, sourceColumnName);
    }

    private void executeDDLSQL(Connection targetConn, FlowFile flowFile) throws SQLException {
        // 从flowfile属性获取SQL语句
        Object sqlAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_QUERY);
        if (sqlAttr == null) {
            throw new IllegalArgumentException("query事件类型需要提供_query属性");
        }

        String sql = sqlAttr.toString();
        String targetDbType = DBUtils.getDBType(this.datasource.getType(), this.datasource.getUrl());
        Object sourceSchemaAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_DATABASE);
        String sourceSchema = sourceSchemaAttr == null ? null : sourceSchemaAttr.toString();
        String targetSchema = getTargetSchema(flowFile);
        sql = DatabaseConverter.translateDDL(targetDbType, sql, sourceSchema, targetSchema);
        DatabaseConverter.prepareDDLConnection(targetDbType, targetConn, targetSchema);

        // 如果没有记录数据，直接执行SQL
        try (Statement stmt = targetConn.createStatement()) {
            stmt.execute(sql);
            int updateCount = stmt.getUpdateCount();
            this.logInfo("已成功执行DDL语句，影响行数：" + updateCount);
        }
        // DDL 会改变目标表结构，清空元数据缓存，让后续数据事件按新结构绑定参数
        this.tableMetaMap.clear();
        this.tableExistsCache.clear();
        this.columnMappingCache.clear();
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
    private void setStatementParameters(PreparedStatement stmt, TableMeta tableMeta, JSONObject record, String eventType,
                                        String targetDbType, Map<String, String> columnMapping) throws SQLException {
        if ("DELETE".equals(eventType)) {
            // DELETE语句只需要设置WHERE条件中的参数（通常是主键）
            setDeleteParameters(stmt, tableMeta, record, targetDbType, columnMapping);
        } else {
            // INSERT/UPDATE/REPLACE语句需要设置所有列的值
            setInsertUpdateParameters(stmt, tableMeta, record, targetDbType, columnMapping);
        }
    }

    /**
     * 设置DELETE语句的参数（只设置WHERE条件中的列）
     */
    private void setDeleteParameters(PreparedStatement stmt, TableMeta tableMeta, JSONObject record, String targetDbType,
                                     Map<String, String> columnMapping) throws SQLException {
        List<ColumnMeta> primaryKeys = getPrimaryKeyColumns(tableMeta);
        if (primaryKeys.isEmpty()) {
            // 如果没有主键，使用所有列作为WHERE条件
            primaryKeys = tableMeta.columns();
        }

        for (int i = 0; i < primaryKeys.size(); i++) {
            ColumnMeta column = primaryKeys.get(i);
            Object value = getMappedValue(record, column, columnMapping);
            setParameter(stmt, i + 1, column, value, targetDbType);
        }
    }

    /**
     * 设置INSERT/UPDATE/REPLACE语句的参数（设置所有列的值）
     */
    private void setInsertUpdateParameters(PreparedStatement stmt, TableMeta tableMeta, JSONObject record, String targetDbType,
                                           Map<String, String> columnMapping) throws SQLException {
        for (int i = 0; i < tableMeta.columns().size(); i++) {
            ColumnMeta column = tableMeta.columns().get(i);
            Object value = getMappedValue(record, column, columnMapping);
            setParameter(stmt, i + 1, column, value, targetDbType);
        }
    }

    /**
     * 按目标库方言绑定单个参数，具体差异下沉到数据源方言实现。
     */
    private void setParameter(PreparedStatement stmt, int index, ColumnMeta column, Object value, String targetDbType)
        throws SQLException {
        DatabaseConverter.bindParameter(targetDbType, stmt, index, column, value);
    }

    /**
     * UPDATE 事件的主键是否发生变化。
     * <p>CDC 的 UPDATE 事件会把变更前镜像放在 {@code __before} 中。</p>
     */
    private boolean primaryKeyChanged(TableMeta tableMeta, JSONObject record, Map<String, String> columnMapping) {
        JSONObject before = record.getJSONObject("__before");
        if (before == null) {
            return false;
        }
        for (ColumnMeta column : getPrimaryKeyColumns(tableMeta)) {
            String oldValue = stringValue(getMappedValue(before, column, columnMapping));
            String newValue = stringValue(getMappedValue(record, column, columnMapping));
            if (!java.util.Objects.equals(oldValue, newValue)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
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
        this.columnMappingCache.clear();
    }

    public List<Map<String, Object>> getHeader_map() {
        return header_map;
    }

    public void setTableMapping(Map<String, String> tableMapping) {
        this.tableMapping = tableMapping;
    }

    public Map<String, String> getTableMapping() {
        return tableMapping;
    }
}
