package com.data.job.pm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * PM系统认证工具类
 * 用于调用PM系统的认证接口获取授权码
 */
public class PMClient {

    private static final String URL = "https://pm-api.yyrd.com";
    private static final String AUTH_URL = URL + "/rest/api/auth/code";
    private static final String ACCESS_TOKEN_URL = URL + "/rest/api/auth/accessToken";
    private static final String CREATE_DEFECT_URL = URL + "/tm/oauth/rest/v1/bip/api/base?lineId=3058614d-5e02-45b3-8084-33d4c6e6a49b";
    private static final String GET_PRODUCT_LINES_URL = URL + "/rest/v1/conf/api/bip/oauth/meta/productLines";
    private static final String GET_MICRO_SERVICE_URL = URL + "/pm/oauth/rest/v1/bip/api/base/getMicroService";
    private static final int DEFAULT_TIMEOUT = 30000; // 30秒超时
    
    private OkHttpClient httpClient;
    
    public PMClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
    }
    
    public PMClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * 获取PM系统认证码
     * 
     * @param clientId 客户端标识
     * @param clientSecret 加密后的clientSecret
     * @return 认证码字符串
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public String getAuthCode(String clientId, String clientSecret) throws IOException, RuntimeException {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("clientId", clientId);
        requestBody.put("clientSecret", clientSecret);
        
        String jsonBody = requestBody.toJSONString();
        
        // 构建请求
        Request request = new Request.Builder()
                .url(AUTH_URL)
                .post(RequestBody.create(jsonBody.getBytes(StandardCharsets.UTF_8)))
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json")
                .build();
        
        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP请求失败: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject result = JSONObject.parseObject(responseBody);
            
            // 检查响应状态
            if (!result.getBooleanValue("success")) {
                throw new RuntimeException("认证失败: " + result.getString("msg"));
            }
            
            // 返回认证码
            return result.getString("data");
        }
    }
    
    /**
     * 获取PM系统访问令牌
     * 
     * @param clientId 客户端标识
     * @param clientSecret 加密后的clientSecret
     * @param authorizationCode 授权码
     * @return 访问令牌字符串
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public String getAccessToken(String clientId, String clientSecret, String authorizationCode) throws IOException, RuntimeException {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("clientId", clientId);
        requestBody.put("clientSecret", clientSecret);
        requestBody.put("authorizationCode", authorizationCode);
        requestBody.put("scopes", new String[]{""});
        requestBody.put("grantType", "");
        
        JSONObject requestParameters = new JSONObject();
        requestParameters.put("userName", clientId);
        requestBody.put("requestParameters", requestParameters);
        
        String jsonBody = requestBody.toJSONString();
        
        // 构建请求
        Request request = new Request.Builder()
                .url(ACCESS_TOKEN_URL)
                .post(RequestBody.create(jsonBody.getBytes(StandardCharsets.UTF_8)))
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json")
                .header("User-Agent", "PostmanRuntime-ApipostRuntime/1.1.0")
                .build();
        
        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP请求失败: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject result = JSONObject.parseObject(responseBody);
            
            // 检查响应状态
            if (!result.getBooleanValue("success")) {
                throw new RuntimeException("获取访问令牌失败: " + result.getString("msg"));
            }
            
            // 返回访问令牌
            return result.getString("data");
        }
    }

    
    /**
     * 完整的认证流程：先获取授权码，再获取访问令牌
     * 
     * @param clientId 客户端标识
     * @param clientSecret 加密后的clientSecret
     * @return 访问令牌字符串
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public String getAccessTokenWithFullAuth(String clientId, String clientSecret) throws IOException, RuntimeException {
        // 先获取授权码
        String authCode = getAuthCode(clientId, clientSecret);
        
        // 再使用授权码获取访问令牌
        return getAccessToken(clientId, clientSecret, authCode);
    }

    
    /**
     * 创建技术缺陷
     * 
     * @param accessToken 访问令牌
     * @param defectData 缺陷数据对象
     * @return 缺陷创建结果，包含缺陷编码和ID
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public DefectCreateResult createDefect(String accessToken, DefectData defectData) throws IOException, RuntimeException {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("severityLevel", defectData.getSeverityLevel());
        requestBody.put("influenceVersion", defectData.getInfluenceVersion());
        requestBody.put("lineId", defectData.getLineId());
        requestBody.put("reporter", defectData.getReporter());
        requestBody.put("microService", defectData.getMicroService());
        requestBody.put("defectType", defectData.getDefectType());
        requestBody.put("priority", defectData.getPriority());
        requestBody.put("title", defectData.getTitle());
        requestBody.put("desc", defectData.getDesc());
        
        // 设置属性列表
        if (defectData.getProperties() != null && !defectData.getProperties().isEmpty()) {
            JSONArray propertiesArray = new JSONArray();
            for (DefectProperty property : defectData.getProperties()) {
                JSONObject prop = new JSONObject();
                prop.put("code", property.getCode());
                prop.put("value", property.getValue());
                propertiesArray.add(prop);
            }
            requestBody.put("properties", propertiesArray);
        }
        
        String jsonBody = requestBody.toJSONString();
        
        // 构建请求
        Request request = new Request.Builder()
                .url(CREATE_DEFECT_URL)
                .post(RequestBody.create(jsonBody.getBytes(StandardCharsets.UTF_8)))
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        
        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP请求失败: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            System.out.println("响应数据: " + responseBody);
            JSONObject result = JSONObject.parseObject(responseBody);
            
            // 检查响应状态
            if (result.getIntValue("code") != 200) {
                throw new RuntimeException("创建缺陷失败: " + result.getString("msg"));
            }
            
            // 解析返回数据
            JSONObject data = result.getJSONObject("data");
            return new DefectCreateResult(
                data.getString("code"),
                data.getString("aid")
            );
        }
    }
    
    /**
     * 完整的缺陷创建流程：先认证，再创建缺陷
     * 
     * @param clientId 客户端标识
     * @param clientSecret 加密后的clientSecret
     * @param defectData 缺陷数据对象
     * @return 缺陷创建结果
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public DefectCreateResult createDefectWithFullAuth(String clientId, String clientSecret, 
                                                      DefectData defectData) throws IOException, RuntimeException {
        // 先获取访问令牌
        String accessToken = getAccessTokenWithFullAuth(clientId, clientSecret);
        
        // 再创建缺陷
        return createDefect(accessToken, defectData);
    }
    
    /**
     * 缺陷数据类
     */
    public static class DefectData {
        private String severityLevel;
        private int influenceVersion;
        private String lineId;
        private String reporter;
        private String assignee;
        private String microService;
        private String defectType;
        private String priority;
        private String title;
        private String desc;
        private java.util.List<DefectProperty> properties;
        
        // 构造函数
        public DefectData() {}
        
        public DefectData(String severityLevel, int influenceVersion, String lineId, String reporter,
                         String microService, String defectType, String priority, String title, String desc) {
            this.severityLevel = severityLevel;
            this.influenceVersion = influenceVersion;
            this.lineId = lineId;
            this.reporter = reporter;
            this.microService = microService;
            this.defectType = defectType;
            this.priority = priority;
            this.title = title;
            this.desc = desc;
        }
        
        // Getter和Setter方法
        public String getSeverityLevel() { return severityLevel; }
        public void setSeverityLevel(String severityLevel) { this.severityLevel = severityLevel; }
        
        public int getInfluenceVersion() { return influenceVersion; }
        public void setInfluenceVersion(int influenceVersion) { this.influenceVersion = influenceVersion; }
        
        public String getLineId() { return lineId; }
        public void setLineId(String lineId) { this.lineId = lineId; }
        
        public String getReporter() { return reporter; }
        public void setReporter(String reporter) { this.reporter = reporter; }
        
        public String getMicroService() { return microService; }
        public void setMicroService(String microService) { this.microService = microService; }
        
        public String getDefectType() { return defectType; }
        public void setDefectType(String defectType) { this.defectType = defectType; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDesc() { return desc; }
        public void setDesc(String desc) { this.desc = desc; }
        
        public java.util.List<DefectProperty> getProperties() { return properties; }
        public void setProperties(java.util.List<DefectProperty> properties) { this.properties = properties; }

        public String getAssignee() {
            return assignee;
        }

        public void setAssignee(String assignee) {
            this.assignee = assignee;
        }
    }
    
    /**
     * 缺陷属性类
     */
    public static class DefectProperty {
        private String code;
        private String value;
        
        public DefectProperty() {}
        
        public DefectProperty(String code, String value) {
            this.code = code;
            this.value = value;
        }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
    
    /**
     * 获取产品线信息
     * 
     * @param accessToken 访问令牌
     * @return 产品线列表
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public java.util.List<ProductLine> getProductLines(String accessToken) throws IOException, RuntimeException {
        // 构建请求
        Request request = new Request.Builder()
                .url(GET_PRODUCT_LINES_URL)
                .get()
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("User-Agent", "PostmanRuntime-ApipostRuntime/1.1.0")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        
        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP请求失败: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject result = JSONObject.parseObject(responseBody);
            
            // 检查响应状态
            if (!result.getBooleanValue("success")) {
                throw new RuntimeException("获取产品线失败: " + result.getString("msg"));
            }
            
            // 解析产品线数据
            String dataStr = result.getString("data");
            System.out.println(dataStr);
            JSONArray productLinesArray = JSONArray.parseArray(dataStr);
            
            java.util.List<ProductLine> productLines = new java.util.ArrayList<>();
            for (int i = 0; i < productLinesArray.size(); i++) {
                JSONObject productLineObj = productLinesArray.getJSONObject(i);
                ProductLine productLine = new ProductLine(
                    productLineObj.getString("aid"),
                    productLineObj.getString("code"),
                    productLineObj.getString("title")
                );
                productLines.add(productLine);
            }
            
            return productLines;
        }
    }
    
    /**
     * 完整的产品线获取流程：先认证，再获取产品线
     * 
     * @param clientId 客户端标识
     * @param clientSecret 加密后的clientSecret
     * @return 产品线列表
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public java.util.List<ProductLine> getProductLinesWithFullAuth(String clientId, String clientSecret) 
            throws IOException, RuntimeException {
        // 先获取访问令牌
        String accessToken = getAccessTokenWithFullAuth(clientId, clientSecret);
        
        // 再获取产品线
        return getProductLines(accessToken);
    }
    
    /**
     * 获取微服务列表
     * 
     * @param accessToken 访问令牌
     * @param pageNumber 页码
     * @param pageSize 每页大小
     * @return 微服务列表响应对象
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public MicroServiceResponse getMicroServices(String accessToken, int pageNumber, int pageSize) throws IOException, RuntimeException {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("pageNumber", pageNumber);
        requestBody.put("pageSize", pageSize);
        
        String jsonBody = requestBody.toJSONString();
        
        // 构建请求
        Request request = new Request.Builder()
                .url(GET_MICRO_SERVICE_URL+"?pageNumber="+pageNumber+"&pageSize="+pageSize)
                .post(RequestBody.create(jsonBody.getBytes(StandardCharsets.UTF_8)))
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json")
                .header("User-Agent", "PostmanRuntime-ApipostRuntime/1.1.0")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        
        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP请求失败: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject result = JSONObject.parseObject(responseBody);
            
            // 检查响应状态
            if (result.getIntValue("code") != 200) {
                throw new RuntimeException("获取微服务失败: " + result.getString("msg"));
            }
            
            // 解析返回数据
            JSONObject data = result.getJSONObject("data");
            JSONArray microServicesArray = data.getJSONArray("records");
            java.util.List<MicroService> microServices = new java.util.ArrayList<>();
            for (int i = 0; i < microServicesArray.size(); i++) {
                JSONObject microServiceObj = microServicesArray.getJSONObject(i);
                MicroService microService = new MicroService(
                    microServiceObj.getString("id"),
                    microServiceObj.getString("code"),
                    microServiceObj.getString("name"),
                    microServiceObj.getString("description")
                );
                microServices.add(microService);
            }
            
            return new MicroServiceResponse(
                microServices,
                data.getIntValue("pageNumber"),
                data.getIntValue("pageSize"),
                data.getIntValue("total")
            );
        }
    }
    
    /**
     * 完整的微服务获取流程：先认证，再获取微服务
     * 
     * @param clientId 客户端标识
     * @param clientSecret 加密后的clientSecret
     * @param pageNumber 页码
     * @param pageSize 每页大小
     * @return 微服务列表响应对象
     * @throws IOException 网络请求异常
     * @throws RuntimeException 业务逻辑异常
     */
    public MicroServiceResponse getMicroServicesWithFullAuth(String clientId, String clientSecret, 
                                                           int pageNumber, int pageSize) throws IOException, RuntimeException {
        // 先获取访问令牌
        String accessToken = getAccessTokenWithFullAuth(clientId, clientSecret);
        
        // 再获取微服务
        return getMicroServices(accessToken, pageNumber, pageSize);
    }
    
    /**
     * 产品线信息类
     */
    public static class ProductLine {
        private String aid;
        private String code;
        private String title;
        
        public ProductLine() {}
        
        public ProductLine(String aid, String code, String title) {
            this.aid = aid;
            this.code = code;
            this.title = title;
        }
        
        public String getAid() { return aid; }
        public void setAid(String aid) { this.aid = aid; }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        @Override
        public String toString() {
            return "产品线[编码: " + code + ", 名称: " + title + ", ID: " + aid + "]";
        }
    }

    
    /**
     * 微服务响应类
     */
    public static class MicroServiceResponse {
        private java.util.List<MicroService> list;
        private int pageNumber;
        private int pageSize;
        private int total;
        
        public MicroServiceResponse() {}
        
        public MicroServiceResponse(java.util.List<MicroService> list, int pageNumber, int pageSize, int total) {
            this.list = list;
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
            this.total = total;
        }
        
        public java.util.List<MicroService> getList() { return list; }
        public void setList(java.util.List<MicroService> list) { this.list = list; }
        
        public int getPageNumber() { return pageNumber; }
        public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
        
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        
        @Override
        public String toString() {
            return "微服务响应[页码: " + pageNumber + ", 每页大小: " + pageSize + ", 总数: " + total + ", 列表大小: " + list.size() + "]";
        }
    }
    
    /**
     * 缺陷创建结果类
     */
    public static class DefectCreateResult {
        private String defectCode;
        private String defectId;
        
        public DefectCreateResult(String defectCode, String defectId) {
            this.defectCode = defectCode;
            this.defectId = defectId;
        }
        
        public String getDefectCode() { return defectCode; }
        public String getDefectId() { return defectId; }
        
        @Override
        public String toString() {
            return "缺陷编码: " + defectCode + ", 缺陷ID: " + defectId;
        }
    }
    
    /**
     * 关闭HTTP客户端（如果使用了自定义客户端）
     */
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
}