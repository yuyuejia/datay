# DuckDB 为什么这么快？我们把它塞进了 ETL 引擎里

> 从列存、向量化到 1 亿行实测：一个嵌入式分析引擎，如何让 ETL 摆脱"重型集群依赖"。

先看一组实测数字：

- 1 亿行、6 列明细数据，多维聚合（含 P95）**0.91 秒**
- 同样 1 亿行，按时间窗口过滤再聚合 **0.22 秒**
- 不导入数据库，直接查 1 亿行 Parquet 文件 **0.15 秒**

这不是跑在几十核服务器上的 ClickHouse，而是一台笔记本上的单进程 DuckDB。

过去几年，DuckDB 从一个"学术圈的小项目"变成了数据分析领域最值得关注的基建之一。它被称作"分析型 SQLite"，但这句话只说对了一半：SQLite 解决的是"把数据库嵌进应用"，DuckDB 解决的是"把**分析引擎**嵌进应用"。

这篇文章想讲清楚三件事：

1. DuckDB 为什么快，快在哪一层；
2. 它到底适合什么场景，不适合什么场景；
3. 我们为什么把它嵌进 DataY 的 ETL 引擎，以及具体怎么用。

---

## 一、DuckDB 是什么？

一句话：**一个进程内运行的列式分析数据库。**

它没有独立的服务进程，不需要安装、启动、维护一个数据库实例。你的程序 `import duckdb`，或者引入一个 JDBC 驱动，它就活在当前进程里。数据可以放在一个单文件数据库里，也可以直接查 Parquet、CSV、JSON，甚至直接查 pandas DataFrame。

几个关键特征：

- **嵌入式**：没有网络协议、没有客户端/服务端，天然省掉一轮序列化和网络往返；
- **零依赖**：C++ 编写，编译和运行都不依赖外部组件；
- **完整 SQL**：窗口函数、CTE、JOIN、子查询、复杂类型、事务，该有的都有；
- **单文件**：一个 `.duckdb` 文件就是一个数据库，复制、备份、分发都很简单；
- **开放格式**：原生支持 Parquet / CSV / JSON，能挂 S3、MinIO，也能读写 Iceberg、DuckLake；
- **MIT 协议**：商用友好。

先感受一下它的使用方式：

```python
import duckdb

# 不建表、不导入，直接查 Parquet 文件
duckdb.sql("""
    SELECT region, sum(amount) AS gmv
    FROM 'sales.parquet'
    GROUP BY region
    ORDER BY gmv DESC
""").show()
```

这就是 DuckDB 最迷人的地方：**数据在哪里，计算就在哪里**。不需要先 `CREATE TABLE`、再 `COPY` 一遍。安装也只需要 `pip install duckdb`。

它和 SQLite 的定位正好互补：

| | SQLite | DuckDB |
|---|---|---|
| 负载类型 | OLTP、点查、嵌入式事务 | OLAP、扫描、聚合、JOIN |
| 存储 | 行存 | 列存 |
| 典型查询 | `WHERE id = ?` | `GROUP BY`、全表扫描、窗口 |
| 强项 | 高并发读、低延迟点查 | 大批量分析、压缩、向量化 |

所以 DuckDB 不是"更快的 SQLite"，也不是"单机版 ClickHouse"。它是**为分析型负载重新设计的一个嵌入式引擎**。

---

## 二、为什么快？拆开看这几层

DuckDB 的快不是某个"黑科技"带来的，而是从存储、执行、并行、优化器到部署形态，每一层都针对 OLAP 做了取舍。

### 1. 列存：只读需要的列

传统的行存数据库按行组织数据。一行有 20 个字段，你只查 3 个字段，它也要把整行读出来。

DuckDB 按列组织数据。查 3 列就只读 3 列，I/O 量可能直接降到原来的 1/5 甚至更低。对于动辄几千万、上亿行的分析查询，"少读"往往比"算得快"更重要。

列存还有一个附带好处：同一列的数据类型一致、分布相似，压缩效率远高于行存。

### 2. 压缩：让数据变小，让 I/O 变少

DuckDB 会根据列的类型和分布自动选择压缩方式：

- 整数：bitpacking、delta、RLE；
- 字符串：字典编码、FSST；
- 浮点数：Chimp、Patas、ALP；
- 低基数列：字典 + 位压缩。

压缩带来的不只是省磁盘，更重要的是**减少内存带宽和 I/O 压力**。分析查询的瓶颈通常不在 CPU 算得慢，而在数据搬得慢。

在我们的测试里，1000 万行、6 列数据写入 DuckDB 后，数据库文件只有 **34.8 MB**；1 亿行是 **375 MB**。同样的数据导出成 ZSTD 压缩的 Parquet，分别是 97 MB 和 975 MB。DuckDB 的原生格式在这个数据集上压得更狠（当然，具体压缩率取决于数据分布，不是普遍规律）。

### 3. 向量化：一次算一批，而不是一行一行算

行存数据库的执行引擎通常是"一行一行"处理：取一行、判断、计算、再取下一行。每行都有函数调用、分支判断、虚函数分派的开销。

DuckDB 使用**向量化执行**：一次处理一批值（一个 vector），在紧凑的循环里批量计算。这样做有几个好处：

- 每个操作符的调用次数从"行数"降到"批次数"；
- 循环体更容易被 CPU 流水线和 SIMD 利用；
- 数据在缓存里连续存放，cache miss 更少。

这套思路来自 MonetDB/X100 的论文，后来被 Vectorwise、DuckDB 等系统发扬光大。DuckDB 官方对它的描述是"columnar-vectorized query execution engine"。

### 4. 多核：morsel-driven 并行

DuckDB 不是"一个查询开一个线程"这么粗糙。它把数据切成一块块 **morsel**（通常几万到十几万行），放到任务队列里，由线程池动态领取。哪个线程先干完，就继续领下一块。

这就是 **morsel-driven parallelism**：

- 负载天然均衡，不会因为某个线程分到"慢数据"而拖后腿；
- 算子之间可以流水线并行；
- 能根据 CPU 核数自动扩展，大数据量扫描时单机 8 核、32 核都能吃满。

### 5. 推送式流水线 + 运行时过滤

很多数据库采用"拉取式"执行：上层算子不断向下层要数据。DuckDB 更偏向**推送式**：数据从扫描算子开始，一块块推过过滤、投影、聚合、JOIN，形成流水线。

配合**谓词下推、投影下推、分区裁剪、Parquet row group 裁剪**，很多数据在扫描阶段就被过滤掉了，后面的算子根本不用处理。

另外，DuckDB 会利用列的最小/最大值、distinct 值等轻量统计信息做基数估计和 JOIN 顺序选择；即使统计信息不完整，也能给出不错的执行计划。

### 6. 嵌入式：没有网络，没有序列化

这是最容易被忽略、但收益非常直接的一层。

传统数仓的查询路径是：

```
应用 → 网络 → 数据库服务 → 执行 → 结果序列化 → 网络 → 应用
```

DuckDB 的路径是：

```
应用 → 函数调用 → 执行 → 结果直接返回
```

没有网络往返，没有结果集序列化，没有跨进程拷贝。对于 ETL 这种"读一批、算一批、写一批"的场景，省掉的开销非常可观。

当然，Java 通过 JDBC 访问时仍有 JNI 边界和结果集转换的成本，但相比一次网络往返，这个开销小得多。

DuckDB 还能和 Arrow、pandas 做零拷贝交互：Python 里可以直接对 DataFrame 跑 SQL，不需要先 `INSERT` 一遍。

### 再加一条：写入路径的 Appender

分析引擎通常"读快写慢"，但 ETL 往往要大量写入。DuckDB 提供了一个 **Appender** 接口：绕过 SQL 解析、查询规划和参数绑定，直接把行数据批量追加到存储层。

我们在 DataY 里实测过：在 1000～10000 行/批的写入场景下，Appender 比拼接多值 `INSERT ... VALUES` **快一个数量级**。这也是 DataY 的 DuckDB 写入链路默认优先走 Appender 的原因。

> **一句话总结**：DuckDB 的快，是"列存 + 压缩 + 向量化 + 多核并行 + 流水线 + 嵌入式"叠在一起的结果。它把 OLAP 该做的事做到了极致，也把不该做的事果断放弃了。

### 快的代价

任何设计都有边界。DuckDB 的取舍是：

- 擅长**大批量扫描和聚合**，不擅长**高频单行点查**；
- 擅长**单机分析**，不擅长**高并发多租户**；
- 擅长**批量写入**，不适合**高频小事务**；
- 同一个数据库文件同一时间只允许一个进程写，进程内并发写也会做冲突检测。

记住这几条，后面的场景选择就不会跑偏。

---

## 三、性能实测：1 亿行到底有多快？

### 测试环境

| 项目 | 配置 |
|---|---|
| 机器 | Apple M5，24 GB 统一内存 |
| 系统 | macOS |
| DuckDB | 1.4.4（Python 包） |
| 线程 | 8 |
| 内存限制 | 8 GB |
| 数据 | 合成订单明细，6 列：id、user_id、product、region、amount、ts |
| 口径 | 预热后执行 3 次，取最好成绩 |

测试数据是程序生成的，不是真实业务数据，目的是看引擎在不同数据量下的量级表现，不作为绝对性能承诺。

### 1000 万行：和 pandas 正面对比

| 测试项 | DuckDB | pandas | 说明 |
|---|---:|---:|---|
| 多维聚合（350 组，含 P95） | **96 ms** | 443 ms | pandas 数据已在内存 |
| 过滤 + 时间字段处理 + 分组 | **23 ms** | 454 ms | pandas 含 copy 与 period 转换 |
| 事实表 JOIN 100 万维表 | **39 ms** | — | 整数主键，输出 6 组 |
| 生成并写入 1000 万行（含建表） | **1.62 s** | — | 单进程 |
| 导出 Parquet（ZSTD） | **0.19 s** | — | 97 MB |
| 直接查询 Parquet | **16 ms** | — | 不导入数据库 |
| 数据库文件大小 | **34.8 MB** | — | 列存 + 压缩 |

这个对比不算完全公平：pandas 的数据已经在内存里，而 DuckDB 还要从磁盘读。但即便如此，DuckDB 的多维聚合仍然快 4.6 倍；到了"过滤 + 时间字段处理 + 分组"这种更接近真实 ETL 的负载，差距接近 20 倍。

原因不复杂：pandas 的 `groupby` 默认单线程，字符串、时间、空值都要逐列处理；DuckDB 则是列存 + 向量化 + 多核，天然适合这种批量聚合。

### 1 亿行：单机进入"秒级"

| 测试项 | 耗时 |
|---|---:|
| 生成并写入 1 亿行（含建表） | **16.5 s** |
| 多维聚合（1400 组，含 P95） | **0.91 s** |
| 时间窗口过滤 + 分组 | **0.22 s** |
| 导出 Parquet（975 MB） | **2.0 s** |
| 直接查询 1 亿行 Parquet | **0.15 s** |
| DuckDB 数据库文件 | **375 MB** |

1 亿行、1400 个分组、还要算 P95，0.91 秒。在很多团队的传统方案里，这类查询往往要起一个 Spark 任务，或者连一套 ClickHouse/Doris 集群；DuckDB 在一个进程里就做完了。

### 公开基准：它处在什么位置？

除了自己的实测，也可以看几个公开基准：

- **ClickBench**：约 1 亿行、43 条分析查询，数据来自真实 Web 分析场景。DuckDB 长期处在单机引擎第一梯队，常被拿来和 ClickHouse 同台比较；
- **H2O.ai db-benchmark**：面向 DataFrame 类负载的 groupby/JOIN 基准，DuckDB 长期名列前茅，明显快于 pandas；
- **TPC-H / TPC-DS**：DuckDB 官方用它们做持续回归，说明它对自己的定位不是"玩具数据库"，而是要认真跑复杂查询的。

> **读基准的正确姿势**：基准是参考答案，不是标准答案。数据分布、并发、冷热缓存、机器配置都会影响结果。真正选型时，拿你自己的数据跑一遍，比看十张排行榜更有用。

---

## 四、DuckDB 适合哪些场景？

### 最适合的"甜点区"

**1. 单机分析、数据探索**

几十 GB 到几百 GB 的明细数据，单机放得下，用 DuckDB 做 Ad-hoc 查询、探索式分析，比等 Spark 启动快得多。

**2. ETL / ELT 的中间计算**

清洗、去重、窗口函数、聚合、多表 JOIN、数据校验——这些"批内计算"是 DuckDB 的主场。与其把数据搬到目标库再算，不如在数据流经 ETL 进程时直接算完。

**3. 直接查文件**

Parquet、CSV、JSON 放在本地、S3、MinIO，DuckDB 可以原地查询，不需要先建表、先导入。对于"数据湖里一堆 Parquet，想快速看一眼"的场景，几乎没有更轻的方案。

**4. 轻量数仓 / 数据集市**

单机 BI、部门级数据集市、指标层，DuckDB + 维度建模 + 调度就能跑起来。数据量再大，可以上 DuckLake 或 Iceberg，把存储放到对象存储。

**5. 嵌入应用**

SaaS 产品内嵌分析、桌面应用本地分析、边缘设备上的数据聚合。DuckDB 可以跟着应用一起分发，不需要客户额外部署数据库。

**6. 数据质量与对账**

两边数据按主键关联，用 DuckDB SQL 算差异、算 checksum、找缺失行。比写 Java 代码或存储过程清晰得多。

**7. 测试与 CI**

用 DuckDB 在本地模拟一个数仓，跑 SQL 回归测试。启动快、零依赖、数据可丢弃，比连一套真实数仓适合得多。

### 不太适合的场景

**1. 高并发点查**

几千 QPS 的 `WHERE id = ?`，DuckDB 不是为这个设计的。用 MySQL、PostgreSQL、Redis 更合适。

**2. 高频小事务**

一行一行地插入、更新、删除，列存的优势发挥不出来，还会放大写放大和版本管理开销。

**3. 超大规模集群**

数据量到了几十 TB、PB，或者需要多节点横向扩展，应该选 ClickHouse、Doris、Spark，或者 DuckDB + DuckLake/对象存储的湖仓方案。

**4. 强多租户、细粒度权限**

DuckDB 是嵌入式引擎，没有服务端的用户体系、权限体系、审计体系。这些要由上层应用来做。

**5. 频繁单行 UPDATE / DELETE**

列存擅长批量追加和批量重写，不擅长"改一行"。CDC 场景可以用主键 `INSERT OR REPLACE` / `DELETE` 兜底，但不要指望它像 OLTP 数据库那样高效。

### 一张表看懂选型

| 维度 | DuckDB | SQLite | ClickHouse / Doris | Spark |
|---|---|---|---|---|
| 形态 | 嵌入式 | 嵌入式 | 服务端集群 | 分布式计算 |
| 强项 | 单机 OLAP | OLTP / 点查 | 高并发、大数据 OLAP | 超大规模批处理 |
| 数据量 | 单机可承载 | 单机 | 集群 | PB 级 |
| 并发 | 单写多读 | 高并发读 | 高并发 | 高吞吐 |
| 运维成本 | 几乎为零 | 几乎为零 | 中 | 高 |

一句话：**单机放得下 + 分析型负载 + 中低并发 = DuckDB 的甜点区。**

---

## 五、DataY：把 DuckDB 嵌进 ETL

聊完 DuckDB，回到我们自己的项目 **DataY**。

传统 ETL 工具（比如 DataX）擅长"搬数据"：从 A 库读，写到 B 库。但真实业务里，数据往往不能直接搬：

- 源表有重复数据，要先按主键去重；
- 字段要清洗、脱敏、类型转换；
- 要多表 JOIN 后才能落仓；
- 要算窗口指标、做数据质量校验；
- 要先把两边数据对一遍，确认没差异再切换。

这些事如果都放到目标库做，目标库压力大、链路长、排查难；如果每个需求都写一段 Java 代码，开发和维护成本又太高。

DataY 的选择是：**在 ETL 进程里内置一个 DuckDB，让数据在流动的过程中就把计算做掉。**

DataY Core 是一个轻量、可嵌入、流批一体的数据集成引擎，DataY Web 在它之上提供可视化编排、调度、建模、看板和智能问数。一个 JAR 启动，2 核 4G 就能跑通"数据集成 → 数仓 → 问数"的完整链路。

为什么不直接外挂一个 ClickHouse？因为对中小数据量来说，多一个服务就多一套部署、监控、权限和故障域；而 DuckDB 可以跟着 ETL 进程一起启动，零额外运维。数据量真的上来，再换 Doris/ClickHouse，或者把存储换成 DuckLake 也不迟。

DataY 也并不绑定 DuckDB：需要高并发、大规模查询时，可以外挂 Doris/ClickHouse；DuckDB 负责单机 ETL 计算和轻量数仓，二者按需组合。

### DuckDB 在 DataY 里的四个位置

| 位置 | 组件 | 作用 |
|---|---|---|
| **计算** | `StreamSqlUnit`、`DuckDBSql`、`DataTransform`、`Join`、`DataQualityCheck` | 在数据流中做 SQL 清洗、聚合、JOIN、去重、校验 |
| **落地** | `DuckDBWrite`、`StreamJdbcOutput`（DuckDB 模式） | 作为目标库，自动建表、批量写入 |
| **联邦查询** | `DuckDBRegister` | `ATTACH` MySQL / PostgreSQL / SQLite / DuckLake，跨源 JOIN |
| **数仓** | 维度建模、指标管理、看板、问数 | 模型物化到 DuckDB，指标查询走本地列存 |

它不是一个"外挂的 DuckDB"，而是把 DuckDB 放在了数据流经的每一段。

### 实战一：流式 SQL 清洗与去重

下面这个任务从 MySQL 读订单，按 `order_id` 去重，过滤掉无效金额，再写入 DuckDB：

```json
{
  "units": [
    {
      ".id": "mysql_in",
      ".name": "StreamJdbcInput",
      "datasource": {
        "type": "mysql",
        "url": "jdbc:mysql://127.0.0.1:3306/shop",
        "username": "reader",
        "password": "******",
        "dbschema": "shop"
      },
      "table": "orders"
    },
    {
      ".id": "dedupe",
      ".name": "StreamSqlUnit",
      "sql": "SELECT * EXCLUDE (rn) FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY updated_at DESC) AS rn FROM orders) t WHERE rn = 1 AND amount > 0"
    },
    {
      ".id": "duckdb_out",
      ".name": "DuckDBWrite",
      "table": "orders_clean",
      "model": "overwrite",
      "dbFile": "shop.duckdb"
    }
  ],
  "connections": [
    { "sourceId": "mysql_in", "targetId": "dedupe" },
    { "sourceId": "dedupe", "targetId": "duckdb_out" }
  ]
}
```

几个关键点：

- `StreamSqlUnit` 会把上游一批数据写入当前任务 DuckDB 里的同名表（示例中是 `orders`），然后执行你配置的 SQL，结果再流向下游；
- 这张 staging 表会自动创建、按批清空，写入走 Appender，读取走流式 ResultSet（`jdbc_stream_results=true`），不会把整个结果集一次性堆进内存；
- `DuckDBWrite` 会自动识别上游表结构，生成 DuckDB DDL，表不存在就建表；写入同样优先走 Appender；
- `DuckDBWrite` 的数据库文件统一放在工作目录的 `log/` 下；`dbFile` 不填时按任务实例生成，任务之间互不干扰。

这段 SQL 如果放在 MySQL 里跑，要先扫全表、再排序、再去重；放在 DataY 的 DuckDB 里，数据是一批批流过来的，计算和搬运重叠进行，延迟和资源占用都更可控。

### 实战二：MySQL CDC 实时写入 DuckDB

如果是实时同步，把输入换成 `MySQLBinlogInput`，输出用 `StreamJdbcOutput` 的 DuckDB 模式：

```json
{
  "units": [
    {
      ".id": "mysql_cdc",
      ".name": "MySQLBinlogInput",
      "datasource": {
        "type": "mysql",
        "url": "jdbc:mysql://127.0.0.1:3306/shop?useSSL=false&allowPublicKeyRetrieval=true",
        "username": "cdc",
        "password": "******",
        "dbschema": "shop"
      },
      "databaseNamePattern": "shop",
      "tableNamePattern": "orders",
      "snapshot": true,
      "includeEventMeta": true
    },
    {
      ".id": "duckdb_out",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "type": "duckdb",
        "url": "jdbc:duckdb:./data/shop.duckdb",
        "driver": "org.duckdb.DuckDBDriver",
        "dbschema": "main"
      },
      "schema": "main",
      "table": "orders",
      "model": "auto"
    }
  ],
  "connections": [
    { "sourceId": "mysql_cdc", "targetId": "duckdb_out" }
  ]
}
```

`model: "auto"` 会根据 CDC 事件类型自动选择写入策略：

| 事件 | 写入策略 |
|---|---|
| INSERT | Appender 批量追加 |
| UPDATE | 按主键 `INSERT OR REPLACE` |
| DELETE | 按主键 `DELETE` |

`snapshot: true` 表示首次运行（没有断点）时先做一次全量快照，再接着增量同步，不用手工补历史数据。`includeEventMeta: true` 会在增量事件的数据行里追加 `_event_type`、`_timestamp`，方便下游做审计和排查（快照行只有 `_event_type`）。

MySQL、PostgreSQL、Oracle、达梦、SQL Server、MongoDB 的 CDC，都可以按同样的方式落到 DuckDB；如果数据量继续增长，把输出换成 `DuckLakeWrite`，数据就落到对象存储上的 DuckLake 表，单机引擎也能管更大的数据。

### 实战三：联邦查询与跨库对账

有时候数据不适合先搬到一个地方再算。比如 MySQL 里是订单，PostgreSQL 里是客户，想直接 JOIN 看一下分布。

用 `DuckDBRegister` 把外部数据源 `ATTACH` 进来（通常放在任务的前置节点，后面接 `DuckDBSql` 或 `StreamSqlUnit`）：

```json
{
  ".id": "attach_mysql",
  ".name": "DuckDBRegister",
  "datasource": {
    "type": "mysql",
    "url": "jdbc:mysql://127.0.0.1:3306/shop",
    "username": "reader",
    "password": "******",
    "dbschema": "shop"
  },
  "alias": "shop"
}
```

然后就可以在 DuckDB 里直接查：

```sql
-- ATTACH 后的完整路径是 catalog.schema.table；
-- 这里 alias=shop、MySQL 库名=shop，所以是 shop.shop.orders
-- local_customers 是本地 DuckDB 表
SELECT o.order_id, o.amount, c.level
FROM shop.shop.orders o
JOIN local_customers c ON o.user_id = c.user_id
WHERE o.amount > 100
ORDER BY o.amount DESC
LIMIT 100;
```

DataY 会把 MySQL/PostgreSQL 的连接信息写进 DuckDB 的 `SECRET`，避免密码出现在日志里；DuckDB 负责查询计划和执行，应用只拿到最终结果集。

这个能力在"跨库数据比对"里特别好用：两边表按主键关联，DuckDB SQL 直接算差异，结果文件写到本地或 MinIO。以前要写一段程序、拉两边数据、在内存里比对，现在核心计算就是一条 SQL。


### 完整链路：2 核 4G 能跑什么？

```
MySQL / PostgreSQL / Oracle / HTTP / Redis
        │  JDBC / CDC / HTTP
        ▼
┌─────────────────────────────────────┐
│ DataY Core（一个 JVM 进程）          │
│                                     │
│  Input → StreamSqlUnit → Sink       │
│            ▲                        │
│         DuckDB                      │
│    （列存 / 向量化 / 多核）          │
└─────────────────────────────────────┘
        │
        ▼
DuckDB / DuckLake / MySQL / Doris / ClickHouse
        │
        ▼
维度建模 → 指标管理 → 分析看板 → 智能问数
```

这条链路里，DuckDB 既是"计算引擎"，也是"轻量数仓"。数据接入后先在 DuckDB 里做清洗和聚合，再物化到维度模型/事实模型，指标查询直接走本地列存。在纯 DuckDB 模式下，看板和问数都不需要再连一个外部数仓。

对于中小数据量、单机可承载的场景，这套方案的性价比很高：**不需要 Hadoop，不需要 Spark，不需要单独部署一个 ClickHouse 集群，一个 JAR 就能跑。**

---

## 六、写在最后

DuckDB 的价值，不是"再做一个更快的数据库"，而是**把分析能力下放到数据经过的地方**。

过去，分析能力被锁在数仓里：数据要先搬过去，建好表，跑完 ETL，才能查。DuckDB 把这件事拆开了——数据在文件里、在应用进程里、在 ETL 管道里，都可以直接算。

DataY 做的事，是把这种能力接进 ETL：不是把 DuckDB 当外挂，而是让它成为数据流的一部分。该清洗的时候清洗，该聚合的时候聚合，该对账的时候对账，算完再落到该去的地方。

如果你正在被这些问题困扰：

- 几千万行数据用 pandas 跑不动；
- 为了一个每天跑一次的任务起一套 Spark 集群，成本太高；
- ETL 里大量清洗逻辑散落在脚本和存储过程里，难维护；
- 想搭一个轻量数仓，但不想引入一堆组件；

不妨试试 DuckDB，也欢迎试试 DataY。

**快速开始：**

```bash
# 下载运行包
wget https://datay.yuyuejia.com.cn/download/datay-web.jar -O datay-web.jar

# 启动（单机模式）
java -jar datay-web.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx

# 浏览器打开 http://localhost:8080
# 默认账号：admin / admin
```

- 产品首页：http://datay.yuyuejia.com.cn/
- 在线 Demo：http://datay-demo.yuyuejia.com.cn/
- 源码仓库：https://gitee.com/yuyuejia/datay

如果这篇文章对你有帮助，欢迎点赞、在看、转发。也欢迎在评论区聊聊：你在什么场景下用过 DuckDB？踩过哪些坑？
