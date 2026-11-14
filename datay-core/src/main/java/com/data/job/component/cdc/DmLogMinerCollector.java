package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 达梦归档日志挖掘（DBMS_LOGMNR）采集器。
 *
 * <p>基于达梦内置的 {@code DBMS_LOGMNR} 包，通过 JDBC 读取本地归档日志，把
 * {@code INSERT/UPDATE/DELETE} 变更转换为通用 {@link BinlogEvent} 交给
 * {@link BinlogEventHandler} 下发下游。
 *
 * <p>达梦的 {@code DBMS_LOGMNR} 目前只支持挖掘归档日志，因此实时采集需要在轮询周期内
 * 主动执行 {@code ALTER SYSTEM ARCHIVE LOG CURRENT}，把在线 redo 切换为归档日志。
 *
 * <p>达梦 {@code MINE_VALUE} 对 UPDATE 只返回变更列，未变更列（包括主键）返回 null；
 * 采集器在 {@code RLOG_APPEND_LOGIC=2} 时通过解析 {@code SQL_REDO} 的 WHERE/SET 子句补全
 * UPDATE 前后镜像，并为 INSERT/DELETE 提供兜底。
 *
 * <p>前置要求：
 * <ul>
 *   <li>数据库开启归档：{@code ARCH_INI=1}，且 {@code V$DATABASE.ARCH_MODE=Y}；</li>
 *   <li>开启逻辑日志：{@code RLOG_APPEND_LOGIC} 为 1 或 2，推荐 2（完整前后镜像）；</li>
 *   <li>记录所有表的逻辑日志：{@code RLOG_IGNORE_TABLE_SET=1}；</li>
 *   <li>已创建系统包：{@code SP_CREATE_SYSTEM_PACKAGES(1,'DBMS_LOGMNR')}；</li>
 *   <li>采集账号具备归档切换、{@code DBMS_LOGMNR} 执行权限及相关 {@code V$} 视图查询权限。</li>
 * </ul>
 *
 * <p>断点续传使用「已提交事务的最大 {@code COMMIT_SCN}」作为位点（持久化为
 * {@code binlogPosition}），任务重启自动从位点续采。
 */
public class DmLogMinerCollector {

    private static final Logger log = LoggerFactory.getLogger(DmLogMinerCollector.class);

    /** 空轮询间隔，避免无变更时频繁查询。 */
    private static final long IDLE_SLEEP_MS = 50L;

    /** 默认最大重连次数：连续失败超过该次数后终止任务。 */
    public static final int DEFAULT_RECONNECT_MAX_ATTEMPTS = 5;

    /** 默认重连间隔（毫秒）。 */
    public static final long DEFAULT_RECONNECT_INTERVAL_MS = 3000L;

    /** 默认归档切换间隔（毫秒）。 */
    public static final long DEFAULT_ARCHIVE_SWITCH_INTERVAL_MS = 5000L;

    /**
     * 连接稳定判定阈值：连接存活达到该时长后，下一次断线会重新累计重连次数。
     */
    private static final long STABLE_CONNECTION_MILLIS = 10_000L;

    /** COMMITTED_DATA_ONLY(2) + DICT_FROM_ONLINE_CATALOG(16)。 */
    private static final int LOGMNR_OPTIONS = 2 + 16;

    /** MINE_VALUE 无法可靠取值的列类型关键字。 */
    private static final String[] UNSUPPORTED_TYPE_KEYWORDS = {
        "LOB",
        "BLOB",
        "CLOB",
        "TEXT",
        "IMAGE",
        "LONG",
        "BINARY",
        "RAW",
        "ROWID",
        "XML",
        "ARRAY",
        "STRUCT",
        "BFILE",
        "GEOMETRY",
        "GEOGRAPHY",
        "DATALINK",
    };

    private final DatasourceInfo datasourceInfo;
    private final String schemaName;
    private final Pattern tableNamePattern;
    private final long pollIntervalMs;
    private final BinlogEventHandler eventHandler;

    /** 是否主动切换归档日志。 */
    private final boolean archiveSwitch;

    /** 两次归档切换之间的最短间隔（毫秒）。 */
    private final long archiveSwitchIntervalMs;

    /** 非连接类挖掘错误是否仅记录日志并继续。 */
    private final boolean ignoreError;

    /** 断点续传起点（SCN），为空表示从当前 SCN 开始。 */
    private Long startScn;

    /** 采集的表范围（快照范围以此为准）。 */
    private final List<CdcSnapshotReader.TableRef> targetTables = new ArrayList<>();

    /** 表名 -> 表元数据缓存。 */
    private final Map<String, TableMeta> tableMetaCache = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile boolean prepared = false;

    /** 是否收到停止请求；用于区分「手动停止」和「连接异常」。 */
    private volatile boolean stopped = false;

    /** 最近一次成功建立挖掘会话的时间；0 表示当前这轮连接未成功建立。 */
    private volatile long connectedAt = 0L;

    /** 连续重连失败的最大次数，超过后任务失败。 */
    private volatile int reconnectMaxAttempts = DEFAULT_RECONNECT_MAX_ATTEMPTS;

    /** 两次重连之间的等待时间（毫秒）。 */
    private volatile long reconnectIntervalMs = DEFAULT_RECONNECT_INTERVAL_MS;

    /** 最近一次切换归档的时间。 */
    private volatile long lastArchiveSwitchAt = 0L;

    /** 是否已经提示过归档缺口，避免每个轮询周期重复告警。 */
    private volatile boolean archiveGapWarned = false;

    /** 挖掘会话连接（LogMiner 会话与连接绑定，必须在同一连接上开始/查询/结束）。 */
    private Connection miningConnection;

    /** 下一个要挖掘的起始 SCN（内存内推进，避免重复挖掘）。 */
    private long nextStartScn = 0L;

    /** 本次采集已提交的最大 SCN，用于持久化位点。 */
    private long lastCommittedScn = 0L;

    public DmLogMinerCollector(
        DatasourceInfo datasourceInfo,
        String schemaName,
        String tableNamePattern,
        Long startScn,
        long pollIntervalMs,
        boolean archiveSwitch,
        long archiveSwitchIntervalMs,
        boolean ignoreError,
        BinlogEventHandler eventHandler
    ) {
        this.datasourceInfo = datasourceInfo;
        this.schemaName = schemaName;
        this.tableNamePattern = (tableNamePattern == null || tableNamePattern.trim().isEmpty())
            ? null
            : Pattern.compile(tableNamePattern, Pattern.CASE_INSENSITIVE);
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 1000L;
        this.startScn = startScn;
        this.archiveSwitch = archiveSwitch;
        this.archiveSwitchIntervalMs = Math.max(1000L, archiveSwitchIntervalMs);
        this.ignoreError = ignoreError;
        this.eventHandler = eventHandler;
    }

    /**
     * 校验源库配置、解析采集表范围，并返回当前 SCN。
     * <p>用于「先全量快照、后增量」场景：必须先取增量起点，保证快照窗口内的变更不丢。
     */
    public long prepare() {
        try (Connection conn = DBUtils.getConnection(datasourceInfo)) {
            validateSource(conn);
            resolveTargetTables(conn);
            prepared = true;
            long scn = currentScn(conn);
            log.info("达梦 CDC 准备完成，表范围={}，当前 SCN={}", targetTables, scn);
            return scn;
        } catch (SQLException e) {
            throw new RuntimeException("初始化达梦 LogMiner 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 启动日志挖掘采集，该方法会阻塞当前线程直到 {@link #stop()} 被调用。
     *
     * <p>连接失败（初始连接失败或挖掘过程中断）会按 {@link #reconnectMaxAttempts}
     * 自动重连；连续失败次数超过上限后抛出 {@link CdcConnectionException}，
     * 由上层 ETL 流程把任务状态置为 {@code FAILED}。手动停止不会触发重连。
     */
    public void start() {
        if (running) {
            log.warn("达梦 LogMiner 采集器已经在运行中");
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
                    throw new RuntimeException("启动达梦 LogMiner 采集器失败: " + e.getMessage(), e);
                }
                log.error("达梦 LogMiner 连接异常: {}", e.getMessage(), e);
                // 已提交的事务位点不能丢，重连后从中断处继续挖掘
                if (lastCommittedScn > 0) {
                    startScn = lastCommittedScn;
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
                    "达梦 LogMiner 连接异常，重连 " +
                    performedReconnects +
                    " 次后仍未恢复（最大重连次数 " +
                    reconnectMaxAttempts +
                    "），任务终止";
                log.error(reason);
                throw new CdcConnectionException(reason);
            }

            long waitMillis = Math.max(0L, reconnectIntervalMs);
            log.warn(
                "达梦 LogMiner 连接已断开，{} ms 后进行第 {}/{} 次重连",
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
     * 建立一次挖掘会话并持续轮询，直到连接中断或收到停止请求。
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

            long begin = (startScn != null && startScn > 0) ? startScn : currentScn(miningConnection);
            nextStartScn = begin + 1;
            connectedAt = System.currentTimeMillis();
            log.info(
                "达梦 LogMiner 已启动，schema={}，表范围={}，起始 SCN={}，轮询间隔={}ms，自动切归档={}",
                schemaName,
                targetTables,
                begin,
                pollIntervalMs,
                archiveSwitch
            );

            pollLoop();
        } finally {
            closeQuietly();
        }
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
            maybeSwitchArchive();
            long endScn;
            try {
                endScn = archivedScn(miningConnection);
            } catch (SQLException e) {
                if (CdcConnectionErrors.isConnectionFailure(e)) {
                    throw e;
                }
                log.warn("读取归档日志 SCN 失败，稍后重试：{}", e.getMessage());
                sleep();
                continue;
            }
            if (endScn < nextStartScn) {
                sleep();
                continue;
            }

            long windowStart = nextStartScn;
            long windowEnd = endScn;
            long maxCommit = 0L;
            try {
                List<ArchiveFile> files = findArchiveFiles(miningConnection, windowStart, windowEnd);
                if (files.isEmpty()) {
                    sleep();
                    continue;
                }
                warnIfArchiveGap(files, windowStart);
                log.debug("挖掘 SCN 区间 [{} , {}]，归档文件数 {}", windowStart, windowEnd, files.size());
                startLogMiner(miningConnection, windowStart, windowEnd, files);
                try {
                    maxCommit = mineWindow(miningConnection);
                } finally {
                    endLogMiner(miningConnection);
                }
                eventHandler.flush();
            } catch (Exception e) {
                if (CdcConnectionErrors.isConnectionFailure(e)) {
                    throw e;
                }
                if (!ignoreError) {
                    running = false;
                    throw new RuntimeException("挖掘 SCN 区间 [" + windowStart + ", " + windowEnd + "] 失败: " + e.getMessage(), e);
                }
                log.error("挖掘 SCN 区间 [{}, {}] 失败：{}", windowStart, windowEnd, e.getMessage(), e);
                eventHandler.handleError(null, e);
            }

            // 有提交则推进位点；无提交则保持原起点，下一轮重新覆盖，避免未提交事务丢失
            if (maxCommit > 0) {
                lastCommittedScn = Math.max(lastCommittedScn, maxCommit);
                nextStartScn = lastCommittedScn + 1;
            }
            sleep();
        }
        log.info("达梦 LogMiner 读取循环结束");
    }

    /**
     * 主动切换归档日志。达梦 DBMS_LOGMNR 只能分析归档日志，轮询周期内切换一次可保证低延迟。
     */
    private void maybeSwitchArchive() {
        if (!archiveSwitch) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastArchiveSwitchAt > 0 && now - lastArchiveSwitchAt < archiveSwitchIntervalMs) {
            return;
        }
        lastArchiveSwitchAt = now;
        try (Statement st = miningConnection.createStatement()) {
            st.execute("ALTER SYSTEM ARCHIVE LOG CURRENT");
            log.debug("已执行归档切换 ALTER SYSTEM ARCHIVE LOG CURRENT");
        } catch (SQLException e) {
            if (CdcConnectionErrors.isConnectionFailure(e)) {
                throw new CdcConnectionException("归档切换时连接异常: " + e.getMessage(), e);
            }
            log.warn("归档切换失败，将在下个周期重试：{}", e.getMessage());
        }
    }

    /**
     * 挖掘当前 LogMiner 会话内目标表的已提交变更，返回区间内最大的已提交 SCN。
     */
    private long mineWindow(Connection conn) throws SQLException {
        long maxCommit = 0L;
        for (CdcSnapshotReader.TableRef ref : targetTables) {
            TableMeta tableMeta = resolveTableMeta(ref.getSchema(), ref.getTable());
            if (tableMeta == null || tableMeta.columns().isEmpty()) {
                continue;
            }
            long tableMax = mineTable(conn, tableMeta);
            maxCommit = Math.max(maxCommit, tableMax);
        }
        return maxCommit;
    }

    private long mineTable(Connection conn, TableMeta tableMeta) throws SQLException {
        List<ColumnMeta> columns = supportedColumns(tableMeta);
        if (columns.isEmpty()) {
            log.warn("表 {}.{} 没有可采集的列，已跳过", tableMeta.getSchema(), tableMeta.getTable());
            return 0L;
        }

        String schema = tableMeta.getSchema();
        String table = tableMeta.getTable();
        StringBuilder select = new StringBuilder(
            "SELECT SCN, COMMIT_SCN, OPERATION, OPERATION_CODE, ROW_ID, RS_ID, SSN, CSF, SQL_REDO, TIMESTAMP AS EVT_TS"
        );
        for (int i = 0; i < columns.size(); i++) {
            String qualified = escapeLiteral(schema + "." + table + "." + columns.get(i).getName());
            select
                .append(", CASE WHEN DBMS_LOGMNR.COLUMN_PRESENT(REDO_VALUE, '")
                .append(qualified)
                .append("') = 1 THEN DBMS_LOGMNR.MINE_VALUE(REDO_VALUE, '")
                .append(qualified)
                .append("') ELSE DBMS_LOGMNR.MINE_VALUE(UNDO_VALUE, '")
                .append(qualified)
                .append("') END AS A")
                .append(i);
            select.append(", DBMS_LOGMNR.MINE_VALUE(UNDO_VALUE, '").append(qualified).append("') AS B").append(i);
        }
        select
            .append(" FROM V$LOGMNR_CONTENTS WHERE SEG_OWNER = ? AND TABLE_NAME = ?")
            .append(" AND OPERATION_CODE IN (1,2,3,4) AND NVL(ROLL_BACK,0) = 0")
            .append(" ORDER BY SCN, RS_ID, SSN");

        long maxCommit = 0L;
        try (PreparedStatement ps = conn.prepareStatement(select.toString())) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder sqlRedoBuffer = new StringBuilder();
                String pendingSqlKey = null;
                while (rs.next()) {
                    long scn = rs.getLong("SCN");
                    String rsId = rs.getString("RS_ID");
                    String sqlKey = scn + "|" + (rsId == null ? "" : rsId);
                    String sqlFragment = rs.getString("SQL_REDO");
                    int csf = rs.getInt("CSF");
                    boolean csfNull = rs.wasNull();
                    if (!csfNull && csf > 0) {
                        // SQL_REDO 超长时会被拆成多个片段；先缓存，等最后一个片段一起解析
                        if (!sqlKey.equals(pendingSqlKey)) {
                            sqlRedoBuffer.setLength(0);
                            pendingSqlKey = sqlKey;
                        }
                        if (sqlFragment != null) {
                            sqlRedoBuffer.append(sqlFragment);
                        }
                        continue;
                    }
                    String sqlRedo;
                    if (sqlKey.equals(pendingSqlKey)) {
                        if (sqlFragment != null) {
                            sqlRedoBuffer.append(sqlFragment);
                        }
                        sqlRedo = sqlRedoBuffer.toString();
                    } else {
                        sqlRedo = sqlFragment;
                    }
                    pendingSqlKey = null;
                    sqlRedoBuffer.setLength(0);

                    BinlogEvent.EventType eventType = toEventType(rs.getInt("OPERATION_CODE"), rs.getString("OPERATION"));
                    if (eventType == null) {
                        continue;
                    }
                    long commitScn = rs.getLong("COMMIT_SCN");
                    if (rs.wasNull() || commitScn <= 0) {
                        commitScn = scn;
                    }

                    Map<String, String> setValues = eventType == BinlogEvent.EventType.UPDATE
                        ? DmSqlRedoParser.parseSetValues(sqlRedo)
                        : Collections.emptyMap();
                    Map<String, String> whereValues = eventType == BinlogEvent.EventType.INSERT
                        ? Collections.emptyMap()
                        : DmSqlRedoParser.parseWhereValues(sqlRedo);
                    Map<String, String> insertValues = eventType == BinlogEvent.EventType.INSERT
                        ? DmSqlRedoParser.parseInsertValues(sqlRedo)
                        : Collections.emptyMap();

                    JSONObject after = new JSONObject();
                    JSONObject before = new JSONObject();
                    for (int i = 0; i < columns.size(); i++) {
                        ColumnMeta column = columns.get(i);
                        String key = column.getName().toUpperCase(Locale.ROOT);
                        String minedAfter = normalizeMinedValue(rs.getString("A" + i));
                        String minedBefore = normalizeMinedValue(rs.getString("B" + i));
                        String afterValue;
                        String beforeValue;
                        if (eventType == BinlogEvent.EventType.UPDATE) {
                            boolean hasOld = whereValues.containsKey(key);
                            boolean hasNew = setValues.containsKey(key);
                            beforeValue = hasOld ? whereValues.get(key) : minedBefore;
                            if (hasNew) {
                                afterValue = setValues.get(key);
                            } else if (hasOld) {
                                afterValue = whereValues.get(key);
                            } else {
                                afterValue = minedAfter;
                            }
                        } else if (eventType == BinlogEvent.EventType.DELETE) {
                            beforeValue = whereValues.containsKey(key) ? whereValues.get(key) : minedBefore;
                            afterValue = null;
                        } else {
                            afterValue = insertValues.containsKey(key) ? insertValues.get(key) : minedAfter;
                            beforeValue = null;
                        }
                        after.put(column.getName(), normalizeValue(column, afterValue));
                        before.put(column.getName(), normalizeValue(column, beforeValue));
                    }

                    BinlogEvent event = new BinlogEvent(eventType, schema, table);
                    event.setTableMeta(tableMeta);
                    event.setTimestamp(resolveTimestamp(rs));
                    event.setBinlogFileName("DM");
                    event.setBinlogPosition(commitScn);
                    if (eventType == BinlogEvent.EventType.DELETE) {
                        event.setBeforeData(before);
                    } else if (eventType == BinlogEvent.EventType.UPDATE) {
                        event.setBeforeData(before);
                        event.setAfterData(after);
                    } else {
                        event.setAfterData(after);
                    }
                    eventHandler.handleEvent(event);

                    if (commitScn > maxCommit) {
                        maxCommit = commitScn;
                    }
                }
            }
        }
        log.debug("表 {}.{} 挖掘完成，最大已提交 SCN={}", schema, table, maxCommit);
        return maxCommit;
    }

    /**
     * 规范化 MINE_VALUE 返回的字符串值。
     *
     * <p>MINE_VALUE 对 NUMBER/DECIMAL 返回的值可能省略末尾 0（如 {@code NUMBER(10,2)} 的
     * {@code 10.5}），而目标 DECIMAL 列要求小数位固定，故按列定义的 scale 补齐，避免写入失败。
     */
    Object normalizeValue(ColumnMeta column, String raw) {
        if (raw == null) {
            return null;
        }
        String type = column.getType();
        if (type == null) {
            return raw;
        }
        String upper = type.toUpperCase(Locale.ROOT);
        if (upper.contains("NUMBER") || upper.contains("DECIMAL") || upper.contains("NUMERIC")) {
            try {
                BigDecimal decimal = new BigDecimal(raw.trim());
                // 仅当列定义了精度或小数位时才按 scale 补齐；通用 NUMBER 不擅自改值
                if (column.getPrecision() > 0 || column.getScale() > 0) {
                    decimal = decimal.setScale(Math.max(column.getScale(), 0), RoundingMode.HALF_UP);
                }
                return decimal.toPlainString();
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }
        // 达梦 DATE/DATETIME/TIMESTAMP 的 MINE_VALUE 文本为 "yyyy-MM-dd HH:mm:ss[.FF]"，
        // 转为 ISO-8601（T 分隔）以便下游按标准格式解析。
        if (
            upper.contains("TIMESTAMP") ||
            upper.contains("DATETIME") ||
            upper.contains("DATE") ||
            upper.startsWith("TIME")
        ) {
            String trimmed = raw.trim();
            if (trimmed.length() > 10 && trimmed.charAt(10) == ' ') {
                return trimmed.substring(0, 10) + "T" + trimmed.substring(11);
            }
            return trimmed;
        }
        return raw;
    }

    /**
     * 达梦 MINE_VALUE 对实际 NULL 列可能返回字符串 "NULL"，统一还原为 Java null。
     */
    private String normalizeMinedValue(String raw) {
        if (raw == null) {
            return null;
        }
        return "NULL".equalsIgnoreCase(raw.trim()) ? null : raw;
    }

    private long resolveTimestamp(ResultSet rs) {        try {
            Timestamp ts = rs.getTimestamp("EVT_TS");
            if (ts != null) {
                return ts.getTime();
            }
        } catch (SQLException ignored) {
        }
        return System.currentTimeMillis();
    }

    private BinlogEvent.EventType toEventType(int operationCode, String operation) {
        switch (operationCode) {
            case 1:
                return BinlogEvent.EventType.INSERT;
            case 2:
                return BinlogEvent.EventType.DELETE;
            case 3:
            case 4:
                return BinlogEvent.EventType.UPDATE;
            default:
                break;
        }
        if (operation == null) {
            return null;
        }
        switch (operation.toUpperCase(Locale.ROOT)) {
            case "INSERT":
                return BinlogEvent.EventType.INSERT;
            case "UPDATE":
            case "BATCH_UPDATE":
                return BinlogEvent.EventType.UPDATE;
            case "DELETE":
                return BinlogEvent.EventType.DELETE;
            default:
                return null;
        }
    }

    /**
     * 过滤 MINE_VALUE 不支持的列类型（LOB/LONG/ROWID/二进制等）。
     */
    List<ColumnMeta> supportedColumns(TableMeta tableMeta) {
        List<ColumnMeta> result = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.getName() == null || column.getName().trim().isEmpty()) {
                continue;
            }
            if (isSupportedColumn(column)) {
                result.add(column);
            } else {
                log.warn(
                    "表 {}.{} 列 {} 类型 {} 暂不支持 LogMiner 取值，已忽略该列",
                    tableMeta.getSchema(),
                    tableMeta.getTable(),
                    column.getName(),
                    column.getType()
                );
            }
        }
        return result;
    }

    private boolean isSupportedColumn(ColumnMeta column) {
        String type = column.getType();
        if (type == null) {
            return true;
        }
        String upper = type.toUpperCase(Locale.ROOT);
        for (String keyword : UNSUPPORTED_TYPE_KEYWORDS) {
            if (upper.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验源库是否满足 LogMiner 前置条件（归档 + 逻辑日志 + DBMS_LOGMNR 参数）。
     */
    private void validateSource(Connection conn) throws SQLException {
        String archMode;
        try (PreparedStatement ps = conn.prepareStatement("SELECT ARCH_MODE FROM V$DATABASE"); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("无法读取 V$DATABASE，请检查采集账号权限");
            }
            archMode = rs.getString(1);
        }
        if (archMode == null || !("Y".equalsIgnoreCase(archMode) || "1".equals(archMode))) {
            throw new SQLException("数据库未开启归档模式，无法使用 DBMS_LOGMNR 采集，请设置 ARCH_INI=1 并开启本地归档");
        }

        Map<String, String> params = queryDmIni(conn);
        int appendLogic = parseParaInt(params.get("RLOG_APPEND_LOGIC"), 0);
        if (appendLogic <= 0) {
            throw new SQLException(
                "RLOG_APPEND_LOGIC=0，达梦不会在日志中记录逻辑操作，无法进行 CDC；请执行 SP_SET_PARA_VALUE(1,'RLOG_APPEND_LOGIC',2)"
            );
        }
        if (appendLogic == 3 || appendLogic == 4) {
            throw new SQLException(
                "RLOG_APPEND_LOGIC=" +
                appendLogic +
                " 不支持采集完整的 DML 前后镜像；请执行 SP_SET_PARA_VALUE(1,'RLOG_APPEND_LOGIC',2)"
            );
        }
        if (appendLogic == 1) {
            log.warn(
                "RLOG_APPEND_LOGIC=1：有主键的表 UPDATE 只记录变更列和主键列，DELETE 只记录主键列，可能缺失未变更列；建议设置为 2"
            );
        }
        int ignoreTableSet = parseParaInt(params.get("RLOG_IGNORE_TABLE_SET"), 1);
        if (ignoreTableSet == 0) {
            log.warn(
                "RLOG_IGNORE_TABLE_SET=0：仅记录使用 ADD LOGIC LOG 显式开启逻辑日志的表；请确认目标表已开启，或执行 SP_SET_PARA_VALUE(1,'RLOG_IGNORE_TABLE_SET',1)"
            );
        }
        int delayAnalyze = parseParaInt(params.get("LOGMNR_DELAY_ANALYZE"), 0);
        if (delayAnalyze == 1) {
            throw new SQLException(
                "LOGMNR_DELAY_ANALYZE=1 时 MINE_VALUE/COLUMN_PRESENT 不可用，无法解析列值；请执行 SP_SET_PARA_VALUE(1,'LOGMNR_DELAY_ANALYZE',0)"
            );
        }
        validateLogmnrPackage(conn);
        if (archiveSwitch) {
            ensureArchiveSwitchPermission(conn);
        }
    }

    /**
     * 校验 DBMS_LOGMNR 系统包是否已创建。
     */
    private void validateLogmnrPackage(Connection conn) {
        try (
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM SYS.SYSOBJECTS WHERE NAME = 'DBMS_LOGMNR'");
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next() && rs.getInt(1) <= 0) {
                throw new SQLException(
                    "未找到 DBMS_LOGMNR 系统包，请以 SYSDBA 执行：SP_CREATE_SYSTEM_PACKAGES(1,'DBMS_LOGMNR')"
                );
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("DBMS_LOGMNR")) {
                throw new RuntimeException(e.getMessage(), e);
            }
            log.debug("校验 DBMS_LOGMNR 系统包失败，继续启动：{}", e.getMessage());
        }
    }

    /**
     * 启动时验证归档切换权限，避免运行中每个周期都失败。
     */
    private void ensureArchiveSwitchPermission(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER SYSTEM ARCHIVE LOG CURRENT");
        } catch (SQLException e) {
            throw new RuntimeException(
                "archiveSwitch=true 但归档切换失败：" +
                e.getMessage() +
                "。请为采集账号授予归档切换权限，或设置 archiveSwitch=false 仅消费已有归档日志",
                e
            );
        }
    }

    private Map<String, String> queryDmIni(Connection conn) throws SQLException {
        Map<String, String> params = new java.util.HashMap<>();
        String sql =
            "SELECT PARA_NAME, PARA_VALUE FROM V$DM_INI WHERE PARA_NAME IN ('RLOG_APPEND_LOGIC','RLOG_IGNORE_TABLE_SET','LOGMNR_DELAY_ANALYZE')";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                params.put(rs.getString(1).toUpperCase(Locale.ROOT), rs.getString(2));
            }
        }
        return params;
    }

    private int parseParaInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String trimmed = value.trim();
        int space = trimmed.indexOf(' ');
        if (space > 0) {
            trimmed = trimmed.substring(0, space);
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long currentScn(Connection conn) throws SQLException {
        try (
            PreparedStatement ps = conn.prepareStatement("SELECT CUR_LSN FROM V$RLOG");
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) {
                long lsn = rs.getLong(1);
                if (!rs.wasNull() && lsn > 0) {
                    return lsn;
                }
            }
        } catch (SQLException e) {
            log.debug("读取 V$RLOG.CUR_LSN 失败，回退到归档日志最大 SCN：{}", e.getMessage());
        }
        long archived = archivedScn(conn);
        if (archived > 0) {
            return archived;
        }
        throw new SQLException("无法读取当前 SCN（V$RLOG.CUR_LSN 与 V$ARCHIVED_LOG 均不可用）");
    }

    private long archivedScn(Connection conn) throws SQLException {
        String sql = "SELECT MAX(NEXT_CHANGE#) FROM V$ARCHIVED_LOG WHERE NAME IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long scn = rs.getLong(1);
                return rs.wasNull() ? 0L : scn;
            }
        }
        return 0L;
    }

    /**
     * 查询与 SCN 窗口重叠的归档日志文件，并过滤出本地归档目录下的文件。
     */
    private List<ArchiveFile> findArchiveFiles(Connection conn, long startScn, long endScn) throws SQLException {
        List<ArchiveFile> files = new ArrayList<>();
        String sql =
            "SELECT NAME, FIRST_CHANGE#, NEXT_CHANGE# FROM V$ARCHIVED_LOG" +
            " WHERE NAME IS NOT NULL AND NEXT_CHANGE# > FIRST_CHANGE# AND NEXT_CHANGE# > ?" +
            " AND (FIRST_CHANGE# IS NULL OR FIRST_CHANGE# <= ?)" +
            " ORDER BY FIRST_CHANGE#";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startScn);
            ps.setLong(2, endScn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }
                    long first = rs.getLong(2);
                    long next = rs.getLong(3);
                    files.add(new ArchiveFile(name, first, next));
                }
            }
        }

        List<String> localDirs = localArchiveDirs(conn);
        if (localDirs.isEmpty()) {
            return files;
        }
        List<ArchiveFile> localFiles = new ArrayList<>();
        for (ArchiveFile file : files) {
            if (isLocalArchive(file.path, localDirs)) {
                localFiles.add(file);
            }
        }
        return localFiles;
    }

    private List<String> localArchiveDirs(Connection conn) {
        List<String> dirs = new ArrayList<>();
        try (
            PreparedStatement ps = conn.prepareStatement(
                "SELECT ARCH_DEST FROM V$DM_ARCH_INI WHERE UPPER(ARCH_TYPE) = 'LOCAL'"
            );
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                String dest = rs.getString(1);
                if (dest != null && !dest.trim().isEmpty()) {
                    dirs.add(dest.trim());
                }
            }
        } catch (SQLException e) {
            log.debug("读取本地归档目录失败，按全部归档文件处理：{}", e.getMessage());
        }
        return dirs;
    }

    private boolean isLocalArchive(String path, List<String> localDirs) {
        String normalized = path.replace('\\', '/');
        for (String dir : localDirs) {
            String normalizedDir = dir.replace('\\', '/');
            if (!normalizedDir.endsWith("/")) {
                normalizedDir = normalizedDir + "/";
            }
            if (normalized.startsWith(normalizedDir) || normalized.equals(dir.replace('\\', '/'))) {
                return true;
            }
        }
        return false;
    }

    private void warnIfArchiveGap(List<ArchiveFile> files, long windowStart) {
        if (files.isEmpty()) {
            return;
        }
        long earliest = files.get(0).firstChange;
        if (earliest > 0 && earliest > windowStart) {
            if (!archiveGapWarned) {
                log.warn(
                    "最早可用归档日志起始 SCN={} 大于续采位点 SCN={}；若期间存在变更，可能因归档被清理而无法恢复，请检查归档保留策略",
                    earliest,
                    windowStart
                );
                archiveGapWarned = true;
            }
        } else {
            archiveGapWarned = false;
        }
    }

    /**
     * 注册待挖掘的本地归档日志并启动 LogMiner。
     */
    private void startLogMiner(Connection conn, long startScn, long endScn, List<ArchiveFile> files) throws SQLException {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (ArchiveFile file : files) {
            paths.add(file.path);
        }
        boolean first = true;
        for (String path : paths) {
            addLogFile(conn, path, first);
            first = false;
        }
        String sql = "BEGIN DBMS_LOGMNR.START_LOGMNR(STARTSCN => ?, ENDSCN => ?, OPTIONS => ?); END;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startScn);
            ps.setLong(2, endScn);
            ps.setInt(3, LOGMNR_OPTIONS);
            ps.execute();
        }
    }

    private void addLogFile(Connection conn, String path, boolean first) throws SQLException {
        String option = first ? "DBMS_LOGMNR.\"NEW\"" : "DBMS_LOGMNR.ADDFILE";
        String sql = "BEGIN DBMS_LOGMNR.ADD_LOGFILE(LOGFILENAME => ?, OPTIONS => " + option + "); END;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.execute();
        }
    }

    private void endLogMiner(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("BEGIN DBMS_LOGMNR.END_LOGMNR; END;")) {
            ps.execute();
        } catch (SQLException e) {
            log.debug("结束 LogMiner 会话失败（忽略）：{}", e.getMessage());
        }
    }

    /**
     * 解析采集表范围（schema 下匹配 tableNamePattern 的表）。
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
        for (String name : sorted) {
            targetTables.add(new CdcSnapshotReader.TableRef(schemaName, name));
        }
        if (targetTables.isEmpty()) {
            throw new SQLException("未在 schema [" + schemaName + "] 中找到匹配表，请检查 tableNamePattern 配置");
        }
    }

    private TableMeta resolveTableMeta(String schema, String table) {
        String key = schema + "." + table;
        return tableMetaCache.computeIfAbsent(key, k -> {
            try (Connection conn = DBUtils.getConnection(datasourceInfo)) {
                return DBUtils.getTableMetaData(conn, schema, table);
            } catch (SQLException e) {
                log.warn("读取表 {}.{} 元数据失败：{}", schema, table, e.getMessage());
                return null;
            }
        });
    }

    private String escapeLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    public void setStartScn(Long startScn) {
        this.startScn = startScn;
    }

    /**
     * 返回采集覆盖的表范围（须先调用 {@link #prepare()}）。
     */
    public List<CdcSnapshotReader.TableRef> getTargetTables() {
        return new ArrayList<>(targetTables);
    }

    public long getLastCommittedScn() {
        return lastCommittedScn;
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
        log.info("达梦 LogMiner 采集器已停止");
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

    private void sleep() throws InterruptedException {
        Thread.sleep(Math.max(IDLE_SLEEP_MS, pollIntervalMs));
    }

    private void closeQuietly() {
        if (miningConnection != null) {
            try {
                miningConnection.close();
            } catch (Exception ignored) {
            }
            miningConnection = null;
        }
    }

    private static class ArchiveFile {

        private final String path;
        private final long firstChange;
        private final long nextChange;

        private ArchiveFile(String path, long firstChange, long nextChange) {
            this.path = path;
            this.firstChange = firstChange;
            this.nextChange = nextChange;
        }
    }
}
