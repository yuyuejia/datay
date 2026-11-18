# 不写代码，把 Redis 接进数据链路：DataY 读写组件实战

> Redis 不只是缓存。DataY 新增 Redis 数据源与输入/输出组件，拖拽连线即可完成 Redis 与数据库、数仓、消息流之间的双向同步。

---

## 01 Redis 为什么总是游离在数据链路之外？

很多团队的数据架构里，Redis 都处在一个尴尬的位置：

- 业务系统用它做缓存、会话、排行榜、设备最新状态；
- 数据团队却很难把它纳入统一的数据集成链路；
- 想同步一批 key，往往要临时写个脚本；
- 想定期把数据库里的数据刷进 Redis，又要维护一套定时任务和序列化代码。

问题不在 Redis 本身，而在**连接 Redis 的那层胶水代码**：

- key 怎么设计？
- String、Hash、List、Set、ZSet、Stream 分别怎么映射成数据行？
- 写入时是覆盖还是追加？
- TTL 怎么设置？
- 批量写入怎么保证性能？
- 任务调试时会不会把生产缓存写脏？

DataY 的解决思路很直接：**把 Redis 做成平台内置的数据源和 ETL 组件**。

不用写 Java、不用维护脚本，在画布上拖两个节点、连一条线，就能完成：

```text
MySQL / 数仓 / 文件  →  Redis
Redis                →  MySQL / 数仓 / 文件
Redis                →  Redis
```

---

## 02 两个组件，覆盖 Redis 读写

DataY 新增了两个 ETL 组件：

| 组件 | 分组 | 作用 |
| --- | --- | --- |
| `RedisInput` | 数据输入 | 按 key 模式扫描 Redis，读取数据并批量下传 |
| `RedisOutput` | 数据输出 | 将上游数据按行写入 Redis |

同时新增了 `REDIS` 数据源类型，连接信息、认证、DB 索引统一在数据源管理中维护，组件里只需要选择数据源。

支持的数据结构：

- String
- Hash
- List
- Set
- ZSet
- Stream

---

## 03 RedisInput：把 key 变成数据流

`RedisInput` 使用 `SCAN` 遍历 key，而不是阻塞式的 `KEYS`，适合生产环境。

### 输出格式

每个 key 输出一行键级记录：

```json
{
  "key": "user:1",
  "type": "hash",
  "value": "{\"name\":\"张三\",\"age\":18}",
  "ttl": -1
}
```

其中：

| 字段 | 说明 |
| --- | --- |
| `key` | Redis key |
| `type` | `string` / `hash` / `list` / `set` / `zset` / `stream` |
| `value` | String 为原始字符串；其余类型为 JSON 字符串 |
| `ttl` | 剩余秒数，`-1` 表示永久；仅开启“输出 TTL”时存在 |

各类型的 `value` 内容：

| Redis 类型 | `value` 示例 |
| --- | --- |
| String | `"hello"` |
| Hash | `"{\"name\":\"张三\",\"age\":18}"` |
| List / Set | `"[\"a\",\"b\"]"` |
| ZSet | `"[{\"member\":\"m1\",\"score\":1.5}]"` |
| Stream | `"[{\"id\":\"1700000000000-0\",\"fields\":{\"f1\":\"v1\"}}]"` |

这种“键级 JSON”模型的好处是：**一个 key 就是一行数据，结构完整，读出来可以直接写入另一个 Redis，也可以落库后继续解析。**

### 核心配置

| 参数 | 说明 |
| --- | --- |
| `sourceId` | Redis 数据源 |
| `keyPattern` | key 匹配模式，例如 `user:*` |
| `typeFilter` | 只读取指定类型：`ALL` / `string` / `hash` / `list` / `set` / `zset` / `stream` |
| `scanCount` | SCAN 每次请求的 key 数量 |
| `batchSize` | 每个 FlowFile 包含的最大 key 数 |
| `maxKeys` | 最大读取 key 数，`0` 表示不限制 |
| `maxEntriesPerKey` | 集合类型单 key 最大读取元素数 |
| `includeTtl` | 是否输出 TTL |

### 为什么要有 maxEntriesPerKey？

Redis 里经常会出现超大 Hash、List 或 Set。如果一次性全部读进内存，很容易把任务打爆。

`maxEntriesPerKey` 用来控制单个 key 的最大读取元素数，默认 1000。对于排行榜、设备状态这类 key 通常够用；确实需要全量读取时，再按场景调大。

### 调试模式

调试运行时，`RedisInput` 会读取真实 Redis 数据，并由调试采样上限控制结束。这样可以先确认：

- 数据源是否连得通
- key 模式是否正确
- 读出来的 value 结构是否符合预期

---

## 04 RedisOutput：把数据写回 Redis

`RedisOutput` 负责把上游每一行写入 Redis，支持 6 种数据结构，并通过 Redisson Batch 批量提交。

### 写入映射

| 数据类型 | Redis 命令 | 默认映射 |
| --- | --- | --- |
| `STRING` | `SET` | `value` 字段，缺失时整行 JSON |
| `HASH` | `HSET` | `value` 为 JSON 对象时整体写入；否则按行字段写入 |
| `LIST` | `RPUSH` | `value` 为 JSON 数组时逐元素写入 |
| `SET` | `SADD` | `value` 为 JSON 数组时逐元素写入 |
| `ZSET` | `ZADD` | `value` 为 `[{member,score}]`，或使用 member/score 字段 |
| `STREAM` | `XADD` | `value` 为 JSON 对象或 `[{id,fields}]`；也支持按行字段写入 |

默认情况下，`keyField=key`、`valueField` 留空时优先读取 `value` 字段，因此可以**直接承接 `RedisInput` 的输出**，实现 Redis → Redis 键级搬运。

### 核心配置

| 参数 | 说明 |
| --- | --- |
| `sourceId` | Redis 数据源 |
| `dataType` | `STRING` / `HASH` / `LIST` / `SET` / `ZSET` / `STREAM` |
| `keyField` | 从行中取该字段作为 key |
| `keyPrefix` | key 前缀，最终 key = 前缀 + key 字段值 |
| `valueField` | value 字段，留空时优先使用 `value` |
| `fieldField` | Hash 模式下 hash field 所在字段 |
| `scoreField` | ZSet 模式下 score 所在字段 |
| `streamIdField` | Stream 消息 ID 字段，留空由 Redis 自动生成 |
| `ttlSeconds` | 固定 TTL，`0` 表示不过期 |
| `ttlField` | 从行中取 TTL，优先于固定 TTL |
| `writeMode` | `APPEND` / `REPLACE` |
| `batchSize` | 每批提交的最大写入命令数 |

### APPEND 与 REPLACE

- `APPEND`：追加或合并。Hash 会合并字段，List 会追加元素，Set/ZSet 会新增成员。
- `REPLACE`：单次任务运行中，首次写某个 key 前先 `DEL`，适合“全量覆盖”场景。

两个容易踩坑的点：

1. `REPLACE` 对同一个 key 在一次任务运行中只删除一次，后续行按 APPEND 语义处理；
2. Stream 不支持 `REPLACE`，始终通过 `XADD` 追加。

### 批量与性能

`RedisOutput` 不会每写一行就同步一次网络，而是按 `batchSize` 使用 Redisson Batch 批量提交。对于几十万、上百万行的同步任务，可以明显减少网络往返。

同时，输出组件会在多个 FlowFile 批次之间复用同一个 Redis 连接，任务结束时统一关闭，避免频繁创建连接。

### 调试安全

调试模式下，`RedisOutput` 会跳过真实写入，不会把测试数据写进生产 Redis。

---

## 05 Redis 数据源：连接、认证与元数据

组件之外，DataY 也把 Redis 纳入了统一的数据源管理。

### 连接配置

| 配置项 | 说明 |
| --- | --- |
| 类型 | `REDIS` |
| 连接 URL | `redis://host:port`，TLS 使用 `rediss://host:port` |
| 默认端口 | `6379` |
| 用户名 / 密码 | 可选，支持 Redis ACL 与 `requirepass` |
| 默认 Schema | Redis DB 索引，例如 `0` |
| 额外参数 | `redis.timeout_seconds`，默认 5 秒 |

### 元数据浏览

在数据源详情中：

- Redis DB 作为 Schema 展示；
- key 作为“表”展示；
- key 类型（string/hash/list/set/zset/stream）作为表类型；
- 表结构按类型展示 field/value、member/score、id/field/value 等列信息。

这让 Redis 不再是一个“只能连上去看看”的黑盒，而是能像普通数据源一样被浏览和理解。

### 只读查询

数据查询面板支持常用只读命令：

```text
PING
GET
HGETALL
LRANGE
SMEMBERS
ZRANGE
XRANGE
TYPE
TTL
SCAN
KEYS
```

写入命令会被明确拒绝，避免误操作。

---

## 06 五个高频场景

### 场景一：MySQL 数据刷进 Redis 缓存

```text
MySQL → RedisOutput
```

订单、商品、用户资料同步到 Redis Hash：

```text
dataType = HASH
keyField = id
keyPrefix = order:
writeMode = REPLACE
ttlSeconds = 86400
```

任务每次运行都会先删除旧 key，再写入最新数据，适合缓存全量刷新。

### 场景二：Redis 数据落库

```text
RedisInput → StreamSqlUnit → StreamJdbcOutput
```

先用 `RedisInput` 把 `device:*` 下的 Hash 读出来，再用 DuckDB SQL 解析 `value` 中的 JSON：

```sql
SELECT
  key,
  json_extract_string(value, '$.deviceId') AS device_id,
  json_extract_string(value, '$.status')    AS status
FROM input
```

然后写入 MySQL、Doris、DuckDB 等目标库。

### 场景三：Redis → Redis 迁移/复制

```text
RedisInput → RedisOutput
```

不同实例、不同 DB 之间做键级复制：

```text
keyPattern = user:*
typeFilter = hash
keyField = key
valueField = value
writeMode = REPLACE
```

`RedisInput` 输出的 `{key,type,value}` 结构，正好可以直接交给 `RedisOutput`。

### 场景四：排行榜同步

```text
MySQL 积分表 → RedisOutput(ZSET)
```

```text
dataType = ZSET
keyField = gameId
keyPrefix = rank:
valueField = userId
scoreField = score
writeMode = REPLACE
```

一次任务运行即可把某个榜单全量刷新到 Redis ZSet。

### 场景五：业务事件写入 Redis Stream

```text
MySQL CDC / Kafka → RedisOutput(STREAM)
```

每条变更事件通过 `XADD` 写入 Redis Stream，供下游实时消费、审计或异步处理。

当前版本 Stream 输入采用快照读取（`XRANGE`），暂未提供消费组和增量位点；如果需要严格消费语义，建议先用 Kafka 等消息队列承接。

---

## 07 任务 JSON 示例

```json
{
  "units": [
    {
      ".id": "redis_source",
      ".name": "RedisInput",
      "sourceId": {
        "type": "REDIS",
        "url": "redis://127.0.0.1:6379",
        "password": "",
        "dbschema": "0"
      },
      "keyPattern": "user:*",
      "typeFilter": "hash",
      "batchSize": 500,
      "maxEntriesPerKey": 1000,
      "includeTtl": true
    },
    {
      ".id": "redis_sink",
      ".name": "RedisOutput",
      "sourceId": {
        "type": "REDIS",
        "url": "redis://127.0.0.1:6379",
        "password": "",
        "dbschema": "0"
      },
      "dataType": "HASH",
      "keyField": "key",
      "valueField": "value",
      "writeMode": "REPLACE",
      "batchSize": 500
    }
  ],
  "connections": [
    { "sourceId": "redis_source", "targetId": "redis_sink", "sourcePort": 0 }
  ],
  "version": "1.0.0"
}
```

在可视化设计器里，这两个节点就是拖拽、连线、填参数，保存后由 DataY Core 引擎执行。

---

## 08 几条实践建议

### 1. 给 key 设计稳定的前缀

建议按业务域划分：

```text
user:1001
order:20261118:8899
device:sensor-01:latest
rank:game:1001
```

配合 `keyPrefix`，后续迁移、清理和权限控制都会更容易。

### 2. TTL 尽量显式配置

缓存类数据建议设置 TTL，避免历史数据无限堆积。`RedisOutput` 既支持固定 TTL，也支持从行里的 `ttl` 字段动态读取。

### 3. 大 key 一定要设上限

`RedisInput` 的 `maxEntriesPerKey` 是保护机制。对于超大集合，优先考虑拆分 key，而不是无限调大上限。

### 4. REPLACE 不是“逐行覆盖”

`REPLACE` 的语义是：一次任务运行中，首次写某个 key 前删除旧 key。后续相同 key 的行会继续合并/追加。需要逐行完全覆盖时，应保证每个 key 只写一行，或者在下游做去重。

### 5. 当前版本的能力边界

- 支持单机 Redis，暂不支持 Cluster / Sentinel；
- Stream 支持快照读取与 `XADD` 写入，暂不支持消费组与增量位点；
- 不支持 Pub/Sub、Lua 脚本等高级能力。

这些边界会在后续版本逐步补齐。

---

## 09 真实 Redis 验证

这次实现使用本地 Redis 8.4 做了完整验证，覆盖：

- String / Hash / List / Set / ZSet / Stream 六种类型读写；
- 类型过滤、批量大小、最大 key 数、单 key 元素上限；
- Key 前缀、固定 TTL、按行 TTL；
- Hash / Set / ZSet 的 REPLACE 覆盖写；
- Redis → Redis 六种类型往返；
- 数据源元数据浏览；
- `GET` / `HGETALL` / `LRANGE` / `SMEMBERS` / `ZRANGE` / `XRANGE` / `TYPE` / `TTL` / `SCAN` / `KEYS` 等只读命令；
- 密码认证与 DB 索引选择；
- 缺少 key、非法 Stream ID 等异常场景。

最终集成测试全部通过：

```text
Tests run: 7, Failures: 0, Errors: 0
```

---

## 10 写在最后

Redis 不应该只是业务系统里的一个缓存角落。

当它被纳入统一的数据源和 ETL 组件之后，很多原本需要临时脚本、定时任务和胶水代码才能完成的事情，都可以变成画布上的两个节点：

- 数据库 → Redis：缓存预热、状态同步、排行榜刷新；
- Redis → 数据库：缓存数据落库、审计、分析；
- Redis → Redis：实例迁移、DB 复制、环境同步；
- 消息流 → Redis：事件写入 Stream，供下游异步处理。

DataY 想做的，就是把这类“数据链路最后一公里”的工作，变成拖拽、配置和运行。

接下来，我们还会继续补齐 Redis Cluster、Sentinel、Stream 消费组等能力。如果你有具体的 Redis 接入场景，欢迎留言告诉我们。

---

## DataY 轻量数据平台

**DataY 是一套开源、轻量的一体化数据平台**，理念很简单：

> 数据平台可以很小，功能不打折。

传统数据平台往往需要 Hadoop、Spark、Flink、ClickHouse 一整套组件，几十台机器起步，运维门槛高。DataY 选择另一条路：

- **一个 JAR**，自研流批一体数据集成引擎，拒绝组件地狱；
- **2 核 4G** 就能跑通「数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用」的完整链路；
- 零依赖启动，10 分钟搭好开发环境；
- 内嵌 DuckDB 作为高性能分析引擎，同时支持 Doris、ClickHouse 等外部分析引擎；
- 调度 master 与执行 worker 可分可合，单机和集群同一套代码。

这里的“轻量”指的是**运行资源门槛低**，不是能力轻量。

DataY 由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行或嵌入 Java 应用 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数、分析看板 |

平台的核心能力包括：

- **数据集成**：MySQL、PostgreSQL、Oracle、SQL Server、Doris、ClickHouse、DuckDB、Redis 等二十余种数据源，支持全量、增量与 CDC 实时同步；
- **数据湖**：支持 DuckLake、Apache Polaris（Iceberg REST Catalog），可直接浏览元数据、执行 SQL 与批量写入；
- **维度建模**：星型 / 雪花模型，普通维度、层级维度、时间维度，事实表外键一键关联；
- **任务调度**：Quartz Cron 精确到秒，DAG 工作流编排，支持失败重试与暂停恢复；
- **高性能数仓**：内嵌 DuckDB，列存 + 向量化，支持 DuckLake / Iceberg 数据湖，也可外挂 Doris / ClickHouse；
- **智能问数**：自然语言 → 指标 → 维度 → 时间过滤 → 取数，一句话拿报表；
- **分析看板**：数据集管理、AI 生成看板、24 栅格设计器、大屏与嵌入分享；
- **AI 原生能力**：AI SQL 查询助手、ETL 任务设计助手、看板设计助手、智能问数助手、Java 脚本助手，以及面向 Agent 的 MCP Harness。

AI 能力兼容 OpenAI Chat Completions 协议，OpenAI、DeepSeek、通义千问、Ollama 等模型服务都可以对接。默认使用 DeepSeek，启动时通过 `DATAY_AI_API_KEY` 配置即可。

### 10 分钟跑起来

```bash
# 从源码构建
git clone https://gitee.com/yuyuejia/datay.git
cd datay
mvn clean package -DskipTests

# 启动（单机模式，调度 + 执行合一）
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx

# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

也可以直接下载打包好的 `datay-web.jar` 启动。

产品首页：<http://datay.yuyuejia.com.cn/>  
在线体验：<http://datay-demo.yuyuejia.com.cn/>  
项目地址：<https://gitee.com/yuyuejia/datay>  
<https://github.com/yuyuejia/datay>
