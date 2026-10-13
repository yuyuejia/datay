package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
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

/**
 * Oracle CDC 采集组件。
 *
 * <p>基于 Oracle 内置日志挖掘（{@code DBMS_LOGMNR}）实时捕获 {@code INSERT/UPDATE/DELETE} 变更，
 * 作为流式 ETL 的输入源。组件通过 JDBC 读取在线与归档重做日志，无需部署 Debezium/GoldenGate 等额外组件。
 *
 * <p>前置要求：
 * <ul>
 *   <li>数据库需处于 {@code ARCHIVELOG} 模式；</li>
 *   <li>开启补充日志：{@code ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;}；</li>
 *   <li>采集账号需具备 {@code LOGMINING} 角色、{@code SELECT ANY TRANSACTION} 权限及
 *       {@code DBMS_LOGMNR} 执行权限。</li>
 * </ul>
 */
@ComponentRegister(
    value = "OracleCDCInput",
    name = "Oracle CDC",
    group = "实时输入",
    desc = "基于 Oracle LogMiner 实时捕获 INSERT/UPDATE/DELETE 变更，作为流式 ETL 输入。",
    order = 40
)
public class OracleCDCInput extends FlowComponent {

    /** 配置参数。 */
    private DatasourceInfo datasource;
    private String schemaName;
    private String tableNamePattern;
    /** 起始 SCN，为空表示从上次保存位点或当前 SCN 开始。 */
    private Long startScn;
    /** LogMiner 轮询间隔（毫秒）。 */
    private Long pollIntervalMs;
    /** 是否首次读取历史全量数据（快照）后再启动增量同步。 */
    private Boolean snapshot = false;
    /** 快照分批大小。 */
    private Integer snapshotFetchSize = 10000;
    private Boolean ignoreError = true;

    private final BlockingQueue<BinlogEvent> queue = new ArrayBlockingQueue<>(1000);

    private OracleLogMinerCollector collector;

    private volatile boolean started = false;

    public OracleCDCInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (getContext() != null && getContext().isDebugMode()) {
            executeDebugMock();
            return;
        }
        if (!started) {
            startCollector();
        } else {
            logInfo("Oracle LogMiner 采集器已经在运行中");
        }
    }

    /**
     * 调试模式：生成一条模拟的 INSERT 变更事件并结束。
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
        logInfo("调试模式：根据组件能力生成模拟 CDC INSERT 事件，表 " + tableMeta.getSchema() + "." + tableMeta.getTable());
    }

    /**
     * 尝试读取源库真实表结构用于生成模拟数据，失败时使用占位结构。
     */
    private TableMeta resolveDebugTableMeta() {
        if (datasource == null || datasource.getUrl() == null) {
            return DebugMockUtils.placeholderTableMeta(null, null);
        }
        String schema = resolveSchema();
        try (Connection conn = DBUtils.getConnection(datasource)) {
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
     * 启动 LogMiner 采集器（阻塞当前源线程）。
     */
    private void startCollector() {
        try {
            String schema = resolveSchema();

            // 断点续传：优先使用组件配置的起始 SCN，其次使用上次保存的位点
            Long beginScn = startScn;
            if (beginScn == null) {
                Object statusScn = getStatus("binlogPosition");
                if (statusScn != null) {
                    try {
                        beginScn = Long.parseLong(statusScn.toString());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            long pollInterval = (pollIntervalMs != null && pollIntervalMs > 0) ? pollIntervalMs : 1000L;
            BinlogEventHandler eventHandler = new DefaultBinlogEventHandler(this, queue);
            collector = new OracleLogMinerCollector(datasource, schema, tableNamePattern, beginScn, pollInterval, eventHandler);
            started = true;
            logInfo("启动 Oracle CDC 采集，schema=" + schema + "，表过滤=" + tableNamePattern);

            if (Boolean.TRUE.equals(snapshot) && beginScn == null && getStatus("snapshotDone") == null) {
                long consistentScn = collector.prepare();
                collector.setStartScn(consistentScn);
                logInfo("开始读取历史全量数据（snapshot），表范围=" + collector.getTargetTables());
                new CdcSnapshotReader(this, datasource, snapshotFetchSize).snapshot(collector.getTargetTables());
                logInfo("历史全量数据读取完成，启动 CDC 增量同步");
            }

            collector.start();
        } catch (Exception e) {
            started = false;
            if (Boolean.FALSE.equals(ignoreError)) {
                throw new RuntimeException("启动 Oracle CDC 采集器失败", e);
            }
            logError("启动 Oracle CDC 采集器失败: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (collector != null && collector.isRunning()) {
            collector.stop();
            started = false;
        }
        super.stop();
    }

    private String resolveSchema() {
        if (schemaName != null && !schemaName.trim().isEmpty()) {
            return schemaName.trim();
        }
        if (datasource != null && datasource.getDbschema() != null && !datasource.getDbschema().trim().isEmpty()) {
            return datasource.getDbschema().trim();
        }
        if (datasource != null && datasource.getUsername() != null && !datasource.getUsername().trim().isEmpty()) {
            return datasource.getUsername().trim().toUpperCase();
        }
        return null;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    /** 兼容前端数据源选择器输出的 {@code schema} 字段。 */
    public void setSchema(Object schema) {
        if (schema != null) {
            this.schemaName = schema.toString();
        }
    }

    public void setTableNamePattern(String tableNamePattern) {
        this.tableNamePattern = tableNamePattern;
    }

    public void setStartScn(Long startScn) {
        this.startScn = startScn;
    }

    public void setPollIntervalMs(Long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public void setSnapshot(Boolean snapshot) {
        this.snapshot = snapshot;
    }

    public void setSnapshotFetchSize(Integer snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
    }

    public void setIgnoreError(Boolean ignoreError) {
        this.ignoreError = ignoreError;
    }

    public boolean isStarted() {
        return started;
    }
}
