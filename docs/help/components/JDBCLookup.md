# JDBCLookup 组件配置文档

## 组件概述

JDBCLookup 是一个跨库关联查询组件。它读取上游 FlowFile 中的记录，以记录里的指定字段（如 `id`）作为查询条件，
批量查询另一个数据库中的关联表，并把查询结果字段回填到原记录中，再继续发送给下游。

一个 JDBCLookup 组件支持在**同一个数据源**下配置多张关联表。`tables` 数组中的每一项独立配置关联表与回填字段，
组件按数组顺序依次执行；后面的关联表可以引用前面已经回填到记录中的字段，实现链式 lookup。

典型场景：

- 上游数据只有 `user_id`，需要从用户库补齐 `user_name`、`dept_name` 等维度字段；
- 上游为编码/字典值，需要从字典库翻译成名称；
- 同一数据源内需要依次关联客户表、地区表、等级表，且后一张表依赖前一张表的回填结果；
- 上游与维表不在同一个数据库（甚至不同类型）时，无需把数据物化到 DuckDB 再 Join。

与 Join 组件的区别：

| 能力 | JDBCLookup | Join |
|------|------------|------|
| 关联方式 | 逐批查询外部库（`IN` 查询） | 先把数据写入 DuckDB，再生成 SQL Join |
| 适合场景 | 事实数据回填少量维表字段 | 多表全量关联、宽表构建 |
| 跨库 | 直接查询另一个数据源 | 需先把各表物化到 DuckDB |
| 多表 | 同一数据源下按顺序逐表回填 | 多表 JOIN 生成新的结果集 |
| 关联结果 | 回填到原记录 | 生成新的结果集 |

## 核心特性

- **多表顺序回填**：一个组件内配置多个关联表，共用 `sourceId`/`schema`，减少流程节点数量。
- **链式 lookup**：后面的关联表可以把前面回填出的字段作为 `localField`。
- **批量查询**：同一批关联键合并为 `IN` 查询，避免逐行访问数据库。
- **结果缓存**：相同关联键在任务运行期间只查询一次，缓存按关联表查询结构隔离。
- **关联类型**：每张表可独立选择 `LEFT` 或 `INNER`。

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `JDBCLookup` |
| `sourceId` | String | 是 | - | 所有关联表所在的数据源 ID |
| `schema` | String | 否 | 数据源默认 schema | 关联表所在 schema，所有关联表共用 |
| `tables` | Array | 否 | - | 关联表配置数组，按顺序执行；每项字段见下表。为空时使用旧版单表字段 |
| `table` | String | 否 | - | 旧版单表配置：关联表表名，仅在 `tables` 为空时生效 |
| `localField` | String | 否 | - | 旧版单表配置：上游记录中的关联字段 |
| `lookupField` | String | 否 | - | 旧版单表配置：关联表中与 `localField` 对应的字段 |
| `selectColumns` | String | 否 | 全部字段 | 旧版单表配置：需要回填的关联表字段，多个用英文逗号分隔 |
| `joinType` | String | 否 | `LEFT` | 旧版单表配置：`LEFT` 保留未匹配记录并补空；`INNER` 丢弃未匹配记录 |
| `outputPrefix` | String | 否 | 空 | 旧版单表配置：回填字段前缀 |
| `where` | String | 否 | - | 旧版单表配置：附加过滤条件（不含 `WHERE` 关键字），以 `AND` 追加 |
| `outputTable` | String | 否 | 上游表名 | 本组件结果集的表名；多个组件接入同一个 Join 时用于区分 |
| `cache` | Boolean | 否 | `true` | 是否缓存关联键的查询结果 |
| `batchSize` | Integer | 否 | `500` | 单次 `IN` 查询包含的关联键数量，最大 5000 |
| `cacheSize` | Integer | 否 | `10000` | 缓存键数量上限（LRU） |

### tables 数组字段

| 字段 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| `table` | String | 是 | - | 关联表表名 |
| `localField` | String | 是 | - | 当前步骤从记录中取值的上游字段；可以是上游字段，也可以是前面关联表回填出的字段 |
| `lookupField` | String | 是 | - | 关联表中与 `localField` 对应的字段 |
| `selectColumns` | String | 否 | 全部字段 | 需要回填的关联表字段，多个用英文逗号分隔 |
| `joinType` | String | 否 | `LEFT` | `LEFT` 保留未匹配记录并补空；`INNER` 丢弃未匹配记录 |
| `outputPrefix` | String | 否 | 空 | 回填字段前缀，如 `user_` |
| `where` | String | 否 | - | 附加过滤条件（不含 `WHERE` 关键字），以 `AND` 追加 |

## 执行逻辑

1. 解析 `tables`；如果 `tables` 为空，则使用旧的顶层单表字段构造一个关联表配置。
2. 按 `tables` 数组顺序逐个关联表执行：
   - 从当前记录中提取该表 `localField` 的值，按规范化后的字符串去重（兼容 `Integer`/`Long`/`BigDecimal` 等数值类型差异）；
   - 对缓存中缺失的关联键，按 `batchSize` 分批执行：

     ```sql
     SELECT <lookupField>, <selectColumns>
     FROM <schema>.<table>
     WHERE <lookupField> IN (?, ?, ...)
       AND (<where>)
     ```

   - 按关联键把查询结果回填到记录中：
     - `LEFT`：未匹配记录保留，回填字段为 `null`；
     - `INNER`：未匹配记录被过滤，后续关联表只处理剩余记录。
3. 所有关联表执行完成后，将所有回填字段追加到 FlowFile 的表元数据中。
4. 通过 `writeRecords` 把结果 FlowFile 发送给下游。

同一 FlowFile 内的关联键会合并为批量查询；跨 FlowFile 的重复关联键由缓存命中。
缓存 key 包含关联表查询结构，因此不同表的相同关联键不会互相串用。

## 字段命名

回填字段名 = `outputPrefix + 关联表字段名`。

例如：

- `outputPrefix = "user_"`，回填 `name`、`dept` → 输出字段 `user_name`、`user_dept`；
- `outputPrefix` 为空，回填 `name` → 输出字段 `name`，若上游已有同名字段则会被覆盖。

多表配置时建议为每张表配置不同前缀，避免字段冲突。

## 配置示例

```json
{
  ".id": "order_customer_lookup",
  ".name": "JDBCLookup",
  "sourceId": "customer_db",
  "schema": "demo",
  "cache": true,
  "batchSize": 500,
  "tables": [
    {
      "table": "customers",
      "localField": "customer_id",
      "lookupField": "id",
      "selectColumns": "name, member_level",
      "joinType": "LEFT",
      "outputPrefix": "customer_",
      "where": "status = 1"
    },
    {
      "table": "member_levels",
      "localField": "customer_member_level",
      "lookupField": "code",
      "selectColumns": "level_name",
      "joinType": "LEFT",
      "outputPrefix": "level_"
    }
  ]
}
```

上游记录：

```json
[
  { "order_id": 1001, "customer_id": 10 },
  { "order_id": 1002, "customer_id": 11 }
]
```

关联表 `customers`：

| id | name | member_level | status |
|----|------|--------------|--------|
| 10 | alice | 金卡 | 1 |
| 11 | bob | 银卡 | 1 |

关联表 `member_levels`：

| code | level_name |
|------|------------|
| 金卡 | 黄金会员 |
| 银卡 | 白银会员 |

输出记录：

```json
[
  {
    "order_id": 1001,
    "customer_id": 10,
    "customer_name": "alice",
    "customer_member_level": "金卡",
    "level_level_name": "黄金会员"
  },
  {
    "order_id": 1002,
    "customer_id": 11,
    "customer_name": "bob",
    "customer_member_level": "银卡",
    "level_level_name": "白银会员"
  }
]
```

旧版单表配置仍然可用：

```json
{
  ".id": "user_lookup",
  ".name": "JDBCLookup",
  "sourceId": "user_db",
  "schema": "demo",
  "table": "users",
  "localField": "user_id",
  "lookupField": "id",
  "selectColumns": "name, dept_name",
  "joinType": "LEFT",
  "outputPrefix": "user_",
  "where": "status = 1",
  "batchSize": 500,
  "cache": true
}
```

## 注意事项

- `tables` 按数组顺序执行。后面的关联表如果引用前面回填出的字段，必须保证该字段名包含前缀，例如 `customer_member_level`。
- 某一步使用 `INNER` 时，未匹配记录会被立即过滤，后续关联表只处理剩余记录。
- 多张表回填同名字段时，后执行的关联表会覆盖先执行的值；建议为每张表配置不同的 `outputPrefix`。
- `localField` 与 `lookupField` 应尽量保持相同的数据类型或可比较的字符串形式，否则可能无法匹配。
- 关联表字段名大小写会按关联表元数据自动修正；上游字段名会先精确匹配，再忽略大小写匹配。
- `where` 会直接拼接到 SQL 中，请确保来源可信，避免 SQL 注入风险。
- 关闭 `cache` 后，每个 FlowFile 的关联键都会重新查询数据库；维表数据在任务运行期间发生变化时，可关闭缓存以获取最新值。
- 查询结果中同一关联键存在多行时，仅取第一行（关联表应按关联字段保证唯一）。
- 所有关联表必须位于同一个 `sourceId` 和 `schema` 下；需要跨数据源时请使用多个 JDBCLookup 组件。
