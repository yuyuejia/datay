package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * MySQL Binlog采集器
 * 使用mysql-binlog-connector-java库进行Binlog采集和解析
 */
public class MySQLBinlogCollector {

    private static final Logger log = LoggerFactory.getLogger(MySQLBinlogCollector.class);

    /** 默认的从库 server-id，可通过 {@link #setServerId(long)} 覆盖。 */
    public static final long DEFAULT_SERVER_ID = 1000L;

    /** MySQL server_id 为无符号 32 位整数。 */
    private static final long MAX_SERVER_ID = 4294967295L;

    private final BinaryLogClient client;
    private final BinlogEventHandler eventHandler;

    // 表映射缓存，用于TABLE_MAP事件
    private final Map<Long, TableMeta> tableMapCache = new ConcurrentHashMap<>();

    // 配置信息
    private DatasourceInfo datasourceInfo;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private volatile Pattern databaseNamePattern;
    private volatile Pattern tableNamePattern;

    // 状态信息
    private volatile boolean running = false;
    private String lastBinlogFile;
    private long lastBinlogPosition;

    // 伪装成从库的唯一标识，默认 1000，可配置
    private long serverId = DEFAULT_SERVER_ID;

    public MySQLBinlogCollector(String host, int port, String username, String password, BinlogEventHandler eventHandler) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.eventHandler = eventHandler;

        this.client = new BinaryLogClient(host, port, username, password);
        configureClient();
    }

    public MySQLBinlogCollector(DatasourceInfo datasourceInfo, BinlogEventHandler eventHandler) {
        this.datasourceInfo = datasourceInfo;
        this.host = getHostFromUrl(datasourceInfo.getUrl());
        this.port = getPortFromUrl(datasourceInfo.getUrl());
        this.username = datasourceInfo.getUsername();
        this.password = datasourceInfo.getPassword();
        this.eventHandler = eventHandler;

        this.client = new BinaryLogClient(host, port, username, password);
        configureClient();
    }

    /**
     * 从URL中提取主机名
     */
    private String getHostFromUrl(String url) {
        // 简化实现，实际应用中需要解析JDBC URL
        // 格式示例：jdbc:mysql://localhost:3306/database
        if (url.startsWith("jdbc:mysql://")) {
            String[] parts = url.substring(13).split("[:/]");
            return parts.length > 0 ? parts[0] : "localhost";
        }
        return "localhost";
    }

    /**
     * 从URL中提取端口号
     */
    private int getPortFromUrl(String url) {
        // 简化实现，实际应用中需要解析JDBC URL
        if (url.startsWith("jdbc:mysql://")) {
            String[] parts = url.substring(13).split("[:/]");
            if (parts.length > 1) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    // 使用默认端口
                }
            }
        }
        return 3306; // MySQL默认端口
    }

    /**
     * 配置Binlog客户端
     */
    private void configureClient() {
        // 设置从库 server-id
        client.setServerId(serverId);

        // 设置事件监听器
        client.registerEventListener(this::handleEvent);

        // CHAR/BINARY 按字节数组反序列化，避免二进制列（VARBINARY/BINARY）被按字符编码解码而丢失数据
        EventDeserializer eventDeserializer = new EventDeserializer();
        eventDeserializer.setCompatibilityMode(EventDeserializer.CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY);
        client.setEventDeserializer(eventDeserializer);

        // 设置连接异常处理器
        client.registerLifecycleListener(
            new BinaryLogClient.LifecycleListener() {
                @Override
                public void onConnect(BinaryLogClient client) {
                    log.info("成功连接到MySQL服务器: {}:{}", host, port);
                    running = true;
                }

                @Override
                public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
                    log.error("与MySQL服务器通信失败: {}", ex.getMessage(), ex);
                    running = false;
                }

                @Override
                public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
                    log.error("Binlog事件反序列化失败: {}", ex.getMessage(), ex);
                }

                @Override
                public void onDisconnect(BinaryLogClient client) {
                    log.info("与MySQL服务器断开连接");
                    running = false;
                }
            }
        );
    }

    /**
     * 启动Binlog采集
     */
    public void start() {
        if (running) {
            log.warn("Binlog采集器已经在运行中");
            return;
        }

        try {
            log.info("开始启动MySQL Binlog采集器，连接: {}:{}，server-id: {}", host, port, serverId);
            client.connect();
        } catch (IOException e) {
            log.error("启动Binlog采集器失败: {}", e.getMessage(), e);
            throw new RuntimeException("启动Binlog采集器失败", e);
        }
    }

    /**
     * 停止Binlog采集
     */
    public void stop() {
        if (!running) {
            log.warn("Binlog采集器已经停止");
            return;
        }

        try {
            log.info("停止MySQL Binlog采集器");
            client.disconnect();
            running = false;
        } catch (IOException e) {
            log.error("停止Binlog采集器失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从指定位置开始采集
     */
    public void startFromPosition(String binlogFile, long position) {
        client.setBinlogFilename(binlogFile);
        client.setBinlogPosition(position);
        start();
    }

    /**
     * 处理Binlog事件
     */
    private void handleEvent(Event event) {
        try {
            EventType eventType = event.getHeader().getEventType();
            // 更新binlog位置
            updateLastPosition(event);

            // 注释：这里处理数据库事务相关的逻辑
            // 注意：在实际生产环境中，需要根据业务需求处理事务边界
            // 当前实现中，我们只处理数据变更事件，不处理完整的事务

            switch (eventType) {
                case TABLE_MAP:
                    handleTableMapEvent(event);
                    break;
                case EXT_WRITE_ROWS:
                case PRE_GA_WRITE_ROWS:
                case WRITE_ROWS:
                    handleWriteRowsEvent(event);
                    break;
                case PRE_GA_UPDATE_ROWS:
                case EXT_UPDATE_ROWS:
                case UPDATE_ROWS:
                    handleUpdateRowsEvent(event);
                    break;
                case EXT_DELETE_ROWS:
                case PRE_GA_DELETE_ROWS:
                case DELETE_ROWS:
                    handleDeleteRowsEvent(event);
                    break;
                case QUERY:
                    handleQueryEvent((QueryEventData) event.getData());
                    break;
                case XID:
                    handleXidEvent((XidEventData) event.getData());
                    break;
                case ROTATE:
                    handleRotateEvent((RotateEventData) event.getData());
                    break;
                case FORMAT_DESCRIPTION:
                    handleFormatDescriptionEvent((FormatDescriptionEventData) event.getData());
                    break;
                default:
                    log.debug("忽略未处理的事件类型: {}", eventType);
            }
        } catch (Exception e) {
            log.error("处理Binlog事件失败: {}", e.getMessage(), e);
            // 创建错误事件并传递给处理器
            BinlogEvent errorEvent = createErrorEvent(event, e);
            eventHandler.handleError(errorEvent, e);
        }
    }

    /**
     * 处理TABLE_MAP事件
     */
    private void handleTableMapEvent(Event event) throws SQLException {
        TableMapEventData eventData = event.getData();
        if (tableMapCache.containsKey(eventData.getTableId())) {
            return;
        }
        if (databaseNamePattern != null && !databaseNamePattern.matcher(eventData.getDatabase()).matches()) {
            return;
        }
        if (tableNamePattern != null && !tableNamePattern.matcher(eventData.getTable()).matches()) {
            return;
        }
        try (Connection connection = DBUtils.getConnection(getDatasourceInfo())) {
            TableMeta tableInfo = DBUtils.getTableMetaData(connection, eventData.getDatabase(), eventData.getTable());
            if (tableInfo.columns().isEmpty()) {
                log.warn("未找到表元数据: {}.{}", eventData.getDatabase(), eventData.getTable());
                return;
            }
            tableMapCache.put(eventData.getTableId(), tableInfo);
        }
        log.debug("处理TABLE_MAP事件: {}.{}", eventData.getDatabase(), eventData.getTable());
    }

    /**
     * 处理INSERT事件
     */
    private void handleWriteRowsEvent(Event event) {
        WriteRowsEventData data = event.getData();
        TableMeta tableInfo = tableMapCache.get(data.getTableId());

        if (tableInfo != null) {
            for (Object[] row : data.getRows()) {
                BinlogEvent binlogEvent = createBinlogEvent(BinlogEvent.EventType.INSERT, tableInfo, event);
                binlogEvent.setAfterData(convertRowToMap(tableInfo, row, data.getIncludedColumns()));
                eventHandler.handleEvent(binlogEvent);
            }
        }
    }

    /**
     * 处理UPDATE事件
     */
    private void handleUpdateRowsEvent(Event event) {
        UpdateRowsEventData data = event.getData();
        TableMeta tableInfo = tableMapCache.get(data.getTableId());

        if (tableInfo != null) {
            for (Map.Entry<Serializable[], Serializable[]> row : data.getRows()) {
                BinlogEvent binlogEvent = createBinlogEvent(BinlogEvent.EventType.UPDATE, tableInfo, event);
                binlogEvent.setBeforeData(convertRowToMap(tableInfo, row.getKey(), data.getIncludedColumns()));
                binlogEvent.setAfterData(convertRowToMap(tableInfo, row.getValue(), data.getIncludedColumns()));
                eventHandler.handleEvent(binlogEvent);
            }
        }
    }

    /**
     * 处理DELETE事件
     */
    private void handleDeleteRowsEvent(Event event) {
        DeleteRowsEventData data = event.getData();
        TableMeta tableInfo = tableMapCache.get(data.getTableId());

        if (tableInfo != null) {
            for (Object[] row : data.getRows()) {
                BinlogEvent binlogEvent = createBinlogEvent(BinlogEvent.EventType.DELETE, tableInfo, event);
                binlogEvent.setBeforeData(convertRowToMap(tableInfo, row, data.getIncludedColumns()));
                eventHandler.handleEvent(binlogEvent);
            }
        }
    }

    /**
     * 处理QUERY事件
     */
    private void handleQueryEvent(QueryEventData data) {
        // 注释：这里可以处理DDL语句或其他查询
        // 当前实现中，我们主要关注数据变更事件，QUERY事件可以用于监控DDL变更
        log.debug("处理QUERY事件: {}", data.getSql());
        if (!isQueryDDL(data.getSql().toLowerCase())) {
            return;
        }
        // 按数据库/表过滤，避免同步其它库的 DDL 到目标端
        if (
            databaseNamePattern != null &&
            data.getDatabase() != null &&
            !databaseNamePattern.matcher(data.getDatabase()).matches()
        ) {
            return;
        }
        if (tableNamePattern != null) {
            String table = extractDdlTableName(data.getSql());
            if (table == null || !tableNamePattern.matcher(table).matches()) {
                return;
            }
        }
        BinlogEvent binlogEvent = new BinlogEvent(BinlogEvent.EventType.QUERY, data.getDatabase(), null);
        binlogEvent.setBinlogFileName(getLastBinlogFile());
        binlogEvent.setBinlogPosition(getLastBinlogPosition());
        binlogEvent.setQuery(data.getSql());
        eventHandler.handleEvent(binlogEvent);
    }

    /**
     * 从 DDL 语句中尽力解析目标表名，用于表过滤。
     */
    private String extractDdlTableName(String sql) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?i)\\b(?:alter|create|truncate|drop)\\s+table\\s+(?:if\\s+(?:not\\s+)?exists\\s+)?[`\"']?([\\w$]+)[`\"']?")
            .matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = java.util.regex.Pattern.compile("(?i)\\brename\\s+table\\s+[`\"']?([\\w$]+)[`\"']?").matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isQueryDDL(String sql) {
        return (
            sql.startsWith("alter ") ||
            sql.startsWith("create ") ||
            sql.startsWith("truncate ") ||
            sql.startsWith("rename ") ||
            sql.startsWith("drop ")
//            sql.startsWith("begin") ||
//            sql.startsWith("commit")
        );
    }

    /**
     * 处理XID事件（事务提交）
     */
    private void handleXidEvent(XidEventData data) {
        // XID 事件表示事务提交：刷新事件缓冲，避免合并缓冲导致数据延迟下发
        log.debug("处理commit (XID)事件，事务ID: {}", data.getXid());
        eventHandler.flush();

//        BinlogEvent binlogEvent = new BinlogEvent(BinlogEvent.EventType.XID, null, null);
//        binlogEvent.setBinlogFileName(getLastBinlogFile());
//        binlogEvent.setBinlogPosition(getLastBinlogPosition());
//        binlogEvent.setXid(data.getXid());
//        eventHandler.handleEvent(binlogEvent);
    }

    /**
     * 处理ROTATE事件（Binlog文件切换）
     */
    private void handleRotateEvent(RotateEventData data) {
        log.info("Binlog文件切换: {} -> {}", lastBinlogFile, data.getBinlogFilename());
        lastBinlogFile = data.getBinlogFilename();
    }

    /**
     * 处理FORMAT_DESCRIPTION事件
     */
    private void handleFormatDescriptionEvent(FormatDescriptionEventData data) {
        log.debug("处理FORMAT_DESCRIPTION事件，Binlog版本: {}", data.getBinlogVersion());
    }

    /**
     * 创建BinlogEvent对象
     */
    private BinlogEvent createBinlogEvent(BinlogEvent.EventType eventType, TableMeta tableInfo, Event event) {
        BinlogEvent binlogEvent = new BinlogEvent(eventType, tableInfo.getSchema(), tableInfo.getTable());
        binlogEvent.setTableMeta(tableInfo);
        binlogEvent.setBinlogFileName(getLastBinlogFile());
        binlogEvent.setBinlogPosition(getLastBinlogPosition());
        binlogEvent.setTimestamp(event.getHeader().getTimestamp());
        binlogEvent.setServerId(event.getHeader().getServerId());
        return binlogEvent;
    }

    /**
     * 将行数据转换为Map
     */
    private JSONObject convertRowToMap(TableMeta tableInfo, Object[] row, BitSet includedColumns) {
        JSONObject result = new JSONObject();
        int i = includedColumns.nextSetBit(0);
        while (i != -1) {
            if (tableInfo.columns().isEmpty()) {
                System.out.println(tableInfo);
            }
            ColumnMeta columnMeta = tableInfo.columns().get(i);
            result.put(columnMeta.getName(), row[i]);
            i = includedColumns.nextSetBit(i + 1);
        }
        return result;
    }

    /**
     * 创建错误事件
     */
    private BinlogEvent createErrorEvent(Event event, Exception e) {
        BinlogEvent errorEvent = new BinlogEvent(BinlogEvent.EventType.UNKNOWN, null, null);
        errorEvent.setTimestamp(event.getHeader().getTimestamp());
        return errorEvent;
    }

    /**
     * 更新最后处理的位置
     */
    private void updateLastPosition(Event event) {
        EventHeaderV4 header = event.getHeader();
        lastBinlogPosition = header.getPosition();
    }

    // Getter方法
    public boolean isRunning() {
        return running;
    }

    public String getLastBinlogFile() {
        return lastBinlogFile;
    }

    public long getLastBinlogPosition() {
        return lastBinlogPosition;
    }

    public DatasourceInfo getDatasourceInfo() {
        return datasourceInfo;
    }

    public Pattern getDatabaseNamePattern() {
        return databaseNamePattern;
    }

    public void setDatabaseNamePattern(Pattern databaseNamePattern) {
        this.databaseNamePattern = databaseNamePattern;
    }

    public Pattern getTableNamePattern() {
        return tableNamePattern;
    }

    public void setTableNamePattern(Pattern tableNamePattern) {
        this.tableNamePattern = tableNamePattern;
    }

    public long getServerId() {
        return serverId;
    }

    /**
     * 设置伪装成从库的 server-id。必须在 {@link #start()} 之前调用。
     *
     * @param serverId MySQL server_id，取值范围 1 ~ 4294967295
     */
    public void setServerId(long serverId) {
        if (serverId <= 0 || serverId > MAX_SERVER_ID) {
            throw new IllegalArgumentException("serverId 必须在 1 ~ " + MAX_SERVER_ID + " 之间，当前值: " + serverId);
        }
        this.serverId = serverId;
        this.client.setServerId(serverId);
    }
}
