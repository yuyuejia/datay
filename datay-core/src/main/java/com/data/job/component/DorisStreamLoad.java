package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldOption;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
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
    @ComponentField(
        label = "Schema",
        type = ComponentFieldType.SCHEMA,
        dependsOn = "sourceId",
        group = "基础配置",
        order = 20,
        help = "Doris 数据库 Schema 名称"
    )
    private String schema;

    @ComponentField(
        label = "目标表名",
        type = ComponentFieldType.TABLE,
        required = true,
        dependsOn = "sourceId",
        dataSourceField = "sourceId",
        schemaField = "schema",
        group = "基础配置",
        order = 30,
        placeholder = "请输入 Doris 表名"
    )
    private String table;

    @ComponentField(
        label = "写入模式",
        type = ComponentFieldType.SELECT,
        defaultValue = "INSERT",
        group = "基础配置",
        order = 40,
        options = { @ComponentFieldOption(value = "INSERT", label = "INSERT"), @ComponentFieldOption(value = "DELETE", label = "DELETE") },
        help = "Doris StreamLoad 支持的写入模式"
    )
    private String model; // 写入模式：insert, update, delete

    @ComponentField(
        key = "sourceId",
        label = "Doris 数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        group = "基础配置",
        order = 10,
        schemaField = "schema",
        help = "选择 Doris 数据库数据源"
    )
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
                    // 建表后回读真实元数据：Doris 会把字段名规范化为小写，如果继续使用
                    // 源表转换结果（可能为大写）构造 Stream Load JSON，Doris 按字段名匹配时
                    // 会把所有行过滤掉并报 [DATA_QUALITY_ERROR]too many filtered rows。
                    targetTable = refreshCreatedTableMeta(targetConn, schema, table, targetTable);
                } else {
                    logInfo("表 " + schema + "." + table + " 已存在");
                    targetTable = DBUtils.getTableMetaData(this.datasource, targetConn, schema, table);
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
     * 自动建表后回读目标表实际元数据，确保字段名与 Doris 实际列名一致（Doris 会规范化为小写）。
     * <p>读取失败时沿用建表前按源表转换出的元数据。</p>
     */
    private TableMeta refreshCreatedTableMeta(Connection targetConn, String schema, String table, TableMeta fallback) {
        try {
            TableMeta actual = DBUtils.getTableMetaData(this.datasource, targetConn, schema, table);
            if (actual != null && actual.columns() != null && !actual.columns().isEmpty()) {
                return actual;
            }
        } catch (SQLException e) {
            logDebug("读取新建表元数据失败，沿用源表转换结果：" + e.getMessage());
        }
        return fallback;
    }

    /**
     * 执行StreamLoad数据写入
     */
    private void streamLoadData(TableMeta targetTable, FlowFile flowFile, String eventType) throws IOException {
        JSONArray records = flowFile.getJsonArray();
        // 构建StreamLoad URL
        String streamLoadUrl = buildStreamLoadUrl(targetTable);

        // 构建请求体（JSON格式）
        String jsonData = buildJsonData(targetTable, records, eventType);

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
            // 不能开启 fuzzy_parse：Doris 会按 JSON key 的位置而不是字段名解析，
            // 而 LEFT JOIN 产生的 NULL 列在 JSON 中会被省略（fastjson 默认不输出 null），
            // 导致后续字段整体左移，轻则写错列、重则触发数据质量错误整批失败。
            // 按字段名解析虽然稍慢，但能保证列值正确。
            // 不允许 Doris 静默过滤行：一旦有行被过滤，说明数据质量或目标表结构有问题，
            // 必须让批次失败，而不是把少写的数据当成写入成功。
            put.setHeader("max_filter_ratio", "0");
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
                // statusCode 200 只代表 FE/BE 服务可用，Doris 的 Stream Load 失败（如 data quality、
                // publish timeout、label 冲突）同样会返回 200，真正的结果在响应体的 Status 字段里。
                // 这里必须校验响应体，否则失败批次会被当成成功，表现为“少写了数据但任务成功”。
                if (statusCode != 200) {
                    throw new IOException(
                            String.format("Stream load failed, statusCode=%s load result=%s", statusCode, loadResult));
                }
                validateStreamLoadResult(loadResult, records.size(), targetTable);
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
     * 构建JSON数据。
     * <p>按目标表列类型做一次规范化：MySQL Binlog 的字符列是 {@code byte[]}，日期时间列按 UTC
     * 承载墙上时间，直接序列化会导致乱码或时区偏移；二进制列不在目标表中时也会被丢弃。</p>
     */
    private String buildJsonData(TableMeta targetTable, JSONArray records, String eventType) {
        if (targetTable == null || targetTable.columns() == null || targetTable.columns().isEmpty()) {
            return records.toJSONString();
        }

        JSONArray normalized = new JSONArray(records.size());
        for (Object item : records) {
            if (!(item instanceof JSONObject)) {
                normalized.add(item);
                continue;
            }
            JSONObject record = (JSONObject) item;
            JSONObject row = new JSONObject();
            for (ColumnMeta column : targetTable.columns()) {
                String name = column.getName();
                // 源端字段可能为大写（如 SQL Server），Doris 自动建表后字段为小写。
                // 这里按字段名忽略大小写匹配，并统一用目标表实际列名作为 JSON key，
                // 否则 Doris 按字段名解析时会过滤掉整行。
                String sourceKey = findKeyIgnoreCase(record, name);
                if (sourceKey != null) {
                    row.put(name, DBUtils.normalizeParameter(column.getType(), record.get(sourceKey)));
                }
            }
            normalized.add(row);
        }
        return normalized.toJSONString();
    }

    /**
     * 按字段名忽略大小写查找记录中实际存在的 key。
     */
    private String findKeyIgnoreCase(JSONObject record, String columnName) {
        if (record == null || columnName == null) {
            return null;
        }
        if (record.containsKey(columnName)) {
            return columnName;
        }
        for (String key : record.keySet()) {
            if (key.equalsIgnoreCase(columnName)) {
                return key;
            }
        }
        return null;
    }

    /**
     * 校验 Stream Load 响应体。
     *
     * <p>Doris 在 HTTP 200 下也可能返回 {@code Status=Fail}（例如数据质量不满足、导入超时、
     * label 冲突等），这些批次实际上没有写入。旧逻辑只看 HTTP 状态码，会把失败当成成功，
     * 造成“日志显示写入成功、实际数据缺失”。这里解析响应体并记录真实行数。</p>
     *
     * @param responseBody 响应体
     * @param recordCount  本批提交的记录数
     * @param targetTable  目标表
     */
    void validateStreamLoadResult(String responseBody, int recordCount, TableMeta targetTable) throws IOException {
        String table = targetTable == null ? "" : targetTable.getSchema() + "." + targetTable.getTable();
        JSONObject responseJson;
        try {
            responseJson = JSONObject.parseObject(responseBody);
        } catch (Exception e) {
            throw new IOException(
                String.format("StreamLoad 响应无法解析, 目标表=%s, 本批记录数=%d, 响应体=%s", table, recordCount, responseBody),
                e
            );
        }
        if (responseJson == null) {
            throw new IOException(String.format("StreamLoad 响应为空, 目标表=%s, 本批记录数=%d", table, recordCount));
        }

        String status = responseJson.getString("Status");
        if (!"Success".equalsIgnoreCase(status)) {
            throw new IOException(
                String.format(
                    "StreamLoad 写入失败, 目标表=%s, 本批记录数=%d, Status=%s, Message=%s, ErrorURL=%s",
                    table,
                    recordCount,
                    status,
                    responseJson.getString("Message"),
                    responseJson.getString("ErrorURL")
                )
            );
        }

        long totalRows = responseJson.getLongValue("NumberTotalRows");
        long loadedRows = responseJson.getLongValue("NumberLoadedRows");
        long filteredRows = responseJson.getLongValue("NumberFilteredRows");
        long unselectedRows = responseJson.getLongValue("NumberUnselectedRows");
        if (filteredRows > 0 || (totalRows > 0 && loadedRows < totalRows)) {
            logWarn(
                String.format(
                    "StreamLoad 存在未写入的行, 目标表=%s, 本批记录数=%d, NumberTotalRows=%d, NumberLoadedRows=%d, " +
                    "NumberFilteredRows=%d, NumberUnselectedRows=%d, ErrorURL=%s",
                    table,
                    recordCount,
                    totalRows,
                    loadedRows,
                    filteredRows,
                    unselectedRows,
                    responseJson.getString("ErrorURL")
                )
            );
        }
        this.logInfo(
            String.format(
                "已成功写入 %d 条记录到表 %s（NumberLoadedRows=%d, NumberFilteredRows=%d）",
                recordCount,
                targetTable == null ? "" : targetTable.getTable(),
                loadedRows,
                filteredRows
            )
        );
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