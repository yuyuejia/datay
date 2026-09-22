package com.data.job.component;

import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.ColumnMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * GenerateTableSelectSql组件 - 获取指定数据源和schema下的所有表，并生成对应的select语句
 * 该组件可以作为数据流的源头，为下游SqlInput组件提供SQL语句
 * 支持大数据量表同步优化：根据主键字段生成分区SQL，并发执行
 */
@ComponentRegister(
    value = "GenerateTableSelectSql",
    name = "生成表SQL",
    group = "数据输入",
    desc = "获取指定数据源和 schema 下的所有表并生成 select 语句，为下游 SqlInput 提供 SQL。",
    order = 50
)
public class GenerateTableSelectSql extends FlowComponent {

    private DatasourceInfo datasource;
    private String schema;
    private String table; // 新增table属性，支持多表逗号分隔
    private int partitionCount = 4; // 默认分区数量
    private boolean enablePartition = false; // 是否启用分区

    public GenerateTableSelectSql() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        logInfo("开始生成表select语句，数据源: " + datasource.getUrl() + ", schema: " + schema);

        if (datasource == null) {
            throw new IllegalArgumentException("数据源信息不能为空");
        }

        if (schema == null || schema.trim().isEmpty()) {
            throw new IllegalArgumentException("schema参数不能为空");
        }

        try (Connection conn = DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword())) {
            // 获取需要处理的表列表
            List<TableMeta> tableList = getTableList(conn);
            
            if (tableList.isEmpty()) {
                logInfo("没有找到需要处理的表");
                return;
            }

            logInfo("获取到需要处理的表，共 " + tableList.size() + " 个表");

            // 为每个表生成select语句并发送
            for (TableMeta tableMeta : tableList) {
                String tableName = tableMeta.getTable();
                
                // 跳过系统表或临时表
                if (shouldSkipTable(tableName)) {
                    logInfo("跳过表: " + tableName);
                    continue;
                }

                // 获取完整的表元数据（包含主键信息）
                TableMeta fullTableMeta = DBUtils.getTableMetaData(conn, schema, tableName);
                
                // 生成select语句
                List<String> selectSqlList = generateSelectSql(fullTableMeta, conn);
                
                // 为每个SQL创建FlowFile并发送
                for (String selectSql : selectSqlList) {
                    FlowFile sqlFlowFile = createSqlFlowFile(selectSql, fullTableMeta);
                    writeRecords(sqlFlowFile);
                    logInfo("为表 " + tableName + " 生成select语句: " + selectSql);
                }
            }

            logInfo("表select语句生成完成，共处理 " + tableList.size() + " 个表");

        } catch (SQLException e) {
            logError("获取表列表失败: " + e.getMessage());
            throw new RuntimeException("获取表列表失败", e);
        }
    }

    /**
     * 获取需要处理的表列表
     * 如果设置了table属性，则只处理指定的表；否则处理schema下的所有表
     */
    private List<TableMeta> getTableList(Connection conn) throws SQLException {
        List<TableMeta> tableList = new ArrayList<>();
        
        if (table != null && !table.trim().isEmpty()) {
            // 处理指定的表（支持多表逗号分隔）
            String[] tableNames = table.split(",");
            for (String tableName : tableNames) {
                tableName = tableName.trim();
                if (!tableName.isEmpty()) {
                    try {
                        TableMeta tableMeta = DBUtils.getTableMetaData(conn, schema, tableName);
                        if (tableMeta != null) {
                            tableList.add(new TableMeta(tableName, tableMeta.columns()));
                            logInfo("找到指定表: " + tableName);
                        } else {
                            logInfo("未找到指定表: " + tableName);
                        }
                    } catch (SQLException e) {
                        logInfo("获取表 " + tableName + " 元数据失败: " + e.getMessage());
                    }
                }
            }
        } else {
            // 获取schema下的所有表
            tableList = DBUtils.getTableList(conn, schema);
            logInfo("获取schema " + schema + " 下的所有表，共 " + tableList.size() + " 个表");
        }
        
        return tableList;
    }

    /**
     * 判断是否应该跳过该表
     */
    private boolean shouldSkipTable(String tableName) {
        // 跳过备份表、临时表等
        return tableName.endsWith("_backup") || 
               tableName.endsWith("_tmp");
    }

    /**
     * 生成表的select语句，支持分区优化
     */
    private List<String> generateSelectSql(TableMeta tableMeta, Connection conn) throws SQLException {
        List<String> sqlList = new ArrayList<>();
        
        // 如果不启用分区或表数据量较小，生成普通SQL
        if (!enablePartition) {
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + schema + "." + tableMeta.getTable());
            return sqlList;
        }
        
        // 获取主键列
        List<ColumnMeta> primaryKeys = getPrimaryKeyColumns(tableMeta);
        if (primaryKeys.isEmpty()) {
            logInfo("表 " + tableMeta.getTable() + " 没有主键，使用普通查询");
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + schema + "." + tableMeta.getTable());
            return sqlList;
        }
        
        // 使用第一个主键进行分区
        ColumnMeta primaryKey = primaryKeys.get(0);
        logInfo("表 " + tableMeta.getTable() + " 使用主键 " + primaryKey.getName() + " 进行分区，分区数: " + partitionCount);
        
        // 获取主键的最小值和最大值
        Object[] minMaxValues = getPrimaryKeyMinMax(conn, tableMeta, primaryKey);
        if (minMaxValues == null) {
            logInfo("无法获取表 " + tableMeta.getTable() + " 的主键范围，使用普通查询");
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + schema + "." + tableMeta.getTable());
            return sqlList;
        }
        
        // 生成分区SQL
        sqlList.addAll(generatePartitionSql(tableMeta, primaryKey, minMaxValues[0], minMaxValues[1]));
        
        return sqlList;
    }

    /**
     * 获取主键列
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

    /**
     * 获取主键的最小值和最大值
     */
    private Object[] getPrimaryKeyMinMax(Connection conn, TableMeta tableMeta, ColumnMeta primaryKey) {
        String sql = "SELECT MIN(" + primaryKey.getName() + "), MAX(" + primaryKey.getName() + ") FROM " + schema + "." + tableMeta.getTable();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                Object minValue = rs.getObject(1);
                Object maxValue = rs.getObject(2);
                
                if (minValue == null || maxValue == null ||minValue.equals("") || maxValue.equals("")) {
                    logInfo("表 " + tableMeta.getTable() + " 的主键值为空");
                    return null;
                }
                logInfo("表 " + tableMeta.getTable() + " 的主键范围: " + minValue + " - " + maxValue);
                return new Object[]{minValue, maxValue};
            }
        } catch (SQLException e) {
            logInfo("获取表 " + tableMeta.getTable() + " 主键范围失败: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * 生成分区SQL
     */
    private List<String> generatePartitionSql(TableMeta tableMeta, ColumnMeta primaryKey, Object minValue, Object maxValue) {
        List<String> sqlList = new ArrayList<>();
        String tableName = schema + "." + tableMeta.getTable();
        String pkName = primaryKey.getName();
        
        // 判断主键类型
        boolean isNumeric = isNumericType(primaryKey.getType());
        boolean isString = isStringType(primaryKey.getType());
        
        if (isNumeric) {
            return generateNumericPartitionSql(tableName, pkName, minValue, maxValue);
        } else if (isString) {
            // 字符串类型分区（基于字符编码）
            String minStr = minValue.toString();
            String maxStr = maxValue.toString();
            
            // 检验字符串是否为数字，如果为数字则按数值类型进行分区
            if (isNumericType(minStr, maxStr) && minStr.length()==maxStr.length()) {
                // 数值字符串使用数值分区策略
                logInfo("表 " + tableMeta.getTable() + " 的主键类型为字符串，但包含数值字符，使用数值分区策略");
                return generateStringAsNumericPartitionSql(tableName, pkName, minStr, maxStr);
            } else {
                // 非数字字符串使用字符分区策略
                logInfo("表 " + tableMeta.getTable() + " 的主键类型为字符串，不包含数值字符，使用字符分区策略");
                return generateStringPartitionByChar(tableName, pkName, minStr, maxStr);
            }
        } else {
            // 其他类型使用普通查询
            logInfo("表 " + tableMeta.getTable() + " 的主键类型不支持分区，使用普通查询");
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + tableName);
        }
        
        return sqlList;
    }

    /**
     * 生成基于字符的字符串分区SQL，确保字符在0-9,a-z范围内进行分区
     */
    private List<String> generateStringPartitionByChar(String tableName, String pkName, String minStr, String maxStr) {
        List<String> sqlList = new ArrayList<>();
        
        // 只取第一个字符进行分区
        char minChar = minStr.charAt(0);
        char maxChar = maxStr.charAt(0);
        
        // 检查字符是否在有效范围内（0-9, a-z）
        if (!(isValidCharRange(minChar)&& isValidCharRange(maxChar))) {
            logInfo("表 " + tableName + " 的主键字符范围超出0-9,a-z，使用普通查询");
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + tableName);
            return sqlList;
        }
        
        // 转换为统一的数值范围进行分区计算
        int minVal = charToNumericValue(minChar);
        int maxVal = charToNumericValue(maxChar);
        int charRange = maxVal - minVal;

        if (charRange <= 1) {
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + tableName);
            return sqlList;
        }

        int tablePartitionCount = Math.min(partitionCount, charRange);
        int partitionSize = Math.max(1, charRange / tablePartitionCount);

        for (int i = 0; i < tablePartitionCount; i++) {
            int startVal = minVal + i * partitionSize;
            int endVal = startVal + partitionSize;
            String startChar = String.valueOf(numericValueToChar(startVal));
            String endChar = String.valueOf(numericValueToChar(endVal));
            if(i == 0){
                startChar = minStr;
            }
            String sql = String.format("/* bip_streaming_export */ SELECT * FROM %s WHERE %s >= '%s' AND %s < '%s'", tableName, pkName, startChar, pkName, endChar);
            if(i == tablePartitionCount - 1){
                endChar = maxStr;
                sql = String.format("/* bip_streaming_export */ SELECT * FROM %s WHERE %s >= '%s' AND %s <= '%s'", tableName, pkName, startChar, pkName, endChar);
            }
            sqlList.add(sql);
        }

        return sqlList;
    }
    /**
     * 检查字符是否在有效范围内（0-9, a-z）
     */
    private boolean isValidCharRange(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z');
    }
    
    /**
     * 将字符转换为统一的数值（0-9: 0-9, a-z: 10-35）
     */
    private int charToNumericValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'a' && c <= 'z') {
            return 10 + (c - 'a');
        }
        return 0; // 不应该发生，因为前面已经检查过范围
    }

    /**
     * 将数值转换回字符
     */
    private char numericValueToChar(int value) {
        if (value >= 0 && value <= 9) {
            return (char) ('0' + value);
        } else if (value >= 10 && value <= 35) {
            return (char) ('a' + (value - 10));
        }
        return '0'; // 默认值
    }

    private List<String> generateNumericPartitionSql(String tableName, String pkName, Object minStr, Object maxStr) {
        List<String> sqlList = new ArrayList<>();
        // 数值类型分区
        long min = ((Number) minStr).longValue();
        long max = ((Number) maxStr).longValue();
        long range = max - min;

        if (range == 0 ) {
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + tableName);
            return sqlList;
        }

        long tablePartitionCount = Math.min(partitionCount, range);
        long partitionSize = Math.max(1, range / tablePartitionCount);

        for (int i = 0; i < tablePartitionCount; i++) {
            long start = min + i * partitionSize;
            long end = start + partitionSize;
            String sql = String.format("/* bip_streaming_export */ SELECT * FROM %s WHERE %s >= %d AND %s < %d", tableName, pkName, start, pkName, end);
            if(i == tablePartitionCount - 1){
                end = max;
                sql = String.format("/* bip_streaming_export */ SELECT * FROM %s WHERE %s >= %d AND %s <= %d", tableName, pkName, start, pkName, end);
            }
            sqlList.add(sql);
        }
        return sqlList;
    }

    private List<String> generateStringAsNumericPartitionSql(String tableName, String pkName, Object minStr, Object maxStr) {
        List<String> sqlList = new ArrayList<>();
        // 数值类型分区
        long min = Long.parseLong(minStr.toString());
        long max = Long.parseLong(maxStr.toString());
        long range = max - min;

        if (range < 0) {
            return generateStringPartitionByChar(tableName, pkName, minStr.toString(), maxStr.toString());
        }

        if (range == 0 ) {
            sqlList.add("/* bip_streaming_export */ SELECT * FROM " + tableName);
            return sqlList;
        }

        long tablePartitionCount = Math.min(partitionCount, range);
        long partitionSize = Math.max(1, range / tablePartitionCount);

        for (int i = 0; i < tablePartitionCount; i++) {
            long start = min + i * partitionSize;
            long end = start + partitionSize;
            String sql = String.format("/* bip_streaming_export */ SELECT * FROM %s WHERE %s >= '%d' AND %s < '%d'", tableName, pkName, start, pkName, end);
            if(i == tablePartitionCount - 1){
                end = max;
                sql = String.format("/* bip_streaming_export */ SELECT * FROM %s WHERE %s >= '%d' AND %s <= '%d'", tableName, pkName, start, pkName, end);
            }
            sqlList.add(sql);
        }
        return sqlList;
    }


    /**
     * 判断是否为数值类型
     */
    private boolean isNumericType(String type) {
        if (type == null) return false;
        String typeUpper = type.toUpperCase();
        return typeUpper.contains("INT") || typeUpper.contains("NUM") || 
               typeUpper.contains("DECIMAL") || typeUpper.contains("FLOAT") || 
               typeUpper.contains("DOUBLE") || typeUpper.contains("REAL");
    }

    private boolean isNumericType(String min, String max) {
        // 判断字符串是否是数字
        boolean isNumeric = false;
        try {
            Long.parseLong(min);
            Long.parseLong(max);
            isNumeric = true;
        } catch (NumberFormatException ignored) {
        }
        return isNumeric;
    }

    /**
     * 判断是否为字符串类型
     */
    private boolean isStringType(String type) {
        if (type == null) return false;
        String typeUpper = type.toUpperCase();
        return typeUpper.contains("CHAR") || typeUpper.contains("TEXT") || 
               typeUpper.contains("VARCHAR") || typeUpper.contains("STRING");
    }

    /**
     * 创建包含SQL语句的FlowFile
     */
    private FlowFile createSqlFlowFile(String sql, TableMeta tableMeta) {
        FlowFile flowFile = new FlowFile();
        
        // 设置SQL语句作为文本数据
        flowFile.setTextData(sql);
        
        // 设置相关属性
        flowFile.setAttribute("_source", "GenerateTableSelectSql");
        flowFile.setAttribute("_sql", sql);
        flowFile.setAttribute("_table", tableMeta.getTable());
        flowFile.setAttribute("_schema", schema);
        flowFile.setAttribute("_timestamp", System.currentTimeMillis());
        flowFile.setAttribute("_partitioned", sql.contains("partition_"));
        
        // 设置表元数据，供下游组件使用
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        
        return flowFile;
    }

    // 自动注入参数的setters
    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setPartitionCount(int partitionCount) {
        this.partitionCount = Math.max(1, partitionCount); // 至少1个分区
    }

    public void setEnablePartition(boolean enablePartition) {
        this.enablePartition = enablePartition;
    }

    /**
     * 获取数据源信息
     */
    public DatasourceInfo getDatasource() {
        return datasource;
    }

    /**
     * 获取schema名称
     */
    public String getSchema() {
        return schema;
    }

    /**
     * 获取分区数量
     */
    public int getPartitionCount() {
        return partitionCount;
    }

    /**
     * 是否启用分区
     */
    public boolean isEnablePartition() {
        return enablePartition;
    }

    public void setTable(String table) {
        this.table = table;
    }
}