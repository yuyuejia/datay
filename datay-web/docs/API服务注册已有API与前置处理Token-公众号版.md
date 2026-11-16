# 不写一行代码，把已有 API 变成平台数据服务：DataY 前置处理换 Token 实战

> 很多团队的接口早就有了：订单查询、库存同步、客户画像……但它们散落在各个系统里，鉴权方式五花八门，想让数据消费方统一调用，往往得再写一层网关代码。
>
> 在 DataY 的「数据服务」模块里，选择「已存在的 API」，填一填就能把已有接口注册成标准数据服务。遇到需要先换 Token 的接口，也没问题——**前置处理**会先帮你把 Token 拿到，再自动带进主请求。

---

## 一、为什么需要「注册已有 API」

平台里的数据服务，传统做法是从表、SQL、数据集发布出去。但现实里还有大量数据只在 HTTP 接口后面：

- 第三方 SaaS 只提供 REST API，不开放数据库；
- 内部业务系统的接口已有鉴权，短时间不可能改造；
- 数据消费方希望走统一入口、统一鉴权、统一日志，而不是各自记一堆地址和密钥。

如果为每个接口单独写适配代码，成本高、上线慢，接口一改还得重新发版。

DataY 的做法是：**把已有 API 当成一种数据来源**。配置一次，平台负责转发、鉴权、参数替换、结果裁剪和调用日志，调用方只需要记住一个服务编码。

---

## 二、五分钟完成注册

### 第 1 步：新建数据服务

进入「数据服务」→「新建」，填写服务名称和服务编码。服务编码是调用地址里的唯一标识，只允许字母、数字、下划线和中划线，例如 `order_query`。

数据来源选择 **「已存在的 API」**。

【图 1：新建数据服务，数据来源选择「已存在的 API」】

### 第 2 步：从 cURL 一键导入

最省事的入口是 **「从 cURL 导入」**。把平时在终端里调通的命令粘进去：

```bash
curl -X GET 'https://api.example.com/v1/orders?status=PAID' \
  -H 'Authorization: Bearer xxxxxxxx'
```

平台会自动解析出请求地址、请求方法、请求头和请求体，填进表单。前置处理那一栏也有同样的导入按钮，后面会用到。

【图 2：粘贴 cURL，自动填充请求配置】

### 第 3 步：补齐请求配置

导入后可以按需微调：

| 配置项 | 说明 |
| --- | --- |
| 请求地址 | 上游接口完整 URL，支持 `${参数名}` 占位符 |
| 请求方法 | GET / POST / PUT / PATCH / DELETE / HEAD |
| 超时（ms） | 默认 30000，可按接口情况调整 |
| SSL 校验 | 默认开启；自签名证书的内网环境可临时关闭，生产环境建议保持开启 |
| 请求头 | JSON 格式，值里同样支持占位符 |
| 请求体 | 非 GET/HEAD 方法可填，JSON 或任意文本 |
| 数据提取路径 | JSONPath，只把响应中的某一段返回给调用方；留空则返回完整响应 |

这里有个很实用的设计：**调用方传进来的参数，可以直接注入到地址、请求头和请求体里**。

比如地址写成：

```text
https://api.example.com/v1/orders?status=${status}&pageSize=${pageSize}
```

调用时传 `status=PAID&pageSize=20`，平台会在转发前完成替换。如果调用方漏传了参数，会直接返回明确的错误提示，而不是把 `${status}` 原样发给上游。

> 小贴士：`access_token` 是平台自己的调用凭证参数，不会被转发给上游，避免平台 Token 泄漏到第三方接口。

### 第 4 步：按需裁剪返回结果

上游返回一大坨 JSON，而调用方只关心其中列表？在「数据提取路径」里填 JSONPath 即可：

```text
$.data.list
```

平台会把提取到的内容作为 `data` 返回，并自动识别 `columns` 和 `total`：

- 提取结果是数组：`total` 为数组长度，`columns` 取第一个元素的字段名；
- 提取结果是对象：`total` 为 1，`columns` 为对象字段名；
- 留空：返回完整响应。

这样数据消费方拿到的结构就是统一的，不用为每个上游接口写解析逻辑。

---

## 三、重点：用前置处理自动获取 Token

真正麻烦的接口，往往不是"调一下"这么简单。

OAuth2 的 `client_credentials`、企业微信/钉钉的 `access_token`、自建网关的临时票据……共同点是：**主请求之前，得先拿一个有时效的 Token**。Token 过期了要重新取，并发调用时又不想每次都去换。

DataY 把这段逻辑收进了「前置处理」。

### 3.1 它是怎么工作的

勾选 **「启用前置处理（先调用接口获取 Token）」** 后，一次服务调用会变成三步：

1. **先调前置接口**：按配置的地址、方法、请求头、请求体发起请求；
2. **提取 Token**：用「Token 路径」从响应 JSON 中取值，放入调用上下文，变量名就是 `${token}`；
3. **再调主接口**：主请求的地址、请求头、请求体中都可以用 `${token}` 引用它。

```
调用方
  │
  ├─▶ 前置接口（换取 Token）
  │        │
  │        └─ 按 tokenPath 提取 → 上下文 ${token}
  │
  └─▶ 主接口（Authorization: Bearer ${token}）
```

整个过程对调用方完全透明：调用方只需要传业务参数，不需要知道 Token 怎么来的。

### 3.2 配置示例：OAuth2 客户端模式

假设上游要求先换 Token：

```bash
curl -X POST 'https://auth.example.com/oauth/token' \
  -H 'Content-Type: application/json' \
  -d '{"client_id":"demo","client_secret":"******","grant_type":"client_credentials"}'
```

响应：

```json
{
  "code": 0,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "expiresIn": 7200
  }
}
```

在「前置处理」里这样填：

| 配置项 | 值 |
| --- | --- |
| 前置地址 | `https://auth.example.com/oauth/token` |
| 请求方法 | `POST` |
| 前置请求头 | `{"Content-Type": "application/json"}` |
| 前置请求体 | `{"client_id":"demo","client_secret":"******","grant_type":"client_credentials"}` |
| Token 路径 | `$.data.accessToken` |
| 缓存秒数 | `3600` |

然后回到主请求，在请求头里写：

```json
{
  "Authorization": "Bearer ${token}"
}
```

主请求地址照常配置，比如：

```text
https://api.example.com/v1/orders?status=${status}
```

保存后，调用 `order_query` 时平台会自动完成"换 Token → 带 Token 调主接口"的整套动作。调用方看到的只有业务参数 `status`。

【图 3：前置处理配置，Token 路径填写 `$.data.accessToken`】

### 3.3 Token 路径怎么写

「Token 路径」用的是 JSONPath：

- `$.data.accessToken`、`$.result.token`：从嵌套 JSON 中取字段；
- 留空：直接把整个响应文本作为 Token（适用于上游直接返回纯文本 Token 的接口）；
- 取不到值：返回"未能从前置接口响应中提取 Token"，不会带着空 Token 去调主接口。

### 3.4 缓存秒数：别每次都去换 Token

把「缓存秒数」设为大于 0 的值，平台会在进程内缓存 Token，过期后自动重新获取。

上面例子里填 `3600`，意味着一个小时内复用同一个 Token。这带来两个好处：

- 减少鉴权接口压力，避免触发上游限流；
- 降低调用延迟，主请求不再每次都多一跳。

缓存的键由**服务 ID + 前置处理配置**共同决定。也就是说，改了前置地址、请求体或 Token 路径，缓存会自然失效，不会误用旧 Token。

如果上游 Token 有效期很短，或者要求严格的一次一取，把缓存秒数设为 `0` 即可关闭缓存。

### 3.5 前置请求也能用业务参数

前置请求的地址、请求头和请求体同样支持 `${参数名}` 占位符。

这意味着多租户场景可以这样玩：调用方传入 `tenant`，前置请求体写成：

```json
{"tenant": "${tenant}", "grant_type": "client_credentials"}
```

平台会用当前调用方的参数去换对应租户的 Token，再访问主接口。同一份服务配置，服务多个调用方。

---

## 四、发布之后怎么调

服务状态设为「启用」后，第三方通过统一入口访问：

```bash
curl 'https://your-datay-host/open-api/data/order_query?status=PAID' \
  -H 'Authorization: Bearer <mcp_token>'
```

也支持 POST，业务参数放在 JSON Body 里：

```bash
curl -X POST 'https://your-datay-host/open-api/data/order_query' \
  -H 'Authorization: Bearer <mcp_token>' \
  -H 'Content-Type: application/json' \
  -d '{"status":"PAID","pageSize":20}'
```

> 鉴权也兼容查询参数 `access_token=<mcp_token>`，方便不便设置 Header 的客户端。注意这是**平台调用凭证**，与上游 Token 是两回事。

返回结构统一为：

```json
{
  "code": 0,
  "message": "success",
  "data": [ ... ],
  "columns": ["id", "orderNo", "amount"],
  "total": 20
}
```

出错时 `code` 为对应状态码，`message` 给出原因，例如参数缺失、上游返回非 2xx、Token 提取失败等。配合调用日志，排障时能直接看到请求路径、参数、耗时和错误信息。

---

## 五、几个容易踩的坑

**1. 把平台 Token 和上游 Token 搞混**

`${token}` 只有一个来源：前置处理。如果主请求里用了 `${token}` 却没启用前置处理，平台会提示缺少参数。平台自身的调用凭证请放在 `Authorization` 头或 `access_token` 参数里，它会参与平台鉴权，但不会被转发。

**2. Token 路径填错层级**

上游返回 `{"data":{"accessToken":"..."}}`，路径就要写 `$.data.accessToken`，而不是 `$.accessToken`。建议先在接口文档或调试工具里确认结构。

**3. 缓存时间超过 Token 有效期**

缓存秒数应小于上游 Token 的实际有效期。否则缓存里的 Token 已失效，主接口会返回 401，表现为"偶尔失败"。

**4. 生产环境关掉 SSL 校验**

关闭 SSL 校验只适合自签名证书的内网联调。公网接口请保持开启，避免中间人风险。

**5. 前置接口地址写错，错误会延后暴露**

前置接口非 2xx 时，平台会返回"前置接口调用失败（HTTP xxx）"，并带上截断后的响应内容。联调阶段建议先单独验证前置接口连通性。

---

## 六、小结

「已存在的 API」这个数据来源，解决的不是"能不能调"的问题，而是**把已有接口纳入统一的数据服务治理**：

- 注册即发布，cURL 导入降低配置成本；
- 占位符让一套配置适配不同调用参数；
- 前置处理把换 Token 的脏活收进平台，调用方无感；
- Token 缓存兼顾性能与上游压力；
- JSONPath 裁剪统一返回结构，调用日志补齐可观测性。

如果你手上正有一批"只能通过接口访问"的数据，不妨先从一个小接口开始，把它注册成 DataY 数据服务。等调用方不再问你要地址和密钥的时候，你会感谢这次配置。

---

## 七、DataY 是什么

这篇文章里的「数据服务」，只是 **DataY** 的一个模块。

DataY 是一个开源、轻量的一体化数据平台，目标很直接：**用尽量小的资源，把数据集成、任务调度、数仓和指标分析这几件事跑通。**

这里的"轻量"指的是**运行资源门槛低**，不是能力轻量。它不依赖 Hadoop、Spark 这类重型组件，一个 JAR 就能启动，默认内嵌 DuckDB 作为高性能分析引擎。开发机 2 核 4G 就能跑通从数据接入到报表查询的完整链路。

它主要由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、流批一体；JSON 定义任务、自动建表、CDC 实时同步 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、数据服务、智能问数 |

除了本文介绍的数据服务，DataY 还提供：

- **数据集成**：MySQL、PostgreSQL、Oracle、SQL Server、Doris、ClickHouse、DuckDB、DuckLake 等数据源，支持全量、增量与 CDC 实时同步；
- **任务调度**：Cron 精确到秒，DAG 工作流编排，支持手动触发、暂停恢复、失败重试，单机与 master/worker 集群共用一套代码；
- **维度建模与指标管理**：星型/雪花模型、原子指标与衍生指标，为智能问数提供统一口径；
- **智能问数**：自然语言 → 指标 → 维度 → 时间过滤 → 取数，一句话拿到报表；
- **AI 原生**：大模型可以作为 ETL 管道中的一个算子，也能通过 MCP 让 Agent 直接操作平台。

一句话概括：

> **DataY = DataX + Nifi + DolphinScheduler**，但它不是三个工具的拼盘，而是把这三类能力重新设计成一套运行时。

**快速开始：**

```bash
git clone https://gitee.com/yuyuejia/datay.git
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

- 产品首页：<http://datay.yuyuejia.com.cn/>
- Github：<https://github.com/yuyuejia/datay>
- Gitee：<https://gitee.com/yuyuejia/datay>

如果这篇文章对你有帮助，欢迎点赞、在看、转发给正在被接口鉴权和数据服务折腾的同事。

---

*本文基于 DataY 数据服务模块的「已存在的 API」能力整理。不同版本的界面字段可能略有差异，以实际页面为准。*
