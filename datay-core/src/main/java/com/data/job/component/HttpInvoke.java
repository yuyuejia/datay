package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentRegister;
import com.data.job.ExceptionUtils;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import okhttp3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HttpInvoke组件 - 调用外部HTTP接口并获取数据，向下游传递数据
 * 支持GET/POST请求，可配置URL、Header、Body等参数
 * 支持JSON响应数据解析，可提取JSON属性生成FlowFile
 */
@ComponentRegister("HttpInvoke")
public class HttpInvoke extends FlowComponent {

    // HTTP请求URL
    private String url;

    // HTTP请求方法 (GET/POST)
    private String method = "GET";

    // 请求头配置 (JSON格式: {"Content-Type": "application/json", "Authorization": "Bearer token"})
    private HashMap<String, String> headers;

    // 请求体内容 (POST请求时使用)
    private String body;

    // 超时时间 (毫秒)
    private int timeout = 30000;

    // 重试次数
    private int retryCount = 1;

    // 重试间隔 (毫秒)
    private int retryInterval = 1000;

    // JSON路径提取 (用于从JSON响应中提取特定字段)
    private String jsonPath;

    // 是否启用SSL证书验证
    private boolean sslVerify = true;

    // HTTP客户端实例
    private OkHttpClient httpClient;

    public HttpInvoke() {
        setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            return;
        }

        // 初始化HTTP客户端
        initHttpClient();

        try {
            // 处理上游传递的结束信号
            if (flowFile.getAttribute("_end") != null) {
                writeRecords(flowFile);
                return;
            }

            // 构建HTTP请求
            Request request = buildHttpRequest(flowFile);

            // 执行HTTP请求
            Response response = executeHttpRequest(request);

            // 处理HTTP响应
            FlowFile resultFlowFile = processHttpResponse(response, flowFile);

            // 向下游传递数据
            writeRecords(resultFlowFile);

        } catch (Exception e) {
            logError("HttpInvoke组件执行失败: " + ExceptionUtils.describe(e));
            throw new RuntimeException("HttpInvoke组件执行失败: " + ExceptionUtils.describe(e), e);
        }
    }

    /**
     * 初始化HTTP客户端
     */
    private void initHttpClient() {
        if (httpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeout, TimeUnit.MILLISECONDS);

            // 如果不启用SSL验证，添加信任所有证书的配置
            if (!sslVerify) {
                builder.hostnameVerifier((hostname, session) -> true);
                // 注意：生产环境不建议禁用SSL验证
            }

            httpClient = builder.build();
        }
    }

    /**
     * 构建HTTP请求
     */
    private Request buildHttpRequest(FlowFile flowFile) {
        Request.Builder requestBuilder = new Request.Builder();

        // 设置URL（支持从上游FlowFile动态获取参数）
        String finalUrl = ParameterUtil.replaceParameters(this.url, flowFile);
        logInfo("HttpInvoke组件执行请求: " + finalUrl);
        requestBuilder.url(finalUrl);

        // 设置请求头
        setRequestHeaders(requestBuilder, flowFile);

        // 设置请求方法和请求体
        String normalizedMethod = (method == null || method.trim().isEmpty()) ? "GET" : method.trim().toUpperCase();
        if ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod)) {
            requestBuilder.method(normalizedMethod, null);
        } else {
            setRequestBody(requestBuilder, flowFile, normalizedMethod);
        }

        return requestBuilder.build();
    }

    /**
     * 设置请求头
     */
    private void setRequestHeaders(Request.Builder requestBuilder, FlowFile flowFile) {
        // 设置默认的Content-Type
        requestBuilder.header("User-Agent", "DataY-HttpInvoke/1.0");

        // 解析配置的请求头
        if (headers != null && !headers.isEmpty()) {
            try {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String headerValue = String.valueOf(entry.getValue());
                    // 支持从FlowFile动态替换header值
                    headerValue = ParameterUtil.replaceParameters(headerValue, flowFile);
                    requestBuilder.header(entry.getKey(), headerValue);
                }
            } catch (Exception e) {
                logWarn("解析请求头配置失败: " + e.getMessage());
            }
        }
    }

    /**
     * 设置请求体
     */
    private void setRequestBody(Request.Builder requestBuilder, FlowFile flowFile, String method) {
        String requestBody = ParameterUtil.replaceParameters(this.body, flowFile);
        if (requestBody != null && !requestBody.trim().isEmpty()) {
            RequestBody body = RequestBody.create(requestBody.getBytes(StandardCharsets.UTF_8));
            requestBuilder.method(method, body);
        } else {
            requestBuilder.method(method, RequestBody.create(new byte[0], null));
        }
    }

    /**
     * 执行HTTP请求（支持重试机制）
     */
    private Response executeHttpRequest(Request request) throws IOException {
        IOException lastException = null;
        Response lastResponse = null;
        int attempts = Math.max(1, retryCount);

        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful()) {
                    return response;
                }
                // 非成功响应：保留最后一次，之前的结果释放掉
                if (lastResponse != null) {
                    lastResponse.close();
                }
                lastResponse = response;
                lastException = null;
                logWarn(
                    "HttpInvoke请求返回非成功状态码: "
                        + response.code()
                        + "，第 "
                        + (attempt + 1)
                        + "/"
                        + attempts
                        + " 次请求");
            } catch (IOException e) {
                lastException = e;
                if (lastResponse != null) {
                    lastResponse.close();
                    lastResponse = null;
                }
                logError("HttpInvoke组件执行请求失败(第 " + (attempt + 1) + "/" + attempts + " 次): " + e.getMessage());
            }
        }

        if (lastResponse != null) {
            String detail = buildHttpErrorDetail(lastResponse);
            lastResponse.close();
            throw new IOException(detail);
        }
        throw lastException != null ? lastException : new IOException("HTTP请求失败");
    }

    /**
     * 组装HTTP错误响应的详细描述，包含请求方法、URL、状态码、状态信息和响应体片段。
     */
    private String buildHttpErrorDetail(Response response) {
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP请求失败: ").append(method).append(" ").append(url);
        builder.append("，状态码: ").append(response.code());
        if (response.message() != null && !response.message().trim().isEmpty()) {
            builder.append(" ").append(response.message().trim());
        }
        try {
            if (response.body() != null) {
                String body = response.body().string();
                if (body != null && !body.trim().isEmpty()) {
                    body = body.trim();
                    if (body.length() > 500) {
                        body = body.substring(0, 500) + "...(已截断)";
                    }
                    builder.append("，响应内容: ").append(body);
                }
            }
        } catch (IOException e) {
            logWarn("读取HTTP错误响应体失败: " + e.getMessage());
        }
        return builder.toString();
    }

    /**
     * 处理HTTP响应
     */
    private FlowFile processHttpResponse(Response response, FlowFile originalFlowFile) throws IOException {
        FlowFile resultFlowFile = new FlowFile();

        // 复制原始FlowFile的属性
        resultFlowFile.getAttributeMap().putAll(originalFlowFile.getAttributeMap());

        // 添加HTTP响应相关的属性
        resultFlowFile.setAttribute("_httpUrl", url);
        resultFlowFile.setAttribute("_httpMethod", method);

        // 读取响应体
        String responseBody = null;
        if (response.body() != null) {
            responseBody = response.body().string();
        }

        try {
            // 尝试解析JSON响应
            if (responseBody != null && responseBody.trim().startsWith("{")) {
                JSONObject jsonObject = JSONObject.parseObject(responseBody);
                resultFlowFile.setJsonObject(jsonObject);
                resultFlowFile.setAttribute("_dataFormat", "JSON_OBJECT");

                // 如果配置了JSON路径，提取特定字段
                if (jsonPath != null && !jsonPath.trim().isEmpty()) {
                    extractJsonPathData(resultFlowFile, jsonObject, jsonPath);
                }

            } else if (responseBody != null && responseBody.trim().startsWith("[")) {
                // 处理JSON数组响应
                JSONArray jsonArray = JSONArray.parseArray(responseBody);
                resultFlowFile.setJsonArray(jsonArray);
                resultFlowFile.setAttribute("_dataFormat", "JSON_ARRAY");

                // 如果配置了JSON路径，提取特定字段
                if (jsonPath != null && !jsonPath.trim().isEmpty()) {
                    extractJsonPathData(resultFlowFile, jsonArray, jsonPath);
                }

            } else {
                // 非JSON格式，使用文本数据
                resultFlowFile.setTextData(responseBody);
                resultFlowFile.setAttribute("_dataFormat", "TEXT");
            }

        } catch (Exception e) {
            // JSON解析失败，使用原始文本数据
            resultFlowFile.setTextData(responseBody);
            resultFlowFile.setAttribute("_dataFormat", "TEXT");
            resultFlowFile.setAttribute("_jsonParseError", e.getMessage());
            logWarn("JSON解析失败，使用文本格式: " + e.getMessage());
        }

        return resultFlowFile;
    }

    /**
     * 根据JSON路径提取数据（使用fastjson2的JSONPath）
     */
    private void extractJsonPathData(FlowFile flowFile, Object jsonData, String jsonPath) {
        try {
//            logInfo("使用JSONPath提取数据: " + jsonData);
            // 使用fastjson2的JSONPath进行数据提取
            Object extractedData = JSONPath.eval(jsonData, jsonPath);
            
            if (extractedData != null) {
                // 根据提取的数据类型设置FlowFile
                if (extractedData instanceof JSONArray) {
                    flowFile.setJsonArray((JSONArray) extractedData);
                    flowFile.setAttribute("_extractedDataType", "JSON_ARRAY");
                } else if (extractedData instanceof JSONObject) {
                    flowFile.setJsonObject((JSONObject) extractedData);
                    flowFile.setAttribute("_extractedDataType", "JSON_OBJECT");
                } else if (extractedData instanceof String) {
                    flowFile.setTextData((String) extractedData);
                    flowFile.setAttribute("_extractedDataType", "STRING");
                } else if (extractedData instanceof Number) {
                    flowFile.setTextData(String.valueOf(extractedData));
                    flowFile.setAttribute("_extractedDataType", "NUMBER");
                } else if (extractedData instanceof Boolean) {
                    flowFile.setTextData(String.valueOf(extractedData));
                    flowFile.setAttribute("_extractedDataType", "BOOLEAN");
                } else {
                    // 其他类型转换为字符串
                    flowFile.setTextData(String.valueOf(extractedData));
                    flowFile.setAttribute("_extractedDataType", "OTHER");
                }
            } else {
                flowFile.setAttribute("_jsonPathNotFound", jsonPath);
                logWarn("JSONPath未找到数据: " + jsonPath);
            }

        } catch (Exception e) {
            logWarn("JSONPath提取失败: " + e.getMessage());
            flowFile.setAttribute("_jsonPathError", e.getMessage());
        }
    }

    @Override
    public void stop() {
        super.stop();
        // 关闭HTTP客户端
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    // Getter和Setter方法，用于参数注入

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        if (method != null && !method.trim().isEmpty()) {
            this.method = method.toUpperCase();
        }
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(HashMap<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setTimeout(String timeout) {
        if (timeout != null && !timeout.trim().isEmpty()) {
            try {
                this.timeout = Integer.parseInt(timeout.trim());
            } catch (NumberFormatException e) {
                this.timeout = 30000; // 解析失败时使用默认值
            }
        }
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setRetryCount(String retryCount) {
        if (retryCount != null && !retryCount.trim().isEmpty()) {
            try {
                this.retryCount = Integer.parseInt(retryCount.trim());
            } catch (NumberFormatException e) {
                this.retryCount = 3; // 解析失败时使用默认值
            }
        }
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    public void setRetryInterval(String retryInterval) {
        if (retryInterval != null && !retryInterval.trim().isEmpty()) {
            try {
                this.retryInterval = Integer.parseInt(retryInterval.trim());
            } catch (NumberFormatException e) {
                this.retryInterval = 1000; // 解析失败时使用默认值
            }
        }
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    public boolean isSslVerify() {
        return sslVerify;
    }

    public void setSslVerify(boolean sslVerify) {
        this.sslVerify = sslVerify;
    }

    public void setSslVerify(String sslVerify) {
        if (sslVerify != null) {
            this.sslVerify = Boolean.parseBoolean(sslVerify.trim());
        }
    }
}