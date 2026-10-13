# 让 AI 直接连上你的数据库：DataY 数据源 MCP

> 你问 AI「帮我看看订单库里今天的异常单」，它能秒写出一段 SQL——
>
> 但它连不上你的库。账号密码、JDBC 串、表结构、同步任务，全都隔着屏幕。
>
> **DataY 数据源 MCP，把这层窗户纸捅破了：让 AI 客户端直接连上你注册过的每一个数据源。**

---

## 一、AI 会写 SQL，却连不上你的库

过去一年，几乎每个团队都在用 AI 写 SQL。

场景很真实：运营想知道「昨天哪些门店的库存对不上」，数据同学把需求翻译成 SQL，让 AI 润色一下，再粘到客户端里跑。

但这条链路里，AI 全程只是个**文本编辑器**：

- 它不知道你有哪些数据源，也不知道哪个是生产、哪个是测试；
- 它看不到表结构，只能靠你手动粘贴字段名，经常猜错列名；
- 就算写出了正确的 SQL，也得**你**去连库、**你**去执行、**你**把结果复制回来；
- 想顺手建个同步任务，还得回到平台上，一格格填表、一张张选表。

问题的根子不在模型，而在**协议**：AI 能说话，但你的数据平台没给它留一扇门。

于是就有了 **MCP（Model Context Protocol）**——一个把平台能力标准化暴露给 AI 客户端的协议。接上之后，Claude Desktop、Cursor、DSH 这些客户端里的 AI，就能**真的动手**，而不只是**动嘴**。

---

## 二、DataY 数据源 MCP 是什么

DataY 通过 MCP 服务端，把「数据源」这个最基础、最高频的能力暴露出去，端点只有一个：

```text
POST /mcp/datasource     （Streamable HTTP）
```

一句话概括它的定位：

> **让 AI 客户端发现你的数据源、看懂你的表、跑通你的 SQL，并把表同步任务建起来、跑起来。**

它一共提供 **6 个工具**，全部以 `对象_动作` 命名，语义直白：

| 工具 | 作用 |
|------|------|
| `datasource_register` | 注册一个新数据源，返回 ID 与基本信息（**不含密码**） |
| `datasource_list` | 列出已注册的数据源，支持关键字过滤 |
| `datasource_tables` | 列出某个 schema 下的表清单（表名 + 注释） |
| `datasource_query` | 在指定数据源上执行 SQL，返回 `columns` / `rows` / `affectedRows` |
| `sync_task_create` | 创建表同步任务（全量 / 仅数据 / 仅结构），可挂 Cron |
| `sync_task_execute` | 立即执行指定的同步任务 |

注意顺序——这六个工具天然构成一条链路：

```text
注册数据源 → 列出数据源 → 看表结构 → 跑 SQL 取数 → 建同步任务 → 立即执行
```

**AI 不是在帮你查资料，而是在替你操作平台。**

---

## 三、六个工具，逐个说清楚

### ① `datasource_register`：把库接进来

必填 `name`、`type`、`url`、`username`、`password`；可选 `hostname`、`port`、`schemaName`、`description`，以及用于特殊连接器的 `extraParams`。

支持的类型覆盖 DataY 全量数据源：MySQL、PostgreSQL、Oracle、Doris、DuckDB、Elasticsearch 等。

```json
{
  "name": "订单库",
  "type": "mysql",
  "url": "jdbc:mysql://127.0.0.1:3306/order_db",
  "username": "readonly",
  "password": "******",
  "schemaName": "order_db"
}
```

> 返回结果里**只有配置信息，永远没有密码**。返回给模型的每一条数据源记录，密码字段都被裁掉了。

### ② `datasource_list`：先看清家底

支持按名称、主机、端口、URL、schema、用户名做关键字过滤，一次最多返回 200 条。

让 AI 先 `datasource_list` 再动手，是个**好习惯**——它会先知道「这台机器上有哪些库」，而不是瞎猜 ID。

### ③ `datasource_tables`：看懂表结构

```json
{ "dataSourceId": "12", "schema": "order_db", "search": "order", "limit": 200 }
```

- `schema` 可选，缺省用数据源自带的默认 schema；
- `search` 按表名模糊过滤，避免把几百张表一股脑塞给模型；
- `limit` 默认 200，上限 1000。

返回的是 `schema` / `table` / `catalog` / `comment`，**把表注释也带上**——AI 判断这张表是干嘛的，靠的往往就是注释。

### ④ `datasource_query`：真正把 SQL 跑起来

```json
{ "dataSourceId": "12", "sql": "select status, count(*) from t_order group by status" }
```

返回结构化的 `columns`、`rows` 与 `affectedRows`。

这一条看起来最简单，其实是最关键的一步：**AI 从「能写 SQL」变成「能取到数」。**

### ⑤ `sync_task_create`：把同步任务建起来

```json
{
  "jobName": "订单同步",
  "sourceId": "12",
  "targetId": "13",
  "type": "FULL_SYNC",
  "cron": "0 0 2 * * ?",
  "tables": [
    {
      "srcSchemaName": "order_db",
      "srcTableName": "t_order",
      "desSchemaName": "dw",
      "desTableName": "t_order"
    }
  ]
}
```

- `type` 支持 `FULL_SYNC`（默认，全量）/ `DATA_ONLY`（仅数据）/ `SCHEMA_ONLY`（仅结构）；
- `cron` 可选，填了就按表达式加入调度；
- 任务创建后状态是 **OFFLINE**，不会偷跑；
- 返回 `id`、`jobCode`、`status`，其中 `jobCode` 就是后续要用的任务标识。

### ⑥ `sync_task_execute`：立刻跑一次

```json
{ "syncTaskId": "31" }
```

建完任务不放心？让 AI 先手动执行一次看看效果，没问题再上线调度。

---

## 四、安全：能放权，但放得有分寸

把数据库交给 AI，第一反应当然是「安全吗？」。DataY 的答案是：**能力开放，权限不开放。**

### 令牌鉴权，一次生成

在 **个人设置** 页面生成 MCP Token，格式为 `mcp_<userId>_<random>`：

- 服务端只存 **BCrypt 哈希**，明文 **只展示一次**；
- 请求携带 `Authorization: Bearer mcp_1_xxxxxxxxxxxx` 即可；
- 令牌属于**某个用户**，这个用户看不到的库，AI 同样看不到。

### 租户隔离，跟着人走

请求头可以额外带 `X-Tenant-Id` 或 `X-Tenant-Code` 指定租户：

- 缺省时使用该令牌用户的**默认租户**；
- 指定的租户**必须属于该用户**，否则直接拒绝；
- 每次工具调用都会把租户上下文绑定到当前线程，**用完立刻清理**——避免线程复用时串租户。

### 出参裁剪，敏感信息不外流

数据源视图统一裁剪，**密码字段绝不返回**；`datasource_list` 也只给配置信息，不给凭据。

一句话：**AI 拿到的是一份「够用就好」的视图，不是你的全部家底。**

---

## 五、接上它，只要几行配置

先在个人设置里生成令牌，然后把它填进客户端配置。

### Claude Desktop / Cursor

```json
{
  "mcpServers": {
    "datay-datasource": {
      "type": "http",
      "url": "http://localhost:8080/mcp/datasource",
      "headers": {
        "Authorization": "Bearer mcp_1_xxxxxxxxxxxx"
      }
    }
  }
}
```

### DSH

```json
{
  "mcpServers": {
    "datay-datasource": {
      "url": "http://localhost:8080/mcp/datasource",
      "headers": {
        "Authorization": "Bearer mcp_1_xxxxxxxxxxxx",
        "X-Tenant-Code": "t3"
      }
    }
  }
}
```

配好之后，在客户端里 `tools/list` 一下，六个工具就都在了。

---

## 六、两个真实用得上的姿势

### 姿势一：一句话做数据体检

> 把「订单库」接进来，看看 `t_order` 里各类状态分别有多少条。

Agent 的调用序列大致是：

```text
datasource_register { name: "订单库", type: "mysql", url: "jdbc:mysql://...", ... }
datasource_list     { search: "订单库" }
datasource_tables   { dataSourceId: 12, schema: "order_db", search: "order" }
datasource_query    { dataSourceId: 12, sql: "select status, count(*) from t_order group by status" }
```

结果是**结构化的表格**，不是一段需要你手动复制的文本。

### 姿势二：把同步任务一句话建好

> 每天凌晨 2 点，把订单库的 `t_order` 同步到数仓的 `dw.t_order`，先跑一次看看。

```text
datasource_tables   { dataSourceId: 12, schema: "order_db", search: "t_order" }
sync_task_create    { jobName: "订单同步", sourceId: 12, targetId: 13, type: "FULL_SYNC",
                      cron: "0 0 2 * * ?", tables: [{ srcSchemaName: "order_db", srcTableName: "t_order",
                      desSchemaName: "dw", desTableName: "t_order" }] }
sync_task_execute   { syncTaskId: 31 }
```

从「知道有哪些表」到「任务已跑过一次」，全程不需要点开任何页面。

---

## 七、它和你已有的两套 MCP 什么关系？

DataY 的 MCP 是**一组端点、各司其职**，互不干扰：

| 端点 | 面向 | 典型场景 |
|------|------|----------|
| `/mcp/datasource` | 数据源取数与表同步 | 接库、看表、跑 SQL、建同步任务 |
| `/mcp/metric` | 指标问数（只读） | 一句话问指标、拿报表 |
| `/mcp/datay` | 平台对象管理 + 指标问数 | 数据源 / ETL 画布任务 / 数据模型的完整增删改查与运行 |

**什么时候用 `/mcp/datasource`？**

当你的诉求是「**连上库、看清表、跑通数、把表搬过去**」时，它就是最轻的那把刀——六个工具，一条链路，不绕弯。

当你要拖拽式 ETL 画布、要建维度 / 事实模型并物化落表时，再上 `/mcp/datay` 那一套。

---

## 八、立即体验

- 🌐 产品首页：[http://datay.yuyuejia.com.cn/](http://datay.yuyuejia.com.cn/)
- 🚀 在线演示：[http://datay-demo.yuyuejia.com.cn/](http://datay-demo.yuyuejia.com.cn/)
- 📦 开源地址：[https://cnb.cool/yuyuejia/datay](https://cnb.cool/yuyuejia/datay)

**让 AI 会写 SQL，只是开始；让 AI 能连上你的库，才算真正干活。**
