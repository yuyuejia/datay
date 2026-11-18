# RedisInput 组件配置文档

## 组件概述

`RedisInput` 是一个 Redis 数据输入组件，基于项目内置的 Redisson 客户端实现。组件按 key 模式扫描 Redis，
把每个 key 读取为一行键级记录，并批量传递给下游组件。

在可视化画布中，`RedisInput` 位于组件面板的 **数据输入** 分组，组件编码为 `RedisInput`。

## 核心特性

- **按 key 模式扫描**：使用 `SCAN` 遍历 key，不使用阻塞的 `KEYS`。
- **多数据结构**：支持 String、Hash、List、Set、ZSet、Stream。
- **键级 JSON 记录**：集合类型的 value 序列化为 JSON 字符串，便于落库或继续处理。
- **类型过滤**：可以只读取指定类型的 key。
- **TTL 输出**：可选输出 key 的剩余存活时间。
- **安全限额**：支持最大 key 数、单 key 集合元素上限，避免超大 key 占用过多内存。

## 输出记录格式

每个 key 输出一行：

```json
{
  "key": "user:1",
  "type": "hash",
  "value": "{\"name\":\"张三\",\"age\":18}",
  "ttl": -1
}
```

| 字段 | 说明 |
| --- | --- |
| `key` | Redis key |
| `type` | `string` / `hash` / `list` / `set` / `zset` / `stream` |
| `value` | String 为原始字符串；Hash/List/Set/ZSet/Stream 为 JSON 字符串 |
| `ttl` | 剩余存活秒数，`-1` 表示永久；仅开启「输出 TTL」时存在 |

各类型 `value` 的内容：

| Redis 类型 | `value` 示例 |
| --- | --- |
| String | `"hello"` |
| Hash | `"{\"name\":\"张三\"}"` |
| List / Set | `"[\"a\",\"b\"]"` |
| ZSet | `"[{\"member\":\"m1\",\"score\":1.5}]"` |
| Stream | `"[{\"id\":\"1700000000000-0\",\"fields\":{\"f1\":\"v1\"}}]"` |

## 配置参数

### 基础配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `.id` | String | 是 | - | 组件唯一标识 |
| `.name` | String | 是 | - | 组件名称，固定为 `RedisInput` |
| `sourceId` | String | 是 | - | Redis 数据源 ID |
| `keyPattern` | String | 否 | `*` | key 匹配模式，支持 Redis glob |
| `typeFilter` | String | 否 | `ALL` | `ALL` / `string` / `hash` / `list` / `set` / `zset` / `stream` |

### 高级配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `scanCount` | Integer | 否 | `1000` | SCAN 每次请求的 key 数量 |
| `batchSize` | Integer | 否 | `500` | 每个 FlowFile 包含的最大 key 数 |
| `maxKeys` | Long | 否 | `0` | 最大读取 key 数，`0` 表示不限制 |
| `maxEntriesPerKey` | Integer | 否 | `1000` | Hash/List/Set/ZSet/Stream 单 key 最大元素数 |
| `includeTtl` | Boolean | 否 | `false` | 是否输出 `ttl` 字段 |

## 任务 JSON 示例

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
    }
  ]
}
```

## 注意事项

- key 在扫描后被删除时会自动跳过，不会导致任务失败。
- 集合类型受 `maxEntriesPerKey` 限制；超出部分不会读取。
- 调试模式读取真实 Redis，并由调试采样行数上限控制结束。
- `RedisInput` 的输出可直接接入 `RedisOutput`，实现 Redis → Redis 键级搬运。
