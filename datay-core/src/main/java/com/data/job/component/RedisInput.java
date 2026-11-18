package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Redis 数据输入组件。
 *
 * <p>按 key 模式扫描 Redis，将每个 key 读取为一行键级记录：
 * {@code {key, type, value, ttl?}}。String 的 value 为原始字符串，Hash/List/Set/ZSet/Stream
 * 的 value 为 JSON 字符串，便于直接落库或交给下游 SQL 处理。
 */
@ComponentRegister(
    value = "RedisInput",
    name = "Redis 输入",
    group = "数据输入",
    desc = "按 key 模式扫描 Redis，读取 String/Hash/List/Set/ZSet/Stream 数据并批量下传。",
    order = 73
)
public class RedisInput extends FlowComponent {

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
        label = "Key 模式",
        type = ComponentFieldType.INPUT,
        group = "基础配置",
        order = 20,
        defaultValue = "*",
        placeholder = "例如 user:*",
        help = "使用 SCAN 匹配 key，支持 Redis glob 通配符；默认 * 表示全部 key"
    )
    private String keyPattern = "*";

    @ComponentField(
        label = "Key 类型",
        type = ComponentFieldType.SELECT,
        group = "基础配置",
        order = 30,
        defaultValue = "ALL",
        options = {
            @ComponentFieldOption(value = "ALL", label = "全部类型"),
            @ComponentFieldOption(value = "string", label = "String"),
            @ComponentFieldOption(value = "hash", label = "Hash"),
            @ComponentFieldOption(value = "list", label = "List"),
            @ComponentFieldOption(value = "set", label = "Set"),
            @ComponentFieldOption(value = "zset", label = "ZSet"),
            @ComponentFieldOption(value = "stream", label = "Stream")
        },
        help = "只读取指定类型的 key，选择「全部类型」时不限制"
    )
    private String typeFilter = "ALL";

    @ComponentField(
        label = "扫描批量",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 10,
        defaultValue = "1000",
        min = 1,
        help = "SCAN 每次向 Redis 请求的 key 数量"
    )
    private int scanCount = 1000;

    @ComponentField(
        label = "输出批量",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 20,
        defaultValue = "500",
        min = 1,
        help = "每个 FlowFile 包含的最大 key 数"
    )
    private int batchSize = 500;

    @ComponentField(
        label = "最大 Key 数",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 30,
        defaultValue = "0",
        min = 0,
        help = "0 表示不限制"
    )
    private long maxKeys = 0;

    @ComponentField(
        label = "读取集合元素上限",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 40,
        defaultValue = "1000",
        min = 1,
        help = "Hash/List/Set/ZSet/Stream 单 key 最大读取元素数，避免超大 key 占用过多内存"
    )
    private int maxEntriesPerKey = 1000;

    @ComponentField(
        label = "输出 TTL",
        type = ComponentFieldType.SWITCH,
        group = "高级配置",
        order = 50,
        defaultValue = "false",
        help = "开启后每行额外输出 ttl 字段（秒，-1 表示永久）"
    )
    private boolean includeTtl = false;

    public RedisInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null || flowFile.getAttribute("_end") != null) {
            return;
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("RedisInput 未配置数据源");
        }

        String pattern = keyPattern == null || keyPattern.trim().isEmpty() ? "*" : keyPattern.trim();
        String expectedType = normalizeTypeFilter();
        int effectiveBatchSize = Math.max(batchSize, 1);
        int effectiveScanCount = Math.max(scanCount, 1);
        int effectiveMaxEntries = Math.max(maxEntriesPerKey, 1);
        long limit = maxKeys <= 0 ? Long.MAX_VALUE : maxKeys;
        long readCount = 0;

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(sourceId)) {
            if (!(connection instanceof RedisConnection redis)) {
                throw new IllegalStateException(
                    "RedisInput 需要 REDIS 数据源，实际类型: " + (sourceId.getType() == null ? "null" : sourceId.getType())
                );
            }

            List<Map<String, Object>> batch = new ArrayList<>(effectiveBatchSize);
            try (Stream<String> keys = redis.scanKeys(pattern, effectiveScanCount)) {
                var iterator = keys.iterator();
                while (iterator.hasNext() && readCount < limit) {
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("RedisInput 读取被中断");
                    }
                    String key = iterator.next();
                    if (expectedType != null && !expectedType.equals(redis.typeOf(key))) {
                        continue;
                    }
                    Map<String, Object> record = redis.readRecord(key, effectiveMaxEntries, includeTtl);
                    if (record == null) {
                        continue;
                    }
                    batch.add(record);
                    readCount++;
                    if (batch.size() >= effectiveBatchSize) {
                        sendBatch(batch);
                        batch = new ArrayList<>(effectiveBatchSize);
                    }
                }
            }
            if (!batch.isEmpty()) {
                sendBatch(batch);
            }
            logInfo("Redis 读取完成，共读取 " + readCount + " 个 key，key 模式: " + pattern);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logError("读取 Redis 失败: " + e.getMessage());
            throw new RuntimeException("读取 Redis 失败", e);
        }
    }

    private void sendBatch(List<Map<String, Object>> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        JSONArray array = new JSONArray();
        array.addAll(batch);
        FlowFile out = new FlowFile();
        out.setJsonArray(array);
        out.setAttribute("_recordCount", batch.size());
        writeRecords(out);
        logDebug("RedisInput 已发送 " + batch.size() + " 条记录到下游");
    }

    private String normalizeTypeFilter() {
        if (typeFilter == null || typeFilter.trim().isEmpty() || "ALL".equalsIgnoreCase(typeFilter.trim())) {
            return null;
        }
        String value = typeFilter.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "string", "hash", "list", "set", "zset", "stream" -> value;
            default -> throw new IllegalArgumentException("RedisInput 不支持的 Key 类型: " + typeFilter);
        };
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

    public String getKeyPattern() {
        return keyPattern;
    }

    public void setKeyPattern(String keyPattern) {
        this.keyPattern = keyPattern;
    }

    public String getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(String typeFilter) {
        this.typeFilter = typeFilter;
    }

    public int getScanCount() {
        return scanCount;
    }

    public void setScanCount(int scanCount) {
        this.scanCount = Math.max(scanCount, 1);
    }

    public void setScanCount(String scanCount) {
        this.scanCount = parseInt(scanCount, 1000);
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

    public long getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(long maxKeys) {
        this.maxKeys = Math.max(maxKeys, 0);
    }

    public void setMaxKeys(String maxKeys) {
        this.maxKeys = parseLong(maxKeys, 0);
    }

    public int getMaxEntriesPerKey() {
        return maxEntriesPerKey;
    }

    public void setMaxEntriesPerKey(int maxEntriesPerKey) {
        this.maxEntriesPerKey = Math.max(maxEntriesPerKey, 1);
    }

    public void setMaxEntriesPerKey(String maxEntriesPerKey) {
        this.maxEntriesPerKey = parseInt(maxEntriesPerKey, 1000);
    }

    public boolean isIncludeTtl() {
        return includeTtl;
    }

    public void setIncludeTtl(boolean includeTtl) {
        this.includeTtl = includeTtl;
    }

    public void setIncludeTtl(String includeTtl) {
        this.includeTtl = includeTtl != null && Boolean.parseBoolean(includeTtl.trim());
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

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Math.max(Long.parseLong(value.trim()), 0);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
