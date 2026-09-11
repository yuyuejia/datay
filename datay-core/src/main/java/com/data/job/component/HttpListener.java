package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.concurrent.Executors;

/**
 * HttpListener组件 - 启动HTTP服务器监听HTTP请求，将请求体生成FlowFile传递到下游
 * 该组件作为数据流的源头，接收HTTP请求并转换为数据流
 */
@ComponentRegister("HttpListener")
public class HttpListener extends FlowComponent {

    // 监听端口号
    private int port;

    // 监听地址
    private String host = "0.0.0.0";

    // 请求路径
    private String path = "/";

    // HTTP服务器实例
    private HttpServer httpServer;

    // 是否启用HTTPS
    private boolean enableHttps = false;

    // 最大请求体大小（字节）
    private int maxBodySize = 10485760; // 10MB

    // 数据格式类型（自动检测或指定）
    private String dataFormat = "AUTO";

    public HttpListener() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 调试模式：不启动 HTTP 服务，根据组件能力生成一条模拟请求数据后结束
        if (getContext() != null && getContext().isDebugMode()) {
            executeDebugMock();
            return;
        }

        logInfo("HttpListener组件开始执行，监听地址: " + host + ":" + port + path);

        try {
            // 启动HTTP服务器
            startHttpServer();
            
            // 等待服务器运行
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            logError("HTTP服务器启动失败: " + e.getMessage());
            throw new RuntimeException("HTTP服务器启动失败", e);
        } finally {
            // 停止HTTP服务器
            stopHttpServer();
        }
    }

    /**
     * 调试模式：生成一条模拟 HTTP 请求 FlowFile 并结束。
     */
    private void executeDebugMock() {
        JSONObject row = new JSONObject();
        row.put("id", 1);
        row.put("name", "debug");
        row.put("event_time", new Timestamp(System.currentTimeMillis()));

        JSONArray jsonArray = new JSONArray();
        jsonArray.add(row);

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonArray);
        flowFile.setAttribute("_source", "HttpListener");
        flowFile.setAttribute("_method", "POST");
        flowFile.setAttribute("_path", path);
        flowFile.setAttribute("_timestamp", System.currentTimeMillis());
        flowFile.setAttribute("_clientAddress", "127.0.0.1");
        flowFile.setAttribute("_contentType", "application/json");
        flowFile.setAttribute("_dataFormat", "JSON_ARRAY");
        flowFile.setAttribute("_debugMock", true);
        writeRecords(flowFile);
        logInfo("调试模式：根据组件能力生成模拟 HTTP 请求数据，path: " + path);
    }

    /**
     * 启动HTTP服务器
     */
    private void startHttpServer() throws IOException {
        InetSocketAddress address = new InetSocketAddress(host, getPort());
        httpServer = HttpServer.create(address, 0);
        
        // 设置请求处理器
        httpServer.createContext(path, new HttpRequestHandler());
        
        // 设置线程池
        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        
        // 启动服务器
        httpServer.start();
        
        logInfo("HTTP服务器已启动，监听地址: " + host + ":" + port + path);
    }

    /**
     * 停止HTTP服务器
     */
    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
            logInfo("HTTP服务器已停止");
        }
    }

    /**
     * HTTP请求处理器
     */
    private class HttpRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // 记录请求信息
                String method = exchange.getRequestMethod();
                String clientAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
                
                logInfo("收到HTTP请求: " + method + " " + exchange.getRequestURI() + " from " + clientAddress);

                // 处理不同的HTTP方法
                if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    handleRequestBody(exchange);
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleGetRequest(exchange);
                } else {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                    return;
                }

                // 发送成功响应
                sendSuccessResponse(exchange);
                
            } catch (Exception e) {
                logError("处理HTTP请求时发生错误: " + e.getMessage());
                sendErrorResponse(exchange, 500, "Internal Server Error");
            }
        }

        /**
         * 处理请求体数据
         */
        private void handleRequestBody(HttpExchange exchange) throws IOException {
            // 读取请求体
            InputStream requestBody = exchange.getRequestBody();
            byte[] bodyBytes = requestBody.readAllBytes();
            
            // 检查请求体大小
            if (bodyBytes.length > maxBodySize) {
                throw new IOException("请求体大小超过限制: " + bodyBytes.length + " > " + maxBodySize);
            }

            // 将请求体转换为字符串
            String bodyContent = new String(bodyBytes, StandardCharsets.UTF_8);
            
            // 生成FlowFile
            FlowFile flowFile = createFlowFileFromRequest(exchange, bodyContent);
            
            // 传递到下游
            writeRecords(flowFile);
            
            logInfo("成功处理HTTP请求体，数据大小: " + bodyBytes.length + " bytes");
        }

        /**
         * 处理GET请求
         */
        private void handleGetRequest(HttpExchange exchange) {
            // 对于GET请求，可以处理查询参数或生成默认数据
            String query = exchange.getRequestURI().getQuery();
            
            FlowFile flowFile = new FlowFile();
            flowFile.setAttribute("_source", "HttpListener");
            flowFile.setAttribute("_method", "GET");
            flowFile.setAttribute("_path", exchange.getRequestURI().getPath());
            flowFile.setAttribute("_query", query != null ? query : "");
            flowFile.setAttribute("_timestamp", System.currentTimeMillis());
            flowFile.setAttribute("_clientAddress", exchange.getRemoteAddress().getAddress().getHostAddress());
            
            // 如果有查询参数，可以将其作为数据
            if (query != null && !query.trim().isEmpty()) {
                flowFile.setTextData("GET Request: " + query);
            } else {
                flowFile.setTextData("GET Request received");
            }
            
            writeRecords(flowFile);
            logInfo("处理GET请求成功");
        }

        /**
         * 从HTTP请求创建FlowFile
         */
        private FlowFile createFlowFileFromRequest(HttpExchange exchange, String bodyContent) {
            FlowFile flowFile = new FlowFile();
            
            // 设置通用属性
            flowFile.setAttribute("_source", "HttpListener");
            flowFile.setAttribute(FlowFile.ATTRIBUTE_ERROR_IGNORE, "true");
            flowFile.setAttribute("_method", exchange.getRequestMethod());
            flowFile.setAttribute("_path", exchange.getRequestURI().getPath());
            flowFile.setAttribute("_timestamp", System.currentTimeMillis());
            flowFile.setAttribute("_clientAddress", exchange.getRemoteAddress().getAddress().getHostAddress());
            flowFile.setAttribute("_contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
            flowFile.setAttribute("_contentLength", bodyContent.length());
            
            // 根据数据格式处理请求体
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String detectedFormat = detectDataFormat(contentType, bodyContent);
            
            try {
                switch (detectedFormat.toUpperCase()) {
                    case "JSON_ARRAY":
                        JSONArray jsonArray = JSONArray.parseArray(bodyContent);
                        flowFile.setJsonArray(jsonArray);
                        flowFile.setAttribute("_dataFormat", "JSON_ARRAY");
                        logInfo("检测到JSON数组格式，元素数量: " + jsonArray.size());
                        break;
                    case "JSON_OBJECT":
                        JSONObject jsonObject = JSONObject.parseObject(bodyContent);
                        flowFile.setJsonObject(jsonObject);
                        flowFile.setAttribute("_dataFormat", "JSON_OBJECT");
                        logInfo("检测到JSON对象格式");
                        break;
                    case "CSV":
                        flowFile.setCsvData(bodyContent);
                        flowFile.setAttribute("_dataFormat", "CSV");
                        logInfo("检测到CSV格式");
                        break;
                    case "TEXT":
                    default:
                        flowFile.setTextData(bodyContent);
                        flowFile.setAttribute("_dataFormat", "TEXT");
                        logInfo("检测到文本格式，长度: " + bodyContent.length() + " characters");
                        break;
                }
            } catch (Exception e) {
                // 如果格式检测失败，默认使用文本格式
                flowFile.setTextData(bodyContent);
                flowFile.setAttribute("_dataFormat", "TEXT");
                flowFile.setAttribute("_formatError", e.getMessage());
                logWarn("数据格式检测失败，使用文本格式: " + e.getMessage());
            }
            
            return flowFile;
        }

        /**
         * 检测数据格式
         */
        private String detectDataFormat(String contentType, String content) {
            // 如果指定了数据格式，使用指定的格式
            if (!"AUTO".equalsIgnoreCase(dataFormat)) {
                return dataFormat;
            }
            
            // 根据Content-Type检测
            if (contentType != null) {
                if (contentType.contains("application/json")) {
                    // 尝试区分JSON数组和对象
                    if (content.trim().startsWith("[")) {
                        return "JSON_ARRAY";
                    } else if (content.trim().startsWith("{")) {
                        return "JSON_OBJECT";
                    }
                } else if (contentType.contains("text/csv") || contentType.contains("application/csv")) {
                    return "CSV";
                } else if (contentType.contains("text/plain")) {
                    return "TEXT";
                }
            }
            
            // 根据内容自动检测
            try {
                if (content.trim().startsWith("[")) {
                    JSONArray.parseArray(content);
                    return "JSON_ARRAY";
                } else if (content.trim().startsWith("{")) {
                    JSONObject.parseObject(content);
                    return "JSON_OBJECT";
                }
            } catch (Exception e) {
                // 不是有效的JSON格式
            }
            
            // 默认使用文本格式
            return "TEXT";
        }

        /**
         * 发送成功响应
         */
        private void sendSuccessResponse(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"success\",\"message\":\"Request processed successfully\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        }

        /**
         * 发送错误响应
         */
        private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            String response = "{\"status\":\"error\",\"message\":\"" + message + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        }
    }

    @Override
    public void stop() {
        super.stop();
        stopHttpServer();
    }

    // Getter和Setter方法，用于参数注入

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setPort(String port) {
        if (port != null && !port.trim().isEmpty()) {
            try {
                this.port = Integer.parseInt(port.trim());
            } catch (NumberFormatException e) {
                this.port = 8080; // 解析失败时使用默认值
            }
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        if (host != null && !host.trim().isEmpty()) {
            this.host = host.trim();
        }
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            this.path = path.trim();
        }
    }

    public boolean isEnableHttps() {
        return enableHttps;
    }

    public void setEnableHttps(boolean enableHttps) {
        this.enableHttps = enableHttps;
    }

    public void setEnableHttps(String enableHttps) {
        if (enableHttps != null) {
            this.enableHttps = Boolean.parseBoolean(enableHttps.trim());
        }
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public void setMaxBodySize(String maxBodySize) {
        if (maxBodySize != null && !maxBodySize.trim().isEmpty()) {
            try {
                this.maxBodySize = Integer.parseInt(maxBodySize.trim());
            } catch (NumberFormatException e) {
                this.maxBodySize = 1048576; // 解析失败时使用默认值
            }
        }
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(String dataFormat) {
        if (dataFormat != null && !dataFormat.trim().isEmpty()) {
            this.dataFormat = dataFormat.trim().toUpperCase();
        }
    }
}