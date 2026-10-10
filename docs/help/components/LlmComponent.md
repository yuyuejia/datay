# LlmComponent 大模型组件配置文档

## 组件概述

LlmComponent 是一个按行调用大模型的组件，兼容 OpenAI Chat Completions 协议，可对接 OpenAI / DeepSeek / 通义千问 / Ollama 等服务。

模型调用复用 DataY Core 的共享客户端 `com.data.ai.llm.OpenAiCompatibleClient`，与 DataY Web 的 AI 助手使用同一套实现（支持 function calling），组件可通过覆写 `resolveTools()` 注册工具，为后续工具调用预留扩展点。

处理流程：

1. 接收上游 FlowFile（JSON 数组），逐行处理；
2. 使用系统提示词与用户输入构造对话请求，用户输入中的动态参数按当前行数据字段赋值；
3. 每行独立调用大模型接口，并将结果合并回该行：
   - 模型返回 JSON 对象：将其属性扩充到该行数据；
   - 模型返回文本：写入 `llm_result` 字段。

处理后的数据以 FlowFile 形式发送给下游，并同步刷新表元数据，便于下游组件自动建表。

## 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为 `LlmComponent` |
| `baseUrl` | String | 是 | `https://api.openai.com/v1` | OpenAI 兼容 API 根地址，需包含版本段；也支持直接填写完整的 `/chat/completions` 地址 |
| `apiKey` | String | 是 | - | API Key，请求时以 `Authorization: Bearer <apiKey>` 发送 |
| `model` | String | 是 | `gpt-4o-mini` | 模型名称，如 `deepseek-chat` / `qwen-plus` |
| `temperature` | Double | 否 | `0.2` | 采样温度 |
| `timeoutSeconds` | Integer | 否 | `120` | 单次请求超时时间（秒） |
| `retryCount` | Integer | 否 | `1` | 请求失败重试次数（含首次） |
| `retryIntervalMillis` | Integer | 否 | `1000` | 重试间隔（毫秒），仅对超时等可重试错误生效 |
| `debugSampleRows` | Integer | 否 | `3` | 调试模式下仅处理的前 N 行，避免逐行调用导致调试超时；正式运行处理全部数据 |
| `systemPrompt` | String | 否 | - | 系统提示词，支持动态参数 |
| `userPrompt` | String | 是 | - | 用户输入，支持动态参数；为空时使用当前行 JSON 文本 |

## 动态参数

`systemPrompt` 与 `userPrompt` 均支持动态参数，运行时自动替换：

| 写法 | 说明 |
|------|------|
| `${字段名}` | 优先取当前行数据字段，取不到时回退到 FlowFile 属性 |
| `${attr:属性名}` | 取上游 FlowFile 属性值 |
| `${FLOW_FILE_DATA}` / `${ROW_DATA}` | 当前行的完整 JSON（按行处理时指向当前行，而非整个数据集） |
| `#{...}` | 引用内置参数，如 `#{UUID}`、`#{TIMESTAMP}`、`#{NOW - 1d, yyyy-MM-dd}` |

> 可引用的行字段取决于上游数据。可在节点配置弹窗的「调试」页查看上一次运行输出的采样字段。

## 结果合并规则

| 模型返回内容 | 处理方式 |
|--------------|----------|
| JSON 对象（如 `{"sentiment":"positive","score":0.9}`） | 解析出的属性逐个合并到当前行；已存在的字段会被覆盖 |
| ```` ```json ... ``` ```` 代码块包裹的 JSON 对象 | 自动去除代码块标记后按 JSON 对象合并 |
| 其它文本 | 写入 `llm_result` 字段 |
| JSON 数组或解析失败的类 JSON 文本 | 按文本处理，写入 `llm_result` 字段 |

## 配置示例

```json
{
  ".id": "llm_sentiment",
  ".name": "LlmComponent",
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-xxxxxx",
  "model": "deepseek-chat",
  "temperature": 0.2,
  "timeoutSeconds": 60,
  "retryCount": 2,
  "systemPrompt": "你是一个文本分析助手，请始终返回 JSON 对象，字段为 sentiment（positive/negative/neutral）和 score（0~1）。",
  "userPrompt": "请分析下面这条评论的情感倾向：${content}"
}
```

假设上游行数据为：

```json
{ "id": 1, "content": "这个产品非常好用，强烈推荐！" }
```

调用后该行变为：

```json
{ "id": 1, "content": "这个产品非常好用，强烈推荐！", "sentiment": "positive", "score": 0.98 }
```

若模型返回纯文本（例如 `正面情感`），则该行变为：

```json
{ "id": 1, "content": "这个产品非常好用，强烈推荐！", "llm_result": "正面情感" }
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
      "table": "comments"
    },
    {
      ".id": "llm_sentiment",
      ".name": "LlmComponent",
      "baseUrl": "https://api.deepseek.com/v1",
      "apiKey": "sk-xxxxxx",
      "model": "deepseek-chat",
      "systemPrompt": "你是一个文本分析助手，请始终返回 JSON 对象，字段为 sentiment 和 score。",
      "userPrompt": "请分析下面这条评论的情感倾向：${content}"
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
      "table": "comments_sentiment",
      "model": "append"
    }
  ],
  "connections": [
    { "sourceId": "jdbc_input", "targetId": "llm_sentiment" },
    { "sourceId": "llm_sentiment", "targetId": "mysql_output" }
  ]
}
```

## DataY Web 使用说明

在 DataY Web 的任务设计器中，「大模型」组件只需配置**系统提示词**与**用户输入**。
接口地址、API Key、模型、温度、超时等连接参数由后端读取**租户级服务配置**（「服务配置 · 大模型」，落库于 `dp_service_config` 的 `llm` 分组）后在生成任务定义时自动注入，
任务配置中不会保存密钥。配置按「租户级覆盖、系统级兜底」两级解析：租户未配置的项自动回退到系统级配置。

> 系统级配置来自 `datay.ai.*`（环境变量 / 配置文件）：

```yaml
datay:
  ai:
    enabled: true
    base-url: https://api.deepseek.com
    api-key: sk-xxxxxx
    model: deepseek-chat
    temperature: 0.2
    timeout-seconds: 120
```

设计器节点翻译逻辑见 `com.data.datafusion.service.etl.LlmNodeTranslator`：
- 未配置大模型 API Key（服务配置与 `datay.ai.api-key` 均为空）时拒绝生成任务定义；
- 未填写用户输入时拒绝生成任务定义；
- 生成的单元使用后端 `LlmComponent`，并注入服务配置中的连接参数。

## 运行日志

每次调用大模型都会记录日志，便于排查与计量：

```
[INFO] 调用大模型：行=1，第 1/1 次，model=deepseek-flash，systemPrompt=...，userPrompt=...
[INFO] 大模型返回：行=1，第 1/1 次，耗时 1135 ms，promptTokens=53，completionTokens=91，totalTokens=144，内容=正面
```

- 调用前记录行号、重试次数、模型、系统提示词与用户输入（均按 300 字符截断）。
- 返回后记录行号、耗时、token 用量与响应内容（按 300 字符截断）。
- 失败与重试记录异常链；请求头中的 API Key 不会写入日志。

## 注意事项

- 组件按行调用模型，行数较多时请关注接口限流与调用成本；可通过上游组件先过滤数据。
- 调试模式下同样会真实调用大模型接口，默认只处理前 `debugSampleRows`（3）行，避免逐行调用触发 DataY Web 的调试整体超时（默认 60 秒）。
- 任务被取消或调试超时时，组件会抛出「大模型调用被中断（任务已取消或调试超时）」，而不再重试。
- 若上游 FlowFile 不是 JSON 数组（文本、二进制等），组件原样透传，不做处理。
