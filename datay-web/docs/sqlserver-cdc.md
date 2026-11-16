# SQL Server CDC：不用 Debezium，也能把变更抓得干干净净

> 一个被低估的能力：SQL Server 自带 CDC，开两条命令就能用。
> 但真把它接进生产链路，坑比想象的多。这篇把原理、实操、限制和工程化一次性讲清楚。

---

## 一、先想清楚：你为什么需要 CDC

做数据集成，绕不开一个根本问题：**源库的数据在变，你怎么知道它变了？**

朴素的做法有三种，各有各的痛：

| 方案 | 怎么做 | 痛点 |
|------|--------|------|
| 全量重刷 | 每隔一段时间整表拉一遍 | 表一大就废，源库压力顶不住，延迟以小时计 |
| 时间戳轮询 | `WHERE update_time > 上次时间` | 硬更新、物理删除永远抓不到；时间字段还得靠业务自觉维护 |
| 触发器写日志表 | 建 trigger 记录变更 | 侵入业务表、影响写入性能、DDL 一改全崩 |

CDC（Change Data Capture，变更数据捕获）的思路完全不同：**数据库自己就在记日志，为什么不直接读日志？**

大多数数据库把这套能力做成了内置功能——MySQL 是 Binlog，PostgreSQL 是逻辑复制槽，Oracle 是 LogMiner，而 SQL Server，就是本文的主角 **CDC**。

---

## 二、SQL Server CDC 到底是什么

一句话：**它把事务日志里的 INSERT / UPDATE / DELETE，搬进一组系统自动维护的"变更表"（change table）里。**

几个关键认知，先帮你把概念对齐：

**1. CDC 是数据库级 + 表级的两级开关**

- `sp_cdc_enable_db`：打开整个数据库的 CDC。执行后数据库会多出一个 `cdc` schema。
- `sp_cdc_enable_table`：指定哪张表要捕获。每张表对应一个 **capture instance**，并生成一张 `cdc.<schema>_<table>_CT` 变更表。

**2. 捕获动作靠 SQL Server Agent 的 capture job**

这是最容易踩坑的地方。打开 CDC 不等于数据马上就有了——真正干活的是 Agent 里的两个作业：

- **capture job**：默认每 **5 秒**扫一次事务日志，把新变更写进 change table；
- **cleanup job**：默认每天凌晨 2 点跑，清理超过保留期（默认 **3 天**）的变更。

> **SQL Server Express 版没有 SQL Server Agent，因此不支持 CDC。**
> 同理，Agent 服务停着，change table 就是一口不动的枯井——你会看到任务正常启动，但一条数据都不来。

**3. 它不侵入业务表**

不像触发器，CDC 不在你的表上加任何东西。业务写入该怎么写还怎么写，只是事务日志多被"顺带看了一眼"。这是它相比触发器方案最大的优势。

**4. 它给的是行级前后镜像**

UPDATE 事件默认能拿到**修改前**和**修改后**两份完整行数据，这对下游做 upsert、识别主键变更、审计diff 都非常关键。

---

## 三、内部链路：一条变更的完整旅程

```
业务 DML
   │
   ▼
事务日志 (transaction log)
   │   ← capture job 每 5 秒扫描
   ▼
变更表 cdc.dbo_orders_CT
   │   （__$operation / __$start_lsn / __$seqval / __$update_mask + 业务列）
   ▼
表值函数 cdc.fn_cdc_get_all_changes_dbo_orders(from_lsn, to_lsn, 'all update old')
   │
   ▼
你的同步程序 / ETL 引擎
```

变更表里每一行都带三个"元数据列"，理解它们就理解了 CDC：

| 列 | 含义 |
|----|------|
| `__$start_lsn` | 该变更所属事务的日志序列号，**全局单调递增**，天然适合做位点 |
| `__$seqval` | 同一事务内的操作序号，用于还原事务内顺序 |
| `__$operation` | 1=DELETE，2=INSERT，3=UPDATE 前镜像，4=UPDATE 后镜像 |
| `__$update_mask` | 二进制掩码，标出本次 UPDATE 到底改了哪几列 |

注意 `__$operation` 的 **3 和 4 是成对出现的**：一条 UPDATE 会在 change table 里留下两行，前镜像 + 后镜像。如果你的程序只处理 4 不处理 3，就会看到"数据是新的，但不知道旧值是什么"。

---

## 四、五步跑通：从零开启 CDC

### 第 1 步：打开数据库 CDC

```sql
USE [your_database];
EXEC sys.sp_cdc_enable_db;

-- 校验
SELECT name, is_cdc_enabled
FROM sys.databases
WHERE name = 'your_database';
```

### 第 2 步：确认 Agent 在跑，捕获作业已启动

```sql
EXEC sys.sp_cdc_help_jobs;
EXEC sys.sp_cdc_start_job @job_type = N'capture';
```

这一步不做，后面全是白忙。

### 第 3 步：为表开启捕获

```sql
EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name   = N'orders',
    @role_name     = NULL,
    @supports_net_changes = 0;

-- 校验
SELECT s.name AS schema_name, t.name AS table_name, t.is_tracked_by_cdc
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE s.name = N'dbo';
```

> **别省略 `@captured_column_list`**。不指定就是捕获全部列，这是推荐做法。
> 如果只捕获部分列，未捕获的列在下游可能被写成 NULL，UPDATE / DELETE 的镜像也会残缺。

### 第 4 步：给采集账号授权

```sql
GRANT SELECT ON [dbo].[orders] TO [datay];
GRANT SELECT ON SCHEMA::cdc TO [datay];   -- 变更表 + 表值函数 + lsn_time_mapping
```

`cdc` schema 的权限最容易被漏掉，报错通常是"读取 capture instance 失败"。

### 第 5 步：验证变更真的进来了

```sql
INSERT INTO dbo.orders VALUES (1, N'alice', 10.50);
UPDATE dbo.orders SET amount = 11.11 WHERE id = 1;
DELETE FROM dbo.orders WHERE id = 1;

-- 直接看变更表
SELECT __$operation, __$start_lsn, id, name, amount
FROM cdc.dbo_orders_CT
ORDER BY __$start_lsn, __$seqval;
```

你会在 5 秒左右看到 1 / 2 / 3 / 4 对应的事件。

---

## 五、工程化：把 CDC 接进实时链路，要注意什么

能查出来只是开始。真正难的是**持续、可靠、不丢不重地同步**。这里有几个设计要点。

### 1. 用 LSN 做位点，别用时间

LSN 是日志序列号，单调递增，还自带事务边界语义。时间戳会因时钟、时区、批量提交而失真，LSN 不会。

标准做法是**按窗口推进**：

```
下界 = fn_cdc_increment_lsn(上次位点)
上界 = fn_cdc_get_max_lsn()
```

把 `[下界, 上界]` 作为一个窗口，窗口内所有表都处理完了，才把上界持久化为新位点。

> **为什么必须"窗口内全部完成才推进"？**
> 一个事务可能同时改了 3 张表。如果按表各自推进位点，中途崩溃重启就会出现"表 A 前进了、表 B 没跟上"，同事务的多表变更从此错位，数据永久不一致。

### 2. 位点推进的语义是"至少一次"

窗口级提交意味着：崩溃重启后，最后一个窗口可能被重复投递。这不是 bug，是权衡。

应对方式很朴素：**下游写入必须幂等**。

- INSERT → `INSERT OR REPLACE` / upsert
- UPDATE → 按主键 upsert
- DELETE → 按主键 delete

只要写入幂等，"至少一次"就等价于"恰好一次"的业务效果。

### 3. 首次全量 + 增量，顺序不能反

增量 CDC 默认只捕获**组件启动之后**的变更。存量数据怎么办？标准答案是：

```
① 记录当前 max LSN  →  ② 全量读存量  →  ③ 从 ① 的 LSN 开始增量
```

先记 LSN 再全量，是为了让快照窗口内发生的变更还能被增量补上。顺序反了，快照期间的数据就永久丢了。

> 代价是可能重复：快照读到的数据和 CDC 补放的变更存在交集。同样靠下游幂等消化。

### 4. 表结构变了怎么办

**SQL Server CDC 不传 DDL。** capture instance 的列结构在 `sp_cdc_enable_table` 那一刻就固定了。源表 `ALTER TABLE ADD COLUMN` 之后：

- change table 里不会有这一列；
- 你的同步程序也不会知道表结构变了。

解法只有两条：重建 capture instance，或者重跑全量。这也是为什么生产上要**把 DDL 变更纳入发布流程**，而不是随手改表。

---

## 六、必须知道的限制清单

这些不是"可能"，是 SQL Server CDC 的硬约束，选型前请逐条对照：

| 限制 | 说明 | 应对 |
|------|------|------|
| 依赖 SQL Server Agent | Agent 停 → 无变更；Express 版根本没有 | 监控 Agent 与 capture job 状态 |
| 默认只保留 3 天 | 停机超期，位点被清理，变更永久丢失 | 调大 `@retention`，或做强告警 |
| 不捕获 DDL | 加列、改类型不会同步 | 重建 capture instance / 重跑全量 |
| 每表最多 2 个 capture instance | 设计上就是给灰度切换用的 | 别把它当无限副本 |
| `text/ntext/image` 前镜像恒为 NULL | DELETE / UPDATE 前镜像拿不到旧值 | 删除只依赖主键，通常可接受 |
| 计算列在 change table 中恒为 NULL | 捕获时就不落值 | 下游自行计算，或改用普通列 |
| `varchar(max)` 等大字段前镜像可能为空 | 只有真正变更时才有值 | 不要依赖前镜像做大字段比对 |
| Azure SQL Database 实现不同 | 与 SQL Server 不是一套 CDC | 面向 SQL Server / Managed Instance |

**关于保留期**，值得单独提醒：

```sql
EXEC sys.sp_cdc_help_jobs;

-- 保留 7 天（单位：分钟）
EXEC sys.sp_cdc_change_job @job_type = N'cleanup', @retention = 10080;
```

CDC 不是消息队列，它没有"消费位点回退"的概念。位点一旦早于最小可用 LSN，就只剩一条路：重跑全量。所以**停机时间必须以保留期为红线做监控**。

---

## 七、横向对比：SQL Server CDC 该不该用

| 维度 | SQL Server CDC | Debezium | 触发器 | 时间戳轮询 |
|------|----------------|----------|--------|------------|
| 额外组件 | 无（内置） | Kafka + Connect | 无 | 无 |
| 对业务表侵入 | 无 | 无 | **有** | 无 |
| 能否捕获删除 | ✅ | ✅ | ✅ | ❌ |
| 能否拿到前镜像 | ✅（部分类型受限） | ✅ | 需自己设计 | ❌ |
| DDL 捕获 | ❌ | ✅ | ❌ | ❌ |
| 运维成本 | 低 | **高** | 中 | 低 |
| 适用场景 | 存量 SQL Server、追求轻量 | 异构多源、需要 schema 演进 | 临时小表 | 仅追加型数据 |

结论很直接：

- **你的源库就是 SQL Server，且不想为了同步再养一套 Kafka** → 内置 CDC 是最优解；
- **需要捕获 DDL、需要跨多种数据库统一接入** → Debezium 更合适；
- **Express 版、或拿不到 Agent 权限** → 老实做全量 + 时间戳增量，并接受它的缺陷。

---

## 八、在 DataY 里怎么用

如果不想自己写 LSN 窗口、位点持久化、前后镜像配对、重连重试这些脏活，可以直接用 DataY 的 `SQLServerCDCInput` 组件。它把这套工程细节封装好了：

- **LSN 窗口级位点推进**：一个窗口内所有目标表处理完才推进，同事务多表变更不会漏；
- **前后镜像配对**：自动把 `__$operation` 3/4 合成一个 UPDATE 事件，前镜像挂在 `__before` 里；
- **断点续传**：位点随数据流持久化，重启自动从 `fn_cdc_increment_lsn` 继续；
- **首次全量快照**：`snapshot=true`，先记 LSN 再全量，然后无缝切增量；
- **自动开启 CDC**：`autoEnableCdc=true`，省掉手工建 capture instance；
- **连接级重连**：连续失败超阈值才判任务失败，不会出现"连接断了但任务显示成功"。

最小配置长这样：

```json
{
  ".id": "sqlserver-cdc-input",
  ".name": "SQLServerCDCInput",
  "sourceId": {
    "type": "sqlserver",
    "url": "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_cdc",
    "username": "datay",
    "password": "******",
    "dbschema": "dbo"
  },
  "schema": "dbo",
  "tableNamePattern": "ORDERS|CUSTOMERS",
  "snapshot": true,
  "pollIntervalMs": 1000
}
```

常用参数：

| 参数 | 说明 | 默认 |
|------|------|------|
| `schema` | 采集的 schema，缺省取数据源配置，再缺省 `dbo` | `dbo` |
| `tableNamePattern` | 表名过滤正则，不区分大小写 | 全部已开启 CDC 的表 |
| `startLsn` | 指定起始 LSN（十六进制） | 上次位点 / 当前位置 |
| `pollIntervalMs` | 轮询间隔（毫秒） | `1000` |
| `autoEnableCdc` | 未开启时自动执行开启语句（需 `db_owner`） | `false` |
| `snapshot` | 首次先全量后增量 | `false` |
| `reconnectMaxAttempts` | 连续重连失败上限 | `5` |

配套 `StreamJdbcOutput`（`model=auto`）就能把变更落到 DuckDB / MySQL / 其他 JDBC 目标，写入策略自动按事件类型选择：

| 事件 | 写入策略 |
|------|----------|
| INSERT | 批量 Appender / 批量 insert |
| UPDATE | 按主键 `INSERT OR REPLACE` |
| DELETE | 按主键 `DELETE` |

---

## 九、写在最后

SQL Server CDC 是一个"开箱即用、但不开箱就不知道有坑"的能力。

它的价值在于：**不引入任何外部组件，就把数据库变成了一个可订阅的变更流**。对大量还在用 SQL Server 的企业来说，这意味着实时同步的门槛从"搭一套 Kafka 集群"降到"执行两条存储过程"。

但它的边界同样清晰：依赖 Agent、保留期有限、不传 DDL、部分类型镜像缺失。这些不是实现缺陷，而是设计取舍。**理解取舍，比背参数重要得多。**

一句话总结：

> 用 CDC 之前，先确认 Agent 在跑、保留期够长、下游写入幂等、DDL 有人管。
> 这四件事做到了，SQL Server CDC 就是一套非常省心的实时同步底座。

---

## 十、关于 DataY：一个 JAR，跑起整套数据平台

上面所有 CDC 的工程细节——LSN 窗口、位点持久化、前后镜像配对、断点续传、重连重试——如果每接一个源库都要重写一遍，成本比想象的高得多。

**DataY 就是把这些事做完的那个东西。**

### 它是什么

DataY 是一个**轻量级一体化数据平台**，定位很直接：

> 数据平台可以很小，功能不打折。

传统数据平台动辄十几台机器、上百 G 内存，Hadoop + Spark + 调度 + 同步工具层层堆叠，运维门槛高得需要专职大数据工程师。DataY 走的是另一条路：**自研流批一体引擎，一个 JAR 启动，2 核 4G 就能跑通从数据接入到报表问数的完整链路。**

它常被概括为一句话：**DataY ≈ DataX + NiFi + DolphinScheduler**。但不是三个工具的拼盘，而是把「进得来、算得动、调得准、用得上」重新设计成一套运行时。

### 五大能力

| 能力 | 说明 |
|------|------|
| **数据集成** | 可视化拖拽画布编排 ETL，也支持 JSON 配置一键跑；全量 / 增量 / 实时 CDC；自动建表与 DDL 转换；跨库数据比对 |
| **维度建模** | 星型 / 雪花模型，普通 / 层级 / 时间三种维度，事实表外键一键关联，指标自动获得业务语义 |
| **任务调度** | Cron 精准到秒、DAG 工作流、嵌套工作流、失败重试；master 与 worker 可分离，单机能跑、集群能扩 |
| **高性能数仓** | 内嵌 DuckDB，列存 + 向量化 + 无锁并发；可外挂 Doris / ClickHouse；支持 DuckLake（Iceberg）构建数据湖 |
| **智能问数** | 自然语言 → 指标 → 维度 → 取数，一句话拿报表；RAG 本地向量索引，指标口径不跑偏 |

另外还有**数据地图**（全域资产目录 + ECharts 关系图）和 **MCP Harness**（`/mcp/datay` 端点，让 Claude Desktop、Cursor 等 AI Agent 直接建数据源、建任务、跑调度、问指标）。

### CDC 支持矩阵

DataY 的 CDC 不是只做了 MySQL，主流数据库基本齐了：

| 数据库 | 实现方式 |
|--------|----------|
| MySQL | Binlog |
| PostgreSQL | 逻辑复制（WAL / pgoutput） |
| Oracle | LogMiner |
| 达梦 DM | LogMiner |
| **SQL Server** | **内置 CDC** |
| MongoDB | Change Streams |

数据源侧覆盖 MySQL 系、Oracle、PostgreSQL 系、SQL Server、MariaDB、达梦、SAP HANA、TDengine、DuckDB、Doris、ClickHouse、GreenPlum，以及本地文件、MinIO、CSV、Excel、Parquet。

### 30 秒上手

```bash
# 下载运行包
wget https://datay.yuyuejia.com.cn/download/datay-web.jar -O datay-web.jar

# 启动（单机模式，AI 能力需提供 API Key）
java -jar datay-web.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx

# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

不想本地装？直接玩在线演示：**http://datay-demo.yuyuejia.com.cn/**

只需要 Core 引擎的话，也可以脱离 Web 单独跑：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

### 一点实在话

DataY 开源免费，个人与企业都可以下载评估。生产环境落地、高可用架构、信创适配、CDC / 流批链路诊断、性能调优这些，作者提供商业支持服务。

- 产品首页：<http://datay.yuyuejia.com.cn/>
- Github：<https://github.com/yuyuejia/datay>
- Gitee：<https://gitee.com/yuyuejia/datay>

如果这篇文章帮你避开了 CDC 的坑，也欢迎给项目点个 Star。

> **数据平台不该是重资产。**
> 一个 JAR，2 核 4G，从数据接入到智能问数——这件事，DataY 想把它做成常态。

---