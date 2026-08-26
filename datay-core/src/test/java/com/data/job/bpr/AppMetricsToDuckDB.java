package com.data.job.bpr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.duckdb.DuckDBDriver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AppMetricsToDuckDB {

    // HTTP 客户端
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // DuckDB 配置（文件持久化）
    private static final String DUCKDB_URL = "jdbc:duckdb:thread_metrics.duckdb";

    // 接口地址
    private static final String API_URL = "https://bip-new52.yyuap.com/iuap-ymc-server/proxy/server/yms/history/ymsMetrics";

    // 请求头（你给的 curl 里的 header）
    private static final String COOKIE = "a10=MDY4Nzg1MjIxMTAzOTQ3NTI5NTUz; yht_default_country=86; _yht_code_uuid=e2da2a32-9a19-4b61-9ed4-e96dd2c5665e; yht_default_login_type=normal; CASPRIVACY=\"\"; at=ce968bbb-cca9-4627-87b4-fb32f39e93b6; com.yonyou.yht.web.utils.CookieLocaleResolver.LOCALE=zh-CN; yht_username_diwork=ST-66292105-oVG6cjCEvCmKoM00QyZu-online__9e0eeca5-5433-4d73-bba1-ccffd657007a; yht_usertoken_diwork=49Tb91uTcxFq7LH3r%2B1NqSuFxCxmlTSoM%2Ftt6RkeShEM%2Boiek3dU1t2VMjt5U8CvWfT3YlB4OA7Vw3bTLARTdw%3D%3D; YMC-SESSION-ID=YzY1ZWE0MDUtOGRmOS00YTQ4LWIyZjItOGU3MjUzOGQ3ZmY3; JSESSIONID=0000fdJQfja-KxcP-5vB-MO0iEDWYvNnvQ67jZtXUsUEObRRLhKF8qxYNbvrDBDLibBg:ed2491b4-4dfe-4a7a-b4cd-1810f1725124; locale=zh_CN; timezone=UTC+08:00; userid=2496399249110466564; username=v_log; XSRF-TOKEN=AX_MTI1K8NU8C0YZ0BT43SMHYHUJ!091536";
    private static final String X_XSRF_TOKEN = "AX_DOEG5YC4E6GDWFBAA7Q642LND!112039";

    // 你提供的所有 appCode 列表
    private static final List<Map<String, String>> APP_LIST = List.of(
            Map.of("appCode", "yonbip-scmmp-group", "appId", "test-yonbip-scmmp-group"),
            Map.of("appCode", "iuap-yyc-yontest", "appId", "test-iuap-yyc-yontest"),
            Map.of("appCode", "yonbip-ec-group", "appId", "test-yonbip-ec-group"),
            Map.of("appCode", "yonbip-fi-group", "appId", "test-yonbip-fi-group"),
            Map.of("appCode", "yonbip-expssc-group", "appId", "test-yonbip-expssc-group"),
            Map.of("appCode", "yonbip-loc-group", "appId", "test-yonbip-loc-group"),
            Map.of("appCode", "yonbip-hy-tstsgbd", "appId", "test-yonbip-hy-tstsgbd"),
            Map.of("appCode", "iuap-ymc-agent", "appId", "test-iuap-ymc-agent"),
            Map.of("appCode", "iuap-dpaas-group", "appId", "test-iuap-dpaas-group"),
            Map.of("appCode", "yonbip-isp-group", "appId", "test-yonbip-isp-group"),
            Map.of("appCode", "yonbip-epm-group", "appId", "test-yonbip-epm-group"),
            Map.of("appCode", "yonbip-gbu-group", "appId", "test-yonbip-gbu-group"),
            Map.of("appCode", "iuap-aipaas-group", "appId", "test-iuap-aipaas-group"),
            Map.of("appCode", "yonbip-scm-group", "appId", "test-yonbip-scm-group"),
            Map.of("appCode", "iuap-ptc-group", "appId", "test-iuap-ptc-group"),
            Map.of("appCode", "iuap-bpaas-group", "appId", "test-iuap-bpaas-group"),
            Map.of("appCode", "yonbip-taxpm-group", "appId", "test-yonbip-taxpm-group"),
            Map.of("appCode", "iuap-yht-group", "appId", "test-iuap-yht-group"),
            Map.of("appCode", "iuap-ipaas-group", "appId", "test-iuap-ipaas-group"),
            Map.of("appCode", "iuap-apaas-group", "appId", "test-iuap-apaas-group"),
            Map.of("appCode", "yonbip-hr-group", "appId", "test-yonbip-hr-group"),
            Map.of("appCode", "yonbip-hrenh-group", "appId", "test-yonbip-hrenh-group"),
            Map.of("appCode", "yonbip-ctm-group", "appId", "test-yonbip-ctm-group"),
            Map.of("appCode", "iuap-event-group", "appId", "test-iuap-event-group")
    );

    // 初始化 DuckDB 表
    private static void initTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS thread_pool_metrics (
                appCode VARCHAR,
                threadPoolName VARCHAR,
                queueUsageRate DOUBLE,
                queueTaskNums BIGINT,
                activeThreadNums BIGINT,
                completeTaskNums BIGINT,
                coreThreadNums BIGINT,
                maxThreadNums BIGINT,
                currentThreadNums BIGINT,
                collectTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """;
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // 请求单个 app 的指标
    private static JsonNode fetchMetrics(String appId, String appCode) throws Exception {
        long end = LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8));
        long start = end - 3600;

        String body = MAPPER.writeValueAsString(Map.of(
                "appId", appId,
                "pipelineCode", "",
                "appCode", appCode,
                "queryType", "threadTable",
                "start", start,
                "end", end
        ));

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json")
                .header("Cookie", COOKIE)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
                .header("X-Ymc-Project-DcCode", "datacenter-1")
                .header("X-Ymc-Project-Env", "online")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("请求失败 appCode=" + appCode + " code=" + response.code());
                return null;
            }
            return MAPPER.readTree(response.body().bytes());
        }
    }

    // 插入 DuckDB
    private static void insert(Connection conn, String appCode, JsonNode data) throws SQLException {
        String insertSql = """
            INSERT INTO thread_pool_metrics (
                appCode, threadPoolName, queueUsageRate, queueTaskNums,
                activeThreadNums, completeTaskNums, coreThreadNums,
                maxThreadNums, currentThreadNums
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (JsonNode node : data) {
                JsonNode attr = node.get("componentAttribute");
                if (attr == null) continue;

                pstmt.setString(1, appCode);
                pstmt.setString(2, attr.get("threadPoolName").asText());
                pstmt.setDouble(3, attr.get("queueUsageRate").asDouble());
                pstmt.setLong(4, attr.get("queueTaskNums").asLong());
                pstmt.setLong(5, attr.get("activeThreadNums").asLong());
                pstmt.setLong(6, attr.get("completeTaskNums").asLong());
                pstmt.setLong(7, attr.get("coreThreadNums").asLong());
                pstmt.setLong(8, attr.get("maxThreadNums").asLong());
                pstmt.setLong(9, attr.get("currentThreadNums").asLong());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    // 循环采集所有应用
    public static void batchCollect() {
        try {
            Class.forName(DuckDBDriver.class.getName());
            try (Connection conn = DriverManager.getConnection(DUCKDB_URL)) {
                initTable(conn);

                for (Map<String, String> app : APP_LIST) {
                    String appCode = app.get("appCode");
                    String appId = app.get("appId");
                    System.out.println("采集：" + appCode);

                    JsonNode resp = fetchMetrics(appId, appCode);
                    if (resp == null || !"200".equals(resp.get("code").asText())) {
                        continue;
                    }

                    JsonNode data = resp.get("data");
                    if (data != null && data.isArray() && !data.isEmpty()) {
                        insert(conn, appCode, data);
                        System.out.println(appCode + " 插入成功：" + data.size() + "条");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 主函数：循环执行
    public static void main(String[] args) {
            System.out.println("===== 开始全量采集 " + LocalDateTime.now() + " =====");
            batchCollect();
    }
}