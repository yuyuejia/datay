# MqttConsumer 组件配置文档

## 组件概述

`MqttConsumer` 是一个 MQTT 实时输入组件，基于 Eclipse Paho MQTT 3.1.1 客户端实现。组件订阅一个或多个 MQTT 主题（支持 `+`、`#` 通配符），把收到的消息转换为 `FlowFile` JSON 数组并传递给下游组件，适用于 IoT 设备上报、消息总线接入、实时事件处理等场景。

在可视化画布中，`MqttConsumer` 位于组件面板的 **实时输入** 分组，组件编码为 `MqttConsumer`。

## 核心特性

- **MQTT 3.1.1**：兼容主流 MQTT Broker，如 Mosquitto、EMQX、HiveMQ。
- **多种连接方式**：支持 `tcp://`、`ssl://`、`ws://`、`wss://`。
- **认证与安全**：支持用户名 / 密码；`ssl://` 使用 JVM 默认信任链完成 TLS 校验。
- **QoS 0/1/2**：按消息可靠性要求选择服务质量等级。
- **通配订阅**：支持 MQTT 单层通配 `+` 和多层通配 `#`。
- **断线重连**：自动重连并在重连成功后重新订阅主题。
- **批量下发**：把多条消息聚合为 JSON 数组，减少下游组件调用次数。
- **格式自适应**：自动解析 JSON 消息体，无法解析时按文本放入 `_payload`。
- **调试模拟**：调试模式下不连接 Broker，直接生成一条模拟消息，便于配置下游组件。

## 配置参数

### 基础参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识 |
| `.name` | String | 是 | - | 组件名称，固定为 `MqttConsumer` |
| `brokerUrl` | String | 是 | `tcp://localhost:1883` | Broker 地址，支持 `tcp://`、`ssl://`、`ws://`、`wss://` |
| `clientId` | String | 否 | `datay-{组件id}` | MQTT 客户端 ID；关闭 Clean Session 时应配置稳定且唯一的值 |
| `topic` | String | 是 | - | 订阅主题，支持 `+` 和 `#` 通配符 |
| `qos` | Integer | 否 | `1` | 订阅 QoS，取值 `0`、`1`、`2` |

### 认证与连接参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `username` | String | 否 | - | Broker 用户名 |
| `password` | String | 否 | - | Broker 密码 |
| `cleanSession` | Boolean | 否 | `true` | 是否使用 Clean Session；关闭后可保留会话状态 |
| `automaticReconnect` | Boolean | 否 | `true` | 连接断开后是否自动重连 |
| `keepAliveInterval` | Integer | 否 | `60` | KeepAlive 间隔，单位秒，最小 `5` |
| `connectionTimeout` | Integer | 否 | `10` | 连接超时，单位秒，最小 `1` |
| `maxInflight` | Integer | 否 | `100` | 最大未确认消息数，最小 `1` |

### 消费与缓冲参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `batchSize` | Integer | 否 | `100` | 每批发送给下游的最大消息数 |
| `consumerTimeoutMs` | Integer | 否 | `1000` | 从内部队列拉取消息的等待超时，单位毫秒 |
| `maxQueueSize` | Integer | 否 | `10000` | 回调线程使用的内部缓冲队列容量 |
| `payloadFormat` | String | 否 | `AUTO` | 消息体解析方式：`AUTO` / `JSON` / `TEXT` |
| `parallelism` | Integer | 否 | `1` | 并发数，MQTT 组件必须保持为 `1` |

> **并发限制**：MQTT 协议没有消费组语义。多个客户端订阅同一主题时，每个客户端都会收到全量消息，因此 `MqttConsumer` 不允许 `parallelism > 1`，否则会重复消费。

## 消息映射

每条 MQTT 消息转换为一条 JSON 记录，并附加以下字段：

| 字段 | 描述 |
|------|------|
| `_topic` | 消息实际发布的主题 |
| `_qos` | 消息 QoS |
| `_retained` | 是否为保留消息 |
| `_duplicate` | 是否为重复投递 |
| `_messageId` | MQTT 消息 ID |
| `_timestamp` | 组件接收消息的时间戳（毫秒） |
| `_payload` | 非 JSON 消息体或无法展开的 JSON 数组 |

JSON 对象消息会被展开到记录顶层。例如 Broker 收到：

```json
{"deviceId":"d-1","temperature":23.5}
```

下游收到的记录为：

```json
{
  "_topic": "device/d-1/telemetry",
  "_qos": 1,
  "_retained": false,
  "_duplicate": false,
  "_messageId": 0,
  "_timestamp": 1792396000000,
  "deviceId": "d-1",
  "temperature": 23.5
}
```

FlowFile 级别还会设置以下属性：

| 属性 | 描述 |
|------|------|
| `_mqttTopic` | 组件配置的订阅主题（通配符原样保留） |
| `_recordCount` | 当前 FlowFile 中的消息条数 |
| `_payloadFormat` | 当前生效的消息体格式 |

## 配置示例

### 基础订阅

```json
{
  ".id": "mqtt_source",
  ".name": "MqttConsumer",
  "brokerUrl": "tcp://127.0.0.1:1883",
  "topic": "device/+/telemetry",
  "qos": 1
}
```

### 用户名密码认证

```json
{
  ".id": "mqtt_source",
  ".name": "MqttConsumer",
  "brokerUrl": "tcp://mqtt.example.com:1883",
  "clientId": "datay-device-consumer",
  "topic": "device/#",
  "qos": 1,
  "username": "datay",
  "password": "secret",
  "cleanSession": true
}
```

### TLS 连接

```json
{
  ".id": "mqtt_tls_source",
  ".name": "MqttConsumer",
  "brokerUrl": "ssl://mqtt.example.com:8883",
  "clientId": "datay-tls-consumer",
  "topic": "device/+/telemetry",
  "qos": 1,
  "username": "datay",
  "password": "secret"
}
```

`ssl://` 使用 JVM 默认信任链校验 Broker 证书。如使用自签 CA，可在启动 DataY 时指定信任库：

```bash
java -Djavax.net.ssl.trustStore=/path/to/truststore.jks \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -jar datay-web.jar
```

### 持久会话

需要 Broker 暂存离线消息时，关闭 Clean Session 并使用稳定的 ClientId：

```json
{
  ".id": "mqtt_source",
  ".name": "MqttConsumer",
  "brokerUrl": "tcp://127.0.0.1:1883",
  "clientId": "datay-device-consumer-01",
  "topic": "device/+/telemetry",
  "qos": 1,
  "cleanSession": false
}
```

## 完整任务示例

把 MQTT 设备消息实时写入 DuckDB：

```json
{
  "units": [
    {
      ".id": "mqtt_source",
      ".name": "MqttConsumer",
      "brokerUrl": "tcp://127.0.0.1:1883",
      "topic": "device/+/telemetry",
      "qos": 1,
      "batchSize": 100,
      "payloadFormat": "AUTO"
    },
    {
      ".id": "duckdb_sink",
      ".name": "DuckDBWrite",
      "table": "device_telemetry",
      "model": "append",
      "dbFile": "mqtt.duckdb"
    }
  ],
  "connections": [
    {
      "sourceId": "mqtt_source",
      "targetId": "duckdb_sink",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

## 本地验证

可以使用 Docker 启动一个 Mosquitto Broker 进行验证：

```bash
docker run --rm -d --name mosquitto \
  -p 1883:1883 \
  eclipse-mosquitto:2 \
  sh -c "printf 'listener 1883 0.0.0.0\nallow_anonymous true\n' > /mosquitto/config/mosquitto.conf && mosquitto -c /mosquitto/config/mosquitto.conf"
```

安装 `mosquitto-clients` 后发布一条测试消息：

```bash
mosquitto_pub -h 127.0.0.1 -p 1883 -t device/d-1/telemetry \
  -m '{"deviceId":"d-1","temperature":23.5}'
```

组件配置 `topic` 为 `device/+/telemetry` 时即可在调试面板看到该消息。

## 故障排除

**连接失败**
- 检查 `brokerUrl` 的协议、主机和端口是否正确。
- 确认 Broker 已启动，且防火墙允许访问。
- 使用用户名密码认证时，确认账号密码和 Broker 的认证配置一致。

**订阅成功但收不到消息**
- 检查 `topic` 与发布端主题是否匹配，注意 `+` 只匹配一层，`#` 匹配多层且必须位于末尾。
- 检查发布端 QoS 与订阅 QoS 是否符合预期。
- 使用 `cleanSession=false` 时，确认 ClientId 在同一任务中保持稳定且唯一。

**消息重复消费**
- 检查 `parallelism` 是否被设置为大于 `1`。MQTT 无消费组语义，请保持为 `1`。
- 检查是否启动了多个使用相同订阅主题的任务。

**消息被丢弃**
- 日志出现 `MQTT消息队列已满，丢弃消息` 时，说明下游处理速度低于消息到达速度。
- 可适当增大 `maxQueueSize`，或优化下游组件、增加批量大小 `batchSize`。

**TLS 握手失败**
- 确认 Broker 证书链完整，且证书中的主机名与 `brokerUrl` 一致。
- 使用自签证书时，通过 JVM 参数指定正确的 truststore。

## 注意事项

1. **无消费组**：同一主题需要多消费者分摊时，需要业务侧自行规划主题分区或使用支持共享订阅的 Broker 特性。
2. **消息顺序**：QoS 1/2 下同一主题的消息顺序由 Broker 和客户端投递机制保证；跨主题不保证顺序。
3. **持久化**：组件只负责接收消息并传递到下游，请确保下游组件完成持久化。
4. **密码存储**：任务 JSON 中的密码以明文保存，生产环境请限制任务配置的访问权限，或使用加密配置中心。
