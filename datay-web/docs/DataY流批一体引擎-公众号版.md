# DataX、NiFi、Flink 都很好，为什么我们还要自研一个流批一体引擎？

> **——DataY Core 的设计与实现**
>
> 如果老板今天说：「把 MySQL 的订单数据同步到分析库，白天要实时，晚上要补历史。」
>
> 你会怎么选？
>
> 选 DataX？它擅长跑批，但实时不是它的强项。
> 选 Flink？实时没问题，但全量、编排、调度还得再配一套。
> 选 NiFi？流程可以画，但复杂 SQL、状态恢复又不够顺手。
>
> 这不是一道选型题，而是一道架构题。

---

## 01 三个工具，三种完全不同的抽象

数据集成这个领域，最容易出现的一种讨论是：

> 「DataX 和 Flink CDC 哪个好？」
> 「NiFi 能不能替代 DataX？」
> 「Flink 都流批一体了，为什么还要别的工具？」

这些问题之所以很难有标准答案，是因为 **DataX、NiFi、Flink 根本不是同一类东西**。

它们各自解决的是数据链路里的一段，而不是全部。

先把三者放回各自的位置。

---

## 02 DataX：一把锋利的「批量同步铲子」

DataX 是阿里巴巴开源的离线数据同步工具，定位非常明确：

> **在异构数据源之间，做稳定、高效的批量数据传输。**

它的核心模型是一个「星型结构」：

- **Job**：一个同步任务；
- **Reader / Writer**：两端的读写插件；
- **Framework**：负责任务切分、线程调度、缓冲和流控。

一个 DataX 任务通常长这样：

```json
{
  "job": {
    "setting": {
      "speed": { "channel": 4 }
    },
    "content": [
      {
        "reader": {
          "name": "mysqlreader",
          "parameter": {
            "username": "root",
            "password": "******",
            "column": ["id", "order_no", "amount"],
            "connection": [
              {
                "jdbcUrl": ["jdbc:mysql://127.0.0.1:3306/order_db"],
                "table": ["t_order"]
              }
            ]
          }
        },
        "writer": {
          "name": "mysqlwriter",
          "parameter": {
            "username": "root",
            "password": "******",
            "column": ["id", "order_no", "amount"],
            "connection": [
              {
                "jdbcUrl": "jdbc:mysql://127.0.0.1:3306/dw",
                "table": ["t_order"]
              }
            ]
          }
        }
      }
    ]
  }
}
```

**DataX 的优点很突出：**

- 插件生态成熟，关系型数据库、大数据存储、文件系统基本都有现成 Reader/Writer；
- 批量同步性能好，Channel 并发、流控、脏数据容忍都做得比较细；
- 部署简单，一个进程就能跑，不需要常驻集群；
- 配置直观，适合「表到表」的确定性同步。

**但它的边界同样明显：**

- **只擅长批。** 任务有明确的开始和结束，不适合 7×24 常驻的实时同步；
- **没有断点续传。** 任务失败后通常要重跑，增量只能靠 `where` 条件 + 外部调度拼出来；
- **没有 DAG 编排。** 一个 Job 就是一条 Reader → Writer 的直线，复杂流程要靠外部调度串；
- **计算能力有限。** 主要是搬运，复杂清洗、关联、聚合要交给下游；
- **没有常驻状态。** 水位、位点、失败恢复，都不在它的模型里。

一句话总结：

> **DataX 是一把很好的铲子，但它不是一条流水线。**

---

## 03 NiFi：一条可视化的「数据传送带」

Apache NiFi 的定位不是「同步工具」，而是 **数据流编排平台**。

它的核心抽象是：

- **Processor**：一个处理节点，负责读取、转换、路由、写入；
- **Connection**：节点之间的连线，带队列和背压；
- **FlowFile**：在连线上流动的数据包，包含内容和属性。

NiFi 最吸引人的地方是「可视化」：

- 拖拽 Processor，连线，配置参数，一条数据流就出来了；
- 每条连线都有队列，可以设置背压阈值；
- 内置 Provenance（数据溯源），能查到每条数据经过了哪些节点；
- 支持内容仓库、流文件仓库，具备一定的可靠性和恢复能力；
- 集群模式可以通过 ZooKeeper 做协调；
- 支持 Site-to-Site，跨实例传输数据。

**NiFi 适合什么？**

- 数据来源多、流向复杂、需要可视化编排的场景；
- 数据需要分流、合并、格式转换、协议适配的场景；
- 需要看到「数据从哪来、到哪去、经过了什么」的场景；
- 流式接入和批处理都能覆盖，但更偏向「数据流动」而不是「数据计算」。

**但 NiFi 也有它的代价：**

- **重。** 它是一个完整的 JVM 服务，集群还要额外的协调组件；
- **不是 SQL 计算引擎。** 复杂关联、聚合、窗口计算，不是它的主场；
- **Processor 多了以后，流程容易变成「连线蜘蛛网」**，维护成本上升；
- **状态与断点续传不是它的核心抽象。** CDC 位点、增量水位这类事情，通常还要依赖具体 Processor 或外部系统；
- **对中小团队来说，学习和运维成本不低。**

一句话总结：

> **NiFi 像一条可控的传送带，但它不负责把数据算明白。**

---

## 04 Flink：一台真正的「分布式流计算发动机」

Apache Flink 的定位是 **分布式、有状态、流批一体的计算引擎**。

它不是数据同步工具，而是计算引擎。它的核心能力是：

- **真正的流处理**：数据一到就处理，不需要先攒成批；
- **事件时间与 Watermark**：能处理乱序数据、迟到数据；
- **有状态计算**：Keyed State、Operator State、窗口、CEP；
- **Checkpoint / Savepoint**：失败恢复、版本升级、状态迁移；
- **Exactly-Once 语义**：在端到端条件满足时，做到精确一次；
- **流批一体**：把批看作有界流，用同一套 API 处理；
- **丰富的 Connector**：Kafka、JDBC、Elasticsearch、Hive、Iceberg、CDC 等。

Flink 的强大，是建立在「一整套分布式运行时」之上的：

```text
JobManager + TaskManager + Checkpoint Storage + Resource Manager
```

这也意味着：

- 需要集群，至少要有 JobManager 和 TaskManager；
- 需要状态后端，RocksDB / 文件系统 / 对象存储；
- 需要 Checkpoint 存储，保证故障恢复；
- 需要资源管理，YARN / Kubernetes / Standalone；
- 需要有人懂 Watermark、State、Checkpoint、反压、Slot、并行度。

**Flink 适合什么？**

- 低延迟、高吞吐的实时计算；
- 复杂事件处理、窗口聚合、流式 Join；
- 对 Exactly-Once 有要求的有状态流任务；
- 数据量大、并发高、需要水平扩展的场景。

**但对很多团队来说，Flink 是「牛刀」：**

- 为了同步几张表，先搭一套 Flink 集群；
- 为了跑一个分钟级任务，先理解 Checkpoint 和状态后端；
- 为了做一次全量同步，可能还要再配一套批任务，或者依赖 CDC 的快照模式；
- 集群、状态、监控、调优，都是长期成本。

一句话总结：

> **Flink 是重型计算引擎，不是轻量集成工具。**

---

## 05 三者放在一起，问题就出现了

把 DataX、NiFi、Flink 放在同一张表里，会更清楚：

| 维度 | DataX | NiFi | Flink |
| --- | --- | --- | --- |
| 核心定位 | 批量数据同步 | 数据流编排 | 分布式流计算 |
| 数据模型 | Job / Task / Reader / Writer | Processor / Connection / FlowFile | DataStream / Table / SQL |
| 批处理 | 强 | 可以 | 可以（有界流） |
| 流处理 | 弱 | 可以，但不是计算引擎 | 强 |
| 状态与断点 | 基本没有 | 有队列与溯源，CDC 位点靠组件 | Checkpoint / Savepoint |
| 计算能力 | 弱 | 中 | 强 |
| 部署形态 | 单机进程 | JVM 服务，集群要协调组件 | 分布式集群 |
| 学习成本 | 低 | 中高 | 高 |
| 最适合 | 表到表批量同步 | 多源数据流编排 | 大规模实时计算 |

它们不是竞争关系，而是各自解决一段：

- DataX 解决「怎么把数据搬过去」；
- NiFi 解决「数据怎么流动、怎么路由」；
- Flink 解决「数据怎么算、怎么保证状态」。

问题在于，**当你把这三段拼成一条链路时，你也就同时拥有了三套运行时、三套状态、三套部署、三套排障方式。**

一个很典型的场景：

```text
MySQL ──▶ DataX 全量 ──▶ 临时表 ──▶ Spark SQL 清洗 ──▶ 数仓
   │
   └────▶ Flink CDC ──▶ Kafka ──▶ Flink 计算 ──▶ 另一个 Sink
```

这条链路能跑，但它有几个天然的问题：

1. **批和流是两套代码。** 同一个清洗逻辑，要在 DataX 的 SQL、Flink 的 SQL 里各写一遍；
2. **状态是两套。** 全量没有断点，流有 Checkpoint，出了问题不知道从哪恢复；
3. **Schema 是两套。** 源表加字段，两边都要改；
4. **背压是断开的。** DataX 的 Channel、Flink 的反压、NiFi 的队列，各管各的；
5. **排障是三个入口。** 同步失败看 DataX，延迟看 Flink UI，数据不对查 Spark。

**组件越多，链路越像「集成」；抽象越多，系统越难「一体」。**

---

## 06 DataY 的答案：不是第四个工具，而是一套运行时

我们做 DataY 的出发点，不是再写一个 DataX，也不是再做一个 Flink。

而是想问一个问题：

> **如果数据集成只需要「把数据从 A 搬到 B，中间做加工」，为什么批和流要分成两套运行时？**

DataY Core 是 DataY 自研的流批一体数据集成引擎。它的答案很直接：

> **用同一套组件模型、同一套 DAG、同一套状态、同一套队列，同时承载批和流。**

它不是一个需要独立部署的集群，而是一个可以：

- 作为独立 JAR 运行；
- 嵌入到 Java 应用里；
- 作为 DataY Web 的执行引擎；

的轻量引擎。一个 JAR，内嵌 DuckDB，2 核 4G 就能跑起来。

它的设计原则只有五条：

1. **任务定义只有一份**：JSON DAG，画布、CLI、嵌入模式共用；
2. **组件只有一套**：Source / Operator / Sink，批流复用；
3. **状态只有一套**：随数据流走，统一落盘；
4. **计算尽量内嵌**：DuckDB 进进程，不额外拉起计算集群；
5. **默认单机，需要时再扩**：不把分布式当成出厂默认。

---

## 07 它是怎么做到的？

### 7.1 一张 DAG，就是全部任务

一个 DataY Core 任务，本质上是一张有向无环图：

```json
{
  "units": [
    {
      ".id": "in",
      ".name": "MySQLBinlogInput",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306/order_db",
        "username": "datay",
        "password": "******",
        "dbschema": "order_db"
      },
      "tableNamePattern": "t_order.*",
      "snapshot": true
    },
    {
      ".id": "sql",
      ".name": "StreamSqlUnit",
      "sql": "select order_id, user_id, amount, status, updated_at from t_order where status = 'PAID'"
    },
    {
      ".id": "out",
      ".name": "DuckLakeWrite",
      "table": "dwd_order_paid",
      "model": "append"
    }
  ],
  "connections": [
    { "sourceId": "in",  "targetId": "sql" },
    { "sourceId": "sql", "targetId": "out" }
  ]
}
```

- `units` 是组件：读、算、写、路由；
- `connections` 是连线：决定数据流向；
- 引擎负责拓扑排序、并发执行、队列传递、失败中断和状态恢复。

同一份 JSON，可以：

- 在 Web 画布上拖出来；
- 交给 Core 独立执行；
- 嵌进其他 Java 应用里运行。

**任务定义是唯一的「真相来源」**，而不是每个引擎各存一份。

### 7.2 FlowFile：数据不是裸数据，而是一个信封

组件之间传递的不是一个 `List<Row>`，而是 **FlowFile**。

一个 FlowFile 里至少有四类信息：

| 内容 | 作用 |
| --- | --- |
| `JSONArray` 数据 | 当前批次的数据行 |
| `TableMeta` | 字段名、字段类型、库表信息 |
| attributes | 事件类型、时间戳、表名等 |
| `statusMap` | 增量水位、Binlog 位点等状态 |

这带来一个很重要的结果：

> **下游组件不只知道「数据是什么」，还知道「数据从哪来、是什么结构、处理到哪了」。**

所以它才能自动建表、自动做类型转换、自动传递断点。

### 7.3 有界队列：背压是天然的

组件之间的连接不是方法调用，而是**有界阻塞队列**。每条连线一个队列，容量默认 20。

```java
new LinkedBlockingQueue<>(QUEUE_SIZE)
```

下游写得慢，队列满了，上游的 `put` 就会阻塞。

不需要额外的限流组件，也不会因为上下游速度不一致把内存打爆。

另外，每个组件可以配置 `parallelism`。引擎按所有组件的并发数之和创建线程池，每个组件记录自己的活跃线程数，最后一个线程结束时才向下游发 `_end`，避免并发线程之间互相踩结束信号。

### 7.4 `_start` 和 `_end`：流和批的分界线

DataY 的运行时里，有三种东西在流动：

- **`_start`**：源组件启动信号；
- **正常 FlowFile**：带数据、元数据和状态；
- **`_end`**：上游结束信号。

于是：

- **批处理** = 有 `_end` 的流：读完、处理完、发结束信号，任务退出；
- **流处理** = 没有 `_end` 的流：CDC 组件持续产生变更，下游持续消费。

对下游组件来说，这两种情况没有区别。它只需要做一件事：

> **收到 FlowFile，处理它，决定是否写给下游。**

这就是 DataY 流批一体的核心：

> **不是让流去兼容批，也不是让批去模拟流，而是把两者都收敛成「FlowFile 在 DAG 上流动」。**

### 7.5 换一个输入组件，批就变成了流

假设你原来做的是每天凌晨的全量同步：

```json
{
  ".id": "in",
  ".name": "StreamJdbcInput",
  "datasource": { "url": "jdbc:mysql://...", "dbschema": "order_db" },
  "table": "t_order"
}
```

现在业务要求实时，只需要把输入组件换掉：

```json
{
  ".id": "in",
  ".name": "MySQLBinlogInput",
  "datasource": { "url": "jdbc:mysql://...", "dbschema": "order_db" },
  "tableNamePattern": "t_order.*",
  "snapshot": true
}
```

**下游的 SQL、路由、清洗、写入组件，一行都不用改。**

因为它们看到的都是同一种东西：带着数据和表元数据的 FlowFile。

### 7.6 内嵌 DuckDB：把计算也收进进程

传统链路里，数据同步完之后，还要交给 Spark/Flink 做加工。

DataY 直接把 **DuckDB** 嵌进引擎：

- 列式存储、向量化执行、多线程；
- 用标准 SQL 做清洗、关联、聚合；
- 不需要额外部署计算集群；
- 对 GB 到 TB 级分析场景，性能足够。

中间的 `StreamSqlUnit` 组件做三件事：

1. 把上游 FlowFile 写入 DuckDB 临时表（`DuckDBAppender` 批量写入）；
2. 在 DuckDB 上执行用户配置的 SQL；
3. 把结果重新封装成 FlowFile，发给下游。

对批处理来说，这是一次 ELT；对流处理来说，这是一次微批计算。

**同一段 SQL、同一个组件、同一条执行路径。**

### 7.7 状态随流走：断点续传不靠「重跑全量」

流批一体真正难的地方，不是数据怎么流，而是**断了以后从哪里继续**。

DataY 的状态设计有三层：

**第一层：状态随数据流走。**

有状态组件把状态写进 FlowFile 的 `statusMap`：

- JDBC 增量同步：`<组件id>.lastIncrValue.<表名>`
- MySQL Binlog：`binlogFile`、`binlogPosition`
- PostgreSQL CDC：WAL 的 LSN
- Oracle CDC：SCN
- MongoDB CDC：resumeToken

**第二层：在 Sink 端统一落盘。**

当前 FlowFile 带状态，并且当前组件是 Sink（或没有下游）时，状态会被合并进任务上下文并持久化。

持久化策略可插拔：

| 策略 | 适用场景 | 存储位置 |
| --- | --- | --- |
| `local` | 单机部署 | 本地状态文件 |
| `minio` | master/worker 分离部署 | MinIO 桶，多节点共享 |

**第三层：CDC 的「全量 + 增量」衔接。**

以 `MySQLBinlogInput` 为例，开启 `snapshot` 后：

1. 先查询当前 Binlog 位点；
2. 从该位点做历史全量快照；
3. 快照完成后，从刚才记录的位点开始消费增量；
4. 后续每次处理都更新位点。

这样全量和增量之间不会出现「快照期间的新数据漏掉」的问题。

需要说清楚的是：DataY 默认提供的是 **至少一次（at-least-once）** 语义。目标端需要按主键做幂等写入，重复事件最多覆盖一次，不会产生重复行。

它不是 Flink 那种基于分布式快照的 Exactly-Once，这是轻量架构的取舍。

### 7.8 Schema 也是数据的一部分

数据集成另一个高频痛点是 Schema：

- 源表加字段，目标表没加，任务失败；
- MySQL 的 `datetime` 写到 DuckDB 变成字符串；
- 每次同步前都要手工建表、改表。

DataY 的做法是把表结构作为数据的一部分：

- 输入组件读取源表时，同时读取 `TableMeta`；
- `TableMeta` 随 FlowFile 传给下游；
- 写入组件根据目标库类型做 DDL 转换，表不存在就按源表结构建表；
- MySQL 的 DDL 变更也会随 Binlog 传下去，在目标端执行。

**Schema 同步不再是一个独立组件，而是数据集成引擎的内置能力。**

### 7.9 组件可扩展：注解注册，自动发现

DataY Core 的组件不是写死的。

一个组件只需要继承 `FlowComponent`，实现 `execute(FlowFile)`，再打上注解：

```java
@ComponentRegister(
    value = "StreamJdbcInput",
    name = "数据源输入",
    group = "数据输入",
    desc = "从关系型数据库流式读取数据"
)
public class StreamJdbcInput extends AbstractJdbcInput {
    // ...
}
```

引擎启动时会扫描组件包，把组件编码、名称、分组、描述注册到 `ComponentFactory`：

- 前端画布的组件面板自动出现；
- JSON 里的 `.name` 通过工厂反射创建实例；
- 组件参数通过 Bean 注入，不需要为每个组件写一套解析代码；
- 新增一个数据源或算子，不需要改引擎主流程。

这也是 DataY 能持续补 CDC、路由、大模型算子、对象存储写入等组件的原因：**引擎稳定，组件生长。**

---

## 08 和三位前辈比，DataY 的取舍

| 维度 | DataX | NiFi | Flink | DataY Core |
| --- | --- | --- | --- | --- |
| 定位 | 批量同步 | 数据流编排 | 分布式流计算 | 流批一体集成引擎 |
| 批处理 | 强 | 可以 | 可以 | 强 |
| 流处理 | 弱 | 可以 | 强 | CDC / 微批 |
| DAG 编排 | 无 | 强 | 有 | 强 |
| 状态与断点 | 无 | 组件级 | Checkpoint | 内置状态与断点 |
| 计算能力 | 弱 | 中 | 强 | 中（内嵌 DuckDB） |
| 部署 | 单进程 | JVM 服务 | 集群 | 一个 JAR |
| 最小资源 | 低 | 中高 | 高 | 2 核 4G |
| 适合规模 | 表级批量 | 多源编排 | PB 级实时 | GB 到几十 TB |

DataY 的取舍很明确：

- **不做 Flink 那种大规模分布式计算**，但把大多数团队的日常计算收进 DuckDB；
- **不做 NiFi 那种通用数据流平台**，但保留组件化 DAG 和可视化编排；
- **不做 DataX 那种纯批量搬运**，但把批量同步作为流的一种特例；
- **不要求你先有一套集群**，但需要扩容时可以拆成 master/worker。

一句话：

> **DataY 不是要取代谁，而是把三者最常用的那部分能力，收进一套运行时。**

---

## 09 它不适合谁？

把边界说清楚，比把话说满更重要。

DataY Core 不适合：

- **PB 级数据湖仓**：数据量到了 PB，Spark/Flink/Iceberg 体系更合适；
- **毫秒级、百万级并发的流计算**：应该交给 Flink；
- **复杂事件时间语义**：窗口、Watermark、迟到数据、CEP，不是它的目标；
- **需要分布式 Shuffle 的大规模批处理**：GB 到 TB 级可以，PB 级不合适；
- **强依赖 Kafka 生态的链路**：DataY 可以直接读 CDC，不需要 Kafka；但如果你已经有成熟 Kafka 体系，继续用也合理。

DataY Core 真正擅长的是：

- 数据库之间的全量 / 增量同步；
- CDC 实时同步到 DuckDB / DuckLake / Doris / ClickHouse；
- 轻量级流批一体的清洗、过滤、路由、聚合；
- 嵌入到其他 Java 应用里，作为数据集成 SDK；
- 在 2 核 4G 的环境里跑通完整链路。

---

## 10 DataY 是什么

DataY 是一套**开源、轻量的一体化数据平台**：

> 一个 JAR、内嵌 DuckDB，2 核 4G 就能跑通「数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用」的完整链路。

如果用一句话概括它的定位：

> **DataY = DataX + Nifi + DolphinScheduler**

但这个「等于」不是把三个开源项目打包在一起，而是用同一套运行时，重新实现数据同步、数据流编排和任务调度这三类能力。

它由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行或嵌入 Java 应用 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数 |

**核心能力：**

- 🔌 **数据集成**：MySQL / PostgreSQL / Oracle / SQL Server / SAP HANA / Doris / ClickHouse / DuckDB 等十余种数据源；
- 🔄 **四种 CDC**：MySQL Binlog、PostgreSQL WAL、Oracle LogMiner、MongoDB Change Streams；
- 🧩 **一套组件模型**：Source / Operator / Sink，批流共用；
- 🛡️ **断点续传**：增量水位、Binlog 位点、LSN、SCN、resumeToken 统一持久化；
- 🏗️ **自动建表**：跨库 DDL 转换，表不存在自动创建；
- 🚀 **内嵌 DuckDB**：SQL 加工不需要额外计算集群；
- ⏰ **任务调度**：Quartz Cron 精确到秒，DAG 拓扑编排，master/worker 可分离；
- 📦 **可嵌入**：Core 可以作为一个 Java 库，嵌进你自己的应用。

**快速开始：**

```bash
git clone https://github.com/yuyuejia/datay
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

也可以只用 Core：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

产品首页：<http://datay.yuyuejia.com.cn/>  
在线体验：<http://datay-demo.yuyuejia.com.cn/>  
项目地址：<https://github.com/yuyuejia/datay>

---

## 11 写在最后

DataX、NiFi、Flink 都是非常优秀的项目。

它们的问题不是「不好用」，而是**它们各自代表了一种不同的抽象，拼在一起时，复杂度会叠加**。

DataY 想做的，不是再发明一个更复杂的抽象，而是把数据集成最常用的那部分，收敛成一套简单的运行时：

- 一份 JSON，描述整条 DAG；
- 一个 FlowFile，承载数据、结构和状态；
- 一条有界队列，解决背压；
- 一个 `_end` 信号，区分批和流；
- 一个内嵌 DuckDB，解决轻量计算；
- 一套状态存储，解决断点续传。

**让批和流的区别，从「两套引擎」变成「有没有结束信号」。**

这，就是 DataY 对流批一体的理解。
