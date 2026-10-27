package com.data.job;

public class Connection {

    private String sourceId;
    private int sourcePort;
    private String targetId;

    /** 连线标签，供路由类组件（如 RouteOnAttribute）按标签分流。 */
    private String label;

    /** 该连线缓冲队列的容量（批次数），为空时使用流程默认值。 */
    private Integer queueCapacity;

    public Connection(String sourceId, String targetId, int sourcePort) {
        this(sourceId, targetId, sourcePort, null);
    }

    public Connection(String sourceId, String targetId, int sourcePort, String label) {
        this(sourceId, targetId, sourcePort, label, null);
    }

    public Connection(String sourceId, String targetId, int sourcePort, String label, Integer queueCapacity) {
        this.sourceId = sourceId;
        this.sourcePort = sourcePort;
        this.targetId = targetId;
        this.label = label;
        this.queueCapacity = queueCapacity;
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

    public Integer getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(Integer queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
