package com.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 租户名获取工具类（基于OkHttp实现）
 */
public class TenantNameFetcher {
    // 接口URL
    private static final String API_URL = "https://ywb.yyuap.com/opsv/batch/operanalysis/getstoragedata/";
    // OkHttp客户端（单例复用）
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)    // 连接超时
            .readTimeout(10, TimeUnit.SECONDS)       // 读取超时
            .writeTimeout(10, TimeUnit.SECONDS)      // 写入超时
            .build();
    // JSON解析器
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据租户ID获取租户名
     * @param tenantId 租户ID（如f3u97bv8）
     * @return 租户信息结果
     */
    public static TenantResult getTenantName(String tenantId) {
        // 1. 构建请求体（JSON格式）
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json;charset=UTF-8"),
                "{\"searchkey\":\"" + tenantId + "\"}"
        );

        // 2. 构建请求头（包含Cookie、User-Agent等）
        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .addHeader("Connection", "keep-alive")
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                // Cookie信息（从curl中提取）
                .addHeader("Cookie", "satoken=61f0f62af524489dbf7cc7eb510b399a; opst=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NjM5NzIwNTcsInVzZXJfaWQiOiJjaGVuamllMiIsInNlYyI6IlpNRCJ9.d1Du5VaroWMfh_g1Fsn0STaD0uOOxi7L3OCxt6RfNOw; Admin-Token=ready")
                .addHeader("Origin", "https://ywb.yyuap.com")
                .addHeader("Referer", "https://ywb.yyuap.com/p/")
                .addHeader("Sec-Fetch-Dest", "empty")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Site", "same-origin")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                .addHeader("X-Token", "ready")
                .addHeader("sec-ch-ua", "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"")
                .addHeader("sec-ch-ua-mobile", "?0")
                .addHeader("sec-ch-ua-platform", "\"macOS\"")
                .build();

        // 3. 发送请求并处理响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new TenantResult(tenantId, null, "failed", "HTTP请求失败，状态码：" + response.code());
            }

            // 解析JSON响应
            String responseBody = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            // 提取租户名（根据接口实际返回结构调整字段路径）
            String tenantName = null;
            if (jsonNode.has("tenantName")) {
                tenantName = jsonNode.get("tenantName").asText();
            } else if (jsonNode.has("com/data") && jsonNode.get("com/data").has("tenantName")) {
                tenantName = jsonNode.get("com/data").get("tenantName").asText();
            } else {
                tenantName = "未知租户（响应结构：" + responseBody + "）";
            }

            return new TenantResult(tenantId, tenantName, "success", null);

        } catch (IOException e) {
            return new TenantResult(tenantId, null, "failed", "请求异常：" + e.getMessage());
        }
    }

    /**
     * 租户信息结果实体类
     */
    public static class TenantResult {
        private String tenantId;
        private String tenantName;
        private String status;
        private String error;

        public TenantResult(String tenantId, String tenantName, String status, String error) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
            this.status = status;
            this.error = error;
        }

        // Getter方法
        public String getTenantId() { return tenantId; }
        public String getTenantName() { return tenantName; }
        public String getStatus() { return status; }
        public String getError() { return error; }

        @Override
        public String toString() {
            return "TenantResult{" +
                    "tenantId='" + tenantId + '\'' +
                    ", tenantName='" + tenantName + '\'' +
                    ", status='" + status + '\'' +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

    // 测试主方法
    public static void main(String[] args) {
        // 单个租户ID测试
        TenantResult result = getTenantName("f3u97bv8");
        System.out.println("租户信息：" + result);
        System.out.println("租户名：" + result.getTenantName());

        // 批量租户ID测试（可选）
        String[] tenantIds = {"vr1rhfar", "qbc7f9cr", "f3u97bv8"};
        for (String id : tenantIds) {
            TenantResult batchResult = getTenantName(id);
            System.out.println("批量处理：" + batchResult);
            // 延迟500ms，避免请求过快
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}