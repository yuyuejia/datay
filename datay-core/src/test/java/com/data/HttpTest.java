package com.data;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;

public class HttpTest {

    private static final String API_URL = "https://ywb.yyuap.com/opsv/batch/operanalysis/getstoragedata/";

    /**
     * 根据租户ID获取租户名
     * @param tenantId 租户ID（如f3u97bv8）
     * @return 租户信息结果
     */
    public static TenantResult getTenantName(String tenantId) {
        // 1. 构建请求体（JSON格式）
        String requestBody = "{\"searchkey\":\"" + tenantId + "\"}";

        // 2. 构建请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Connection", "keep-alive");
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("Cookie", "satoken=0cc76b316dc1403f8a59c46c3103f8db; yht_username_diwork=chenjie2; opst=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NjUwMDY2NDYsInVzZXJfaWQiOiJjaGVuamllMiIsInNlYyI6IlpNRCJ9.8jhWAHWThkhr1K5OQDUeFdGPmxs83FE1JSZzK1RSYqo; Admin-Token=ready");
        headers.put("Origin", "https://ywb.yyuap.com");
        headers.put("Referer", "https://ywb.yyuap.com/p/");
        headers.put("Sec-Fetch-Dest", "empty");
        headers.put("Sec-Fetch-Mode", "cors");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36");
        headers.put("X-Token", "ready");
        headers.put("sec-ch-ua", "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"macOS\"");

        // 3. 发送请求并处理响应
        try (HttpResponse response = HttpRequest.post(API_URL)
                .headerMap(headers, true)
                .body(requestBody)
                .timeout(10000) // 10秒超时
                .execute()) {
            
            if (!response.isOk()) {
                return new TenantResult(tenantId, null, "failed", "HTTP请求失败，状态码：" + response.getStatus());
            }

            // 解析JSON响应
            String responseBody = response.body();
            JSONObject jsonObject = JSONObject.parseObject(responseBody);

            // 提取租户名（根据接口实际返回结构调整字段路径）
            String tenantName = null;
            JSONArray dataArray = jsonObject.getJSONArray("com/data");
            if(dataArray != null && dataArray.size() > 0){
                JSONObject dataObject = dataArray.getJSONObject(0);
                tenantName = dataObject.getString("tenantname");
            } else {
                tenantName = "未知租户";
            }

            return new TenantResult(tenantId, tenantName, "success", null);

        } catch (Exception e) {
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

    @Test
    @Timeout(6000000)
    public void testHttpInput() throws Exception {
        // 批量租户ID测试
        String[] tenantIds = {"sg581d8c","kyl1eteh","m5sh078x","hx5cv72q","dzmyste3","trtofr7u","qyic8c7o","l9v4o15d","ee6y8cai","l1uuoxyd","pe7u2tm7","ii1tjcse","acth07p8","iepnf3xq","kne5nfhj","qi4326e8","hfrkpjlu","f7wafnbg","7139","p3qpkdri","g5oc2v3y","j30ib0to","gny3q5is","oyu4u4z5","dea8kec0","b5u1ptc2","wg4utr7t","uu7wzppv","jsvml2pi","r6qaewfr","f1ddyzv1","ngy7jl4t"};
        for (String id : tenantIds) {
            TenantResult batchResult = getTenantName(id);
            System.out.println(batchResult);
            // 延迟500ms，避免请求过快
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

}