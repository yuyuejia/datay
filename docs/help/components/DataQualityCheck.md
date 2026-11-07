# DataQualityCheck 数据质量检核组件

## 组件概述

`DataQualityCheck` 用于在 ETL 流程中执行数据质量检核。组件支持表级规则和字段级规则，
检核结果写入文件存储（本地或 MinIO）；当 ERROR 规则不达标时，组件抛出异常，当前任务失败。

支持两种模式：

| 模式 | 说明 |
|------|------|
| `FLOW` | 消费上游 FlowFile，持续写入 DuckDB 临时表，所有数据到达后执行检核 |
| `TABLE` | 直接检核指定的 DuckDB 表 |

`FLOW` 模式下组件会透传上游数据，因此可以放在流程中间；如果作为最后一个节点，则只执行检核。

## 配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识 |
| `.name` | String | 是 | - | 固定为 `DataQualityCheck` |
| `checkMode` | String | 否 | `FLOW` | `FLOW` / `TABLE` |
| `sourceTable` | String | TABLE 模式必填 | `dq_data_<组件id>` | 检核源表，如 `main.join_result` |
| `rules` | Array | 是 | `[]` | 质量规则列表 |
| `failOnError` | Boolean | 否 | `true` | ERROR 规则不达标时是否使任务失败 |
| `outputDir` | String | 否 | `data-quality/etl/<组件id>` | 结果目录（租户相对路径） |
| `resultPrefix` | String | 否 | `quality_<组件id>` | 结果文件名前缀 |

> WARN 规则仅记录告警，不使任务失败；失败规则抽样明细默认输出 100 行。
>
> 文件存储配置和租户前缀由设计器翻译器自动注入，通常无需手工填写。

## 规则配置

```json
{
  "ruleId": "规则ID",
  "ruleCode": "DQ_FIELD_NOT_NULL",
  "ruleName": "订单号非空",
  "ruleLevel": "FIELD",
  "ruleType": "NOT_NULL",
  "column": "order_id",
  "columns": [],
  "config": { "trimWhitespace": true },
  "severity": "ERROR",
  "maxInvalidRows": 0,
  "maxInvalidRatio": 0
}
```

阈值判定：

- `invalidRows <= maxInvalidRows`
- 且 `invalidRatio <= maxInvalidRatio`
- 任一不满足即视为不达标

## 支持的规则类型

| ruleType | 级别 | 说明 |
|----------|------|------|
| `ROW_COUNT_MIN` | 表级 | 表行数下限 |
| `ROW_COUNT_RANGE` | 表级 | 表行数范围 |
| `COMPOSITE_UNIQUE` | 表级 | 组合字段唯一 |
| `CUSTOM_SQL` | 表级 | 自定义 SQL，需返回 `invalid_rows`，可自由引用字段和聚合 |
| `NOT_NULL` | 字段级 | 字段非空，可配置空白字符串视为空值 |
| `UNIQUE` | 字段级 | 字段唯一 |
| `RANGE` | 字段级 | 数值 / 日期范围 |
| `ENUM` | 字段级 | 枚举值 |
| `REGEX` | 字段级 | 正则格式 |
| `LENGTH` | 字段级 | 字符串长度范围 |
| `FRESHNESS` | 字段级 | 数据时效 |

## 结果文件

```text
<outputDir>/<instanceCode>/
  <resultPrefix>_summary.csv
  <resultPrefix>_detail_1_<ruleKey>.csv
```

汇总文件字段：

```text
rule_key, rule_name, rule_level, column_name, rule_type, severity,
checked_rows, invalid_rows, invalid_ratio,
max_invalid_rows, max_invalid_ratio, status, message
```

## 配置示例

```json
{
  ".id": "dq_check",
  ".name": "DataQualityCheck",
  "checkMode": "FLOW",
  "rules": [
    {
      "ruleCode": "DQ_FIELD_NOT_NULL",
      "ruleName": "订单号非空",
      "ruleType": "NOT_NULL",
      "column": "order_id",
      "config": { "trimWhitespace": true },
      "severity": "ERROR",
      "maxInvalidRows": 0
    },
    {
      "ruleCode": "DQ_TABLE_ROW_COUNT_MIN",
      "ruleName": "表非空",
      "ruleType": "ROW_COUNT_MIN",
      "config": { "minRows": 1 },
      "severity": "ERROR",
      "maxInvalidRows": 0
    }
  ],
  "failOnError": true
}
```

## 常见问题

1. **任务在检核阶段失败，但结果文件已经生成？**
   这是预期行为。组件会先写入汇总和明细文件，再根据阈值抛出异常，便于排查问题数据。

2. **TABLE 模式找不到表？**
   TABLE 模式要求上游组件已经把数据物化到 DuckDB。例如先使用 `DuckDBWrite`、`JdbcInput` 或 `StreamSqlUnit`。
   FLOW 模式不依赖已有表，会自行创建临时表。

3. **自定义 SQL 有什么限制？**
   仅支持单条 `SELECT`，不能包含多条语句。自定义 SQL 需返回 `invalid_rows` 列。
