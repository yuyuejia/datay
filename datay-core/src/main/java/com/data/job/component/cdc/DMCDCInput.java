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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 达梦 CDC 采集组件。
 *
 * <p>基于达梦内置日志挖掘（{@code DBMS_LOGMNR}）实时捕获 {@code INSERT/UPDATE/DELETE} 变更，
 * 作为流式 ETL 的输入源。达梦 {@code DBMS_LOGMNR} 只能分析归档日志，组件会按
 * {@code archiveSwitchIntervalMs} 主动执行 {@code ALTER SYSTEM ARCHIVE LOG CURRENT}，保证低延迟。
 *
 * <p>前置要求：
 * <ul>
 *   <li>开启归档：{@code ARCH_INI=1}，且 {@code V$DATABASE.ARCH_MODE=Y}；</li>
 *   <li>开启逻辑日志：{@code SP_SET_PARA_VALUE(1,'RLOG_APPEND_LOGIC',2)}；</li>
 *   <li>记录所有表逻辑日志：{@code SP_SET_PARA_VALUE(1,'RLOG_IGNORE_TABLE_SET',1)}；</li>
 *   <li>创建系统包：{@code SP_CREATE_SYSTEM_PACKAGES(1,'DBMS_LOGMNR')}；</li>
 *   <li>采集账号具备归档切换、{@code DBMS_LOGMNR} 执行权限及相关 {@code V$} 视图查询权限。</li>
 * </ul>
 */
@ComponentRegister(
    value = "DMCDCInput",
    name = "达梦 CDC",
    group = "实时输入",
    desc = "基于达梦 DBMS_LOGMNR 实时捕获 INSERT/UPDATE/DELETE 变更，作为流式 ETL 输入。",
    order = 50
)
public class DMCDCInput extends FlowComponent {

    /** 配置参数。 */
    private DatasourceInfo datasource;
    private String schemaName;
    private String tableNamePattern;
    /** 起始 SCN，为空表示从上次保存位点或当前 SCN 开始。 */
    private Long startScn;
    /** LogMiner 轮询间隔（毫秒）。 */
    private Long pollIntervalMs;
    /** 是否主动切换归档日志。 */
    private Boolean archiveSwitch = true;
    /** 两次归档切换之间的最短间隔（毫秒）。 */
    private Long archiveSwitchIntervalMs = DmLogMinerCollector.DEFAULT_ARCHIVE_SWITCH_INTERVAL_MS;
    /** 是否首次读取历史全量数据（快照）后再启动增量同步。 */
    private Boolean snapshot = false;
    /** 快照分批大小。 */
    private Integer snapshotFetchSize = 10000;
    /** 是否在输出数据行中追加事件类型和时间戳。 */
    private Boolean includeEventMeta = false;
    private Boolean ignoreError = true;

    /** 连续重连失败的最大次数，超过后任务失败；默认 5。 */
    private Integer reconnectMaxAttempts = DmLogMinerCollector.DEFAULT_RECONNECT_MAX_ATTEMPTS;
    /** 两次重连之间的等待时间（毫秒）；默认 3000。 */
    private Long reconnectIntervalMs = DmLogMinerCollector.DEFAULT_RECONNECT_INTERVAL_MS;

    private final BlockingQueue<BinlogEvent> queue = new ArrayBlockingQueue<>(1000);

    private DmLogMinerCollector collector;

    private volatile boolean started = false;

    public DMCDCInput() {
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
            logInfo("达梦 LogMiner 采集器已经在运行中");
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
            long switchInterval = (archiveSwitchIntervalMs != null && archiveSwitchIntervalMs > 0)
                ? archiveSwitchIntervalMs
                : DmLogMinerCollector.DEFAULT_ARCHIVE_SWITCH_INTERVAL_MS;
            boolean enableArchiveSwitch = !Boolean.FALSE.equals(archiveSwitch);
            boolean enableEventMeta = Boolean.TRUE.equals(includeEventMeta);
            boolean continueOnError = !Boolean.FALSE.equals(ignoreError);
            BinlogEventHandler eventHandler = new DefaultBinlogEventHandler(this, queue, enableEventMeta);
            collector = new DmLogMinerCollector(
                datasource,
                schema,
                tableNamePattern,
                beginScn,
                pollInterval,
                enableArchiveSwitch,
                switchInterval,
                continueOnError,
                eventHandler
            );
            if (reconnectMaxAttempts != null) {
                collector.setReconnectMaxAttempts(reconnectMaxAttempts);
            }
            if (reconnectIntervalMs != null) {
                collector.setReconnectIntervalMs(reconnectIntervalMs);
            }
            started = true;
            logInfo(
                "启动达梦 CDC 采集，schema=" +
                schema +
                "，表过滤=" +
                tableNamePattern +
                "，自动切归档=" +
                enableArchiveSwitch
            );

            if (Boolean.TRUE.equals(snapshot) && beginScn == null && getStatus("snapshotDone") == null) {
                long consistentScn = collector.prepare();
                collector.setStartScn(consistentScn);
                Map<String, Object> startStatus = new LinkedHashMap<>();
                startStatus.put("binlogPosition", consistentScn);
                logInfo(
                    "开始读取历史全量数据（snapshot），起始 SCN=" + consistentScn + "，表范围=" + collector.getTargetTables()
                );
                new CdcSnapshotReader(this, datasource, snapshotFetchSize, enableEventMeta).snapshot(
                    collector.getTargetTables(),
                    startStatus
                );
                logInfo("历史全量数据读取完成，启动 CDC 增量同步");
            }

            collector.start();
        } catch (CdcConnectionException e) {
            started = false;
            throw e;
        } catch (Exception e) {
            started = false;
            if (CdcConnectionErrors.isConnectionFailure(e)) {
                throw new CdcConnectionException("达梦 CDC 采集器连接失败: " + e.getMessage(), e);
            }
            // 启动阶段失败时组件无法产生任何数据，必须让任务失败，避免源组件静默结束
            throw new RuntimeException("启动达梦 CDC 采集器失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        // 重连等待期间 running=false，因此不能只在 isRunning() 时停止
        if (collector != null) {
            collector.stop();
        }
        started = false;
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

    public void setArchiveSwitch(Boolean archiveSwitch) {
        this.archiveSwitch = archiveSwitch;
    }

    public void setArchiveSwitchIntervalMs(Long archiveSwitchIntervalMs) {
        this.archiveSwitchIntervalMs = archiveSwitchIntervalMs;
    }

    public void setSnapshot(Boolean snapshot) {
        this.snapshot = snapshot;
    }

    public void setSnapshotFetchSize(Integer snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
    }

    public void setIncludeEventMeta(Boolean includeEventMeta) {
        this.includeEventMeta = includeEventMeta;
    }

    public Boolean getIncludeEventMeta() {
        return includeEventMeta;
    }

    public void setIgnoreError(Boolean ignoreError) {
        this.ignoreError = ignoreError;
    }

    public void setReconnectMaxAttempts(Integer reconnectMaxAttempts) {
        this.reconnectMaxAttempts = reconnectMaxAttempts;
    }

    public void setReconnectIntervalMs(Long reconnectIntervalMs) {
        this.reconnectIntervalMs = reconnectIntervalMs;
    }

    public boolean isStarted() {
        return started;
    }
}
