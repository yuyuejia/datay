# RedisOutput 组件配置文档

## 组件概述

`RedisOutput` 是一个 Redis 数据输出组件，基于项目内置的 Redisson 客户端实现。组件把上游 `FlowFile`
中的每一行写入 Redis，支持 String、Hash、List、Set、ZSet、Stream，并通过 Redisson Batch 批量提交。

在可视化画布中，`RedisOutput` 位于组件面板的 **数据输出** 分组，组件编码为 `RedisOutput`。

## 核心特性

- **多数据结构**：支持 String / Hash / List / Set / ZSet / Stream。
- **键级 JSON 往返**：默认读取行中的 `key` 和 `value` 字段，可直接承接 `RedisInput` 输出。
- **字段映射**：支持自定义 key 字段、key 前缀、value 字段、hash field、zset score、stream ID。
- **TTL**：支持固定 TTL 或按行读取 TTL 字段。
- **REPLACE 模式**：首次写某个 key 前先删除旧 key，适合覆盖写入。
- **批量提交**：按 `batchSize` 使用 Redisson Batch 提交，降低网络往返开销。
- **调试安全**：调试模式跳过真实写入。

## 写入映射

| 数据类型 | Redis 命令 | 默认映射 |
| --- | --- | --- |
| `STRING` | `SET` | `value` 字段，缺失时整行 JSON |
| `HASH` | `HSET` | `value` 为 JSON 对象时整体写入；否则按行字段写入 |
| `LIST` | `RPUSH` | `value` 为 JSON 数组时逐元素写入 |
| `SET` | `SADD` | `value` 为 JSON 数组时逐元素写入 |
| `ZSET` | `ZADD` | `value` 为 `[{member,score}]` 或 member/score 字段 |
| `STREAM` | `XADD` | `value` 为 JSON 对象或 `[{fields:{...}}]`；也支持按行字段写入 |

## 配置参数

### 基础配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `.id` | String | 是 | - | 组件唯一标识 |
| `.name` | String | 是 | - | 组件名称，固定为 `RedisOutput` |
| `sourceId` | String | 是 | - | Redis 数据源 ID |
| `dataType` | String | 否 | `STRING` | `STRING` / `HASH` / `LIST` / `SET` / `ZSET` / `STREAM` |

### 字段映射

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `keyField` | String | 否 | `key` | 从行中取该字段作为 key |
| `keyPrefix` | String | 否 | - | key 前缀，最终 key = 前缀 + key 字段值 |
| `valueField` | String | 否 | - | value 字段；留空时优先使用 `value` 字段 |
| `fieldField` | String | 否 | `field` | Hash 模式下 hash field 所在字段 |
| `scoreField` | String | 否 | `score` | ZSet 模式下 score 所在字段 |
| `streamIdField` | String | 否 | - | Stream 消息 ID 字段，留空或 `*` 时由 Redis 生成 |

### 写入配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `ttlSeconds` | Long | 否 | `0` | 固定 TTL，`0` 表示不过期 |
| `ttlField` | String | 否 | - | 从行中取 TTL，优先于固定 TTL |
| `writeMode` | String | 否 | `APPEND` | `APPEND` / `REPLACE` |
| `batchSize` | Integer | 否 | `500` | 每批提交给 Redis 的最大写入命令数 |

## 任务 JSON 示例

### 关系型数据写入 Hash

```json
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
  "keyField": "id",
  "keyPrefix": "user:",
  "valueField": "payload",
  "ttlSeconds": 3600,
  "writeMode": "REPLACE",
  "batchSize": 500
}
```

### Redis 到 Redis 键级搬运

```json
{
  ".id": "redis_copy",
  ".name": "RedisOutput",
  "sourceId": {
    "type": "REDIS",
    "url": "redis://127.0.0.1:6379",
    "dbschema": "0"
  },
  "dataType": "HASH",
  "keyField": "key",
  "valueField": "value",
  "writeMode": "REPLACE"
}
```

## 注意事项

- `REPLACE` 对 Stream 不生效，Stream 始终通过 `XADD` 追加。
- `REPLACE` 在单次任务运行中对同一个 key 只删除一次，同 key 的后续行按 APPEND 语义合并/追加。
- key 字段缺失或为空时任务会失败，避免静默写入错误 key。
- 调试模式不会向 Redis 写入测试数据。
- `RedisOutput` 依赖 Redis 数据源；请先在数据源管理中创建 `REDIS` 类型数据源。
