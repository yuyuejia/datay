package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * FeishuBitableOutput组件 - 将数据写入飞书多维表格（Bitable）
 * <p>
 * 通过飞书开放平台 Open API 向多维表格写入记录：
 * 1. 使用 app_id / app_secret 获取 tenant_access_token
 * 2. 接收上游 FlowFile 中的 JSONArray 记录
 * 3. 根据记录是否包含 record_id 决定新增（batch_create）或更新（batch_update）
 * <p>
 * 配置示例：
 * {
 *   "appId": "cli_xxx",
 *   "appSecret": "xxx",
 *   "appToken": "EEmSbfNF8aD2RcsP26CcU3Bvnee",
 *   "tableId": "tblpjHsRMt8moPj8",
 *   "batchSize": "500"
 * }
 */
@ComponentRegister("FeishuBitableOutput")
public class FeishuBitableOutput extends FlowComponent {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 应用凭证
    private String appId;
    private String appSecret;

    // 多维表格标识（app_token）
    private String appToken;

    // 数据表标识
    private String tableId;

    // 开放平台地址，飞书默认 https://open.feishu.cn
    private String baseUrl = "https://open.feishu.cn";

    // 单批写入条数，最大 500
    private int batchSize = 500;

    // record_id 对应的字段名，用于判断新增还是更新
    private String recordIdField = "record_id";

    // 需要排除、不写入多维表格的字段（逗号分隔）
    private String excludeFields;

    // token 缓存
    private String tenantAccessToken;
    private long tokenExpireTime = 0;

    // HTTP 客户端实例
    private OkHttpClient httpClient;

    public FeishuBitableOutput() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 调试模式：跳过实际写库
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入飞书多维表格");
            return;
        }
        if (flowFile.getData() == null) {
            return;
        }
        JSONArray records = flowFile.getJsonArray();
        if (records == null || records.isEmpty()) {
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

            String token = getTenantAccessToken();

            // 拆分新增/更新记录
            List<JSONObject> createRecords = new ArrayList<>();
            List<JSONObject> updateRecords = new ArrayList<>();
            for (Object o : records) {
                JSONObject record = (JSONObject) o;
                JSONObject fields = buildFields(record);
                if (fields.isEmpty()) {
                    continue;
                }
                String recordId = record.getString(recordIdField);
                if (recordId != null && !recordId.trim().isEmpty()) {
                    JSONObject update = new JSONObject();
                    update.put("record_id", recordId);
                    update.put("fields", fields);
                    updateRecords.add(update);
                } else {
                    JSONObject create = new JSONObject();
                    create.put("fields", fields);
                    createRecords.add(create);
                }
            }

            if (!createRecords.isEmpty()) {
                batchCreate(token, createRecords);
            }
            if (!updateRecords.isEmpty()) {
                batchUpdate(token, updateRecords);
            }
            logInfo("写入完成，新增 " + createRecords.size() + " 条，更新 " + updateRecords.size() + " 条");

        } catch (Exception e) {
            throw new RuntimeException("FeishuBitableOutput 组件执行失败", e);
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
     * 从记录中构建需要写入多维表格的字段（排除 record_id 及配置的排除字段）
     */
    public JSONObject buildFields(JSONObject record) {
        Set<String> excludes = new HashSet<>();
        if (recordIdField != null && !recordIdField.trim().isEmpty()) {
            excludes.add(recordIdField.trim());
        }
        if (excludeFields != null) {
            for (String s : excludeFields.split(",")) {
                if (s != null && !s.trim().isEmpty()) {
                    excludes.add(s.trim());
                }
            }
        }
        JSONObject fields = new JSONObject();
        for (String key : record.keySet()) {
            if (excludes.contains(key)) {
                continue;
            }
            fields.put(key, record.get(key));
        }
        return fields;
    }

    /**
     * 批量新增记录
     */
    public void batchCreate(String token, List<JSONObject> records) throws IOException {
        for (int i = 0; i < records.size(); i += batchSize) {
            List<JSONObject> batch = records.subList(i, Math.min(i + batchSize, records.size()));
            JSONObject body = new JSONObject();
            JSONArray arr = new JSONArray();
            arr.addAll(batch);
            body.put("records", arr);

            Request request = new Request.Builder()
                    .url(baseUrl + "/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records/batch_create")
                    .header("Authorization", "Bearer " + token)
                    .post(RequestBody.create(body.toJSONString(), JSON))
                    .build();

            JSONObject resp = executeJson(request);
            if (resp.getIntValue("code") != 0) {
                throw new IOException("批量新增记录失败: " + resp);
            }
            logInfo("批量新增 " + batch.size() + " 条记录成功");
        }
    }

    /**
     * 批量更新记录
     */
    public void batchUpdate(String token, List<JSONObject> records) throws IOException {
        for (int i = 0; i < records.size(); i += batchSize) {
            List<JSONObject> batch = records.subList(i, Math.min(i + batchSize, records.size()));
            JSONObject body = new JSONObject();
            JSONArray arr = new JSONArray();
            arr.addAll(batch);
            body.put("records", arr);

            Request request = new Request.Builder()
                    .url(baseUrl + "/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records/batch_update")
                    .header("Authorization", "Bearer " + token)
                    .post(RequestBody.create(body.toJSONString(), JSON))
                    .build();

            JSONObject resp = executeJson(request);
            if (resp.getIntValue("code") != 0) {
                throw new IOException("批量更新记录失败: " + resp);
            }
            logInfo("批量更新 " + batch.size() + " 条记录成功");
        }
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        }
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, Math.min(500, batchSize));
    }

    public void setBatchSize(String batchSize) {
        if (batchSize != null && !batchSize.trim().isEmpty()) {
            try {
                setBatchSize(Integer.parseInt(batchSize.trim()));
            } catch (NumberFormatException e) {
                this.batchSize = 500;
            }
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

    public String getExcludeFields() {
        return excludeFields;
    }

    public void setExcludeFields(String excludeFields) {
        this.excludeFields = excludeFields;
    }

    public String getTenantAccessTokenCached() {
        return tenantAccessToken;
    }
}
