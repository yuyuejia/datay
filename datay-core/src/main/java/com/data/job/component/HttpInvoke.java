package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentRegister;
import com.data.job.DebugLimitReachedException;
import com.data.job.ExceptionUtils;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.JsonTableMetaUtils;
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
@ComponentRegister(
    value = "HttpInvoke",
    name = "HTTP调用",
    group = "数据输入",
    desc = "调用外部 HTTP 接口获取数据，支持 GET/POST、Header、Body 配置及 JSON 响应解析。",
    order = 60
)
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

    // 是否启用分页循环拉取
    private boolean paginationEnabled = false;

    // 分页起始页码
    private int startPage = 1;

    // 每页条数（同时通过 ${pageSize} 注入请求体）
    private int pageSize = 100;

    // 最大分页数，防止接口忽略分页参数导致死循环
    private int maxPages = 1000;

    // 分页请求间隔（毫秒）
    private long requestInterval = 0L;

    // 分页数据路径，为空时回退到 jsonPath
    private String dataPath;

    // 请求体中页码字段路径（静态 JSON 请求体时使用），如 std_data.parameter.page_no
    private String pageNoPath;

    // 请求体中每页条数字段路径（静态 JSON 请求体时使用），如 std_data.parameter.page_size
    private String pageSizePath;

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
            if (paginationEnabled) {
                executePaginated(flowFile);
            } else {
                executeSingle(flowFile);
            }
        } catch (DebugLimitReachedException e) {
            // 调试模式达到采样上限，交由引擎按正常结束处理
            throw e;
        } catch (Exception e) {
            logError("HttpInvoke组件执行失败: " + ExceptionUtils.describe(e));
            throw new RuntimeException("HttpInvoke组件执行失败: " + ExceptionUtils.describe(e), e);
        }
    }

    /**
     * 单次请求模式：调用一次接口并向下游传递响应数据。
     */
    private void executeSingle(FlowFile flowFile) throws IOException {
        Request request = buildHttpRequest(flowFile);
        FlowFile resultFlowFile;
        try (Response response = executeHttpRequest(request)) {
            resultFlowFile = processHttpResponse(response, flowFile);
        }
        writeRecords(resultFlowFile);
    }

    /**
     * 分页循环模式：按页码递增反复调用接口，直到没有下一页或返回空数据。
     *
     * <p>每页响应会作为独立的 FlowFile 向下游发送，避免全量数据一次性堆积在内存中。
     * 请求体支持 {@code ${pageNo}} 与 {@code ${pageSize}} 占位符，可在每次请求时替换为当前页码和每页条数。</p>
     */
    private void executePaginated(FlowFile flowFile) throws IOException {
        int pageNo = startPage > 0 ? startPage : 1;
        int currentPageSize = pageSize > 0 ? pageSize : 100;
        int maxPageCount = maxPages > 0 ? maxPages : 1000;
        int fetchedPages = 0;
        long fetchedRows = 0L;
        // 时间等非分页参数在本次任务开始时统一求值，保证所有分页使用同一时间范围
        String baseBody = ParameterUtil.replaceParameters(this.body, flowFile);

        while (fetchedPages < maxPageCount) {
            if (fetchedPages > 0 && requestInterval > 0) {
                sleepQuietly(requestInterval);
            }

            FlowFile pageFlowFile = new FlowFile();
            pageFlowFile.getAttributeMap().putAll(flowFile.getAttributeMap());
            pageFlowFile.setAttribute("pageNo", pageNo);
            pageFlowFile.setAttribute("pageSize", currentPageSize);
            pageFlowFile.setAttribute("_pageNo", pageNo);
            pageFlowFile.setAttribute("_pageSize", currentPageSize);

            String pageBody = buildPageBody(baseBody, pageNo, currentPageSize);
            Request request = buildHttpRequest(pageFlowFile, pageBody);
            String responseBody;
            try (Response response = executeHttpRequest(request)) {
                responseBody = response.body() != null ? response.body().string() : null;
            }

            FlowFile resultFlowFile = processHttpResponse(responseBody, pageFlowFile);
            JSONArray rows = resultFlowFile.getJsonArray();
            if (rows == null) {
                logWarn("分页模式未从响应中解析到数据数组，请检查 dataPath / jsonPath 配置，已停止分页；响应内容: " + abbreviate(responseBody));
                break;
            }
            if (rows.isEmpty()) {
                logInfo("第 " + pageNo + " 页返回空数据，分页结束");
                break;
            }

            writeRecords(resultFlowFile);
            fetchedPages++;
            fetchedRows += rows.size();
            logInfo("分页拉取第 " + pageNo + " 页，记录数: " + rows.size() + "，累计: " + fetchedRows);

            if (rows.size() < currentPageSize) {
                break;
            }
            pageNo++;
        }

        if (fetchedPages >= maxPageCount) {
            logWarn("已达到分页上限 maxPages=" + maxPageCount + "，停止拉取");
        }
        logInfo("分页拉取完成，共 " + fetchedPages + " 页，" + fetchedRows + " 条记录");
    }

    private String normalizeJsonPath(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("$") ? trimmed : "$." + trimmed;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("分页请求等待被中断", e);
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        String trimmed = text.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "...(已截断)" : trimmed;
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
        return buildHttpRequest(flowFile, this.body);
    }

    /**
     * 构建HTTP请求，允许分页模式下传入当前页的请求体。
     */
    private Request buildHttpRequest(FlowFile flowFile, String bodyTemplate) {
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
            setRequestBody(requestBuilder, flowFile, normalizedMethod, bodyTemplate);
        }

        return requestBuilder.build();
    }

    /**
     * 生成当前页的请求体。
     * <p>优先使用 {@code ${pageNo}} / {@code ${pageSize}} 占位符；也可以通过 pageNoPath / pageSizePath
     * 直接修改 JSON 请求体中的页码和每页条数字段。</p>
     * <p>{@code bodyTemplate} 已经过首次参数求值（例如时间表达式），分页循环中复用该结果，
     * 避免跨页时时间范围发生变化。</p>
     */
    private String buildPageBody(String bodyTemplate, int pageNo, int pageSize) {
        if (bodyTemplate == null || bodyTemplate.trim().isEmpty()) {
            return bodyTemplate;
        }
        if (bodyTemplate.contains("${pageNo}") || bodyTemplate.contains("${pageSize}")) {
            return bodyTemplate;
        }
        if ((pageNoPath == null || pageNoPath.trim().isEmpty()) && (pageSizePath == null || pageSizePath.trim().isEmpty())) {
            return bodyTemplate;
        }

        try {
            JSONObject jsonObject = JSONObject.parseObject(bodyTemplate);
            setJsonPathValue(jsonObject, pageNoPath, pageNo);
            setJsonPathValue(jsonObject, pageSizePath, pageSize);
            return jsonObject.toJSONString();
        } catch (Exception e) {
            logWarn("按字段路径更新分页请求体失败，将使用原始请求体: " + e.getMessage());
            return bodyTemplate;
        }
    }

    /**
     * 按点号路径设置 JSON 字段值，例如 std_data.parameter.page_no。
     */
    private void setJsonPathValue(JSONObject root, String path, Object value) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        }
        String[] segments = normalized.split("\\.");
        JSONObject current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            Object child = current.get(segments[i]);
            if (!(child instanceof JSONObject)) {
                child = new JSONObject();
                current.put(segments[i], child);
            }
            current = (JSONObject) child;
        }
        current.put(segments[segments.length - 1], value);
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
    private void setRequestBody(Request.Builder requestBuilder, FlowFile flowFile, String method, String bodyTemplate) {
        String requestBody = ParameterUtil.replaceParameters(bodyTemplate, flowFile);
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
        String responseBody = response.body() != null ? response.body().string() : null;
        return processHttpResponse(responseBody, originalFlowFile);
    }

    /**
     * 处理HTTP响应体
     */
    private FlowFile processHttpResponse(String responseBody, FlowFile originalFlowFile) {
        FlowFile resultFlowFile = new FlowFile();

        // 复制原始FlowFile的属性
        resultFlowFile.getAttributeMap().putAll(originalFlowFile.getAttributeMap());

        // 添加HTTP响应相关的属性
        resultFlowFile.setAttribute("_httpUrl", url);
        resultFlowFile.setAttribute("_httpMethod", method);

        String effectiveJsonPath = resolveDataPath();
        try {
            // 尝试解析JSON响应
            if (responseBody != null && responseBody.trim().startsWith("{")) {
                JSONObject jsonObject = JSONObject.parseObject(responseBody);
                resultFlowFile.setJsonObject(jsonObject);
                resultFlowFile.setAttribute("_dataFormat", "JSON_OBJECT");

                // 如果配置了JSON路径，提取特定字段
                if (effectiveJsonPath != null && !effectiveJsonPath.trim().isEmpty()) {
                    extractJsonPathData(resultFlowFile, jsonObject, normalizeJsonPath(effectiveJsonPath));
                }

            } else if (responseBody != null && responseBody.trim().startsWith("[")) {
                // 处理JSON数组响应
                JSONArray jsonArray = JSONArray.parseArray(responseBody);
                resultFlowFile.setJsonArray(jsonArray);
                resultFlowFile.setAttribute("_dataFormat", "JSON_ARRAY");

                // 如果配置了JSON路径，提取特定字段
                if (effectiveJsonPath != null && !effectiveJsonPath.trim().isEmpty()) {
                    extractJsonPathData(resultFlowFile, jsonArray, normalizeJsonPath(effectiveJsonPath));
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

    private String resolveDataPath() {
        return dataPath != null && !dataPath.trim().isEmpty() ? dataPath.trim() : jsonPath;
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
                    buildTableMetadata(flowFile, extractedData);
                } else if (extractedData instanceof JSONObject) {
                    flowFile.setJsonObject((JSONObject) extractedData);
                    flowFile.setAttribute("_extractedDataType", "JSON_OBJECT");
                    buildTableMetadata(flowFile, extractedData);
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

    /**
     * 根据提取的JSON数据推断表结构，生成FlowFile的表元数据属性，
     * 供下游StreamJdbcOutput组件自动建表使用。
     */
    private void buildTableMetadata(FlowFile flowFile, Object jsonData) {
        try {
            TableMeta tableMeta = JsonTableMetaUtils.build(jsonData);
            if (tableMeta != null) {
                flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
            }
        } catch (Exception e) {
            logWarn("生成表元数据失败: " + e.getMessage());
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

    public boolean isPaginationEnabled() {
        return paginationEnabled;
    }

    public void setPaginationEnabled(boolean paginationEnabled) {
        this.paginationEnabled = paginationEnabled;
    }

    public void setPaginationEnabled(String paginationEnabled) {
        if (paginationEnabled != null && !paginationEnabled.trim().isEmpty()) {
            this.paginationEnabled = Boolean.parseBoolean(paginationEnabled.trim());
        }
    }

    public int getStartPage() {
        return startPage;
    }

    public void setStartPage(int startPage) {
        this.startPage = startPage;
    }

    public void setStartPage(String startPage) {
        if (startPage != null && !startPage.trim().isEmpty()) {
            try {
                this.startPage = Integer.parseInt(startPage.trim());
            } catch (NumberFormatException e) {
                this.startPage = 1;
            }
        }
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public void setPageSize(String pageSize) {
        if (pageSize != null && !pageSize.trim().isEmpty()) {
            try {
                this.pageSize = Integer.parseInt(pageSize.trim());
            } catch (NumberFormatException e) {
                this.pageSize = 100;
            }
        }
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public void setMaxPages(String maxPages) {
        if (maxPages != null && !maxPages.trim().isEmpty()) {
            try {
                this.maxPages = Integer.parseInt(maxPages.trim());
            } catch (NumberFormatException e) {
                this.maxPages = 1000;
            }
        }
    }

    public long getRequestInterval() {
        return requestInterval;
    }

    public void setRequestInterval(long requestInterval) {
        this.requestInterval = requestInterval;
    }

    public void setRequestInterval(String requestInterval) {
        if (requestInterval != null && !requestInterval.trim().isEmpty()) {
            try {
                this.requestInterval = Long.parseLong(requestInterval.trim());
            } catch (NumberFormatException e) {
                this.requestInterval = 0L;
            }
        }
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getPageNoPath() {
        return pageNoPath;
    }

    public void setPageNoPath(String pageNoPath) {
        this.pageNoPath = pageNoPath;
    }

    public String getPageSizePath() {
        return pageSizePath;
    }

    public void setPageSizePath(String pageSizePath) {
        this.pageSizePath = pageSizePath;
    }
}