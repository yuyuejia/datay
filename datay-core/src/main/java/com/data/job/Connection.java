package com.data.job;

public class Connection {

    private String sourceId;
    private int sourcePort;
    private String targetId;

    /** 连线标签，供路由类组件（如 RouteOnAttribute）按标签分流。 */
    private String label;

    public Connection(String sourceId, String targetId, int sourcePort) {
        this(sourceId, targetId, sourcePort, null);
    }

    public Connection(String sourceId, String targetId, int sourcePort, String label) {
        this.sourceId = sourceId;
        this.sourcePort = sourcePort;
        this.targetId = targetId;
        this.label = label;
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
