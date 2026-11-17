# KafkaProducer 组件配置文档

## 组件概述

`KafkaProducer` 是一个 Kafka 实时输出组件，基于 `kafka-clients` 实现。组件把上游 `FlowFile` 中的每一行数据逐条发送到指定 Kafka 主题，适用于实时同步、事件分发、消息总线对接等场景。

在可视化画布中，`KafkaProducer` 位于组件面板的 **数据输出** 分组，组件编码为 `KafkaProducer`。

## 核心特性

- **逐行发送**：JSON 数组中的每行发送为一条消息，JSON 对象发送为单条消息。
- **消息 Key**：可从记录字段提取消息 key，便于按 key 分区与顺序消费。
- **消息体裁剪**：可只发送指定字段作为消息体，例如只发送 `payload`。
- **文本兼容**：纯文本 / CSV / 二进制数据按原文作为单条消息发送。
- **可靠投递**：支持 `acks`、重试、压缩、linger 与发送超时配置。
- **指定分区**：支持固定发送到某个分区，留空由 Kafka 按 key 自动分区。
- **调试安全**：调试模式跳过实际发送，不会向 Kafka 写入测试数据。

## 配置参数

### 基础参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识 |
| `.name` | String | 是 | - | 组件名称，固定为 `KafkaProducer` |
| `bootstrapServers` | String | 是 | `localhost:9092` | Kafka broker 地址，多个用逗号分隔 |
| `topic` | String | 是 | - | 目标主题 |
| `keyField` | String | 否 | - | 从记录中取该字段作为消息 key，留空表示不带 key |
| `valueField` | String | 否 | - | 只发送该字段的值作为消息体，留空表示发送整行 JSON |

### 高级参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `partition` | Integer | 否 | `-1` | 目标分区，`-1` 表示由 Kafka 按 key 自动选择 |
| `acks` | String | 否 | `all` | 确认级别：`all` / `1` / `0` |
| `compressionType` | String | 否 | `none` | 压缩方式：`none` / `gzip` / `snappy` / `lz4` / `zstd` |
| `lingerMs` | Integer | 否 | `0` | 发送前等待合并成批的时间，单位毫秒 |
| `retries` | Integer | 否 | `3` | 发送失败重试次数 |
| `sendTimeoutMs` | Integer | 否 | `30000` | 单条消息等待发送确认的最长时间，单位毫秒 |

## 消息映射

上游数据的处理规则：

| 上游数据类型 | 发送行为 |
|--------------|----------|
| JSON 数组 | 每个元素发送为一条消息；元素为 JSON 对象时按记录字段提取 key / 消息体 |
| JSON 对象 | 发送为一条消息 |
| 纯文本 / CSV | 原文作为一条消息 |
| 二进制 | 按 UTF-8 解码后作为一条消息 |

- 未配置 `valueField`：JSON 对象整体序列化为消息体；
- 配置 `valueField`：只发送该字段的值（字符串原样，数字/布尔按 JSON 形式，例如 `23.5`）；
- 配置 `keyField` 且字段为空时，该条消息不带 key。

## 任务 JSON 示例

```json
{
  "units": [
    {
      ".id": "kafka_source",
      ".name": "KafkaConsumer",
      "bootstrapServers": "127.0.0.1:9092",
      "topic": "device-telemetry",
      "groupId": "datay-device",
      "autoOffsetReset": "earliest",
      "batchSize": 100
    },
    {
      ".id": "kafka_sink",
      ".name": "KafkaProducer",
      "bootstrapServers": "127.0.0.1:9092",
      "topic": "device-telemetry-forward",
      "keyField": "deviceId",
      "acks": "all",
      "compressionType": "gzip",
      "retries": 3
    }
  ],
  "connections": [
    {
      "sourceId": "kafka_source",
      "targetId": "kafka_sink",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

## 调试运行

调试模式下组件会跳过实际发送，只记录「调试模式：跳过写入 Kafka」，避免调试数据污染目标主题。需要验证发送效果时，请在正式运行中向目标主题发送一批数据。

## 本地验证

使用 Docker 启动单节点 Kafka（KRaft）：

```bash
docker run -d --name datay-kafka \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@127.0.0.1:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  apache/kafka:3.9.0
```

创建主题并消费验证：

```bash
docker exec datay-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server 127.0.0.1:9092 --create --topic device-telemetry-forward \
  --partitions 1 --replication-factor 1

docker exec datay-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server 127.0.0.1:9092 --topic device-telemetry-forward \
  --from-beginning --property print.key=true
```

## 故障排除

**连接失败**
- 检查 `bootstrapServers` 地址与端口，确认 broker 已启动；
- 云上 Kafka 需要配置安全协议与认证时，请确认网络与 ACL 允许写入目标主题。

**发送超时**
- 适当增大 `sendTimeoutMs` 与 `retries`；
- 检查目标主题分区是否可用、ISR 是否正常。

**消息顺序**
- 同一 key 的消息会进入同一分区，从而保持分区内顺序；
- 未配置 `keyField` 且多分区时，消息可能分散到不同分区，无法保证全局顺序。

**吞吐偏低**
- 适当增大 `lingerMs`，让更多消息合并成批；
- 开启压缩（如 `lz4` / `zstd`）降低网络开销。
