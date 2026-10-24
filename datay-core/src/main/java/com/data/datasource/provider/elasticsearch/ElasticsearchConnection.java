package com.data.datasource.provider.elasticsearch;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Elasticsearch 的非 JDBC 连接实现。
 *
 * <p>不引入任何 JDBC 驱动，直接通过 REST API 通信：
 * <ul>
 *   <li>查询：{@code POST /_sql?format=json}，以 {@code {"query":"..."}} 提交 SQL；
 *       当语句为 JSON 时（以 {@code &#123; 开头}）按 Query DSL 走 {@code /_search}；</li>
 *   <li>元数据：{@code GET /_cat/indices} 列出索引（表），{@code GET /&#123;index&#125;/_mapping} 读取字段；</li>
 *   <li>连通性：{@code GET /}。</li>
 * </ul>
 *
 * <p>连接参数：{@code url} 可为 {@code http(s)://host:port}，也可只写 {@code host:port}；
 * 认证支持用户名/密码（Basic）或 {@code extraParams.es.api_key}（ApiKey）；
 * 可选 {@code extraParams.es.scheme}、{@code es.path_prefix}、{@code es.default_index}、{@code es.timeout_seconds}。
 */
public class ElasticsearchConnection implements DataSourceConnection {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final String authorization;
    private final String defaultIndex;

    public ElasticsearchConnection(DatasourceInfo info) {
        Map<String, String> extra = info.getExtraParams() == null ? Collections.emptyMap() : info.getExtraParams();
        this.baseUrl = resolveBaseUrl(info, extra);
        this.timeout = Duration.ofSeconds(parseInt(extra.get("es.timeout_seconds"), DEFAULT_TIMEOUT_SECONDS));
        this.authorization = resolveAuthorization(info, extra);
        this.defaultIndex = extra.get("es.default_index");
        // Elasticsearch 常使用自签名证书，平台侧默认始终忽略 https 证书校验
        this.httpClient = buildInsecureHttpClient(timeout);
    }

    @Override
    public QueryResult executeQuery(String statement, List<Object> params, int maxRows) throws Exception {
        if (statement == null || statement.trim().isEmpty()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }
        String trimmed = statement.trim();
        if (trimmed.startsWith("{")) {
            return searchByDsl(trimmed, maxRows);
        }
        return executeSql(statement, maxRows);
    }

    @Override
    public int executeUpdate(String statement, List<Object> params) {
        throw new UnsupportedOperationException("Elasticsearch 数据源为只读，不支持写操作");
    }

    @Override
    public List<String> getSchemas() {
        return List.of("_all");
    }

    @Override
    public List<TableMeta> getTables(String schema, Integer limit, String search) throws Exception {
        String response = request("GET", "/_cat/indices?format=json&h=index,docs.count,store.size&s=index", null);
        JSONArray indices = JSONUtil.parseArray(response);
        List<TableMeta> tables = new ArrayList<>();
        String keyword = search == null ? null : search.toLowerCase();
        for (int i = 0; i < indices.size(); i++) {
            JSONObject index = indices.getJSONObject(i);
            String name = index.getStr("index");
            if (name == null || name.isBlank()) {
                continue;
            }
            boolean hidden = name.startsWith(".");
            if (hidden && (keyword == null || !name.toLowerCase().contains(keyword))) {
                continue;
            }
            if (keyword != null && !name.toLowerCase().contains(keyword)) {
                continue;
            }
            if (limit != null && tables.size() >= limit) {
                break;
            }
            TableMeta table = new TableMeta(name);
            table.setDbType(ElasticsearchDataSourceProvider.TYPE);
            String docs = index.getStr("docs.count");
            if (docs != null) {
                table.setComment("docs.count=" + docs);
            }
            tables.add(table);
        }
        return tables;
    }

    @Override
    public TableMeta getTableMeta(String schema, String table) throws Exception {
        String response = request("GET", "/" + encode(table) + "/_mapping", null);
        JSONObject root = JSONUtil.parseObj(response);
        TableMeta tableMeta = new TableMeta(table);
        tableMeta.setDbType(ElasticsearchDataSourceProvider.TYPE);
        if (root.isEmpty()) {
            return tableMeta;
        }
        String firstName = root.keySet().iterator().next();
        JSONObject indexNode = root.getJSONObject(firstName);
        JSONObject mappings = indexNode == null ? null : indexNode.getJSONObject("mappings");
        JSONObject properties = mappings == null ? null : mappings.getJSONObject("properties");
        List<ColumnMeta> columns = new ArrayList<>();
        if (properties != null) {
            flattenMapping(properties, null, columns);
        }
        tableMeta.setColumns(columns);
        return tableMeta;
    }

    @Override
    public boolean testConnection() throws Exception {
        request("GET", "/", null);
        return true;
    }

    // ------------------------------------------------------------------ query

    private QueryResult executeSql(String sql, int maxRows) throws Exception {
        JSONObject body = new JSONObject();
        body.set("query", sql);
        String response = request("POST", "/_sql?format=json", body.toString());

        JSONObject result = JSONUtil.parseObj(response);
        if (result.containsKey("error")) {
            throw new IOException("Elasticsearch SQL 执行失败: " + result.get("error"));
        }
        JSONArray columnNodes = result.getJSONArray("columns");
        List<String> columns = new ArrayList<>();
        if (columnNodes != null) {
            for (int i = 0; i < columnNodes.size(); i++) {
                columns.add(columnNodes.getJSONObject(i).getStr("name"));
            }
        }
        JSONArray rowNodes = result.getJSONArray("rows");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (rowNodes != null) {
            for (int i = 0; i < rowNodes.size(); i++) {
                if (maxRows > 0 && rows.size() >= maxRows) {
                    break;
                }
                JSONArray rowNode = rowNodes.getJSONArray(i);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 0; c < columns.size(); c++) {
                    Object value = c < rowNode.size() ? normalizeValue(rowNode.get(c)) : null;
                    row.put(columns.get(c), value);
                }
                rows.add(row);
            }
        }
        return new QueryResult(columns, rows, rows.size());
    }

    private QueryResult searchByDsl(String dsl, int maxRows) throws Exception {
        int size = maxRows > 0 ? maxRows : 1000;
        String index = defaultIndex == null || defaultIndex.isBlank() ? "" : encode(defaultIndex) + "/";
        String path = "/" + index + "_search?size=" + size;
        String response = request("POST", path, dsl);

        JSONObject result = JSONUtil.parseObj(response);
        JSONObject hits = result.getJSONObject("hits");
        JSONArray hitNodes = hits == null ? null : hits.getJSONArray("hits");
        Set<String> columnSet = new LinkedHashSet<>();
        List<Map<String, Object>> rawRows = new ArrayList<>();
        if (hitNodes != null) {
            for (int i = 0; i < hitNodes.size(); i++) {
                if (maxRows > 0 && rawRows.size() >= maxRows) {
                    break;
                }
                JSONObject hit = hitNodes.getJSONObject(i);
                Map<String, Object> flat = new LinkedHashMap<>();
                flat.put("_index", hit.getStr("_index"));
                flat.put("_id", hit.getStr("_id"));
                flat.put("_score", hit.get("_score"));
                JSONObject source = hit.getJSONObject("_source");
                if (source != null) {
                    for (String key : source.keySet()) {
                        flat.put(key, normalizeValue(source.get(key)));
                    }
                }
                columnSet.addAll(flat.keySet());
                rawRows.add(flat);
            }
        }

        List<String> columns = new ArrayList<>(columnSet);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> flat : rawRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : columns) {
                row.put(column, flat.get(column));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, rows.size());
    }

    // ------------------------------------------------------------------ write

    /**
     * 覆盖式重建索引（用于全量同步）：先删除同名索引再创建，避免残留旧文档。
     */
    public void recreateIndex(String index) throws Exception {
        try {
            request("DELETE", "/" + encode(index), null);
        } catch (IOException ignored) {
            // 索引不存在时删除返回 404，忽略
        }
        request("PUT", "/" + encode(index), "{}");
    }

    /**
     * 批量写入文档到指定索引，返回成功写入的文档数。
     *
     * @param idField 指定作为文档 {@code _id} 的字段名；为 {@code null} 时由 ES 自动生成
     */
    public int bulkIndex(String index, List<Map<String, Object>> documents, String idField) throws Exception {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> document : documents) {
            JSONObject meta = new JSONObject();
            meta.set("_index", index);
            if (idField != null && document.get(idField) != null) {
                meta.set("_id", String.valueOf(document.get(idField)));
            }
            JSONObject action = new JSONObject();
            action.set("index", meta);
            ndjson.append(action).append('\n');
            ndjson.append(new JSONObject(document)).append('\n');
        }
        String response = request("POST", "/_bulk", ndjson.toString(), "application/x-ndjson");
        JSONObject result = JSONUtil.parseObj(response);
        JSONArray items = result.getJSONArray("items");
        int success = 0;
        String firstError = null;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i).getJSONObject("index");
                if (item == null) {
                    continue;
                }
                Integer statusObj = item.getInt("status");
                int status = statusObj == null ? 0 : statusObj;
                if (status >= 200 && status < 300) {
                    success++;
                } else if (firstError == null) {
                    firstError = item.getJSONObject("error") == null ? item.toString() : item.getJSONObject("error").toString();
                }
            }
        }
        if (Boolean.TRUE.equals(result.getBool("errors"))) {
            throw new IOException("Elasticsearch 批量写入存在失败项: " + firstError);
        }
        return success;
    }

    // ------------------------------------------------------------------ metadata

    private static void flattenMapping(JSONObject properties, String prefix, List<ColumnMeta> out) {
        for (String field : properties.keySet()) {
            JSONObject node = properties.getJSONObject(field);
            if (node == null) {
                continue;
            }
            String name = prefix == null ? field : prefix + "." + field;
            String type = node.getStr("type");
            JSONObject nested = node.getJSONObject("properties");
            if (type != null || nested == null) {
                ColumnMeta column = new ColumnMeta(name, type == null ? "object" : type);
                column.setNullable(true);
                out.add(column);
            }
            if (nested != null) {
                flattenMapping(nested, name, out);
            }
        }
    }

    // ------------------------------------------------------------------ http

    private String request(String method, String path, String body) throws Exception {
        return request(method, path, body, "application/json");
    }

    private String request(String method, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest
            .newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Content-Type", contentType);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("Elasticsearch 请求失败 [" + response.statusCode() + "] " + path + ": " + response.body());
        }
        return response.body();
    }

    private static String resolveBaseUrl(DatasourceInfo info, Map<String, String> extra) {
        String scheme = extra.getOrDefault("es.scheme", "http");
        String url = info.getUrl();
        String base;
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            base = url;
        } else if (url != null && !url.isBlank()) {
            base = scheme + "://" + url;
        } else if (info.getHostname() != null && !info.getHostname().isBlank()) {
            String port = info.getPort() != null && !info.getPort().isBlank() ? ":" + info.getPort() : "";
            base = scheme + "://" + info.getHostname() + port;
        } else {
            throw new IllegalArgumentException("Elasticsearch 数据源缺少连接地址（url 或 hostname）");
        }
        String pathPrefix = extra.get("es.path_prefix");
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            base = base + "/" + pathPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
        }
        return base.replaceAll("/+$", "");
    }

    private static String resolveAuthorization(DatasourceInfo info, Map<String, String> extra) {
        String apiKey = extra.get("es.api_key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "ApiKey " + apiKey;
        }
        if (info.getUsername() != null && !info.getUsername().isBlank()) {
            String credentials = info.getUsername() + ":" + (info.getPassword() == null ? "" : info.getPassword());
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    private static Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 构建始终信任所有证书、且不做主机名校验的 HTTP 客户端。
     *
     * <p>Elasticsearch 集群普遍使用自签名证书，平台默认忽略 https 证书校验，避免连接测试/查询
     * 因证书不可信而失败。
     */
    private static HttpClient buildInsecureHttpClient(Duration timeout) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { TRUST_ALL_MANAGER }, new SecureRandom());
            SSLParameters sslParameters = new SSLParameters();
            // 置空以关闭主机名（HTTPS endpoint identification）校验
            sslParameters.setEndpointIdentificationAlgorithm(null);
            return HttpClient.newBuilder().connectTimeout(timeout).sslContext(sslContext).sslParameters(sslParameters).build();
        } catch (Exception e) {
            throw new IllegalStateException("初始化 Elasticsearch HTTP 客户端失败", e);
        }
    }

    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
