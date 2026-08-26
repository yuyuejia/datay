package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;

/**
 * LogFlowFile组件 - 接收flowfile并记录数据内容
 * 该组件用于调试和监控数据流，记录接收到的flowfile数据内容
 */
public class LogFlowFile extends FlowComponent {

    // 日志级别：DEBUG, INFO, WARN, ERROR
    private String logLevel = "INFO";

    // 是否记录数据内容（默认记录）
    private boolean logDataContent = true;

    // 是否记录属性信息
    private boolean logAttributes = true;

    // 最大数据记录长度（避免日志过大）
    private int maxDataLength = 5000;

    // 是否记录数据格式信息
    private boolean logDataFormat = true;

    // 是否记录时间戳
    private boolean logTimestamp = true;

     public LogFlowFile() {
        setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null) {
            logWarn("接收到空的FlowFile");
            return;
        }

        // 检查是否为结束信号
        if (flowFile.getAttribute("_end") != null) {
            return;
        }

        // 记录FlowFile信息
        logFlowFileInfo(flowFile);
        writeRecords(flowFile);
    }

    /**
     * 记录FlowFile的详细信息
     */
    private void logFlowFileInfo(FlowFile flowFile) {
        StringBuilder logMessage = new StringBuilder();

        logMessage.append("接收到FlowFile: ");

        // 记录数据格式
        //        if (logDataFormat) {
        //            Object data = flowFile.getData();
        //            if (data != null) {
        //                logMessage.append("数据格式: ").append(getDataFormatDescription(data)).append(" - ");
        //            }
        //        }

        // 记录属性信息
        if (logAttributes) {
            logAttributes(flowFile, logMessage);
        }

        // 记录数据内容
        if (logDataContent) {
            logDataContent(flowFile, logMessage);
        }

        // 根据日志级别记录
        switch (logLevel.toUpperCase()) {
            case "DEBUG":
                logDebug(logMessage.toString());
                break;
            case "WARN":
                logWarn(logMessage.toString());
                break;
            case "ERROR":
                logError(logMessage.toString());
                break;
            default: // INFO
                logInfo(logMessage.toString());
                break;
        }
    }

    /**
     * 记录属性信息
     */
    private void logAttributes(FlowFile flowFile, StringBuilder logMessage) {
        // 这里可以添加特定的属性记录逻辑
        // 目前主要记录通用属性
        Object eventType = flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE);
        Object table = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE);
        Object timestamp = flowFile.getAttribute(FlowFile.ATTRIBUTE_TIMESTAMP);

        if (eventType != null) {
            logMessage.append("事件类型: ").append(eventType).append(" - ");
        }
        if (table != null) {
            logMessage.append("表名: ").append(table).append(" - ");
        }
        if (timestamp != null) {
            logMessage.append("时间: ").append(timestamp).append(" - ");
        }
    }

    /**
     * 记录数据内容
     */
    private void logDataContent(FlowFile flowFile, StringBuilder logMessage) {
        Object data = flowFile.getData();
        if (data == null) {
            logMessage.append("数据内容: null");
            return;
        }

        String dataString = getDataString(data);
        if (dataString.length() > maxDataLength) {
            dataString = dataString.substring(0, maxDataLength) + "... [截断]";
        }
        logMessage.append("数据内容: ").append(dataString);
    }

    /**
     * 获取数据的字符串表示
     */
    private String getDataString(Object data) {
        if (data instanceof JSONArray) {
            return ((JSONArray) data).toJSONString();
        } else if (data instanceof JSONObject) {
            return ((JSONObject) data).toJSONString();
        } else if (data instanceof String) {
            return (String) data;
        } else if (data instanceof byte[]) {
            return "二进制数据[" + ((byte[]) data).length + "字节]";
        } else {
            return String.valueOf(data);
        }
    }

    // Getter和Setter方法，用于参数注入

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public boolean isLogDataContent() {
        return logDataContent;
    }

    public void setLogDataContent(boolean logDataContent) {
        this.logDataContent = logDataContent;
    }

    public boolean isLogAttributes() {
        return logAttributes;
    }

    public void setLogAttributes(boolean logAttributes) {
        this.logAttributes = logAttributes;
    }

    public int getMaxDataLength() {
        return maxDataLength;
    }

    public void setMaxDataLength(int maxDataLength) {
        this.maxDataLength = maxDataLength;
    }

    public boolean isLogDataFormat() {
        return logDataFormat;
    }

    public void setLogDataFormat(boolean logDataFormat) {
        this.logDataFormat = logDataFormat;
    }

    public boolean isLogTimestamp() {
        return logTimestamp;
    }

    public void setLogTimestamp(boolean logTimestamp) {
        this.logTimestamp = logTimestamp;
    }
}
