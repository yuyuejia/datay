# DecisionComponent 决策模型组件配置文档

## 组件概述

DecisionComponent 是一个按行调用**决策模型**的组件，对接 Ollama 的 Jev-style 决策接口
（`POST /v1/systemone`，自 Ollama 0.35 起提供）。发送一段 `state` 与一组命名问题，
模型在**一次请求**内对全部问题作答，适合工单分派、模型路由、内容风控等需要快速决策的场景。

接口调用复用 DataY Core 的共享客户端 `com.data.ai.llm.SystemOneClient`（与 `LlmComponent`
同属 `com.data.ai.llm` 包，风格一致）。

处理流程：

1. 接收上游 FlowFile（JSON 数组），逐行处理；
2. 以当前行构造 `state`，对配置的 `questions` 发起一次决策请求；
3. 把 `answers` 展平合并回该行，并将结果发送给下游，同时刷新表元数据，便于下游自动建表。

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `DecisionComponent` |
| `baseUrl` | String | 否 | `http://localhost:11434/v1` | Ollama 服务根地址，需包含版本段；也支持直接填写完整的 `/v1/systemone` 地址 |
| `apiKey` | String | 否 | `ollama` | API Key，请求时以 `Authorization: Bearer <apiKey>` 发送（Ollama 本地服务通常忽略） |
| `model` | String | 否 | `nimble` | 决策模型名，如 `nimble` / `tev1` / `tev1:0.8b` |
| `timeoutSeconds` | Integer | 否 | `120` | 单次请求超时时间（秒） |
| `retryCount` | Integer | 否 | `1` | 请求失败重试次数（含首次） |
| `retryIntervalMillis` | Integer | 否 | `1000` | 重试间隔（毫秒），仅对超时等可重试错误生效 |
| `debugSampleRows` | Integer | 否 | `3` | 调试模式下仅处理的前 N 行，避免逐行调用导致调试超时；正式运行处理全部数据 |
| `questions` | String(JSON) | 是 | - | 问题定义，键为问题名，值为 `{type, instructions, criteria}` |
| `state` | String(JSON) | 否 | - | state 模板，支持动态参数；为空时按 `stateFields` / 整行构造 |
| `stateFields` | String | 否 | - | state 字段子集（逗号分隔），仅在 `state` 模板为空时生效 |
| `answerPrefix` | String | 否 | - | 合并到行数据时的字段前缀 |
| `includeConfidence` | Boolean | 否 | `true` | 是否为 choice/score 合并 `_confidence` 字段 |
| `includeProbabilities` | Boolean | 否 | `false` | 是否合并 `_probabilities`（score 另含 `_legend`），以 JSON 字符串写入 |
| `resultField` | String | 否 | - | 将完整的 `answers` JSON 写入该字段 |

## 问题类型（questions）

`questions` 是一个 JSON 对象，每个键为问题名，值为：

```json
{
  "team": {
    "type": "choice",
    "instructions": "Which team should handle this ticket?",
    "criteria": { "billing": "Payments and refunds", "technical": "Bugs and integrations", "other": "None of the above" }
  },
  "refund": {
    "type": "noul",
    "instructions": "Does the customer explicitly ask for a refund?"
  },
  "urgency": {
    "type": "score",
    "instructions": "How urgent is this ticket?",
    "criteria": ["Routine", "Soon", "Urgent"]
  }
}
```

| type | 含义 | criteria | 回答字段 |
|------|------|----------|----------|
| `choice` | 从若干选项中选一个 | 对象：`选项值 -> 说明` | `choice`、`probabilities`、`confidence` |
| `noul` | 是 / 否概率（0~1） | 无 | `noul` |
| `score` | 打分（0~1） | 数组：有序档位 | `score`、`legend`、`probabilities`、`confidence` |

## state 取值

`state` 是模型的输入，按以下优先级构造：

| 优先级 | 配置 | 说明 |
|--------|------|------|
| 1 | `state` 模板 | 按模板构造，支持动态参数；模板本身为合法 JSON 时直接解析，否则包装为 `{"state": 文本}` |
| 2 | `stateFields` | 取当前行的指定字段（逗号分隔）组成 `state` |
| 3 | - | 使用当前行整行数据 |

动态参数（`state` 模板内）：

| 写法 | 说明 |
|------|------|
| `${字段名}` | 优先取当前行数据字段，取不到时回退到 FlowFile 属性 |
| `${attr:属性名}` | 取上游 FlowFile 属性值 |
| `${FLOW_FILE_DATA}` / `${ROW_DATA}` | 当前行的完整 JSON |
| `#{...}` | 引用内置参数，如 `#{UUID}`、`#{TIMESTAMP}` |

## 答案合并规则

对 `answers` 中每个问题名 `q`（`answerPrefix` 为前缀，默认空）：

| 回答类型 | 合并字段 |
|----------|----------|
| `choice` | `q`=选项值；`q_confidence`（`includeConfidence=true`）；`q_probabilities`（`includeProbabilities=true`） |
| `noul` | `q`=0~1 概率 |
| `score` | `q`=分值；`q_confidence`；`q_probabilities`；`q_legend`（`includeProbabilities=true`） |
| 其它 / 未识别 | `q`=该回答的 JSON 字符串 |

`resultField` 非空时，额外把完整的 `answers` JSON 写入该字段。

> `probabilities` / `legend` 以 JSON 字符串写入，作为稳定的文本列，便于下游自动建表。

## 配置示例

```json
{
  ".id": "ticket_decision",
  ".name": "DecisionComponent",
  "baseUrl": "http://localhost:11434/v1",
  "apiKey": "ollama",
  "model": "nimble",
  "timeoutSeconds": 120,
  "answerPrefix": "decision_",
  "includeConfidence": true,
  "includeProbabilities": false,
  "stateFields": "ticket",
  "questions": "{\"team\":{\"type\":\"choice\",\"instructions\":\"Which team should handle this ticket?\",\"criteria\":{\"billing\":\"Payments and refunds\",\"technical\":\"Bugs and integrations\",\"other\":\"None of the above\"}},\"refund\":{\"type\":\"noul\",\"instructions\":\"Does the customer explicitly ask for a refund?\"},\"urgency\":{\"type\":\"score\",\"instructions\":\"How urgent is this ticket?\",\"criteria\":[\"Routine\",\"Soon\",\"Urgent\"]}}"
}
```

假设上游行数据为：

```json
{ "ticket": "I was charged twice. Please refund the extra payment." }
```

调用后该行变为：

```json
{
  "ticket": "I was charged twice. Please refund the extra payment.",
  "decision_team": "billing",
  "decision_team_confidence": 0.922,
  "decision_refund": 0.997,
  "decision_urgency": 0.815,
  "decision_urgency_confidence": 0.046
}
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
      "table": "tickets"
    },
    {
      ".id": "ticket_decision",
      ".name": "DecisionComponent",
      "model": "nimble",
      "stateFields": "ticket",
      "answerPrefix": "decision_",
      "questions": "{\"team\":{\"type\":\"choice\",\"instructions\":\"Which team should handle this ticket?\",\"criteria\":{\"billing\":\"Payments and refunds\",\"technical\":\"Bugs and integrations\",\"other\":\"None of the above\"}},\"refund\":{\"type\":\"noul\",\"instructions\":\"Does the customer explicitly ask for a refund?\"},\"urgency\":{\"type\":\"score\",\"instructions\":\"How urgent is this ticket?\",\"criteria\":[\"Routine\",\"Soon\",\"Urgent\"]}}"
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
      "table": "tickets_triage",
      "model": "append"
    }
  ],
  "connections": [
    { "sourceId": "jdbc_input", "targetId": "ticket_decision" },
    { "sourceId": "ticket_decision", "targetId": "mysql_output" }
  ]
}
```

## 对接 Ollama

```bash
# 1. 安装 / 升级 Ollama 到 0.35 及以上
# 2. 拉取决策模型
ollama pull nimble

# 3. 验证端点
curl http://localhost:11434/v1/systemone -d '{
  "model": "nimble",
  "state": { "ticket": "I was charged twice. Please refund the extra payment." },
  "questions": {
    "refund": { "type": "noul", "instructions": "Does the customer explicitly ask for a refund?" }
  }
}'
```

## DataY Web 使用说明

在 DataY Web 的任务设计器中，「决策模型」组件只需配置**问题定义**（questions）与可选的
state 取值方式、字段前缀、合并开关。接口地址、API Key、模型、超时等连接参数由后端读取
**租户级服务配置**（「服务配置 · 决策模型」，落库于 `dp_service_config` 的 `decision` 分组）
后在生成任务定义时自动注入，任务配置中不会保存密钥。配置按「租户级覆盖、系统级兜底」
两级解析：租户未配置的项自动回退到系统级配置。

> 系统级配置来自 `datay.decision.*`（环境变量 / 配置文件）：

```yaml
datay:
  decision:
    enabled: true
    base-url: http://localhost:11434/v1
    api-key: ollama
    model: nimble
    timeout-seconds: 120
```

设计器节点翻译逻辑见 `com.data.datafusion.service.etl.DecisionNodeTranslator`：
- 未配置决策模型服务时拒绝生成任务定义；
- 未填写问题定义时拒绝生成任务定义；
- 生成的单元使用后端 `DecisionComponent`，并注入服务配置中的连接参数。

## 运行日志

每次调用决策模型都会记录日志，便于排查与计量：

```
[INFO] 调用决策模型：行=1，第 1/1 次，model=nimble，问题=[team, refund, urgency]，state={"ticket":"I was charged twice..."}
[INFO] 决策模型返回：行=1，第 1/1 次，耗时 91 ms，inputTokens=841，outputTokens=4，answers={...}
```

- 调用前记录行号、重试次数、模型、问题名与 state（均按 300 字符截断）。
- 返回后记录行号、耗时、token 用量与 answers（按 300 字符截断）。
- 失败与重试记录异常链；请求头中的 API Key 不会写入日志。

## 注意事项

- 组件按行调用模型，行数较多时请关注本地推理资源；决策模型单次耗时通常在百毫秒级，建议批量场景评估吞吐。
- 调试模式下同样会真实调用决策模型，默认只处理前 `debugSampleRows`（3）行，避免触发 DataY Web 的调试整体超时（默认 60 秒）。
- 任务被取消或调试超时时，组件会抛出「决策模型调用被中断（任务已取消或调试超时）」，而不再重试。
- 若上游 FlowFile 不是 JSON 数组（文本、二进制等），组件原样透传，不做处理。
