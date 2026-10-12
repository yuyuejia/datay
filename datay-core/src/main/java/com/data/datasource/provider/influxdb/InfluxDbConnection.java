package com.data.datasource.provider.influxdb;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
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
 * InfluxDB 的非 JDBC 连接实现。
 *
 * <p>不引入任何 InfluxDB 客户端依赖，直接通过 HTTP API 通信：
 * <ul>
 *   <li>InfluxDB 1.x：{@code POST /query}（InfluxQL），元数据通过
 *       {@code SHOW DATABASES} / {@code SHOW MEASUREMENTS} / {@code SHOW FIELD KEYS} /
 *       {@code SHOW TAG KEYS} 获取；</li>
 *   <li>InfluxDB 2.x：{@code POST /api/v2/query}（Flux，返回注解式 CSV），
 *       Bucket 通过 Buckets API 读取，measurement/tag/field 通过
 *       {@code influxdata/influxdb/schema} 包读取；</li>
 *   <li>连通性：{@code GET /ping}（失败时回退 {@code GET /health}）。</li>
 * </ul>
 *
 * <p>连接参数：{@code url} 直接填写 {@code http(s)://host:port}（推荐），也兼容
 * {@code influxdb://host:port} 或仅 {@code host:port}。Bucket（2.x）/ Database（1.x）取自数据源 Schema。
 * 可选 {@code extraParams}：
 * <ul>
 *   <li>{@code influx.org}：2.x 组织名；{@code influx.token}：2.x API Token；
 *       配置了 Org 或 Token 即视为 2.x（Flux），否则视为 1.x（InfluxQL，使用用户名/密码）；</li>
 *   <li>{@code influx.version}：可显式指定 {@code 1} / {@code 2} 覆盖上述推断；</li>
 *   <li>{@code influx.scheme}：URL 未带协议时的默认协议，默认 {@code http}；</li>
 *   <li>{@code influx.bucket} / {@code influx.database}：覆盖默认 Bucket / Database（缺省回退 Schema）；</li>
 *   <li>{@code influx.timeout_seconds}：请求超时秒数，默认 30。</li>
 * </ul>
 *
 * <p>该连接对通用查询为只读（{@link #executeUpdate} 不支持写操作）；ETL 输出组件通过专用的
 * {@link #writePoints} / {@link #deleteMeasurement} 完成写入。
 */
public class InfluxDbConnection implements DataSourceConnection {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String VERSION_1 = "1";
    private static final String VERSION_2 = "2";

    private final String baseUrl;
    private final String version;
    private final String org;
    private final String token;
    private final String defaultBucket;
    private final String defaultDatabase;
    private final String retentionPolicy;
    private final String username;
    private final String password;
    private final Duration timeout;
    private final HttpClient httpClient;

    public InfluxDbConnection(DatasourceInfo info) {
        Map<String, String> extra = info.getExtraParams() == null ? Collections.emptyMap() : info.getExtraParams();
        this.baseUrl = resolveBaseUrl(info, extra);
        this.org = trimToNull(extra.get("influx.org"));
        this.token = trimToNull(extra.get("influx.token"));
        this.version = resolveVersion(extra.get("influx.version"), this.org, this.token);
        this.username = info.getUsername();
        this.password = info.getPassword();
        String schema = trimToNull(info.getDbschema());
        this.defaultBucket = coalesce(trimToNull(extra.get("influx.bucket")), schema);
        this.defaultDatabase = coalesce(trimToNull(extra.get("influx.database")), schema);
        this.retentionPolicy = trimToNull(extra.get("influx.rp"));
        this.timeout = Duration.ofSeconds(parseInt(extra.get("influx.timeout_seconds"), DEFAULT_TIMEOUT_SECONDS));
        // InfluxDB 常见自签名证书部署，平台侧默认忽略 https 证书校验
        this.httpClient = buildInsecureHttpClient(timeout);
    }

    /** 连接使用的 InfluxDB 版本（{@code "1"} 或 {@code "2"}）。 */
    public String getVersion() {
        return version;
    }

    // ------------------------------------------------------------------ DataSourceConnection

    @Override
    public QueryResult executeQuery(String statement, List<Object> params, int maxRows) throws Exception {
        if (statement == null || statement.trim().isEmpty()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }
        if (VERSION_1.equals(version)) {
            return queryInfluxQl(statement, maxRows);
        }
        // 2.x：语句为 Flux；若用户直接书写 InfluxQL（含 SHOW/SELECT 且非 Flux），走兼容的 /query 接口
        if (isFlux(statement)) {
            return queryFlux(statement, maxRows);
        }
        return queryInfluxQl(statement, maxRows);
    }

    @Override
    public int executeUpdate(String statement, List<Object> params) {
        throw new UnsupportedOperationException("InfluxDB 数据源为只读，不支持写操作");
    }

    // ------------------------------------------------------------------ 写能力（供 ETL 组件复用）

    /**
     * 以行协议批量写入数据点。
     *
     * <p>每行 {@code Map} 转换为一条 line protocol：{@code tagColumns} 中的列作为 tag，
     * {@code timeColumn} 指定的列作为时间戳（ISO 字符串按时刻解析；数值按 {@code timeUnit} 换算为纳秒），
     * 其余非空列作为 field。返回实际写入的点数。
     *
     * @param bucket     目标 Bucket（2.x）/ Database（1.x），为空时回退数据源默认值
     * @param measurement 目标 measurement（表名），必填
     * @param rows       待写入的行
     * @param timeColumn 时间戳列名，可为 {@code null}（由服务端赋予当前时间）
     * @param timeUnit   数值时间戳单位：{@code ns}/{@code us}/{@code ms}/{@code s}，默认 {@code s}
     * @param tagColumns 作为 tag 的列名集合，可为 {@code null}
     * @return 写入的数据点数量
     */
    public int writePoints(
        String bucket,
        String measurement,
        List<Map<String, Object>> rows,
        String timeColumn,
        String timeUnit,
        Collection<String> tagColumns
    ) throws Exception {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (measurement == null || measurement.trim().isEmpty()) {
            throw new IllegalArgumentException("InfluxDB 写入需要指定 measurement（目标表）");
        }
        String target = coalesce(trimToNull(bucket), VERSION_2.equals(version) ? defaultBucket : defaultDatabase);
        if (target == null) {
            throw new IllegalArgumentException("InfluxDB 写入需要指定 Bucket / Database");
        }
        if (VERSION_2.equals(version) && org == null) {
            throw new IllegalArgumentException("InfluxDB 2.x 写入需要配置 org：请在数据源额外参数中填写 influx.org");
        }

        Set<String> tags = new LinkedHashSet<>();
        if (tagColumns != null) {
            for (String tag : tagColumns) {
                if (tag != null && !tag.trim().isEmpty()) {
                    tags.add(tag.trim());
                }
            }
        }
        String timeField = trimToNull(timeColumn);
        String unit = timeUnit == null ? "s" : timeUnit.trim().toLowerCase();

        StringBuilder body = new StringBuilder();
        int lines = 0;
        for (Map<String, Object> row : rows) {
            String line = toLineProtocol(measurement.trim(), row, timeField, unit, tags);
            if (line == null) {
                continue;
            }
            if (lines > 0) {
                body.append('\n');
            }
            body.append(line);
            lines++;
        }
        if (lines == 0) {
            return 0;
        }

        String path;
        if (VERSION_2.equals(version)) {
            path = "/api/v2/write?org=" + encode(org) + "&bucket=" + encode(target) + "&precision=ns";
        } else {
            path = "/write?db=" + encode(target) + "&precision=ns";
            if (retentionPolicy != null) {
                path += "&rp=" + encode(retentionPolicy);
            }
        }
        request("POST", path, body.toString(), "text/plain; charset=utf-8", null);
        return lines;
    }

    /**
     * 删除指定 measurement 的全部数据（用于 overwrite 写入模式）。
     *
     * <p>2.x 走 {@code /api/v2/delete}；1.x 走 {@code DROP MEASUREMENT}。
     */
    public void deleteMeasurement(String bucket, String measurement) throws Exception {
        if (measurement == null || measurement.trim().isEmpty()) {
            throw new IllegalArgumentException("InfluxDB 删除需要指定 measurement（目标表）");
        }
        String name = measurement.trim();
        String target = coalesce(trimToNull(bucket), VERSION_2.equals(version) ? defaultBucket : defaultDatabase);
        if (VERSION_1.equals(version)) {
            queryInfluxQl("DROP MEASUREMENT " + influxQlIdentifier(name), 0, target);
            return;
        }
        if (org == null) {
            throw new IllegalArgumentException("InfluxDB 2.x 删除需要配置 org：请在数据源额外参数中填写 influx.org");
        }
        if (target == null) {
            throw new IllegalArgumentException("InfluxDB 2.x 删除需要指定 Bucket");
        }
        JSONObject body = new JSONObject();
        body.set("start", "1970-01-01T00:00:00Z");
        body.set("stop", Instant.now().plusSeconds(3600).toString());
        body.set("predicate", "_measurement=" + fluxString(name));
        request(
            "POST",
            "/api/v2/delete?org=" + encode(org) + "&bucket=" + encode(target),
            body.toString(),
            "application/json",
            null
        );
    }

    private static String toLineProtocol(
        String measurement,
        Map<String, Object> row,
        String timeField,
        String timeUnit,
        Set<String> tags
    ) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        StringBuilder line = new StringBuilder(escapeLineProtocol(measurement, false));
        for (String tag : tags) {
            Object value = row.get(tag);
            if (value == null) {
                continue;
            }
            String tagValue = String.valueOf(value);
            if (tagValue.isEmpty()) {
                continue;
            }
            line.append(',').append(escapeLineProtocol(tag, true)).append('=').append(escapeLineProtocol(tagValue, true));
        }

        StringBuilder fields = new StringBuilder();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty() || tags.contains(key) || (timeField != null && timeField.equals(key))) {
                continue;
            }
            String formatted = formatFieldValue(entry.getValue());
            if (formatted == null) {
                continue;
            }
            if (fields.length() > 0) {
                fields.append(',');
            }
            fields.append(escapeLineProtocol(key, true)).append('=').append(formatted);
        }
        if (fields.length() == 0) {
            return null;
        }
        line.append(' ').append(fields);

        if (timeField != null) {
            Long nanos = toEpochNanos(row.get(timeField), timeUnit);
            if (nanos != null) {
                line.append(' ').append(nanos);
            }
        }
        return line.toString();
    }

    private static String formatFieldValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (value instanceof Float number) {
            return Float.isFinite(number) ? number.toString() : null;
        }
        if (value instanceof Double number) {
            return Double.isFinite(number) ? number.toString() : null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return value + "i";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof java.util.Date date) {
            return quoteFieldValue(Instant.ofEpochMilli(date.getTime()).toString());
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : quoteFieldValue(text);
    }

    private static String quoteFieldValue(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 行协议转义：measurement/tag/field key 需转义逗号、空格、等号；值额外场景由调用方控制。 */
    private static String escapeLineProtocol(String value, boolean escapeEquals) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == ',' || c == ' ') {
                sb.append('\\').append(c);
            } else if (escapeEquals && c == '=') {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Long toEpochNanos(Object value, String timeUnit) {
        if (value == null) {
            return null;
        }
        long multiplier = unitMultiplier(timeUnit);
        if (value instanceof Number number) {
            return multiplyExact(number.longValue(), multiplier);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        Instant instant = parseInstant(text);
        if (instant != null) {
            return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
        }
        try {
            return multiplyExact(Long.parseLong(text), multiplier);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long unitMultiplier(String timeUnit) {
        switch (timeUnit == null ? "s" : timeUnit) {
            case "ns":
                return 1L;
            case "us":
                return 1_000L;
            case "ms":
                return 1_000_000L;
            default:
                return 1_000_000_000L;
        }
    }

    private static Long multiplyExact(long value, long multiplier) {
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // 继续尝试带时区或本地时间格式
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // 继续尝试本地时间格式
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    @Override
    public List<String> getSchemas() throws Exception {
        if (VERSION_2.equals(version)) {
            return listBuckets();
        }
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : queryInfluxQl("SHOW DATABASES", 0).getRows()) {
            Object name = row.get("name");
            if (name != null) {
                names.add(String.valueOf(name));
            }
        }
        return names;
    }

    @Override
    public List<TableMeta> getTables(String schema, Integer limit, String search) throws Exception {
        String namespace = VERSION_2.equals(version) ? coalesce(trimToNull(schema), defaultBucket) : coalesce(trimToNull(schema), defaultDatabase);
        List<String> names;
        if (VERSION_2.equals(version)) {
            if (namespace == null) {
                return List.of();
            }
            names = fluxQueryValues(
                "import \"influxdata/influxdb/schema\"\nschema.measurements(bucket: " + fluxString(namespace) + ")"
            );
        } else {
            String ql = namespace == null ? "SHOW MEASUREMENTS" : "SHOW MEASUREMENTS ON " + influxQlIdentifier(namespace);
            names = new ArrayList<>();
            for (Map<String, Object> row : queryInfluxQl(ql, 0).getRows()) {
                Object name = row.get("name");
                if (name != null) {
                    names.add(String.valueOf(name));
                }
            }
        }

        String keyword = search == null ? null : search.toLowerCase();
        List<TableMeta> tables = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (keyword != null && !name.toLowerCase().contains(keyword)) {
                continue;
            }
            if (limit != null && tables.size() >= limit) {
                break;
            }
            TableMeta table = new TableMeta(name);
            table.setSchema(namespace);
            table.setDbType(InfluxDbDataSourceProvider.TYPE);
            tables.add(table);
        }
        return tables;
    }

    @Override
    public TableMeta getTableMeta(String schema, String table) throws Exception {
        if (VERSION_2.equals(version)) {
            String bucket = coalesce(trimToNull(schema), defaultBucket);
            if (bucket == null) {
                throw new IllegalArgumentException("未指定 Bucket：请在数据源中配置默认 Bucket，或在请求中提供 schema");
            }
            List<ColumnMeta> columns = new ArrayList<>();
            String fluxTags =
                "import \"influxdata/influxdb/schema\"\n" +
                "schema.measurementTagKeys(bucket: " + fluxString(bucket) + ", measurement: " + fluxString(table) + ")";
            String fluxFields =
                "import \"influxdata/influxdb/schema\"\n" +
                "schema.measurementFieldKeys(bucket: " + fluxString(bucket) + ", measurement: " + fluxString(table) + ")";
            for (String tag : fluxQueryValues(fluxTags)) {
                if (!isInternalFluxColumn(tag)) {
                    columns.add(new ColumnMeta(tag, AllDataType.STRING.getName()));
                }
            }
            for (String field : fluxQueryValues(fluxFields)) {
                if (!isInternalFluxColumn(field)) {
                    columns.add(new ColumnMeta(field, AllDataType.DOUBLE.getName()));
                }
            }
            TableMeta tableMeta = new TableMeta(table, columns);
            tableMeta.setSchema(bucket);
            tableMeta.setDbType(InfluxDbDataSourceProvider.TYPE);
            return tableMeta;
        }

        String database = coalesce(trimToNull(schema), defaultDatabase);
        String from = influxQlIdentifier(table);
        List<ColumnMeta> columns = new ArrayList<>();
        for (Map<String, Object> row : queryInfluxQl("SHOW FIELD KEYS FROM " + from, 0, database).getRows()) {
            Object fieldKey = row.get("fieldKey");
            if (fieldKey == null) {
                continue;
            }
            ColumnMeta column = new ColumnMeta(String.valueOf(fieldKey), mapInfluxFieldType(row.get("fieldType")));
            columns.add(column);
        }
        for (Map<String, Object> row : queryInfluxQl("SHOW TAG KEYS FROM " + from, 0, database).getRows()) {
            Object tagKey = row.get("tagKey");
            if (tagKey == null) {
                continue;
            }
            columns.add(new ColumnMeta(String.valueOf(tagKey), AllDataType.STRING.getName()));
        }
        TableMeta tableMeta = new TableMeta(table, columns);
        tableMeta.setSchema(database);
        tableMeta.setDbType(InfluxDbDataSourceProvider.TYPE);
        return tableMeta;
    }

    @Override
    public boolean testConnection() {
        try {
            request("GET", "/ping", null, null, null);
            return true;
        } catch (Exception pingFailed) {
            try {
                request("GET", "/health", null, null, "application/json");
                return true;
            } catch (Exception healthFailed) {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ query

    private QueryResult queryInfluxQl(String statement, int maxRows) throws Exception {
        return queryInfluxQl(statement, maxRows, null);
    }

    /**
     * 执行 InfluxQL。{@code database} 非空时覆盖默认数据库作为 {@code db} 查询参数，
     * 用于 SHOW FIELD/TAG KEYS 等需要显式库上下文的语句。
     */
    private QueryResult queryInfluxQl(String statement, int maxRows, String database) throws Exception {
        String db = database != null ? database : defaultDatabase;
        StringBuilder path = new StringBuilder("/query?q=").append(encode(statement));
        if (db != null) {
            path.append("&db=").append(encode(db));
        }
        String response = request("POST", path.toString(), null, null, "application/json");
        return parseInfluxQl(response, maxRows);
    }

    private QueryResult queryFlux(String statement, int maxRows) throws Exception {
        if (org == null) {
            throw new IllegalArgumentException("InfluxDB 2.x 需要配置 org：请在数据源额外参数中填写 influx.org");
        }
        String response = request(
            "POST",
            "/api/v2/query?org=" + encode(org),
            statement,
            "application/vnd.flux",
            "application/csv"
        );
        return parseFluxCsv(response, maxRows);
    }

    /**
     * 读取 Flux 查询结果中 {@code _value} 列的值列表（用于 schema 包等返回单列的结果）。
     */
    private List<String> fluxQueryValues(String flux) throws Exception {
        QueryResult result = queryFlux(flux, 0);
        int valueIndex = result.getColumns().indexOf("_value");
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : result.getRows()) {
            Object value = valueIndex >= 0 ? row.get("_value") : null;
            if (value != null && !String.valueOf(value).isBlank()) {
                values.add(String.valueOf(value));
            }
        }
        return values;
    }

    private List<String> listBuckets() throws Exception {
        // Buckets API 的 limit 取值范围为 1..100
        String path = "/api/v2/buckets?limit=100";
        if (org != null) {
            path += "&org=" + encode(org);
        }
        String response = request("GET", path, null, null, "application/json");
        JSONObject root = JSONUtil.parseObj(response);
        JSONArray buckets = root.getJSONArray("buckets");
        List<String> names = new ArrayList<>();
        if (buckets != null) {
            for (int i = 0; i < buckets.size(); i++) {
                String name = buckets.getJSONObject(i).getStr("name");
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    // ------------------------------------------------------------------ parsing

    private static QueryResult parseInfluxQl(String response, int maxRows) throws IOException {
        JSONObject root = JSONUtil.parseObj(response);
        JSONArray results = root.getJSONArray("results");
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                JSONObject result = results.getJSONObject(i);
                if (result == null) {
                    continue;
                }
                if (result.containsKey("error")) {
                    throw new IOException("InfluxDB 查询失败: " + result.get("error"));
                }
                JSONArray series = result.getJSONArray("series");
                if (series == null) {
                    continue;
                }
                for (int s = 0; s < series.size(); s++) {
                    JSONObject one = series.getJSONObject(s);
                    JSONArray columnNodes = one.getJSONArray("columns");
                    List<String> seriesColumns = new ArrayList<>();
                    if (columnNodes != null) {
                        for (int c = 0; c < columnNodes.size(); c++) {
                            seriesColumns.add(columnNodes.getStr(c));
                        }
                    }
                    for (String column : seriesColumns) {
                        if (!columns.contains(column)) {
                            columns.add(column);
                        }
                    }
                    JSONArray valueNodes = one.getJSONArray("values");
                    if (valueNodes == null) {
                        continue;
                    }
                    for (int v = 0; v < valueNodes.size(); v++) {
                        if (maxRows > 0 && rows.size() >= maxRows) {
                            break;
                        }
                        JSONArray rowNode = valueNodes.getJSONArray(v);
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int c = 0; c < seriesColumns.size(); c++) {
                            Object value = c < rowNode.size() ? normalizeJsonValue(rowNode.get(c)) : null;
                            row.put(seriesColumns.get(c), value);
                        }
                        rows.add(row);
                    }
                }
            }
        }
        return new QueryResult(columns, rows, rows.size());
    }

    /**
     * 解析 Flux 注解式 CSV：忽略 {@code #} 注释行，以每个结果块的表头行作为列定义，其余为数据行。
     */
    private static QueryResult parseFluxCsv(String csv, int maxRows) {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean expectHeader = true;
        boolean leadingEmptyColumn = false;
        if (csv == null) {
            return new QueryResult(columns, rows, 0);
        }
        for (String line : csv.split("\r?\n")) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                // #group / #datatype / #default 等注释行，其后的首个非注释行为表头
                expectHeader = true;
                continue;
            }
            List<String> fields = parseCsvLine(line);
            if (expectHeader) {
                columns = new ArrayList<>(fields);
                leadingEmptyColumn = !columns.isEmpty() && columns.get(0).isEmpty();
                if (leadingEmptyColumn) {
                    columns.remove(0);
                }
                expectHeader = false;
                continue;
            }
            if (maxRows > 0 && rows.size() >= maxRows) {
                continue;
            }
            if (leadingEmptyColumn && !fields.isEmpty()) {
                fields.remove(0);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                Object value = c < fields.size() ? normalizeCsvValue(fields.get(c)) : null;
                row.put(columns.get(c), value);
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, rows.size());
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    // ------------------------------------------------------------------ http

    private String request(String method, String path, String body, String contentType, String accept) throws Exception {
        HttpRequest.Builder builder = HttpRequest
            .newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(timeout);
        if (accept != null) {
            builder.header("Accept", accept);
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        String authorization = resolveAuthorization();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("InfluxDB 请求失败 [" + response.statusCode() + "] " + path + ": " + response.body());
        }
        return response.body();
    }

    private String resolveAuthorization() {
        if (token != null) {
            return "Token " + token;
        }
        if (username != null && !username.isBlank()) {
            String credentials = username + ":" + (password == null ? "" : password);
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    /** InfluxDB 内部伪列（schema 函数会把它们也作为 tag key 返回），展示时过滤掉。 */
    private static boolean isInternalFluxColumn(String name) {
        return (
            "_start".equals(name) || "_stop".equals(name) || "_time".equals(name) || "_field".equals(name) || "_measurement".equals(name)
        );
    }

    private static boolean isFlux(String statement) {
        String trimmed = statement.trim().toLowerCase();
        return (
            trimmed.contains("|>") ||
            trimmed.startsWith("from(") ||
            trimmed.startsWith("import ") ||
            trimmed.startsWith("option ") ||
            trimmed.startsWith("buckets(") ||
            trimmed.startsWith("schema.")
        );
    }

    private static String mapInfluxFieldType(Object fieldType) {
        if (fieldType == null) {
            return AllDataType.STRING.getName();
        }
        switch (String.valueOf(fieldType).toLowerCase()) {
            case "float":
                return AllDataType.DOUBLE.getName();
            case "integer":
                return AllDataType.BIGINT.getName();
            case "unsigned":
                return AllDataType.BIGINT.getName();
            case "boolean":
                return AllDataType.BOOLEAN.getName();
            default:
                return AllDataType.STRING.getName();
        }
    }

    private static Object normalizeJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return value;
    }

    private static Object normalizeCsvValue(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String resolveBaseUrl(DatasourceInfo info, Map<String, String> extra) {
        String scheme = extra.getOrDefault("influx.scheme", "http");
        String url = info.getUrl();
        String base;
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            base = url;
        } else if (url != null && url.startsWith("influxdb://")) {
            base = scheme + "://" + url.substring("influxdb://".length());
        } else if (url != null && !url.isBlank()) {
            base = scheme + "://" + url;
        } else if (info.getHostname() != null && !info.getHostname().isBlank()) {
            String port = info.getPort() != null && !info.getPort().isBlank() ? ":" + info.getPort() : "";
            base = scheme + "://" + info.getHostname() + port;
        } else {
            throw new IllegalArgumentException("InfluxDB 数据源缺少连接地址（url 或 hostname）");
        }
        return base.replaceAll("/+$", "");
    }

    private static String resolveVersion(String value, String org, String token) {
        if (value != null && !value.trim().isEmpty()) {
            return value.trim().startsWith("1") ? VERSION_1 : VERSION_2;
        }
        // 未显式指定版本时按凭据推断：配置了 Org/Token 视为 2.x（Flux），否则视为 1.x（InfluxQL）
        return (org != null || token != null) ? VERSION_2 : VERSION_1;
    }

    /** 生成 Flux 字符串字面量（双引号包裹并转义内部双引号与反斜杠）。 */
    private static String fluxString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 生成 InfluxQL 标识符（双引号包裹并转义内部双引号）。 */
    private static String influxQlIdentifier(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String coalesce(String first, String second) {
        return first != null ? first : second;
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
     * <p>InfluxDB 常以自签名证书暴露 https，平台默认忽略证书校验，避免连接测试/查询因证书不可信而失败。
     */
    private static HttpClient buildInsecureHttpClient(Duration timeout) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { TRUST_ALL_MANAGER }, new SecureRandom());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);
            return HttpClient.newBuilder().connectTimeout(timeout).sslContext(sslContext).sslParameters(sslParameters).build();
        } catch (Exception e) {
            throw new IllegalStateException("初始化 InfluxDB HTTP 客户端失败", e);
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
