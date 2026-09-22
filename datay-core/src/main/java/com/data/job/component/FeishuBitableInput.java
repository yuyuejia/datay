package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.AllDataType;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FeishuBitableInput组件 - 读取飞书多维表格（Bitable）数据
 * <p>
 * 通过飞书开放平台 Open API 读取多维表格记录：
 * 1. 使用 app_id / app_secret 获取 tenant_access_token
 * 2. 分页读取指定数据表的记录
 * 3. 将记录以 JSONArray 形式向下游传递
 * <p>
 * 配置示例：
 * {
 *   "appId": "cli_xxx",
 *   "appSecret": "xxx",
 *   "appToken": "EEmSbfNF8aD2RcsP26CcU3Bvnee",
 *   "tableId": "tblpjHsRMt8moPj8",
 *   "viewId": "vew0XXvjv5",
 *   "incrColumn": "last_modified_time"
 * }
 */
@ComponentRegister(
    value = "FeishuBitableInput",
    name = "飞书多维表格",
    group = "数据输入",
    desc = "通过飞书开放平台 API 读取多维表格记录，分页读取后以 JSON 数组传递给下游。",
    order = 70
)
public class FeishuBitableInput extends FlowComponent {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 应用凭证
    private String appId;
    private String appSecret;

    // 多维表格标识（app_token 即多维表格 URL 中的 base 标识）
    private String appToken;

    // 数据表标识
    private String tableId;

    // 视图标识（可选，用于按视图过滤数据）
    private String viewId;

    // 单页拉取条数，最大 500
    private int pageSize = 100;

    // 开放平台地址，飞书默认 https://open.feishu.cn，国际版可配置 https://open.larksuite.com
    private String baseUrl = "https://open.feishu.cn";

    // 拉取记录时是否同时拉取字段元数据
    private boolean fetchFields = true;

    // record_id 对应的字段名
    private String recordIdField = "record_id";

    // 增量同步字段，取值 last_modified_time（默认）或 created_time；为空表示全量同步
    private String incrColumn;

    // 创建时间对应的字段名
    private String createdTimeField = "created_time";

    // 最后更新时间对应的字段名
    private String modifiedTimeField = "last_modified_time";

    // 目标表名（可选），默认规则为 "feishu" + "_" + tableId
    private String tableName;

    // token 缓存
    private String tenantAccessToken;
    private long tokenExpireTime = 0;

    // HTTP 客户端实例
    private OkHttpClient httpClient;

    public FeishuBitableInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        initHttpClient();

        try {
            if (appId == null || appId.trim().isEmpty() || appSecret == null || appSecret.trim().isEmpty()) {
                throw new IllegalArgumentException("appId 和 appSecret 不能为空");
            }
            if (appToken == null || appToken.trim().isEmpty() || tableId == null || tableId.trim().isEmpty()) {
                throw new IllegalArgumentException("appToken 和 tableId 不能为空");
            }

            // 可选：拉取字段元数据并构建目标表元数据（用于下游自动建表）
            TableMeta tableMeta = null;
            if (fetchFields) {
                List<FieldInfo> fields = fetchFields();
                tableMeta = buildTableMeta(fields);
                logInfo("获取到 " + fields.size() + " 个字段，目标表: " + resolveTableName());
            }

            // 分页拉取全部记录
            List<BitableRecord> allRecords = fetchAllRecords();
            logInfo("读取到 " + allRecords.size() + " 条记录");

            // 增量过滤与状态维护
            List<JSONObject> records = filterIncremental(allRecords, flowFile);
            logInfo("本次需要同步 " + records.size() + " 条记录");

            // 按 pageSize 分批向下游发送
            int batchCount = 0;
            for (int i = 0; i < records.size(); i += pageSize) {
                List<JSONObject> batch = records.subList(i, Math.min(i + pageSize, records.size()));
                sendBatchRecords(batch, tableMeta, flowFile);
                batchCount++;
            }
            // 无数据时发送一个空事件，保证任务正常结束
            if (records.isEmpty()) {
                sendBatchRecords(new ArrayList<>(), tableMeta, flowFile);
            }
            logInfo("数据发送完成，共 " + batchCount + " 批");

        } catch (Exception e) {
            throw new RuntimeException("FeishuBitableInput 组件执行失败", e);
        }
    }

    /**
     * 初始化 HTTP 客户端
     */
    private void initHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
    }

    /**
     * 获取 tenant_access_token，带缓存与过期校验
     */
    public synchronized String getTenantAccessToken() throws IOException {
        initHttpClient();
        long now = System.currentTimeMillis();
        if (tenantAccessToken != null && now < tokenExpireTime - 60_000) {
            return tenantAccessToken;
        }

        JSONObject body = new JSONObject();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);

        Request request = new Request.Builder()
                .url(baseUrl + "/open-apis/auth/v3/tenant_access_token/internal")
                .post(RequestBody.create(body.toJSONString(), JSON))
                .build();

        JSONObject resp = executeJson(request);
        if (resp.getIntValue("code") != 0) {
            throw new IOException("获取 tenant_access_token 失败: " + resp);
        }
        this.tenantAccessToken = resp.getString("tenant_access_token");
        int expire = resp.getIntValue("expire", 7200);
        this.tokenExpireTime = now + expire * 1000L;
        return tenantAccessToken;
    }

    /**
     * 获取数据表的字段元数据（字段名 + 类型）
     */
    public List<FieldInfo> fetchFields() throws IOException {
        String token = getTenantAccessToken();
        List<FieldInfo> fields = new ArrayList<>();

        String pageToken = null;
        do {
            StringBuilder url = new StringBuilder(baseUrl)
                    .append("/open-apis/bitable/v1/apps/").append(appToken)
                    .append("/tables/").append(tableId)
                    .append("/fields?page_size=100");
            if (pageToken != null) {
                url.append("&page_token=").append(pageToken);
            }

            Request request = new Request.Builder()
                    .url(url.toString())
                    .header("Authorization", "Bearer " + token)
                    .get()
                    .build();

            JSONObject resp = executeJson(request);
            if (resp.getIntValue("code") != 0) {
                throw new IOException("获取字段列表失败: " + resp);
            }
            JSONObject data = resp.getJSONObject("data");
            JSONArray items = data.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    fields.add(new FieldInfo(item.getString("field_name"), item.getIntValue("type", 1)));
                }
            }
            pageToken = data.getBooleanValue("has_more") ? data.getString("page_token") : null;
        } while (pageToken != null);

        return fields;
    }

    /**
     * 获取数据表的字段名称列表
     */
    public List<String> fetchFieldNames() throws IOException {
        List<String> names = new ArrayList<>();
        for (FieldInfo field : fetchFields()) {
            names.add(field.getName());
        }
        return names;
    }

    /**
     * 根据飞书字段元数据构建目标表元数据（用于下游自动建表）
     */
    public TableMeta buildTableMeta(List<FieldInfo> fields) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setDbType(DBType.MYSQL.name());
        tableMeta.setTable(resolveTableName());

        ColumnMeta recordIdCol = new ColumnMeta(recordIdField, AllDataType.VARCHAR.getName());
        recordIdCol.setLength(64);
        recordIdCol.setPrimaryKey(true);
        recordIdCol.setNullable(false);
        tableMeta.addColumn(recordIdCol);

        if (fields != null) {
            for (FieldInfo field : fields) {
                tableMeta.addColumn(toColumnMeta(field));
            }
        }
        tableMeta.addColumn(bigintColumn(createdTimeField));
        tableMeta.addColumn(bigintColumn(modifiedTimeField));
        return tableMeta;
    }

    /**
     * 将飞书字段类型映射为通用列类型
     */
    private ColumnMeta toColumnMeta(FieldInfo field) {
        String type;
        int length = 0;
        switch (field.getType()) {
            case 2: // 数字
                type = AllDataType.DOUBLE.getName();
                break;
            case 5: // 日期
            case 1001: // 创建时间
            case 1002: // 最后更新时间
                type = AllDataType.BIGINT.getName();
                break;
            case 7: // 复选框
                type = AllDataType.BOOLEAN.getName();
                break;
            case 4: // 多选
                type = AllDataType.VARCHAR.getName();
                length = 1024;
                break;
            case 22: // 地理位置
                type = AllDataType.VARCHAR.getName();
                length = 512;
                break;
            case 1005: // 自动编号
                type = AllDataType.VARCHAR.getName();
                length = 64;
                break;
            case 1: // 文本
            case 3: // 单选
            case 11: // 人员
            case 13: // 电话号码
            case 15: // 超链接
            case 17: // 附件
            case 18: // 单向关联
            case 21: // 双向关联
            case 23: // 群组
            case 1003: // 创建人
            case 1004: // 修改人
            default:
                type = AllDataType.VARCHAR.getName();
                length = 255;
                break;
        }
        ColumnMeta column = new ColumnMeta(field.getName(), type);
        column.setLength(length);
        column.setPrecision(-1);
        column.setScale(-1);
        return column;
    }

    private ColumnMeta bigintColumn(String name) {
        ColumnMeta column = new ColumnMeta(name, AllDataType.BIGINT.getName());
        column.setPrecision(-1);
        column.setScale(-1);
        return column;
    }

    /**
     * 目标表名，默认规则为 "feishu" + "_" + tableId
     */
    public String resolveTableName() {
        if (tableName != null && !tableName.trim().isEmpty()) {
            return tableName.trim();
        }
        return "feishu_" + tableId;
    }

    /**
     * 分页拉取全部记录（包含 created_time / last_modified_time）
     */
    public List<BitableRecord> fetchAllRecords() throws IOException {
        String token = getTenantAccessToken();
        List<BitableRecord> records = new ArrayList<>();

        String pageToken = null;
        int total = -1;
        do {
            StringBuilder url = new StringBuilder(baseUrl)
                    .append("/open-apis/bitable/v1/apps/").append(appToken)
                    .append("/tables/").append(tableId)
                    .append("/records?page_size=").append(pageSize)
                    .append("&automatic_fields=true");
            if (viewId != null && !viewId.trim().isEmpty()) {
                url.append("&view_id=").append(viewId.trim());
            }
            if (pageToken != null) {
                url.append("&page_token=").append(pageToken);
            }

            Request request = new Request.Builder()
                    .url(url.toString())
                    .header("Authorization", "Bearer " + token)
                    .get()
                    .build();

            JSONObject resp = executeJson(request);
            if (resp.getIntValue("code") != 0) {
                throw new IOException("读取记录失败: " + resp);
            }
            JSONObject data = resp.getJSONObject("data");
            total = data.getIntValue("total", -1);
            JSONArray items = data.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    // 记录扁平化为「字段名 -> 字段值」结构，便于下游直接写入关系型数据库
                    JSONObject record = flattenFields(item.getJSONObject("fields"));
                    record.put(recordIdField, item.getString("record_id"));
                    long createdTime = item.getLongValue("created_time", 0);
                    long lastModifiedTime = item.getLongValue("last_modified_time", 0);
                    record.put(createdTimeField, createdTime);
                    record.put(modifiedTimeField, lastModifiedTime);
                    records.add(new BitableRecord(record, createdTime, lastModifiedTime));
                }
            }
            pageToken = data.getBooleanValue("has_more") ? data.getString("page_token") : null;
        } while (pageToken != null);

        logInfo("记录总数: " + total + "，实际拉取: " + records.size());
        return records;
    }

    /**
     * 解析增量同步字段，为空表示全量同步
     */
    private String resolveIncrField() {
        if (incrColumn == null || incrColumn.trim().isEmpty()) {
            return null;
        }
        String field = incrColumn.trim();
        if ("created_time".equalsIgnoreCase(field)) {
            return "created_time";
        }
        return "last_modified_time";
    }

    /**
     * 根据增量配置过滤记录，并维护增量状态
     * <p>
     * 返回需要向下游发送的记录列表；增量值会被写入 flowFile 的状态，由引擎统一保存
     */
    private List<JSONObject> filterIncremental(List<BitableRecord> allRecords, FlowFile flowFile) {
        String incrField = resolveIncrField();
        if (incrField == null) {
            List<JSONObject> records = new ArrayList<>();
            for (BitableRecord r : allRecords) {
                records.add(r.getData());
            }
            return records;
        }

        String statusKey = "lastIncrValue." + tableId;
        Object lastValueObj = getStatus(statusKey);
        long lastValue = toLong(lastValueObj);
        if (lastValue > 0) {
            logInfo("增量同步，字段: " + incrField + "，上次增量值: " + lastValue);
        } else {
            logInfo("增量同步，字段: " + incrField + "，首次执行，同步全部记录");
        }

        List<JSONObject> records = new ArrayList<>();
        long maxIncrValue = lastValue;
        for (BitableRecord r : allRecords) {
            long incrValue = "created_time".equals(incrField) ? r.getCreatedTime() : r.getLastModifiedTime();
            if (incrValue > maxIncrValue) {
                maxIncrValue = incrValue;
            }
            if (lastValue == 0 || incrValue > lastValue) {
                records.add(r.getData());
            }
        }

        // 保存增量状态
        if (maxIncrValue > 0) {
            flowFile.setStatus(getId() + "." + statusKey, String.valueOf(maxIncrValue));
            logInfo("更新增量状态: " + statusKey + " = " + maxIncrValue);
        }
        return records;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将多维表格的复杂字段值扁平化为简单值
     */
    private JSONObject flattenFields(JSONObject fields) {
        JSONObject result = new JSONObject();
        if (fields == null) {
            return result;
        }
        for (String key : fields.keySet()) {
            result.put(key, flattenValue(fields.get(key)));
        }
        return result;
    }

    /**
     * 扁平化单个字段值，支持文本、单选、多选、日期、人员、附件、链接等常见类型
     */
    public Object flattenValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            if (arr.isEmpty()) {
                return null;
            }
            if (arr.size() == 1) {
                return flattenValue(arr.get(0));
            }
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                Object flattened = flattenValue(arr.get(i));
                if (flattened != null) {
                    parts.add(String.valueOf(flattened));
                }
            }
            return String.join(", ", parts);
        }
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if (obj.containsKey("text")) {
                return obj.get("text");
            }
            if (obj.containsKey("name")) {
                return obj.get("name");
            }
            if (obj.containsKey("en_name")) {
                return obj.get("en_name");
            }
            if (obj.containsKey("link")) {
                return obj.get("link");
            }
            return obj.toJSONString();
        }
        return String.valueOf(value);
    }

    /**
     * 发送一批记录到下游队列
     */
    private void sendBatchRecords(List<JSONObject> batchRecords, TableMeta tableMeta, FlowFile sourceFlowFile) {
        JSONArray jsonRecords = new JSONArray();
        jsonRecords.addAll(batchRecords);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonRecords);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, resolveTableName());
        if (tableMeta != null) {
            flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        }
        // 传递增量状态，由引擎统一保存
        if (sourceFlowFile != null && !sourceFlowFile.getStatusMap().isEmpty()) {
            flowFile.getStatusMap().putAll(sourceFlowFile.getStatusMap());
        }
        writeRecords(flowFile);
    }

    /**
     * 执行 HTTP 请求并解析 JSON 响应
     */
    private JSONObject executeJson(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP 请求失败 " + response.code() + ": " + body);
            }
            return JSONObject.parseObject(body);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    // Getter 和 Setter，用于参数注入

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAppToken() {
        return appToken;
    }

    public void setAppToken(String appToken) {
        this.appToken = appToken;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getViewId() {
        return viewId;
    }

    public void setViewId(String viewId) {
        this.viewId = viewId;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(1, Math.min(500, pageSize));
    }

    public void setPageSize(String pageSize) {
        if (pageSize != null && !pageSize.trim().isEmpty()) {
            try {
                setPageSize(Integer.parseInt(pageSize.trim()));
            } catch (NumberFormatException e) {
                this.pageSize = 100;
            }
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        }
    }

    public boolean isFetchFields() {
        return fetchFields;
    }

    public void setFetchFields(boolean fetchFields) {
        this.fetchFields = fetchFields;
    }

    public void setFetchFields(String fetchFields) {
        if (fetchFields != null && !fetchFields.trim().isEmpty()) {
            this.fetchFields = Boolean.parseBoolean(fetchFields.trim());
        }
    }

    public String getRecordIdField() {
        return recordIdField;
    }

    public void setRecordIdField(String recordIdField) {
        if (recordIdField != null && !recordIdField.trim().isEmpty()) {
            this.recordIdField = recordIdField.trim();
        }
    }

    public String getTenantAccessTokenCached() {
        return tenantAccessToken;
    }

    public String getIncrColumn() {
        return incrColumn;
    }

    public void setIncrColumn(String incrColumn) {
        this.incrColumn = incrColumn;
    }

    public String getCreatedTimeField() {
        return createdTimeField;
    }

    public void setCreatedTimeField(String createdTimeField) {
        if (createdTimeField != null && !createdTimeField.trim().isEmpty()) {
            this.createdTimeField = createdTimeField.trim();
        }
    }

    public String getModifiedTimeField() {
        return modifiedTimeField;
    }

    public void setModifiedTimeField(String modifiedTimeField) {
        if (modifiedTimeField != null && !modifiedTimeField.trim().isEmpty()) {
            this.modifiedTimeField = modifiedTimeField.trim();
        }
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 飞书字段元数据
     */
    public static class FieldInfo {
        private final String name;
        private final int type;

        public FieldInfo(String name, int type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public int getType() {
            return type;
        }
    }

    /**
     * 一条多维表格记录：扁平化后的字段数据 + 系统时间戳
     */
    public static class BitableRecord {
        private final JSONObject data;
        private final long createdTime;
        private final long lastModifiedTime;

        public BitableRecord(JSONObject data, long createdTime, long lastModifiedTime) {
            this.data = data;
            this.createdTime = createdTime;
            this.lastModifiedTime = lastModifiedTime;
        }

        public JSONObject getData() {
            return data;
        }

        public long getCreatedTime() {
            return createdTime;
        }

        public long getLastModifiedTime() {
            return lastModifiedTime;
        }
    }
}
