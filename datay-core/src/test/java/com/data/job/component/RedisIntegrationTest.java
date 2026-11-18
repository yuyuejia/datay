package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.QueryResult;
import com.data.datasource.provider.redis.RedisConnection;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "REDIS_IT_HOST", matches = ".+")
class RedisIntegrationTest extends ComponentTestBase {

    private final String prefix = "datay-it-" + UUID.randomUUID() + ":";
    private final String database = System.getenv().getOrDefault("REDIS_IT_DB", "0");
    private final DatasourceInfo datasource = datasource();

    @BeforeEach
    void setUp() {
        super.setUpBase();
    }

    @AfterEach
    void cleanUp() {
        try (RedisConnection connection = new RedisConnection(datasource)) {
            deleteByPattern(connection, prefix + "*");
        }
    }

    @Test
    @Timeout(60000)
    void writesAndReadsAllSupportedTypes() {
        write("HASH", "[{\"key\":\"" + prefix + "hash\",\"value\":{\"name\":\"张三\",\"age\":18}}]");
        write("LIST", "[{\"key\":\"" + prefix + "list\",\"value\":[\"a\",\"b\"]}]");
        write("SET", "[{\"key\":\"" + prefix + "set\",\"value\":[\"x\",\"y\"]}]");
        write("ZSET", "[{\"key\":\"" + prefix + "zset\",\"value\":[{\"member\":\"m1\",\"score\":1.5}]}]");
        write("STREAM", "[{\"key\":\"" + prefix + "stream\",\"value\":[{\"fields\":{\"f1\":\"v1\"}}]}]");
        write("STRING", "[{\"key\":\"" + prefix + "str\",\"value\":\"hello\"}]");

        try (RedisConnection connection = new RedisConnection(datasource)) {
            assertTrue(connection.testConnection());
            assertEquals("string", connection.typeOf(prefix + "str"));
            assertEquals("hash", connection.typeOf(prefix + "hash"));
            assertEquals("list", connection.typeOf(prefix + "list"));
            assertEquals("set", connection.typeOf(prefix + "set"));
            assertEquals("zset", connection.typeOf(prefix + "zset"));
            assertEquals("stream", connection.typeOf(prefix + "stream"));

            Map<String, Object> stringRecord = connection.readRecord(prefix + "str", 100, true);
            assertNotNull(stringRecord);
            assertEquals("hello", stringRecord.get("value"));

            Map<String, Object> hashRecord = connection.readRecord(prefix + "hash", 100, false);
            assertNotNull(hashRecord);
            assertTrue(String.valueOf(hashRecord.get("value")).contains("张三"));
        }

        CapturingRedisInput input = input(prefix + "*", "ALL");
        input.setBatchSize(2);
        input.execute(createStartFlowFile());

        List<Map<String, Object>> rows = input.rows();
        assertEquals(6, rows.size());
        assertTrue(rows.stream().anyMatch(row -> (prefix + "hash").equals(row.get("key"))));
        assertTrue(rows.stream().anyMatch(row -> "stream".equals(row.get("type"))));
        assertTrue(input.flowFiles().size() >= 3, "batchSize=2 时应拆分为多个 FlowFile");
    }

    @Test
    @Timeout(60000)
    void supportsTypeFilterMaxKeysAndEntryLimit() {
        write("HASH", "[{\"key\":\"" + prefix + "h1\",\"value\":{\"a\":\"1\"}}," +
            "{\"key\":\"" + prefix + "h2\",\"value\":{\"b\":\"2\"}}," +
            "{\"key\":\"" + prefix + "h3\",\"value\":{\"c\":\"3\"}}]");
        write("LIST", "[{\"key\":\"" + prefix + "list\",\"value\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}]");

        CapturingRedisInput hashOnly = input(prefix + "*", "hash");
        hashOnly.execute(createStartFlowFile());
        assertEquals(3, hashOnly.rows().size());
        assertTrue(hashOnly.rows().stream().allMatch(row -> "hash".equals(row.get("type"))));

        CapturingRedisInput limited = input(prefix + "*", "ALL");
        limited.setMaxKeys(2);
        limited.execute(createStartFlowFile());
        assertEquals(2, limited.rows().size());

        CapturingRedisInput listInput = input(prefix + "list", "list");
        listInput.setMaxEntriesPerKey(2);
        listInput.execute(createStartFlowFile());
        assertEquals(1, listInput.rows().size());
        JSONArray value = JSON.parseArray(String.valueOf(listInput.rows().get(0).get("value")));
        assertEquals(2, value.size());
        assertEquals("1", value.getString(0));
        assertEquals("2", value.getString(1));
    }

    @Test
    @Timeout(60000)
    void supportsKeyPrefixTtlFieldAndReplaceForCollections() {
        RedisOutput initial = output("HASH");
        initial.setKeyField("id");
        initial.setKeyPrefix(prefix + "pfx:");
        initial.setTtlField("expire");
        initial.execute(json("[{\"id\":\"user:1\",\"value\":{\"name\":\"old\",\"age\":1},\"expire\":30}]"));
        initial.stop();

        RedisOutput output = output("HASH");
        output.setKeyField("id");
        output.setKeyPrefix(prefix + "pfx:");
        output.setTtlField("expire");
        output.setWriteMode("REPLACE");
        output.execute(json("[{\"id\":\"user:1\",\"value\":{\"name\":\"new\"},\"expire\":60}]"));
        output.stop();

        try (RedisConnection connection = new RedisConnection(datasource)) {
            String key = prefix + "pfx:user:1";
            assertEquals("hash", connection.typeOf(key));
            assertTrue(connection.ttlSeconds(key) > 0);
            Map<String, Object> record = connection.readRecord(key, 100, false);
            assertNotNull(record);
            JSONObject value = JSON.parseObject(String.valueOf(record.get("value")));
            assertEquals("new", value.getString("name"));
            assertTrue(!value.containsKey("age"), "REPLACE 后不应保留旧 hash 字段");
        }

        replaceCollections();
    }

    @Test
    @Timeout(90000)
    void supportsRedisToRedisRoundTrip() {
        write("STRING", "[{\"key\":\"" + prefix + "src:str\",\"value\":\"hello\"}]");
        write("HASH", "[{\"key\":\"" + prefix + "src:hash\",\"value\":{\"name\":\"张三\",\"age\":18}}]");
        write("LIST", "[{\"key\":\"" + prefix + "src:list\",\"value\":[\"a\",\"b\"]}]");
        write("SET", "[{\"key\":\"" + prefix + "src:set\",\"value\":[\"x\",\"y\"]}]");
        write("ZSET", "[{\"key\":\"" + prefix + "src:zset\",\"value\":[{\"member\":\"m1\",\"score\":1.5},{\"member\":\"m2\",\"score\":2}]}]");
        write("STREAM", "[{\"key\":\"" + prefix + "src:stream\",\"value\":[{\"id\":\"1-0\",\"fields\":{\"f1\":\"v1\"}},{\"id\":\"2-0\",\"fields\":{\"f2\":\"v2\"}}]}]");

        CapturingRedisInput input = input(prefix + "src:*", "ALL");
        input.setMaxEntriesPerKey(100);
        input.execute(createStartFlowFile());
        List<Map<String, Object>> rows = input.rows();
        assertEquals(6, rows.size());

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("type")).toUpperCase();
            Map<String, Object> copy = new LinkedHashMap<>(row);
            String sourceKey = String.valueOf(row.get("key"));
            copy.put("key", prefix + "dst:" + sourceKey.substring((prefix + "src:").length()));
            grouped.computeIfAbsent(type, key -> new ArrayList<>()).add(copy);
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            RedisOutput output = output(entry.getKey());
            JSONArray array = new JSONArray();
            array.addAll(entry.getValue());
            FlowFile flowFile = new FlowFile();
            flowFile.setJsonArray(array);
            output.execute(flowFile);
            output.stop();
        }

        try (RedisConnection connection = new RedisConnection(datasource)) {
            assertEquals("hello", connection.readRecord(prefix + "dst:str", 100, false).get("value"));
            JSONObject hash = JSON.parseObject(
                String.valueOf(connection.readRecord(prefix + "dst:hash", 100, false).get("value"))
            );
            assertEquals("张三", hash.getString("name"));
            assertEquals(18, hash.getIntValue("age"));
            assertEquals(2, JSON.parseArray(String.valueOf(connection.readRecord(prefix + "dst:list", 100, false).get("value"))).size());
            assertEquals(2, JSON.parseArray(String.valueOf(connection.readRecord(prefix + "dst:set", 100, false).get("value"))).size());
            JSONArray zset = JSON.parseArray(String.valueOf(connection.readRecord(prefix + "dst:zset", 100, false).get("value")));
            assertEquals(2, zset.size());
            assertEquals("m1", zset.getJSONObject(0).getString("member"));
            JSONArray stream = JSON.parseArray(
                String.valueOf(connection.readRecord(prefix + "dst:stream", 100, false).get("value"))
            );
            assertEquals(2, stream.size());
            assertEquals("1-0", stream.getJSONObject(0).getString("id"));
            assertEquals("v1", stream.getJSONObject(0).getJSONObject("fields").getString("f1"));
        }
    }

    @Test
    @Timeout(60000)
    void supportsDataSourceMetadataAndReadOnlyCommands() {
        write("STRING", "[{\"key\":\"" + prefix + "str\",\"value\":\"hello\"}]");
        write("HASH", "[{\"key\":\"" + prefix + "hash\",\"value\":{\"name\":\"张三\"}}]");
        write("LIST", "[{\"key\":\"" + prefix + "list\",\"value\":[\"a\",\"b\"]}]");
        write("SET", "[{\"key\":\"" + prefix + "set\",\"value\":[\"x\",\"y\"]}]");
        write("ZSET", "[{\"key\":\"" + prefix + "zset\",\"value\":[{\"member\":\"m1\",\"score\":1.5}]}]");
        write("STREAM", "[{\"key\":\"" + prefix + "stream\",\"value\":[{\"fields\":{\"f1\":\"v1\"}}]}]");

        try (RedisConnection connection = new RedisConnection(datasource)) {
            assertEquals(List.of(database), connection.getSchemas());
            List<TableMeta> tables = connection.getTables(null, 100, prefix);
            assertEquals(6, tables.size());
            assertEquals("hash", connection.getTableMeta(null, prefix + "hash").getTableType());
            assertEquals("list", connection.getTableMeta(null, prefix + "list").getTableType());
            assertEquals("set", connection.getTableMeta(null, prefix + "set").getTableType());
            assertEquals("zset", connection.getTableMeta(null, prefix + "zset").getTableType());
            assertEquals("stream", connection.getTableMeta(null, prefix + "stream").getTableType());

            assertColumns(connection.executeQuery("PING", null, 10), "result");
            assertColumns(connection.executeQuery("GET " + prefix + "str", null, 10), "key", "value");
            assertColumns(connection.executeQuery("HGETALL " + prefix + "hash", null, 10), "field", "value");
            assertColumns(connection.executeQuery("LRANGE " + prefix + "list 0 -1", null, 10), "index", "value");
            assertColumns(connection.executeQuery("SMEMBERS " + prefix + "set", null, 10), "value");
            assertColumns(connection.executeQuery("ZRANGE " + prefix + "zset 0 -1 WITHSCORES", null, 10), "member", "score");
            assertColumns(connection.executeQuery("XRANGE " + prefix + "stream - + COUNT 10", null, 10), "id", "field", "value");
            assertColumns(connection.executeQuery("TYPE " + prefix + "stream", null, 10), "key", "type");
            assertColumns(connection.executeQuery("TTL " + prefix + "str", null, 10), "key", "ttl");
            assertColumns(connection.executeQuery("SCAN 0 MATCH " + prefix + "* COUNT 100", null, 100), "key", "type");
            assertColumns(connection.executeQuery("KEYS " + prefix + "*", null, 100), "key", "type");

            assertEquals("stream", connection.executeQuery("TYPE " + prefix + "stream", null, 10).getRows().get(0).get("type"));
            assertEquals(1, connection.executeQuery("ZRANGE " + prefix + "zset 0 -1 WITHSCORES", null, 10).getRows().size());
            assertEquals(1, connection.executeQuery("XRANGE " + prefix + "stream - + COUNT 10", null, 10).getRows().size());
            assertThrows(UnsupportedOperationException.class, () -> connection.executeUpdate("SET k v", null));
            assertThrows(IllegalArgumentException.class, () -> connection.executeQuery("INCR counter", null, 10));
        }
    }

    @Test
    @Timeout(60000)
    void authenticatesWithUrlCredentialsAndSelectsDatabase() {
        String password = System.getenv("REDIS_IT_PASSWORD");
        assumeTrue(password != null && !password.isBlank(), "需要 REDIS_IT_PASSWORD 验证 URL 认证");

        DatasourceInfo info = new DatasourceInfo();
        info.setType("REDIS");
        info.setUrl(
            "redis://:" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "@" + System.getenv("REDIS_IT_HOST") + ":" + System.getenv().getOrDefault("REDIS_IT_PORT", "6379")
                + "/" + database
        );
        try (RedisConnection connection = new RedisConnection(info)) {
            assertTrue(connection.testConnection());
            assertEquals(List.of(database), connection.getSchemas());
        }
    }

    @Test
    @Timeout(60000)
    void rejectsMissingKeyAndInvalidStreamId() {
        RedisOutput missingKey = output("STRING");
        assertThrows(IllegalArgumentException.class, () -> missingKey.execute(json("[{\"value\":\"v\"}]")));
        missingKey.stop();

        RedisOutput invalidStream = output("STREAM");
        invalidStream.setStreamIdField("id");
        assertThrows(
            IllegalArgumentException.class,
            () -> invalidStream.execute(json("[{\"key\":\"" + prefix + "bad\",\"id\":\"not-an-id\",\"value\":{\"f\":\"v\"}}]"))
        );
        invalidStream.stop();
    }

    private void replaceCollections() {
        RedisOutput initialSet = output("SET");
        initialSet.execute(json("[{\"key\":\"" + prefix + "set:r\",\"value\":[\"a\",\"b\"]}]"));
        initialSet.stop();

        RedisOutput setOutput = output("SET");
        setOutput.setWriteMode("REPLACE");
        setOutput.execute(json("[{\"key\":\"" + prefix + "set:r\",\"value\":[\"c\"]}]"));
        setOutput.stop();

        RedisOutput initialZSet = output("ZSET");
        initialZSet.execute(json("[{\"key\":\"" + prefix + "zset:r\",\"value\":[{\"member\":\"a\",\"score\":1},{\"member\":\"b\",\"score\":2}]}]"));
        initialZSet.stop();

        RedisOutput zsetOutput = output("ZSET");
        zsetOutput.setWriteMode("REPLACE");
        zsetOutput.execute(json("[{\"key\":\"" + prefix + "zset:r\",\"value\":[{\"member\":\"c\",\"score\":3}]}]"));
        zsetOutput.stop();

        try (RedisConnection connection = new RedisConnection(datasource)) {
            JSONArray set = JSON.parseArray(String.valueOf(connection.readRecord(prefix + "set:r", 100, false).get("value")));
            assertEquals(1, set.size());
            assertEquals("c", set.getString(0));
            JSONArray zset = JSON.parseArray(String.valueOf(connection.readRecord(prefix + "zset:r", 100, false).get("value")));
            assertEquals(1, zset.size());
            assertEquals("c", zset.getJSONObject(0).getString("member"));
        }
    }

    private RedisOutput output(String dataType) {
        RedisOutput output = new RedisOutput();
        initComponent(output, "redis-output-" + dataType.toLowerCase(), "RedisOutput");
        output.setSourceId(datasource);
        output.setDataType(dataType);
        return output;
    }

    private void write(String dataType, String text) {
        RedisOutput output = output(dataType);
        output.execute(json(text));
        output.stop();
    }

    private CapturingRedisInput input(String pattern, String typeFilter) {
        CapturingRedisInput input = new CapturingRedisInput();
        initComponent(input, "redis-input-it", "RedisInput");
        input.setSourceId(datasource);
        input.setKeyPattern(pattern);
        input.setTypeFilter(typeFilter);
        return input;
    }

    private void assertColumns(QueryResult result, String... columns) {
        assertEquals(List.of(columns), result.getColumns());
    }

    private FlowFile json(String text) {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(JSON.parseArray(text));
        return flowFile;
    }

    private DatasourceInfo datasource() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("REDIS");
        info.setUrl("redis://" + System.getenv("REDIS_IT_HOST") + ":" + System.getenv().getOrDefault("REDIS_IT_PORT", "6379"));
        info.setDbschema(database);
        String password = System.getenv("REDIS_IT_PASSWORD");
        if (password != null && !password.isBlank()) {
            info.setPassword(password);
        }
        return info;
    }

    private void deleteByPattern(RedisConnection connection, String pattern) {
        try (Stream<String> keys = connection.scanKeys(pattern, 1000)) {
            String[] values = keys.toArray(String[]::new);
            if (values.length > 0) {
                connection.client().getKeys().delete(values);
            }
        }
    }

    private static class CapturingRedisInput extends RedisInput {

        private final List<FlowFile> flowFiles = new ArrayList<>();

        @Override
        public void writeRecords(FlowFile flowFile) {
            flowFiles.add(flowFile);
        }

        private List<FlowFile> flowFiles() {
            return flowFiles;
        }

        private List<Map<String, Object>> rows() {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (FlowFile flowFile : flowFiles) {
                JSONArray array = flowFile.getJsonArray();
                if (array == null) {
                    continue;
                }
                for (Object item : array) {
                    rows.add((Map<String, Object>) item);
                }
            }
            return rows;
        }
    }
}
