# 任务设计与开发

DataY 提供可视化的任务设计能力，覆盖数据集成、SQL、Shell 以及 DAG 工作流。

## 任务类型

| 类型 | 说明 |
| --- | --- |
| ETL | 基于 DataY Core 引擎的数据集成任务 |
| SQL | 执行 SQL 脚本任务 |
| Shell | 执行 Shell 脚本任务 |
| DAG | DAG 工作流编排任务，按依赖关系拓扑排序执行 |
| DEMO | 演示任务 |

## ETL 可视化设计

- 从组件面板拖拽组件节点到画布，通过连线编排数据流
- 双击节点打开配置弹窗，配置组件参数
- 通过 Cron 表达式选择器配置调度规则
- 保存后自动生成 DataY Core 可执行的任务 JSON（`units` + `connections`），由 DataY Core 引擎执行

![ETL 画布](../images/ETL画布.png)

### 支持的 ETL 组件

组件继承自 DataY Core，完整参数见 [ETL 组件介绍](components/README.md)：

- **输入组件**：`StreamJdbcInput`、`JdbcInput`、`MySQLBinlogInput`、`PostgresCDCInput`
- **处理组件**：`DuckDBSql`、`StreamSqlUnit`、`Join`、`DataTransform`、`JavaScriptComponent`
- **输出组件**：`StreamJdbcOutput`、`DuckDBWrite`、`DuckLakeWrite`、`DorisStreamLoad`、`ModelWrite`
- **其他组件**：`GenerateFlowFile`、`LogFlowFile`、`HttpListener`

## DAG 工作流

DAG 任务用于编排一组有先后关系的子任务：

- 在任务定义中声明子任务及其依赖关系
- 执行时自动做**拓扑排序**，按依赖顺序调度子任务
- 上游失败时，下游不会被执行

## 任务依赖

除了 DAG 内部的依赖，平台还支持**任务之间**的依赖（JobDepend）：

- 配置父子依赖后，父任务成功后自动触发子任务
- 依赖未满足时，任务实例进入 **WAITING** 状态并轮询恢复
- 适合跨任务、跨类型的编排（例如：ETL 任务 → SQL 任务）

详细的依赖调度行为见 [任务调度与实例](guide/scheduler.md)。

## 任务 JSON（进阶）

画布保存后生成的 JSON 就是 DataY Core 的任务定义，格式为：

```json
{
  "units": [
    { ".id": "c125de36", ".name": "StreamJdbcInput", "table": "orders" },
    { ".id": "a949c10a", ".name": "StreamJdbcOutput", "model": "overwrite" }
  ],
  "connections": [
    { "sourceId": "c125de36", "targetId": "a949c10a" }
  ]
}
```

该 JSON 既可以在平台里执行，也可以交给 DataY Core 独立运行，详见
[数据集成引擎（DataY Core）](introduction/core.md)。

## 下一步

- [任务调度与实例](guide/scheduler.md)：为任务配置定时与依赖调度
- [场景案例](cases/README.md)：查看各类任务的实际配置
