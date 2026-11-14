package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * SQL Server 内置 CDC（变更数据捕获）采集器。
 *
 * <p>基于 SQL Server 官方 CDC 功能：开启数据库与表的 CDC 后，SQL Server Agent 的捕获作业会把
 * 事务日志中的 {@code INSERT/UPDATE/DELETE} 写入 change table。组件通过
 * {@code cdc.fn_cdc_get_all_changes_<capture_instance>} 读取变更，并转换为通用
 * {@link BinlogEvent} 交给下游。
 *
 * <p>位点使用十六进制 LSN（如 {@code 0x0000002A0000003C0004}），持久化为
 * {@code binlogPosition}。与按单条事件推进位点的实现不同，这里以「LSN 窗口」为单位：
 * 一个窗口内所有目标表都处理完成后才推进位点，避免同一事务修改多张表时某张表尚未发出、
 * 位点却已前移导致丢数据。语义为至少一次，下游按幂等方式写入即可。
 *
 * <p>前置要求（见组件文档）：
 * <ul>
 *   <li>SQL Server 2008 及以上，且 SQL Server Agent 正在运行；</li>
 *   <li>数据库已执行 {@code sys.sp_cdc_enable_db}；</li>
 *   <li>目标表已执行 {@code sys.sp_cdc_enable_table}；</li>
 *   <li>采集账号具备源表 SELECT 权限与 {@code cdc} schema 的 SELECT 权限；</li>
 *   <li>自动开启 CDC（{@code autoEnableCdc=true}）时账号需要 {@code db_owner}。</li>
 * </ul>
 */
public class SqlServerCdcCollector {

    private static final Logger log = LoggerFactory.getLogger(SqlServerCdcCollector.class);

    /** 空轮询间隔，避免无变更时频繁查询。 */
    private static final long IDLE_SLEEP_MS = 50L;

    /** 默认最大重连次数：连续失败超过该次数后终止任务。 */
    public static final int DEFAULT_RECONNECT_MAX_ATTEMPTS = 5;

    /** 默认重连间隔（毫秒）。 */
    public static final long DEFAULT_RECONNECT_INTERVAL_MS = 3000L;

    /** 连接稳定判定阈值：连接存活达到该时长后，下一次断线会重新累计重连次数。 */
    private static final long STABLE_CONNECTION_MILLIS = 10_000L;

    /** 无变更时也周期推进位点的时间间隔，避免长期积累过大的待扫描 LSN 范围。 */
    private static final long CHECKPOINT_INTERVAL_MS = 30_000L;

    /** SQL Server 最小 LSN，用于从 change table 可用范围起点开始读取。 */
    static final String ZERO_LSN = "0x00000000000000000000";

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private final DatasourceInfo datasourceInfo;
    private final String schemaName;
    private final Pattern tableNamePattern;
    private final long pollIntervalMs;
    private final boolean autoEnableCdc;
    private final boolean ignoreError;
    private final BinlogEventHandler eventHandler;

    /** 数据源特定的值读取逻辑（如 SQL Server 的时间类型）。 */
    private final JdbcDataSourceProvider provider;

    /** 断点续传起点（已处理的 LSN），为空表示首次启动。 */
    private String startLsn;

    /** 是否从 change table 的最早可用 LSN 开始（用于无权/暂无位点的快照场景）。 */
    private volatile boolean startFromBeginning = false;

    /** 采集的表范围（快照范围以此为准）。 */
    private final List<TargetTable> targetTables = new ArrayList<>();

    /** 表名 -> 表元数据缓存。 */
    private final Map<String, TableMeta> tableMetaCache = new ConcurrentHashMap<>();

    /** 已经提示过 CDC 保留期缺口的表，避免每轮都打印警告。 */
    private final Set<String> retentionWarned = new HashSet<>();

    private volatile boolean running = false;
    private volatile boolean prepared = false;

    /** 是否收到停止请求；用于区分「手动停止」和「连接异常」。 */
    private volatile boolean stopped = false;

    /** 最近一次成功建立连接的时间；0 表示当前这轮连接未成功建立。 */
    private volatile long connectedAt = 0L;

    /** 最近一次成功发送 checkpoint 的时间。 */
    private volatile long lastCheckpointAt = 0L;

    /** 最近一次提示「没有可用 CDC LSN」的时间。 */
    private volatile long lastNoLsnWarnAt = 0L;

    /** 连续重连失败的最大次数，超过后任务失败。 */
    private volatile int reconnectMaxAttempts = DEFAULT_RECONNECT_MAX_ATTEMPTS;

    /** 两次重连之间的等待时间（毫秒）。 */
    private volatile long reconnectIntervalMs = DEFAULT_RECONNECT_INTERVAL_MS;

    /** CDC 查询连接。 */
    private Connection miningConnection;

    /** 已完整处理到的 LSN（窗口上界）；下一轮从它的下一个 LSN 开始。 */
    private String lastProcessedLsn;

    public SqlServerCdcCollector(
        DatasourceInfo datasourceInfo,
        String schemaName,
        String tableNamePattern,
        String startLsn,
        long pollIntervalMs,
        boolean autoEnableCdc,
        boolean ignoreError,
        BinlogEventHandler eventHandler
    ) {
        this.datasourceInfo = datasourceInfo;
        this.schemaName = schemaName;
        this.tableNamePattern = (tableNamePattern == null || tableNamePattern.trim().isEmpty())
            ? null
            : Pattern.compile(tableNamePattern, Pattern.CASE_INSENSITIVE);
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 1000L;
        this.startLsn = startLsn;
        this.autoEnableCdc = autoEnableCdc;
        this.ignoreError = ignoreError;
        this.eventHandler = eventHandler;
        this.provider = DBUtils.resolveJdbcProvider(datasourceInfo);
    }

    /**
     * 校验源库配置、解析采集表范围，并返回当前最大 LSN。
     * <p>用于「先全量快照、后增量」场景：必须先取增量起点，保证快照窗口内的变更不丢。
     * 如果 CDC 尚未产生可用 LSN（例如刚开启捕获作业），返回 {@code null}，调用方应从
     * change table 的最早可用位点开始。
     */
    public String prepare() {
        try (Connection conn = DBUtils.getConnection(datasourceInfo)) {
            validateSource(conn);
            resolveTargetTables(conn);
            prepared = true;
            String lsn = currentMaxLsn(conn);
            log.info("SQL Server CDC 准备完成，表范围={}，当前最大 LSN={}", targetTables, lsn);
            return lsn;
        } catch (SQLException e) {
            throw new RuntimeException("初始化 SQL Server CDC 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 启动 CDC 采集，该方法会阻塞当前线程直到 {@link #stop()} 被调用。
     *
     * <p>连接失败会按 {@link #reconnectMaxAttempts} 自动重连；连续失败次数超过上限后抛出
     * {@link CdcConnectionException}，由上层 ETL 流程把任务状态置为 {@code FAILED}。
     * 手动停止不会触发重连。
     */
    public void start() {
        if (running) {
            log.warn("SQL Server CDC 采集器已经在运行中");
            return;
        }
        if (stopped) {
            return;
        }

        int failedAttempts = 0;
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            running = true;
            connectedAt = 0L;
            try {
                runOnce();
            } catch (Exception e) {
                if (stopped) {
                    break;
                }
                if (!CdcConnectionErrors.isConnectionFailure(e)) {
                    running = false;
                    throw new RuntimeException("启动 SQL Server CDC 采集器失败: " + e.getMessage(), e);
                }
                log.error("SQL Server CDC 连接异常: {}", e.getMessage(), e);
                // 已完整处理的窗口位点不能丢，重连后从最后处理到的 LSN 继续
                if (lastProcessedLsn == null && startLsn != null) {
                    lastProcessedLsn = startLsn;
                }
            }

            if (stopped) {
                break;
            }

            long connectedFor = connectedAt > 0 ? System.currentTimeMillis() - connectedAt : 0L;
            if (connectedAt > 0 && connectedFor >= STABLE_CONNECTION_MILLIS) {
                failedAttempts = 0;
            }
            failedAttempts++;

            if (failedAttempts > reconnectMaxAttempts) {
                running = false;
                int performedReconnects = Math.max(0, failedAttempts - 1);
                String reason =
                    "SQL Server CDC 连接异常，重连 " +
                    performedReconnects +
                    " 次后仍未恢复（最大重连次数 " +
                    reconnectMaxAttempts +
                    "），任务终止";
                log.error(reason);
                throw new CdcConnectionException(reason);
            }

            long waitMillis = Math.max(0L, reconnectIntervalMs);
            log.warn(
                "SQL Server CDC 连接已断开，{} ms 后进行第 {}/{} 次重连",
                waitMillis,
                failedAttempts,
                reconnectMaxAttempts
            );
            if (!sleepBeforeReconnect(waitMillis)) {
                break;
            }
        }

        if (stopped || Thread.currentThread().isInterrupted()) {
            running = false;
        }
    }

    /**
     * 建立一次 CDC 查询连接并持续轮询，直到连接中断或收到停止请求。
     */
    private void runOnce() throws Exception {
        try {
            miningConnection = DBUtils.getConnection(datasourceInfo);
            miningConnection.setAutoCommit(true);
            if (!prepared) {
                validateSource(miningConnection);
                resolveTargetTables(miningConnection);
                prepared = true;
            }

            initializeStartPosition();
            connectedAt = System.currentTimeMillis();
            log.info(
                "SQL Server CDC 已启动，schema={}，表范围={}，起始 LSN={}，轮询间隔={}ms",
                schemaName,
                targetTables,
                lastProcessedLsn,
                pollIntervalMs
            );

            pollLoop();
        } finally {
            closeQuietly();
        }
    }

    /**
     * 初始化增量起点。
     *
     * <ul>
     *   <li>显式位点 / 上次保存位点：从该位点之后继续；</li>
     *   <li>{@code startFromBeginning}：从 change table 最早可用位点开始（快照场景）；</li>
     *   <li>普通首次启动：从当前最大 LSN 开始，只采集启动之后的变更。</li>
     * </ul>
     */
    private void initializeStartPosition() throws SQLException {
        if (lastProcessedLsn != null) {
            return;
        }
        if (startLsn != null && !startLsn.trim().isEmpty()) {
            lastProcessedLsn = normalizeLsn(startLsn);
            return;
        }
        if (startFromBeginning) {
            lastProcessedLsn = ZERO_LSN;
            return;
        }
        String maxLsn = currentMaxLsn(miningConnection);
        lastProcessedLsn = maxLsn == null ? ZERO_LSN : maxLsn;
    }

    /**
     * 重连等待，期间可被 {@link #stop()} 快速打断。
     *
     * @return true 表示等待结束且未收到停止请求
     */
    private boolean sleepBeforeReconnect(long millis) {
        long remaining = millis;
        while (remaining > 0 && !stopped) {
            long sleep = Math.min(remaining, 200L);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remaining -= sleep;
        }
        return !stopped;
    }

    private void pollLoop() throws Exception {
        while (running && !stopped && !Thread.currentThread().isInterrupted()) {
            String toLsn;
            try {
                toLsn = currentMaxLsn(miningConnection);
            } catch (SQLException e) {
                if (CdcConnectionErrors.isConnectionFailure(e)) {
                    throw e;
                }
                log.warn("读取 SQL Server CDC 最大 LSN 失败，稍后重试：{}", e.getMessage());
                sleep();
                continue;
            }
            if (toLsn == null) {
                // change table 还没有任何捕获数据，等待捕获作业产生第一个 LSN
                long now = System.currentTimeMillis();
                if (lastNoLsnWarnAt == 0L || now - lastNoLsnWarnAt >= CHECKPOINT_INTERVAL_MS) {
                    lastNoLsnWarnAt = now;
                    log.warn(
                        "SQL Server CDC 暂未检测到可用变更位点，请确认 SQL Server Agent 正在运行且 CDC 捕获作业已启动"
                    );
                }
                sleep();
                continue;
            }
            lastNoLsnWarnAt = 0L;

            if (compareLsn(lastProcessedLsn, toLsn) > 0) {
                log.warn(
                    "SQL Server CDC 位点 {} 大于当前最大 LSN {}，可能数据库已恢复或切换；将从当前最大 LSN 重新开始",
                    lastProcessedLsn,
                    toLsn
                );
                lastProcessedLsn = toLsn;
                sleep();
                continue;
            }

            String fromLsn;
            try {
                fromLsn = incrementLsn(miningConnection, lastProcessedLsn);
            } catch (SQLException e) {
                if (CdcConnectionErrors.isConnectionFailure(e)) {
                    throw e;
                }
                log.warn("计算 SQL Server CDC 起始 LSN 失败，稍后重试：{}", e.getMessage());
                sleep();
                continue;
            }
            if (compareLsn(fromLsn, toLsn) > 0) {
                sleep();
                continue;
            }

            boolean emitted = false;
            try {
                log.debug("读取 SQL Server CDC LSN 区间 [{}, {}]", fromLsn, toLsn);
                for (TargetTable table : targetTables) {
                    emitted |= pollTable(table, fromLsn, toLsn);
                }
                eventHandler.flush();

                boolean shouldCheckpoint = emitted || shouldCheckpointWhenIdle();
                if (shouldCheckpoint) {
                    eventHandler.checkpoint(toLsn);
                    lastCheckpointAt = System.currentTimeMillis();
                }

                // 整个窗口处理完成后才推进位点
                lastProcessedLsn = toLsn;
            } catch (Exception e) {
                if (CdcConnectionErrors.isConnectionFailure(e)) {
                    throw e;
                }
                log.error("读取 SQL Server CDC LSN 区间 [{}, {}] 失败：{}", fromLsn, toLsn, e.getMessage(), e);
                eventHandler.handleError(null, e);
                if (!ignoreError) {
                    throw new RuntimeException("SQL Server CDC 采集失败: " + e.getMessage(), e);
                }
            }
            sleep();
        }
        log.info("SQL Server CDC 读取循环结束");
    }

    /**
     * 长时间没有目标表变更时，也周期推进一次位点，避免待扫描的 LSN 范围持续扩大。
     * 首次事件之前不发送空 checkpoint，避免下游收到无表元数据的空 FlowFile。
     */
    private boolean shouldCheckpointWhenIdle() {
        return lastCheckpointAt > 0 && System.currentTimeMillis() - lastCheckpointAt >= CHECKPOINT_INTERVAL_MS;
    }

    /**
     * 读取一张表的变更并下发，返回是否产生了事件。
     */
    private boolean pollTable(TargetTable target, String fromLsn, String toLsn) throws SQLException {
        TableMeta tableMeta = resolveTableMeta(target.getSchema(), target.getTable());
        if (tableMeta == null || tableMeta.columns().isEmpty()) {
            log.warn("表 {}.{} 没有可采集的列，已跳过", target.getSchema(), target.getTable());
            return false;
        }

        String minLsn = minLsn(miningConnection, target.getCaptureInstance());
        if (minLsn == null) {
            log.debug("表 {}.{} 的 capture instance 尚未产生可用 LSN", target.getSchema(), target.getTable());
            return false;
        }

        byte[] from = parseLsn(fromLsn);
        byte[] min = parseLsn(minLsn);
        if (Arrays.compareUnsigned(from, min) < 0) {
            if (retentionWarned.add(target.getCaptureInstance())) {
                log.warn(
                    "表 {}.{} 的 CDC 位点 {} 早于当前最小 LSN {}，将从最小 LSN 继续；" +
                    "若是刚开启 CDC 属于正常情况，否则可能因 CDC 清理丢失了部分变更",
                    target.getSchema(),
                    target.getTable(),
                    fromLsn,
                    minLsn
                );
            }
            from = min;
        }
        if (Arrays.compareUnsigned(from, parseLsn(toLsn)) > 0) {
            return false;
        }

        String functionName = "cdc." + quoteIdentifier("fn_cdc_get_all_changes_" + target.getCaptureInstance());
        String sql =
            "SELECT ct.*, lm.tran_end_time AS __datay_cdc_tran_end_time " +
            "FROM " +
            functionName +
            "(?, ?, N'all update old') ct " +
            "LEFT JOIN cdc.lsn_time_mapping lm ON lm.start_lsn = ct.__$start_lsn " +
            "ORDER BY ct.__$start_lsn, ct.__$seqval, ct.__$operation";

        boolean emitted = false;
        try (PreparedStatement ps = miningConnection.prepareStatement(sql)) {
            ps.setBytes(1, from);
            ps.setBytes(2, parseLsn(toLsn));
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int lsnIndex = findColumn(meta, "__$start_lsn");
                int seqIndex = findColumn(meta, "__$seqval");
                int opIndex = findColumn(meta, "__$operation");
                int timeIndex = findColumn(meta, "__datay_cdc_tran_end_time");
                if (lsnIndex < 0 || seqIndex < 0 || opIndex < 0) {
                    throw new SQLException("CDC 查询结果缺少 __$start_lsn/__$seqval/__$operation 元数据列");
                }

                List<CapturedColumn> columns = capturedColumns(tableMeta, meta);
                if (columns.isEmpty()) {
                    throw new SQLException(
                        "表 " +
                        target.getSchema() +
                        "." +
                        target.getTable() +
                        " 的 CDC 结果中没有可映射的源列，请检查 capture instance 的 captured_column_list"
                    );
                }

                Map<String, JSONObject> pendingBefore = new HashMap<>();
                while (rs.next()) {
                    int operation = rs.getInt(opIndex);
                    byte[] lsnBytes = rs.getBytes(lsnIndex);
                    byte[] seqBytes = rs.getBytes(seqIndex);
                    if (lsnBytes == null) {
                        continue;
                    }
                    String lsnHex = toHex(lsnBytes);
                    String pairKey = lsnHex + ":" + toHex(seqBytes);
                    long eventTime = readEventTime(rs, timeIndex);

                    if (operation == 3) {
                        pendingBefore.put(pairKey, readRow(rs, columns));
                        continue;
                    }

                    JSONObject row = readRow(rs, columns);
                    BinlogEvent.EventType eventType;
                    JSONObject before = null;
                    JSONObject after = null;
                    switch (operation) {
                        case 1:
                            eventType = BinlogEvent.EventType.DELETE;
                            before = row;
                            break;
                        case 2:
                            eventType = BinlogEvent.EventType.INSERT;
                            after = row;
                            break;
                        case 4:
                            eventType = BinlogEvent.EventType.UPDATE;
                            after = row;
                            before = pendingBefore.remove(pairKey);
                            if (before == null) {
                                log.debug(
                                    "表 {}.{} 的 UPDATE 缺少前镜像，LSN={}；下游将按后镜像执行更新",
                                    target.getSchema(),
                                    target.getTable(),
                                    lsnHex
                                );
                            }
                            break;
                        default:
                            continue;
                    }

                    BinlogEvent event = new BinlogEvent(eventType, target.getSchema(), target.getTable());
                    event.setTableMeta(tableMeta);
                    event.setTimestamp(eventTime);
                    event.setBinlogFileName("SQLSERVER");
                    event.setBeforeData(before);
                    event.setAfterData(after);
                    eventHandler.handleEvent(event);
                    emitted = true;
                }
            }
        } catch (SQLException e) {
            // SQL Server 在 LSN 区间落在有效窗口之外时返回误导性的 313 错误。
            // 必须让整个窗口失败、不推进位点，下一轮重新读取 min/max LSN 后再试，
            // 否则可能跳过该表在当前窗口内的变更。
            if (e.getErrorCode() == 313) {
                log.warn(
                    "表 {}.{} 的 CDC LSN 区间 [{}, {}] 不在有效范围内，本窗口不推进位点：{}",
                    target.getSchema(),
                    target.getTable(),
                    fromLsn,
                    toLsn,
                    e.getMessage()
                );
            }
            throw e;
        }
        log.debug("表 {}.{} 的 CDC 读取完成，LSN 区间 [{}, {}]", target.getSchema(), target.getTable(), fromLsn, toLsn);
        return emitted;
    }

    /**
     * 读取一行 CDC 数据中的捕获列。
     */
    private JSONObject readRow(ResultSet rs, List<CapturedColumn> columns) throws SQLException {
        JSONObject row = new JSONObject();
        for (CapturedColumn column : columns) {
            Object value = provider == null
                ? rs.getObject(column.index)
                : provider.readSourceValue(rs, column.index, column.jdbcType, column.typeName, column.column);
            row.put(column.column.getName(), normalizeValue(value));
        }
        return row;
    }

    /**
     * 构建捕获列与结果集下标的映射。capture instance 可能只捕获了部分列，这里只保留
     * 结果集中实际存在的列。
     */
    private List<CapturedColumn> capturedColumns(TableMeta tableMeta, ResultSetMetaData meta) throws SQLException {
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String name = meta.getColumnLabel(i);
            if (name == null || name.isEmpty()) {
                name = meta.getColumnName(i);
            }
            if (name != null) {
                indexes.put(name.toUpperCase(Locale.ROOT), i);
            }
        }

        List<CapturedColumn> result = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (column == null || column.getName() == null) {
                continue;
            }
            Integer index = indexes.get(column.getName().toUpperCase(Locale.ROOT));
            if (index == null) {
                continue;
            }
            result.add(
                new CapturedColumn(column, index, meta.getColumnType(index), meta.getColumnTypeName(index))
            );
        }
        return result;
    }

    /**
     * 将 JDBC 值规范化为下游可写入的基础类型。
     */
    Object normalizeValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof java.util.UUID) {
            return value.toString();
        }
        if (value instanceof java.sql.Clob) {
            java.sql.Clob clob = (java.sql.Clob) value;
            long length = clob.length();
            return length > Integer.MAX_VALUE ? clob.getSubString(1, Integer.MAX_VALUE) : clob.getSubString(1, (int) length);
        }
        if (value instanceof java.sql.Blob) {
            java.sql.Blob blob = (java.sql.Blob) value;
            long length = blob.length();
            return blob.getBytes(1, (int) Math.min(length, Integer.MAX_VALUE));
        }
        if (
            value instanceof String ||
            value instanceof Number ||
            value instanceof Boolean ||
            value instanceof byte[] ||
            value instanceof java.util.Date ||
            value instanceof java.time.temporal.Temporal
        ) {
            return value;
        }
        return value.toString();
    }

    private long readEventTime(ResultSet rs, int timeIndex) {
        if (timeIndex < 0) {
            return System.currentTimeMillis();
        }
        try {
            Timestamp timestamp = rs.getTimestamp(timeIndex);
            return timestamp == null ? System.currentTimeMillis() : timestamp.getTime();
        } catch (SQLException e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * 校验数据库是否开启 CDC，必要时自动开启。
     */
    private void validateSource(Connection conn) throws SQLException {
        Boolean enabled = null;
        try (
            PreparedStatement ps = conn.prepareStatement(
                "SELECT is_cdc_enabled FROM sys.databases WHERE database_id = DB_ID()"
            );
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) {
                enabled = rs.getBoolean(1);
            }
        }
        if (enabled == null) {
            throw new SQLException("无法读取 sys.databases，请检查采集账号权限");
        }
        if (enabled) {
            return;
        }
        if (!autoEnableCdc) {
            throw new SQLException(
                "数据库未开启 CDC，无法采集。请以 db_owner/sysadmin 执行：EXEC sys.sp_cdc_enable_db; " +
                "或在组件中开启 autoEnableCdc"
            );
        }
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute("EXEC sys.sp_cdc_enable_db");
        }
        log.info("数据库 [{}] 未开启 CDC，已自动执行 sys.sp_cdc_enable_db", conn.getCatalog());
    }

    /**
     * 解析采集表范围（schema 下匹配 tableNamePattern 的表），并定位 capture instance。
     */
    private void resolveTargetTables(Connection conn) throws SQLException {
        targetTables.clear();
        List<TableMeta> tables = DBUtils.getTableList(conn, schemaName);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (TableMeta tableMeta : tables) {
            String table = tableMeta.getTable();
            if (table == null || table.trim().isEmpty()) {
                continue;
            }
            if (tableNamePattern == null || tableNamePattern.matcher(table).matches()) {
                names.add(table);
            }
        }
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        if (sorted.isEmpty()) {
            throw new SQLException("未在 schema [" + schemaName + "] 中找到匹配表，请检查 tableNamePattern 配置");
        }

        Map<String, String> captureInstances = loadCaptureInstances(conn);
        List<String> missing = new ArrayList<>();
        for (String table : sorted) {
            if (captureInstances.containsKey(table.toUpperCase(Locale.ROOT))) {
                continue;
            }
            if (autoEnableCdc) {
                enableTableCdc(conn, schemaName, table);
            } else {
                missing.add(table);
            }
        }
        if (!missing.isEmpty()) {
            String exampleTable = missing.get(0);
            throw new SQLException(
                "以下表未开启 CDC，无法采集：" +
                missing +
                "。请以 db_owner 执行 EXEC sys.sp_cdc_enable_table @source_schema=N'" +
                escapeLiteral(schemaName) +
                "', @source_name=N'" +
                escapeLiteral(exampleTable) +
                "', @role_name=NULL, @supports_net_changes=0; " +
                "或在组件中开启 autoEnableCdc"
            );
        }
        if (autoEnableCdc) {
            // 自动开启后 capture instance 才创建，需要重新读取
            captureInstances = loadCaptureInstances(conn);
        }

        for (String table : sorted) {
            String captureInstance = captureInstances.get(table.toUpperCase(Locale.ROOT));
            if (captureInstance == null) {
                throw new SQLException(
                    "表 " +
                    schemaName +
                    "." +
                    table +
                    " 已开启 CDC，但未找到可用的 capture instance，请检查 sys.sp_cdc_help_change_data_capture"
                );
            }
            targetTables.add(new TargetTable(schemaName, table, captureInstance));
        }
        log.info("SQL Server CDC 目标表：{}", targetTables);
    }

    private Map<String, String> loadCaptureInstances(Connection conn) throws SQLException {
        Map<String, String> result = new HashMap<>();
        String sql =
            "SELECT ct.capture_instance, t.name AS source_table, ct.has_drop_pending " +
            "FROM cdc.change_tables ct " +
            "JOIN sys.tables t ON t.object_id = ct.source_object_id " +
            "JOIN sys.schemas s ON s.schema_id = t.schema_id " +
            "WHERE s.name = ? " +
            "ORDER BY t.name, ct.capture_instance";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String captureInstance = rs.getString("capture_instance");
                    String table = rs.getString("source_table");
                    boolean dropPending = rs.getBoolean("has_drop_pending");
                    if (captureInstance == null || table == null || dropPending) {
                        continue;
                    }
                    result.putIfAbsent(table.toUpperCase(Locale.ROOT), captureInstance);
                }
            }
        } catch (SQLException e) {
            throw new SQLException(
                "读取 CDC capture instance 失败，请确认数据库已开启 CDC 且采集账号具有 cdc schema 的 SELECT 权限：" +
                e.getMessage(),
                e
            );
        }
        return result;
    }

    private void enableTableCdc(Connection conn, String schema, String table) throws SQLException {
        String sql =
            "EXEC sys.sp_cdc_enable_table @source_schema=N'" +
            escapeLiteral(schema) +
            "', @source_name=N'" +
            escapeLiteral(table) +
            "', @role_name=NULL, @supports_net_changes=0";
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute(sql);
        }
        log.info("表 {}.{} 未开启 CDC，已自动执行 sys.sp_cdc_enable_table", schema, table);
    }

    private TableMeta resolveTableMeta(String schema, String table) {
        String key = schema + "." + table;
        return tableMetaCache.computeIfAbsent(key, k -> {
            try (Connection conn = DBUtils.getConnection(datasourceInfo)) {
                TableMeta tableMeta = DBUtils.getTableMetaData(conn, schema, table);
                if (tableMeta != null) {
                    if (tableMeta.getSchema() == null) {
                        tableMeta.setSchema(schema);
                    }
                    if (tableMeta.getDbType() == null) {
                        tableMeta.setDbType("SQLSERVER");
                    }
                }
                return tableMeta;
            } catch (SQLException e) {
                log.warn("读取表 {}.{} 元数据失败：{}", schema, table, e.getMessage());
                return null;
            }
        });
    }

    private String currentMaxLsn(Connection conn) throws SQLException {
        try (
            PreparedStatement ps = conn.prepareStatement("SELECT sys.fn_cdc_get_max_lsn()");
            ResultSet rs = ps.executeQuery()
        ) {
            if (!rs.next()) {
                return null;
            }
            byte[] lsn = rs.getBytes(1);
            return lsn == null ? null : toHex(lsn);
        }
    }

    private String minLsn(Connection conn, String captureInstance) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT sys.fn_cdc_get_min_lsn(?)")) {
            ps.setString(1, captureInstance);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                byte[] lsn = rs.getBytes(1);
                return lsn == null ? null : toHex(lsn);
            }
        }
    }

    private String incrementLsn(Connection conn, String lsn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT sys.fn_cdc_increment_lsn(?)")) {
            ps.setBytes(1, parseLsn(lsn));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return lsn;
                }
                byte[] next = rs.getBytes(1);
                return next == null ? lsn : toHex(next);
            }
        }
    }

    private int findColumn(ResultSetMetaData meta, String name) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (name.equalsIgnoreCase(meta.getColumnLabel(i)) || name.equalsIgnoreCase(meta.getColumnName(i))) {
                return i;
            }
        }
        return -1;
    }

    private String quoteIdentifier(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    private String escapeLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    static String normalizeLsn(String lsn) {
        return toHex(parseLsn(lsn));
    }

    static String toHex(byte[] lsn) {
        if (lsn == null) {
            return null;
        }
        return "0x" + HEX.formatHex(lsn);
    }

    static byte[] parseLsn(String lsn) {
        if (lsn == null || lsn.trim().isEmpty()) {
            throw new IllegalArgumentException("LSN 不能为空");
        }
        String hex = lsn.trim();
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.isEmpty()) {
            return new byte[10];
        }
        if ((hex.length() & 1) == 1) {
            hex = "0" + hex;
        }
        byte[] raw;
        try {
            raw = HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法 LSN: " + lsn, e);
        }
        if (raw.length == 10) {
            return raw;
        }
        if (raw.length < 10) {
            byte[] padded = new byte[10];
            System.arraycopy(raw, 0, padded, 10 - raw.length, raw.length);
            return padded;
        }
        throw new IllegalArgumentException("LSN 长度不能超过 10 字节: " + lsn);
    }

    static int compareLsn(String a, String b) {
        return Arrays.compareUnsigned(parseLsn(a), parseLsn(b));
    }

    private void sleep() {
        if (pollIntervalMs > 0) {
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        } else {
            try {
                Thread.sleep(IDLE_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private void closeQuietly() {
        if (miningConnection != null) {
            try {
                miningConnection.close();
            } catch (SQLException ignored) {
            } finally {
                miningConnection = null;
            }
        }
    }

    public void setStartLsn(String startLsn) {
        this.startLsn = startLsn;
    }

    /**
     * 设置为从 change table 的最早可用 LSN 开始读取（用于暂无可用位点的快照场景）。
     */
    public void setStartFromBeginning(boolean startFromBeginning) {
        this.startFromBeginning = startFromBeginning;
    }

    public List<CdcSnapshotReader.TableRef> getTargetTables() {
        List<CdcSnapshotReader.TableRef> refs = new ArrayList<>(targetTables.size());
        for (TargetTable table : targetTables) {
            refs.add(new CdcSnapshotReader.TableRef(table.getSchema(), table.getTable()));
        }
        return refs;
    }

    public String getLastProcessedLsn() {
        return lastProcessedLsn;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        running = false;
        try {
            eventHandler.flush();
        } catch (Exception e) {
            log.warn("停止时刷新事件缓冲失败：{}", e.getMessage());
        }
        closeQuietly();
        log.info("SQL Server CDC 采集器已停止");
    }

    /**
     * 设置连续重连失败的最大次数，超过后 {@link #start()} 抛出连接异常。
     */
    public void setReconnectMaxAttempts(int maxAttempts) {
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("reconnectMaxAttempts 不能小于 0，当前值: " + maxAttempts);
        }
        this.reconnectMaxAttempts = maxAttempts;
    }

    /**
     * 设置两次重连之间的等待时间（毫秒），小于 0 时按 0 处理。
     */
    public void setReconnectIntervalMs(long intervalMs) {
        this.reconnectIntervalMs = Math.max(0L, intervalMs);
    }

    public int getReconnectMaxAttempts() {
        return reconnectMaxAttempts;
    }

    public long getReconnectIntervalMs() {
        return reconnectIntervalMs;
    }

    /** 目标表与 capture instance。 */
    public static final class TargetTable {

        private final String schema;
        private final String table;
        private final String captureInstance;

        TargetTable(String schema, String table, String captureInstance) {
            this.schema = schema;
            this.table = table;
            this.captureInstance = captureInstance;
        }

        public String getSchema() {
            return schema;
        }

        public String getTable() {
            return table;
        }

        public String getCaptureInstance() {
            return captureInstance;
        }

        @Override
        public String toString() {
            return schema + "." + table + "(" + captureInstance + ")";
        }
    }

    /** CDC 结果集中的捕获列。 */
    private static final class CapturedColumn {

        private final ColumnMeta column;
        private final int index;
        private final int jdbcType;
        private final String typeName;

        CapturedColumn(ColumnMeta column, int index, int jdbcType, String typeName) {
            this.column = column;
            this.index = index;
            this.jdbcType = jdbcType;
            this.typeName = typeName;
        }
    }
}
