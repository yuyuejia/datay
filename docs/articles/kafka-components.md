# 不写代码，把 Kafka 接入数据链路：DataY 消费与生产组件实战

> 摘要：Kafka 接入不该从写代码开始。DataY 内置 Kafka 消费 / 生产组件，拖拽连线即可完成实时接入、落库、转发与断点续传。

---

## 01 实时数据接入，为什么总是卡在 Kafka 上？

做数据集成的人，大概都经历过这样的循环：

- 业务说：订单、设备、日志都进 Kafka 了，帮忙同步到数仓；
- 开发说：好，我写个消费者；
- 然后开始处理消费组、offset 提交、异常重连、消息格式解析、批量写入、位点恢复……
- 一周后，代码能跑了，但换个主题、加个字段、改个下游，又得改代码重新上线。

Kafka 本身很成熟，真正麻烦的是**把它接进数据链路的那一层胶水代码**：消费位点怎么存、任务重启后从哪继续、消息体是 JSON 还是纯文本、下游写库失败了怎么办。

DataY 的思路很简单：**把 Kafka 的消费和生产做成开箱即用的 ETL 组件**。不用写代码，在画布上拖两个节点、连一条线，就能把数据从 Kafka 搬进来，或者把处理结果推出去。

下面分别看看这两个组件。

---

## 02 Kafka 消费组件：把主题变成数据流

### 能力一览

DataY 的「Kafka消费」组件位于组件面板的 **实时输入** 分组，主要能力包括：

- **实时消费**：连接 Kafka broker，持续消费指定主题；
- **批量下发**：按批次聚合为 JSON 数组，减少下游组件调用次数；
- **格式自适应**：JSON 消息自动展开为字段，非 JSON 消息放入 `_value`；
- **断点续传**：按分区保存消费位点，任务异常、重启后继续消费；
- **灵活起点**：支持 `earliest` / `latest`、指定分区、指定 offset、指定时间戳；
- **调试取数**：调试模式连接真实 Broker，收到一批真实消息后自动结束；
- **优雅停止**：停止任务时唤醒消费线程并安全关闭连接。

### 核心配置

| 参数 | 说明 |
| --- | --- |
| `bootstrapServers` | Broker 地址，多个用逗号分隔 |
| `topic` | 要消费的主题 |
| `groupId` | 消费组 ID |
| `autoOffsetReset` | 无位点时的策略：`earliest` / `latest` / `none` |
| `enableAutoCommit` | 是否启用 Kafka 自动提交 |
| `batchSize` | 每批发送给下游的最大消息数 |
| `consumerTimeoutMs` | 拉取消息的等待超时 |
| `partition` | 指定分区，`-1` 表示消费所有分区 |
| `startOffset` | 指定起始 offset（需配合指定分区） |
| `startTimestamp` | 按时间戳定位起始位置 |

### 消息会变成什么样？

Kafka 里的这条消息：

```json
{"deviceId":"sensor-01","temperature":23.5,"status":"ok"}
```

下游收到的每一行记录会自动带上链路元数据：

```json
{
  "_topic": "device-telemetry",
  "_partition": 0,
  "_offset": 1024,
  "_timestamp": 1794896741985,
  "_key": "sensor-01",
  "deviceId": "sensor-01",
  "temperature": 23.5,
  "status": "ok"
}
```

如果消息体不是 JSON，也不会丢：原文会放入 `_value` 字段，后续可以用转换组件再处理。

### 重点：位点状态与异常恢复

很多 Kafka 接入方案依赖消费组提交位点，但一旦换消费组、位点过期或者需要跨环境恢复，就容易「从头再来」或者「丢一段」。

DataY 的做法参考了 CDC 组件的状态机制：**按分区把消费位点保存到任务状态里**。

状态键长这样：

```text
n1.kafkaOffset.device-telemetry.0 = 1025
```

含义是：节点 `n1` 在 `device-telemetry` 主题的分区 0 上，下一次从 offset 1025 继续消费。

它有几个关键设计：

1. **按分区保存**，多分区各自记录，不会互相覆盖；
2. **下游处理成功后才推进**：位点随批次流向下游，由写入组件处理成功后统一持久化，语义是 at-least-once，失败会重放而不是丢数据；
3. **启动时优先从状态恢复**，其次才看显式起始 offset / 时间戳，最后回退到消费组位点；
4. **位点越界自动兜底**：如果消息已被 retention 清理，会根据 `autoOffsetReset` 重置并告警，避免任务反复失败；
5. **状态可视**：在任务状态页可以直接看到每个分区的位点，也支持手动清理后从头重放。

这意味着：任务重启、机器宕机、下游短暂不可用之后，Kafka 消费都能从上次的位置接着跑，而不是靠猜。

### 调试模式也很「真实」

调试运行时，消费组件不会生成模拟数据，而是连接真实 Broker 消费一批真实消息，然后结束本次调试。30 秒内没有消息会给出明确提示，方便先确认 Broker、主题和消息格式。

---

## 03 Kafka 生产组件：把数据推回 Kafka

有了消费，自然也少不了生产。DataY 新增的「Kafka生产」组件位于 **数据输出** 分组，负责把上游 FlowFile 中的数据逐条推送到 Kafka。

### 能力一览

- **逐行发送**：JSON 数组每行一条消息，JSON 对象一条消息；
- **消息 Key**：可从记录字段提取 key，保证同一业务键进入同一分区、保持顺序；
- **消息体裁剪**：可只发送某个字段作为消息体；
- **文本兼容**：纯文本、CSV、二进制也能作为消息发送；
- **可靠投递**：支持 `acks`、重试、压缩、linger、发送超时；
- **指定分区**：可固定写入某个分区；
- **调试安全**：调试模式跳过实际发送，不会污染目标主题。

### 核心配置

| 参数 | 说明 |
| --- | --- |
| `bootstrapServers` | Broker 地址 |
| `topic` | 目标主题 |
| `keyField` | 从记录中取该字段作为消息 key，留空则不带 key |
| `valueField` | 只发送该字段作为消息体，留空发送整行 JSON |
| `partition` | 目标分区，`-1` 由 Kafka 按 key 自动分区 |
| `acks` | `all` / `1` / `0` |
| `compressionType` | `none` / `gzip` / `snappy` / `lz4` / `zstd` |
| `lingerMs` | 发送前等待合并成批的时间 |
| `retries` | 发送失败重试次数 |
| `sendTimeoutMs` | 单条消息等待确认的超时 |

### 一个容易忽略的细节

生产组件发送完成后会 `flush` 并逐条等待发送结果，而不是「发出去就不管了」。这样一旦目标主题不可用、权限不足或者网络异常，任务会明确失败并给出原因，而不是悄悄丢消息。

例如 Broker 不可达时，日志会直接告诉你：

```text
Kafka生产组件执行失败: Topic xxx not present in metadata after 2000 ms
```

---

## 04 三个高频场景

### 场景一：Kafka → MySQL 实时落库

```text
Kafka消费 → SQL 写入（MySQL）
```

设备上报、订单事件进入 Kafka 后，用 SQL 模板逐行写入 MySQL：

```sql
INSERT INTO device_events
(event_id, device_id, temperature, status, kafka_offset)
VALUES (${eventId}, ${deviceId}, ${temperature}, ${status}, ${_offset})
```

配合位点状态，任务重启后会从上次消费的位置继续，不重复、不丢数。

### 场景二：Kafka → Kafka 主题转发

```text
Kafka消费 → Kafka生产
```

跨集群、跨主题转发时，用 `keyField` 指定业务主键，既能把数据重新分区，也能保证同一设备、同一订单的消息在分区内有序。

### 场景三：CDC → Kafka

```text
MySQL CDC → Kafka生产
```

数据库变更实时捕获后直接投递到 Kafka，供下游实时计算、搜索索引、缓存刷新等场景消费。DataY 的 CDC 组件负责位点与快照，Kafka 生产组件负责把变更事件可靠地送出去。

对应的任务 JSON 也很直观：

```json
{
  "units": [
    {
      ".id": "n1",
      ".name": "KafkaConsumer",
      "bootstrapServers": "127.0.0.1:9092",
      "topic": "device-telemetry",
      "groupId": "datay-device",
      "autoOffsetReset": "earliest",
      "batchSize": 100
    },
    {
      ".id": "n2",
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
    { "sourceId": "n1", "targetId": "n2", "sourcePort": 0 }
  ],
  "version": "1.0.0"
}
```

---

## 05 几条实践建议

- **需要顺序时一定要设置 key**：同一 key 进入同一分区，Kafka 只保证分区内有序；
- **优先 `acks=all`**：除非能接受少量丢失，否则不要用 `acks=0` 追求吞吐；
- **适当增大 `lingerMs`**：高吞吐场景下让更多消息合并成批，再配合 `lz4` / `zstd` 压缩；
- **位点状态是资产**：需要从头重放时，在任务状态页删除对应 `kafkaOffset` 键即可；
- **调试和生产分开**：调试模式不会写 Kafka、不会提交生产位点，可以放心点；
- **关注主题保留时间**：位点长期未推进且消息被清理时，组件会按策略重置，必要时及时告警。

---

## 06 关于 DataY：让数据平台回归「轻量」

如果你看到这里，可能会发现：Kafka 组件只是 DataY 的一个缩影。

**DataY 是一个轻量数据平台**，把数据集成、维度建模、任务调度、高性能数仓、智能问数放进同一套运行时。它不依赖 Hadoop、Spark 这类重型组件，**一个 JAR 就能启动，2 核 4 G 就能跑通从数据接入到报表查询的完整链路**。

它大概相当于：

> DataY = DataX + NiFi + DolphinScheduler

但不是三个工具的拼盘，而是重新设计的一体化体验：

- **数据集成**：20+ 数据源，支持 MySQL / PostgreSQL / Oracle / 达梦 / SQL Server / MongoDB CDC 实时同步，可视化画布拖拽编排；
- **维度建模**：星型 / 雪花模型，指标口径统一沉淀；
- **任务调度**：Cron、DAG 工作流、依赖编排、失败重试，单机可跑、master/worker 可分离；
- **高性能数仓**：内嵌 DuckDB，也支持 Doris、ClickHouse、DuckLake；
- **智能问数**：自然语言 → 指标 → 维度 → 取数，一句话拿报表。

更重要的是，所有能力都可以用同一套组件体系组合：Kafka、CDC、HTTP、数据库、文件、大模型…… 在画布上连起来，就是一个可调度、可观测、可恢复的实时或批量任务。

### 快速体验

- 官网：<https://datay.yuyuejia.com.cn>
- Gitee：<https://gitee.com/yuyuejia/datay>
- GitHub：<https://github.com/yuyuejia/datay>
- CNB：<https://cnb.cool/yuyuejia/datay>

下载运行包，一行命令启动：

```bash
java -jar datay-web.jar
```

浏览器打开 `http://localhost:8080`，默认账号 `admin / admin`，就可以开始拖拽你的第一条 Kafka 数据流了。

如果这篇文章对你有帮助，欢迎点个「在看」，把 DataY 分享给身边正在被数据集成折磨的同行。也欢迎加入技术交流群，一起聊聊实时数据那些事。

---

*本文基于 DataY 当前版本编写，组件参数以实际页面为准。*
