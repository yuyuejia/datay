# SqlSink 组件配置文档

## 组件概述

`SqlSink` 是一个通用 SQL 写入组件。它不自动生成数据库方言 SQL，而是执行用户配置的单条 DML 模板。

模板中的动态参数会在运行时解析为 JDBC `?` 占位符，并通过 `PreparedStatement` 绑定，因此可以通过 SQL 模板实现：

- MySQL：`INSERT ... ON DUPLICATE KEY UPDATE col = col + VALUES(col)`
- PostgreSQL / DuckDB：`INSERT ... ON CONFLICT ... DO UPDATE SET col = table.col + EXCLUDED.col`
- Oracle / SQL Server：`MERGE` 中的 `target.col + source.col`
- 普通 `INSERT`、`UPDATE`、`DELETE`

它适合把实时增量指标原子累加到汇总表，而不是像 `StreamJdbcOutput` 的 `update` 模式那样整行覆盖。

> 注意：`SqlSink` 使用 `PreparedStatement` 参数绑定，动态参数只能出现在值位置，不能用于表名、列名或 SQL 关键字。字符串和日期不需要手动加引号；值绑定可以避免值位置上的 SQL 注入，但动态 SQL 结构仍应由使用者保证安全。

## 核心特性

- **SQL 模板 + 动态参数绑定**：在 SQL 中直接写 `${字段名}`、`${attr:属性名}`、`#{...}`，运行时转换为 JDBC `?` 占位符
- **批量执行**：按 `batchSize` 分批 `addBatch` / `executeBatch`
- **事务控制**：一个 FlowFile 内的所有批次可在一个事务中提交
- **事件过滤**：可只处理 `INSERT`、`UPDATE` 或 `DELETE`
- **嵌套字段**：支持 `${__before.amount}` 这类 CDC before 镜像字段
- **FlowFile 属性**：支持 `${attr:_eventType}`、`${attr:_xid}`、`${attr:_table}` 等
- **方言无关**：累加、UPSERT、MERGE 的 SQL 由使用者编写

## 配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `.id` | String | 是 | - | 组件唯一标识 |
| `.name` | String | 是 | - | 固定为 `SqlSink` |
| `parallelism` | String | 否 | `1` | 组件并行度 |
| `sql` | String | 是 | - | 单条 DML 模板，支持动态参数 |
| `batchSize` | Integer | 否 | `500` | 每个 JDBC 批次的行数，最大 10000 |
| `transaction` | Boolean | 否 | `true` | 一个 FlowFile 内的批次是否在同一事务提交 |
| `ignoreError` | Boolean | 否 | `false` | 失败时是否只记录日志并跳过 |
| `eventTypes` | Array<String> | 否 | `[]` | 只处理指定事件类型，空表示全部 |
| `queryTimeout` | Integer | 否 | `0` | SQL 超时秒数，0 表示不设置 |

### 数据源参数

| 参数名 | 类型 | 必填 | 描述 |
| --- | --- | --- | --- |
| `sourceId.url` | String | 是 | 目标数据库连接 URL |
| `sourceId.driver` | String | 否 | JDBC 驱动类名 |
| `sourceId.username` | String | 是 | 数据库用户名 |
| `sourceId.password` | String | 是 | 数据库密码 |
| `sourceId.dbschema` | String | 否 | 默认 schema |

> 在 DataY Web 中配置时，`sourceId` 是数据源 ID，后端会转换成完整的数据源对象。直接写任务 JSON 时，也可以使用 `datasource` 字段。

## 动态参数

SQL 中可以使用以下动态参数，运行时从当前行或 FlowFile 中取值：

| 写法 | 说明 |
| --- | --- |
| `${字段名}` | 取当前行字段；支持 `${__before.amount}` 这类嵌套路径；取不到时回退到 FlowFile 属性 |
| `${attr:属性名}` | 只取 FlowFile 属性，例如 `${attr:_eventType}` |
| `${FLOW_FILE_DATA}` | 当前 FlowFile 的完整数据内容 |
| `${ROW_DATA}` | 当前行的完整 JSON 数据 |
| `#{...}` | 内置参数和时间表达式，如 `#{UUID}`、`#{TIMESTAMP}`、`#{NOW - 1d, yyyy-MM-dd}` |

规则：

1. 动态参数会转换为 JDBC `?` 占位符，值通过 `PreparedStatement.setObject` 绑定。
2. 字符串和日期不需要加引号，例如 `${stat_date}`、`${attr:_eventType}`。
3. 不要在动态参数外面手动加引号，写成 `'${stat_date}'` 会被当作普通字符串 `'?'`，而不是绑定参数。
4. 参数缺失或值为 `null` 时，组件会直接报错，不会把 `null` 静默写入。
5. 如果需要字面量 `?`，请写在 SQL 字符串常量中（例如 `'?'`）；不要手动写裸 `?` 作为占位符。
6. 动态参数只能用于值位置；表名、列名、排序字段等 SQL 结构不能通过参数绑定。
7. 如果目标驱动对字符串到日期/数值的隐式转换较严格，可在 SQL 中显式转换，例如 `CAST(${stat_date} AS DATE)`。

### 示例

```sql
INSERT INTO dws_store_sales (stat_date, store_key, order_cnt, sales_amount)
VALUES (${stat_date}, ${store_key}, ${order_cnt_delta}, ${sales_amount_delta})
ON DUPLICATE KEY UPDATE
  order_cnt = order_cnt + VALUES(order_cnt),
  sales_amount = sales_amount + VALUES(sales_amount)
```

其中：

- `${stat_date}` 取当前行的 `stat_date`
- `${store_key}` 取当前行的 `store_key`
- `${order_cnt_delta}`、`${sales_amount_delta}` 取当前行的增量指标
- `${attr:_eventType}` 可以取 FlowFile 的 `_eventType` 属性，绑定到字符串列时不需要加引号

## 执行流程

1. 调试模式跳过实际写库。
2. 校验 SQL、数据源和 DML 前缀。
3. 读取 FlowFile 的 JSON 数组或 JSON 对象。
4. 按 `eventTypes` 过滤事件。
5. 获取目标数据源连接。
6. `transaction=true` 时关闭自动提交。
7. 如果 SQL 中不含动态参数，作为静态 SQL 每个非空 FlowFile 执行一次。
8. 如果 SQL 中含动态参数，逐行解析参数值并绑定到同一个 `PreparedStatement`，按 `batchSize` 累计执行 `addBatch` / `executeBatch`。
9. 全部完成后统一 `commit()`；异常时 `rollback()`。
10. `ignoreError=false` 时抛出异常，避免 CDC 的 `_error_ignore` 属性吞掉失败。

## MySQL 累加示例

### 目标表

```sql
CREATE TABLE dws_store_sales (
  stat_date DATE NOT NULL,
  store_key VARCHAR(50) NOT NULL,
  order_cnt BIGINT NOT NULL DEFAULT 0,
  sales_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, store_key)
);
```

### 任务 JSON

```json
{
  ".id": "summary_sink",
  ".name": "SqlSink",
  "sourceId": {
    "type": "mysql",
    "url": "jdbc:mysql://127.0.0.1:3306/dws",
    "driver": "com.mysql.cj.jdbc.Driver",
    "username": "root",
    "password": "******",
    "dbschema": "dws"
  },
  "sql": "INSERT INTO dws_store_sales (stat_date, store_key, order_cnt, sales_amount) VALUES (${stat_date}, ${store_key}, ${order_cnt_delta}, ${sales_amount_delta}) ON DUPLICATE KEY UPDATE order_cnt = order_cnt + VALUES(order_cnt), sales_amount = sales_amount + VALUES(sales_amount)",
  "batchSize": 500,
  "transaction": true
}
```

上游 StreamSQL 需要把一批事件先聚合成增量行，例如：

```sql
SELECT
  stat_date,
  store_key,
  COUNT(*) AS order_cnt_delta,
  SUM(pay_amount) AS sales_amount_delta
FROM order_events
GROUP BY stat_date, store_key
```

MySQL 8.0.19+ 也可以使用别名写法：

```sql
INSERT INTO dws_store_sales (stat_date, store_key, order_cnt, sales_amount)
VALUES (${stat_date}, ${store_key}, ${order_cnt_delta}, ${sales_amount_delta}) AS new
ON DUPLICATE KEY UPDATE
  order_cnt = dws_store_sales.order_cnt + new.order_cnt,
  sales_amount = dws_store_sales.sales_amount + new.sales_amount
```

## PostgreSQL 累加示例

```sql
INSERT INTO dws_store_sales (stat_date, store_key, order_cnt, sales_amount)
VALUES (${stat_date}, ${store_key}, ${order_cnt_delta}, ${sales_amount_delta})
ON CONFLICT (stat_date, store_key) DO UPDATE SET
  order_cnt = dws_store_sales.order_cnt + EXCLUDED.order_cnt,
  sales_amount = dws_store_sales.sales_amount + EXCLUDED.sales_amount
```

## Oracle / SQL Server MERGE 示例

Oracle：

```sql
MERGE INTO dws_store_sales target
USING (
  SELECT ${stat_date} AS stat_date, ${store_key} AS store_key,
         ${order_cnt_delta} AS order_cnt, ${sales_amount_delta} AS sales_amount
  FROM dual
) source
ON (target.stat_date = source.stat_date AND target.store_key = source.store_key)
WHEN MATCHED THEN UPDATE SET
  target.order_cnt = target.order_cnt + source.order_cnt,
  target.sales_amount = target.sales_amount + source.sales_amount
WHEN NOT MATCHED THEN INSERT (stat_date, store_key, order_cnt, sales_amount)
VALUES (source.stat_date, source.store_key, source.order_cnt, source.sales_amount)
```

SQL Server：

```sql
MERGE INTO dws_store_sales AS target
USING (
  SELECT ${stat_date} AS stat_date, ${store_key} AS store_key,
         ${order_cnt_delta} AS order_cnt, ${sales_amount_delta} AS sales_amount
) AS source
ON (target.stat_date = source.stat_date AND target.store_key = source.store_key)
WHEN MATCHED THEN UPDATE SET
  target.order_cnt = target.order_cnt + source.order_cnt,
  target.sales_amount = target.sales_amount + source.sales_amount
WHEN NOT MATCHED THEN INSERT (stat_date, store_key, order_cnt, sales_amount)
VALUES (source.stat_date, source.store_key, source.order_cnt, source.sales_amount);
```

## UPDATE / DELETE 的负增量

汇总表做增量累加时，`DELETE` 和部分 `UPDATE` 需要写入负增量：

- `DELETE`：`order_cnt_delta = -1`，`sales_amount_delta = -old_amount`
- `UPDATE`：`sales_amount_delta = new_amount - old_amount`
- 如果状态变化会导致分类迁移（例如 `PAID` → `CANCELLED`），需要同时写“旧分类 -1、新分类 +1”两行

CDC 的 `UPDATE` 事件在记录中可能带有 `__before`，可以在 StreamSQL 中通过 `__before.amount` 读取旧值；`DELETE` 事件的数据本身就是变更前镜像。

```sql
INSERT INTO dws_store_sales (stat_date, store_key, order_cnt, sales_amount)
VALUES (${stat_date}, ${store_key}, -1, -${__before.amount})
ON DUPLICATE KEY UPDATE
  order_cnt = order_cnt + VALUES(order_cnt),
  sales_amount = sales_amount + VALUES(sales_amount)
```

## 注意事项

1. **不自动建表**：目标表必须提前创建，并具备合适的主键或唯一约束。
2. **只支持单条 DML**：`SqlSink` 不支持一次执行多条 SQL；一条 SQL 配置一个组件。
3. **参数只能用于值位置**：动态参数会转换成 JDBC `?` 占位符，不能用于表名、列名、排序字段等 SQL 结构；SQL 结构动态变化时应拆分任务或使用受控的 SQL 生成逻辑。
4. **重放不是幂等的**：任务重启或位点回退时，同一批增量可能被重复累加。需要业务侧按 `_xid`、Binlog 位点或事件 ID 做去重，或改用“明细层 + 重算”方案。
5. **事务兼容性**：Doris 等不支持 JDBC 事务的目标应将 `transaction` 设为 `false`。
6. **`ignoreError` 谨慎开启**：开启后失败只记录日志，可能造成数据缺失。
7. **动态参数缺失会失败**：请先在调试页确认上游字段名和属性名。
8. **不要手动写裸 `?`**：`SqlSink` 只把 `${...}` / `#{...}` 转换为 JDBC 占位符；如果 SQL 中还有手工写的裸 `?`，会被 JDBC 当成额外参数，导致参数数量不匹配。

## 故障排除

**问题：SQL 中存在未解析的动态参数**

- 原因：当前行没有该字段，FlowFile 没有该属性，或参数名写错。
- 解决：在调试页查看上游采样数据和关键属性，修正 `${...}` / `${attr:...}`。

**问题：字符串参数被写成了问号或报参数数量不匹配**

- 原因：在动态参数外面手动加了引号，例如 `'${stat_date}'`，会生成 `'?'` 而不是绑定参数；或者 SQL 中手动写了裸 `?`。
- 解决：去掉动态参数外的引号，写成 `${stat_date}`；不要手动写裸 `?`。

**问题：`UPDATE` / `DELETE` 后汇总值不对**

- 原因：只处理了 `INSERT`，没有为 `UPDATE` / `DELETE` 生成负增量。
- 解决：在 StreamSQL 中根据 `_eventType` 和 `__before` 生成正负 delta。

**问题：任务重启后汇总值翻倍**

- 原因：增量累加不是幂等操作。
- 解决：增加去重键，或改为从明细表按窗口重算。

**问题：Doris 写入报事务相关错误**

- 原因：目标不支持 JDBC 事务。
- 解决：将 `transaction` 设为 `false`。

---

*本文档基于 SqlSink 组件 v1.0 版本编写*
