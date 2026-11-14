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
 * SQL Server CDC 采集组件。
 *
 * <p>基于 SQL Server 内置变更数据捕获（CDC）实时捕获 {@code INSERT/UPDATE/DELETE} 变更，
 * 作为流式 ETL 的输入源。组件通过 JDBC 调用 {@code cdc.fn_cdc_get_all_changes_*} 读取
 * change table，无需部署 Debezium/Kafka 等额外组件。
 *
 * <p>前置要求：
 * <ul>
 *   <li>SQL Server 2012 及以上（建议 2016+），且 SQL Server Agent 正在运行；</li>
 *   <li>数据库已执行 {@code sys.sp_cdc_enable_db}；</li>
 *   <li>目标表已执行 {@code sys.sp_cdc_enable_table}；</li>
 *   <li>采集账号具备源表 SELECT 权限与 {@code cdc} schema 的 SELECT 权限；</li>
 *   <li>开启 {@code autoEnableCdc} 时账号需要 {@code db_owner} 权限。</li>
 * </ul>
 */
@ComponentRegister(
    value = "SQLServerCDCInput",
    name = "SQL Server CDC",
    group = "实时输入",
    desc = "基于 SQL Server 内置 CDC 实时捕获 INSERT/UPDATE/DELETE 变更，作为流式 ETL 输入。",
    order = 60
)
public class SQLServerCDCInput extends FlowComponent {

    /** 配置参数。 */
    private DatasourceInfo datasource;
    private String schemaName;
    private String tableNamePattern;
    /** 起始 LSN（十六进制字符串），为空表示从上次保存位点或当前位置开始。 */
    private String startLsn;
    /** CDC 轮询间隔（毫秒）。 */
    private Long pollIntervalMs;
    /** 是否自动开启数据库/表 CDC，需要 db_owner 权限。 */
    private Boolean autoEnableCdc = false;
    /** 是否首次读取历史全量数据（快照）后再启动增量同步。 */
    private Boolean snapshot = false;
    /** 快照分批大小。 */
    private Integer snapshotFetchSize = 10000;
    /** 是否在输出数据行中追加事件类型和时间戳。 */
    private Boolean includeEventMeta = false;
    private Boolean ignoreError = true;

    /** 连续重连失败的最大次数，超过后任务失败；默认 5。 */
    private Integer reconnectMaxAttempts = SqlServerCdcCollector.DEFAULT_RECONNECT_MAX_ATTEMPTS;
    /** 两次重连之间的等待时间（毫秒）；默认 3000。 */
    private Long reconnectIntervalMs = SqlServerCdcCollector.DEFAULT_RECONNECT_INTERVAL_MS;

    private final BlockingQueue<BinlogEvent> queue = new ArrayBlockingQueue<>(1000);

    private SqlServerCdcCollector collector;

    private volatile boolean started = false;

    public SQLServerCDCInput() {
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
            logInfo("SQL Server CDC 采集器已经在运行中");
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
     * 启动 CDC 采集器（阻塞当前源线程）。
     */
    private void startCollector() {
        try {
            String schema = resolveSchema();

            // 断点续传：优先使用组件配置的起始 LSN，其次使用上次保存的位点
            String beginLsn = startLsn;
            if (beginLsn == null || beginLsn.trim().isEmpty()) {
                Object statusLsn = getStatus("binlogPosition");
                if (statusLsn != null && !statusLsn.toString().trim().isEmpty()) {
                    beginLsn = statusLsn.toString().trim();
                }
            }

            long pollInterval = (pollIntervalMs != null && pollIntervalMs > 0) ? pollIntervalMs : 1000L;
            boolean enableAutoEnableCdc = Boolean.TRUE.equals(autoEnableCdc);
            boolean enableEventMeta = Boolean.TRUE.equals(includeEventMeta);
            boolean continueOnError = !Boolean.FALSE.equals(ignoreError);
            BinlogEventHandler eventHandler = new DefaultBinlogEventHandler(this, queue, enableEventMeta);
            collector = new SqlServerCdcCollector(
                datasource,
                schema,
                tableNamePattern,
                beginLsn,
                pollInterval,
                enableAutoEnableCdc,
                continueOnError,
                eventHandler
            );
            if (reconnectMaxAttempts != null) {
                collector.setReconnectMaxAttempts(reconnectMaxAttempts);
            }
            if (reconnectIntervalMs != null) {
                collector.setReconnectIntervalMs(reconnectIntervalMs);
            }

            // 快照完成后若没有可用位点，需要从 change table 最早位点继续，避免漏掉快照期间的变更
            if (beginLsn == null && getStatus("snapshotDone") != null) {
                collector.setStartFromBeginning(true);
            }

            started = true;
            logInfo(
                "启动 SQL Server CDC 采集，schema=" +
                schema +
                "，表过滤=" +
                tableNamePattern +
                "，自动开启 CDC=" +
                enableAutoEnableCdc
            );

            if (Boolean.TRUE.equals(snapshot) && beginLsn == null && getStatus("snapshotDone") == null) {
                String consistentLsn = collector.prepare();
                Map<String, Object> startStatus = new LinkedHashMap<>();
                if (consistentLsn != null) {
                    collector.setStartLsn(consistentLsn);
                    startStatus.put("binlogPosition", consistentLsn);
                    logInfo(
                        "开始读取历史全量数据（snapshot），起始 LSN=" +
                        consistentLsn +
                        "，表范围=" +
                        collector.getTargetTables()
                    );
                } else {
                    // CDC 尚未产生可用 LSN（例如刚开启捕获作业），从 change table 最早位点开始
                    collector.setStartFromBeginning(true);
                    logWarn(
                        "CDC 尚未产生可用 LSN，快照完成后将从 change table 最早可用位点开始采集；" +
                        "请确认 SQL Server Agent 的 CDC 捕获作业正在运行"
                    );
                    logInfo("开始读取历史全量数据（snapshot），表范围=" + collector.getTargetTables());
                }
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
                throw new CdcConnectionException("SQL Server CDC 采集器连接失败: " + e.getMessage(), e);
            }
            // 启动阶段失败时组件无法产生任何数据，必须让任务失败，避免源组件静默结束
            throw new RuntimeException("启动 SQL Server CDC 采集器失败: " + e.getMessage(), e);
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
        // SQL Server 默认 schema 为 dbo；数据源默认 schema 在保存时会被清空
        return "dbo";
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

    public void setStartLsn(String startLsn) {
        this.startLsn = startLsn;
    }

    public void setPollIntervalMs(Long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public void setAutoEnableCdc(Boolean autoEnableCdc) {
        this.autoEnableCdc = autoEnableCdc;
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
