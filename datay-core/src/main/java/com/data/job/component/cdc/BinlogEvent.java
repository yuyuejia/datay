package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.TableMeta;

import java.util.Map;

/**
 * MySQL Binlog事件数据模型
 * 表示从MySQL Binlog中解析出的数据变更事件
 */
public class BinlogEvent {

    // 事件类型枚举
    public enum EventType {
        INSERT,
        UPDATE,
        DELETE,
        QUERY,
        XID,
        ROTATE,
        FORMAT_DESCRIPTION,
        TABLE_MAP,
        UNKNOWN,
    }

    private EventType eventType;
    private String database;
    private TableMeta tableMeta;
    private String table;
    private Long timestamp;
    private Long serverId;
    private String binlogFileName;
    private Long binlogPosition;
    private Long nextBinlogPosition;

    // 数据变更内容
    private JSONObject beforeData; // UPDATE/DELETE事件的前镜像数据
    private JSONObject afterData; // INSERT/UPDATE事件的后镜像数据

    // 事务相关信息
    private Long xid; // 事务ID
    private String query; // QUERY事件的SQL语句

    public BinlogEvent() {}

    public BinlogEvent(EventType eventType, String database, String table) {
        this.eventType = eventType;
        this.database = database;
        this.table = table;
        this.timestamp = System.currentTimeMillis();
    }

    // Getter和Setter方法
    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public void setBinlogFileName(String binlogFileName) {
        this.binlogFileName = binlogFileName;
    }

    public Long getBinlogPosition() {
        return binlogPosition;
    }

    public void setBinlogPosition(Long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    public Long getNextBinlogPosition() {
        return nextBinlogPosition;
    }

    public void setNextBinlogPosition(Long nextBinlogPosition) {
        this.nextBinlogPosition = nextBinlogPosition;
    }

    public Map<String, Object> getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(JSONObject beforeData) {
        this.beforeData = beforeData;
    }

    public JSONObject getAfterData() {
        return afterData;
    }

    public void setAfterData(JSONObject afterData) {
        this.afterData = afterData;
    }

    public Long getXid() {
        return xid;
    }

    public void setXid(Long xid) {
        this.xid = xid;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public TableMeta getTableMeta() {
        return tableMeta;
    }

    public void setTableMeta(TableMeta tableMeta) {
        this.tableMeta = tableMeta;
    }

    /**
     * 转换为JSON格式，便于传输和存储
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("eventType", eventType.name());
        json.put("database", database);
        json.put("table", table);
        json.put("timestamp", timestamp);
        json.put("serverId", serverId);
        json.put("binlogFileName", binlogFileName);
        json.put("binlogPosition", binlogPosition);
        json.put("nextBinlogPosition", nextBinlogPosition);
        json.put("xid", xid);
        json.put("query", query);

        if (beforeData != null) {
            json.put("beforeData", new JSONObject(beforeData));
        }
        if (afterData != null) {
            json.put("afterData", new JSONObject(afterData));
        }

        return json;
    }

    @Override
    public String toString() {
        return String.format("BinlogEvent{eventType=%s, database=%s, table=%s, position=%d}", eventType, database, table, binlogPosition);
    }
}
