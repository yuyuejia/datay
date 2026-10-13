package com.data.datasource.provider.mongodb;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.IndexMeta;
import com.data.metadata.TableMeta;
import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.connection.ClusterConnectionMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

/**
 * MongoDB 的非 JDBC 连接实现（基于官方 {@code mongodb-driver-sync}）。
 *
 * <p>连接参数：{@code url} 支持 {@code mongodb://host:port/db} 与 {@code mongodb+srv://...}；
 * 认证使用用户名/密码，{@code extraParams} 支持 {@code authSource}、{@code replicaSet}、
 * {@code tls}、{@code directConnection}。
 *
 * <p>查询以 JSON 信封提交：
 * <ul>
 *   <li>{@code {"collection":"users","filter":{...},"projection":{...},"sort":{...},"limit":100}}</li>
 *   <li>{@code {"collection":"users","pipeline":[{...}]}}（聚合）</li>
 * </ul>
 * 可选 {@code "database":"db"} 覆盖连接默认库。
 *
 * <p>该连接为只读（{@link #executeUpdate} 不支持写），数据写入由 ETL 输出组件通过
 * {@link #upsertDocuments} 完成。
 */
public class MongoConnection implements DataSourceConnection {

    private static final int DEFAULT_SAMPLE_SIZE = 100;
    private static final Set<String> SYSTEM_DATABASES = Set.of("admin", "local", "config");

    private final MongoClient client;
    private final String defaultDatabase;

    public MongoConnection(DatasourceInfo info) {
        Map<String, String> extra = info.getExtraParams() == null ? Collections.emptyMap() : info.getExtraParams();
        String uri = resolveUri(info);
        ConnectionString connectionString = new ConnectionString(uri);

        com.mongodb.MongoClientSettings.Builder builder = com.mongodb.MongoClientSettings
            .builder()
            .applyConnectionString(connectionString);

        String database = connectionString.getDatabase() != null ? connectionString.getDatabase() : info.getDbschema();
        this.defaultDatabase = database == null ? "" : database;

        if (info.getUsername() != null && !info.getUsername().isBlank()) {
            String authSource = extra.get("authSource");
            if (authSource == null || authSource.isBlank()) {
                authSource = !defaultDatabase.isBlank() ? defaultDatabase : "admin";
            }
            builder.credential(
                MongoCredential.createCredential(info.getUsername(), authSource, toChars(info.getPassword()))
            );
        }

        String replicaSet = extra.get("replicaSet");
        if (replicaSet != null && !replicaSet.isBlank()) {
            builder.applyToClusterSettings(b -> b.requiredReplicaSetName(replicaSet.trim()));
        }
        if (isTrue(extra.get("directConnection"))) {
            builder.applyToClusterSettings(b -> b.mode(ClusterConnectionMode.SINGLE));
        }
        if (isTrue(extra.get("tls")) || isTrue(extra.get("ssl"))) {
            builder.applyToSslSettings(b -> b.enabled(true));
        }

        this.client = MongoClients.create(builder.build());
    }

    /** 连接默认数据库（来自 URL 或 dbschema），可能为空。 */
    public String defaultDatabase() {
        return defaultDatabase;
    }

    // ------------------------------------------------------------------ CDC 复用能力

    /** 暴露底层 MongoClient，供 Change Streams 等 CDC 场景复用同一连接配置。 */
    public MongoClient mongoClient() {
        return client;
    }

    /** 解析目标数据库名：显式传入优先，否则回退连接默认库。 */
    public String databaseName(String database) {
        return resolveDatabase(database);
    }

    /** 获取集合句柄（供 Change Streams / 游标读取使用）。 */
    public MongoCollection<Document> collection(String database, String collectionName) {
        return client.getDatabase(resolveDatabase(database)).getCollection(collectionName);
    }

    /** 列出数据库下所有集合名（排除 system.*）。 */
    public List<String> listCollections(String database) {
        List<String> names = new ArrayList<>();
        for (String name : client.getDatabase(resolveDatabase(database)).listCollectionNames()) {
            if (name.startsWith("system.")) {
                continue;
            }
            names.add(name);
        }
        return names;
    }

    // ------------------------------------------------------------------ DataSourceConnection

    @Override
    public QueryResult executeQuery(String statement, List<Object> params, int maxRows) {
        if (statement == null || statement.trim().isEmpty()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }
        JSONObject envelope = JSON.parseObject(statement.trim());
        if (envelope == null) {
            throw new IllegalArgumentException("查询语句必须是 JSON 对象，例如 {\"collection\":\"users\",\"filter\":{}}");
        }
        String database = envelope.getString("database");
        String collection = envelope.getString("collection");
        if (collection == null || collection.isBlank()) {
            throw new IllegalArgumentException("查询语句缺少 collection");
        }

        int limit = envelope.containsKey("limit") ? envelope.getIntValue("limit", 0) : 0;
        if (maxRows > 0 && (limit <= 0 || limit > maxRows)) {
            limit = maxRows;
        }

        List<Map<String, Object>> rows;
        if (envelope.containsKey("pipeline")) {
            rows = aggregate(database, collection, toMapList(envelope.getJSONArray("pipeline")), limit);
        } else {
            rows = findDocuments(
                database,
                collection,
                toMap(envelope.get("filter")),
                toMap(envelope.get("projection")),
                toMap(envelope.get("sort")),
                limit
            );
        }

        LinkedHashSet<String> columnSet = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            columnSet.addAll(row.keySet());
        }
        List<String> columns = new ArrayList<>(columnSet);
        List<Map<String, Object>> orderedRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            for (String column : columns) {
                ordered.put(column, row.get(column));
            }
            orderedRows.add(ordered);
        }
        return new QueryResult(columns, orderedRows, orderedRows.size());
    }

    @Override
    public int executeUpdate(String statement, List<Object> params) {
        throw new UnsupportedOperationException("MongoDB 数据源为只读，不支持写操作，请使用 MongoDB 输出组件");
    }

    @Override
    public List<String> getSchemas() {
        List<String> databases = new ArrayList<>();
        for (String name : client.listDatabaseNames()) {
            if (!SYSTEM_DATABASES.contains(name)) {
                databases.add(name);
            }
        }
        return databases;
    }

    @Override
    public List<TableMeta> getTables(String schema, Integer limit, String search) {
        MongoDatabase database = client.getDatabase(resolveDatabase(schema));
        String keyword = search == null ? null : search.toLowerCase();
        List<TableMeta> tables = new ArrayList<>();
        for (String name : database.listCollectionNames()) {
            if (keyword != null && !name.toLowerCase().contains(keyword)) {
                continue;
            }
            if (limit != null && tables.size() >= limit) {
                break;
            }
            TableMeta table = new TableMeta(name);
            table.setSchema(resolveDatabase(schema));
            table.setDbType(MongoDataSourceProvider.TYPE);
            tables.add(table);
        }
        return tables;
    }

    @Override
    public TableMeta getTableMeta(String schema, String table) {
        return sampleTableMeta(schema, table, DEFAULT_SAMPLE_SIZE);
    }

    @Override
    public TableMeta getTableDetail(String schema, String table) {
        TableMeta tableMeta = sampleTableMeta(schema, table, DEFAULT_SAMPLE_SIZE);
        List<IndexMeta> indexes = new ArrayList<>();
        try {
            MongoCollection<Document> collection = client.getDatabase(resolveDatabase(schema)).getCollection(table);
            for (Document index : collection.listIndexes()) {
                IndexMeta indexMeta = new IndexMeta(index.getString("name"));
                Document key = index.get("key", Document.class);
                if (key != null) {
                    for (String field : key.keySet()) {
                        indexMeta.addColumn(field);
                    }
                }
                indexMeta.setUnique(Boolean.TRUE.equals(index.getBoolean("unique")));
                indexMeta.setType("index");
                indexes.add(indexMeta);
            }
        } catch (Exception ignored) {
            // 集合不存在或无权限读取索引时忽略
        }
        tableMeta.setIndexes(indexes);
        return tableMeta;
    }

    @Override
    public boolean testConnection() {
        try {
            client.getDatabase("admin").runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        client.close();
    }

    // ------------------------------------------------------------------ 读写能力（供 ETL 组件复用）

    /**
     * 按过滤/投影/排序查询文档，返回规范化后的行（BSON 值转为 JSON 友好形式）。
     */
    public List<Map<String, Object>> findDocuments(
        String database,
        String collection,
        Map<String, Object> filter,
        Map<String, Object> projection,
        Map<String, Object> sort,
        int limit
    ) {
        MongoCollection<Document> coll = client.getDatabase(resolveDatabase(database)).getCollection(collection);
        FindIterable<Document> iterable = coll.find(toDocument(filter));
        if (projection != null && !projection.isEmpty()) {
            iterable = iterable.projection(toDocument(projection));
        }
        if (sort != null && !sort.isEmpty()) {
            iterable = iterable.sort(toDocument(sort));
        }
        if (limit > 0) {
            iterable = iterable.limit(limit);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Document doc : iterable) {
            rows.add(normalizeDocument(doc));
        }
        return rows;
    }

    /**
     * 执行聚合管道，返回规范化后的行。
     */
    public List<Map<String, Object>> aggregate(
        String database,
        String collection,
        List<Map<String, Object>> pipeline,
        int limit
    ) {
        MongoCollection<Document> coll = client.getDatabase(resolveDatabase(database)).getCollection(collection);
        List<Document> stages = new ArrayList<>();
        if (pipeline != null) {
            for (Map<String, Object> stage : pipeline) {
                stages.add(toDocument(stage));
            }
        }
        if (limit > 0) {
            stages.add(new Document("$limit", limit));
        }
        AggregateIterable<Document> iterable = coll.aggregate(stages);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Document doc : iterable) {
            rows.add(normalizeDocument(doc));
        }
        return rows;
    }

    /**
     * 采样集合文档并推断字段结构（MongoDB 无固定 schema，按采样文档合并字段类型，冲突时记为 JSON）。
     */
    public TableMeta sampleTableMeta(String database, String collection, int sampleSize) {
        MongoCollection<Document> coll = client.getDatabase(resolveDatabase(database)).getCollection(collection);
        List<Document> docs = coll
            .find()
            .limit(sampleSize <= 0 ? DEFAULT_SAMPLE_SIZE : sampleSize)
            .into(new ArrayList<>());
        return buildTableMeta(resolveDatabase(database), collection, docs);
    }

    /**
     * 删除集合（用于 overwrite 写模式）。
     */
    public void dropCollection(String database, String collection) {
        client.getDatabase(resolveDatabase(database)).getCollection(collection).drop();
    }

    /**
     * 批量写入文档。{@code idField} 非空时以该字段值作为文档 {@code _id} 做 upsert（重复写入则覆盖），
     * 否则直接插入。
     *
     * @return 写入（含覆盖）的文档数
     */
    public int upsertDocuments(String database, String collection, List<Map<String, Object>> documents, String idField) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        MongoCollection<Document> coll = client.getDatabase(resolveDatabase(database)).getCollection(collection);
        if (idField != null && !idField.isBlank()) {
            List<WriteModel<Document>> operations = new ArrayList<>(documents.size());
            for (Map<String, Object> document : documents) {
                Document doc = toDocument(document);
                Object idValue = document.get(idField);
                if (idValue == null) {
                    idValue = doc.get(idField);
                }
                if (idValue == null) {
                    operations.add(new com.mongodb.client.model.InsertOneModel<>(doc));
                } else {
                    idValue = normalizeBson(idValue);
                    doc.put("_id", idValue);
                    operations.add(
                        new ReplaceOneModel<>(new Document("_id", idValue), doc, new ReplaceOptions().upsert(true))
                    );
                }
            }
            coll.bulkWrite(operations, new BulkWriteOptions().ordered(false));
        } else {
            List<Document> docs = new ArrayList<>(documents.size());
            for (Map<String, Object> document : documents) {
                docs.add(toDocument(document));
            }
            coll.insertMany(docs);
        }
        return documents.size();
    }

    // ------------------------------------------------------------------ 内部工具

    private String resolveDatabase(String database) {
        if (database != null && !database.isBlank()) {
            return database;
        }
        if (!defaultDatabase.isBlank()) {
            return defaultDatabase;
        }
        throw new IllegalArgumentException("未指定数据库：请在数据源中配置数据库名，或在查询信封中提供 database");
    }

    private static TableMeta buildTableMeta(String schema, String collection, List<Document> docs) {
        Map<String, String> types = new LinkedHashMap<>();
        for (Document doc : docs) {
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String type = bsonTypeName(entry.getValue());
                types.merge(entry.getKey(), type, (a, b) -> a.equals(b) ? a : AllDataType.JSON.getName());
            }
        }
        List<ColumnMeta> columns = new ArrayList<>();
        for (Map.Entry<String, String> entry : types.entrySet()) {
            ColumnMeta column = new ColumnMeta(entry.getKey(), entry.getValue());
            if (AllDataType.VARCHAR.getName().equals(entry.getValue())) {
                column.setLength(255);
            }
            if ("_id".equals(entry.getKey())) {
                column.setPrimaryKey(true);
                column.setNullable(false);
            }
            columns.add(column);
        }
        TableMeta tableMeta = new TableMeta(collection, columns);
        tableMeta.setSchema(schema);
        tableMeta.setDbType(MongoDataSourceProvider.TYPE);
        return tableMeta;
    }

    private static String bsonTypeName(Object value) {
        if (value == null) {
            return AllDataType.VARCHAR.getName();
        }
        if (value instanceof String || value instanceof ObjectId) {
            return AllDataType.VARCHAR.getName();
        }
        if (value instanceof Boolean) {
            return AllDataType.BOOLEAN.getName();
        }
        if (value instanceof Integer) {
            return AllDataType.INT.getName();
        }
        if (value instanceof Long) {
            return AllDataType.BIGINT.getName();
        }
        if (value instanceof Double || value instanceof Float) {
            return AllDataType.DOUBLE.getName();
        }
        if (value instanceof Decimal128 || value instanceof BigDecimal) {
            return AllDataType.DECIMAL.getName();
        }
        if (value instanceof java.util.Date) {
            return AllDataType.TIMESTAMP.getName();
        }
        if (value instanceof Binary || value instanceof byte[]) {
            return AllDataType.BLOB.getName();
        }
        if (value instanceof Document || value instanceof Map || value instanceof List) {
            return AllDataType.JSON.getName();
        }
        return AllDataType.VARCHAR.getName();
    }

    /** 将 BSON 文档规范化为 JSON 友好的有序 Map（供 CDC 事件与快照复用）。 */
    public static Map<String, Object> normalizeDocument(Document document) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            row.put(entry.getKey(), normalizeBson(entry.getValue()));
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeBson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId) {
            return ((ObjectId) value).toHexString();
        }
        if (value instanceof Decimal128) {
            try {
                return ((Decimal128) value).bigDecimalValue();
            } catch (ArithmeticException e) {
                return value.toString();
            }
        }
        if (value instanceof Binary) {
            return Base64.getEncoder().encodeToString(((Binary) value).getData());
        }
        if (value instanceof byte[]) {
            return Base64.getEncoder().encodeToString((byte[]) value);
        }
        if (value instanceof java.util.Date) {
            return Instant.ofEpochMilli(((java.util.Date) value).getTime()).toString();
        }
        if (value instanceof Document) {
            return normalizeDocument((Document) value);
        }
        if (value instanceof Map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                nested.put(String.valueOf(entry.getKey()), normalizeBson(entry.getValue()));
            }
            return nested;
        }
        if (value instanceof List) {
            List<Object> nested = new ArrayList<>();
            for (Object item : (List<?>) value) {
                nested.add(normalizeBson(item));
            }
            return nested;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        JSONObject json = JSON.parseObject(JSON.toJSONString(value));
        return json == null ? null : new LinkedHashMap<>(json);
    }

    private static List<Map<String, Object>> toMapList(JSONArray array) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            list.add(toMap(array.getJSONObject(i)));
        }
        return list;
    }

    private static Document toDocument(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new Document();
        }
        return Document.parse(JSON.toJSONString(map));
    }

    private static String resolveUri(DatasourceInfo info) {
        String url = info.getUrl();
        if (url == null || url.isBlank()) {
            String host = info.getHostname();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("MongoDB 数据源缺少连接地址（url 或 hostname）");
            }
            String port = info.getPort() == null || info.getPort().isBlank() ? "27017" : info.getPort();
            String database = info.getDbschema() == null ? "" : info.getDbschema();
            url = "mongodb://" + host + ":" + port + (database.isEmpty() ? "" : "/" + database);
        }
        if (!url.startsWith("mongodb://") && !url.startsWith("mongodb+srv://")) {
            url = "mongodb://" + url;
        }
        return url;
    }

    private static char[] toChars(String value) {
        return (value == null ? "" : value).toCharArray();
    }

    private static boolean isTrue(String value) {
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()));
    }
}
