package com.data.job;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class FlowFile {

    public Map<String, Object> getStatus() {
        return status;
    }

    // 数据格式类型枚举
    public enum DataFormat {
        JSON_ARRAY, // JSON数组格式
        JSON_OBJECT, // JSON对象格式
        CSV, // CSV格式
        TEXT, // 纯文本格式
        BINARY, // 二进制格式
    }

    public static final String ATTRIBUTE_TABLE_METADATA = "_tableMetadata";
    public static final String ATTRIBUTE_DATABASE = "_database";
    public static final String ATTRIBUTE_TABLE = "_table";
    public static final String ATTRIBUTE_EVENT_TYPE = "_eventType";
    public static final String ATTRIBUTE_QUERY = "_query";
    public static final String ATTRIBUTE_SQL = "_sql";
    public static final String ATTRIBUTE_ERROR_IGNORE = "_error_ignore";
    public static final String ATTRIBUTE_TIMESTAMP = "_timestamp";

    private final Map<String, Object> attributes;

    private final Map<String, Object> status;

    private DataFormat dataFormat;

    // 不同格式的数据存储
    private JSONArray jsonArray; // JSON数组数据
    private JSONObject jsonObject; // JSON对象数据
    private String csvData; // CSV格式数据
    private String textData; // 文本数据
    private byte[] binaryData; // 二进制数据

    public FlowFile() {
        this.attributes = new HashMap<>();
        this.status = new HashMap<>();
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Map<String, Object> getAttributeMap() {
        return attributes;
    }

    public Object getData() {
        if (dataFormat == null) {
            return null;
        }
        switch (dataFormat) {
            case JSON_ARRAY:
                return jsonArray;
            case JSON_OBJECT:
                return jsonObject;
            case CSV:
                return csvData;
            case TEXT:
                return textData;
            case BINARY:
                return binaryData;
            default:
                return null;
        }
    }

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    public JSONArray getJsonArray() {
        return jsonArray;
    }

    public void setJsonArray(JSONArray jsonArray) {
        this.dataFormat = DataFormat.JSON_ARRAY;
        this.jsonArray = jsonArray;
    }

    public void setJsonObject(JSONObject jsonObject) {
        this.dataFormat = DataFormat.JSON_OBJECT;
        this.jsonObject = jsonObject;
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public String getCsvData() {
        return csvData;
    }

    public void setCsvData(String csvData) {
        this.dataFormat = DataFormat.CSV;
        this.csvData = csvData;
    }

    public String getTextData() {
        return textData;
    }

    public void setTextData(String textData) {
        this.dataFormat = DataFormat.TEXT;
        this.textData = textData;
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public void setBinaryData(byte[] binaryData) {
        this.dataFormat = DataFormat.BINARY;
        this.binaryData = binaryData;
    }

    public void setStatus(String key, Object value) {
        this.status.put(key, value);
    }

    public Map<String, Object> getStatusMap() {
        return status;
    }
}
