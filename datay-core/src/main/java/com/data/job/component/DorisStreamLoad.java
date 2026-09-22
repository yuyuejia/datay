package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@ComponentRegister(
    value = "DorisStreamLoad",
    name = "Doris写入",
    group = "数据输出",
    desc = "通过 Stream Load 将数据批量导入 Doris，支持 insert/update/delete 写入模式。",
    order = 30
)
public class DorisStreamLoad extends FlowComponent {

    // 对应job.json参数
    private String schema;
    private String table;
    private String model; // 写入模式：insert, update, delete

    private DatasourceInfo datasource;

    private final Map<String, TableMeta> tableMetaMap = new HashMap<>();

    public DorisStreamLoad() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 调试模式：跳过实际写库
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入 Doris");
            return;
        }
        if (flowFile.getData() == null || flowFile.getJsonArray().isEmpty()) {
            return;
        }

        // 获取目标表名和schema
        String targetTableName = getTargetTableName(flowFile);
        String targetSchema = getTargetSchema(flowFile);

        // 获取源表元数据
        TableMeta targetTable = getTableMeta(targetTableName, targetSchema, flowFile);
        // 获取事件类型
        String eventType = getEventType(flowFile);
        if (eventType == null) {
            eventType = this.model != null ? this.model.toUpperCase() : "INSERT";
        }
        if ("UPDATE".equals(eventType)) {
            logError("不支持的事件类型: " + eventType);
            return;
        }

        // 执行StreamLoad
        try {
            streamLoadData(targetTable, flowFile, eventType);
        } catch (IOException e) {
            logError("StreamLoad失败: " + e.getMessage());
            throw new RuntimeException("StreamLoad失败", e);
        }
    }

    private synchronized TableMeta getTableMeta(String table, String schema, FlowFile flowFile) {
        String tableKey = schema + "." + table;
        TableMeta targetTable = this.tableMetaMap.get(tableKey);
        if (targetTable == null) {
            try (Connection targetConn = DBUtils.getConnection(datasource)) {
                if (!DBUtils.tableExists(targetConn, schema, table)) {
                    String targetDbType = datasource.getType();
                    if (flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA) != null) {
                        logInfo("表 " + schema + "." + table + " 不存在，从flowfile属性获取表元数据");
                        TableMeta sourceTable = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
                        targetTable = DatabaseConverter.convert(sourceTable.getDbType(), targetDbType, sourceTable);
                        targetTable.setDbType(targetDbType);
                        targetTable.setTable(table);
                        targetTable.setSchema(schema);
                    }
                    logInfo("表 " + schema + "." + table + " 不存在，创建表");
                    String ddl = DatabaseConverter.generateTableDDL(targetDbType, targetTable);
                    logInfo("创建表 " + schema + "." + table + " 的DDL: " + ddl);
                    DBUtils.execute(targetConn, ddl);
                } else {
                    logInfo("表 " + schema + "." + table + " 已存在");
                    targetTable = DBUtils.getTableMetaData(targetConn, schema, table);
                }
                if ("overwrite".equals(this.model)) {
                    logInfo("清空表 " + schema + "." + table);
                    DBUtils.execute(targetConn, String.format("TRUNCATE TABLE %s", schema + "." + table));
                }
            } catch (SQLException e) {
                logError("获取表元数据失败，" + e.getMessage());
                throw new RuntimeException(e);
            }

            this.tableMetaMap.put(tableKey, targetTable);
        }

        return targetTable;
    }

    /**
     * 执行StreamLoad数据写入
     */
    private void streamLoadData(TableMeta targetTable, FlowFile flowFile, String eventType) throws IOException {
        JSONArray records = flowFile.getJsonArray();
        // 构建StreamLoad URL
        String streamLoadUrl = buildStreamLoadUrl(targetTable);

        // 构建请求体（JSON格式）
        String jsonData = buildJsonData(records, eventType);

        final HttpClientBuilder httpClientBuilder = HttpClients
                .custom()
                .setRedirectStrategy(new DefaultRedirectStrategy() {
                    @Override
                    protected boolean isRedirectable(String method) {
                        return true;
                    }
                });

        try (CloseableHttpClient client = httpClientBuilder.build()) {
            HttpPut put = new HttpPut(streamLoadUrl);
            StringEntity entity = new StringEntity(jsonData, "UTF-8");
            put.setHeader(HttpHeaders.EXPECT, "100-continue");
            put.setHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(this.datasource.getUsername(), this.datasource.getPassword()));
            put.setHeader("format", "json");
            put.setHeader("strip_outer_array", "true");
            put.setHeader("fuzzy_parse", "true");
            if ("DELETE".equals(eventType)) {
                put.setHeader("merge_type", "DELETE");
            }
            // the label header is optional, not necessary
            // use label header can ensure at most once semantics
//            put.setHeader("label", "39c25a5c-7000-496e-a98e-348a264c81de");
            put.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(put)) {
                String loadResult = "";
                if (response.getEntity() != null) {
                    loadResult = EntityUtils.toString(response.getEntity());
                }
                final int statusCode = response.getStatusLine().getStatusCode();
                // statusCode 200 just indicates that doris be service is ok, not stream load
                // you should see the output content to find whether stream load is success
                if (statusCode != 200) {
                    throw new IOException(
                            String.format("Stream load failed, statusCode=%s load result=%s", statusCode, loadResult));
                }
                this.logInfo("已成功写入 " + records.size() + " 条记录到表 " + targetTable.getTable());
            }
        }
    }

    /**
     * 构建StreamLoad URL
     */
    private String buildStreamLoadUrl(TableMeta targetTable) {
        String endpoint = this.datasource.getExtraParams().get("fe_endpoint");
        // 从JDBC URL中提取主机和端口
        // jdbc:mysql://host:port/database -> http://host:port/api/database/table/_stream_load
        return endpoint + "/api/" + targetTable.getSchema() + "/" + targetTable.getTable() + "/_stream_load";
    }

    private String basicAuthHeader(String username, String password) {
        final String tobeEncode = username + ":" + password;
        byte[] encoded = Base64.encodeBase64(tobeEncode.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(encoded);
    }

    /**
     * 构建JSON数据
     */
    private String buildJsonData(JSONArray records, String eventType) {
        // 对于DELETE操作，需要特殊处理
//        if ("DELETE".equals(eventType)) {
//            JSONArray deleteRecords = new JSONArray();
//            for (Object record : records) {
//                JSONObject jsonRecord = (JSONObject) record;
//                // DELETE操作只需要包含标识列（通常是主键）
//                JSONObject deleteRecord = new JSONObject();
//                // 这里需要根据实际表结构确定标识列，简化处理使用所有列
//                deleteRecord.putAll(jsonRecord);
//                deleteRecords.add(deleteRecord);
//            }
//            return deleteRecords.toJSONString();
//        }

        return records.toJSONString();
    }

    /**
     * 检查StreamLoad是否成功
     */
    private boolean isStreamLoadSuccess(String responseBody) {
        try {
            JSONObject responseJson = JSONObject.parseObject(responseBody);
            String status = responseJson.getString("Status");
            return "Success".equals(status);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取目标表名（参考JdbcOutput的实现）
     */
    private String getTargetTableName(FlowFile flowFile) {
        if (this.table != null && !this.table.trim().isEmpty()) {
            return this.table;
        }

        Object tableAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE);
        if (tableAttr != null) {
            return tableAttr.toString();
        }

        TableMeta tableMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (tableMeta != null && tableMeta.getTable() != null) {
            return tableMeta.getTable();
        }

        throw new IllegalArgumentException("未配置目标表名，且无法从flowfile属性中获取表名");
    }

    /**
     * 获取目标schema（参考JdbcOutput的实现）
     */
    private String getTargetSchema(FlowFile flowFile) {
        if (this.schema != null && !this.schema.trim().isEmpty()) {
            return this.schema;
        }

        if (this.table != null && !this.table.trim().isEmpty() &&
            this.datasource != null && this.datasource.getDbschema() != null &&
            !this.datasource.getDbschema().trim().isEmpty()) {
            return this.datasource.getDbschema();
        }

        Object databaseAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_DATABASE);
        if (databaseAttr != null) {
            return databaseAttr.toString();
        }

        if (this.datasource != null && this.datasource.getDbschema() != null) {
            return this.datasource.getDbschema();
        }

        TableMeta tableMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (tableMeta != null && tableMeta.getSchema() != null) {
            return tableMeta.getSchema();
        }

        return this.datasource.getDbschema();
    }

    /**
     * 从FlowFile中获取事件类型
     */
    private String getEventType(FlowFile flowFile) {
        Object eventTypeAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE);
        if (eventTypeAttr != null) {
            return eventTypeAttr.toString().toUpperCase();
        }
        return null;
    }

    // 自动注入方法
    public void setTable(String table) {
        this.table = table;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void stop() {
        super.stop();
    }
}