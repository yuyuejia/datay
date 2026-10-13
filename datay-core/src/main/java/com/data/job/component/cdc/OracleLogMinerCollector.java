package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Oracle 日志挖掘（LogMiner）采集器。
 *
 * <p>基于 Oracle 内置的 {@code DBMS_LOGMNR} 包，通过 JDBC 读取重做日志（在线 + 归档），
 * 把 {@code INSERT/UPDATE/DELETE} 变更转换为通用 {@link BinlogEvent} 交给 {@link BinlogEventHandler}
 * 下发下游。无需部署 Debezium/GoldenGate/XStream 等额外组件。
 *
 * <p>前置要求（见组件文档）：
 * <ul>
 *   <li>数据库处于 {@code ARCHIVELOG} 模式；</li>
 *   <li>开启补充日志：{@code ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;}；</li>
 *   <li>采集账号具备 {@code LOGMINING} 角色、{@code SELECT ANY TRANSACTION} 权限，以及
 *       {@code DBMS_LOGMNR}/{@code DBMS_LOGMNR_D} 的执行权限。</li>
 * </ul>
 *
 * <p>断点续传使用「已提交事务的最大 {@code COMMIT_SCN}」作为位点（持久化为 {@code binlogPosition}），
 * 任务重启自动从位点续采。
 */
public class OracleLogMinerCollector {

    private static final Logger log = LoggerFactory.getLogger(OracleLogMinerCollector.class);

    /** 空轮询间隔，避免无变更时频繁查询。 */
    private static final long IDLE_SLEEP_MS = 50L;

    private static final String[] UNSUPPORTED_TYPE_KEYWORDS = { "LOB", "BFILE", "LONG", "ROWID", "XMLTYPE" };

    private final DatasourceInfo datasourceInfo;
    private final String schemaName;
    private final Pattern tableNamePattern;
    private final long pollIntervalMs;
    private final BinlogEventHandler eventHandler;

    /** 断点续传起点（SCN），为空表示从当前 SCN 开始。 */
    private Long startScn;

    /** 采集的表范围（快照范围以此为准）。 */
    private final List<CdcSnapshotReader.TableRef> targetTables = new ArrayList<>();

    /** 表名 -> 表元数据缓存。 */
    private final Map<String, TableMeta> tableMetaCache = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile boolean prepared = false;

    /** 挖掘会话连接（LogMiner 会话与连接绑定，必须在同一连接上开始/查询/结束）。 */
    private Connection miningConnection;

    /** 下一个要挖掘的起始 SCN（内存内推进，避免重复挖掘）。 */
    private long nextStartScn = 0L;

    /** 本次采集已提交的最大 SCN，用于持久化位点。 */
    private long lastCommittedScn = 0L;

    /** 采集连接是否位于可插入数据库（PDB）内部。 */
    private boolean inPdb = false;

    public OracleLogMinerCollector(
        DatasourceInfo datasourceInfo,
        String schemaName,
        String tableNamePattern,
        Long startScn,
        long pollIntervalMs,
        BinlogEventHandler eventHandler
    ) {
        this.datasourceInfo = datasourceInfo;
        this.schemaName = schemaName;
        this.tableNamePattern = (tableNamePattern == null || tableNamePattern.trim().isEmpty())
            ? null
            : Pattern.compile(tableNamePattern, Pattern.CASE_INSENSITIVE);
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 1000L;
        this.startScn = startScn;
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
            log.info("Oracle CDC 准备完成，表范围={}，当前 SCN={}", targetTables, scn);
            return scn;
        } catch (SQLException e) {
            throw new RuntimeException("初始化 Oracle LogMiner 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 启动日志挖掘采集，该方法会阻塞当前线程直到 {@link #stop()} 被调用。
     */
    public void start() {
        running = true;
        try {
            miningConnection = DBUtils.getConnection(datasourceInfo);
            miningConnection.setAutoCommit(true);
            setupSession(miningConnection);
            if (!prepared) {
                validateSource(miningConnection);
                resolveTargetTables(miningConnection);
            }

            long begin = (startScn != null && startScn > 0) ? startScn : currentScn(miningConnection);
            nextStartScn = begin + 1;
            log.info(
                "Oracle LogMiner 已启动，schema={}，表范围={}，起始 SCN={}，轮询间隔={}ms",
                schemaName,
                targetTables,
                begin,
                pollIntervalMs
            );

            pollLoop();
        } catch (Exception e) {
            if (!running) {
                log.info("Oracle LogMiner 采集器已停止");
                return;
            }
            throw new RuntimeException("启动 Oracle LogMiner 采集器失败: " + e.getMessage(), e);
        } finally {
            closeQuietly();
        }
    }

    private void pollLoop() throws InterruptedException {
        while (running && !Thread.currentThread().isInterrupted()) {
            long endScn;
            try {
                endScn = currentScn(miningConnection);
            } catch (SQLException e) {
                log.warn("读取当前 SCN 失败，稍后重试：{}", e.getMessage());
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
                log.debug("挖掘 SCN 区间 [{} , {}]", windowStart, windowEnd);
                startLogMiner(miningConnection, windowStart, windowEnd);
                try {
                    maxCommit = mineWindow(miningConnection);
                } finally {
                    endLogMiner(miningConnection);
                }
                eventHandler.flush();
            } catch (Exception e) {
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
        log.info("Oracle LogMiner 读取循环结束");
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
            "SELECT SCN, COMMIT_SCN, OPERATION, ROW_ID, TIMESTAMP AS EVT_TS"
        );
        for (int i = 0; i < columns.size(); i++) {
            String qualified = schema + "." + table + "." + columns.get(i).getName();
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
            .append(" AND OPERATION IN ('INSERT','UPDATE','DELETE') AND NVL(CSF,0) = 0")
            .append(" ORDER BY SCN, RS_ID");

        long maxCommit = 0L;
        try (PreparedStatement ps = conn.prepareStatement(select.toString())) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String operation = rs.getString("OPERATION");
                    if (operation == null) {
                        continue;
                    }
                    long commitScn = rs.getLong("COMMIT_SCN");
                    if (rs.wasNull()) {
                        commitScn = rs.getLong("SCN");
                    }
                    BinlogEvent.EventType eventType = toEventType(operation);
                    if (eventType == null) {
                        continue;
                    }

                    JSONObject after = new JSONObject();
                    JSONObject before = new JSONObject();
                    for (int i = 0; i < columns.size(); i++) {
                        ColumnMeta column = columns.get(i);
                        after.put(column.getName(), normalizeValue(column, rs.getString("A" + i)));
                        before.put(column.getName(), normalizeValue(column, rs.getString("B" + i)));
                    }

                    BinlogEvent event = new BinlogEvent(eventType, schema, table);
                    event.setTableMeta(tableMeta);
                    event.setTimestamp(resolveTimestamp(rs));
                    event.setBinlogFileName("ORACLE");
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
    private Object normalizeValue(ColumnMeta column, String raw) {
        if (raw == null) {
            return null;
        }
        String type = column.getType();
        if (type == null) {
            return raw;
        }
        String upper = type.toUpperCase();
        if (upper.contains("NUMBER") || upper.contains("DECIMAL") || upper.contains("NUMERIC")) {
            try {
                java.math.BigDecimal decimal = new java.math.BigDecimal(raw.trim());
                int scale = Math.max(column.getScale(), 0);
                decimal = decimal.setScale(scale, java.math.RoundingMode.HALF_UP);
                return decimal.toPlainString();
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }
        // Oracle TIME/TIMESTAMP 的 MINE_VALUE 文本为 "yyyy-MM-dd HH:mm:ss[.FF]"，
        // 转为 ISO-8601（T 分隔）以便下游按标准格式解析。
        if (upper.contains("TIMESTAMP") || upper.contains("DATETIME")) {
            return raw.trim().replace(' ', 'T');
        }
        // Oracle DATE 含时间部分，而目标类型多映射为 DATE，截断到日期部分。
        if ("DATE".equals(upper)) {
            String trimmed = raw.trim();
            int space = trimmed.indexOf(' ');
            return space > 0 ? trimmed.substring(0, space) : trimmed;
        }
        return raw;
    }

    private long resolveTimestamp(ResultSet rs) {
        try {
            java.sql.Timestamp ts = rs.getTimestamp("EVT_TS");
            if (ts != null) {
                return ts.getTime();
            }
        } catch (SQLException ignored) {
        }
        return System.currentTimeMillis();
    }

    private BinlogEvent.EventType toEventType(String operation) {
        switch (operation.toUpperCase()) {
            case "INSERT":
                return BinlogEvent.EventType.INSERT;
            case "UPDATE":
                return BinlogEvent.EventType.UPDATE;
            case "DELETE":
                return BinlogEvent.EventType.DELETE;
            default:
                return null;
        }
    }

    /**
     * 过滤 MINE_VALUE 不支持的列类型（LOB/LONG/ROWID 等）。
     */
    private List<ColumnMeta> supportedColumns(TableMeta tableMeta) {
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
        String upper = type.toUpperCase();
        for (String keyword : UNSUPPORTED_TYPE_KEYWORDS) {
            if (upper.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验源库是否满足 LogMiner 前置条件（ARCHIVELOG + 补充日志）。
     */
    private void validateSource(Connection conn) throws SQLException {
        String logMode;
        String suppMin;
        String suppAll;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT LOG_MODE, SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("无法读取 V$DATABASE，请检查采集账号权限");
                }
                logMode = rs.getString(1);
                suppMin = rs.getString(2);
                suppAll = rs.getString(3);
            }
        }
        if (!"ARCHIVELOG".equalsIgnoreCase(logMode)) {
            throw new SQLException("数据库未开启归档模式（ARCHIVELOG），无法使用 LogMiner 采集");
        }
        boolean minOn = suppMin != null && !"NO".equalsIgnoreCase(suppMin);
        boolean allOn = suppAll != null && !"NO".equalsIgnoreCase(suppAll);
        if (!minOn) {
            throw new SQLException(
                "数据库未开启最小补充日志，请执行：ALTER DATABASE ADD SUPPLEMENTAL LOG DATA; 及 ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;"
            );
        }
        if (!allOn) {
            log.warn("数据库未开启 ALL COLUMNS 补充日志，UPDATE 事件可能缺失未变更列，建议：ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;");
        }
        detectPdb(conn);
    }

    /**
     * 检测当前连接是否位于可插入数据库（PDB）内部。PDB 中不允许 ADD_LOGFILE。
     */
    private void detectPdb(Connection conn) {
        try (
            PreparedStatement ps = conn.prepareStatement(
                "SELECT SYS_CONTEXT('USERENV','CON_NAME'), SYS_CONTEXT('USERENV','CDB_NAME') FROM DUAL"
            );
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) {
                String conName = rs.getString(1);
                String cdbName = rs.getString(2);
                inPdb = cdbName != null && !cdbName.isEmpty() && conName != null && !conName.equalsIgnoreCase(cdbName);
                log.info("Oracle 容器信息：CON_NAME={}，CDB_NAME={}，PDB={}", conName, cdbName, inPdb);
            }
        } catch (SQLException e) {
            log.debug("检测 PDB 环境失败，按非 PDB 处理：{}", e.getMessage());
        }
    }

    private long currentScn(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT CURRENT_SCN FROM V$DATABASE"); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("无法读取 CURRENT_SCN");
    }

    /**
     * 设置会话 NLS 格式，保证 MINE_VALUE 返回的日期/时间字符串可被下游解析。
     */
    private void setupSession(Connection conn) throws SQLException {
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute("ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD HH24:MI:SS'");
            st.execute("ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF6'");
            st.execute("ALTER SESSION SET NLS_TIMESTAMP_TZ_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM'");
        }
    }

    /**
     * 注册待挖掘的重做日志文件（在线 + 与区间重叠的归档日志），并启动 LogMiner。
     */
    private void startLogMiner(Connection conn, long startScn, long endScn) throws SQLException {
        // PDB 内部不允许 DBMS_LOGMNR.ADD_LOGFILE（ORA-65040），此时交由 START_LOGMNR 自动定位所需日志
        if (!inPdb) {
            addLogFiles(conn, startScn, endScn);
        }
        String sql =
            "BEGIN DBMS_LOGMNR.START_LOGMNR(" +
            "STARTSCN => ?, ENDSCN => ?, " +
            "OPTIONS => DBMS_LOGMNR.DICT_FROM_ONLINE_CATALOG + DBMS_LOGMNR.COMMITTED_DATA_ONLY); END;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startScn);
            ps.setLong(2, endScn);
            ps.execute();
        }
    }

    private void addLogFiles(Connection conn, long startScn, long endScn) throws SQLException {
        LinkedHashSet<String> files = new LinkedHashSet<>();
        // 在线重做日志（含尚未归档的最新变更）
        try (
            PreparedStatement ps = conn.prepareStatement(
                "SELECT f.MEMBER FROM V$LOGFILE f, V$LOG l WHERE f.GROUP# = l.GROUP# AND l.STATUS IN ('CURRENT','ACTIVE')"
            );
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                files.add(rs.getString(1));
            }
        }
        // 与 SCN 区间重叠的归档日志
        try (
            PreparedStatement ps = conn.prepareStatement(
                "SELECT NAME FROM V$ARCHIVED_LOG WHERE DELETED = 'NO' AND FIRST_CHANGE# <= ? AND (NEXT_CHANGE# IS NULL OR NEXT_CHANGE# >= ?) ORDER BY FIRST_CHANGE#"
            )
        ) {
            ps.setLong(1, endScn);
            ps.setLong(2, startScn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(rs.getString(1));
                }
            }
        }

        boolean first = true;
        for (String file : files) {
            addLogFile(conn, file, first);
            first = false;
        }
        if (first) {
            throw new SQLException("未找到可用的重做日志文件，无法启动 LogMiner（请确认数据库处于 ARCHIVELOG 模式）");
        }
    }

    private void addLogFile(Connection conn, String path, boolean first) throws SQLException {
        String option = first ? "DBMS_LOGMNR.NEW" : "DBMS_LOGMNR.ADDFILE";
        String sql = "BEGIN DBMS_LOGMNR.ADD_LOGFILE(LOGFILENAME => ?, OPTIONS => " + option + "); END;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.execute();
        } catch (SQLException e) {
            // ORA-01289：重复添加同一日志文件，忽略
            if (e.getErrorCode() == 1289) {
                log.debug("日志文件已添加，忽略：{}", path);
                return;
            }
            throw e;
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
        for (String name : names) {
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
        running = false;
        try {
            eventHandler.flush();
        } catch (Exception e) {
            log.warn("停止时刷新事件缓冲失败：{}", e.getMessage());
        }
        closeQuietly();
        log.info("Oracle LogMiner 采集器已停止");
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
}
