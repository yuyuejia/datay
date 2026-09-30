# ETL 组件介绍

ETL 组件是 DataY Core 数据处理流程中的节点。在 DataY Web 的可视化画布中，
你可以拖拽这些组件、连线编排数据流；在 Core 中，它们对应任务 JSON 里 `units` 的一项。

每个组件通过 `.name` 指定类型、`.id` 指定唯一标识，通过 `connections` 连接上下游。

## 输入组件

| 组件 | 说明 | 文档 |
| --- | --- | --- |
| `StreamJdbcInput` | JDBC 数据源输入，支持多表、增量、流式读取 | [StreamJdbcInput](components/StreamJdbcInput.md) |
| `MySQLBinlogInput` | 读取 MySQL Binlog，实时捕获数据变更（CDC） | [MySQLBinlogInput](components/MySQLBinlogInput.md) |
| `PostgresCDCInput` | PostgreSQL 逻辑复制（WAL / pgoutput）实时捕获变更 | [PostgresCDCInput](components/PostgresCDCInput.md) |

## 处理组件

| 组件 | 说明 | 文档 |
| --- | --- | --- |
| `StreamSqlUnit` | 流式 SQL 处理，使用 DuckDB 对流式数据做 SQL 转换 | [StreamSqlUnit](components/StreamSqlUnit.md) |
| `DataTransform` | 数据转换，预置类型转换 / 过滤 / 字符串 / 数学 / 日期等规则 | [DataTransform](components/DataTransform.md) |
| `Join` | 多表关联，按配置生成 DuckDB Join SQL | [Join](components/Join.md) |

## 输出组件

| 组件 | 说明 | 文档 |
| --- | --- | --- |
| `StreamJdbcOutput` | JDBC 数据源输出，支持 append / update / replace / overwrite 等模式 | [StreamJdbcOutput](components/StreamJdbcOutput.md) |
| `DorisStreamLoad` | 通过 Stream Load 协议把数据写入 Apache Doris | [DorisStreamLoad](components/DorisStreamLoad.md) |

## 其他组件

| 组件 | 说明 | 文档 |
| --- | --- | --- |
| `HttpListener` | HTTP 监听器，接收请求并触发数据处理任务 | [HttpListener](components/HttpListener.md) |
| `LlmComponent` | 按行调用大模型，兼容 OpenAI Chat Completions 协议 | [LlmComponent](components/LlmComponent.md) |

## 其他内置组件

以下组件在引擎中内置可用，文档仍在完善中：

- **输入**：`JdbcInput`
- **处理**：`DuckDBSql`、`SqlTask`、`JavaScriptComponent`
- **输出**：`DuckDBWrite`、`DuckLakeWrite`
- **路由**：`HashRouter`、`RandomRouter`
- **其他**：`GenerateFlowFile`

## 组件与场景案例

想知道组件在真实任务里怎么组合使用，可以参考 [场景案例](cases/README.md)，
例如 [MySQL CDC 实时同步到 DuckLake](cases/mysqlCDC-ducklake.md)。

## 下一步

- [数据集成与同步](guide/integration.md)：在画布中使用组件
- [场景案例](cases/README.md)：组件的真实组合示例
