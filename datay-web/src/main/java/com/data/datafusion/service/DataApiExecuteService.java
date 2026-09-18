package com.data.datafusion.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.mcp.McpTokenService;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.data.job.DatasourceInfo;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 数据服务对外调用入口。
 *
 * <p>复用用户的 {@code mcp_token} 鉴权：令牌所属用户默认租户下配置的“数据服务”才会被解析和执行。
 * 表来源类型支持按列等值过滤与分页；SQL 来源类型把 {@code ${param}} 占位符替换为预编译参数后只读执行。</p>
 */
@Service
public class DataApiExecuteService {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiExecuteService.class);

    private static final String PLACEHOLDER_PREFIX = "${";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 1000;

    private static final int MAX_SQL_ROWS = 10000;

    private static final int DEFAULT_HTTP_TIMEOUT = 30000;

    private static final int MAX_HTTP_TIMEOUT = 300000;

    /** JDK HttpClient 禁止设置的受限请求头，遇到时跳过而不是抛出异常。 */
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
        "connection",
        "content-length",
        "expect",
        "host",
        "upgrade"
    );

    private static final Pattern DML_KEYWORD = Pattern.compile(
        "(?i)\\b(update|delete|insert|drop|alter|truncate|merge|create|replace|grant|revoke|rename|set)\\b"
    );

    /** 提取 SQL 的首个关键字，忽略前导空白以及行注释、块注释。 */
    private static final Pattern FIRST_KEYWORD = Pattern.compile(
        "^\\s*(?:(?:--[^\\n]*\\n|/\\*[\\s\\S]*?\\*/)\\s*)*([A-Za-z]+)"
    );

    private final McpTokenService mcpTokenService;

    private final TenantRepository tenantRepository;

    private final DataApiService dataApiService;

    private final DataSourceService dataSourceService;

    /** 前置处理 Token 缓存，键为「服务 ID + 前置配置」，值为 Token 与过期时间。 */
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public DataApiExecuteService(
        McpTokenService mcpTokenService,
        TenantRepository tenantRepository,
        DataApiService dataApiService,
        DataSourceService dataSourceService
    ) {
        this.mcpTokenService = mcpTokenService;
        this.tenantRepository = tenantRepository;
        this.dataApiService = dataApiService;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 使用用户的 mcp_token 调用指定编码的数据服务。
     *
     * @param token  用户令牌，形如 {@code mcp_<userId>_<random>}。
     * @param code   服务编码。
     * @param params 调用参数（键为参数名，值为参数值）。表模式中与表字段同名的参数将做等值过滤，
     *               并识别 {@code pageNum}/{@code pageSize} 分页参数；SQL 模式中用于绑定 SQL 里的占位符。
     * @return 统一返回体 {@code {code, message, data, columns, total, pageNum, pageSize}}。
     */
    public Map<String, Object> invokeApi(String token, String code, Map<String, Object> params) {
        User user = mcpTokenService
            .authenticateUser(token)
            .orElseThrow(() -> new DataApiAccessException(401, "无效或缺失的访问令牌"));

        Long tenantId = tenantRepository
            .findDefaultTenantByUserId(user.getId())
            .map(Tenant::getId)
            .orElseThrow(() -> new DataApiAccessException(401, "用户未关联任何租户"));

        TenantContext.setTenantId(tenantId);
        try {
            DataApiDTO api = dataApiService
                .findByCode(code)
                .orElseThrow(() -> new DataApiAccessException(404, "服务不存在或未发布：" + code));
            if (!DataApi.STATUS_ENABLED.equals(api.getStatus())) {
                throw new DataApiAccessException(404, "服务不存在或未发布：" + code);
            }

            if (DataApi.SOURCE_TYPE_API.equals(api.getSourceType())) {
                return invokeFromApi(api, params);
            }

            DataSourceDTO dataSource = dataSourceService
                .findOne(api.getDataSourceId())
                .orElseThrow(() -> new DataApiAccessException(500, "服务绑定的数据源不存在"));

            DatasourceInfo datasourceInfo = DataSourceQueryService.toDatasourceInfo(dataSource);
            if (DataApi.SOURCE_TYPE_TABLE.equals(api.getSourceType())) {
                return invokeFromTable(api, datasourceInfo, params);
            }
            return invokeFromSql(api, datasourceInfo, params);
        } finally {
            TenantContext.clear();
        }
    }

    // ------------------------------------------------------------------ table

    private Map<String, Object> invokeFromTable(DataApiDTO api, DatasourceInfo datasourceInfo, Map<String, Object> params) {
        String schema = api.getSchemaName();
        String table = api.getTableName();
        try (Connection connection = DBUtils.getConnection(datasourceInfo)) {
            String dbType = resolveDbType(datasourceInfo);
            TableMeta tableMeta = DBUtils.getTableMetaData(connection, schema, table);
            Map<String, ColumnMeta> columnMap = new LinkedHashMap<>();
            for (ColumnMeta column : tableMeta.columns()) {
                columnMap.put(column.getName(), column);
            }

            String qualifiedTable = qualifyTable(dbType, schema, table);
            List<String> conditions = new ArrayList<>();
            List<Object> bindValues = new ArrayList<>();

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                if (paramName == null || isReservedTableParam(paramName)) {
                    continue;
                }
                ColumnMeta column = columnMap.get(paramName);
                if (column == null) {
                    continue;
                }
                String rawValue = toStringValue(entry.getValue());
                if (rawValue == null || rawValue.isEmpty()) {
                    continue;
                }
                conditions.add(quoteIdent(dbType, column.getName()) + " = ?");
                bindValues.add(convertByColumnType(column, rawValue));
            }

            int pageNum = parsePositiveInt(toStringValue(params.get("pageNum")), 1);
            int pageSize = Math.min(Math.max(parsePositiveInt(toStringValue(params.get("pageSize")), DEFAULT_PAGE_SIZE), 1), MAX_PAGE_SIZE);
            long maxOffset = Integer.MAX_VALUE - pageSize;
            if (((long) pageNum - 1) * pageSize > maxOffset) {
                pageNum = (int) (maxOffset / pageSize) + 1;
            }
            int offset = (pageNum - 1) * pageSize;

            String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

            long total = 0;
            try (
                PreparedStatement countStmt = connection.prepareStatement("SELECT COUNT(*) FROM " + qualifiedTable + where);
            ) {
                bindParameters(countStmt, bindValues);
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }

            String baseSelect = "SELECT * FROM " + qualifiedTable + where;
            String pageSql = buildPagedSelect(dbType, baseSelect, pageSize, offset);
            List<Object> pageParams = new ArrayList<>(bindValues);
            if (isOracle(dbType)) {
                pageParams.add((long) pageNum * pageSize);
                pageParams.add((long) offset);
            } else {
                pageParams.add(pageSize);
                pageParams.add(offset);
            }

            Map<String, Object> readResult;
            try (
                PreparedStatement dataStmt = connection.prepareStatement(pageSql);
            ) {
                bindParameters(dataStmt, pageParams);
                try (ResultSet rs = dataStmt.executeQuery()) {
                    readResult = readResultSet(rs, MAX_PAGE_SIZE);
                }
            }

            return buildSuccess(readResult, total, pageNum, pageSize);
        } catch (SQLException e) {
            LOG.error("Failed to execute table data api {} ({}).{}", api.getCode(), schema, table, e);
            throw new DataApiAccessException(500, "服务执行失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------- sql

    private Map<String, Object> invokeFromSql(DataApiDTO api, DatasourceInfo datasourceInfo, Map<String, Object> params) {
        String originalSql = api.getSqlText();
//        assertReadOnlySql(originalSql);

        List<String> placeholderNames = new ArrayList<>();
        StringBuilder parsedSql = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(originalSql);
        int lastEnd = 0;
        while (matcher.find()) {
            parsedSql.append(originalSql, lastEnd, matcher.start());
            placeholderNames.add(matcher.group(1));
            parsedSql.append("?");
            lastEnd = matcher.end();
        }
        parsedSql.append(originalSql.substring(lastEnd));

        List<String> missing = new ArrayList<>();
        List<Object> bindValues = new ArrayList<>();
        for (String name : placeholderNames) {
            Object value = params.get(name);
            if (value == null || toStringValue(value).isEmpty()) {
                missing.add(name);
            } else {
                bindValues.add(toBindableValue(value));
            }
        }
        if (!missing.isEmpty()) {
            throw new DataApiAccessException(400, "缺少参数：" + String.join(", ", missing));
        }

        String sql = parsedSql.toString();
        try (Connection connection = DBUtils.getConnection(datasourceInfo)) {
            Map<String, Object> readResult;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                bindParameters(stmt, bindValues);
                try (ResultSet rs = stmt.executeQuery()) {
                    readResult = readResultSet(rs, MAX_SQL_ROWS);
                }
            }
            return buildSuccess(readResult, ((List<?>) readResult.get("rows")).size(), 1, 0);
        } catch (SQLException e) {
            LOG.error("Failed to execute sql data api {}: {}", api.getCode(), sql, e);
            throw new DataApiAccessException(500, "服务执行失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------- api

    /**
     * 代理调用已注册的 HTTP API。
     *
     * <p>支持两种能力：其一，直接按配置的地址、方法、请求头、请求体转发；其二，先调用前置接口获取
     * Token。URL、请求头、请求体中均可使用 {@code ${param}} 占位符，调用方同名参数会在转发前替换；
     * 前置接口获取到的 Token 则以 {@code ${token}} 引用。</p>
     */
    private Map<String, Object> invokeFromApi(DataApiDTO api, Map<String, Object> params) {
        JSONObject config = parseApiConfig(api.getApiConfig());
        String url = config.getString("url");
        if (url == null || url.isBlank()) {
            throw new DataApiAccessException(500, "API 服务未配置请求地址");
        }

        Map<String, Object> context = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if ("access_token".equals(entry.getKey())) {
                continue;
            }
            String value = toStringValue(entry.getValue());
            if (value != null) {
                context.put(entry.getKey(), value);
            }
        }

        String body = config.getString("body");
        String method = normalizeHttpMethod(config.getString("method"), body);
        JSONObject headers = new JSONObject();
        JSONObject configuredHeaders = config.getJSONObject("headers");
        if (configuredHeaders != null) {
            headers.putAll(configuredHeaders);
        }

        JSONObject preProcess = config.getJSONObject("preProcess");
        if (preProcess != null && preProcess.getBooleanValue("enabled", false)) {
            context.put("token", resolvePreProcessToken(api, preProcess, context));
        }

        url = substitutePlaceholders(url, context, "请求地址");
        body = substitutePlaceholders(body, context, "请求体");
        headers = substituteHeaders(headers, context);

        int timeout = clampTimeout(config.getIntValue("timeout", DEFAULT_HTTP_TIMEOUT));
        boolean sslVerify = config.getBooleanValue("sslVerify", true);

        HttpResponse<String> response = sendHttpRequest(method, url, headers, body, timeout, sslVerify);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DataApiAccessException(502, "上游接口调用失败（HTTP " + response.statusCode() + "）：" + truncate(response.body()));
        }
        return buildApiResult(response.body(), config.getString("jsonPath"));
    }

    private String resolvePreProcessToken(DataApiDTO api, JSONObject preProcess, Map<String, Object> context) {
        int ttlSeconds = Math.max(0, preProcess.getIntValue("cacheTtlSeconds", 0));
        String cacheKey = api.getId() + "|" + preProcess.toJSONString();
        if (ttlSeconds > 0) {
            CachedToken cached = tokenCache.get(cacheKey);
            if (cached != null && cached.expireAt() > System.currentTimeMillis()) {
                return cached.value();
            }
        }

        String url = preProcess.getString("url");
        if (url == null || url.isBlank()) {
            throw new DataApiAccessException(500, "前置处理未配置请求地址");
        }
        String body = preProcess.getString("body");
        String method = normalizeHttpMethod(preProcess.getString("method"), body);
        JSONObject headers = new JSONObject();
        JSONObject configuredHeaders = preProcess.getJSONObject("headers");
        if (configuredHeaders != null) {
            headers.putAll(configuredHeaders);
        }

        String resolvedUrl = substitutePlaceholders(url, context, "前置请求地址");
        String resolvedBody = substitutePlaceholders(body, context, "前置请求体");
        JSONObject resolvedHeaders = substituteHeaders(headers, context);

        int timeout = clampTimeout(preProcess.getIntValue("timeout", DEFAULT_HTTP_TIMEOUT));
        boolean sslVerify = preProcess.getBooleanValue("sslVerify", true);

        HttpResponse<String> response = sendHttpRequest(method, resolvedUrl, resolvedHeaders, resolvedBody, timeout, sslVerify);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DataApiAccessException(
                502,
                "前置接口调用失败（HTTP " + response.statusCode() + "）：" + truncate(response.body())
            );
        }

        String token = extractToken(response.body(), preProcess.getString("tokenPath"));
        if (token == null || token.isBlank()) {
            throw new DataApiAccessException(502, "未能从前置接口响应中提取 Token");
        }
        if (ttlSeconds > 0) {
            tokenCache.put(cacheKey, new CachedToken(token, System.currentTimeMillis() + ttlSeconds * 1000L));
        }
        return token;
    }

    private static String extractToken(String responseBody, String tokenPath) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        if (tokenPath == null || tokenPath.isBlank()) {
            return responseBody.trim();
        }
        try {
            Object value = JSONPath.eval(JSON.parse(responseBody), tokenPath);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            throw new DataApiAccessException(502, "Token 提取路径无效：" + tokenPath);
        }
    }

    private HttpResponse<String> sendHttpRequest(
        String method,
        String url,
        JSONObject headers,
        String body,
        int timeout,
        boolean sslVerify
    ) {
        validateHttpUrl(url);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMillis(timeout));
            if (headers != null) {
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    String name = entry.getKey();
                    Object value = entry.getValue();
                    if (name == null || value == null || RESTRICTED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    builder.header(name, String.valueOf(value));
                }
            }
            HttpRequest.BodyPublisher publisher = (body == null || body.isEmpty())
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            builder.method(method, publisher);
            HttpClient client = buildHttpClient(timeout, sslVerify);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (DataApiAccessException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to call registered api: {} {}", method, url, e);
            throw new DataApiAccessException(502, "调用上游接口失败：" + e.getMessage());
        }
    }

    private static HttpClient buildHttpClient(int timeout, boolean sslVerify) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.min(timeout, 30000)))
            .followRedirects(HttpClient.Redirect.NORMAL);
        if (!sslVerify) {
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            builder.sslContext(trustAllSslContext()).sslParameters(sslParameters);
        }
        return builder.build();
    }

    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // 信任所有客户端证书
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // 信任所有服务端证书
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                },
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new DataApiAccessException(500, "初始化 HTTPS 上下文失败：" + e.getMessage());
        }
    }

    private static void validateHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new DataApiAccessException(500, "API 服务未配置请求地址");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new DataApiAccessException(400, "非法的 API 地址：" + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new DataApiAccessException(400, "仅支持 http/https 协议的 API 地址");
        }
    }

    private static Map<String, Object> buildApiResult(String responseBody, String jsonPath) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");

        boolean hasPath = jsonPath != null && !jsonPath.isBlank();
        List<String> columns = new ArrayList<>();
        Object data = null;
        long total = 0;

        Object root = null;
        boolean parsedJson = false;
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                root = JSON.parse(responseBody);
                parsedJson = true;
            } catch (Exception e) {
                if (hasPath) {
                    throw new DataApiAccessException(502, "响应内容不是合法 JSON，无法按路径提取数据");
                }
                data = responseBody;
                total = 1;
            }
        }

        if (parsedJson) {
            Object value = root;
            if (hasPath) {
                try {
                    value = JSONPath.eval(root, jsonPath);
                } catch (Exception e) {
                    throw new DataApiAccessException(502, "数据提取路径无效：" + jsonPath);
                }
                if (value == null) {
                    throw new DataApiAccessException(502, "数据提取路径未匹配到数据：" + jsonPath);
                }
            }
            data = value;
            if (value instanceof JSONArray array) {
                total = array.size();
                if (!array.isEmpty() && array.get(0) instanceof JSONObject first) {
                    columns.addAll(first.keySet());
                }
            } else if (value instanceof JSONObject object) {
                total = 1;
                columns.addAll(object.keySet());
            } else {
                total = 1;
            }
        } else if (hasPath) {
            throw new DataApiAccessException(502, "响应内容为空，无法按路径提取数据");
        }

        result.put("data", data);
        result.put("columns", columns);
        result.put("total", total);
        return result;
    }

    private static JSONObject parseApiConfig(String apiConfig) {
        if (apiConfig == null || apiConfig.isBlank()) {
            throw new DataApiAccessException(500, "API 服务未配置");
        }
        try {
            return JSON.parseObject(apiConfig);
        } catch (Exception e) {
            throw new DataApiAccessException(500, "API 服务配置解析失败：" + e.getMessage());
        }
    }

    private static String substitutePlaceholders(String template, Map<String, Object> context, String label) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder();
        List<String> missing = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = context.get(name);
            if (value == null) {
                missing.add(name);
                matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(builder);
        if (!missing.isEmpty()) {
            throw new DataApiAccessException(400, label + "缺少参数：" + String.join(", ", missing));
        }
        return builder.toString();
    }

    private static JSONObject substituteHeaders(JSONObject headers, Map<String, Object> context) {
        JSONObject result = new JSONObject();
        if (headers == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Object value = entry.getValue();
            result.put(
                entry.getKey(),
                value == null ? null : substitutePlaceholders(String.valueOf(value), context, "请求头")
            );
        }
        return result;
    }

    private static String normalizeHttpMethod(String method, String body) {
        if (method == null || method.isBlank()) {
            return body == null || body.isBlank() ? "GET" : "POST";
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private static int clampTimeout(int timeout) {
        if (timeout <= 0) {
            return DEFAULT_HTTP_TIMEOUT;
        }
        return Math.min(timeout, MAX_HTTP_TIMEOUT);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }

    /** 缓存的前置处理 Token。 */
    private record CachedToken(String value, long expireAt) {}

    private void assertReadOnlySql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new DataApiAccessException(400, "自定义 SQL 不能为空");
        }
        String trimmed = sql.trim();
        Matcher keywordMatcher = FIRST_KEYWORD.matcher(trimmed);
        String firstWord = keywordMatcher.find() ? keywordMatcher.group(1).toUpperCase() : "";
        boolean readableFirstKeyword =
            firstWord.equals("SELECT") ||
            firstWord.equals("WITH") ||
            firstWord.equals("SHOW") ||
            firstWord.equals("EXPLAIN") ||
            firstWord.equals("DESC") ||
            firstWord.equals("DESCRIBE");
        if (!readableFirstKeyword || DML_KEYWORD.matcher(trimmed).find()) {
            throw new DataApiAccessException(400, "自定义 SQL 仅支持 SELECT 等只读查询");
        }
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> buildSuccess(Map<String, Object> readResult, long total, int pageNum, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", readResult.get("rows"));
        result.put("columns", readResult.get("columns"));
        result.put("total", total);
        if (pageNum > 0) {
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
        }
        return result;
    }

    private Map<String, Object> readResultSet(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), convertCell(rs.getObject(i)));
            }
            rows.add(row);
            rowCount++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        return result;
    }

    private static Object convertCell(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date || value instanceof java.sql.Time) {
            return value.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof byte[]) {
            return "[BLOB]";
        }
        if (value instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (SQLException e) {
                return null;
            }
        }
        if (value instanceof java.sql.Blob) {
            return "[BLOB]";
        }
        return value;
    }

    private static void bindParameters(PreparedStatement stmt, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            stmt.setObject(i + 1, values.get(i));
        }
    }

    private static Object convertByColumnType(ColumnMeta column, String rawValue) {
        String type = column.getType() == null ? "" : column.getType().toUpperCase();
        if (type.contains("BOOL")) {
            return toBoolean(rawValue);
        }
        if (type.contains("INT") || type.contains("NUM") || type.contains("DEC") || type.contains("FLOAT") || type.contains("DOUBLE") || type.contains("REAL")) {
            try {
                if (
                    type.contains("FLOAT") ||
                    type.contains("DOUBLE") ||
                    type.contains("REAL") ||
                    type.contains("DEC") ||
                    type.contains("NUM")
                ) {
                    return Double.parseDouble(rawValue);
                }
                return Long.parseLong(rawValue);
            } catch (NumberFormatException e) {
                return rawValue;
            }
        }
        return rawValue;
    }

    private static Object toBindableValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = toStringValue(value);
        if (text == null || text.isEmpty()) {
            return text;
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.valueOf(text);
        }
        if (text.matches("-?\\d+")) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (text.matches("-?\\d+\\.\\d+([eE][+-]?\\d+)?")) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return text;
    }

    private static Boolean toBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof String[] array) {
            return array.length == 0 ? null : array[0];
        }
        return String.valueOf(value);
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isReservedTableParam(String paramName) {
        return "pageNum".equals(paramName) || "pageSize".equals(paramName) || "access_token".equals(paramName);
    }

    private static String buildPagedSelect(String dbType, String baseSelect, int pageSize, int offset) {
        if (isOracle(dbType)) {
            return "SELECT * FROM (SELECT t.*, ROWNUM rn FROM (" + baseSelect + ") t WHERE ROWNUM <= ?) WHERE rn > ?";
        }
        return baseSelect + " LIMIT ? OFFSET ?";
    }

    private static boolean isOracle(String dbType) {
        return "ORACLE".equals(dbType);
    }

    private static String resolveDbType(DatasourceInfo datasourceInfo) {
        String fromUrl = DBUtils.getDBType(datasourceInfo.getUrl());
        if (fromUrl != null && !fromUrl.isBlank()) {
            return fromUrl.toUpperCase();
        }
        return datasourceInfo.getType() == null ? "MYSQL" : datasourceInfo.getType().toUpperCase();
    }

    private static String qualifyTable(String dbType, String schema, String table) {
        String quotedTable = quoteIdent(dbType, table);
        if (schema != null && !schema.trim().isEmpty()) {
            return quoteIdent(dbType, schema.trim()) + "." + quotedTable;
        }
        return quotedTable;
    }

    private static String quoteIdent(String dbType, String ident) {
        if (ident == null) {
            return "";
        }
        if ("MYSQL".equals(dbType) || "DORIS".equals(dbType) || "CLICKHOUSE".equals(dbType)) {
            return "`" + ident.replace("`", "``") + "`";
        }
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
