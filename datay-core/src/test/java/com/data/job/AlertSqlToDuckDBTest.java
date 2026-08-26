package com.data.job;

import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AlertSqlToDuckDBTest {

    // 获取token的URL
    private static final String TOKEN_URL = "https://ymc.yonyoucloud.com/iuap-ymc-server/oauth2/token";
    // 获取告警SQL的URL
    private static final String ALERT_SQL_URL = "https://ymc.yonyoucloud.com/iuap-ymc-server/proxy/server/warning/sql";
    // 客户端ID
    private static final String CLIENT_ID = "cKHjbDtv7cLv5ZuSUdMx2HDu";
    // 客户端密钥
    private static final String CLIENT_SECRET = "gn2yXykXCr1yLHfpoPFFBB5AWcTK35dG7qnqk3Iol5OGntNy";
    // DuckDB数据库文件
    private static final String DUCKDB_FILE = "alert_sql.db";

    // HTTP客户端
    private OkHttpClient httpClient;

    public AlertSqlToDuckDBTest() {
        // 初始化HTTP客户端
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 获取token
     */
    private String getToken() throws IOException {
        // 构建请求体
        RequestBody requestBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build();

        // 构建请求
        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取token失败: " + response.code() + " " + response.message());
            }

            // 解析响应
            String responseBody = response.body().string();
            System.out.println("Response: " + responseBody);
            JSONObject jsonObject = JSONObject.parseObject(responseBody);
            return jsonObject.getString("access_token");
        }
    }

    /**
     * 获取告警SQL
     */
    private JSONObject getAlertSql(String token, int page, int pageSize, String beginTime, String endTime) throws IOException {
        // 构建请求体
        JSONObject requestBodyJson = new JSONObject();
        requestBodyJson.put("page", page);
        requestBodyJson.put("pageSize", pageSize);
        requestBodyJson.put("beginTime", beginTime);
        requestBodyJson.put("endTime", endTime);

        RequestBody requestBody = RequestBody.create(
                requestBodyJson.toJSONString().getBytes(StandardCharsets.UTF_8),
                MediaType.parse("application/json")
        );

        // 构建请求
        Request request = new Request.Builder()
                .url(ALERT_SQL_URL)
                .post(requestBody)
                .header("Authorization", "Bearer " + token)
                .header("x-ymc-project-dccode", "dc-core3")
                .header("x-ymc-project-env", "online-dc-core3")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
                .build();

        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取告警SQL失败: " + response.code() + " " + response.message());
            }

            // 解析响应
            String responseBody = response.body().string();
            return JSONObject.parseObject(responseBody);
        }
    }

    /**
     * 初始化DuckDB表
     */
    private void initDuckDBTable() throws SQLException {
        DuckDBEngine duckDBEngine = DuckDBEngine.getInstance();
        String createTableSql = """
        CREATE TABLE IF NOT EXISTS alert_sql (
            id VARCHAR PRIMARY KEY,
            sysId VARCHAR,
            pspanId VARCHAR,
            thread VARCHAR,
            env VARCHAR,
            userId VARCHAR,
            spanId VARCHAR,
            appId VARCHAR,
            busiAction VARCHAR,
            namespace VARCHAR,
            userParentId VARCHAR,
            appCode VARCHAR,
            podId VARCHAR,
            srcAppCode VARCHAR,
            curAppCode VARCHAR,
            traceId VARCHAR,
            ts VARCHAR,
            createTime VARCHAR,
            sqlStr TEXT,
            md5Sql VARCHAR,
            beginTs VARCHAR,
            costTime VARCHAR,
            rowCount VARCHAR,
            defCol1 VARCHAR,
            ds VARCHAR
        )
        """;
        duckDBEngine.executeUpdate(createTableSql, DUCKDB_FILE);
    }

    /**
     * 保存告警SQL到DuckDB
     */
    private void saveAlertSqlToDuckDB(JSONObject alertData) throws SQLException {
        DuckDBEngine duckDBEngine = DuckDBEngine.getInstance();
        try (Connection conn = duckDBEngine.getConnection(DUCKDB_FILE)) {
            String insertSql = """
            INSERT OR REPLACE INTO alert_sql (
                id, sysId, pspanId, thread, env, userId, spanId, appId, busiAction, namespace,
                userParentId, appCode, podId, srcAppCode, curAppCode, traceId, ts, createTime, sqlStr,
                md5Sql, beginTs, costTime, rowCount, defCol1, ds
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                // 遍历data数组
                for (Object item : alertData.getJSONArray("data")) {
                    JSONObject alertItem = (JSONObject) item;
                    pstmt.setString(1, alertItem.getString("id"));
                    pstmt.setString(2, alertItem.getString("sysId"));
                    pstmt.setString(3, alertItem.getString("pspanId"));
                    pstmt.setString(4, alertItem.getString("thread"));
                    pstmt.setString(5, alertItem.getString("env"));
                    pstmt.setString(6, alertItem.getString("userId"));
                    pstmt.setString(7, alertItem.getString("spanId"));
                    pstmt.setString(8, alertItem.getString("appId"));
                    pstmt.setString(9, alertItem.getString("busiAction"));
                    pstmt.setString(10, alertItem.getString("namespace"));
                    pstmt.setString(11, alertItem.getString("userParentId"));
                    pstmt.setString(12, alertItem.getString("appCode"));
                    pstmt.setString(13, alertItem.getString("podId"));
                    pstmt.setString(14, alertItem.getString("srcAppCode"));
                    pstmt.setString(15, alertItem.getString("curAppCode"));
                    pstmt.setString(16, alertItem.getString("traceId"));
                    pstmt.setString(17, alertItem.getString("ts"));
                    pstmt.setString(18, alertItem.getString("createTime"));
                    pstmt.setString(19, alertItem.getString("sqlStr"));
                    pstmt.setString(20, alertItem.getString("md5Sql"));
                    pstmt.setString(21, alertItem.getString("beginTs"));
                    pstmt.setString(22, alertItem.getString("costTime"));
                    pstmt.setString(23, alertItem.getString("rowCount"));
                    pstmt.setString(24, alertItem.getString("defCol1"));
                    pstmt.setString(25, alertItem.getString("ds"));
                    pstmt.executeUpdate();
                }
            }
        }
    }

    /**
     * 运行测试
     */
    public void runTest() throws Exception {
        System.out.println("开始获取监控系统告警SQL...");

        // 1. 获取token
        System.out.println("获取token成功");

        // 2. 初始化DuckDB表
        System.out.println("2. 初始化DuckDB表...");
        initDuckDBTable();
        System.out.println("初始化DuckDB表成功");

        // 3. 分割时间区间
        System.out.println("3. 分割时间区间...");
        List<TimeRange> timeRanges = splitTimeRange("2026-04-01 00:00:00", "2026-04-01 23:59:59", 4);
        for (int i = 0; i < timeRanges.size(); i++) {
            TimeRange range = timeRanges.get(i);
            System.out.println("时间区间" + (i+1) + ": " + range.getBeginTime() + " - " + range.getEndTime());
        }

        // 4. 创建线程池
        System.out.println("4. 创建线程池...");
        int pageSize = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        List<Future<Integer>> futures = new ArrayList<>();

        // 5. 并行获取数据
        System.out.println("5. 并行获取数据...");
        for (int i = 0; i < timeRanges.size(); i++) {
            final TimeRange range = timeRanges.get(i);
            final int rangeIndex = i + 1;
            
            futures.add(executorService.submit(() -> {
                try {
                    System.out.println("开始处理时间区间" + rangeIndex + ": " + range.getBeginTime() + " - " + range.getEndTime());
                    System.out.println("1. 获取token...");
                    String token = getToken();
                    // 先获取第一页数据，了解总数量
                    int page = 1;
                    JSONObject firstPageData = getAlertSql(token, page, pageSize, range.getBeginTime(), range.getEndTime());
                    int total = firstPageData.getIntValue("total");
                    System.out.println("时间区间" + rangeIndex + "总告警SQL数量: " + total);

                    // 计算总页数
                    int totalPages = (total + pageSize - 1) / pageSize;
                    System.out.println("时间区间" + rangeIndex + "总页数: " + totalPages);

                    // 保存第一页数据
                    saveAlertSqlToDuckDB(firstPageData);
                    System.out.println("时间区间" + rangeIndex + "保存第1页数据成功，数量: " + firstPageData.getJSONArray("data").size());

                    // 循环获取剩余页数的数据
                    for (page = 2; page <= totalPages; page++) {
                        System.out.println("时间区间" + rangeIndex + "获取第" + page + "页数据...");
                        token = getToken();
                        JSONObject pageData = getAlertSql(token, page, pageSize, range.getBeginTime(), range.getEndTime());
                        saveAlertSqlToDuckDB(pageData);
                        System.out.println("时间区间" + rangeIndex + "保存第" + page + "页数据成功，数量: " + pageData.getJSONArray("data").size());
                    }

                    System.out.println("时间区间" + rangeIndex + "处理完成，总数量: " + total);
                    return total;
                } catch (Exception e) {
                    System.err.println("时间区间" + rangeIndex + "处理失败: " + e.getMessage());
                    e.printStackTrace();
                    return 0;
                }
            }));
        }

        // 6. 等待所有任务完成
        System.out.println("6. 等待所有任务完成...");
        int totalCount = 0;
        for (Future<Integer> future : futures) {
            totalCount += future.get();
        }

        // 7. 关闭线程池和HTTP客户端
        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.SECONDS);
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();

        System.out.println("所有数据保存完成，总数量: " + totalCount);
        System.out.println("测试完成");
    }

    /**
     * 时间范围类
     */
    private static class TimeRange {
        private final String beginTime;
        private final String endTime;

        public TimeRange(String beginTime, String endTime) {
            this.beginTime = beginTime;
            this.endTime = endTime;
        }

        public String getBeginTime() {
            return beginTime;
        }

        public String getEndTime() {
            return endTime;
        }
    }

    /**
     * 分割时间范围
     */
    private List<TimeRange> splitTimeRange(String beginTime, String endTime, int splitCount) {
        List<TimeRange> timeRanges = new ArrayList<>();
        
        // 按小时分割
        // 00:00:00 - 05:59:59
        timeRanges.add(new TimeRange("2026-04-01 00:00:00", "2026-04-01 05:59:59"));
        // 06:00:00 - 11:59:59
        timeRanges.add(new TimeRange("2026-04-01 06:00:00", "2026-04-01 11:59:59"));
        // 12:00:00 - 17:59:59
        timeRanges.add(new TimeRange("2026-04-01 12:00:00", "2026-04-01 17:59:59"));
        // 18:00:00 - 23:59:59
        timeRanges.add(new TimeRange("2026-04-01 18:00:00", "2026-04-01 23:59:59"));
        
        return timeRanges;
    }

    public static void main(String[] args) {
        try {
            AlertSqlToDuckDBTest test = new AlertSqlToDuckDBTest();
            test.runTest();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
