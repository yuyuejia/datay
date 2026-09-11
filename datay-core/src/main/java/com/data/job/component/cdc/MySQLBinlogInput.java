package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.DebugMockUtils;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Pattern;

/**
 * MySQL Binlog采集组件
 * 作为ETL流程的输入组件，实时采集MySQL的Binlog数据变更
 */
public class MySQLBinlogInput extends FlowComponent {

    public MySQLBinlogInput() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    // 配置参数
    private DatasourceInfo datasource;
    private String binlogFile; // 指定从哪个Binlog文件开始采集（可选）
    private Long binlogPosition; // 指定从哪个位置开始采集（可选）
    private String databaseNamePattern;
    private String tableNamePattern;

    private final BlockingQueue<BinlogEvent> queue = new ArrayBlockingQueue<>(1000);

    // 采集器实例
    private MySQLBinlogCollector collector;

    private volatile boolean started = false;

    @Override
    public void execute(FlowFile flowFile) {
        // 调试模式：不启动真实 Binlog 采集，根据源表结构生成一条模拟变更事件后结束
        if (getContext() != null && getContext().isDebugMode()) {
            executeDebugMock();
            return;
        }
        if (!started) {
            startBinlogCollector();
        } else {
            logInfo("Binlog采集器已经在运行中");
        }
    }

    /**
     * 调试模式：生成一条模拟的 INSERT 变更事件 FlowFile 并结束。
     */
    private void executeDebugMock() {
        TableMeta tableMeta = resolveDebugTableMeta();
        JSONObject row = DebugMockUtils.sampleRow(tableMeta);
        JSONArray rows = new JSONArray();
        rows.add(row);

        FlowFile mockFlowFile = new FlowFile();
        mockFlowFile.setJsonArray(rows);
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, tableMeta.getSchema());
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, System.currentTimeMillis());
        mockFlowFile.setAttribute("_debugMock", true);
        writeRecords(mockFlowFile);
        logInfo("调试模式：根据组件能力生成模拟 Binlog INSERT 事件，表 " + tableMeta.getSchema() + "." + tableMeta.getTable());
    }

    /**
     * 尝试读取源库真实表结构用于生成模拟数据，失败时使用占位结构。
     */
    private TableMeta resolveDebugTableMeta() {
        if (datasource == null || datasource.getUrl() == null) {
            return DebugMockUtils.placeholderTableMeta(null, null);
        }
        String schema = datasource.getDbschema();
        try (Connection conn = DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword())) {
            List<TableMeta> tables = DBUtils.getTableList(conn, schema);
            if (tables != null) {
                for (TableMeta table : tables) {
                    if (DebugMockUtils.matchesPattern(tableNamePattern, table.getTable())) {
                        return DBUtils.getTableMetaData(conn, schema, table.getTable());
                    }
                }
                if (!tables.isEmpty()) {
                    return DBUtils.getTableMetaData(conn, schema, tables.get(0).getTable());
                }
            }
        } catch (Exception e) {
            logWarn("调试模式：读取源表结构失败，使用内置模拟结构：" + e.getMessage());
        }
        return DebugMockUtils.placeholderTableMeta(schema, null);
    }

    /**
     * 启动Binlog采集器
     */
    private void startBinlogCollector() {
        try {
            // 创建事件处理器
            BinlogEventHandler eventHandler = new DefaultBinlogEventHandler(this, queue);

            // 创建Binlog采集器
            collector = new MySQLBinlogCollector(datasource, eventHandler);
            if (databaseNamePattern != null && !databaseNamePattern.isEmpty()) {
                collector.setDatabaseNamePattern(Pattern.compile(databaseNamePattern));
            }
            if (tableNamePattern != null && !tableNamePattern.isEmpty()) {
                collector.setTableNamePattern(Pattern.compile(tableNamePattern));
            }

            // 如果指定了起始位置，则从指定位置开始采集
            Object binlogFileStatus = getStatus("binlogFile");
            Object binlogPositionStatus = getStatus("binlogPosition");
            if (binlogFileStatus != null && binlogPositionStatus != null) {
                logInfo("从状态启动Binlog采集:", binlogFileStatus.toString(), binlogPositionStatus.toString());
                collector.startFromPosition(binlogFileStatus.toString(), Long.parseLong(binlogPositionStatus.toString()));
            } else if (binlogFile != null && binlogPosition != null) {
                logInfo("从指定位置启动Binlog采集:", binlogFile, String.valueOf(binlogPosition));
                collector.startFromPosition(binlogFile, binlogPosition);
            } else {
                logInfo("启动Binlog采集，从当前位置开始");
                collector.start();
            }

            started = true;
        } catch (Exception e) {
            logError("启动Binlog采集器失败: ", e.getMessage());
            throw new RuntimeException("启动Binlog采集器失败", e);
        }
    }

    /**
     * 停止采集器
     */
    public void stop() {
        if (collector != null && collector.isRunning()) {
            collector.stop();
            started = false;
            logInfo("MySQL Binlog采集组件已停止");
        }
        super.stop();
    }

    // Getter和Setter方法
    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    public void setBinlogPosition(Long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    public boolean isStarted() {
        return started;
    }

    public void setDatabaseNamePattern(String databaseNamePattern) {
        this.databaseNamePattern = databaseNamePattern;
    }

    public void setTableNamePattern(String tableNamePattern) {
        this.tableNamePattern = tableNamePattern;
    }
}