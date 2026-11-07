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
 * PostgreSQL CDC 采集组件。
 *
 * <p>基于 PostgreSQL 逻辑复制（WAL + pgoutput 插件）实时捕获 {@code INSERT/UPDATE/DELETE} 变更，
 * 作为流式 ETL 的输入源。组件会自动创建并维护 publication 与 replication slot。
 *
 * <p>注意：逻辑复制仅捕获组件启动之后的增量变更，存量数据需另行全量同步。
 */
@ComponentRegister(
    value = "PostgresCDCInput",
    name = "PostgreSQL CDC",
    group = "实时输入",
    desc = "基于 PostgreSQL 逻辑复制（WAL/pgoutput）实时捕获 INSERT/UPDATE/DELETE 变更，作为流式 ETL 输入。",
    order = 20
)
public class PostgresCDCInput extends FlowComponent {

    /** 配置参数。 */
    private DatasourceInfo datasource;
    private String schemaName;
    private String tableNamePattern;
    private String publicationName;
    private String slotName;
    private String startLsn;
    private Boolean ignoreError = true;
    /** 是否首次读取历史全量数据（快照）后再启动增量同步。 */
    private Boolean snapshot = false;
    /** 快照分批大小。 */
    private Integer snapshotFetchSize = 10000;

    /** 连续重连失败的最大次数，超过后任务失败；默认 5。 */
    private Integer reconnectMaxAttempts = PostgresWalCollector.DEFAULT_RECONNECT_MAX_ATTEMPTS;
    /** 两次重连之间的等待时间（毫秒）；默认 3000。 */
    private Long reconnectIntervalMs = PostgresWalCollector.DEFAULT_RECONNECT_INTERVAL_MS;

    private final BlockingQueue<BinlogEvent> queue = new ArrayBlockingQueue<>(1000);

    private PostgresWalCollector collector;

    private volatile boolean started = false;

    public PostgresCDCInput() {
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
            logInfo("PostgreSQL 逻辑复制采集器已经在运行中");
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
     * 启动逻辑复制采集器（阻塞当前源线程）。
     */
    private void startCollector() {
        try {
            String schema = resolveSchema();
            String publication = resolvePublicationName();
            String slot = resolveSlotName();

            // 断点续传：优先使用组件配置的起始 LSN，其次使用上次保存的位点
            Long lsn = null;
            if (startLsn != null && !startLsn.trim().isEmpty()) {
                lsn = org.postgresql.replication.LogSequenceNumber.valueOf(startLsn.trim()).asLong();
            }
            if (lsn == null) {
                Object statusLsn = getStatus("binlogPosition");
                if (statusLsn != null) {
                    try {
                        lsn = Long.parseLong(statusLsn.toString());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            BinlogEventHandler eventHandler = new DefaultBinlogEventHandler(this, queue);
            collector = new PostgresWalCollector(datasource, schema, publication, slot, tableNamePattern, lsn, eventHandler);
            if (reconnectMaxAttempts != null) {
                collector.setReconnectMaxAttempts(reconnectMaxAttempts);
            }
            if (reconnectIntervalMs != null) {
                collector.setReconnectIntervalMs(reconnectIntervalMs);
            }
            started = true;
            logInfo("启动 PostgreSQL CDC 采集，schema=" + schema + "，publication=" + publication + "，slot=" + slot);

            // 首次全量快照：无断点且未完成过快照时执行，先置 slot 一致点为增量起点
            if (Boolean.TRUE.equals(snapshot) && lsn == null && getStatus("snapshotDone") == null) {
                long consistentLsn = collector.prepare();
                collector.setStartLsn(consistentLsn);
                Map<String, Object> startStatus = new LinkedHashMap<>();
                startStatus.put("binlogPosition", consistentLsn);
                logInfo("开始读取历史全量数据（snapshot），起始 LSN=" + consistentLsn + "，表范围=" + collector.getTargetTables());
                new CdcSnapshotReader(this, datasource, snapshotFetchSize).snapshot(collector.getTargetTables(), startStatus);
                logInfo("历史全量数据读取完成，启动 CDC 增量同步");
            }

            collector.start();
        } catch (CdcConnectionException e) {
            started = false;
            throw e;
        } catch (Exception e) {
            started = false;
            if (CdcConnectionErrors.isConnectionFailure(e)) {
                throw new CdcConnectionException("PostgreSQL CDC 采集器连接失败: " + e.getMessage(), e);
            }
            if (Boolean.FALSE.equals(ignoreError)) {
                throw new RuntimeException("启动 PostgreSQL CDC 采集器失败", e);
            }
            logError("启动 PostgreSQL CDC 采集器失败: " + e.getMessage());
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
            return schemaName;
        }
        if (datasource != null && datasource.getDbschema() != null && !datasource.getDbschema().trim().isEmpty()) {
            return datasource.getDbschema();
        }
        return "public";
    }

    private String resolvePublicationName() {
        if (publicationName != null && !publicationName.trim().isEmpty()) {
            return publicationName;
        }
        return "datay_pub_" + normalizeIdentifier(getId());
    }

    private String resolveSlotName() {
        if (slotName != null && !slotName.trim().isEmpty()) {
            return slotName;
        }
        return "datay_slot_" + normalizeIdentifier(getId());
    }

    private static String normalizeIdentifier(String value) {
        String normalized = (value == null ? "cdc" : value).toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return normalized.isEmpty() ? "cdc" : normalized;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    /** 兼容前端数据源选择器输出的 {@code schema} 字段。 */
    public void setSchema(String schema) {
        this.schemaName = schema;
    }

    public void setTableNamePattern(String tableNamePattern) {
        this.tableNamePattern = tableNamePattern;
    }

    public void setPublicationName(String publicationName) {
        this.publicationName = publicationName;
    }

    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public void setStartLsn(String startLsn) {
        this.startLsn = startLsn;
    }

    public void setIgnoreError(Boolean ignoreError) {
        this.ignoreError = ignoreError;
    }

    public void setSnapshot(Boolean snapshot) {
        this.snapshot = snapshot;
    }

    public void setSnapshotFetchSize(Integer snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
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
