package com.data.job;

public class Connection {

    private String sourceId;
    private int sourcePort;
    private String targetId;

    public Connection(String sourceId, String targetId, int sourcePort) {
        this.sourceId = sourceId;
        this.sourcePort = sourcePort;
        this.targetId = targetId;
    }

    // Getter/Setter 保持与JSON字段名称一致
    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
}
