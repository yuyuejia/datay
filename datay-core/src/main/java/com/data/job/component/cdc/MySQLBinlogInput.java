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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Pattern;

/**
 * MySQL Binlog采集组件
 * 作为ETL流程的输入组件，实时采集MySQL的Binlog数据变更
 */
@ComponentRegister(
    value = "MySQLBinlogInput",
    name = "Mysql CDC",
    group = "实时输入",
    desc = "基于 MySQL Binlog 实时捕获数据库数据变更（INSERT/UPDATE/DELETE），作为流式 ETL 输入。",
    order = 10
)
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

    /** 是否首次读取历史全量数据（快照）后再启动增量同步。 */
    private Boolean snapshot = false;
    /** 快照分批大小。 */
    private Integer snapshotFetchSize = 10000;

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
            boolean hasCheckpoint = (binlogFileStatus != null && binlogPositionStatus != null) || (binlogFile != null && binlogPosition != null);

            if (Boolean.TRUE.equals(snapshot) && !hasCheckpoint && getStatus("snapshotDone") == null) {
                // 首次全量快照：先记录当前 binlog 位点，全量读取后从该位点启动增量
                String[] position = queryCurrentBinlogPosition();
                List<CdcSnapshotReader.TableRef> snapshotTables = resolveSnapshotTables();
                logInfo("开始读取历史全量数据（snapshot），起始位点 " + position[0] + ":" + position[1] + "，表范围=" + snapshotTables);
                new CdcSnapshotReader(this, datasource, snapshotFetchSize).snapshot(snapshotTables);
                logInfo("历史全量数据读取完成，启动 CDC 增量同步");
                collector.startFromPosition(position[0], Long.parseLong(position[1]));
            } else if (binlogFileStatus != null && binlogPositionStatus != null) {
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
     * 查询当前 binlog 位点（MySQL 8.4+ 使用 {@code SHOW BINARY LOG STATUS}，旧版本回退 {@code SHOW MASTER STATUS}）。
     */
    private String[] queryCurrentBinlogPosition() throws Exception {
        try (Connection conn = DBUtils.getConnection(datasource)) {
            try (java.sql.Statement st = conn.createStatement()) {
                try (java.sql.ResultSet rs = st.executeQuery("SHOW BINARY LOG STATUS")) {
                    rs.next();
                    return new String[] { rs.getString("File"), rs.getString("Position") };
                } catch (Exception e) {
                    try (java.sql.ResultSet rs = st.executeQuery("SHOW MASTER STATUS")) {
                        rs.next();
                        return new String[] { rs.getString("File"), rs.getString("Position") };
                    }
                }
            }
        }
    }

    /**
     * 解析快照读取使用的 schema。
     */
    private String resolveSnapshotSchema() {
        if (databaseNamePattern != null && databaseNamePattern.matches("[A-Za-z0-9_]+")) {
            return databaseNamePattern;
        }
        if (datasource.getDbschema() != null && !datasource.getDbschema().trim().isEmpty()) {
            return datasource.getDbschema();
        }
        DatasourceInfo parsed = DBUtils.parseConnectionUrl(datasource);
        return parsed.getDbschema();
    }

    /**
     * 解析快照读取的表范围，与 CDC 增量同步范围保持一致：
     * 库由 {@code databaseNamePattern} 控制（为空回退到数据源 schema），表由 {@code tableNamePattern} 控制。
     */
    private List<CdcSnapshotReader.TableRef> resolveSnapshotTables() throws Exception {
        List<String> databases = resolveSnapshotDatabases();
        Pattern tablePattern = (tableNamePattern != null && !tableNamePattern.trim().isEmpty())
            ? Pattern.compile(tableNamePattern)
            : null;

        List<CdcSnapshotReader.TableRef> tables = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection(datasource)) {
            for (String database : databases) {
                for (TableMeta tableMeta : DBUtils.getTableList(conn, database)) {
                    String table = tableMeta.getTable();
                    if (table == null || table.trim().isEmpty()) {
                        continue;
                    }
                    if (tablePattern != null && !tablePattern.matcher(table).matches()) {
                        continue;
                    }
                    tables.add(new CdcSnapshotReader.TableRef(database, table));
                }
            }
        }
        if (tables.isEmpty()) {
            throw new RuntimeException("未找到匹配的表，无法读取历史全量数据，请检查 databaseNamePattern / tableNamePattern");
        }
        return tables;
    }

    /**
     * 解析快照覆盖的数据库：{@code databaseNamePattern} 为单个库名时直接使用；
     * 为正则时枚举匹配的库；为空时回退到数据源 schema。
     */
    private List<String> resolveSnapshotDatabases() throws Exception {
        if (databaseNamePattern != null && !databaseNamePattern.trim().isEmpty() && !databaseNamePattern.matches("[A-Za-z0-9_]+")) {
            Pattern databasePattern = Pattern.compile(databaseNamePattern);
            List<String> matched = new ArrayList<>();
            try (
                Connection conn = DBUtils.getConnection(datasource);
                java.sql.Statement st = conn.createStatement();
                java.sql.ResultSet rs = st.executeQuery("SHOW DATABASES")
            ) {
                while (rs.next()) {
                    String database = rs.getString(1);
                    if (isSystemDatabase(database)) {
                        continue;
                    }
                    if (databasePattern.matcher(database).matches()) {
                        matched.add(database);
                    }
                }
            }
            if (!matched.isEmpty()) {
                return matched;
            }
        }
        return Collections.singletonList(resolveSnapshotSchema());
    }

    private boolean isSystemDatabase(String database) {
        return (
            "information_schema".equalsIgnoreCase(database) ||
            "performance_schema".equalsIgnoreCase(database) ||
            "mysql".equalsIgnoreCase(database) ||
            "sys".equalsIgnoreCase(database)
        );
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

    public void setSnapshot(Boolean snapshot) {
        this.snapshot = snapshot;
    }

    public void setSnapshotFetchSize(Integer snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
    }
}