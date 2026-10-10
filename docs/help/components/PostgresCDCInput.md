# PostgresCDCInput 组件配置文档

## 组件概述

PostgresCDCInput 是一个基于 PostgreSQL **逻辑复制**（WAL + `pgoutput` 插件）的实时数据输入组件，用于捕获 PostgreSQL 数据库的数据变更。组件通过逻辑复制流实时解析 `INSERT`、`UPDATE`、`DELETE` 事件，是构建实时数据同步、CDC（Change Data Capture）和流式 ETL 流程的核心输入组件。

组件底层使用 pgjdbc 的复制 API 读取 WAL，并内置 `pgoutput` 协议解析器，无需额外部署 Kafka/Debezium 等组件。

## 核心特性

- **实时数据变更捕获**：基于 PostgreSQL 逻辑复制，毫秒级捕获数据变更
- **支持多种事件类型**：`INSERT`、`UPDATE`、`DELETE`
- **自动维护复制对象**：自动创建并维护 publication 与 replication slot
- **断点续传**：事务提交位点（LSN）随数据流持久化，任务重启自动续传
- **表级过滤**：支持基于表名的正则表达式过滤
- **事件合并优化**：按事务刷新，相邻同类型事件自动合并

## 前置要求

在源 PostgreSQL 上需要满足：

```sql
-- 1. 开启逻辑复制（需要重启数据库）
ALTER SYSTEM SET wal_level = logical;
-- 修改后重启 PostgreSQL

-- 2. 采集账号需要 REPLICATION 权限
ALTER ROLE <采集账号> WITH REPLICATION;

-- 3. 采集账号需要拥有（或具备权限访问）待采集的表
```

> 说明：创建 publication 需要账号拥有对应表；`FOR ALL TABLES` 需要超级用户。组件默认按 schema 下表逐个发布，因此采集账号拥有这些表即可。

## 配置参数

### 必需参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `datasource` | DatasourceInfo | ✅ | PostgreSQL 数据源连接信息 |
| `schema` / `schemaName` | String | ✅ | 采集的 schema，缺省取数据源的 `dbschema`，再缺省为 `public` |

### 可选参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `tableNamePattern` | String | ❌ | 表名过滤正则表达式，为空表示采集 schema 下所有表 | `"user_.*"` |
| `publicationName` | String | ❌ | publication 名称，留空自动生成 `datay_pub_<节点ID>` | `"datay_pub_cdc"` |
| `slotName` | String | ❌ | 逻辑复制槽名称，留空自动生成 `datay_slot_<节点ID>` | `"datay_slot_cdc"` |
| `startLsn` | String | ❌ | 指定从哪个 LSN 开始采集，为空表示从槽位确认位点续传 | `"0/16B3748"` |
| `snapshot` | Boolean | ❌ | 首次运行（无断点）时先读取历史全量数据，再启动增量同步，默认 `false` | `true` |
| `snapshotFetchSize` | Integer | ❌ | 快照分批大小，默认 `10000` | `5000` |

## 数据源配置 (DatasourceInfo)

```json
{
  "type": "postgresql",
  "url": "jdbc:postgresql://127.0.0.1:5432/testdb",
  "username": "admin",
  "password": "123456",
  "dbschema": "public"
}
```

## 配置示例

### 示例1：采集 schema 下全部表

```json
{
  ".id": "PostgresCDCInput",
  ".name": "PostgresCDCInput",
  "datasource": {
    "type": "postgresql",
    "url": "jdbc:postgresql://127.0.0.1:5432/testdb",
    "username": "admin",
    "password": "123456",
    "dbschema": "public"
  },
  "schema": "public"
}
```

### 示例2：按表过滤 + 固定 publication/slot

```json
{
  ".id": "pg-cdc-input",
  ".name": "PostgresCDCInput",
  "datasource": {
    "type": "postgresql",
    "url": "jdbc:postgresql://127.0.0.1:5432/testdb",
    "username": "admin",
    "password": "123456",
    "dbschema": "public"
  },
  "schema": "public",
  "tableNamePattern": "cdc_demo|customers",
  "publicationName": "datay_pub_demo",
  "slotName": "datay_slot_demo"
}
```

### 示例3：从指定 LSN 开始

```json
{
  ".id": "pg-cdc-input",
  ".name": "PostgresCDCInput",
  "datasource": {
    "url": "jdbc:postgresql://127.0.0.1:5432/testdb",
    "username": "admin",
    "password": "123456",
    "dbschema": "public"
  },
  "startLsn": "0/16B3748"
}
```

## 工作原理

1. **建立连接**：普通连接用于表元数据与 publication/Slot 维护；另建一条带 `replication=database` 的逻辑复制连接读取 WAL。
2. **维护发布**：按 schema 列出目标表并按正则过滤，自动 `CREATE/ALTER PUBLICATION ... FOR TABLE`。
3. **维护槽位**：不存在逻辑复制槽时自动创建（插件 `pgoutput`）。
4. **读取 WAL**：`PGReplicationStream` 按事务读取 `Begin / Relation / Insert / Update / Delete / Commit` 消息。
5. **协议解析**：`PgOutputDecoder` 解析 `pgoutput` 协议，按列名生成 before/after 数据，并按 PG 物理类型做基础转换。
6. **事件下发**：转换为通用 CDC 事件（`BinlogEvent`）交给下游；事务提交时刷新缓冲区并把确认位点回传服务端。
7. **状态保存**：提交位点（LSN）随 FlowFile 持久化，任务重启自动续传。

## 首次全量快照（snapshot）

开启 `snapshot=true` 后，组件首次运行（无历史断点、无 `snapshotDone` 标记）会：

1. 先创建 publication 与 replication slot，取 slot 一致点作为增量起点（保证增量不丢）；
2. 对 schema 下匹配 `tableNamePattern` 的表（**即 publication 的表范围**）做一次性全量读取（单事务 `REPEATABLE READ`），按 `INSERT` 事件分批下发；
3. 再从上述位点启动逻辑复制增量同步；
4. 全量完成后写入 `snapshotDone` 状态，任务重启有断点或已完成快照时不会重复全量。

> 快照读取的表范围与增量同步范围一致（由 `schema` + `tableNamePattern` 控制），范围外的表不会被读取。

> 采用「简单一致性」策略：快照位点取自快照之前，快照窗口内发生的变更会由 CDC 补放，存在少量重复的可能。对幂等写入或存量基本稳定的场景足够；如需严格一致可后续引入 PostgreSQL 导出快照。

## 与 MySQL CDC 的差异（重要）

- **不传输 DDL**：PostgreSQL 逻辑复制只发送数据变更，**不会**发送 `ALTER TABLE` 等 DDL。源表结构变更需手动处理目标端或重跑全量。
- **初始数据**：逻辑复制默认只捕获组件启动后的增量，存量数据需另行执行一次全量同步。
- **未变更 TOAST**：`UPDATE` 时未变更的大字段（TOAST）不会携带值。
- **TRUNCATE**：当前版本捕获到 `TRUNCATE` 时仅告警，不做同步。

## 故障排除

**问题：`wal_level` 不是 logical**
- 执行 `ALTER SYSTEM SET wal_level = logical;` 后重启 PostgreSQL，并用 `SHOW wal_level;` 确认。

**问题：权限不足 / `CREATE_REPLICATION_SLOT` 报错**
- 确认采集账号具备 `REPLICATION` 权限（`ALTER ROLE xxx WITH REPLICATION;`）。

**问题：publication 创建失败**
- 确认采集账号拥有待发布的表；或改用 `FOR ALL TABLES`（需超级用户）。

**问题：任务重启后数据重复或缺失**
- 确认 `slotName` 与上次一致；组件会从槽位确认位点或保存的 LSN 续传。

---

*本文档基于 PostgresCDCInput 组件 v1.0 版本编写*
