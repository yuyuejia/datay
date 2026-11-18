package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.redis.RedisConnection;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldOption;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.redisson.api.RBatch;
import org.redisson.api.StreamMessageId;

/**
 * Redis 数据输出组件。
 *
 * <p>将上游 FlowFile 中的每一行写入 Redis，支持 String/Hash/List/Set/ZSet/Stream。
 * 默认按 {@code keyField} 取 key、按 {@code valueField}（缺省回退 {@code value} 字段）取 value，
 * 因此可以直接承接 {@link RedisInput} 的键级 JSON 记录。
 */
@ComponentRegister(
    value = "RedisOutput",
    name = "Redis 输出",
    group = "数据输出",
    desc = "将上游数据按行写入 Redis，支持 String/Hash/List/Set/ZSet/Stream、TTL 与批量写入。",
    order = 14
)
public class RedisOutput extends FlowComponent {

    @ComponentField(
        key = "sourceId",
        label = "Redis 数据源",
        type = ComponentFieldType.DATASOURCE,
        dataSourceType = "REDIS",
        required = true,
        group = "基础配置",
        order = 10
    )
    private DatasourceInfo sourceId;

    @ComponentField(
        label = "数据类型",
        type = ComponentFieldType.SELECT,
        group = "基础配置",
        order = 20,
        defaultValue = "STRING",
        options = {
            @ComponentFieldOption(value = "STRING", label = "String"),
            @ComponentFieldOption(value = "HASH", label = "Hash"),
            @ComponentFieldOption(value = "LIST", label = "List"),
            @ComponentFieldOption(value = "SET", label = "Set"),
            @ComponentFieldOption(value = "ZSET", label = "ZSet"),
            @ComponentFieldOption(value = "STREAM", label = "Stream")
        }
    )
    private String dataType = "STRING";

    @ComponentField(
        label = "Key 字段",
        type = ComponentFieldType.INPUT,
        group = "字段映射",
        order = 10,
        defaultValue = "key",
        help = "从行中取该字段作为 Redis key；默认兼容 RedisInput 输出的 key 字段"
    )
    private String keyField = "key";

    @ComponentField(
        label = "Key 前缀",
        type = ComponentFieldType.INPUT,
        group = "字段映射",
        order = 20,
        placeholder = "例如 sync:",
        help = "可选，最终 key = 前缀 + Key 字段值"
    )
    private String keyPrefix;

    @ComponentField(
        label = "Value 字段",
        type = ComponentFieldType.INPUT,
        group = "字段映射",
        order = 30,
        placeholder = "留空时优先使用 value 字段",
        help = "留空时优先读取行中的 value 字段；仍不存在时按类型自动映射"
    )
    private String valueField;

    @ComponentField(
        label = "Hash Field 字段",
        type = ComponentFieldType.INPUT,
        group = "字段映射",
        order = 40,
        defaultValue = "field",
        help = "Hash 模式下，从行中取该字段作为 hash field；不存在时使用整行字段"
    )
    private String fieldField = "field";

    @ComponentField(
        label = "ZSet Score 字段",
        type = ComponentFieldType.INPUT,
        group = "字段映射",
        order = 50,
        defaultValue = "score",
        help = "ZSet 模式下，从行中取该字段作为 score"
    )
    private String scoreField = "score";

    @ComponentField(
        label = "Stream ID 字段",
        type = ComponentFieldType.INPUT,
        group = "字段映射",
        order = 60,
        placeholder = "留空由 Redis 自动生成",
        help = "Stream 模式下，从行中取该字段作为消息 ID，例如 1700000000000-0"
    )
    private String streamIdField;

    @ComponentField(
        label = "TTL（秒）",
        type = ComponentFieldType.NUMBER,
        group = "写入配置",
        order = 10,
        defaultValue = "0",
        min = 0,
        help = "0 表示不设置过期时间"
    )
    private long ttlSeconds = 0;

    @ComponentField(
        label = "TTL 字段",
        type = ComponentFieldType.INPUT,
        group = "写入配置",
        order = 20,
        placeholder = "留空使用固定 TTL",
        help = "从行中取该字段作为 TTL（秒），优先于固定 TTL"
    )
    private String ttlField;

    @ComponentField(
        label = "写入模式",
        type = ComponentFieldType.SELECT,
        group = "写入配置",
        order = 30,
        defaultValue = "APPEND",
        options = {
            @ComponentFieldOption(value = "APPEND", label = "APPEND - 追加/合并"),
            @ComponentFieldOption(value = "REPLACE", label = "REPLACE - 首次写入前删除旧 key")
        },
        help = "Stream 不支持 REPLACE，始终按 XADD 追加"
    )
    private String writeMode = "APPEND";

    @ComponentField(
        label = "写入批量",
        type = ComponentFieldType.NUMBER,
        group = "写入配置",
        order = 40,
        defaultValue = "500",
        min = 1,
        help = "每批提交给 Redis 的最大写入命令数"
    )
    private int batchSize = 500;

    /** 当前任务运行期间已经执行过 REPLACE 的 key，避免同一批/后续批次重复 DEL。 */
    private final Set<String> preparedKeys = ConcurrentHashMap.newKeySet();

    /** 输出组件会在多个 FlowFile 批次中复用同一连接，任务结束时关闭。 */
    private volatile RedisConnection redisConnection;

    public RedisOutput() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null || flowFile.getAttribute("_end") != null) {
            return;
        }
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入 Redis");
            return;
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("RedisOutput 未配置数据源");
        }

        JSONArray records = flowFile.getJsonArray();
        if (records == null || records.isEmpty()) {
            return;
        }

        String type = normalizeDataType();
        boolean replace = "REPLACE".equalsIgnoreCase(writeMode);
        int effectiveBatchSize = Math.max(batchSize, 1);
        int written = 0;

        try {
            RedisConnection redis = connection();

            RBatch batch = redis.client().createBatch();
            int queued = 0;
            for (Object item : records) {
                Map<String, Object> row = toRow(item);
                String key = resolveKey(row);
                if (replace && !"STREAM".equals(type) && preparedKeys.add(key)) {
                    redis.queueDelete(batch, key);
                    queued++;
                }

                long ttl = resolveTtl(row);
                switch (type) {
                    case "STRING":
                        redis.queueString(batch, key, resolveStringValue(row));
                        queued++;
                        break;
                    case "HASH":
                        Map<String, String> hashFields = resolveHashFields(row);
                        if (!hashFields.isEmpty()) {
                            redis.queueHash(batch, key, hashFields);
                            queued++;
                        }
                        break;
                    case "LIST":
                        List<String> listValues = resolveListValues(row);
                        if (!listValues.isEmpty()) {
                            redis.queueList(batch, key, listValues);
                            queued++;
                        }
                        break;
                    case "SET":
                        Set<String> setValues = resolveSetValues(row);
                        if (!setValues.isEmpty()) {
                            redis.queueSet(batch, key, setValues);
                            queued++;
                        }
                        break;
                    case "ZSET":
                        Map<String, Double> zsetValues = resolveZSetValues(row);
                        if (!zsetValues.isEmpty()) {
                            redis.queueZSet(batch, key, zsetValues);
                            queued++;
                        }
                        break;
                    case "STREAM":
                        queued += queueStreamValues(redis, batch, key, row);
                        break;
                    default:
                        throw new IllegalArgumentException("RedisOutput 不支持的写入类型: " + dataType);
                }

                if (ttl > 0) {
                    redis.queueExpire(batch, key, ttl);
                    queued++;
                }
                written++;

                if (queued >= effectiveBatchSize) {
                    batch.execute();
                    batch = redis.client().createBatch();
                    queued = 0;
                }
            }
            if (queued > 0) {
                batch.execute();
            }
            logInfo("已写入 " + written + " 行到 Redis，类型: " + type + "，写入模式: " + writeMode);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logError("写入 Redis 失败: " + e.getMessage());
            throw new RuntimeException("写入 Redis 失败", e);
        }
    }

    private synchronized RedisConnection connection() throws Exception {
        if (redisConnection == null) {
            DataSourceConnection connection = DataSourceProviderFactory.connect(sourceId);
            if (!(connection instanceof RedisConnection redis)) {
                connection.close();
                throw new IllegalStateException(
                    "RedisOutput 需要 REDIS 数据源，实际类型: " + (sourceId.getType() == null ? "null" : sourceId.getType())
                );
            }
            redisConnection = redis;
        }
        return redisConnection;
    }

    private synchronized void closeConnection() {
        RedisConnection connection = redisConnection;
        redisConnection = null;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                logWarn("关闭 Redis 连接失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void writeEndRecord() {
        try {
            closeConnection();
        } finally {
            super.writeEndRecord();
        }
    }

    @Override
    public void stop() {
        closeConnection();
        super.stop();
    }

    private int queueStreamValues(RedisConnection redis, RBatch batch, String key, Map<String, Object> row) {
        Object value = resolveValue(row);
        int queued = 0;
        if (value instanceof Collection<?> collection) {
            queued += queueStreamEntries(redis, batch, key, collection);
            if (queued > 0) {
                return queued;
            }
        }

        if (value instanceof String text && text.trim().startsWith("[")) {
            try {
                JSONArray array = JSON.parseArray(text);
                if (array != null) {
                    queued += queueStreamEntries(redis, batch, key, array);
                    if (queued > 0) {
                        return queued;
                    }
                }
            } catch (Exception ignored) {
                // 非 JSON 数组时按单个 value 处理
            }
        }

        if (value instanceof Map<?, ?> map) {
            StreamMessageId id = resolveStreamId(row);
            Map<String, String> fields = toStringMap(map);
            if (!fields.isEmpty()) {
                redis.queueStream(batch, key, id, fields);
                return 1;
            }
        }

        if (value instanceof String text && text.trim().startsWith("{")) {
            Map<String, String> fields = parseStringMap(text);
            if (!fields.isEmpty()) {
                redis.queueStream(batch, key, resolveStreamId(row), fields);
                return 1;
            }
        }

        Map<String, String> fields = resolveStreamFields(row);
        if (!fields.isEmpty()) {
            redis.queueStream(batch, key, resolveStreamId(row), fields);
            return 1;
        }
        return 0;
    }

    private int queueStreamEntries(RedisConnection redis, RBatch batch, String key, Collection<?> entries) {
        int queued = 0;
        for (Object item : entries) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> entry = toStringObjectMap(map);
            StreamMessageId id = resolveStreamId(entry);
            Map<String, String> fields = resolveEntryFields(entry);
            if (!fields.isEmpty()) {
                redis.queueStream(batch, key, id, fields);
                queued++;
            }
        }
        return queued;
    }

    private Map<String, Object> toRow(Object item) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (item instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                row.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            row.put("value", item);
        }
        return row;
    }

    private String resolveKey(Map<String, Object> row) {
        String field = keyField == null || keyField.trim().isEmpty() ? "key" : keyField.trim();
        Object value = row.get(field);
        if (value == null) {
            throw new IllegalArgumentException("RedisOutput 未在行中找到 key 字段: " + field);
        }
        String key = toText(value).trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("RedisOutput 的 key 字段值为空: " + field);
        }
        String prefix = keyPrefix == null ? "" : keyPrefix.trim();
        return prefix + key;
    }

    private long resolveTtl(Map<String, Object> row) {
        if (ttlField != null && !ttlField.trim().isEmpty()) {
            Object value = row.get(ttlField.trim());
            if (value != null) {
                return parseLong(value, ttlSeconds);
            }
        }
        return Math.max(ttlSeconds, 0);
    }

    private String resolveStringValue(Map<String, Object> row) {
        Object value = resolveValue(row);
        if (value != null) {
            return toText(value);
        }
        return JSON.toJSONString(row);
    }

    private Map<String, String> resolveHashFields(Map<String, Object> row) {
        Object value = resolveValue(row);
        Map<String, String> parsed = toMapOrNull(value);
        if (parsed != null) {
            return parsed;
        }

        String fieldName = trimToNull(fieldField);
        if (fieldName != null && row.containsKey(fieldName)) {
            String field = toText(row.get(fieldName)).trim();
            if (!field.isEmpty()) {
                Object fieldValue = value != null ? value : row.get("value");
                return Map.of(field, toText(fieldValue));
            }
        }

        if (value != null) {
            return Map.of("value", toText(value));
        }
        return rowWithoutKey(row);
    }

    private List<String> resolveListValues(Map<String, Object> row) {
        return resolveCollectionValues(row);
    }

    private Set<String> resolveSetValues(Map<String, Object> row) {
        return new LinkedHashSet<>(resolveCollectionValues(row));
    }

    private List<String> resolveCollectionValues(Map<String, Object> row) {
        Object value = resolveValue(row);
        List<String> values = toStringList(value);
        if (!values.isEmpty()) {
            return values;
        }
        if (row.containsKey("member")) {
            return List.of(toText(row.get("member")));
        }
        if (value != null) {
            return List.of(toText(value));
        }
        return List.of(JSON.toJSONString(row));
    }

    private Map<String, Double> resolveZSetValues(Map<String, Object> row) {
        Object value = resolveValue(row);
        Map<String, Double> values = toScoredMap(value);
        if (!values.isEmpty()) {
            return values;
        }

        String memberFieldName = valueField != null && !valueField.trim().isEmpty() ? valueField.trim() : "member";
        Object member = row.get(memberFieldName);
        if (member == null) {
            member = row.get("value");
        }
        if (member != null) {
            String scoreFieldName = scoreField == null || scoreField.trim().isEmpty() ? "score" : scoreField.trim();
            double score = parseDouble(row.get(scoreFieldName), 0D);
            return Map.of(toText(member), score);
        }
        throw new IllegalArgumentException("RedisOutput 写入 ZSet 需要 member/value 字段与 score 字段");
    }

    private Map<String, String> resolveEntryFields(Map<String, Object> entry) {
        Object fields = entry.get("fields");
        Map<String, String> parsed = toMapOrNull(fields);
        if (parsed != null) {
            return parsed;
        }
        return resolveStreamFields(entry);
    }

    private Map<String, String> resolveStreamFields(Map<String, Object> row) {
        Object value = resolveValue(row);
        Map<String, String> parsed = toMapOrNull(value);
        if (parsed != null) {
            return parsed;
        }

        Object fields = row.get("fields");
        parsed = toMapOrNull(fields);
        if (parsed != null) {
            return parsed;
        }

        String fieldName = trimToNull(fieldField);
        if (fieldName != null && row.containsKey(fieldName)) {
            String field = toText(row.get(fieldName)).trim();
            if (!field.isEmpty()) {
                Object fieldValue = value != null ? value : row.get("value");
                return Map.of(field, toText(fieldValue));
            }
        }
        if (value != null) {
            return Map.of("value", toText(value));
        }
        return rowWithoutKey(row);
    }

    private StreamMessageId resolveStreamId(Map<String, Object> row) {
        String field = trimToNull(streamIdField);
        if (field == null && row.containsKey("id")) {
            field = "id";
        }
        if (field == null) {
            return null;
        }
        Object value = row.get(field);
        if (value == null) {
            return null;
        }
        String text = toText(value).trim();
        if (text.isEmpty() || "*".equals(text)) {
            return null;
        }
        String[] parts = text.split("-", 2);
        try {
            long id0 = Long.parseLong(parts[0]);
            long id1 = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
            return new StreamMessageId(id0, id1);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("RedisOutput 的 Stream ID 格式非法: " + text);
        }
    }

    private Object resolveValue(Map<String, Object> row) {
        String field = trimToNull(valueField);
        if (field != null && row.containsKey(field)) {
            return row.get(field);
        }
        if (row.containsKey("value")) {
            return row.get("value");
        }
        return null;
    }

    private Map<String, String> rowWithoutKey(Map<String, Object> row) {
        String field = keyField == null || keyField.trim().isEmpty() ? "key" : keyField.trim();
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (field.equals(entry.getKey())) {
                continue;
            }
            values.put(entry.getKey(), toText(entry.getValue()));
        }
        return values;
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private Map<String, String> toMapOrNull(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toStringMap(map);
        }
        if (value instanceof String text && text.trim().startsWith("{")) {
            return parseStringMap(text);
        }
        return null;
    }

    private Map<String, String> toStringMap(Map<?, ?> map) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), toText(entry.getValue()));
        }
        return result;
    }

    private Map<String, String> parseStringMap(String text) {
        try {
            JSONObject object = JSON.parseObject(text);
            if (object == null) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (String key : object.keySet()) {
                result.put(key, toText(object.get(key)));
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                result.add(toText(item));
            }
            return result;
        }
        if (value instanceof String text && text.trim().startsWith("[")) {
            try {
                JSONArray array = JSON.parseArray(text);
                if (array != null) {
                    List<String> result = new ArrayList<>(array.size());
                    for (Object item : array) {
                        result.add(toText(item));
                    }
                    return result;
                }
            } catch (Exception ignored) {
                // 非 JSON 数组时按单个字符串处理
            }
        }
        return value == null ? List.of() : List.of(toText(value));
    }

    private Map<String, Double> toScoredMap(Object value) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), parseDouble(entry.getValue(), 0D));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    Object member = map.get("member");
                    Object score = map.get("score");
                    if (member != null) {
                        result.put(toText(member), parseDouble(score, 0D));
                    }
                }
            }
            return result;
        }
        if (value instanceof String text && text.trim().startsWith("[")) {
            try {
                JSONArray array = JSON.parseArray(text);
                if (array != null) {
                    for (Object item : array) {
                        if (item instanceof Map<?, ?> map) {
                            Object member = map.get("member");
                            Object score = map.get("score");
                            if (member != null) {
                                result.put(toText(member), parseDouble(score, 0D));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // 由后续字段映射兜底
            }
        }
        return result;
    }

    private String normalizeDataType() {
        if (dataType == null || dataType.trim().isEmpty()) {
            return "STRING";
        }
        String value = dataType.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "STRING", "HASH", "LIST", "SET", "ZSET", "STREAM" -> value;
            default -> throw new IllegalArgumentException("RedisOutput 不支持的写入类型: " + dataType);
        };
    }

    private static String toText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return JSON.toJSONString(value);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static double parseDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public DatasourceInfo getSourceId() {
        return sourceId;
    }

    public void setSourceId(DatasourceInfo sourceId) {
        this.sourceId = sourceId;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.sourceId = datasource;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getKeyField() {
        return keyField;
    }

    public void setKeyField(String keyField) {
        this.keyField = keyField;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        this.valueField = valueField;
    }

    public String getFieldField() {
        return fieldField;
    }

    public void setFieldField(String fieldField) {
        this.fieldField = fieldField;
    }

    public String getScoreField() {
        return scoreField;
    }

    public void setScoreField(String scoreField) {
        this.scoreField = scoreField;
    }

    public String getStreamIdField() {
        return streamIdField;
    }

    public void setStreamIdField(String streamIdField) {
        this.streamIdField = streamIdField;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = Math.max(ttlSeconds, 0);
    }

    public void setTtlSeconds(String ttlSeconds) {
        this.ttlSeconds = Math.max(parseLong(ttlSeconds, 0), 0);
    }

    public String getTtlField() {
        return ttlField;
    }

    public void setTtlField(String ttlField) {
        this.ttlField = ttlField;
    }

    public String getWriteMode() {
        return writeMode;
    }

    public void setWriteMode(String writeMode) {
        this.writeMode = writeMode;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(batchSize, 1);
    }

    public void setBatchSize(String batchSize) {
        this.batchSize = parseInt(batchSize, 500);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Math.max(Integer.parseInt(value.trim()), 1);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
