# MySQL CDC 实时同步，到底能跑多快？

## DataY 实测：DuckDB 窄表快照近百万行/秒，20 字段宽表 31 万行/秒

> 做数据集成的人，大概都经历过这样的场景：
>
> 白天业务库不敢动，晚上全量同步跑到天亮；上了 CDC，又担心延迟高、吞吐低、更新一条卡半天。
>
> 与其讲架构，不如直接上数据。
>
> 这篇文章，我们用 DataY 做了一轮 MySQL CDC 实时同步实测，目标端分别是 DuckDB 和 PostgreSQL，覆盖全量快照、增量 INSERT、UPDATE、DELETE，以及单行事务端到端延迟。除了 4 字段窄表，我们还补了一轮 20 字段宽表，尽量贴近真实业务表。

---

## 01 测试环境：先把口径说清楚

性能数字最怕脱离环境。先把测试条件摆出来：

| 项目 | 配置 |
|---|---|
| 测试机器 | macOS arm64，JDK 21 |
| 源端 | 本地 MySQL 9.4.0，`binlog_format=ROW`、`sync_binlog=1` |
| DuckDB 目标 | DuckDB JDBC 1.5.3.0，嵌入式 |
| PostgreSQL 目标 | PostgreSQL 18.1，Docker，localhost:15432 |
| 链路 | `MySQLBinlogInput → StreamJdbcOutput`，单并发 |
| 批量事务 | 5000 行/事务 |
| 快照批次 | 10000 行/批 |

这不是一份 formal benchmark，而是一次**单机、本地回环、可复现的工程实测**。重点看趋势、瓶颈和优化空间，而不是把数字当成绝对上限。

另外说明一下：DataY 的产品定位是“2 核 4G 就能跑通数据集成 → 数仓 → 问数完整链路”。本文数据来自开发机单机实测，不代表 2C4G 机器上的绝对性能，但足以说明这套引擎的能力边界。

---

## 02 表结构：4 字段基准表 + 20 字段宽表

为了排除宽表、二级索引、外键、触发器等干扰，基准表只保留 4 个字段和 1 个主键；宽表则扩展到 20 个字段，用来观察行宽对同步性能的影响。

### 2.1 4 字段基准表

DuckDB 场景使用 `test.cdc_perf`，PostgreSQL 场景使用 `test.cdc_pg_perf`，两边结构完全一致：

```sql
CREATE TABLE test.cdc_perf (
  id         BIGINT       NOT NULL,
  name       VARCHAR(128),
  amount     DECIMAL(12,2),
  created_at DATETIME,
  PRIMARY KEY (id)
);
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 主键，快照与增量数据都靠它定位 |
| `name` | `VARCHAR(128)` | 字符串字段，用于验证 UPDATE 覆盖 |
| `amount` | `DECIMAL(12,2)` | 定点数，用于观察 DECIMAL 精度映射 |
| `created_at` | `DATETIME` | 时间字段，用于观察时间类型映射 |

目标端由 DataY 的 `StreamJdbcOutput(auto)` 自动建表。

DuckDB 目标表：

```sql
CREATE TABLE main.cdc_perf (
  id         BIGINT,
  name       VARCHAR,
  amount     DECIMAL(12,2),
  created_at TIMESTAMP,
  PRIMARY KEY (id)
);
```

PostgreSQL 目标表：

```sql
CREATE TABLE test.cdc_pg_perf (
  id         BIGINT NOT NULL,
  name       VARCHAR(128),
  amount     NUMERIC(12,2),
  created_at TIMESTAMP,
  CONSTRAINT pk_cdc_pg_perf PRIMARY KEY (id)
);
```

类型映射关系：

| MySQL | DuckDB | PostgreSQL |
|---|---|---|
| `BIGINT` | `BIGINT` | `BIGINT` |
| `VARCHAR(128)` | `VARCHAR` | `VARCHAR(128)` |
| `DECIMAL(12,2)` | `DECIMAL(12,2)` | `NUMERIC(12,2)` |
| `DATETIME` | `TIMESTAMP` | `TIMESTAMP` |

### 2.2 20 字段宽表

DuckDB 场景源表为 `test.cdc_perf_wide`，PostgreSQL 场景为 `test.cdc_pg_perf_wide`：

```sql
CREATE TABLE test.cdc_perf_wide (
  id          BIGINT PRIMARY KEY,
  c_tinyint   TINYINT,
  c_smallint  SMALLINT,
  c_int       INT,
  c_bigint    BIGINT,
  c_decimal   DECIMAL(18,6),
  c_float     FLOAT,
  c_double    DOUBLE,
  name        VARCHAR(128),
  c_char      CHAR(16),
  c_text      TEXT,
  c_date      DATE,
  c_time      TIME,
  c_datetime  DATETIME,
  c_timestamp TIMESTAMP,
  c_bool      TINYINT(1),
  c_binary    VARBINARY(16),
  c_blob      BLOB,
  c_amount2   DECIMAL(12,2),
  c_remark    VARCHAR(255)
);
```

宽表覆盖整数、定点数、浮点数、字符串、文本、日期、时间、二进制等常见类型。DuckDB 目标端会把 `VARCHAR` 落为无长度限制的 `VARCHAR`，`VARBINARY` / `BLOB` 落为 `BLOB`；PostgreSQL 目标端对应为 `BYTEA`、`NUMERIC`、`TIMESTAMP` 等类型。

### 2.3 测试数据分布

为了让结果可复现，数据按固定规则生成：

| 阶段 | 数据量 | 主键范围 | 数据规则 |
|---|---:|---|---|
| 全量快照 | 100 万行 | 1 ~ 1,000,000 | `name='row_<id>'`，`amount=(id % 1000)+0.25`，`created_at='2026-01-01 00:00:00'` |
| 增量 INSERT | 50 万行 | 10,000,000 ~ 10,499,999 | `name='bulk_<id>'`，5000 行/事务 |
| 增量 UPDATE | 2 万行 | 10,000,000 ~ 10,019,999 | `name=CONCAT('upd_', id)` |
| 增量 DELETE | 2 万行 | 10,000,000 ~ 10,019,999 | 按主键范围删除 |
| 单行延迟 | 500 次 | 20,000,000 ~ 20,000,499 | 每次单行 INSERT，`name='lat_<id>'` |

4 字段窄表单行约 60 字节；20 字段宽表因为包含多个字符串、二进制和时间字段，单行明显更宽。

---

## 03 结果一：4 字段窄表

### 3.1 MySQL CDC → DuckDB

| 场景 | 数据量 | 吞吐 | 耗时 |
|---|---:|---:|---:|
| 全量快照 | 100 万行 | **86 万 ~ 108 万 行/s** | 0.93 ~ 1.16 s |
| 增量 INSERT | 50 万行 | **23 万 ~ 26 万 行/s** | 1.90 ~ 2.16 s |
| 增量 UPDATE | 2 万行 | **3.2 万 ~ 4.0 万 行/s** | 0.49 ~ 0.63 s |
| 增量 DELETE | 2 万行 | **7.1 万 ~ 7.6 万 行/s** | 0.26 ~ 0.28 s |

增量 INSERT 后，CDC 从“最后提交”到“全部可见”只需要 **0.1 ~ 0.2 秒**，基本是实时追平。

单行事务端到端延迟：

- **p50：约 10.1 ms**
- **p95：约 12.2 ms**
- p99：14 ~ 20 ms，偶发约 116 ms 的尾毛刺

### 3.2 MySQL CDC → PostgreSQL

| 场景 | 数据量 | 吞吐 | 耗时 |
|---|---:|---:|---:|
| 全量快照 | 100 万行 | **18.6 万 ~ 22.6 万 行/s** | 4.4 ~ 5.4 s |
| 增量 INSERT | 50 万行 | **13.2 万 ~ 16.4 万 行/s** | 3.0 ~ 3.8 s |
| 增量 UPDATE | 2 万行 | **4.3 万 ~ 5.5 万 行/s** | 0.37 ~ 0.47 s |
| 增量 DELETE | 2 万行 | **5.9 万 ~ 10.8 万 行/s** | 0.19 ~ 0.34 s |

增量 INSERT 后，CDC 追平时间约 **1.1 ~ 1.8 秒**。

单行事务端到端延迟：

- **p50：约 10.3 ~ 11.2 ms**
- **p95：约 12.8 ~ 13.8 ms**
- p99 会因 PG checkpoint / OS 抖动出现 50 ~ 110 ms 的偶发尖刺

### 3.3 两者对比

| 场景 | DuckDB | PostgreSQL | 结论 |
|---|---:|---:|---|
| 全量快照 100 万行 | 86 万 ~ 108 万 行/s | 18.6 万 ~ 22.6 万 行/s | DuckDB 约 4 ~ 5 倍 |
| 增量 INSERT 50 万行 | 23 万 ~ 26 万 行/s | 13.2 万 ~ 16.4 万 行/s | DuckDB 约 1.5 ~ 1.9 倍 |
| 增量 UPDATE 2 万行 | 3.2 万 ~ 4.0 万 行/s | 4.3 万 ~ 5.5 万 行/s | PG 略快 |
| 增量 DELETE 2 万行 | 7.1 万 ~ 7.6 万 行/s | 5.9 万 ~ 10.8 万 行/s | 基本相当 |
| 单行延迟 p50 | 约 10.1 ms | 约 10.3 ~ 11.2 ms | 相差 0.3 ~ 0.8 ms |

DuckDB 是进程内嵌入式，没有网络开销；PostgreSQL 是独立服务，走 localhost 回环。快照和批量 INSERT 的差距主要来自这里，符合预期。

---

## 04 结果二：20 字段宽表

真实业务表往往有几十个字段。为了更贴近生产场景，我们又补了一轮 20 字段宽表验证，数据量保持不变：快照 100 万行、增量 INSERT 50 万行、UPDATE/DELETE 各 2 万行、单行延迟采样 500 次。

### 4.1 DuckDB：4 字段 vs 20 字段

| 场景 | 4 字段 | 20 字段 | 变化 |
|---|---:|---:|---|
| 全量快照 100 万行 | 86 万 ~ 108 万 行/s | **30.7 万 ~ 31.6 万 行/s** | 约为原来的 1/3 |
| 增量 INSERT 50 万行 | 23 万 ~ 26 万 行/s | **11.8 万 ~ 12.6 万 行/s** | 约为原来的 1/2 |
| 增量 UPDATE 2 万行 | 3.2 万 ~ 4.0 万 行/s | **0.96 万 ~ 1.03 万 行/s** | 约为原来的 1/3 ~ 1/4 |
| 增量 DELETE 2 万行 | 7.1 万 ~ 7.6 万 行/s | **6.8 万 ~ 7.0 万 行/s** | 基本持平 |
| 单行延迟 p50 | 约 10.1 ms | **约 10.9 ms** | 略增 |
| 单行延迟 p95 | 约 12.2 ms | **12.3 ~ 13.1 ms** | 略增 |

### 4.2 PostgreSQL：4 字段 vs 20 字段

| 场景 | 4 字段 | 20 字段 | 变化 |
|---|---:|---:|---|
| 全量快照 100 万行 | 18.6 万 ~ 22.6 万 行/s | **约 10.8 万 行/s** | 约为原来的 1/2 |
| 增量 INSERT 50 万行 | 13.2 万 ~ 16.4 万 行/s | **8.7 万 ~ 9.0 万 行/s** | 约为原来的 0.6 ~ 0.7 |
| 增量 UPDATE 2 万行 | 4.3 万 ~ 5.5 万 行/s | **4.1 万 ~ 4.4 万 行/s** | 基本持平 |
| 增量 DELETE 2 万行 | 5.9 万 ~ 10.8 万 行/s | **5.2 万 ~ 8.3 万 行/s** | 基本持平 |
| 单行延迟 p50 | 约 10.3 ~ 11.2 ms | **约 9.3 ~ 10.8 ms** | 基本持平 |
| 单行延迟 p95 | 约 12.8 ~ 13.8 ms | **约 13.0 ~ 13.4 ms** | 基本持平 |

### 4.3 行宽带来的三个变化

**1. 快照和 INSERT 对行宽最敏感**

DuckDB 快照从 86 万 ~ 108 万行/s 降到 30.7 万 ~ 31.6 万行/s；PostgreSQL 从 18.6 万 ~ 22.6 万行/s 降到约 10.8 万行/s。

原因很直接：20 个字段意味着每行要序列化更多数据，目标端也要绑定更多参数。这是最真实的“宽表成本”。

**2. DuckDB 的 UPDATE 对列数更敏感**

DuckDB 的 UPDATE 走集合式写入，20 个字段会让单条 SQL 的 `VALUES` 结构和参数数量明显变大，规划与绑定成本上升，吞吐从 3.2 万 ~ 4.0 万行/s 降到约 1 万行/s。

但即使如此，相比优化前的逐行 `INSERT OR REPLACE`（约 1,800 行/s），仍然快了 **5 倍以上**。

PostgreSQL 的 UPDATE 是逐行 JDBC batch，列数增加主要是数据量增加，协议开销占比仍然较高，所以吞吐基本保持在 4 万行/s 左右。

**3. DELETE 和延迟受影响较小**

DELETE 只按主键定位，不涉及全部字段的更新，所以宽表下 DuckDB 仍有约 6.8 万 ~ 7.0 万行/s，PostgreSQL 约 5.2 万 ~ 8.3 万行/s。

单行延迟基本维持在 10ms 左右，说明固定成本仍然主要来自 MySQL 事务提交和状态持久化，而不是字段数量。

### 4.4 更贴近真实场景的一组结论

如果按 20 字段宽表来评估，可以记这一组数：

| 目标端 | 全量快照 | 增量 INSERT | 增量 UPDATE | 增量 DELETE | 单行 p50 |
|---|---:|---:|---:|---:|---:|
| DuckDB | 约 31 万 行/s | 约 12 万 行/s | 约 1 万 行/s | 约 7 万 行/s | 约 10.9 ms |
| PostgreSQL | 约 10.8 万 行/s | 约 9 万 行/s | 约 4.3 万 行/s | 约 5 ~ 8 万 行/s | 约 10 ms |

---

## 05 三个关键发现

### 发现一：DuckDB 的 UPDATE 曾经是明显短板

在优化之前，DuckDB 目标的 UPDATE 只有 **约 1,800 行/秒**，2 万行要跑 11 秒；DELETE 约 **7,500 行/秒**。

原因不是 DuckDB 本身慢，而是写入方式：DuckDB JDBC 的 `executeBatch` 并不会在服务端真正批量执行，逐行 `INSERT OR REPLACE` 会把每一行都单独规划执行一次。

我们把 UPDATE/DELETE 改成集合式写入：

```sql
-- UPDATE
INSERT INTO t (...)
SELECT ... FROM (VALUES (...), (...), ...) AS v(...)
ON CONFLICT (pk) DO UPDATE SET ...

-- DELETE
DELETE FROM t AS t
USING (VALUES (...), (...), ...) AS v(pk)
WHERE t.pk = v.pk
```

优化后：

| 场景 | 优化前 | 优化后（4 字段） | 提升 |
|---|---:|---:|---:|
| UPDATE 2 万行 | 1,800 行/s | **3.2 万 ~ 4.0 万 行/s** | **约 18 ~ 23 倍** |
| DELETE 2 万行 | 7,500 行/s | **7.1 万 ~ 7.6 万 行/s** | **约 9 ~ 10 倍** |

20 字段宽表下，UPDATE 约 1 万行/s，虽然比窄表低，但仍比逐行写入快 5 倍以上。

这里还有两个容易被忽略的细节：

- DuckDB 的 `ON CONFLICT` 在同一语句里遇到重复主键时保留第一条，而 CDC 语义要求“后写覆盖”，所以必须先按主键去重，保留最后一条；
- 主键变更不能简单合并，否则同一事务里的链式改键会乱序，必须按事件顺序处理“删旧键 + 写新键”。

性能优化不是把 SQL 换个写法就结束了，语义正确性同样重要。

### 发现二：单行事务有约 10ms 的固定成本

单行 INSERT 的 p50 约 10ms，拆开看：

- MySQL 单行事务提交：约 4 ms；
- 状态文件持久化：文件 fsync + 原子 rename + 目录 fsync，在 macOS 上实测约 8 ms；
- 目标端写入与可见：约 6 ms。

也就是说，**小事务的吞吐上限约为 100 ~ 125 事务/秒**。想提高吞吐，最直接的办法不是换机器，而是增大事务批量：

- 1000 行/事务时，50 万行约 11 万 行/s；
- 5000 行/事务时，同样 50 万行约 24 万 ~ 26 万 行/s。

对业务侧的建议很实际：**能批量提交就批量提交，不要用单行事务跑高吞吐同步**。

### 发现三：PostgreSQL 有一个隐藏开关

PostgreSQL 默认的 JDBC batch 没有把 INSERT 重写成多行语句。在 URL 上加上一个参数：

```
jdbc:postgresql://127.0.0.1:15432/datay_test?reWriteBatchedInserts=true
```

效果非常明显：

| 场景 | 默认 | 开启后 | 提升 |
|---|---:|---:|---:|
| 全量快照 100 万行 | 4.43 s / 22.6 万 行/s | **2.25 s / 44.4 万 行/s** | 约 **2 倍** |
| 增量 INSERT 50 万行 | 3.46 s / 14.5 万 行/s | **2.10 s / 23.8 万 行/s** | 约 **1.6 倍** |

如果你的 PostgreSQL 目标以全量同步、批量写入为主，这个参数值得加入数据源 URL。

UPDATE / DELETE / 单行延迟基本不受影响。

---

## 06 最后

DataY 想做的事情一直没变：**用最极致的技术栈精简，交付最完整的数据能力。**

一个 JAR、2 核 4G 就能跑通数据集成 → 数仓 → 问数，这不是一句口号。实时同步的性能，也不是靠 PPT 讲出来的，是一行一行跑出来的。

如果你也在做 MySQL CDC、实时数仓、跨库同步，欢迎试试 DataY，也欢迎把你们的场景和压测结果发给我们。

- GitHub：https://github.com/yuyuejia/datay
- Gitee：https://gitee.com/yuyuejia/datay
- 官网：http://datay.yuyuejia.com.cn/
