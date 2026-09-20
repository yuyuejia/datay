package com.data.job;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试模式下单个组件的数据采样结果。
 * 以列 + 行的二维结构保存，便于前端表格化展示，供下游组件配置时参考。
 */
public class DebugResult {

    private List<String> columns = new ArrayList<>();

    private List<List<Object>> rows = new ArrayList<>();

    // 物化到 DuckDB 的表（当前节点执行后 DuckDB 中存在的表），供下游组件配置时引用
    private List<DebugTable> tables = new ArrayList<>();

    // 关键属性信息（FlowFile attributes），首次出现即保留，供下游组件配置参考
    private Map<String, String> attributes = new LinkedHashMap<>();

    // 是否因达到采样上限而被截断
    private boolean truncated = false;

    // 最多展示的属性数量
    private static final int MAX_ATTRIBUTES = 30;

    // 单个属性值最大长度
    private static final int MAX_ATTRIBUTE_LENGTH = 1000;

    public DebugResult() {}

    /**
     * 从 FlowFile 中追加采样数据，最多采集 limit 行。
     */
    public synchronized void append(FlowFile flowFile, int limit) {
        if (flowFile == null) {
            return;
        }
        captureAttributes(flowFile);
        if (flowFile.getData() == null) {
            return;
        }
        Object data = flowFile.getData();
        if (data instanceof JSONArray) {
            JSONArray array = (JSONArray) data;
            for (Object item : array) {
                if (rows.size() >= limit) {
                    truncated = true;
                    break;
                }
                if (item instanceof JSONObject) {
                    JSONObject record = (JSONObject) item;
                    for (String key : record.keySet()) {
                        if (!columns.contains(key)) {
                            columns.add(key);
                            // 新增列时，为已有行补 null，保持行列对齐
                            for (List<Object> existing : rows) {
                                existing.add(null);
                            }
                        }
                    }
                    List<Object> row = new ArrayList<>(columns.size());
                    for (String column : columns) {
                        row.add(toSerializable(record.get(column)));
                    }
                    rows.add(row);
                } else {
                    if (columns.isEmpty()) {
                        columns.add("value");
                    }
                    rows.add(new ArrayList<>(Collections.singletonList(toSerializable(item))));
                }
            }
        } else if (data instanceof JSONObject) {
            if (rows.size() >= limit) {
                truncated = true;
                return;
            }
            JSONObject record = (JSONObject) data;
            for (String key : record.keySet()) {
                if (!columns.contains(key)) {
                    columns.add(key);
                }
            }
            List<Object> row = new ArrayList<>(columns.size());
            for (String column : columns) {
                row.add(toSerializable(record.get(column)));
            }
            rows.add(row);
        } else {
            if (rows.size() >= limit) {
                truncated = true;
                return;
            }
            if (columns.isEmpty()) {
                columns.add("value");
            }
            rows.add(new ArrayList<>(Collections.singletonList(toSerializable(data))));
        }
    }

    /**
     * 仅在尚无采样数据时写入（用于从 DuckDB 物化表回填当前节点执行后的所有表）。
     */
    public synchronized void setTablesIfEmpty(List<DebugTable> newTables) {
        if (!rows.isEmpty() || !tables.isEmpty()) {
            return;
        }
        if (newTables != null) {
            this.tables = new ArrayList<>(newTables);
        }
    }

    /**
     * 调试结果中的 DuckDB 表元数据及采样数据。
     */
    public static class DebugTable {

        private String tableName;

        private List<String> columns = new ArrayList<>();

        private List<String> columnTypes = new ArrayList<>();

        private List<List<Object>> rows = new ArrayList<>();

        private boolean truncated = false;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }

        public List<String> getColumnTypes() {
            return columnTypes;
        }

        public void setColumnTypes(List<String> columnTypes) {
            this.columnTypes = columnTypes;
        }

        public List<List<Object>> getRows() {
            return rows;
        }

        public void setRows(List<List<Object>> rows) {
            this.rows = rows;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public void setTruncated(boolean truncated) {
            this.truncated = truncated;
        }
    }

    /**
     * 采集 FlowFile 的关键属性（忽略开始/结束等内部信号，首次出现即保留）。
     */
    private void captureAttributes(FlowFile flowFile) {
        if (attributes.size() >= MAX_ATTRIBUTES) {
            return;
        }
        for (Map.Entry<String, Object> entry : flowFile.getAttributeMap().entrySet()) {
            String key = entry.getKey();
            if ("_start".equals(key) || "_end".equals(key)) {
                continue;
            }
            if (attributes.containsKey(key)) {
                continue;
            }
            attributes.put(key, formatAttributeValue(entry.getValue()));
            if (attributes.size() >= MAX_ATTRIBUTES) {
                break;
            }
        }
    }

    private String formatAttributeValue(Object value) {
        if (value == null) {
            return "null";
        }
        String text;
        if (value instanceof TableMeta) {
            text = formatTableMeta((TableMeta) value);
        } else {
            text = String.valueOf(value);
        }
        if (text.length() > MAX_ATTRIBUTE_LENGTH) {
            text = text.substring(0, MAX_ATTRIBUTE_LENGTH) + "...";
        }
        return text;
    }

    private String formatTableMeta(TableMeta tableMeta) {
        StringBuilder builder = new StringBuilder();
        if (tableMeta.getSchema() != null && !tableMeta.getSchema().isEmpty()) {
            builder.append(tableMeta.getSchema()).append(".");
        }
        builder.append(tableMeta.getTable());
        if (tableMeta.getDbType() != null && !tableMeta.getDbType().isEmpty()) {
            builder.append(" [").append(tableMeta.getDbType()).append("]");
        }
        List<ColumnMeta> columns = tableMeta.columns();
        if (columns != null && !columns.isEmpty()) {
            builder.append(" (");
            for (int i = 0; i < columns.size(); i++) {
                ColumnMeta column = columns.get(i);
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(column.getName()).append(":").append(column.getType());
            }
            builder.append(")");
        }
        return builder.toString();
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<DebugTable> getTables() {
        return tables;
    }

    public void setTables(List<DebugTable> tables) {
        this.tables = tables;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public void setRows(List<List<Object>> rows) {
        this.rows = rows;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    /**
     * 将 JDBC/驱动返回的值转换为可 JSON 序列化的值，避免调试接口因驱动特有类型（如 DuckDB 的 Blob 对象）序列化失败。
     * <p>
     * 二进制转为 Base64 字符串，Clob 转为文本，无法识别的驱动类型转为字符串。
     */
    public static Object toSerializable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return Base64.getEncoder().encodeToString((byte[]) value);
        }
        if (value instanceof java.sql.Blob) {
            try (InputStream in = ((java.sql.Blob) value).getBinaryStream()) {
                return Base64.getEncoder().encodeToString(readAllBytes(in));
            } catch (Exception e) {
                return "<blob>";
            }
        }
        if (value instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) value;
                int length = (int) Math.min(clob.length(), MAX_ATTRIBUTE_LENGTH);
                return clob.getSubString(1, length);
            } catch (Exception e) {
                return "<clob>";
            }
        }
        if (isJsonSafeType(value.getClass())) {
            return value;
        }
        return String.valueOf(value);
    }

    private static boolean isJsonSafeType(Class<?> type) {
        return Number.class.isAssignableFrom(type)
            || CharSequence.class.isAssignableFrom(type)
            || Boolean.class == type
            || Character.class == type
            || java.util.Date.class.isAssignableFrom(type)
            || java.time.temporal.Temporal.class.isAssignableFrom(type)
            || java.time.temporal.TemporalAmount.class.isAssignableFrom(type)
            || java.util.UUID.class == type
            || type.isEnum()
            || Map.class.isAssignableFrom(type)
            || List.class.isAssignableFrom(type);
    }

    private static byte[] readAllBytes(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
