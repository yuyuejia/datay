# Join 组件配置文档

## 组件概述

Join 是一个多表关联处理组件。它根据可视化配置生成 DuckDB SQL，将多个上游表按关联条件进行 Join，并把结果写入结果集表，再以 FlowFile 形式发送给下游。

组件同时兼容两类上游：

- **流式上游**（如 `StreamJdbcInput`）：Join 会先接收上游分批传来的 FlowFile，按表名写入 DuckDB，等待所有上游全部完成后，再执行 Join。
- **已物化上游**（如 `JdbcInput`、`SqlUnit`）：上游已把数据写入 DuckDB，Join 收到结束信号后直接执行。

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `Join` |
| `outputTable` | String | 否 | `join_result` | 结果集输出表名（DuckDB `main` schema） |
| `selectColumns` | String | 否 | `*` | 输出字段，使用表名限定，如 `orders.id, users.name` |
| `fromTable` | String | 是 | - | 主表表名，对应上游写入 DuckDB 的表名 |
| `joins` | Array | 否 | - | 关联表配置列表，元素为 `{type, table, leftTable, leftField, rightField}` |

### joins 元素说明

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| `type` | String | 否 | 关联类型：`INNER`（默认）、`LEFT`、`RIGHT`、`FULL`，也可直接写 `LEFT JOIN` |
| `table` | String | 是 | 关联表表名 |
| `leftTable` | String | 否 | 左表表名，缺省为主表；可引用主表或前面已关联的表 |
| `leftField` | String | 是 | 左表关联字段 |
| `rightField` | String | 是 | 当前关联表的关联字段 |
| `on` | String | 否 | 手写关联条件，如 `orders.user_id = users.id`；与 `leftField`/`rightField` 二选一 |

每个关联仅支持单字段关联，生成条件为 `<leftTable>.<leftField> = <rightTable>.<rightField>`，使用表名限定，无需配置别名。

Web 设计器中，表名与字段均由「调试上游获取表」按钮触发调试上游组件后生成下拉选项，字段来源于调试数据中的 tableMetadata，无需手工输入。

## 生成的 SQL

组件会根据配置生成如下 SQL 并执行：

```sql
CREATE OR REPLACE TABLE main.<outputTable> AS
SELECT <selectColumns>
FROM main.<fromTable>
<type> JOIN main.<table> ON <leftTable>.<leftField> = <rightTable>.<rightField>
...
```

表名前未显式指定 schema 时，默认使用 `main`。

## 配置示例

```json
{
  ".id": "order_join",
  ".name": "Join",
  "outputTable": "order_detail",
  "selectColumns": "orders.id AS order_id, orders.amount, users.name AS user_name, products.title",
  "fromTable": "orders",
  "joins": [
    { "type": "LEFT", "table": "users", "leftTable": "orders", "leftField": "user_id", "rightField": "id" },
    { "type": "INNER", "table": "products", "leftTable": "orders", "leftField": "product_id", "rightField": "id" }
  ]
}
```

## 处理流程

1. **接收数据**：上游为流式组件时，逐批接收 FlowFile，根据 FlowFile 携带的表元数据在 DuckDB `main` 中创建表（不存在时）并追加数据，支持多批次写入。
2. **等待完成**：当所有上游输入都发送结束信号（`_end`）后，组件才开始执行 Join，保证关联时数据完整。
3. **执行 Join**：根据配置生成 SQL，执行 `CREATE OR REPLACE TABLE ... AS SELECT ...`，将结果写入 `outputTable`。
4. **发送下游**：读取结果集表，按 `FETCH_SIZE` 分批封装为 FlowFile 发送给下游组件（如 `StreamJdbcOutput`、`JdbcOutput`）。

## 完整任务配置示例

```json
{
  "units": [
    {
      ".id": "orders_input",
      ".name": "StreamJdbcInput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/source_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "user",
        "password": "password",
        "dbschema": "source_db"
      },
      "table": "orders"
    },
    {
      ".id": "users_input",
      ".name": "StreamJdbcInput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/source_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "user",
        "password": "password",
        "dbschema": "source_db"
      },
      "table": "users"
    },
    {
      ".id": "order_join",
      ".name": "Join",
      "outputTable": "order_detail",
      "selectColumns": "orders.id AS order_id, orders.amount, users.name AS user_name",
      "fromTable": "orders",
      "joins": [
        { "type": "LEFT", "table": "users", "leftTable": "orders", "leftField": "user_id", "rightField": "id" }
      ]
    },
    {
      ".id": "order_output",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/target_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "user",
        "password": "password",
        "dbschema": "target_db"
      },
      "table": "order_detail",
      "model": "overwrite"
    }
  ],
  "connections": [
    { "sourceId": "orders_input", "targetId": "order_join" },
    { "sourceId": "users_input", "targetId": "order_join" },
    { "sourceId": "order_join", "targetId": "order_output" }
  ]
}
```

## 使用场景

- **宽表构建**：将事实表与多张维表关联，输出宽表后写入目标库。
- **多源数据关联**：关联来自不同数据源（不同输入组件）的数据。
- **数据校验**：通过 `INNER JOIN` 过滤出关联不上的数据。
