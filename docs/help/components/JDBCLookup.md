# JDBCLookup 组件配置文档

## 组件概述

JDBCLookup 是一个跨库关联查询组件。它读取上游 FlowFile 中的记录，以记录里的指定字段（如 `id`）作为查询条件，
批量查询另一个数据库中的关联表，并把查询结果字段回填到原记录中，再继续发送给下游。

典型场景：

- 上游数据只有 `user_id`，需要从用户库补齐 `user_name`、`dept_name` 等维度字段；
- 上游为编码/字典值，需要从字典库翻译成名称；
- 上游与维表不在同一个数据库（甚至不同类型）时，无需把数据物化到 DuckDB 再 Join。

与 Join 组件的区别：

| 能力 | JDBCLookup | Join |
|------|------------|------|
| 关联方式 | 逐批查询外部库（`IN` 查询） | 先把数据写入 DuckDB，再生成 SQL Join |
| 适合场景 | 事实数据回填少量维表字段 | 多表全量关联、宽表构建 |
| 跨库 | 直接查询另一个数据源 | 需先把各表物化到 DuckDB |
| 关联结果 | 回填到原记录 | 生成新的结果集 |

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `JDBCLookup` |
| `sourceId` | String | 是 | - | 关联表所在的数据源 ID |
| `schema` | String | 否 | 数据源默认 schema | 关联表所在 schema |
| `table` | String | 是 | - | 关联表表名 |
| `localField` | String | 是 | - | 上游记录中的关联字段，如 `user_id` |
| `lookupField` | String | 是 | - | 关联表中与 `localField` 对应的字段，如 `id` |
| `selectColumns` | String | 否 | 全部字段 | 需要回填的关联表字段，多个用英文逗号分隔，如 `name, dept_name` |
| `joinType` | String | 否 | `LEFT` | `LEFT` 保留未匹配记录并补空；`INNER` 丢弃未匹配记录 |
| `outputPrefix` | String | 否 | 空 | 回填字段前缀，如 `user_`；留空时直接使用关联表字段名，同名字段会被覆盖 |
| `where` | String | 否 | - | 附加过滤条件（不含 `WHERE` 关键字），以 `AND` 追加 |
| `cache` | Boolean | 否 | `true` | 是否缓存关联键的查询结果；相同关联键在任务内只查询一次 |
| `batchSize` | Integer | 否 | `500` | 单次 `IN` 查询包含的关联键数量，最大 5000 |
| `cacheSize` | Integer | 否 | `10000` | 缓存键数量上限（LRU） |

## 执行逻辑

1. 从每个 FlowFile 中提取 `localField` 的值，按规范化后的字符串去重（兼容 `Integer`/`Long`/`BigDecimal` 等数值类型差异）。
2. 对缓存中缺失的关联键，按 `batchSize` 分批执行：

   ```sql
   SELECT <lookupField>, <selectColumns>
   FROM <schema>.<table>
   WHERE <lookupField> IN (?, ?, ...)
     AND (<where>)
   ```

3. 按关联键把查询结果回填到记录中：
   - `LEFT`：未匹配记录保留，回填字段为 `null`；
   - `INNER`：未匹配记录被过滤。
4. 将回填字段追加到 FlowFile 的表元数据中，供下游组件识别字段与类型。
5. 通过 `writeRecords` 把结果 FlowFile 发送给下游。

同一 FlowFile 内的关联键会合并为批量查询，避免逐行访问数据库；跨 FlowFile 的重复关联键由缓存命中。

## 字段命名

回填字段名 = `outputPrefix + 关联表字段名`。

例如：

- `outputPrefix = "user_"`，回填 `name`、`dept` → 输出字段 `user_name`、`user_dept`；
- `outputPrefix` 为空，回填 `name` → 输出字段 `name`，若上游已有同名字段则会被覆盖。

建议配置前缀，避免覆盖上游字段。

## 配置示例

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

上游记录：

```json
[
  { "order_id": 1001, "user_id": 10 },
  { "order_id": 1002, "user_id": 11 }
]
```

关联表 `users`：

| id | name | dept_name | status |
|----|------|-----------|--------|
| 10 | alice | finance | 1 |
| 11 | bob | sales | 1 |

输出记录：

```json
[
  { "order_id": 1001, "user_id": 10, "user_name": "alice", "user_dept_name": "finance" },
  { "order_id": 1002, "user_id": 11, "user_name": "bob", "user_dept_name": "sales" }
]
```

## 注意事项

- `localField` 与 `lookupField` 应尽量保持相同的数据类型或可比较的字符串形式，否则可能无法匹配。
- 关联表字段名大小写会按关联表元数据自动修正；上游字段名会先精确匹配，再忽略大小写匹配。
- `where` 会直接拼接到 SQL 中，请确保来源可信，避免 SQL 注入风险。
- 关闭 `cache` 后，每个 FlowFile 的关联键都会重新查询数据库；维表数据在任务运行期间发生变化时，可关闭缓存以获取最新值。
- 查询结果中同一关联键存在多行时，仅取第一行（关联表应按关联字段保证唯一）。
