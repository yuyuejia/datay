package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 目标表列计划缓存：列名、源字段名、类型归一化策略在首批数据时计算一次，
     * 避免每行每列重复做大小写转换和类型判断。
     */
    private final Map<String, TablePlan> tablePlanMap = new ConcurrentHashMap<>();

    /**
     * 复用同一个 HttpClient 和连接池。原实现每批都 new 一个客户端，500 批会产生
     * 500 次连接建立/销毁。
     */
    private volatile CloseableHttpClient httpClient;

    /**
     * 单批 JSON Lines 的复用缓冲区。
     * <p>Doris FE 的 307 重定向 + chunked 请求在高负载下偶发卡死，这里改为一次性
     * 序列化到可复用 byte[]，用 Content-Length 发送；缓冲区按线程复用，不再每批
     * 分配 15–20 MB 的 String/byte[]。首次扩容后每批零大对象分配。</p>
     */
    private static final ThreadLocal<ReusableBuffer> BATCH_BUFFER =
        ThreadLocal.withInitial(() -> new ReusableBuffer(256 * 1024));

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
     * 执行 Stream Load 数据写入。
     *
     * <p>请求体按 JSON Lines 流式生成：每行一个 JSON 对象，边序列化边发送，
     * 不再把整批 10,000 行拼成一个 15–20 MB 的 String/byte[]。这样既避免了
     * humongous allocation，也让 Doris 可以按行解析。</p>
     */
    private void streamLoadData(TableMeta targetTable, FlowFile flowFile, String eventType) throws IOException {
        JSONArray records = flowFile.getJsonArray();
        TablePlan plan = getTablePlan(targetTable, flowFile);

        HttpPut put = new HttpPut(plan.streamLoadUrl);
        put.setHeader(HttpHeaders.EXPECT, "100-continue");
        put.setHeader(HttpHeaders.AUTHORIZATION, plan.authorization);
        put.setHeader("format", "json");
        put.setHeader("read_json_by_line", "true");
        // 不能开启 fuzzy_parse：Doris 会按 JSON key 的位置而不是字段名解析，
        // 而 LEFT JOIN 产生的 NULL 列在 JSON 中会被省略，导致后续字段整体左移。
        // 不允许 Doris 静默过滤行：一旦有行被过滤，说明数据质量或目标表结构有问题，
        // 必须让批次失败，而不是把少写的数据当成写入成功。
        put.setHeader("max_filter_ratio", "0");
        if ("DELETE".equals(eventType)) {
            put.setHeader("merge_type", "DELETE");
        }
        ReusableBuffer body = buildBatchBody(records, plan);
        put.setEntity(new ByteArrayEntity(
            body.array(),
            0,
            body.size(),
            ContentType.APPLICATION_JSON
        ));

        try (CloseableHttpResponse response = getHttpClient().execute(put)) {
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

    /**
     * 把一批记录序列化为 JSON Lines。缓冲按线程复用，避免每批分配大对象。
     */
    private ReusableBuffer buildBatchBody(JSONArray records, TablePlan plan) throws IOException {
        ReusableBuffer buffer = BATCH_BUFFER.get();
        buffer.reset();
        // UTF-8 writer 直接产出 byte[]，避免 UTF-16 char[] 和逐行 flush 时的转码/复制。
        JSONWriter writer = JSONWriter.ofUTF8();
        try {
            for (int i = 0; i < records.size(); i++) {
                writeRecord(writer, buffer, records.get(i), plan);
            }
        } finally {
            writer.close();
        }
        return buffer;
    }

    private void writeRecord(JSONWriter writer, ReusableBuffer buffer, Object item, TablePlan plan) throws IOException {
        if (plan.passthrough) {
            writer.writeAny(item);
            writer.writeRaw('\n');
            writer.flushTo(buffer);
            return;
        }
        if (!(item instanceof JSONObject)) {
            writer.writeAny(item);
            writer.writeRaw('\n');
            writer.flushTo(buffer);
            return;
        }
        JSONObject record = (JSONObject) item;
        writer.startObject();
        for (ColumnPlan column : plan.columns) {
            Object value = record.get(column.sourceKey);
            if (value == null) {
                continue;
            }
            writer.writeName(column.targetName);
            writer.writeColon();
            ValueKind.write(writer, column.kind, column.rawType, value);
        }
        writer.endObject();
        writer.writeRaw('\n');
        writer.flushTo(buffer);
    }

    private CloseableHttpClient getHttpClient() {
        CloseableHttpClient client = this.httpClient;
        if (client == null) {
            synchronized (this) {
                client = this.httpClient;
                if (client == null) {
                    client = HttpClients
                        .custom()
                        .setRedirectStrategy(new DefaultRedirectStrategy() {
                            @Override
                            protected boolean isRedirectable(String method) {
                                return true;
                            }
                        })
                        // 避免 Doris/网络异常时永久挂起；正常单批远小于 5 分钟。
                        .setDefaultRequestConfig(
                            RequestConfig.custom()
                                .setConnectTimeout(30_000)
                                .setConnectionRequestTimeout(30_000)
                                .setSocketTimeout(300_000)
                                .build()
                        )
                        // 无 label 的 Stream Load 重试可能重复写入，失败交给任务重跑。
                        .disableAutomaticRetries()
                        .build();
                    this.httpClient = client;
                }
            }
        }
        return client;
    }

    /**
     * 获取或构建目标表列计划。计划按目标表缓存，后续批次只做取值和序列化。
     */
    private TablePlan getTablePlan(TableMeta targetTable, FlowFile flowFile) {
        String tableKey = targetTable.getSchema() + "." + targetTable.getTable();
        TablePlan plan = this.tablePlanMap.get(tableKey);
        if (plan != null) {
            return plan;
        }
        synchronized (this.tablePlanMap) {
            plan = this.tablePlanMap.get(tableKey);
            if (plan == null) {
                plan = buildTablePlan(targetTable, flowFile);
                this.tablePlanMap.put(tableKey, plan);
            }
        }
        return plan;
    }

    private TablePlan buildTablePlan(TableMeta targetTable, FlowFile flowFile) {
        String streamLoadUrl = buildStreamLoadUrl(targetTable);
        String authorization = basicAuthHeader(this.datasource.getUsername(), this.datasource.getPassword());
        if (targetTable == null || targetTable.columns() == null || targetTable.columns().isEmpty()) {
            // 目标表元数据为空时保持旧行为：原样序列化源记录。
            return new TablePlan(true, Collections.emptyList(), streamLoadUrl, authorization);
        }

        JSONArray records = flowFile.getJsonArray();
        Map<String, String> sourceKeys = new HashMap<>();
        // 优先使用首批记录的实际 key（转换/重命名后最准确），再用源表元数据兜底。
        if (!records.isEmpty() && records.get(0) instanceof JSONObject) {
            for (String key : ((JSONObject) records.get(0)).keySet()) {
                sourceKeys.putIfAbsent(key.toLowerCase(Locale.ROOT), key);
            }
        }
        TableMeta sourceTable = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (sourceTable != null && sourceTable.columns() != null) {
            for (ColumnMeta column : sourceTable.columns()) {
                if (column.getName() != null) {
                    sourceKeys.putIfAbsent(column.getName().toLowerCase(Locale.ROOT), column.getName());
                }
            }
        }

        List<ColumnPlan> columns = new ArrayList<>(targetTable.columns().size());
        for (ColumnMeta targetColumn : targetTable.columns()) {
            String targetName = targetColumn.getName();
            if (targetName == null) {
                continue;
            }
            String sourceKey = sourceKeys.get(targetName.toLowerCase(Locale.ROOT));
            if (sourceKey == null) {
                continue;
            }
            columns.add(new ColumnPlan(
                targetName,
                sourceKey,
                ValueKind.of(targetColumn.getType()),
                targetColumn.getType()
            ));
        }
        return new TablePlan(false, columns, streamLoadUrl, authorization);
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

    private enum ValueKind {
        TEXT,
        INT,
        DECIMAL,
        DATETIME,
        DATE,
        TIME,
        BOOLEAN,
        OTHER;

        static ValueKind of(String fieldType) {
            if (DBUtils.isTextType(fieldType)) {
                return TEXT;
            }
            String type = fieldType == null ? "" : fieldType.trim().toUpperCase(Locale.ROOT);
            int parameterStart = type.indexOf('(');
            if (parameterStart > 0) {
                type = type.substring(0, parameterStart).trim();
            }
            if ("BOOLEAN".equals(type) || "BOOL".equals(type)) {
                return BOOLEAN;
            }
            if ("DATE".equals(type)) {
                return DATE;
            }
            if ("TIME".equals(type)) {
                return TIME;
            }
            if ("DATETIME".equals(type) || type.endsWith("TIMESTAMP")) {
                return DATETIME;
            }
            if (
                "DECIMAL".equals(type) ||
                type.startsWith("DECIMAL") ||
                "NUMERIC".equals(type) ||
                type.startsWith("NUMERIC") ||
                "NUMBER".equals(type)
            ) {
                return DECIMAL;
            }
            if (type.contains("INT")) {
                return INT;
            }
            return OTHER;
        }

        static void write(JSONWriter writer, ValueKind kind, String rawType, Object value) {
            if (value == null) {
                writer.writeNull();
                return;
            }
            switch (kind) {
                case TEXT:
                    if (value instanceof String) {
                        writer.writeString((String) value);
                        return;
                    }
                    if (value instanceof byte[]) {
                        writer.writeString((byte[]) value);
                        return;
                    }
                    if (value instanceof Number || value instanceof Boolean) {
                        writer.writeAny(value);
                        return;
                    }
                    writer.writeString(value.toString());
                    return;
                case INT:
                    if (value instanceof Boolean) {
                        writer.writeInt32((Boolean) value ? 1 : 0);
                        return;
                    }
                    if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
                        writer.writeInt32(((Number) value).intValue());
                        return;
                    }
                    if (value instanceof Long) {
                        writer.writeInt64((Long) value);
                        return;
                    }
                    break;
                case DECIMAL:
                    if (value instanceof Boolean) {
                        writer.writeInt32((Boolean) value ? 1 : 0);
                        return;
                    }
                    if (value instanceof BigDecimal) {
                        writer.writeDecimal((BigDecimal) value);
                        return;
                    }
                    if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
                        writer.writeInt32(((Number) value).intValue());
                        return;
                    }
                    if (value instanceof Long) {
                        writer.writeInt64((Long) value);
                        return;
                    }
                    break;
                case DATETIME:
                    if (value instanceof Timestamp) {
                        writer.writeAny(toLocalDateTimeFast((Timestamp) value));
                        return;
                    }
                    if (value instanceof LocalDateTime) {
                        writer.writeAny(value);
                        return;
                    }
                    if (value instanceof java.util.Date) {
                        writer.writeAny(LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZoneOffset.UTC));
                        return;
                    }
                    break;
                case DATE:
                    if (value instanceof LocalDate) {
                        writer.writeAny(value);
                        return;
                    }
                    if (value instanceof java.sql.Date) {
                        writer.writeAny(((java.sql.Date) value).toLocalDate());
                        return;
                    }
                    if (value instanceof java.util.Date) {
                        writer.writeAny(
                            LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZoneOffset.UTC).toLocalDate()
                        );
                        return;
                    }
                    break;
                case BOOLEAN:
                    if (value instanceof Boolean) {
                        writer.writeBool((Boolean) value);
                        return;
                    }
                    break;
                default:
                    break;
            }
            // 复杂/少见类型回退到原有归一化逻辑，保证行为兼容。
            writer.writeAny(DBUtils.normalizeParameter(rawType, value));
        }
    }

    /**
     * Timestamp 的快速本地时间转换。原实现 Timestamp.toLocalDateTime() 会走
     * java.util.Date.getMonth() -> Calendar -> ZoneInfo，在宽表逐字段转换时占比较高。
     */
    private static LocalDateTime toLocalDateTimeFast(Timestamp value) {
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static final class ColumnPlan {

        private final String targetName;
        private final String sourceKey;
        private final ValueKind kind;
        private final String rawType;

        private ColumnPlan(String targetName, String sourceKey, ValueKind kind, String rawType) {
            this.targetName = targetName;
            this.sourceKey = sourceKey;
            this.kind = kind;
            this.rawType = rawType;
        }
    }

    private static final class TablePlan {

        private final boolean passthrough;
        private final List<ColumnPlan> columns;
        private final String streamLoadUrl;
        private final String authorization;

        private TablePlan(boolean passthrough, List<ColumnPlan> columns, String streamLoadUrl, String authorization) {
            this.passthrough = passthrough;
            this.columns = columns;
            this.streamLoadUrl = streamLoadUrl;
            this.authorization = authorization;
        }
    }

    private static final class ReusableBuffer extends ByteArrayOutputStream {

        private ReusableBuffer(int size) {
            super(size);
        }

        private byte[] array() {
            return this.buf;
        }
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

    @Override
    public void stop() {
        super.stop();
        CloseableHttpClient client = this.httpClient;
        if (client != null) {
            this.httpClient = null;
            try {
                client.close();
            } catch (IOException e) {
                logDebug("关闭 Doris HttpClient 失败: " + e.getMessage());
            }
        }
    }
}