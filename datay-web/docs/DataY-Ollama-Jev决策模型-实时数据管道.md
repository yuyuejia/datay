# DataY × Ollama × Jev 决策模型：把"决定"装进实时数据管道

> 过去我们让大模型"写一段话"，现在我们要让模型"给一个决定"。
>
> 而且这个决定，最好在**数据还没有离开你的机器**的时候就做出来。
>
> 这篇文章讲清楚三件事：**决策模型**是什么、为什么它值得**本地跑**、以及 DataY 如何把它变成**实时数据管道里的一个算子**。

---

## 一、有些事，不需要"长篇大论"，只需要一个"决定"

先看几个每天都在发生的场景。

**工单分派**：一条新工单进来，到底是退款问题、技术故障，还是物流投诉？该派给谁？这位用户是不是在明确要求退款？有多急？

**内容风控**：这条评论该放过、该折叠，还是该拦截？

**模型路由**：这个请求该走便宜的小模型，还是贵的大模型？

它们的共同点是：

> **你要的不是一段文字，而是一个"标签"、一个"概率"、一个"分数"——一个可以被程序直接消费的决定。**

过去，我们用生成式大模型来做这件事：写一段提示词，让它"输出 JSON"。能跑，但总有点别扭——你要的是**决定**，它给你的却是**一段作文**，还得靠正则和容错去猜、去解析。

更别扭的是成本与延迟：让一个几十上百亿参数的模型，为了"选一个选项"而噼里啪啦生成几十个 token，还要把数据发到云端、再等结果回来。**对一条每秒都在流动的数据管道来说，这太慢了。**

于是有了一个更专注的物种：**决策模型（Decision Model）**。

---

## 二、决策模型：不是"生成文本"，而是"给出决定"

Ollama 从 0.35 起，通过一个全新的端点 `/v1/systemone` 原生支持 **Jev-style 决策模型**。它的工作方式和聊天模型完全不同：

- 你给它一段 `state`（一段状态 / 一条数据）；
- 再给它一组**命名问题**（`questions`）；
- 它在**一次请求**里，把这组问题**全部答完**；
- 每道题的回答是**强类型**的：选项、概率、置信度——而不是自由文本。

拿一条工单举例，一个请求就问完三件事：

```bash
curl http://localhost:11434/v1/systemone -d '{
  "model": "nimble",
  "state": {
    "ticket": "I was charged twice. Please refund the extra payment."
  },
  "questions": {
    "team": {
      "type": "choice",
      "instructions": "Which team should handle this ticket?",
      "criteria": {
        "billing":  "Payments and refunds",
        "technical":"Bugs and integrations",
        "other":    "None of the above"
      }
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
}'
```

返回的是**结构化的决定**，还附带概率与置信度：

```json
{
  "model": "nimble",
  "answers": {
    "team":    { "type": "choice", "choice": "billing",
                 "probabilities": {"billing": 0.985, "technical": 0.012, "other": 0.003},
                 "confidence": 0.922 },
    "refund":  { "type": "noul", "noul": 0.997 },
    "urgency": { "type": "score", "score": 0.815,
                 "legend": {"0": "Routine", "1": "Soon", "2": "Urgent"},
                 "probabilities": {"0": 0.378, "1": 0.429, "2": 0.193},
                 "confidence": 0.046 }
  },
  "usage": { "input_tokens": 841, "output_tokens": 4 }
}
```

请注意几个"生成式模型给不了"的东西：

| 特性 | 说明 |
| --- | --- |
| **一次请求，多个决定** | 一个请求里把 `team` / `refund` / `urgency` 全答完，不用来回三次 |
| **强类型回答** | `choice` 给你选项和概率，`noul` 给你 0~1 的是否置信，`score` 给你分值和档位 |
| **自带置信度** | 每个决定都带 `confidence`，**低置信度的可以自动转人工** |
| **输出极短** | `output_tokens` 只有个位数——它不"啰嗦"，它只"下结论" |

**这就是决策模型和聊天模型最本质的区别：前者产出一个"可编程的决定"，后者产出一段"需要再加工的文本"。**

---

## 三、为什么这件事，一定要"本地化"

决策模型可能是**最适合本地跑**的一类模型。

原因很朴素：**决定要快，数据要稳。**

**① 没有网络往返，延迟天然更低。**
聊天模型的调用要经过"请求 → 云端排队 → 生成 → 返回"，一轮秒级起步。决策模型在本地推理，Ollama 官方在 M5 Max 上实测 `nimble` 平均 **91ms / 次决策**——这个量级，已经足够"一边流动一边判断"。

**② 数据不出机器。**
工单里是用户的投诉原文，会话里有订单与账号，日志里可能还有敏感信息。**把这些数据发到外部云服务，本身就是一道合规与审计的门槛。**
本地决策模型让"判断"发生在你自己的机器上——**数据外发为零。**

**③ 零调用成本、可离线。**
本地推理不按 token 计费，不存在"流量一大就烧钱"。内网、离线环境、数据隔离区里都能跑。

**④ 一条命令就能开始。**

```bash
ollama pull nimble      # 9B 决策模型（Bespoke Labs）
# 或更轻量
ollama pull tev1:0.8b   # 0.8B 实验性决策模型（Together AI）
```

**模型本地化 + 管道化，是决策这件事最自然的归宿。**

---

## 四、DataY 把决策模型做成管道里的一个算子

光有模型还不够。真正的问题是：**这个"决定"怎么进到数据流里？**

大多数团队的第一反应是搭一个旁路服务：

```
业务库 / Kafka ──导出──▶ 独立的决策服务 ──结果──▶ 再导回业务库
```

**这个架构在生产里几乎一定会失控**——多一套服务要部署监控、链路断成两截、重试幂等背压全部重写、延迟被层层叠加。

DataY 的做法只有一句话：

> **把决策模型做成数据流里的一个算子，和过滤、Join、SQL 算子同级。**

DataY Core 新增了 **`DecisionComponent`（画布上叫「决策模型」）**。它的行为同样一句话说得清：

> **输入一行 JSON，构造 `state`，对一组 `questions` 发起一次决策，把答案展平合并进这一行。**

答案会按固定规则**展平成列**，下游可以自动建表：

| 问题类型 | 合并出来的字段 |
| --- | --- |
| `choice` | `<问题名>`=选项值；可选 `<问题名>_confidence`、`<问题名>_probabilities` |
| `noul` | `<问题名>`=0~1 概率 |
| `score` | `<问题名>`=分值；可选 `_confidence`、`_probabilities`、`_legend` |

于是同一行数据加上了 `team`、`team_confidence`、`refund`、`urgency` 等字段——**这些是数仓里可聚合、可路由、可编程的结构化信号。**

组件的配置也很干净，核心就是一份 `questions`：

```json
{
  ".id": "ticket_decision",
  ".name": "DecisionComponent",
  "model": "nimble",
  "stateFields": "content",
  "answerPrefix": "decision_",
  "includeConfidence": true,
  "questions": "{\"category\":{\"type\":\"choice\",\"instructions\":\"Which team should handle this ticket?\",\"criteria\":{\"refund\":\"退款与扣款\",\"tech\":\"技术故障\",\"logistics\":\"物流问题\",\"account\":\"账号安全\",\"other\":\"其他\"}},\"refund\":{\"type\":\"noul\",\"instructions\":\"Does the customer explicitly ask for a refund?\"},\"urgency\":{\"type\":\"score\",\"instructions\":\"How urgent is this ticket?\",\"criteria\":[\"低\",\"中\",\"高\"]}}"
}
```

和平台其它 AI 能力一样，**接口地址、API Key、模型、超时由「服务配置 · 决策模型」统一注入**（系统级默认在 `datay.decision.*`），任务定义里不落密钥。

---

## 五、真实实测：本地 `tev1:0.8b` 跑 10 条工单

空谈不如跑一把。我们在本地 Ollama（0.40.1，`tev1:0.8b`）上，让 `DecisionComponent` 处理 10 条真实语气的电商客服工单，问题为 `category`（choice）+ `refund`（noul）+ `urgency`（score）。

**结果（真实运行，未加工）：**

| 工单 | category | refund 概率 | urgency |
| --- | --- | --- | --- |
| 订单扣了两次钱，请把多扣的那笔退给我，急！ | **refund** | 0.82 | 1.88 |
| 登录一直失败，验证码收不到 | **tech** | 0.003 | 1.68 |
| 下单三天了还没发货，什么时候能发？ | **logistics** | 0.012 | 1.79 |
| 收到的空气炸锅外壳有裂痕，申请退货退款 | **refund** | 0.61 | 1.11 |
| 请问怎么开发票？公司报销要用 | **other** | 0.048 | 1.05 |
| 账号好像被盗了，有人用我的账号下单，赶紧冻结 | **account** | 0.013 | 1.70 |
| 会员权益里说的免费运费险怎么用？ | **logistics** | 0.050 | 1.04 |
| 优惠券显示已过期，但明明是今天领的 | **other** | 0.031 | 1.04 |
| 快递显示签收但我根本没收到，东西丢了谁负责 | **logistics** | 0.015 | 1.43 |
| App 更新后在设置页闪退，安卓手机 | **tech** | 0.008 | 1.37 |

**关键指标：**

- **10 条工单，总耗时 1752ms，平均 175ms / 条**（含本地推理与 HTTP 往返）；
- 每条输入的 `input_tokens` 约 400，`output_tokens` 为 0（决策模型不"生成文本"）；
- "重复扣款"被同时判成 `refund=0.82` 且 `category=refund`，并给出 `category_confidence=0.987`。

一个行内同时拿到三个决定，还带置信度和完整概率分布：

```json
{
  "ticket_id": "T001",
  "content": "订单扣了两次钱，请把多扣的那笔退给我，急！",
  "category": "refund",
  "category_confidence": 0.987,
  "category_probabilities": "{\"refund\":0.997,\"tech\":0.0003,\"logistics\":0.0001,\"account\":0.0004,\"other\":0.0019}",
  "refund": 0.819,
  "urgency": 1.88,
  "urgency_confidence": 0.658,
  "urgency_legend": "{\"0\":\"低\",\"1\":\"中\",\"2\":\"高\"}"
}
```

> 对比一下：生成式 `LlmComponent` 单条调用通常在 **0.5~1.2 秒**；决策模型是 **百毫秒级**。
> 差的不是一点速度，而是**"能不能实时"**这道分水岭。

---

## 六、把它拼成一条实时管道

决策算子的价值，要放进完整链路里才体现得出来。一条典型的**工单实时分派管道**：

```
        工单系统 / 客服会话（实时产生）
                    │
                    ▼   MySQL Binlog / Kafka（秒级入流）
        ┌────────────────────────────────────────────┐
        │              实时处理流水线                    │
        │                                            │
        │  原始工单 ──▶ 【决策模型算子】                │  一次请求同时给出
        │                 │  choice / noul / score    │  category + refund + urgency
        │                 ▼                          │
        │      category / refund / urgency / 置信度    │
        │                 │                          │
        │                 ▼                          │
        │            【属性路由】按 category 分叉        │
        └────────────────────────────────────────────┘
             │            │            │
          退款/财务       技术故障       账号安全
             │            │            │  category=account 且 urgency 高
             ▼            ▼            ▼
        自动建退款单    派研发工单     实时冻结 + 主管告警
             │            │            │
             └────────────┴────────────┘
                          │
                          ▼  全量落库（DuckDB / Doris / ES）
                 实时看板 · 班次复盘 · 标签沉淀
```

对应的任务定义（DataY 画布拖出来即得，也能交给 DataY Core 独立跑）：

```json
{
  "units": [
    { ".id": "ticket_in", ".name": "MySQLBinlogInput", "table": "tickets" },
    {
      ".id": "decide",
      ".name": "DecisionComponent",
      "model": "nimble",
      "stateFields": "content",
      "includeConfidence": true,
      "questions": "{\"category\":{\"type\":\"choice\",\"instructions\":\"Which team?\",\"criteria\":{\"refund\":\"退款\",\"tech\":\"技术\",\"account\":\"账号安全\",\"other\":\"其他\"}},\"refund\":{\"type\":\"noul\",\"instructions\":\"是否明确要求退款?\"},\"urgency\":{\"type\":\"score\",\"instructions\":\"紧急程度?\",\"criteria\":[\"低\",\"中\",\"高\"]}}"
    },
    { ".id": "route", ".name": "RouteOnAttribute", "matchMode": "data", "dataField": "category", "defaultLabel": "other" },
    { ".id": "freeze", ".name": "HttpInvoke", "url": "http://risk:8080/freeze", "method": "POST", "body": "{\"ticketId\":\"${ticket_id}\"}" },
    { ".id": "sink", ".name": "DuckDBWrite", "table": "tickets_decided", "model": "append" }
  ],
  "connections": [
    { "sourceId": "ticket_in", "targetId": "decide" },
    { "sourceId": "decide", "targetId": "route" },
    { "sourceId": "decide", "targetId": "sink" },
    { "sourceId": "route", "targetId": "freeze", "label": "account" },
    { "sourceId": "route", "targetId": "sink", "label": "other" }
  ]
}
```

三个关键点：

1. **入口是"流"**：工单产生即入流，不攒批、不等调度；
2. **中间是"决策"**：一次调用给出多个强类型字段，而不是生成一段待解析的文本；
3. **出口是"动作"**：命中的分叉直接回调业务系统，**响应发生在用户还在等待的时候**。

---

## 七、几个绕不开的工程问题

**① 决策模型能"兜底"到什么程度？**
决策模型给你的是**概率 + 置信度**，这正是分层处理的抓手：
命中高置信度 → 自动流转；命中低置信度（如 `confidence` 偏低）→ **走 `RouteOnAttribute` 转人工复核**；完全无关 → `other` 兜底。
**让模型只处理它擅长的，把不确定的交还给人。**

**② 本地推理吃得消吗？**
`tev1:0.8b` 这类小模型对资源非常友好，`nimble`（9B）在 Apple Silicon 上也能跑到约 91ms/次。真实高频流场景，先做**上游收敛**（明显规则用 SQL / 关键词先处理掉），只把"需要语义判断"的那部分送进决策算子。

**③ 输出还会"跑偏"吗？**
相比生成式模型，决策模型的输出**天生受 Schema 约束**——`choice` 只能从你给的 `criteria` 里选，`noul` 只能是 0~1，`score` 落在档位上。**脏输出的概率被大幅压缩**，下游拿到的就是干净的列。

**④ 稳定性怎么保证？**
`DecisionComponent` 内置重试（`retryCount` / `retryIntervalMillis`），超时类错误自动重试并记录完整异常链；任务取消或超时会立即中断，不会拖住整条流。

**⑤ 模型怎么选、怎么换？**
`nimble` / `tev1` / `tev1:0.8b`，甚至未来 Ollama 云端托管的决策模型，**只改「服务配置 · 决策模型」里的一个模型名**，任务不用动。

---

## 八、这条路走通之后

回到开头的场景。

**工单**：不再是"先来后到"地排队等人看，而是**进来的那一刻**就已经分好类、判好急、标注好是否需要退款——该转人工的转人工，该自动处理的当场处理。

**风控**：评论在被展示**之前**就被判定通过、折叠还是拦截，全流程在本地完成，数据不出机器。

**路由**：每个请求在入口处就被决策模型打了分，流量按决定自动分流到最合适、最经济的模型。

技术上的本质变化只有一句话：

> **"做决定"第一次可以像过滤、Join 一样，成为数据流里一个稳定、可编程、低延迟的算子。**

而且这个算子**跑在你自己机器上**：数据不外发、成本不按量烧、延迟在百毫秒级。

---

## 九、DataY 是什么

上面这条实时决策流水线，跑在 **DataY** 上。它是一个**开源、轻量的一体化数据平台**：

> 一个 JAR、内嵌 DuckDB，**2 核 4G** 就能跑通「数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用」的完整链路。

它由「一个引擎 + 一个平台」组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行，或嵌入 Java 应用 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、Quartz 调度、维度建模、指标管理、智能问数 |

**AI 原生能力：**

- 🧠 **管道内的决策模型 `DecisionComponent`**：逐行调用 Ollama 决策模型（`/v1/systemone`），把一次"决定"变成结构化字段；
- 💬 **管道内的大模型 `LlmComponent`**：逐行调用大模型，把非结构化文本变成结构化字段；
- 🔌 **面向 Agent 的 MCP Harness**：通过 `/mcp/datay` 把数据源、ETL 任务、数据模型的管理动作与指标问数能力暴露给 Claude Desktop / Cursor 等客户端。
- 🤖 **智能问数**：自然语言 → 指标 → 维度 → 时间过滤 → 取数，一句话拿报表。

**快速开始（本地决策，一条命令拉模型）：**

```bash
# 1. 安装并启动 Ollama（0.35+），拉取决策模型
ollama pull tev1:0.8b        # 或 nimble

# 2. 启动 DataY（决策模型服务默认指向 http://localhost:11434/v1）
git clone https://cnb.cool/yuyuejia/datay
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

在「服务配置 · 决策模型」里填上 `http://localhost:11434/v1`、模型 `nimble`，再拖一个「决策模型」组件到画布上——**本地决策，即刻上线。**

在线体验：<http://datay-demo.yuyuejia.com.cn/>
产品首页：<http://datay.yuyuejia.com.cn/>

---

## 十、写在最后

数据管道的使命，从来不只是"搬得更快"。

> 当"做决定"这件事被装进管道、又被放到本地，数据平台才真正从**"搬运数据"**走向**"在流动中创造决策"**。

生成式模型让机器学会"表达"，
决策模型让机器学会"决断"，
而**本地化**，让这一切**快得起、稳得住、留得住数据**。

三者合在一起，才是"边发生、边判断、边响应"的实时数据平台。
