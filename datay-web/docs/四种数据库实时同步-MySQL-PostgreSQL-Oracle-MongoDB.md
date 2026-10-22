# 四种数据库，一套实时同步：MySQL、PostgreSQL、Oracle、MongoDB CDC 实战

> 实时同步真正难的不是“连上数据库”，而是“持续拿到变更”。
>
> MySQL 看 Binlog，PostgreSQL 读 WAL，Oracle 挖日志，MongoDB 订阅 Change Streams。
> 四种数据库，四套机制、四种位点、四组前置条件。
>
> 如果每接一个源都要再引入一套 Debezium + Kafka + Flink，同步这件事的成本，可能比数据本身还高。

## 01 批量同步解决不了的问题

很多团队的同步任务，一开始都是这么做的：

```text
每天凌晨 2 点 → 全量查一遍源库 → 覆盖写入目标库
```

数据量小的时候没问题。但只要业务开始实时化，这套方案很快会露出三个短板：

- **延迟高**：早上 9 点看到的报表，数据截止在凌晨 2 点；
- **压力大**：每次全量扫描都会给源库带来明显负载，表越大越明显；
- **抓不到中间态**：一条订单从创建、支付到取消，批量同步只能看到最终结果，过程全部丢失。

CDC（Change Data Capture，变更数据捕获）解决的就是这个问题：**不反复查全表，而是直接读取数据库的变更日志，把每一次 INSERT、UPDATE、DELETE 都变成一条数据流。**

这件事在概念上很简单，落到工程上却不简单，因为不同数据库的“变更日志”完全不是一回事：

| 数据库 | 变更来源 | 采集方式 | 位点 |
| --- | --- | --- | --- |
| MySQL | Binlog | 伪装从库订阅 | binlog 文件 + position |
| PostgreSQL | WAL | 逻辑复制 + `pgoutput` | LSN |
| Oracle | Redo Log | LogMiner 日志挖掘 | SCN |
| MongoDB | Oplog | Change Streams | resumeToken |

这篇文章讲清楚一件事：**把这四种数据库的实时变更，收敛成同一种数据流、同一套下游写法。**

## 02 四种数据库，四种 CDC

### MySQL：订阅 Binlog

MySQL 的 Binlog 本来就是为复制设计的，CDC 只是把“从库”换成了一个数据同步任务。

前提条件很明确：

- 开启 Binlog；
- `binlog_format=ROW`；
- 采集账号具备 `REPLICATION SLAVE`、`REPLICATION CLIENT` 权限。

```sql
-- 检查
SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'binlog_format';

-- 授权
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'datay'@'%';
```

DataY 的 `MySQLBinlogInput` 会把自己伪装成一个从库，实时解析 `WRITE_ROWS`、`UPDATE_ROWS`、`DELETE_ROWS` 等事件。

几个容易被忽略的点：

- `serverId` 必须唯一。多个任务连同一个 MySQL 实例时，冲突会导致互相踢下线；
- 相邻的同库同表同类型事件会自动合并，最多 10,000 条，减少下游写入次数；
- MySQL 是四种源里**唯一会把 DDL 一起传下去**的，`ALTER TABLE` 可以自动同步到目标端；
- Binlog 有保留时间，任务停太久再启动，可能已经追不回来了。

### PostgreSQL：读取 WAL

PostgreSQL 没有 Binlog，对应的是 WAL（Write-Ahead Log）。CDC 走的是逻辑复制：

```sql
-- 必须重启生效
ALTER SYSTEM SET wal_level = logical;

-- 采集账号需要复制权限
ALTER ROLE datay WITH REPLICATION;
```

`PostgresCDCInput` 底层用 pgjdbc 的复制 API 读取 WAL，内置 `pgoutput` 协议解析器，不需要额外部署 Debezium 或 Kafka。

组件会自动维护两样东西：

- **publication**：声明哪些表需要发布；
- **replication slot**：记录消费到了哪个 LSN。

这两者既是便利，也是运维点。尤其是 slot：**任务长期停摆而 slot 一直存在时，PostgreSQL 会保留对应的 WAL，磁盘可能被慢慢吃满。** 下线任务时记得清理 slot。

和 MySQL 相比，PostgreSQL CDC 有几个明确差异：

- **不传输 DDL**：逻辑复制只发数据变更，源表加字段不会自动同步；
- **TOAST 列**：UPDATE 时未变更的大字段不会携带值；
- **TRUNCATE**：当前版本只告警，不做同步；
- **初始数据**：默认只捕获启动后的增量，存量需要开启 `snapshot` 或单独做一次全量。

### Oracle：LogMiner 日志挖掘

Oracle 的变更在 Redo Log 里，DataY 用的是数据库自带的 `DBMS_LOGMNR`，不依赖 GoldenGate、XStream 这类额外组件。

前置条件比前两种更重：

```sql
-- 1. 归档模式（需要 SYSDBA，重启到 MOUNT）
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;

-- 2. 补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 3. 授权
GRANT LOGMINING TO datay;
GRANT SELECT ANY TRANSACTION TO datay;
GRANT EXECUTE ON SYS.DBMS_LOGMNR TO datay;
GRANT EXECUTE ON SYS.DBMS_LOGMNR_D TO datay;
```

`OracleCDCInput` 会按 SCN 区间挖掘在线与归档日志，把已提交事务转换成 INSERT / UPDATE / DELETE 事件。

这里的工程约束要提前知道：

- **必须开归档模式**，否则没有日志可挖；
- **建议开启全列补充日志**，否则 UPDATE 事件里未变更的列可能拿不到；
- **LOB 列暂不支持**：`CLOB/BLOB/LONG/ROWID` 会被忽略并告警；
- **不传输 DDL**，源表结构变更需要单独处理；
- **长事务有极小概率丢失**，不要让事务长时间不提交；
- 归档日志会持续增长，需要配合备份与清理策略。

### MongoDB：订阅 Change Streams

MongoDB 的 CDC 最“原生”：直接用 Change Streams 监听集合变更，官方驱动就支持。

但它有一个硬前提：**必须运行在副本集或分片集群上，单机 mongod 不可用。**

本地验证可以起一个单节点副本集：

```bash
docker run -d --name datay-mongo-cdc -p 27017:27017 mongo:7 \
  --replSet rs0 --bind_ip_all

docker exec datay-mongo-cdc mongosh --eval \
  'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'

docker exec datay-mongo-cdc mongosh --eval 'rs.status().ok'
```

`MongoCDCInput` 把变更流事件映射成统一事件：

| Change Streams 事件 | 映射为 | 下发数据 |
| --- | --- | --- |
| `insert` | INSERT | `fullDocument` |
| `update` / `replace` | UPDATE | `fullDocument`（默认 `updateLookup`） |
| `delete` | DELETE | `documentKey`（通常只有 `_id`） |
| `drop` / `rename` / `dropDatabase` | 忽略 | 仅告警 |

同样有边界：

- **DELETE 前镜像有限**：默认只能拿到 `_id`，要完整前镜像需在集合上启用 pre-image；
- **集合无固定 schema**：字段由采样推断，类型冲突时按 JSON 处理；
- **不传输结构变更**：删集合、改集合名不会自动同步。

## 03 统一事件模型：四种源，一种流

四种采集组件看起来完全不同，但输出被统一成了同一个模型：`BinlogEvent`。

一条变更进入数据流后，长这样：

```json
{
  "eventType": "UPDATE",
  "database": "source",
  "table": "orders",
  "timestamp": 1792396517078,
  "beforeData": { "id": 1001, "status": "PENDING" },
  "afterData":  { "id": 1001, "status": "PAID" }
}
```

FlowFile 上同时带上下游需要的元数据：

| 属性 | 含义 |
| --- | --- |
| `_eventType` | `INSERT` / `UPDATE` / `DELETE` / `DDL` |
| `_table` | 表名（MongoDB 为集合名） |
| `_database` | 库名 / schema |
| `_tableMetadata` | 表结构，用于自动建表与类型转换 |
| `_timestamp` | 事件时间 |

**这一步是整个方案的关键。** 从这一行开始，MySQL 的 Binlog、PostgreSQL 的 WAL、Oracle 的 SCN、MongoDB 的 resumeToken，对下游都不再有区别。

下游只需要认识 `_eventType`，就能决定怎么写：

- INSERT → 插入；
- UPDATE → 按主键更新；
- DELETE → 按主键删除；
- DDL → 执行结构变更语句。

`StreamJdbcOutput` 的 `model=auto` 做的就是这件事。目标表不存在时，它还会根据 `_tableMetadata` 自动建表，并做跨库类型映射。

也就是说，同一条链路可以这样复用：

```text
MySQL Binlog      ─┐
PostgreSQL WAL    ─┼─▶ 统一 CDC 事件 ─▶ StreamJdbcOutput(model=auto) ─▶ MySQL / PG / Oracle / DuckDB / Doris
Oracle LogMiner   ─┤
MongoDB Change    ─┘
```

## 04 四种源怎么配

四个输入组件的配置结构高度一致，差异主要在“采集范围”和“前置条件”。

### MySQL

```json
{
  ".id": "mysql_cdc",
  ".name": "MySQLBinlogInput",
  "sourceId": {
    "type": "mysql",
    "url": "jdbc:mysql://127.0.0.1:3306/source",
    "username": "datay",
    "password": "******",
    "dbschema": "source"
  },
  "databaseNamePattern": "source",
  "tableNamePattern": "orders|customers",
  "serverId": 1001,
  "snapshot": true
}
```

`serverId` 多任务部署时务必错开。

### PostgreSQL

```json
{
  ".id": "pg_cdc",
  ".name": "PostgresCDCInput",
  "sourceId": {
    "type": "postgresql",
    "url": "jdbc:postgresql://127.0.0.1:5432/source",
    "username": "datay",
    "password": "******",
    "dbschema": "public"
  },
  "schema": "public",
  "tableNamePattern": "orders|customers",
  "publicationName": "datay_pub_orders",
  "slotName": "datay_slot_orders",
  "snapshot": true
}
```

固定 `publicationName` 和 `slotName`，任务重启后才知道从哪里续。

### Oracle

```json
{
  ".id": "oracle_cdc",
  ".name": "OracleCDCInput",
  "sourceId": {
    "type": "oracle",
    "url": "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
    "driver": "oracle.jdbc.OracleDriver",
    "username": "datay",
    "password": "******",
    "dbschema": "DATAY"
  },
  "schema": "DATAY",
  "tableNamePattern": "ORDERS|CUSTOMERS",
  "pollIntervalMs": 1000,
  "snapshot": true
}
```

表名过滤不区分大小写，采集范围始终限定在指定 schema 内。

### MongoDB

```json
{
  ".id": "mongo_cdc",
  ".name": "MongoCDCInput",
  "sourceId": {
    "type": "mongodb",
    "url": "mongodb://127.0.0.1:27017",
    "dbschema": "test",
    "extraParams": {
      "replicaSet": "rs0",
      "directConnection": "true",
      "authSource": "admin"
    }
  },
  "database": "test",
  "collectionNamePattern": "orders|customers",
  "snapshot": true
}
```

MongoDB 的连接参数放在 `extraParams` 里，副本集名称和 `directConnection` 是最常出问题的两项。

## 05 全量 + 增量：快照不是可选项

CDC 默认只捕获“任务启动之后”的变更。这意味着，如果源库里已经有 500 万条历史数据，直接开 CDC，目标端只会拿到新数据。

四种输入组件都提供了 `snapshot`：

```json
{
  "snapshot": true,
  "snapshotFetchSize": 5000
}
```

首次运行（没有断点、没有 `snapshotDone` 标记）时，顺序是：

1. 先确定增量起点（MySQL Binlog 位点 / PG LSN / Oracle SCN / Mongo operationTime）；
2. 对采集范围内的表做一次性全量读取，按 INSERT 事件分批下发；
3. 再从上述位点启动增量同步；
4. 全量完成后写入 `snapshotDone`，重启不会重复全量。

这里采用的是一致性策略叫“简单一致性”：**增量位点取在快照之前，快照期间发生的变更会由 CDC 再补一遍。**

好处是实现轻、切换快；代价是可能出现少量重复。对于按主键幂等写入，或者存量基本稳定的场景，完全够用。如果业务要求严格不重不漏，就需要更重的导出快照方案。

## 06 断点续传：四种位点，一个原则

实时同步任务一定会重启：发布、扩容、机器故障、数据库维护。

所以每个 CDC 组件都必须回答一个问题：**重启之后，从哪里接着读？**

| 源 | 位点 | 保存方式 |
| --- | --- | --- |
| MySQL | `binlogFile` + `binlogPosition` | 随 FlowFile 持久化 |
| PostgreSQL | LSN | 复制槽确认位点 + 任务状态 |
| Oracle | 已提交事务的最大 SCN | 随 FlowFile 持久化 |
| MongoDB | resumeToken | 随 FlowFile 持久化 |

四种实现，原则只有一个：**位点跟着数据走，数据写成功之后再确认位点。**

这也是为什么 CDC 场景要强调幂等。至少一次投递、故障恢复重放、快照与增量重叠，都会带来重复事件。最稳妥的做法是：

- 目标表必须有主键；
- 写入使用 `model=auto` 或按主键 upsert；
- 不要用“自增 ID + append”去接 CDC 流。

## 07 目标端：auto 模式与自动建表

源端统一之后，目标端就简单了。

`StreamJdbcOutput` 的 `model=auto` 会根据 `_eventType` 自动选择写入策略：

| 事件 | 写入策略 |
| --- | --- |
| INSERT | INSERT / 批量追加 |
| UPDATE | 按主键 UPDATE（DuckDB 为 INSERT OR REPLACE） |
| DELETE | 按主键 DELETE |
| DDL | 执行 DDL 语句（仅 MySQL 源会产生） |

目标表不存在时，组件会读取 `_tableMetadata`，经过 `DatabaseConverter` 做类型映射后自动建表。常见映射包括：

- MySQL `DATETIME` → PostgreSQL `TIMESTAMP` / Oracle `TIMESTAMP`；
- MySQL `TINYINT` → 目标端小整数类型；
- Oracle `NUMBER(p,s)` → 目标端 `DECIMAL(p,s)`；
- MongoDB 动态字段 → JSON / 文本列。

一个完整的跨库链路，任务定义只有两个节点：

```json
{
  "units": [
    {
      ".id": "mysql_cdc",
      ".name": "MySQLBinlogInput",
      "sourceId": {
        "type": "mysql",
        "url": "jdbc:mysql://127.0.0.1:3306/source",
        "username": "datay",
        "password": "******",
        "dbschema": "source"
      },
      "tableNamePattern": "orders",
      "snapshot": true
    },
    {
      ".id": "pg_sink",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "type": "postgresql",
        "url": "jdbc:postgresql://127.0.0.1:5432/target",
        "username": "datay",
        "password": "******",
        "dbschema": "public"
      },
      "schema": "public",
      "table": "",
      "model": "auto"
    }
  ],
  "connections": [
    { "sourceId": "mysql_cdc", "targetId": "pg_sink", "sourcePort": 0 }
  ],
  "version": "1.0.0"
}
```

把 `MySQLBinlogInput` 换成另外三个组件，链路其余部分不用动。

## 08 选型与限制对照

| 维度 | MySQL | PostgreSQL | Oracle | MongoDB |
| --- | --- | --- | --- | --- |
| 采集机制 | Binlog | 逻辑复制 / WAL | LogMiner | Change Streams |
| 延迟 | 毫秒级 | 毫秒级 | 秒级（轮询） | 毫秒级 |
| 位点 | file + position | LSN | SCN | resumeToken |
| DDL 同步 | 支持 | 不支持 | 不支持 | 不支持 |
| 全量快照 | 支持 | 支持 | 支持 | 支持 |
| 额外部署 | 无 | 无 | 无 | 无 |
| 主要前提 | ROW 模式 + 复制权限 | `wal_level=logical` + 复制权限 | 归档 + 补充日志 + LOGMINING | 副本集 |
| 主要限制 | Binlog 过期 | TOAST / TRUNCATE / 槽位膨胀 | LOB 列 / 长事务 / 归档空间 | DELETE 前镜像有限 / 无固定 schema |

选型时可以记住三句话：

1. **关系型库之间做实时同步，优先 CDC，不要轮询。** 延迟和源库压力都不在一个量级。
2. **只有 MySQL 能自动同步 DDL。** 其余三种源的表结构变更，要有单独的上线流程。
3. **MongoDB 的难点不在接入，在数据建模。** 无固定 schema 意味着目标端要么用 JSON 列，要么先做字段收敛。

## 09 上线前必须检查的几件事

**① 权限最小化**

采集账号只给复制/挖掘所需权限，不要直接用业务账号或 DBA 账号跑同步。

**② 位点与目标表主键**

没有主键的表，UPDATE / DELETE 无法可靠落地。CDC 上线前先检查采集范围内所有表的主键。

**③ 日志与磁盘**

- MySQL：Binlog 保留时间要覆盖最长停机时间；
- PostgreSQL：关注 replication slot 造成的 WAL 堆积；
- Oracle：归档日志增长与清理策略；
- MongoDB：oplog 窗口要足够长，否则 resumeToken 会失效。

**④ 重复与幂等**

默认语义是至少一次。目标端按主键写入，重复事件最多覆盖一次，不会产生脏数据。

**⑤ 观测**

至少要看三个指标：采集位点、同步延迟、目标端写入失败数。没有这三个指标，实时同步就是黑盒。

## 10 DataY 是什么

上面这套能力，跑在 **DataY** 上。它是一个**开源、轻量的一体化数据平台**：

> 一个 JAR、内嵌 DuckDB，2 核 4G 就能跑通「数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用」的完整链路。

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数 |

**和实时同步相关的能力：**

- **四种 CDC 输入**：MySQL Binlog、PostgreSQL WAL、Oracle LogMiner、MongoDB Change Streams；
- **统一的变更事件模型**：INSERT / UPDATE / DELETE 前后镜像 + 表结构元数据；
- **全量与增量一体**：`snapshot` 首次先全量后增量，完成后自动标记；
- **断点续传**：Binlog 位点、LSN、SCN、resumeToken 全部随任务状态持久化；
- **自动建表与类型转换**：跨 MySQL / PostgreSQL / Oracle / DuckDB / Doris 写入；
- **可视化编排**：CDC 之后可以接实时 SQL、属性路由、字段清洗、大模型判断，再落到目标端；

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

## 11 写在最后

实时同步的复杂度，从来不在“写一个循环不断查表”。

真正的复杂度藏在四种数据库各自的变更日志里：怎么开、怎么读、从哪续、哪些变更拿不到、失败了怎么办。

把这些差异收敛掉之后，剩下的就是一条普通的数据流：

> **捕获变更 → 统一事件 → 清洗路由 → 幂等写入 → 可观测。**

当 MySQL、PostgreSQL、Oracle、MongoDB 都只是“一个输入组件”的差别，实时同步才从一次性项目，变成可以长期维护的基础设施。
