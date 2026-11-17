package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldOption;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 生产组件：把上游 FlowFile 中的每一行数据作为一条消息发送到 Kafka 主题。
 *
 * <p>JSON 对象按整行序列化为消息体（也可通过「消息体字段」只发送指定字段），纯文本/CSV 按原文发送；
 * 支持从记录字段提取消息 key、指定目标分区、acks、压缩、linger、重试等常用生产者参数。
 */
@ComponentRegister(
    value = "KafkaProducer",
    name = "Kafka生产",
    group = "数据输出",
    desc = "将上游 FlowFile 数据逐条发送到 Kafka 主题，支持消息 key、指定分区与 JSON/文本消息体。",
    order = 25
)
public class KafkaProducerComponent extends FlowComponent {

    @ComponentField(
        label = "Bootstrap Servers",
        type = ComponentFieldType.INPUT,
        required = true,
        group = "基础配置",
        order = 10,
        defaultValue = "localhost:9092",
        placeholder = "localhost:9092",
        help = "Kafka broker 地址，多个用逗号分隔"
    )
    private String bootstrapServers = "localhost:9092";

    @ComponentField(
        label = "Topic",
        type = ComponentFieldType.INPUT,
        required = true,
        group = "基础配置",
        order = 20,
        placeholder = "请输入 Kafka Topic 名称"
    )
    private String topic;

    @ComponentField(
        label = "消息Key字段",
        type = ComponentFieldType.INPUT,
        group = "基础配置",
        order = 30,
        placeholder = "留空表示不带 key",
        help = "从每条记录中取该字段作为 Kafka 消息 key，留空则不发送 key"
    )
    private String keyField;

    @ComponentField(
        label = "消息体字段",
        type = ComponentFieldType.INPUT,
        group = "基础配置",
        order = 40,
        placeholder = "留空表示发送整行 JSON",
        help = "只发送该字段的值作为消息体；留空时 JSON 记录按整行序列化发送"
    )
    private String valueField;

    @ComponentField(
        label = "指定分区",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 10,
        defaultValue = "-1",
        min = -1,
        placeholder = "-1 表示由 Kafka 自动分区",
        help = "-1 表示由 Kafka 按 key 自动选择分区"
    )
    private Integer partition = -1;

    @ComponentField(
        label = "acks",
        type = ComponentFieldType.SELECT,
        group = "高级配置",
        order = 20,
        defaultValue = "all",
        options = {
            @ComponentFieldOption(value = "all", label = "all - 所有 ISR 确认（最安全）"),
            @ComponentFieldOption(value = "1", label = "1 - Leader 确认"),
            @ComponentFieldOption(value = "0", label = "0 - 不等待确认（可能丢消息）")
        }
    )
    private String acks = "all";

    @ComponentField(
        label = "压缩方式",
        type = ComponentFieldType.SELECT,
        group = "高级配置",
        order = 30,
        defaultValue = "none",
        options = {
            @ComponentFieldOption(value = "none", label = "none - 不压缩"),
            @ComponentFieldOption(value = "gzip", label = "gzip"),
            @ComponentFieldOption(value = "snappy", label = "snappy"),
            @ComponentFieldOption(value = "lz4", label = "lz4"),
            @ComponentFieldOption(value = "zstd", label = "zstd")
        }
    )
    private String compressionType = "none";

    @ComponentField(
        label = "Linger（ms）",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 40,
        defaultValue = "0",
        min = 0,
        help = "发送前等待更多消息合并成批的时间，适当增大可提升吞吐"
    )
    private int lingerMs = 0;

    @ComponentField(
        label = "重试次数",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 50,
        defaultValue = "3",
        min = 0
    )
    private int retries = 3;

    @ComponentField(
        label = "发送超时（ms）",
        type = ComponentFieldType.NUMBER,
        group = "高级配置",
        order = 60,
        defaultValue = "30000",
        min = 1000,
        help = "单条消息等待发送确认的最长时间"
    )
    private int sendTimeoutMs = 30000;

    public KafkaProducerComponent() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null || flowFile.getAttribute("_end") != null) {
            return;
        }
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入 Kafka");
            return;
        }

        List<KafkaMessage> messages = extractMessages(flowFile);
        if (messages.isEmpty()) {
            logDebug("未从 FlowFile 中解析到可发送的消息");
            return;
        }

        validateConfig();
        Properties props = producerProperties();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            List<Future<RecordMetadata>> futures = new ArrayList<>(messages.size());
            for (KafkaMessage message : messages) {
                ProducerRecord<String, String> record = partition != null && partition >= 0
                    ? new ProducerRecord<>(topic, partition, message.key, message.value)
                    : new ProducerRecord<>(topic, message.key, message.value);
                futures.add(producer.send(record));
            }
            producer.flush();

            int sent = 0;
            long timeout = Math.max(sendTimeoutMs, 1000);
            for (Future<RecordMetadata> future : futures) {
                future.get(timeout, TimeUnit.MILLISECONDS);
                sent++;
            }
            logInfo("已成功发送 " + sent + " 条消息到 Kafka topic: " + topic);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka生产组件发送被中断", e);
        } catch (Exception e) {
            logError("Kafka生产组件执行失败: " + e.getMessage());
            throw new RuntimeException("Kafka生产组件执行失败", e);
        }
    }

    private void validateConfig() {
        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            throw new IllegalArgumentException("Kafka生产组件未配置 Bootstrap Servers");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Kafka生产组件未配置 Topic");
        }
    }

    private Properties producerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.trim());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, normalizeAcks());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, normalizeCompression());
        props.put(ProducerConfig.LINGER_MS_CONFIG, Math.max(lingerMs, 0));
        props.put(ProducerConfig.RETRIES_CONFIG, Math.max(retries, 0));
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, Math.max(sendTimeoutMs, 1000));
        return props;
    }

    private String normalizeAcks() {
        if (acks == null) {
            return "all";
        }
        String value = acks.trim().toLowerCase(Locale.ROOT);
        if ("0".equals(value) || "1".equals(value) || "all".equals(value) || "-1".equals(value)) {
            return value;
        }
        logWarn("不支持的 acks 配置 " + acks + "，回退为 all");
        return "all";
    }

    private String normalizeCompression() {
        if (compressionType == null) {
            return "none";
        }
        String value = compressionType.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(value) || "gzip".equals(value) || "snappy".equals(value)
            || "lz4".equals(value) || "zstd".equals(value)) {
            return value;
        }
        logWarn("不支持的压缩方式 " + compressionType + "，回退为 none");
        return "none";
    }

    /**
     * 从 FlowFile 中提取待发送消息：JSON 数组逐行发送、JSON 对象单条发送、其他数据类型按原文单条发送。
     */
    private List<KafkaMessage> extractMessages(FlowFile flowFile) {
        List<KafkaMessage> messages = new ArrayList<>();

        JSONArray array = flowFile.getJsonArray();
        if (array != null) {
            for (Object item : array) {
                if (item instanceof JSONObject) {
                    JSONObject record = (JSONObject) item;
                    messages.add(new KafkaMessage(resolveKey(record), resolveValue(record)));
                } else {
                    messages.add(new KafkaMessage(null, toMessageString(item)));
                }
            }
            return messages;
        }

        JSONObject object = flowFile.getJsonObject();
        if (object != null) {
            messages.add(new KafkaMessage(resolveKey(object), resolveValue(object)));
            return messages;
        }

        Object data = flowFile.getData();
        if (data == null) {
            return messages;
        }
        String text = data instanceof byte[] ? new String((byte[]) data, StandardCharsets.UTF_8) : String.valueOf(data);
        messages.add(new KafkaMessage(null, text));
        return messages;
    }

    private String resolveKey(JSONObject record) {
        if (keyField == null || keyField.trim().isEmpty()) {
            return null;
        }
        Object key = record.get(keyField.trim());
        return key == null ? null : String.valueOf(key);
    }

    private String resolveValue(JSONObject record) {
        if (valueField == null || valueField.trim().isEmpty()) {
            return record.toJSONString();
        }
        return toMessageString(record.get(valueField.trim()));
    }

    private String toMessageString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        return JSON.toJSONString(value);
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        if (bootstrapServers != null && !bootstrapServers.trim().isEmpty()) {
            this.bootstrapServers = bootstrapServers.trim();
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getKeyField() {
        return keyField;
    }

    public void setKeyField(String keyField) {
        this.keyField = keyField;
    }

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        this.valueField = valueField;
    }

    public Integer getPartition() {
        return partition;
    }

    public void setPartition(Integer partition) {
        this.partition = partition;
    }

    public void setPartition(String partition) {
        if (partition != null && !partition.trim().isEmpty()) {
            try {
                this.partition = Integer.parseInt(partition.trim());
            } catch (NumberFormatException e) {
                this.partition = -1;
            }
        }
    }

    public String getAcks() {
        return acks;
    }

    public void setAcks(String acks) {
        if (acks != null && !acks.trim().isEmpty()) {
            this.acks = acks.trim();
        }
    }

    public String getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(String compressionType) {
        if (compressionType != null && !compressionType.trim().isEmpty()) {
            this.compressionType = compressionType.trim();
        }
    }

    public int getLingerMs() {
        return lingerMs;
    }

    public void setLingerMs(int lingerMs) {
        this.lingerMs = Math.max(lingerMs, 0);
    }

    public void setLingerMs(String lingerMs) {
        if (lingerMs != null && !lingerMs.trim().isEmpty()) {
            try {
                this.lingerMs = Math.max(Integer.parseInt(lingerMs.trim()), 0);
            } catch (NumberFormatException e) {
                this.lingerMs = 0;
            }
        }
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = Math.max(retries, 0);
    }

    public void setRetries(String retries) {
        if (retries != null && !retries.trim().isEmpty()) {
            try {
                this.retries = Math.max(Integer.parseInt(retries.trim()), 0);
            } catch (NumberFormatException e) {
                this.retries = 3;
            }
        }
    }

    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(int sendTimeoutMs) {
        this.sendTimeoutMs = Math.max(sendTimeoutMs, 1000);
    }

    public void setSendTimeoutMs(String sendTimeoutMs) {
        if (sendTimeoutMs != null && !sendTimeoutMs.trim().isEmpty()) {
            try {
                this.sendTimeoutMs = Math.max(Integer.parseInt(sendTimeoutMs.trim()), 1000);
            } catch (NumberFormatException e) {
                this.sendTimeoutMs = 30000;
            }
        }
    }

    /** 待发送的一条 Kafka 消息。 */
    private static class KafkaMessage {

        private final String key;

        private final String value;

        private KafkaMessage(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
