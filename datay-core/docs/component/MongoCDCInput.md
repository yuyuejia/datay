# MongoCDCInput 组件配置文档

## 组件概述

MongoCDCInput 是一个基于 MongoDB **Change Streams**（变更流）的实时数据输入组件，用于捕获 MongoDB 集合的数据变更。组件通过官方 MongoDB Java 驱动的 `watch` API 实时解析 `insert`、`update`、`replace`、`delete` 事件，是构建实时数据同步、CDC（Change Data Capture）和流式 ETL 流程的核心输入组件。

组件依赖 MongoDB 官方同步驱动，无需额外部署 Kafka/Debezium 等组件。

## 核心特性

- **实时数据变更捕获**：基于 Change Streams，毫秒级捕获数据变更
- **支持多种事件类型**：`insert`、`update`（含 `replace`）、`delete`
- **全量 + 增量**：可先对集合做一次性全量快照，再无缝切换到增量变更流
- **断点续传**：变更流 resumeToken 随数据流持久化，任务重启自动续传
- **集合级过滤**：支持单集合或基于集合名的正则表达式过滤
- **后镜像（post-image）**：update 事件默认使用 `updateLookup` 返回更新后的完整文档
- **事件合并优化**：相邻同集合同类型事件自动合并下发

## 前置要求（重要）

Change Streams **必须运行在副本集（replica set）或分片集群**上，单机模式（未开启 `--replSet`）不可用。

本地可用 docker 启动单节点副本集进行验证：

```bash
# 1. 启动单节点副本集
docker run -d --name datay-mongo-cdc -p 27017:27017 mongo:7 --replSet rs0 --bind_ip_all

# 2. 初始化副本集（host 必须写成宿主机可解析的地址）
docker exec datay-mongo-cdc mongosh --eval \
  'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'

# 3. 查看状态，确认 ok:1
docker exec datay-mongo-cdc mongosh --eval 'rs.status().ok'
```

> 采集账号需要对目标库/集合具备 `read` 权限；`Change Streams` 还要求具备读取 `local` 库 oplog 的权限（内置 `read` 或 `clusterMonitor` 等角色即可）。

## 配置参数

### 必需参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `datasource` | DatasourceInfo | ✅ | MongoDB 数据源连接信息 |

### 可选参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `database` / `schema` | String | ❌ | 采集的数据库，缺省取数据源的 `dbschema` | `"test"` |
| `collection` | String | ❌ | 采集的单个集合名，与 `collectionNamePattern` 二选一 | `"cdc_demo"` |
| `collectionNamePattern` | String | ❌ | 集合名过滤正则，为空表示数据库下所有集合 | `"order_.*"` |
| `fullDocument` | String | ❌ | 后镜像策略：`updateLookup`（默认）、`whenAvailable`、`required` | `"updateLookup"` |
| `maxAwaitTimeMs` | Long | ❌ | 变更流阻塞等待时间（毫秒），默认 `1000` | `2000` |
| `resumeToken` | String | ❌ | 指定续传的 resumeToken（JSON 字符串），为空则用任务状态或从当前位置 | `"{\"_data\":...}"` |
| `startAtOperationTime` | Long | ❌ | 从指定 operationTime（epoch 秒）开始采集 | `1710000000` |
| `snapshot` | Boolean | ❌ | 首次运行（无断点）时先读取历史全量数据，再启动增量同步，默认 `false` | `true` |
| `snapshotFetchSize` | Integer | ❌ | 快照分批大小，默认 `10000` | `5000` |
| `ignoreError` | Boolean | ❌ | 采集启动失败时是否忽略（不抛出中断任务），默认 `true` | `false` |

## 数据源配置 (DatasourceInfo)

```json
{
  "type": "mongodb",
  "url": "mongodb://127.0.0.1:27017",
  "username": "root",
  "password": "123456",
  "dbschema": "test",
  "extraParams": {
    "directConnection": "true",
    "replicaSet": "rs0",
    "authSource": "admin"
  }
}
```

> `extraParams` 支持 `authSource`、`replicaSet`、`directConnection`、`tls`/`ssl`，与 MongoDB 数据源的其它组件一致。

## 配置示例

### 示例1：采集单个集合

```json
{
  ".id": "MongoCDCInput",
  ".name": "MongoCDCInput",
  "datasource": {
    "type": "mongodb",
    "url": "mongodb://127.0.0.1:27017",
    "dbschema": "test",
    "extraParams": { "directConnection": "true" }
  },
  "database": "test",
  "collection": "cdc_demo"
}
```

### 示例2：按正则采集多个集合 + 全量快照

```json
{
  ".id": "mongo-cdc-input",
  ".name": "MongoCDCInput",
  "datasource": {
    "type": "mongodb",
    "url": "mongodb://127.0.0.1:27017",
    "dbschema": "test"
  },
  "database": "test",
  "collectionNamePattern": "order_.*",
  "snapshot": true,
  "snapshotFetchSize": 5000
}
```

## 工作原理

1. **建立连接**：使用 MongoDB 数据源连接读取变更流，另按需读取集合元数据（采样推断字段）。
2. **校验副本集**：确认目标为副本集；单机模式直接报错，避免静默不可用。
3. **解析集合范围**：按 `collection` 或 `collectionNamePattern` 解析待采集集合。
4. **构建变更流**：单集合使用 `collection.watch()`，多集合使用 `database.watch()` 并按 `ns.coll` 正则过滤；按 `fullDocument` 配置后镜像策略。
5. **读取变更**：循环读取 `ChangeStreamDocument`，转换为通用 CDC 事件（`BinlogEvent`）。
6. **事件下发**：`insert` 取 `fullDocument` 作为后镜像；`update`/`replace` 取 `fullDocument`（updateLookup）作为后镜像；`delete` 取 `documentKey` 作为前镜像。
7. **状态保存**：每条变更的 resumeToken 随 FlowFile 持久化，任务重启自动续传。

## 变更事件语义

| operationType | 映射事件 | 下发数据 |
|---------------|----------|----------|
| `insert` | INSERT | `fullDocument`（后镜像） |
| `update` / `replace` | UPDATE | `fullDocument`（updateLookup 后镜像） |
| `delete` | DELETE | `documentKey`（通常仅 `_id`） |
| `drop` / `rename` / `dropDatabase` / `invalidate` | 忽略 | 仅记录告警，不自动同步结构变更 |

## 首次全量快照（snapshot）

开启 `snapshot=true` 后，组件首次运行（无历史断点、无 `snapshotDone` 标记）会：

1. 校验副本集并获取当前 `operationTime` 作为增量起点（保证增量不丢）；
2. 对匹配范围内的集合做一次性全量读取，按 `INSERT` 事件分批下发；
3. 再从上述 `operationTime` 启动变更流增量同步；
4. 全量完成后写入 `snapshotDone` 状态，任务重启有断点或已完成快照时不会重复全量。

> 采用「简单一致性」策略：增量位点取自快照之前，快照窗口内发生的变更会由变更流补放，存在少量重复的可能。对幂等写入或存量基本稳定的场景足够。

## 与关系型 CDC 的差异（重要）

- **不传输结构变更**：`drop`、`rename`、`dropDatabase` 等结构类事件不会自动同步。
- **DELETE 前镜像有限**：默认仅能拿到 `documentKey`（通常为 `_id`），不含完整文档；如需完整前镜像需在集合上启用 pre-image。
- **集合无固定 schema**：字段元数据由采样推断，字段类型冲突时按 JSON 处理。
- **依赖副本集**：单机 mongod 不支持 Change Streams。

## 故障排除

**问题：报错「MongoDB 未启用副本集」**
- 使用 `--replSet` 启动 mongod 并执行 `rs.initiate(...)`，确认 `rs.status().ok == 1`。

**问题：`resumeToken` 解析失败**
- 组件会记录告警并改为从当前位置开始采集；确认配置的 resumeToken 为合法 JSON。

**问题：任务重启后数据重复或缺失**
- 组件会从任务状态中保存的 resumeToken 续传；若手动改动过 `resumeToken` 或清空了状态文件，需注意起点变化。

**问题：切换副本集成员后 host 不可解析**
- `rs.initiate` 时 `host` 应写成采集端（应用）可解析的地址，例如 `localhost:27017`。

---

*本文档基于 MongoCDCInput 组件 v1.0 版本编写*
