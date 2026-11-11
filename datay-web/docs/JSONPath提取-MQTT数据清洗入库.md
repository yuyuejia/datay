# 别再手写解析脚本了：用 JSONPath 组件，把 MQTT 消息洗干净再入库

> 设备上报的是一批 JSON，Broker 只负责搬运，
> 真正麻烦的是：怎么从消息里把业务字段挑出来，稳稳当当地写进数据库。
>
> 这篇用一个真实链路——MQTT 订阅 → 提取 `_payload` → MySQL——把 DataY 新增的
> **EvaluateJsonPath（JSONPath提取）** 组件讲清楚。

---

## 01 消息已经上来了，为什么数据还没入库

物联网和实时业务里，MQTT 几乎已经是标配：

```text
设备 / 网关 ──MQTT──▶ Broker ──?──▶ 数据库 / 数仓 / 看板
```

前半段很成熟：连接、QoS、断线重连、通配订阅，Broker 都帮你兜住了。

真正费劲的是后半段。消息到了消费者手里，往往长这样：

```json
[
  { "deviceId": "d-1", "temperature": 23.5 },
  { "deviceId": "d-2", "temperature": 21.8 }
]
```

它不是一个可以直接入表的结构，而是一段**嵌套在消息里的数据**。你可能需要：

- 从消息记录里找到真正的业务数组；
- 把数组拆成一行行记录；
- 根据记录字段自动建表、推断类型；
- 批量写入 MySQL；
- 没有匹配到数据的消息要跳过，不能把任务打挂。

过去这些逻辑通常散落在一个常驻脚本里。能跑，但字段一变、表结构一变，就要改代码。

DataY 的做法是把这一步做成一个标准组件：**EvaluateJsonPath**。

---

## 02 EvaluateJsonPath 是什么

一句话：

> **用一条 JSONPath 表达式，从上游数据里取出你真正想要的那部分，作为新的数据内容交给下游。**

它不是脚本，也不是 SQL，而是一个配置极简的数据处理节点：

| 配置 | 说明 |
| --- | --- |
| `jsonPath` | JSONPath 表达式，例如 `$.payload`、`$._payload` |
| `valueField` | 提取结果是标量时，包装成 `{valueField: 值}`，默认 `value` |

它最有价值的地方，不是“支持 JSONPath”这件事本身，而是**和 DataY 数据流语义对齐**：

- 上游是 **JSON 数组** → 逐行求值；
- 上游是 **JSON 对象** → 对整个对象直接求值；
- 提取结果是 **对象** → 直接成为下游记录；
- 提取结果是 **数组** → 展平合并，作为 FlowFile 数据内容；
- 提取结果是 **标量** → 包装成一条 `{value: ...}` 记录；
- 未命中 → 跳过并告警，不影响任务继续运行。

这意味着：**JSONPath 组件后面可以直接接数据库输出，不需要再写任何解析代码。**

---

## 03 一个关键设计：数组逐行，对象直接

这是 EvaluateJsonPath 和很多“字符串替换式”JSONPath 工具最大的区别。

### 输入是 JSON 数组

组件会把数组里的每条记录单独拿出来执行 JSONPath：

```text
[{...}, {...}, {...}]
   │       │      │
   ▼       ▼      ▼
 JSONPath JSONPath JSONPath
   │       │      │
   └───────┴──────┘
          ▼
     新的输出数组
```

- 提取到对象：直接作为一条输出记录；
- 提取到数组：展平后并入输出数据；
- 提取到标量：包装成 `{value: 标量}`；
- 某一行没匹配到：只跳过这一行，其余行照常输出。

### 输入是 JSON 对象

组件对整个对象执行一次 JSONPath，提取结果直接替换数据内容。

这个设计解决了一个很常见的痛点：

> 上游一条消息里装着**一批**业务数据，而不是一条。

比如 MQTT 批量上报、HTTP 接口分页返回、Kafka 消息里的 `data` 数组。

EvaluateJsonPath 可以先把这批数据“拆出来”，再交给下游逐条入库。

---

## 04 实战：MQTT 的 `_payload` 怎么变成 MySQL 表

下面是一条已经验证跑通的链路：

```text
MqttConsumer ──▶ EvaluateJsonPath ──▶ StreamJdbcOutput
   订阅主题         提取 _payload          写入 MySQL
```

### 第 1 步：消息长什么样

设备向主题 `device/d-1/telemetry/batch` 发布一条批量消息：

```json
[
  { "deviceId": "d-1", "temperature": 23.5 },
  { "deviceId": "d-2", "temperature": 21.8 }
]
```

`MqttConsumer` 的 `payloadFormat` 设为 `AUTO`。它会自动识别消息体：

- 如果消息体是 **单个 JSON 对象**，字段会直接展开到记录顶层；
- 如果消息体是 **多条记录组成的 JSON 数组**，数组会放进 `_payload` 字段；
- 如果消息体是普通文本，原文放进 `_payload` 字段。

所以上面的消息进入下游时，记录大致是：

```json
{
  "_topic": "device/d-1/telemetry/batch",
  "_qos": 1,
  "_retained": false,
  "_duplicate": false,
  "_messageId": 0,
  "_timestamp": 1792396517078,
  "_payload": [
    { "deviceId": "d-1", "temperature": 23.5 },
    { "deviceId": "d-2", "temperature": 21.8 }
  ]
}
```

真正的业务数据，就在 `_payload` 里。

### 第 2 步：配置 EvaluateJsonPath

拖入「JSONPath提取」组件，只需要填一个表达式：

```json
{
  "jsonPath": "$._payload"
}
```

执行时会发生什么？

1. 上游是一条 JSON 数组，组件逐行求值；
2. 对这条记录执行 `$._payload`，取出里面的业务数组；
3. 结果是 JSON 数组，组件将其展平，作为新的数据内容；
4. 下游拿到的是两条干净的业务记录。

输出：

```json
[
  { "deviceId": "d-1", "temperature": 23.5 },
  { "deviceId": "d-2", "temperature": 21.8 }
]
```

`_topic`、`_qos`、`_payload` 这些消息元数据不会进入最终数据内容，也不会污染目标表结构。

### 第 3 步：写入 MySQL

最后接一个「数据源输出」（`StreamJdbcOutput`）：

| 参数 | 值 |
| --- | --- |
| 数据源 | 本地 MySQL |
| Schema | `test_db` |
| 表名 | `mqtt_device_telemetry` |
| 写入模式 | `append` |

EvaluateJsonPath 会根据提取结果自动重建表元数据，输出组件据此建表：

```sql
SHOW COLUMNS FROM mqtt_device_telemetry;

-- deviceid     varchar(3)
-- temperature  double
```

数据也按预期落库：

```text
deviceid | temperature
---------+------------
d-1      | 23.5
d-2      | 21.8
```

整条链路没有写一行解析代码。

### 第 4 步：完整任务配置

把三个节点串起来，任务 JSON 的核心部分如下：

```json
{
  "units": [
    {
      ".id": "mqtt1",
      ".name": "MqttConsumer",
      "brokerUrl": "tcp://127.0.0.1:1883",
      "topic": "device/+/telemetry/batch",
      "qos": "1",
      "payloadFormat": "AUTO",
      "batchSize": "100"
    },
    {
      ".id": "eval1",
      ".name": "EvaluateJsonPath",
      "jsonPath": "$._payload"
    },
    {
      ".id": "mysql1",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306/test_db?useSSL=false&allowPublicKeyRetrieval=true",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "******",
        "dbschema": "test_db"
      },
      "table": "mqtt_device_telemetry",
      "schema": "test_db",
      "model": "append"
    }
  ],
  "connections": [
    { "sourceId": "mqtt1", "targetId": "eval1" },
    { "sourceId": "eval1", "targetId": "mysql1" }
  ]
}
```

运行日志（节选）：

```text
[MqttConsumer-mqtt1]     已成功发送 1 条MQTT消息到下游组件
[EvaluateJsonPath-eval1] EvaluateJsonPath: 逐行提取完成，输出 2 条记录，跳过 0 行
[StreamJdbcOutput-mysql1] 已成功写入 2 条记录到表 mqtt_device_telemetry，写入模式：APPEND
```

---

## 05 `_payload` 的三种常见形态

`_payload` 不是只有数组一种用法。理解它的形态，配置时就不会踩坑。

### 形态一：多条记录数组

就是上面的例子：

```json
[ { "deviceId": "d-1" }, { "deviceId": "d-2" } ]
```

用 `$._payload` 提取，**数组展平为多行**，适合批量上报。

### 形态二：嵌套业务对象

如果设备把业务数据包在 `payload` 字段里：

```json
{
  "msgId": 1001,
  "payload": { "deviceId": "d-1", "temperature": 23.5 }
}
```

`MqttConsumer` 的 AUTO 模式会把顶层字段展开，`payload` 保留为对象。此时用：

```text
$.payload
```

提取出来的就是 `{ "deviceId": "d-1", "temperature": 23.5 }`，直接作为一条记录入库。

### 形态三：纯文本

如果消息体本身就是普通文本，会进入 `_payload`：

```text
device d-1 temperature 23.5
```

用 `$._payload` 提取，得到的是标量结果。EvaluateJsonPath 会按 `valueField` 包装成：

```json
{ "value": "device d-1 temperature 23.5" }
```

适合把原始报文原样落库，后续再离线解析。

---

## 06 它和 StreamSqlUnit 怎么选

两者都能处理数据，但定位不同：

| 场景 | 推荐组件 |
| --- | --- |
| 从嵌套 JSON 里取字段 / 取子数组 | **EvaluateJsonPath** |
| 拆开消息里的批量数组，再逐条入库 | **EvaluateJsonPath** |
| 字段改名、类型转换、过滤、聚合、窗口计算 | StreamSqlUnit / DataTransform |
| 多表关联、复杂 SQL | Join / JDBCLookup |

一个很常见的组合是：

```text
MQTT ──▶ EvaluateJsonPath（拆包、取业务字段）──▶ StreamSqlUnit（清洗、聚合）──▶ 数据库
```

**JSONPath 负责“把数据拿出来”，SQL 负责“把数据算明白”。** 各司其职，配置都比手写脚本轻。

---

## 07 几个容易踩的坑

### 1. 一个组件只支持一条 JSONPath

需要同时提取多个字段时，可以串联多个 EvaluateJsonPath，或者提取出对象后交给下游 SQL 处理。

### 2. 数组结果会展平

如果 `jsonPath` 的结果是数组，数组元素会依次并入输出数据，而不是作为一个嵌套数组塞进某个字段。这是为了让下游可以直接按记录写库。

### 3. 数组输入是逐行求值的

JSON 数组输入时，别指望用 `$[0].id` 这种“以整个数组为根”的路径。组件会把每一行单独取出来求值。要处理整个数组，请在上游先包装成 JSON 对象。

### 4. 未命中不会报错

某一行没匹配到，只会跳过该行并记录告警；所有行都没匹配到时，整个 FlowFile 不下发。任务不会因为一条脏消息直接失败。

### 5. 自动建表不等于生产表结构

示例里的表是下游根据数据自动推断出来的，适合验证和临时分析。生产环境建议提前建表，明确主键、索引、字段长度和字符集。

---

## 08 小结

MQTT 解决的是“消息怎么上来”，EvaluateJsonPath 解决的是“消息里的数据怎么拿出来”。

把这两步拆开之后，一条实时链路会变得非常清晰：

```text
MQTT 接入  →  JSONPath 提取  →  SQL / 入库  →  看板 / 告警 / 服务
```

不需要常驻解析脚本，不需要为每一种消息格式改代码，也不需要把 `_payload` 里的半结构化数据直接扔进数据库。

**数据管道里的每一步，都应该是可配置、可复用、可观测的。**

这，就是 EvaluateJsonPath 想解决的问题。

---

## 09 DataY 是什么

上面这条 MQTT → JSONPath → MySQL 的链路，跑在 **DataY** 上。它是一个**开源、轻量的一体化数据平台**：

> 一个 JAR，2 核 4G 就能跑通「数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用」的完整链路。

它由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行或嵌入 Java 应用 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数 |

**核心能力：**

- **数据集成**：MySQL / PostgreSQL / Oracle / SQL Server / Doris / ClickHouse / DuckDB 等十余种数据源，支持 MySQL Binlog、PostgreSQL WAL、Oracle LogMiner、MongoDB Change Streams CDC 实时同步，Kafka 消费、MQTT 消费，自动建表与 DDL 转换；
- **数据处理**：SQL、JavaScript、JSONPath 提取、属性路由、多表关联、跨库 Lookup、大模型与决策组件，流式与批处理统一编排；
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

项目地址：

- GitHub：<https://github.com/yuyuejia/datay>
- Gitee：<https://gitee.com/yuyuejia/datay>
