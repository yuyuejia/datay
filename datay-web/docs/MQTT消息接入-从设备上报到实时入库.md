# 设备在实时上报，数据却在等跑批：用 DataY 把 MQTT 消息接进数据库

> 设备已经连上 Broker 了，消息也在持续上报，
> 但报表上看到的还是昨天的数据。
>
> MQTT 解决了"消息怎么上来"，剩下"数据怎么入库、怎么复用"这一段，
> 其实是一个标准的 ETL 问题。

## 01 一个很常见的断层

物联网项目里，数据链路通常长这样：

```text
设备 / 网关  ──MQTT──▶  Broker  ──???──▶  数据库 / 数仓 / 看板
```

前半段很顺：设备通过 MQTT 协议把遥测、状态、告警发到 Broker，网络不稳定也能靠 QoS 和会话机制兜住。

问题往往出在后半段。Broker 里的消息要变成能查询、能分析、能对账的数据，中间还差一整套事情：

- 谁去订阅主题，订阅哪些主题；
- 消息体怎么解析，字段怎么落到表里；
- 目标表结构怎么维护，批量写入怎么控制；
- 断线了怎么重连，重连后订阅还在不在；
- 任务怎么调度、失败怎么重试、日志在哪里看。

很多团队的做法是：写一个常驻脚本，用 MQTT 客户端收消息，再自己连数据库写库。能跑，但很快会遇到和所有"手写同步脚本"一样的问题——**没人愿意长期维护它**。

这篇文章讲清楚一件事：**把 MQTT 消费做成数据管道里的一个标准输入组件，后半段就能像普通 ETL 一样被配置、被调度、被观测。**

## 02 DataY 的解法：MqttConsumer

DataY 在"实时输入"分类里新增了 **`MqttConsumer`（MQTT 消费）** 组件。

它的定位可以用一句话说清：

> **订阅 MQTT 主题，把每一条消息变成数据流里的一行记录。**

基于 Eclipse Paho MQTT 3.1.1 实现，主要能力包括：

- **多种连接方式**：`tcp://`、`ssl://`、`ws://`、`wss://`；
- **认证与安全**：支持用户名 / 密码，`ssl://` 走 TLS；
- **QoS 0/1/2**：按消息可靠性要求选择服务质量；
- **通配订阅**：支持 `+`（单层）和 `#`（多层）通配符；
- **断线重连**：连接断开后自动重连，并在重连成功后重新订阅；
- **批量下发**：把多条消息聚合为 JSON 数组，减少下游写库次数；
- **格式自适应**：自动解析 JSON 消息体，无法解析时按文本放进 `_payload`；
- **调试模拟**：调试模式下不连 Broker，直接生成一条模拟消息，方便先配好下游。

对使用者的体感是：**MQTT 消息进来之后，后面就和 Kafka、CDC、HTTP 一样，是一条普通的 DataY 数据流。**

## 03 消息到了下游，长什么样

`MqttConsumer` 收到的每一条消息，都会转换成一条 JSON 记录，并附带一组元数据字段：

| 字段         | 含义                                   |
| ------------ | -------------------------------------- |
| `_topic`     | 消息实际发布的主题                     |
| `_qos`       | 消息的 QoS                             |
| `_retained`  | 是否为保留消息                         |
| `_duplicate` | 是否为重复投递                         |
| `_messageId` | MQTT 消息 ID                           |
| `_timestamp` | 组件接收消息的时间戳（毫秒）           |
| `_payload`   | 非 JSON 消息体，或无法展开的 JSON 数组 |

如果消息体本身是一个 JSON 对象，会被直接展开到记录顶层。例如设备上报：

```json
{ "device_id": "dev-001", "temperature": 23.5, "humidity": 56.2, "event_time": 1792396500000 }
```

下游拿到的就是：

```json
{
  "_topic": "device/dev-001/telemetry",
  "_qos": 1,
  "_retained": false,
  "_duplicate": false,
  "_messageId": 0,
  "_timestamp": 1792396517078,
  "device_id": "dev-001",
  "temperature": 23.5,
  "humidity": 56.2,
  "event_time": 1792396500000
}
```

FlowFile 级别还会带上 `_mqttTopic`、`_recordCount`、`_payloadFormat` 几个属性，供下游组件按需引用。

**从这一行开始，MQTT 消息就从"一段字节"变成了可查询、可路由、可写库的结构化数据。**

## 04 五个典型使用场景

### 场景一：IoT 设备遥测汇聚

设备按产品线、设备型号、设备 ID 组织主题，例如：

```text
iot/{productKey}/{deviceId}/telemetry
```

`MqttConsumer` 订阅 `iot/+/+/telemetry`，一次接住所有设备的遥测数据，下游写入 MySQL 或 DuckDB，直接支撑设备状态看板、历史趋势查询。

主题分层设计好之后，新增设备不需要改任务，天然就接得住。

### 场景二：工业设备与边缘数据汇聚

工厂现场的 PLC、网关通常通过 MQTT 把采集数据汇总到中心 Broker。这类场景的特点是：网络可能不稳定、设备可能离线一段时间再恢复。

把 `cleanSession` 设为 `false`，并给组件配置一个稳定的 `clientId`，Broker 会为该会话保留离线期间的消息（QoS 1/2 且主题匹配时）。设备恢复后，`MqttConsumer` 自动重连并重新订阅，历史消息继续入库，**不需要人工补数**。

### 场景三：实时告警与事件流

告警类消息往往需要"分流处理"：不同等级的告警去不同的下游。

在 `MqttConsumer` 后面接一个 **`RouteOnAttribute`（属性路由）**，按消息里的 `level` 或 `alarm_type` 字段分流：

```text
                        ┌── critical ──▶  写告警表 + HTTP 通知
  MQTT 告警 ──▶ 属性路由 ├── warning  ──▶  写告警表
                        └── other    ──▶  兜底记录
```

严重告警可以实时回调业务系统，普通告警只入库存档，未定义类型统一走 `other` 兜底，不会悄悄丢失。

### 场景四：业务系统之间的消息总线

不只是设备，很多业务系统也用 MQTT 做轻量消息总线：订单状态变更、库存变动、支付回调。

把业务消息统一接进 DataY，既能实时写入数仓供分析，也能通过 `StreamSqlUnit` 做清洗、过滤、聚合，或者通过 `StreamJdbcOutput` 同步到其他业务库。

对于"至少一次"投递带来的重复消息，建议按业务主键做幂等：设备类可以用 `device_id + event_time`，消息类可以用 `_topic + _messageId`。

### 场景五：MQTT + 实时 SQL + 大模型

MQTT 只负责"接进来"，真正的价值在后面的处理链路：

- 用 `StreamSqlUnit` 做实时清洗、字段转换、窗口聚合；
- 用 `RouteOnAttribute` 做规则分流；
- 用 `LlmComponent` 处理设备上报的自然语言描述（例如故障现象），自动判断故障类型和紧急程度；
- 用 `DuckDBWrite` / `StreamJdbcOutput` / `DorisStreamLoad` 落到不同目标端。

**一条链路里，MQTT 消息可以被清洗、被判断、被分发，最终变成可查询、可告警、可分析的数据。**

## 05 动手跑通：从 Mosquitto 到 MySQL

下面用一个最小链路，把 MQTT 到 MySQL 的同步完整跑一遍。

链路只有两个节点：

```text
MqttConsumer  ──▶  StreamJdbcOutput
   消费主题            写入 MySQL
```

### 第 1 步：起一个本地 Broker

用 Docker 起一个带认证的 Mosquitto：

```bash
docker run -d --name datay-mosquitto -p 1883:1883 \
  eclipse-mosquitto:2 \
  sh -c "mosquitto_passwd -c -b /mosquitto/config/passwd datay secret && \
         chmod 644 /mosquitto/config/passwd && \
         printf 'listener 1883 0.0.0.0\nallow_anonymous false\npassword_file /mosquitto/config/passwd\n' \
         > /mosquitto/config/mosquitto.conf && \
         exec mosquitto -c /mosquitto/config/mosquitto.conf"
```

Broker 地址 `tcp://127.0.0.1:1883`，账号 `datay / secret`。

### 第 2 步：建目标表

MQTT 消息本身不带表结构元数据，写入关系型数据库时建议先把目标表建好：

```sql
CREATE TABLE source.mqtt_device_telemetry (
  id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  device_id   VARCHAR(64) NOT NULL,
  temperature DOUBLE,
  humidity    DOUBLE,
  event_time  BIGINT,
  _topic      VARCHAR(255),
  _qos        INT,
  _retained   TINYINT(1),
  _timestamp  BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> 如果只是本地分析，也可以把下游换成 `DuckDBWrite`，它会根据 JSON 记录自动推断字段并建表。

### 第 3 步：配置任务

在 DataY 画布上拖一个「MQTT消费」和一个「数据源输出」，连线后配置参数：

`MqttConsumer`：

| 参数        | 值                     |
| ----------- | ---------------------- |
| Broker 地址 | `tcp://127.0.0.1:1883` |
| 订阅主题    | `device/+/telemetry`   |
| QoS         | `1`                    |
| 用户名      | `datay`                |
| 密码        | `secret`               |
| 批量大小    | `100`                  |
| 消息体格式  | `AUTO`                 |

`StreamJdbcOutput`：

| 参数     | 值                      |
| -------- | ----------------------- |
| 数据源   | 本地 MySQL              |
| 表名     | `mqtt_device_telemetry` |
| 写入策略 | `append`                |
| 批处理数 | `1000`                  |

保存后，一个可运行、可调度的 MQTT 同步任务就成型了。

### 第 4 步：发消息并验证

用 `mosquitto_pub` 模拟设备上报：

```bash
docker exec datay-mosquitto mosquitto_pub -h 127.0.0.1 -p 1883 \
  -u datay -P secret -t device/dev-001/telemetry -q 1 \
  -m '{"device_id":"dev-001","temperature":23.5,"humidity":56.2,"event_time":1792396500000}'
```

多设备、多主题重复几次后，MySQL 里可以看到完整记录：

```text
id  device_id  temperature  humidity  event_time      _topic                     _qos
1   dev-001    23.5         56.2      1792396500000   device/dev-001/telemetry   1
2   dev-002    24.1         54.8      1792396510000   device/dev-002/telemetry   1
3   dev-003    22.8         58.1      1792396520000   device/dev-003/telemetry   1
```

任务日志同样清晰：

```text
[MqttConsumer-mqtt_source]  MQTT连接成功，clientId: datay-mqtt_source
[MqttConsumer-mqtt_source]  MQTT订阅成功，topic: device/+/telemetry, qos: 1
[MqttConsumer-mqtt_source]  已成功发送 1 条MQTT消息到下游组件
[StreamJdbcOutput-mysql_sink] 已成功写入 1 条记录到表 mqtt_device_telemetry，写入模式：APPEND
```

**整个过程不需要写一行 MQTT 客户端代码，也不需要维护常驻脚本。**

## 06 一个完整的任务定义

上面这条链路，保存下来就是一份标准的 DataY Core 任务 JSON，既能在 Web 平台调度，也能直接交给 Core 独立运行：

```json
{
  "units": [
    {
      ".id": "mqtt_source",
      ".name": "MqttConsumer",
      "brokerUrl": "tcp://127.0.0.1:1883",
      "topic": "device/+/telemetry",
      "qos": 1,
      "username": "datay",
      "password": "******",
      "batchSize": 100,
      "consumerTimeoutMs": 1000,
      "payloadFormat": "AUTO"
    },
    {
      ".id": "mysql_sink",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "type": "MYSQL",
        "url": "jdbc:mysql://127.0.0.1:3306/source?useSSL=false&serverTimezone=Asia/Shanghai",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "******",
        "dbschema": "source"
      },
      "schema": "source",
      "table": "mqtt_device_telemetry",
      "model": "append",
      "maxRows": 1000
    }
  ],
  "connections": [{ "sourceId": "mqtt_source", "targetId": "mysql_sink", "sourcePort": 0 }],
  "version": "1.0.0"
}
```

要接到真实环境，只需要改 `brokerUrl`、`topic` 和账号密码。分页、时间参数这类 HTTP 同步里的复杂点不存在，MQTT 的复杂度主要在于**主题设计和投递语义**，这些集中在下面几个工程细节里。

## 07 几个必须知道的工程细节

**① 主题要分层设计**

MQTT 没有消费组，订阅关系就是路由规则。主题层级建议从一开始就规划好：

```text
{业务域}/{产品线}/{设备ID}/{消息类型}
```

需要按产品线聚合，就订阅 `iot/+/+/telemetry`；需要接单个设备，就订阅具体主题。**主题设计得好，后面加设备、加产品线都不用动任务。**

**② QoS 不是越高越好**

- QoS 0：最多一次，可能丢，适合高频、允许丢失的指标；
- QoS 1：至少一次，可能重复，是大多数遥测场景的平衡点；
- QoS 2：恰好一次，开销最大，链路更长，只有确实不能重复时才用。

QoS 1/2 都可能带来重复消息，**幂等应该由下游按业务主键保证，而不是依赖 QoS。**

**③ 离线消息与 cleanSession**

- `cleanSession=true`（默认）：每次连接都是新会话，断开期间的离线消息不保留；
- `cleanSession=false`：Broker 保留会话状态和离线消息，适合网络不稳、允许补数的设备场景，但必须配置**稳定且唯一**的 `clientId`。

关闭 Clean Session 后，Broker 端会积压消息，要关注 Broker 的存储和清理策略。

**④ 并发数只能是 1**

MQTT 没有 Kafka 那样的消费组语义。多个客户端订阅同一主题时，**每个客户端都会收到全量消息**，所以 `MqttConsumer` 的 `parallelism` 必须保持为 `1`。

如果单任务吞吐不够，正确的方向是：把不同主题拆给不同任务，或者让 Broker 侧做分区/共享订阅，而不是简单调大组件并发。

**⑤ 消费不过来会发生什么**

组件内部用一个缓冲队列衔接 MQTT 回调线程和写库线程。如果下游写库速度长期低于消息到达速度，队列会被填满，组件会记录告警并丢弃消息：

```text
MQTT消息队列已满，丢弃消息，topic: device/dev-001/telemetry
```

看到这类日志，要么增大 `maxQueueSize` 给下游更多缓冲，要么优化下游组件（提高批量、加索引），或者按主题拆任务。

**⑥ 安全与 TLS**

- 生产环境优先使用 `ssl://`，用 Broker 证书做校验；
- 自签 CA 时，通过 JVM 参数指定信任库：

```bash
java -Djavax.net.ssl.trustStore=/path/to/truststore.jks \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -jar datay-web.jar
```

- 任务配置里的密码是业务配置的一部分，要控制任务 JSON 的访问权限；
- Broker 侧建议按主题做设备级授权，避免一个设备能订阅所有主题。

**⑦ 调试模式很好用**

在 DataY 画布的「调试」页运行 `MqttConsumer` 时，组件不会真的连 Broker，而是生成一条带 `_topic`、`_qos` 等字段的模拟消息。**可以先把下游的 SQL、字段映射、写库配好，再切到真实 Broker，少一轮来回。**

## 08 DataY 是什么

上面这条 MQTT 接入链路，跑在 **DataY** 上。它是一个**开源、轻量的一体化数据平台**：

> 一个 JAR、内嵌 DuckDB，2 核 4G 就能跑通「数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用」的完整链路。

它由两块组成：

| 模块           | 定位           | 说明                                                                                                       |
| -------------- | -------------- | ---------------------------------------------------------------------------------------------------------- |
| **DataY Core** | 数据集成引擎   | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行或嵌入 Java 应用 |
| **DataY Web**  | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数                                                     |

**核心能力：**

- **数据集成**：MySQL / PostgreSQL / Oracle / SQL Server / Doris / ClickHouse / DuckDB 等十余种数据源，支持 MySQL Binlog、PostgreSQL WAL、Oracle LogMiner、MongoDB Change Streams CDC 实时同步，Kafka 消费、MQTT 消费，自动建表与 DDL 转换；
- **维度建模**：星型 / 雪花模型，普通维度、层级维度、时间维度，事实表外键一键关联；
- **任务调度**：Quartz Cron 精确到秒，DAG 拓扑编排，父子任务依赖，失败重试；
- **高性能数仓**：内嵌 DuckDB（列存 + 向量化），支持 DuckLake 数据湖，需要时外挂 Doris / ClickHouse；
- **智能问数**：自然语言 → 指标 → 维度 → 时间过滤 → 取数，一句话拿报表。

**快速开始：**

```bash
git clone https://gitee.com/yuyuejia/datay.git
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

产品首页：<http://datay.yuyuejia.com.cn/> 

项目地址：<https://github.com/yuyuejia/datay>

## 09 写在最后

MQTT 是物联网时代最成功的协议之一，它把"设备怎么把消息送上来"这件事解决得很好。

但数据平台真正要的，不是 Broker 里堆积的消息，而是**能查询、能分析、能对账的数据**。

这中间差的不是又一个自定义脚本，而是一个标准化的输入组件：

> **订阅主题、解析消息、批量下发、断线重连、统一调度、可观测。**
>
> 把 MQTT 消息变成数据流里的一行，后面的清洗、路由、写库、建模、问数，就和任何一条数据管道一样。

当设备数据不再需要"等跑批"，当新增一类设备只需要新增一个主题，实时数据平台才真正接住了物联网的流量。
