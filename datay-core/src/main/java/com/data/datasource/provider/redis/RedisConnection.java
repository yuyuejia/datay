package com.data.datasource.provider.redis;

import com.alibaba.fastjson2.JSON;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.redisson.Redisson;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RStream;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

/**
 * Redis 的非 JDBC 连接实现（复用 Redisson 3.x）。
 *
 * <p>连接参数：
 * <ul>
 *   <li>{@code url}：{@code redis://host:port} 或 {@code rediss://host:port}，也可只写 {@code host:port}；</li>
 *   <li>{@code username}/{@code password}：ACL 或 Redis 6 之前的 requirepass 认证；</li>
 *   <li>{@code dbschema} 或 URL 路径：Redis DB 索引，默认 {@code 0}；</li>
 *   <li>{@code extraParams.redis.timeout_seconds}：连接与命令超时，默认 5 秒。</li>
 * </ul>
 *
 * <p>统一使用 {@link StringCodec}，Redis 中保存的是可读字符串 / JSON，方便与其它客户端互通。
 * 该连接对通用 SQL 查询只提供少量只读 Redis 命令，写入由 {@code RedisOutput} 组件负责。
 */
public class RedisConnection implements DataSourceConnection {

    private static final int DEFAULT_TIMEOUT_SECONDS = 5;
    private static final int DEFAULT_SCAN_COUNT = 1000;
    private static final int DEFAULT_MAX_ENTRIES = 1000;
    private static final Pattern COMMAND_TOKEN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|\\S+");
    /** Redisson 3.13.x 的 TYPE 解码器未包含 stream，改用 Lua 返回原始类型字符串。 */
    private static final String TYPE_SCRIPT = "return redis.status_reply(redis.call('TYPE', KEYS[1])['ok'])";

    private final RedissonClient client;
    private final int database;

    public RedisConnection(DatasourceInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("Redis 数据源配置不能为空");
        }
        RedisEndpoint endpoint = resolveEndpoint(info);
        this.database = endpoint.database();

        Config config = new Config();
        config.setCodec(new StringCodec());
        SingleServerConfig server = config.useSingleServer();
        server.setAddress(endpoint.address());
        server.setDatabase(database);
        server.setConnectionMinimumIdleSize(1);
        server.setConnectionPoolSize(10);
        server.setTimeout(endpoint.timeoutMillis());
        if (endpoint.username() != null && !endpoint.username().isBlank()) {
            server.setUsername(endpoint.username());
        }
        if (endpoint.password() != null && !endpoint.password().isEmpty()) {
            server.setPassword(endpoint.password());
        }
        this.client = Redisson.create(config);
    }

    /** 底层 Redisson 客户端，供输入/输出组件复用连接能力。 */
    public RedissonClient client() {
        return client;
    }

    /** 当前 Redis DB 索引。 */
    public int database() {
        return database;
    }

    /**
     * 扫描匹配 pattern 的 key。调用方需关闭返回的 Stream。
     */
    public Stream<String> scanKeys(String pattern, int count) {
        String match = pattern == null || pattern.trim().isEmpty() ? "*" : pattern.trim();
        int scanCount = count <= 0 ? DEFAULT_SCAN_COUNT : count;
        return client.getKeys().getKeysStreamByPattern(match, scanCount);
    }

    /**
     * 读取一个 key 并转换为统一的键级记录；key 不存在或类型无法识别时返回 {@code null}。
     *
     * @param key         Redis key
     * @param maxEntries  集合类型最大读取元素数，{@code <= 0} 时使用默认值
     * @param includeTtl  是否输出 TTL（秒，-1 表示永久，-2 表示 key 不存在）
     */
    public Map<String, Object> readRecord(String key, int maxEntries, boolean includeTtl) {
        String type = typeOf(key);
        if (type == null) {
            return null;
        }
        int limit = maxEntries <= 0 ? DEFAULT_MAX_ENTRIES : maxEntries;
        Object value = readValue(key, type, limit);
        if (value == null) {
            return null;
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("key", key);
        record.put("type", type);
        record.put("value", value);
        if (includeTtl) {
            record.put("ttl", ttlSeconds(key));
        }
        return record;
    }

    /**
     * 返回 Redis key 的类型：string/hash/list/set/zset/stream；key 不存在时返回 {@code null}。
     */
    public String typeOf(String key) {
        try {
            String raw = client
                .getScript(StringCodec.INSTANCE)
                .eval(
                    RScript.Mode.READ_ONLY,
                    TYPE_SCRIPT,
                    RScript.ReturnType.STATUS,
                    Collections.singletonList(key)
                );
            return normalizeType(raw);
        } catch (RuntimeException e) {
            // 极少数禁用 Lua 的 Redis 环境回退到 Redisson TYPE；其解码器不认识 stream，需要按异常信息兜底
            try {
                RType type = client.getKeys().getType(key);
                if (type == null) {
                    return null;
                }
                switch (type) {
                    case MAP:
                        return "hash";
                    case LIST:
                        return "list";
                    case SET:
                        return "set";
                    case ZSET:
                        return "zset";
                    case OBJECT:
                        return "string";
                    default:
                        return null;
                }
            } catch (RuntimeException fallbackError) {
                if (containsMessage(fallbackError, "stream")) {
                    return "stream";
                }
                throw fallbackError;
            }
        }
    }

    private static String normalizeType(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "none".equals(value)) {
            return null;
        }
        return switch (value) {
            case "string", "hash", "list", "set", "zset", "stream" -> value;
            default -> null;
        };
    }

    private static boolean containsMessage(Throwable error, String keyword) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** key 的剩余存活时间，单位秒；-1 永久，-2 不存在。 */
    public long ttlSeconds(String key) {
        long pttl = client.<String>getBucket(key, StringCodec.INSTANCE).remainTimeToLive();
        if (pttl < 0) {
            return pttl;
        }
        return pttl / 1000;
    }

    // ------------------------------------------------------------------ 写能力（供 ETL 组件复用）

    /** 在批量命令中追加 DEL。 */
    public void queueDelete(RBatch batch, String key) {
        batch.getKeys().deleteAsync(key);
    }

    /** 在批量命令中追加 SET。 */
    public void queueString(RBatch batch, String key, String value) {
        batch.<String>getBucket(key, StringCodec.INSTANCE).setAsync(value);
    }

    /** 在批量命令中追加 HSET。 */
    public void queueHash(RBatch batch, String key, Map<String, String> fields) {
        batch.<String, String>getMap(key, StringCodec.INSTANCE).putAllAsync(fields);
    }

    /** 在批量命令中追加 RPUSH。 */
    public void queueList(RBatch batch, String key, Collection<String> values) {
        batch.<String>getList(key, StringCodec.INSTANCE).addAllAsync(values);
    }

    /** 在批量命令中追加 SADD。 */
    public void queueSet(RBatch batch, String key, Collection<String> values) {
        batch.<String>getSet(key, StringCodec.INSTANCE).addAllAsync(values);
    }

    /** 在批量命令中追加 ZADD。 */
    public void queueZSet(RBatch batch, String key, Map<String, Double> members) {
        batch.<String>getScoredSortedSet(key, StringCodec.INSTANCE).addAllAsync(members);
    }

    /** 在批量命令中追加 XADD。 */
    public void queueStream(RBatch batch, String key, StreamMessageId id, Map<String, String> fields) {
        if (id == null) {
            batch.<String, String>getStream(key, StringCodec.INSTANCE).addAllAsync(fields);
        } else {
            batch.<String, String>getStream(key, StringCodec.INSTANCE).addAllAsync(id, fields);
        }
    }

    /** 在批量命令中追加 EXPIRE；ttlSeconds <= 0 时忽略。 */
    public void queueExpire(RBatch batch, String key, long ttlSeconds) {
        if (ttlSeconds > 0) {
            batch.<String>getBucket(key, StringCodec.INSTANCE).expireAsync(ttlSeconds, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------ DataSourceConnection

    @Override
    public QueryResult executeQuery(String statement, List<Object> params, int maxRows) {
        if (statement == null || statement.trim().isEmpty()) {
            throw new IllegalArgumentException("Redis 查询语句不能为空");
        }
        List<String> tokens = tokenize(statement);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Redis 查询语句不能为空");
        }
        String command = tokens.get(0).toUpperCase(Locale.ROOT);
        int limit = maxRows > 0 ? maxRows : 1000;

        switch (command) {
            case "PING":
                return singleValueResult("result", "PONG");
            case "GET":
                requireArgs(tokens, 2, "GET key");
                return getResult(requireArg(tokens, 1, "GET key"));
            case "HGETALL":
                requireArgs(tokens, 2, "HGETALL key");
                return hgetAllResult(requireArg(tokens, 1, "HGETALL key"), limit);
            case "LRANGE":
                requireArgs(tokens, 4, "LRANGE key start stop");
                return lrangeResult(
                    requireArg(tokens, 1, "LRANGE key start stop"),
                    parseInt(tokens.get(2), 0),
                    parseInt(tokens.get(3), -1),
                    limit
                );
            case "SMEMBERS":
                requireArgs(tokens, 2, "SMEMBERS key");
                return smembersResult(requireArg(tokens, 1, "SMEMBERS key"), limit);
            case "ZRANGE":
                requireArgs(tokens, 4, "ZRANGE key start stop [WITHSCORES]");
                return zrangeResult(
                    requireArg(tokens, 1, "ZRANGE key start stop [WITHSCORES]"),
                    parseInt(tokens.get(2), 0),
                    parseInt(tokens.get(3), -1),
                    tokens.stream().anyMatch(token -> "WITHSCORES".equalsIgnoreCase(token)),
                    limit
                );
            case "XRANGE":
                requireArgs(tokens, 4, "XRANGE key start end [COUNT n]");
                return xrangeResult(
                    requireArg(tokens, 1, "XRANGE key start end"),
                    tokens.get(2),
                    tokens.get(3),
                    resolveCountOption(tokens, limit),
                    limit
                );
            case "TYPE":
                requireArgs(tokens, 2, "TYPE key");
                return typeResult(requireArg(tokens, 1, "TYPE key"));
            case "TTL":
                requireArgs(tokens, 2, "TTL key");
                return ttlResult(requireArg(tokens, 1, "TTL key"));
            case "SCAN":
            case "KEYS":
                return scanResult(tokens, command, limit);
            default:
                throw new IllegalArgumentException(
                    "Redis 数据源暂不支持该查询命令: " + command
                        + "，支持 GET/HGETALL/LRANGE/SMEMBERS/ZRANGE/XRANGE/TYPE/TTL/SCAN/KEYS/PING"
                );
        }
    }

    @Override
    public int executeUpdate(String statement, List<Object> params) {
        throw new UnsupportedOperationException("Redis 数据源为只读查询，写入请使用 RedisOutput 组件");
    }

    @Override
    public List<String> getSchemas() {
        return List.of(String.valueOf(database));
    }

    @Override
    public List<TableMeta> getTables(String schema, Integer limit, String search) {
        String keyword = search == null || search.trim().isEmpty() ? null : search.trim();
        String pattern = keyword == null ? "*" : "*" + escapeGlob(keyword) + "*";
        List<TableMeta> tables = new ArrayList<>();
        int max = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        try (Stream<String> keys = scanKeys(pattern, DEFAULT_SCAN_COUNT)) {
            Iterator<String> iterator = keys.iterator();
            while (iterator.hasNext() && tables.size() < max) {
                String key = iterator.next();
                TableMeta table = new TableMeta(key);
                table.setSchema(String.valueOf(database));
                table.setDbType(RedisDataSourceProvider.TYPE);
                tables.add(table);
            }
        }
        return tables;
    }

    @Override
    public TableMeta getTableMeta(String schema, String table) {
        String type = typeOf(table);
        if (type == null) {
            type = "string";
        }
        List<ColumnMeta> columns = new ArrayList<>();
        switch (type) {
            case "hash":
                columns.add(new ColumnMeta("field", AllDataType.STRING.getName()));
                columns.add(new ColumnMeta("value", AllDataType.STRING.getName()));
                break;
            case "list":
                columns.add(new ColumnMeta("index", AllDataType.BIGINT.getName()));
                columns.add(new ColumnMeta("value", AllDataType.STRING.getName()));
                break;
            case "set":
                columns.add(new ColumnMeta("value", AllDataType.STRING.getName()));
                break;
            case "zset":
                columns.add(new ColumnMeta("member", AllDataType.STRING.getName()));
                columns.add(new ColumnMeta("score", AllDataType.DOUBLE.getName()));
                break;
            case "stream":
                columns.add(new ColumnMeta("id", AllDataType.STRING.getName()));
                columns.add(new ColumnMeta("field", AllDataType.STRING.getName()));
                columns.add(new ColumnMeta("value", AllDataType.STRING.getName()));
                break;
            default:
                columns.add(new ColumnMeta("key", AllDataType.STRING.getName()));
                columns.add(new ColumnMeta("value", AllDataType.STRING.getName()));
                columns.add(new ColumnMeta("ttl", AllDataType.BIGINT.getName()));
                break;
        }
        TableMeta tableMeta = new TableMeta(table, columns);
        tableMeta.setSchema(String.valueOf(database));
        tableMeta.setDbType(RedisDataSourceProvider.TYPE);
        tableMeta.setTableType(type);
        return tableMeta;
    }

    @Override
    public boolean testConnection() {
        client.getKeys().count();
        return true;
    }

    @Override
    public void close() {
        client.shutdown();
    }

    // ------------------------------------------------------------------ 查询实现

    private Object readValue(String key, String type, int limit) {
        switch (type) {
            case "hash": {
                Map<String, String> values = client.<String, String>getMap(key, StringCodec.INSTANCE).readAllMap();
                if (values.size() > limit) {
                    Map<String, String> limited = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        if (limited.size() >= limit) {
                            break;
                        }
                        limited.put(entry.getKey(), entry.getValue());
                    }
                    values = limited;
                }
                return JSON.toJSONString(values);
            }
            case "list": {
                RList<String> list = client.getList(key, StringCodec.INSTANCE);
                return JSON.toJSONString(list.range(0, Math.max(limit - 1, 0)));
            }
            case "set": {
                RSet<String> set = client.getSet(key, StringCodec.INSTANCE);
                try (Stream<String> stream = set.stream(DEFAULT_SCAN_COUNT)) {
                    return JSON.toJSONString(stream.limit(limit).collect(Collectors.toList()));
                }
            }
            case "zset": {
                RScoredSortedSet<String> zset = client.getScoredSortedSet(key, StringCodec.INSTANCE);
                Collection<ScoredEntry<String>> entries = zset.entryRange(0, Math.max(limit - 1, 0));
                List<Map<String, Object>> values = new ArrayList<>();
                for (ScoredEntry<String> entry : entries) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("member", entry.getValue());
                    item.put("score", entry.getScore());
                    values.add(item);
                }
                return JSON.toJSONString(values);
            }
            case "stream": {
                RStream<String, String> stream = client.getStream(key, StringCodec.INSTANCE);
                Map<StreamMessageId, Map<String, String>> entries = stream.range(
                    limit,
                    StreamMessageId.MIN,
                    StreamMessageId.MAX
                );
                List<Map<String, Object>> values = new ArrayList<>();
                for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", entry.getKey().toString());
                    item.put("fields", entry.getValue());
                    values.add(item);
                }
                return JSON.toJSONString(values);
            }
            default:
                return client.<String>getBucket(key, StringCodec.INSTANCE).get();
        }
    }

    private QueryResult getResult(String key) {
        RBucket<String> bucket = client.getBucket(key, StringCodec.INSTANCE);
        String value = bucket.get();
        if (value == null) {
            return QueryResult.empty();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("value", value);
        return new QueryResult(List.of("key", "value"), List.of(row), 1);
    }

    private QueryResult hgetAllResult(String key, int limit) {
        Map<String, String> values = client.<String, String>getMap(key, StringCodec.INSTANCE).readAllMap();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (rows.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field", entry.getKey());
            row.put("value", entry.getValue());
            rows.add(row);
        }
        return new QueryResult(List.of("field", "value"), rows, rows.size());
    }

    private QueryResult lrangeResult(String key, int start, int stop, int limit) {
        List<String> values = client.<String>getList(key, StringCodec.INSTANCE).range(start, stop);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < values.size() && rows.size() < limit; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i);
            row.put("value", values.get(i));
            rows.add(row);
        }
        return new QueryResult(List.of("index", "value"), rows, rows.size());
    }

    private QueryResult smembersResult(String key, int limit) {
        Set<String> values = client.<String>getSet(key, StringCodec.INSTANCE).readAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String value : values) {
            if (rows.size() >= limit) {
                break;
            }
            rows.add(Collections.singletonMap("value", value));
        }
        return new QueryResult(List.of("value"), rows, rows.size());
    }

    private QueryResult zrangeResult(String key, int start, int stop, boolean withScores, int limit) {
        Collection<ScoredEntry<String>> entries = client
            .<String>getScoredSortedSet(key, StringCodec.INSTANCE)
            .entryRange(start, stop);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = withScores ? List.of("member", "score") : List.of("member");
        for (ScoredEntry<String> entry : entries) {
            if (rows.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("member", entry.getValue());
            if (withScores) {
                row.put("score", entry.getScore());
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, rows.size());
    }

    private QueryResult xrangeResult(String key, String start, String end, int count, int limit) {
        RStream<String, String> stream = client.getStream(key, StringCodec.INSTANCE);
        Map<StreamMessageId, Map<String, String>> entries = stream.range(
            Math.min(count, limit),
            parseStreamId(start, StreamMessageId.MIN),
            parseStreamId(end, StreamMessageId.MAX)
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
            for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                if (rows.size() >= limit) {
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", entry.getKey().toString());
                row.put("field", field.getKey());
                row.put("value", field.getValue());
                rows.add(row);
            }
        }
        return new QueryResult(List.of("id", "field", "value"), rows, rows.size());
    }

    private QueryResult typeResult(String key) {
        String type = typeOf(key);
        if (type == null) {
            return QueryResult.empty();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("type", type);
        return new QueryResult(List.of("key", "type"), List.of(row), 1);
    }

    private QueryResult ttlResult(String key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("ttl", ttlSeconds(key));
        return new QueryResult(List.of("key", "ttl"), List.of(row), 1);
    }

    private QueryResult scanResult(List<String> tokens, String command, int limit) {
        String pattern = "*";
        int scanCount = DEFAULT_SCAN_COUNT;
        int startIndex = "SCAN".equals(command) ? 2 : 1;
        for (int i = startIndex; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("MATCH".equalsIgnoreCase(token) && i + 1 < tokens.size()) {
                pattern = tokens.get(++i);
            } else if ("COUNT".equalsIgnoreCase(token) && i + 1 < tokens.size()) {
                scanCount = parseInt(tokens.get(++i), DEFAULT_SCAN_COUNT);
            } else if ("KEYS".equals(command) && i == 1) {
                pattern = token;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Stream<String> keys = scanKeys(pattern, scanCount)) {
            Iterator<String> iterator = keys.iterator();
            while (iterator.hasNext() && rows.size() < limit) {
                String key = iterator.next();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", key);
                row.put("type", typeOf(key));
                rows.add(row);
            }
        }
        return new QueryResult(List.of("key", "type"), rows, rows.size());
    }

    private QueryResult singleValueResult(String column, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(column, value);
        return new QueryResult(List.of(column), List.of(row), 1);
    }

    // ------------------------------------------------------------------ URL / 参数解析

    private static RedisEndpoint resolveEndpoint(DatasourceInfo info) {
        String rawUrl = trimToNull(info.getUrl());
        String scheme = "redis";
        String uriHost = null;
        String uriUser = null;
        String uriPassword = null;
        Integer uriPort = null;
        Integer uriDatabase = null;

        if (rawUrl != null) {
            String normalized = rawUrl.contains("://") ? rawUrl : "redis://" + rawUrl;
            URI uri = URI.create(normalized);
            if (uri.getScheme() != null) {
                scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            }
            if (!"redis".equals(scheme) && !"rediss".equals(scheme)) {
                throw new IllegalArgumentException("Redis 数据源 URL 仅支持 redis:// 或 rediss://: " + rawUrl);
            }
            uriHost = trimToNull(uri.getHost());
            uriPort = uri.getPort() > 0 ? uri.getPort() : null;
            if (uri.getUserInfo() != null) {
                String[] credentials = uri.getUserInfo().split(":", 2);
                uriUser = trimToNull(credentials[0]);
                uriPassword = credentials.length > 1 ? credentials[1] : null;
            }
            String path = trimToNull(uri.getPath());
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            uriDatabase = parseDatabase(path);
        }

        String host = trimToNull(info.getHostname());
        if (host == null) {
            host = uriHost;
        }
        if (host == null) {
            throw new IllegalArgumentException("Redis 数据源缺少主机地址（hostname 或 url）");
        }
        int port = parsePort(trimToNull(info.getPort()), uriPort == null ? 6379 : uriPort);
        String username = trimToNull(info.getUsername());
        if (username == null) {
            username = uriUser;
        }
        String password = info.getPassword();
        if (password == null) {
            password = uriPassword;
        }
        int database = resolveDatabase(info.getDbschema(), uriDatabase);

        Map<String, String> extra = info.getExtraParams() == null ? Collections.emptyMap() : info.getExtraParams();
        int timeoutSeconds = parseInt(extra.get("redis.timeout_seconds"), DEFAULT_TIMEOUT_SECONDS);
        int timeoutMillis = Math.max(timeoutSeconds, 1) * 1000;

        String address = scheme + "://" + host + ":" + port;
        return new RedisEndpoint(address, database, username, password, timeoutMillis);
    }

    private static int resolveDatabase(String schema, Integer uriDatabase) {
        Integer schemaDatabase = parseDatabase(schema);
        if (schemaDatabase != null) {
            return schemaDatabase;
        }
        return uriDatabase == null ? 0 : uriDatabase;
    }

    private static Integer parseDatabase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int database = Integer.parseInt(value.trim());
            return database >= 0 ? database : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parsePort(String port, int defaultValue) {
        if (port == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(port);
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> tokenize(String statement) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = COMMAND_TOKEN.matcher(statement.trim());
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                tokens.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                tokens.add(matcher.group(2));
            } else {
                tokens.add(matcher.group());
            }
        }
        return tokens;
    }

    private static void requireArgs(List<String> tokens, int size, String usage) {
        if (tokens.size() < size) {
            throw new IllegalArgumentException("Redis 命令参数不足，用法: " + usage);
        }
    }

    private static String requireArg(List<String> tokens, int index, String usage) {
        if (index >= tokens.size() || tokens.get(index).isBlank()) {
            throw new IllegalArgumentException("Redis 命令参数不足，用法: " + usage);
        }
        return tokens.get(index);
    }

    private static int resolveCountOption(List<String> tokens, int defaultCount) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            if ("COUNT".equalsIgnoreCase(tokens.get(i))) {
                return parseInt(tokens.get(i + 1), defaultCount);
            }
        }
        return defaultCount;
    }

    private static StreamMessageId parseStreamId(String value, StreamMessageId defaultValue) {
        String text = trimToNull(value);
        if (text == null || "-".equals(text) || "+".equals(text) || "$".equals(text) || "*".equals(text)) {
            return defaultValue;
        }
        String[] parts = text.split("-", 2);
        try {
            long id0 = Long.parseLong(parts[0]);
            long id1 = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
            return new StreamMessageId(id0, id1);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String escapeGlob(String value) {
        return value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?").replace("[", "\\[").replace("]", "\\]");
    }

    private record RedisEndpoint(String address, int database, String username, String password, int timeoutMillis) {}
}
