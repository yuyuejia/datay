package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.postgresql.PGConnection;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;
import org.postgresql.replication.fluent.logical.ChainedLogicalStreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PostgreSQL 逻辑复制（WAL）采集器。
 *
 * <p>基于 pgjdbc 的复制 API，使用内置 {@code pgoutput} 输出插件实时读取逻辑解码后的
 * 数据变更。采集器负责：自动维护 publication 与 replication slot、读取复制流、
 * 解析 pgoutput 消息（见 {@link PgOutputDecoder}），并把变更转换为通用
 * {@link BinlogEvent} 交给 {@link BinlogEventHandler}。
 */
public class PostgresWalCollector {

    private static final Logger log = LoggerFactory.getLogger(PostgresWalCollector.class);

    private static final String OUTPUT_PLUGIN = "pgoutput";
    private static final long IDLE_SLEEP_MS = 50L;

    private final DatasourceInfo datasourceInfo;
    private final String schemaName;
    private final String publicationName;
    private final String slotName;
    private final Pattern tableNamePattern;
    private Long startLsn;
    private final BinlogEventHandler eventHandler;
    private final PgOutputDecoder decoder;

    /** 当前 publication 覆盖的表（快照范围以此为准）。 */
    private final List<CdcSnapshotReader.TableRef> targetTables = new ArrayList<>();

    private volatile boolean running = false;
    private volatile boolean prepared = false;

    private Connection replicationConnection;
    private PGReplicationStream stream;
    private PGConnection pgConnection;

    /** 当前事务的提交记录 LSN（来自 Begin 消息），用于断点续传。 */
    private volatile long currentTransactionLsn = 0L;

    public PostgresWalCollector(
        DatasourceInfo datasourceInfo,
        String schemaName,
        String publicationName,
        String slotName,
        String tableNamePattern,
        Long startLsn,
        BinlogEventHandler eventHandler
    ) {
        this.datasourceInfo = datasourceInfo;
        this.schemaName = schemaName;
        this.publicationName = publicationName;
        this.slotName = slotName;
        this.tableNamePattern = (tableNamePattern == null || tableNamePattern.trim().isEmpty())
            ? null
            : Pattern.compile(tableNamePattern);
        this.startLsn = startLsn;
        this.eventHandler = eventHandler;
        this.decoder = new PgOutputDecoder(this::resolveTableMeta);
    }

    /**
     * 启动逻辑复制采集，该方法会阻塞当前线程直到 {@link #stop()} 被调用。
     */
    public void start() {
        running = true;
        try (Connection metaConnection = DBUtils.getConnection(datasourceInfo)) {
            if (!prepared) {
                ensurePublication(metaConnection);
                ensureReplicationSlot(metaConnection);
            }

            replicationConnection = openReplicationConnection();
            pgConnection = replicationConnection.unwrap(PGConnection.class);

            ChainedLogicalStreamBuilder builder = pgConnection
                .getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(slotName)
                .withSlotOption("proto_version", 1)
                .withSlotOption("publication_names", publicationName);
            if (startLsn != null) {
                builder.withStartPosition(LogSequenceNumber.valueOf(startLsn));
                log.info("从指定 LSN {} 启动 PostgreSQL 逻辑复制", LogSequenceNumber.valueOf(startLsn).asString());
            } else {
                log.info("从 replication slot [{}] 的确认位点启动 PostgreSQL 逻辑复制", slotName);
            }
            stream = builder.start();
            log.info("PostgreSQL 逻辑复制已启动，slot={}，publication={}，schema={}", slotName, publicationName, schemaName);

            readLoop();
        } catch (Exception e) {
            if (!running) {
                log.info("PostgreSQL 逻辑复制采集器已停止");
                return;
            }
            throw new RuntimeException("启动 PostgreSQL 逻辑复制采集器失败: " + e.getMessage(), e);
        } finally {
            closeQuietly();
        }
    }

    /**
     * 读取复制流并解码消息。
     */
    private void readLoop() throws Exception {
        while (running && !Thread.currentThread().isInterrupted()) {
            ByteBuffer buffer = stream.readPending();
            if (buffer == null) {
                sleep();
                continue;
            }
            char messageType = (char) (buffer.get() & 0xFF);
            decoder.decode(messageType, buffer, decoderCallback);
        }
        log.info("PostgreSQL 逻辑复制读取循环结束");
    }

    private final PgOutputDecoder.Callback decoderCallback = new PgOutputDecoder.Callback() {
        @Override
        public void onInsert(PgOutputDecoder.RelationInfo relation, JSONObject after) {
            BinlogEvent event = createEvent(BinlogEvent.EventType.INSERT, relation);
            event.setAfterData(after);
            eventHandler.handleEvent(event);
        }

        @Override
        public void onUpdate(PgOutputDecoder.RelationInfo relation, JSONObject before, JSONObject after) {
            BinlogEvent event = createEvent(BinlogEvent.EventType.UPDATE, relation);
            event.setBeforeData(before != null ? before : new JSONObject());
            event.setAfterData(after);
            eventHandler.handleEvent(event);
        }

        @Override
        public void onDelete(PgOutputDecoder.RelationInfo relation, JSONObject before) {
            BinlogEvent event = createEvent(BinlogEvent.EventType.DELETE, relation);
            event.setBeforeData(before != null ? before : new JSONObject());
            eventHandler.handleEvent(event);
        }

        @Override
        public void onTruncate(List<PgOutputDecoder.RelationInfo> relations, boolean cascade) {
            for (PgOutputDecoder.RelationInfo relation : relations) {
                log.warn("捕获到 TRUNCATE 事件 {}.{}（cascade={}），逻辑复制模式下暂不支持同步该操作", relation.getSchema(), relation.getTable(), cascade);
            }
        }

        @Override
        public void onBegin(long finalLsn) {
            currentTransactionLsn = finalLsn;
        }

        @Override
        public void onCommit(long commitLsn) {
            handleCommit(commitLsn);
        }
    };

    /**
     * 事务提交：刷新事件缓冲并把确认位点回传给服务端。
     */
    private void handleCommit(long commitLsn) {
        eventHandler.flush();
        try {
            LogSequenceNumber lsn = LogSequenceNumber.valueOf(commitLsn);
            stream.setAppliedLSN(lsn);
            stream.setFlushedLSN(lsn);
            stream.forceUpdateStatus();
        } catch (Exception e) {
            log.warn("更新复制确认位点失败：{}", e.getMessage());
        }
    }

    private BinlogEvent createEvent(BinlogEvent.EventType eventType, PgOutputDecoder.RelationInfo relation) {
        BinlogEvent event = new BinlogEvent(eventType, relation.getSchema(), relation.getTable());
        event.setTableMeta(relation.getTableMeta());
        event.setTimestamp(System.currentTimeMillis());
        event.setBinlogFileName(slotName);
        event.setBinlogPosition(currentTransactionLsn);
        return event;
    }

    private TableMeta resolveTableMeta(String schema, String table) throws SQLException {
        try (Connection connection = DBUtils.getConnection(datasourceInfo)) {
            return DBUtils.getTableMetaData(connection, schema, table);
        }
    }

    /**
     * 确保 publication 存在并包含目标 schema 下匹配的表。
     */
    private void ensurePublication(Connection connection) throws SQLException {
        List<String> tables = listTargetTables(connection);
        if (tables.isEmpty()) {
            throw new SQLException("未在 schema [" + schemaName + "] 中找到匹配表，无法创建 publication，请检查 tableNamePattern");
        }

        targetTables.clear();
        for (String table : tables) {
            targetTables.add(new CdcSnapshotReader.TableRef(schemaName, table));
        }

        StringBuilder tableList = new StringBuilder();
        for (String table : tables) {
            if (tableList.length() > 0) {
                tableList.append(", ");
            }
            tableList.append(quoteIdentifier(schemaName)).append(".").append(quoteIdentifier(table));
        }

        if (publicationExists(connection)) {
            DBUtils.execute(connection, "ALTER PUBLICATION " + quoteIdentifier(publicationName) + " SET TABLE " + tableList);
            log.info("publication [{}] 已更新，包含表：{}", publicationName, tables);
        } else {
            DBUtils.execute(connection, "CREATE PUBLICATION " + quoteIdentifier(publicationName) + " FOR TABLE " + tableList);
            log.info("publication [{}] 已创建，包含表：{}", publicationName, tables);
        }
    }

    private List<String> listTargetTables(Connection connection) throws SQLException {
        List<TableMeta> tableMetas = DBUtils.getTableList(connection, schemaName);
        Set<String> tables = new LinkedHashSet<>();
        for (TableMeta tableMeta : tableMetas) {
            String table = tableMeta.getTable();
            if (table == null || table.trim().isEmpty()) {
                continue;
            }
            if (tableNamePattern == null || tableNamePattern.matcher(table).matches()) {
                tables.add(table);
            }
        }
        return new ArrayList<>(tables);
    }

    private boolean publicationExists(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM pg_publication WHERE pubname = ?")) {
            ps.setString(1, publicationName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 预先确保 publication 与 replication slot 就绪，并返回可用于启动复制的 LSN（slot 一致点）。
     * <p>用于「先全量快照、后增量」场景：必须在读取快照前创建 slot，保证增量不丢。
     */
    public long prepare() {
        try (Connection metaConnection = DBUtils.getConnection(datasourceInfo)) {
            ensurePublication(metaConnection);
            LogSequenceNumber lsn = ensureReplicationSlot(metaConnection);
            prepared = true;
            return lsn.asLong();
        } catch (SQLException e) {
            throw new RuntimeException("初始化 PostgreSQL 复制对象失败: " + e.getMessage(), e);
        }
    }

    public void setStartLsn(Long startLsn) {
        this.startLsn = startLsn;
    }

    /**
     * 返回 publication 覆盖的表范围（须先调用 {@link #prepare()}）。
     */
    public List<CdcSnapshotReader.TableRef> getTargetTables() {
        return new ArrayList<>(targetTables);
    }

    /**
     * 确保逻辑复制槽存在。
     */
    private LogSequenceNumber ensureReplicationSlot(Connection connection) throws SQLException {
        LogSequenceNumber confirmed = getReplicationSlotLsn(connection);
        if (confirmed != null) {
            log.info("replication slot [{}] 已存在，将复用其确认位点 {}", slotName, confirmed.asString());
            return confirmed;
        }
        Connection replicationConn = openReplicationConnection();
        try {
            PGConnection pg = replicationConn.unwrap(PGConnection.class);
            LogSequenceNumber consistentPoint = pg
                .getReplicationAPI()
                .createReplicationSlot()
                .logical()
                .withSlotName(slotName)
                .withOutputPlugin(OUTPUT_PLUGIN)
                .make()
                .getConsistentPoint();
            log.info("replication slot [{}] 已创建，一致点 {}", slotName, consistentPoint.asString());
            return consistentPoint;
        } finally {
            closeQuietly(replicationConn);
        }
    }

    /**
     * 读取复制槽的确认位点，不存在返回 {@code null}。
     * <p>新建但尚未消费的槽 {@code confirmed_flush_lsn} 为空，此时回退到 {@code restart_lsn}。
     */
    private LogSequenceNumber getReplicationSlotLsn(Connection connection) throws SQLException {
        try (
            PreparedStatement ps = connection.prepareStatement(
                "SELECT confirmed_flush_lsn, restart_lsn FROM pg_replication_slots WHERE slot_name = ?"
            )
        ) {
            ps.setString(1, slotName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String lsn = rs.getString(1);
                if (lsn == null) {
                    lsn = rs.getString(2);
                }
                return lsn == null ? LogSequenceNumber.INVALID_LSN : LogSequenceNumber.valueOf(lsn);
            }
        }
    }

    /**
     * 打开逻辑复制连接。
     *
     * <p>pgjdbc 需要在启动参数中携带 {@code replication=database} 才能进入 walsender 模式；
     * 由于该参数在握手前依据 {@code assumeMinServerVersion} 判断，且复制命令必须走简单查询协议，
     * 因此这里额外设置 {@code assumeMinServerVersion=9.4} 与 {@code preferQueryMode=simple}。
     */
    private Connection openReplicationConnection() throws SQLException {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("user", datasourceInfo.getUsername());
        properties.setProperty("password", datasourceInfo.getPassword());
        properties.setProperty("replication", "database");
        properties.setProperty("assumeMinServerVersion", "9.4");
        properties.setProperty("preferQueryMode", "simple");
        return java.sql.DriverManager.getConnection(datasourceInfo.getUrl(), properties);
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private void sleep() {
        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    public void stop() {
        running = false;
        try {
            eventHandler.flush();
        } catch (Exception e) {
            log.warn("停止时刷新事件缓冲失败：{}", e.getMessage());
        }
        closeQuietly();
        log.info("PostgreSQL 逻辑复制采集器已停止");
    }

    public boolean isRunning() {
        return running;
    }

    private void closeQuietly() {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
            stream = null;
        }
        closeQuietly(replicationConnection);
        replicationConnection = null;
        pgConnection = null;
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }
}
