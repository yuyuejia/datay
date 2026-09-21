# DataTransform 数据转换组件配置文档

## 组件概述

DataTransform 是一个基于 DuckDB 的数据转换组件，预置了常用的转换规则（类型转换、过滤、字符串/数学/日期函数等）。组件根据用户配置的规则列表自动生成 DuckDB SQL，对上游数据进行转换。

处理流程：

1. 接收上游 FlowFile，将数据写入 DuckDB 临时表（`main.tmp_<组件id>`）；
2. 根据 `rules`、`selectColumns`、`filter` 生成转换 SQL；
3. 执行转换，将结果集封装为 FlowFile 发送给下游。

每个 FlowFile 独立转换（可按批次流式处理，不做跨批次聚合）。

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `DataTransform` |
| `rules` | Array | 否 | `[]` | 转换规则列表 |
| `selectColumns` | String | 否 | - | 基础输出字段；为空时输出 `* EXCLUDE(被覆盖/删除/重命名的字段)` |
| `filter` | String | 否 | - | 额外的原始 WHERE 条件，与规则中的过滤条件以 AND 组合 |
| `outputTable` | String | 否 | 上游输入表名，无则 `tmp_<id>` | 输出给下游时携带的表名（表元数据标识） |

## 转换规则

规则通用字段：

| 字段 | 说明 |
|------|------|
| `type` | 规则类型 |
| `column` | 待处理的输入字段 |
| `targetColumn` | 输出字段别名；留空或与 `column` 相同时表示覆盖原字段 |

### 字段处理规则

| type | 说明 | 额外字段 | 生成的 DuckDB 表达式 |
|------|------|----------|----------------------|
| `cast` | 类型转换 | `targetType`；`DECIMAL`/`NUMERIC` 时用 `precision`、`scale` 指定精度与小数位 | `CAST(col AS DECIMAL(10,2))` |
| `rename` | 字段重命名 | - | `col AS targetColumn` |
| `drop` | 删除字段 | - | 从 `*` 中排除 |
| `upper` / `lower` | 大小写转换 | - | `UPPER(col)` / `LOWER(col)` |
| `trim` / `ltrim` / `rtrim` | 去除空格 | - | `TRIM(col)` 等 |
| `replace` | 字符串替换 | `search`、`replacement` | `REPLACE(col, 'a', 'b')` |
| `substring` | 截取子串 | `start`、`length` | `SUBSTRING(col, 1, 3)` |
| `concat` | 拼接字段 | `columns`、`separator` | `CONCAT_WS(' ', col, other)` |
| `coalesce` | 空值填充 | `defaultValue` / `values` | `COALESCE(col, 'x')` |
| `round` | 四舍五入 | `decimals` | `ROUND(col, 2)` |
| `ceil` / `floor` / `abs` / `sqrt` | 数学函数 | - | `CEIL(col)` 等 |
| `length` | 字符串长度 | - | `LENGTH(col)` |
| `date_format` | 日期格式化 | `format` | `STRFTIME(col, '%Y-%m-%d')` |
| `parse_date` | 字符串转日期 | `format` | `STRPTIME(col, '%Y-%m-%d')` |
| `extract` | 日期部分提取 | `part` | `DATE_PART('year', col)` |
| `split` | 字符串拆分 | `delimiter`、`index` | `SPLIT_PART(col, ',', 1)` |
| `add` / `subtract` / `multiply` / `divide` | 四则运算 | `value`、`valueIsColumn` | `(col + value)` |
| `custom` | 自定义表达式 | `expression` | `expression`（`{column}` 替换为字段名） |

### 过滤规则

| type | 说明 | 额外字段 |
|------|------|----------|
| `filter` | 过滤条件 | `operator`、`value`、`valueIsColumn` / `raw` |

`operator` 支持：`=`、`!=`、`>`、`>=`、`<`、`<=`、`LIKE`、`NOT LIKE`、`IN`、`NOT IN`、`IS NULL`、`IS NOT NULL`、`BETWEEN`、`NOT BETWEEN`。

- `IN` / `BETWEEN` 的 `value` 为数组；
- `valueIsColumn=true` 时 `value` 视为字段名；
- `raw=true` 时 `value` 作为原始 SQL 表达式拼入。

## 配置示例

```json
{
  ".id": "data_transform",
  ".name": "DataTransform",
  "rules": [
    { "type": "cast", "column": "age", "targetType": "INTEGER" },
    { "type": "upper", "column": "name", "targetColumn": "name_upper" },
    { "type": "filter", "column": "status", "operator": "=", "value": "ACTIVE" },
    { "type": "filter", "column": "age", "operator": ">=", "value": 18 },
    { "type": "cast", "column": "price", "targetType": "DECIMAL", "precision": 12, "scale": 2 }
  ],
  "selectColumns": "",
  "filter": "tenant_id = 1",
  "outputTable": "transform_result"
}
```

将生成：

```sql
SELECT * EXCLUDE (age, price), CAST(age AS INTEGER) AS age, UPPER(name) AS name_upper,
       CAST(price AS DECIMAL(12,2)) AS price
FROM main.tmp_data_transform
WHERE (tenant_id = 1) AND status = 'ACTIVE' AND age >= 18
```

## 完整任务配置示例

```json
{
  "units": [
    {
      ".id": "jdbc_input",
      ".name": "StreamJdbcInput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/source_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "user",
        "password": "password",
        "dbschema": "source_db"
      },
      "table": "raw_data"
    },
    {
      ".id": "clean",
      ".name": "DataTransform",
      "rules": [
        { "type": "trim", "column": "name" },
        { "type": "coalesce", "column": "email", "defaultValue": "unknown" },
    { "type": "cast", "column": "age", "targetType": "INTEGER" },
    { "type": "cast", "column": "price", "targetType": "DECIMAL", "precision": 12, "scale": 2 },
        { "type": "filter", "column": "age", "operator": "IS NOT NULL" }
      ],
      "outputTable": "clean_data"
    },
    {
      ".id": "mysql_output",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/target_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "user",
        "password": "password",
        "dbschema": "target_db"
      },
      "table": "clean_data",
      "model": "append"
    }
  ],
  "connections": [
    { "sourceId": "jdbc_input", "targetId": "clean" },
    { "sourceId": "clean", "targetId": "mysql_output" }
  ]
}
```

## 扩展规则

`com.data.job.component.transform.TransformSqlBuilder` 支持运行时注册自定义规则：

```java
TransformSqlBuilder.registerHandler("md5", rule -> {
    // 返回 expression / alias / exclude / where
});
```

内置支持的规则类型可通过 `TransformSqlBuilder.supportedRuleTypes()` 获取。
