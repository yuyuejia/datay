# EvaluateJsonPath JSONPath提取组件配置文档

## 组件概述

EvaluateJsonPath（JSONPath提取）是一个**数据处理组件**。它使用单个 JSONPath 表达式从上游 FlowFile 的 JSON 数据中提取子结构，并用提取结果替换数据内容后发送给下游。

求值方式按上游数据类型自动区分：

| 上游数据格式 | 求值方式 |
|--------------|----------|
| `JSON_ARRAY` | **逐行求值**：每条记录单独执行一次 JSONPath |
| `JSON_OBJECT` | **直接求值**：对整个 JSON 对象执行一次 JSONPath |
| `TEXT` | 先尝试解析为 JSON，再按解析结果走上面两种方式；解析失败则跳过 |
| `CSV` / `BINARY` / 空数据 | 不支持，跳过并告警 |

处理流程：

1. 接收上游 FlowFile；
2. 按数据类型确定求值方式（数组逐行 / 对象直接）；
3. 执行 JSONPath 提取；
4. 用提取结果替换数据内容，并重建表元数据；
5. 发送给下游；未命中的 FlowFile（或数组中的行）跳过并记录告警。

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `EvaluateJsonPath` |
| `jsonPath` | String | 是 | - | JSONPath 表达式，如 `$.data.items[*]`；不以 `$` 开头时自动补全为 `$.` 前缀 |
| `valueField` | String | 否 | `value` | 提取结果为标量时，包装成 `{valueField: 值}` 使用的字段名；数组结果直接作为数据内容 |

## 求值范围与输出结构

### JSON_ARRAY：逐行求值

输入是 JSON 数组时，对数组中的**每一条记录**分别执行 JSONPath，每行产生一条输出记录：

| 单行提取结果 | 输出记录 |
|--------------|----------|
| `JSONObject` | 直接作为该条输出记录 |
| `JSONArray` | **展平后作为数据内容合并输出**（数组元素依次追加到最终输出数组） |
| 标量（字符串/数字/布尔） | 包装为 `{ "<valueField>": 值 }` |
| `null`（未命中） | 跳过该行，其余行正常输出 |

> 数组类型的提取结果会**展平合并**到最终输出数组中，直接作为 FlowFile 的数据内容；如果数组元素是对象，它们会直接成为下游记录。

示例：输入

```json
[
  { "id": 1, "data": { "name": "a" } },
  { "id": 2, "data": { "name": "b" } }
]
```

配置 `"jsonPath": "$.data"`，输出（对象直接展开）：

```json
[
  { "name": "a" },
  { "name": "b" }
]
```

配置 `"jsonPath": "$.data.name"`，输出（标量包装，默认 `valueField=value`）：

```json
[
  { "value": "a" },
  { "value": "b" }
]
```

配置 `"jsonPath": "$.data.tags"`，输入每行的 `tags` 为数组：

```json
[
  { "data": { "tags": ["x", "y"] } },
  { "data": { "tags": ["z"] } }
]
```

输出（数组展平合并为数据内容）：

```json
["x", "y", "z"]
```

### JSON_OBJECT：直接求值

输入是 JSON 对象时，对整个对象执行一次 JSONPath：

| 提取结果 | 输出 |
|----------|------|
| `JSONArray` | 直接作为下游 JSON 数组数据 |
| `JSONObject` | 直接作为下游 JSON 对象数据 |
| 标量 | 包装为单条记录 `[{ "<valueField>": 值 }]` |
| `null`（未命中） | 跳过该 FlowFile，不下发 |

示例：输入

```json
{ "data": { "items": [ { "id": 1 }, { "id": 2 } ] } }
```

配置 `"jsonPath": "$.data.items"`，输出：

```json
[
  { "id": 1 },
  { "id": 2 }
]
```

## 未命中处理

- 数组逐行求值时，未命中的行会被跳过，命中行照常输出，日志记录跳过行数；
- 所有行都未命中时，整个 FlowFile 不下发；
- JSON 对象求值未命中时，整个 FlowFile 不下发；
- 未配置 `jsonPath`、数据为空或格式不支持时，同样跳过并告警。

## 配置示例

```json
{
  ".id": "eval1",
  ".name": "EvaluateJsonPath",
  "jsonPath": "$.data.items[*]",
  "valueField": "value"
}
```

## 完整任务配置示例

从生成测试数据 → 逐行提取 `data` 对象 → 记录输出：

```json
{
  "units": [
    {
      ".id": "gen1",
      ".name": "GenerateFlowFile",
      "inputData": "[{\"id\":1,\"data\":{\"name\":\"a\"}},{\"id\":2,\"data\":{\"name\":\"b\"}}]",
      "dataFormat": "JSON_ARRAY",
      "loopCount": "1"
    },
    {
      ".id": "eval1",
      ".name": "EvaluateJsonPath",
      "jsonPath": "$.data"
    },
    {
      ".id": "log1",
      ".name": "LogFlowFile",
      "logDataContent": "true"
    }
  ],
  "connections": [
    { "sourceId": "gen1", "targetId": "eval1", "sourcePort": 0 },
    { "sourceId": "eval1", "targetId": "log1", "sourcePort": 0 }
  ],
  "version": "1.0.0"
}
```

运行日志（节选）：

```
[EvaluateJsonPath-eval1] - [INFO] EvaluateJsonPath: 逐行提取完成，输出 2 条记录，跳过 0 行
[LogFlowFile-log1]       ... 数据内容: [{"name":"a"},{"name":"b"}]
```

## DataY Web 使用说明

1. 在任务设计器中拖入「JSONPath提取」组件；
2. 双击组件打开配置，填写 JSONPath 表达式（如 `$.data.items[*]`）；
3. 如果提取结果是标量，可按需修改「包装字段名」，默认 `value`；数组结果会直接作为数据内容；
4. 调试时可先用上游组件运行采样，确认字段路径后再保存任务。

## 注意事项

- 一个组件只支持**一个** JSONPath 表达式；需要提取多个字段时，串联多个 EvaluateJsonPath 组件；
- 数组输入只支持逐行求值；如果 JSONPath 需要以整个数组为根（如 `$[0].id`），请在上游先把数组包装成 JSON 对象；
- 提取后会按新数据重建 `_tableMetadata`（保留原表名与 schema），避免下游按旧结构建表；
- 提取结果为空数组不算未命中，会正常下发空数组，由下游处理。
