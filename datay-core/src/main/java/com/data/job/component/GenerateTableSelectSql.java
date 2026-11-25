package com.data.job.component;

import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.util.TablePartitionPlanner;
import com.data.metadata.TableMeta;
import com.data.metadata.ColumnMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
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
        String tableName = schema + "." + tableMeta.getTable();

        // 如果不启用分区或表数据量较小，生成普通SQL
        if (!enablePartition) {
            sqlList.add("SELECT * FROM " + tableName);
            return sqlList;
        }

        // 获取主键列
        List<ColumnMeta> primaryKeys = getPrimaryKeyColumns(tableMeta);
        if (primaryKeys.isEmpty()) {
            logInfo("表 " + tableMeta.getTable() + " 没有主键，使用普通查询");
            sqlList.add("SELECT * FROM " + tableName);
            return sqlList;
        }

        // 使用第一个主键进行分区
        ColumnMeta primaryKey = primaryKeys.get(0);
        if (!TablePartitionPlanner.supports(primaryKey)) {
            logInfo("表 " + tableMeta.getTable() + " 的主键类型不支持分区，使用普通查询");
            sqlList.add("SELECT * FROM " + tableName);
            return sqlList;
        }

        logInfo("表 " + tableMeta.getTable() + " 使用主键 " + primaryKey.getName() + " 进行分区，分区数: " + partitionCount);

        // 获取主键的最小值和最大值
        Object[] minMaxValues = getPrimaryKeyMinMax(conn, tableMeta, primaryKey);
        if (minMaxValues == null) {
            logInfo("无法获取表 " + tableMeta.getTable() + " 的主键范围，使用普通查询");
            sqlList.add("SELECT * FROM " + tableName);
            return sqlList;
        }

        TablePartitionPlanner.Plan plan = TablePartitionPlanner.plan(primaryKey, minMaxValues[0], minMaxValues[1], partitionCount);
        if (!plan.isPartitionable()) {
            logInfo("表 " + tableMeta.getTable() + " 无法生成有效分区，使用普通查询");
            sqlList.add("SELECT * FROM " + tableName);
            return sqlList;
        }

        for (int i = 0; i < plan.size(); i++) {
            sqlList.add("SELECT * FROM " + tableName + " WHERE " + plan.predicate(i));
        }

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
        try {
            Object[] minMax = TablePartitionPlanner.queryMinMax(conn, schema + "." + tableMeta.getTable(), primaryKey.getName());
            if (minMax == null) {
                logInfo("表 " + tableMeta.getTable() + " 的主键值为空");
                return null;
            }
            logInfo("表 " + tableMeta.getTable() + " 的主键范围: " + minMax[0] + " - " + minMax[1]);
            return minMax;
        } catch (SQLException e) {
            logInfo("获取表 " + tableMeta.getTable() + " 主键范围失败: " + e.getMessage());
        }

        return null;
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