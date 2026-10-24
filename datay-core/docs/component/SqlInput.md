# SqlInput 组件配置文档

## 组件概述

`SqlInput` 用于在指定 JDBC 数据源上执行自定义 SQL 查询，并把查询结果以 FlowFile 形式发送给下游。它支持两种模式：

1. **配置 SQL 模式**：在组件中直接填写 `sql`。
2. **上游 SQL 模式**：`sql` 留空，从上游 FlowFile 中读取完整 SQL。

两种模式都支持动态参数替换。

> 注意：`SqlInput` 是查询组件，内部使用 `Statement.executeQuery`，不适合执行 `UPDATE`、`DELETE`、`DDL`。需要写库时请使用 `StreamJdbcOutput`、`DuckDBSql` 或专门的 SQL 任务。

## 配置参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceId` / `datasource` | Object | 是 | 查询使用的数据源 |
| `sql` | String | 否 | 要执行的完整 SQL；留空时使用上游 FlowFile 中的 SQL |

## 动态参数

`sql` 中可以使用以下动态参数，运行时从当前 FlowFile 的属性或数据中取值：

| 写法 | 说明 |
| --- | --- |
| `${attr:属性名}` | 取当前 FlowFile 的属性值 |
| `${属性名}` | 取当前 FlowFile 的属性值；上游只有一行数据时，也可取该行字段 |
| `${FLOW_FILE_DATA}` | 取当前 FlowFile 的完整数据内容 |
| `#{...}` | 内置参数，如 `#{TIMESTAMP}`、`#{UUID}`、`#{NOW - 1d}` |

当 FlowFile 是单个 JSON 对象，或只包含一行 JSON 数组时，`${字段名}` 可以直接引用该行字段。多行数组不会自动取某一行，避免产生歧义；多行场景请先用上游组件聚合成单行，或把条件放到属性中。

示例：

```sql
SELECT *
FROM orders
WHERE id IN (${attr:id_condition})
  AND update_time >= '#{NOW - 1d}'
```

上游 FlowFile 中存在属性：

```text
id_condition = 1,2,3
```

实际执行的 SQL 为：

```sql
SELECT *
FROM orders
WHERE id IN (1,2,3)
  AND update_time >= '2026-10-23 10:00:00'
```

参数值会直接替换进 SQL 文本，不会作为 `PreparedStatement` 参数绑定，因此只应引用可信的上游数据，避免 SQL 注入。

## 上游 SQL 模式

当 `sql` 留空时，`SqlInput` 按以下顺序从 FlowFile 读取完整 SQL：

1. 文本数据 `textData`
2. `_sql` 属性
3. `_query` 属性

同时需要 `_table` 属性作为结果表名；缺失时回退为 `sql_query_result`。

典型用法：

```text
GenerateTableSelectSql → SqlInput
```

上游也可以先拼接好完整 SQL，再通过 `_sql` 或文本数据传给 `SqlInput`。注意传入的必须是**完整 SQL**，不能只传 `WHERE` 条件片段。

## 配置示例

### 固定 SQL + 动态参数

```json
{
  ".id": "query_orders",
  ".name": "SqlInput",
  "datasource": {
    "url": "jdbc:mysql://127.0.0.1:3306/demo",
    "driver": "com.mysql.cj.jdbc.Driver",
    "username": "root",
    "password": "password",
    "dbschema": "demo"
  },
  "sql": "SELECT * FROM orders WHERE id IN (${attr:id_condition})"
}
```

### 上游传入完整 SQL

```json
{
  ".id": "query_from_upstream",
  ".name": "SqlInput",
  "datasource": {
    "url": "jdbc:mysql://127.0.0.1:3306/demo",
    "driver": "com.mysql.cj.jdbc.Driver",
    "username": "root",
    "password": "password",
    "dbschema": "demo"
  }
}
```

上游 FlowFile 需要设置：

- `_sql` 或文本数据：完整 SQL
- `_table`：结果表名（可选，缺省为 `sql_query_result`）

## 注意事项

1. `SqlInput` 只负责查询，不负责更新目标表。
2. 动态参数是文本替换，不会自动加引号；字符串条件需要自己写引号，或使用数据库函数。
3. 动态参数替换失败时会保留原 SQL 并记录告警，建议在调试页确认上游属性和输出字段。
4. 大数据量查询建议在 SQL 中带过滤条件，并配合数据源索引。
