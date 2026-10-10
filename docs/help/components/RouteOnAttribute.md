# RouteOnAttribute 属性路由组件配置文档

## 组件概述

RouteOnAttribute（属性路由）是一个**分流组件**。它根据 FlowFile 中的属性或数据项，与**下游连线上设置的标签**进行匹配，命中的 FlowFile（或记录）自动发送到对应连线，实现"一条数据流按规则自动分叉"。

它只负责路由，不修改数据内容，因此可以放在任意处理组件与输出组件之间。

处理流程：

1. 接收上游 FlowFile；
2. 按配置的匹配模式（属性名 / 属性值 / 数据项）与各下游连线标签逐一比对；
3. 命中的 FlowFile 只发往命中的那条连线；未命中的走兜底标签（默认 `other`）对应的连线；
4. 若兜底连线也不存在，则丢弃并记录告警。

> 与 HashRouter / RandomRouter 的区别：后两者是"把数据平均/随机分发"，无法控制去向；RouteOnAttribute 是"按业务规则精准分流"，去向由连线标签决定。

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `RouteOnAttribute` |
| `matchMode` | String | 否 | `attribute` | 匹配模式：`attribute`（属性名）/ `value`（属性值）/ `data`（数据项） |
| `attributeName` | String | 否 | - | `matchMode=value` 时必填，指定用于取值的属性名 |
| `dataField` | String | 否 | - | `matchMode=data` 时必填，指定记录中用于匹配的字段名 |
| `defaultLabel` | String | 否 | `other` | 未匹配时兜底路由到的连线标签；该连线不存在时丢弃数据 |

## 连线标签

连线的标签在下游连线上设置（DataY Web 设计器中**双击连线**即可编辑），引擎侧对应 `connections` 中的 `label` 字段：

```json
{ "sourceId": "route1", "targetId": "log_vip", "sourcePort": 0, "label": "vip" }
```

- 标签区分大小写，匹配时会对首尾空格做裁剪；
- 多个连线标签相同时，命中第一条；
- **`other` 是约定的兜底标签**：所有未匹配的数据都会尝试走 `other` 连线。

## 三种匹配模式

### 1. 属性名模式（`attribute`，默认）

FlowFile 中**存在与连线标签同名的属性**时命中。

适用场景：上游已经通过属性打好了标记，例如 `_eventType`、自定义标记位等。

```json
{ "matchMode": "attribute" }
```

示例：连线标签为 `vip`，FlowFile 携带属性 `vip=true`，则该 FlowFile 路由到 `vip` 连线。

### 2. 属性值模式（`value`）

取 `attributeName` 指定属性的值，**等于连线标签**时命中。

适用场景：上游把分类值写进了某一个属性，例如 `_eventType=INSERT/UPDATE/DELETE`。

```json
{ "matchMode": "value", "attributeName": "type" }
```

示例：FlowFile 属性 `type=vip`，连线标签 `vip`，路由到该连线。

### 3. 数据项模式（`data`）

逐条读取 FlowFile 记录中 `dataField` 字段的值，**等于连线标签**的记录分流到该连线。
同一个 FlowFile 可以**按记录拆分到多条连线**，每种标签的命中记录会被合并为一个新的 FlowFile 下发（保持原属性不变，便于下游继续处理）。

适用场景：上游是包含业务分类字段的结构化数据，需要按字段值分表、分库、分通道输出。

```json
{ "matchMode": "data", "dataField": "type" }
```

示例：FlowFile 数据为

```json
[
  { "id": 1, "type": "vip" },
  { "id": 2, "type": "normal" },
  { "id": 3, "type": "vip" },
  { "id": 4, "type": "unknown" }
]
```

连线标签有 `vip`、`normal`、`other`，则输出：

| 连线标签 | 收到的数据 |
|----------|------------|
| `vip` | `[{"id":1,"type":"vip"},{"id":3,"type":"vip"}]` |
| `normal` | `[{"id":2,"type":"normal"}]` |
| `other` | `[{"id":4,"type":"unknown"}]`（unknown 未命中，走兜底） |

> 数据项模式目前支持 JSON 数组（`JSON_ARRAY`）与 JSON 对象（`JSON_OBJECT`）；其它格式原样忽略并记录告警。

## 兜底规则

- 未命中任何连线时，会尝试路由到 `defaultLabel`（默认 `other`）对应的连线；
- 若 `other` 连线存在，则未匹配数据全部进入该连线；
- 若 `other` 连线不存在，则未匹配数据被丢弃，并在日志中记录告警条数。

## 配置示例

按数据项（`type`）分流，未匹配走 `other`：

```json
{
  ".id": "route1",
  ".name": "RouteOnAttribute",
  "matchMode": "data",
  "dataField": "type",
  "defaultLabel": "other"
}
```

## 完整任务配置示例

从生成测试数据 → 按数据项分流 → 分别记录日志：

```json
{
  "units": [
    {
      ".id": "gen1",
      ".name": "GenerateFlowFile",
      "inputData": "[{\"id\":1,\"type\":\"vip\"},{\"id\":2,\"type\":\"normal\"},{\"id\":3,\"type\":\"vip\"},{\"id\":4,\"type\":\"unknown\"}]",
      "dataFormat": "JSON_ARRAY",
      "loopCount": "1"
    },
    {
      ".id": "route1",
      ".name": "RouteOnAttribute",
      "matchMode": "data",
      "dataField": "type",
      "defaultLabel": "other"
    },
    { ".id": "log_vip", ".name": "LogFlowFile", "logDataContent": "true" },
    { ".id": "log_normal", ".name": "LogFlowFile", "logDataContent": "true" },
    { ".id": "log_other", ".name": "LogFlowFile", "logDataContent": "true" }
  ],
  "connections": [
    { "sourceId": "gen1", "targetId": "route1", "sourcePort": 0 },
    { "sourceId": "route1", "targetId": "log_vip", "sourcePort": 0, "label": "vip" },
    { "sourceId": "route1", "targetId": "log_normal", "sourcePort": 0, "label": "normal" },
    { "sourceId": "route1", "targetId": "log_other", "sourcePort": 0, "label": "other" }
  ],
  "version": "1.0.0"
}
```

运行日志（节选）：

```
[LogFlowFile-log_vip]    ... 数据内容: [{"id":1,"type":"vip"},{"id":3,"type":"vip"}]
[LogFlowFile-log_normal] ... 数据内容: [{"id":2,"type":"normal"}]
[LogFlowFile-log_other]  ... 数据内容: [{"id":4,"type":"unknown"}]
```

## DataY Web 使用说明

1. 在任务设计器中拖入「属性路由」组件；
2. 双击组件打开配置，选择匹配模式；`按属性值` 填写属性名，`按数据项` 填写匹配字段名；
3. 保留默认兜底标签 `other`（也可自定义为其它标签）；
4. **双击下游连线**设置标签（如 `vip`、`normal`、`other`），保存任务即可。

> 调试技巧：配置弹窗的「调试」页可以先运行上游，查看采样数据的字段名，再回到「配置」页填写匹配字段，避免字段名写错。

## 注意事项

- 路由组件只投递到命中的连线，未配置标签的连线不会收到数据（除非它正好是兜底标签）；
- 数据项模式会按标签把记录聚合成新的 FlowFile，**记录顺序按其在原 FlowFile 中出现的顺序保留**；
- 结束后，引擎仍会向所有下游广播结束信号，因此未命中数据的下游分支也能正常结束，不会卡住任务；
- 属性名/属性值模式作用于整个 FlowFile，不做记录级拆分；若要按记录拆分请使用数据项模式。
