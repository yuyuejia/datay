# MongoDB 怎么做 CDC？不用 Debezium，Change Streams 就是它的“binlog”

> 给 MongoDB 做实时同步，很多人的第一反应是上 Debezium + Kafka Connect。
>
> 为了几张集合的变更，先部署 Kafka、Kafka Connect 一整套组件，运维成本比业务本身还高。
>
> 其实 MongoDB 自己就提供了一条官方变更流：**Change Streams**。
>
> 它把 oplog 里的写入封装成带断点的事件流，官方驱动直接消费，不需要任何中间件。

这篇文章讲清楚四件事：

1. MongoDB 的变更到底存在哪里，Change Streams 又封装了什么；
2. 一条事件长什么样，`update` / `delete` 有哪些天然缺口；
3. 全量快照 + 增量同步 + 断点续传应该怎么排顺序；
4. 落地时最容易踩的坑，以及一条可以直接跑起来的配置。

---

## 01 MongoDB 没有 binlog，但有 oplog

关系型数据库做 CDC，通常盯的是 binlog / WAL / redo log。

MongoDB 的对应物是 **oplog**：副本集里每个写操作都会记录到 `local.oplog.rs`，从节点靠重放它保持数据一致。

```text
客户端写入
   ↓
主节点执行写操作
   ↓
写入 oplog（local.oplog.rs）
   ↓
从节点拉取并重放
```

oplog 是一个**固定大小的 capped collection**，写满之后旧记录会被覆盖。这一点很关键：它决定了 CDC 能回溯多远。

直接读 oplog 当然可以，但格式是内部实现，字段会随版本变化，还要自己处理位点、过滤、事务边界。

MongoDB 给出的官方答案是 **Change Streams**：

```text
业务写入
   ↓
oplog
   ↓  $changeStream 聚合阶段
Change Streams 游标
   ↓
insert / update / replace / delete 事件
   ↓
resumeToken（断点）
```

它不是另一个日志系统，而是建立在 oplog 之上的一层 API。你拿到的每条事件都带 `resumeToken`，断线后可以从这个位点继续。

### 代价：必须跑在副本集上

Change Streams 依赖副本集的复制机制，**单机模式（没有 `--replSet`）不可用**。

这是 MongoDB CDC 和 MySQL CDC 最大的前置差异。本地验证时，单节点副本集就够了：

```bash
docker run -d --name datay-mongo-cdc -p 27017:27017 mongo:7 \
  --replSet rs0 --bind_ip_all

docker exec datay-mongo-cdc mongosh --eval \
  'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'

# 返回 1 表示副本集就绪
docker exec datay-mongo-cdc mongosh --eval 'rs.status().ok'
```

注意 `host` 要写成**采集端能解析的地址**。写成容器内部主机名，宿主机上的应用往往连不上，这是第一次部署最常见的坑。

连接单节点副本集时，还建议在连接串或参数里加 `directConnection=true`，让驱动直接连这个节点，而不是先做拓扑发现。

---

## 02 一条 Change Stream 事件长什么样

用 `mongosh` 就能直接观察变更流：

```javascript
const cs = db.cdc_demo.watch([], { fullDocument: "updateLookup" });

while (true) {
  if (cs.hasNext()) {
    printjson(cs.next());
  }
}
```

执行一条更新：

```javascript
db.cdc_demo.updateOne(
  { _id: 1 },
  { $set: { name: "alice2", amount: 11.11 } }
);
```

会看到类似这样的事件：

```json
{
  "_id": { "_data": "8265..." },
  "operationType": "update",
  "clusterTime": { "$timestamp": { "t": 1760000000, "i": 1 } },
  "ns": { "db": "test", "coll": "cdc_demo" },
  "documentKey": { "_id": 1 },
  "updateDescription": {
    "updatedFields": { "name": "alice2", "amount": 11.11 },
    "removedFields": []
  },
  "fullDocument": {
    "_id": 1,
    "name": "alice2",
    "amount": 11.11
  }
}
```

几个字段决定了这条流能不能用：

| 字段 | 作用 |
| --- | --- |
| `operationType` | `insert` / `update` / `replace` / `delete`，还有 `drop`、`rename` 等 |
| `ns` | 命名空间，即 `库.集合` |
| `documentKey` | 文档主键，通常是 `_id`；`delete` 事件只有它 |
| `updateDescription` | 只包含**发生变化的字段**，不是整行 |
| `fullDocument` | 更新后的完整文档，需要显式开启 |
| `_id` | 事件的 `resumeToken`，断点续传的锚点 |

### `update` 默认只给“差异”，不是整行

这是 MongoDB CDC 和关系型 CDC 最大的语义差异。

MySQL 的 binlog 可以配置成 ROW 模式，直接给出更新后的整行；MongoDB 的 `update` 事件默认只告诉你哪些字段变了。

如果下游需要整行数据，必须显式开启 `fullDocument`（原生驱动的默认行为是不返回后镜像）：

| 策略 | 行为 | 适用场景 |
| --- | --- | --- |
| `updateLookup` | 收到事件后再回查一次当前文档 | 大多数同步场景，DataY 的默认值 |
| `whenAvailable` | 能拿到就返回，拿不到 `fullDocument` 为 `null` | 允许缺后镜像 |
| `required` | 拿不到直接报错 | 强依赖后镜像 |

`updateLookup` 的回查发生在**读取事件时**，不是写入时。如果同一条文档在极短时间内被改了多次，回查到的可能是更新的版本。对最终一致性同步来说通常没问题，但不要假设它等于“那次 update 的精确结果”。

### `delete` 只有主键

```json
{
  "operationType": "delete",
  "ns": { "db": "test", "coll": "cdc_demo" },
  "documentKey": { "_id": 2 }
}
```

删除事件默认**不包含被删文档的内容**。目标端只能按 `_id` 删，没法拿到完整的“删除前镜像”。

如果业务确实需要，MongoDB 6.0 之后可以在集合上开启 pre-image：

```javascript
db.runCommand({
  collMod: "cdc_demo",
  changeStreamPreAndPostImages: { enabled: true }
});
```

然后在 `watch` 时指定 `fullDocumentBeforeChange`。但多数同步场景其实用不上，按主键删除就够了。

### 结构变更不会自动同步

`drop`、`rename`、`dropDatabase`、`invalidate` 这些事件能捕获到，但 DataY 的 MongoDB CDC 目前只记录告警，**不会自动同步结构变更**。

原因很简单：MongoDB 没有关系型数据库那套 DDL 语义，集合被删了、改名了，目标端该跟着删表还是保留数据，不是一个可以自动决定的问题。

如果业务真的会调整集合结构，建议把结构变更纳入发布流程，而不是指望 CDC 自动跟随。

---

## 03 全量 + 增量：顺序错了就会丢数

只启动 Change Streams，只能拿到**启动之后**的变更。存量数据怎么办？

最直觉的做法是：

```text
先全量导出 → 再启动 Change Streams
```

这个顺序有一个致命窗口：全量导出期间发生的变更，两边都接不住。

正确顺序应该反过来：

```text
先拿到当前 resumeToken（增量起点）
        ↓
再读全量快照
        ↓
从那个 resumeToken 开始追增量
```

这样，快照期间发生的变更会在增量阶段被补放。代价是**边界附近可能重复**，因为快照读到的文档可能已经包含了某条增量变更。

这就是常说的“简单一致性”：

- 不丢：增量起点早于快照开始；
- 可能重：快照与增量有交集；
- 下游需要**幂等写入**，比如按 `_id` 做 upsert。

对绝大多数同步场景，这个取舍是划算的。要做到严格不重，需要 snapshot read concern + 事务级协调，复杂度和成本都高得多，通常没有必要。

### 断点续传靠 resumeToken

每条事件都带 `resumeToken`。采集端处理完一条，就把它保存下来。

任务重启时：

```text
有 resumeToken → 从断点继续
没有 resumeToken → 从当前位置开始
```

这里还有一个容易被忽略的限制：**oplog 是有窗口的**。

如果任务停了太久，断点已经被 oplog 覆盖，`resumeAfter` 会失败。这时候只能重新做一次全量快照，没有别的办法。

所以生产环境要关注两件事：

1. oplog 大小是否足够覆盖“最长故障恢复时间”；
2. 任务失败告警是否及时，不要等到断点过期才发现。

### 连接断了要重连，但不能无限重连

网络抖动、主从切换、节点重启，都会让变更流中断。

一个可用的采集器需要：

- 断线后按固定间隔重连；
- 重连时从最后消费的 `resumeToken` 继续；
- 连续失败超过阈值后，让任务**明确失败**，而不是假装还在运行。

DataY 的默认策略是：每 3 秒重连一次，连续失败 5 次后任务置为 `FAILED`；如果一次连接稳定运行超过 10 秒，失败计数会重置。

这条规则看起来不起眼，但它避免了一个很危险的状态：**连接早就断了，任务页面还显示“运行中”**。

---

## 04 MongoDB 没有固定 schema，同步时怎么处理

关系型 CDC 可以直接读取表结构，MongoDB 不行。

同一个集合里，文档 A 有 `phone` 字段，文档 B 没有；文档 C 的 `amount` 是数字，文档 D 的 `amount` 是字符串。这些在 MongoDB 里都合法。

DataY 的处理方式是**采样推断**：

1. 从集合中取一批文档（默认 100 条）；
2. 合并所有出现过的字段；
3. 根据 BSON 类型映射到目标类型；
4. 同一字段出现类型冲突时，统一按 `JSON` 处理。

BSON 到 JSON 的规范化规则也固定下来：

| BSON 类型 | 转换结果 |
| --- | --- |
| `ObjectId` | 24 位十六进制字符串 |
| `Decimal128` | 数值 |
| `Date` | ISO-8601 字符串 |
| `Binary` | Base64 字符串 |
| 嵌套文档 / 数组 | JSON 对象 / 数组 |

这套规则不追求“还原 MongoDB 的所有类型”，而是保证**跨库可读、可落表、可查询**。

代价是：目标端拿到的是 JSON 形态的嵌套结构，不会自动展开成多张关联表。如果下游要做结构化分析，建议在同步链路里加一个字段提取或展开步骤，而不是指望自动建表把嵌套文档拆好。

---

## 05 四个必须提前知道的坑

### 坑一：单机 MongoDB 跑不起来

报错通常是：

```text
$changeStream is only supported on replica sets
```

解决方式不是改配置，而是把实例改成副本集。生产环境本来就是副本集或分片集群，问题多出现在本地开发和测试环境。

### 坑二：`rs.initiate` 的 host 写错

单节点副本集初始化时写了容器名：

```javascript
rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "mongo:27017" }] })
```

宿主机上的应用解析不了 `mongo`，连接就会一直超时。

改成采集端能解析的地址：

```javascript
rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "localhost:27017" }] })
```

### 坑三：只同步了 `updateDescription`，目标端字段残缺

如果消费端直接把 `updateDescription.updatedFields` 当成整行写入，结果就是：只更新过的字段有值，其他字段全空。

正确做法是使用 `fullDocument`（`updateLookup`），拿到完整文档后再按主键 upsert。

### 坑四：把数组更新当成整行覆盖

MongoDB 支持 `$push`、`$pull`、`$set` 到数组下标。这类更新在事件里表现为对数组的局部修改。

如果你在目标端按“整行 upsert”处理，而拿到的 `fullDocument` 又是回查结果，通常没问题；但如果用的是 `updateDescription` 自己做增量合并，就要特别小心数组下标和删除语义，很容易合错。

**能拿完整后镜像，就不要自己拼增量。**

---

## 06 在 DataY 里配置一条 MongoDB CDC

DataY 把上面这些细节收进了 `MongoCDCInput` 组件。一个最小的任务配置只有两个节点：

```json
{
  "units": [
    {
      ".id": "MongoCDCInput",
      ".name": "MongoCDCInput",
      "sourceId": {
        "type": "mongodb",
        "url": "mongodb://127.0.0.1:27017",
        "dbschema": "test",
        "extraParams": {
          "directConnection": "true"
        }
      },
      "database": "test",
      "collection": "cdc_demo"
    },
    {
      ".id": "StreamJdbcOutput",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "url": "jdbc:duckdb:./data/cdc_demo.duckdb",
        "driver": "org.duckdb.DuckDBDriver",
        "dbschema": "main"
      },
      "schema": "main",
      "table": "",
      "model": "auto"
    }
  ],
  "connections": [
    {
      "sourceId": "MongoCDCInput",
      "targetId": "StreamJdbcOutput",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

启动：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar mongoCdcToDuckDB.json
```

输出端不一定是 DuckDB。`StreamJdbcOutput` 走标准 JDBC，换成 MySQL、PostgreSQL、Doris 等目标库时，只需要改连接串和建表方式。

### 常用参数

| 参数 | 说明 |
| --- | --- |
| `database` / `schema` | 采集哪个库，缺省取数据源的 `dbschema` |
| `collection` | 采集单个集合 |
| `collectionNamePattern` | 按正则采集多个集合，如 `order_.*`；为空表示全库 |
| `fullDocument` | 后镜像策略，默认 `updateLookup` |
| `snapshot` | 首次运行先全量、后增量，默认 `false` |
| `snapshotFetchSize` | 快照分批大小，默认 10000 |
| `resumeToken` | 手动指定续传位点，一般不用配 |
| `startAtOperationTime` | 从指定时间点开始采集 |
| `maxAwaitTimeMs` | 变更流阻塞等待时间，默认 1000 毫秒 |
| `reconnectMaxAttempts` | 连续重连失败上限，默认 5 |
| `reconnectIntervalMs` | 重连间隔，默认 3000 毫秒 |

### 多集合采集

只写 `collectionNamePattern`，不写 `collection`：

```json
{
  "database": "test",
  "collectionNamePattern": "order_.*",
  "snapshot": true,
  "snapshotFetchSize": 5000
}
```

组件会：

1. 列出库下所有匹配的集合；
2. 用 `database.watch()` 建立一条变更流，按 `ns.coll` 正则过滤；
3. 对匹配的集合逐个做全量快照；
4. 快照完成后从统一起点追增量。

这样多个集合共享一条变更流，不需要为每个集合单独起任务。

---

## 07 跑一遍：增、改、删

先建一个空集合，然后启动任务（`snapshot=false`，从当前位点开始采集）：

```javascript
use test
db.createCollection("cdc_demo")
```

任务进入运行状态后，依次执行：

```javascript
db.cdc_demo.insertOne({ _id: 1, name: "alice", amount: 10.5 })
db.cdc_demo.insertOne({ _id: 2, name: "bob",   amount: 20.0 })

db.cdc_demo.updateOne(
  { _id: 1 },
  { $set: { name: "alice2", amount: 11.11 } }
)

db.cdc_demo.deleteOne({ _id: 2 })
```

目标 DuckDB 会自动建表并跟随变更，最终只剩一条：

```text
_id | name   | amount
1   | alice2 | 11.11
```

对应的写入策略由 `model=auto` 决定：

| 事件 | 目标端动作 |
| --- | --- |
| `INSERT` | 批量追加 |
| `UPDATE` | 按 `_id` 做 `INSERT OR REPLACE` |
| `DELETE` | 按 `_id` 删除 |

如果开启 `snapshot=true`，第一次运行会先把存量文档按 `INSERT` 分批写出，再从快照起点接增量。仓库里的端到端测试覆盖了两条链路：

- `MongoCdcToDuckDBTest`：实时 insert / update / delete；
- `MongoCdcSnapshotToDuckDBTest`：先全量、后增量。

---

## 08 MongoDB CDC 和关系型 CDC 有什么不同

| 维度 | MySQL / PostgreSQL CDC | MongoDB CDC |
| --- | --- | --- |
| 变更来源 | binlog / WAL | oplog（通过 Change Streams） |
| 前置条件 | 开启日志、复制权限 | 必须是副本集或分片集群 |
| 事件内容 | 通常是整行前后镜像 | `update` 默认只有差异字段 |
| 删除事件 | 可拿到整行 | 默认只有 `_id` |
| 结构 | 固定 schema，可同步 DDL | 无固定 schema，结构变更不同步 |
| 断点 | binlog file + position / LSN | `resumeToken` |
| 回溯窗口 | 取决于日志保留策略 | 取决于 oplog 大小与写入量 |
| 事务 | 有明确事务边界 | 不提供事务提交事件 |

最后一条值得展开：关系型 CDC 可以按事务攒批，等 `COMMIT` 再下发；MongoDB 的变更流是逐条事件，不会额外发一个“事务结束了”的信号。多文档事务里的变更虽然带 `txnNumber`，但要自己按它归组。

所以采集端通常按时间窗口或条数攒批，空闲时刷新缓冲区。DataY 的做法是：变更流空闲超过 `maxAwaitTimeMs`（默认 1 秒）就把缓冲区里的事件下发，避免低流量时数据一直悬着。

---

## 09 什么时候适合用 MongoDB CDC

比较适合：

- 把 MongoDB 的业务数据实时同步到数仓或分析库；
- 用 MongoDB 作为主存储，需要同步一份到关系型库做报表；
- 搜索索引、缓存、下游服务的实时更新；
- 需要捕获文档变更做审计、事件驱动。

不太适合：

- 源端是单机 MongoDB，又不想改造成副本集；
- 业务强依赖集合结构变更的自动同步；
- 历史数据量极大，停机窗口又很短——首次全量仍然要读一遍所有文档。

还有一个现实问题：**首轮全量不是“零成本”**。它会完整扫描集合，对大集合要控制分批大小、避开业务高峰，必要时先用从节点或只读节点承担。

---

## 10 写在最后

MongoDB 的 CDC 并不神秘，核心就三件事：

1. **Change Streams 是官方变更流**，不用为了同步再引入 Kafka 和 Debezium；
2. **`update` 默认是差异，不是整行**，想要后镜像就开 `updateLookup`；
3. **断点靠 `resumeToken`，但 oplog 有窗口**，停太久只能重做全量。

真正决定一条链路能不能长期跑下去的，往往不是“能不能连上”，而是这些细节：

- 全量和增量的起点有没有排对；
- 断线重连后能不能从正确位点续上；
- 下游写入是不是幂等；
- 结构变更、删除、数组更新这些边界有没有想清楚。

DataY 的 `MongoCDCInput` 把这些做成了组件能力：副本集校验、`updateLookup` 后镜像、快照 + 增量、`resumeToken` 持久化、有限次重连，以及无 schema 情况下的字段推断。

一条配置，从 MongoDB 到 DuckDB、MySQL 或 Doris，变更就能持续流过去。

---

## 关于 DataY

DataY 是一套**开源、轻量的一体化数据平台**：

> 数据平台可以很小，功能不打折。

- **一个 JAR，自研流批一体引擎**，不依赖 Hadoop / Spark / Flink；
- **2 核 4G** 就能跑通数据集成 → 维度建模 → 任务调度 → 数据应用；
- 支持 MySQL、PostgreSQL、Oracle、SQL Server、达梦、MongoDB、Doris、ClickHouse、DuckDB 等二十余种数据源；
- 六种 CDC：MySQL Binlog、PostgreSQL WAL、Oracle LogMiner、达梦 LogMiner、SQL Server CDC、MongoDB Change Streams；
- 统一组件模型，批流共用；断点续传位点统一持久化；
- 自动建表、流内计算、任务调度、MCP Harness 一应俱全。

**快速开始：**

```bash
git clone https://gitee.com/yuyuejia/datay.git
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

只跑 Core 单任务：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

产品首页：<http://datay.yuyuejia.com.cn/>
在线体验：<http://datay-demo.yuyuejia.com.cn/>
项目地址：<https://gitee.com/yuyuejia/datay>

