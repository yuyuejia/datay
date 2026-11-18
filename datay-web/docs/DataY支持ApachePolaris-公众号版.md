# 不装 Spark、不写代码：DataY 正式支持 Apache Polaris

> 一个 JAR 的轻量数据平台，如何用 DuckDB 打通 Polaris Iceberg REST Catalog 的元数据浏览、SQL 查询与 ETL 写入。

---

## 01 数据湖的“最后一公里”，卡在 Catalog 上

这两年，Iceberg 几乎成了开放数据湖的事实标准。但真正落地时，很多团队会遇到同一个问题：

**表格式统一了，Catalog 却还是各玩各的。**

Hive Metastore 老态龙钟，云厂商的 Catalog 又各有各的锁；想让 Spark、Flink、Trino、DuckDB 同时访问同一批 Iceberg 表，往往要先搭一套元数据服务、再维护一套权限体系。

Apache Polaris 就是为解决这个问题而生的：它是 Apache 基金会旗下的 **Iceberg REST Catalog** 实现，用一套标准的 REST 接口，把「元数据管理 + 访问控制 + 凭证下发」收敛到一个服务里。

但新的问题来了——

> Polaris 有了，轻量团队怎么用？
> 为了读几张 Iceberg 表，难道还要再部署一套 Spark 集群？

DataY 的答案很直接：**不用。**

在最新版本中，DataY 已原生支持 Apache Polaris 数据源。你不需要安装 Spark、Flink、Trino，不需要额外写一行 Java 代码，只要在 DataY 里新建一个数据源，就能完成：

- Catalog / Namespace / Table 三级元数据浏览
- 表结构、列类型查看
- SQL 工作台直接查询 Iceberg 表
- 数据集成任务批量写入
- INSERT / UPDATE / DELETE / OVERWRITE 完整写入语义

---

## 02 DataY 是怎么做到的？

一句话概括：**DataY 把 DuckDB 变成了 Polaris 的“轻量客户端”。**

DuckDB 1.5 的 `iceberg` 扩展原生支持 Iceberg REST Catalog，DataY 在此基础上做了一层工程化封装：

```
DataY ETL / SQL 工作台
        │
        ▼
   DuckDB（本地 scratch 文件）
        │  ATTACH ... TYPE ICEBERG
        ▼
 Apache Polaris（Iceberg REST Catalog）
        │
        ▼
 S3 / S3 兼容存储 / GCS（数据与元数据文件）
```

具体来说，DataY 为每个 Polaris 数据源创建一个独立的本地 DuckDB scratch 文件，连接时自动完成：

1. 加载 `httpfs`、`iceberg` 扩展；
2. 使用用户名/密码（即 OAuth2 Client ID / Client Secret）创建 Iceberg Secret；
3. 执行 `ATTACH '<catalog>' AS polaris (TYPE ICEBERG, ENDPOINT '...', SECRET ...)`；
4. `USE polaris`，让未限定 Catalog 的 SQL 也默认落到 Polaris。

凭证只写入 DuckDB Secret，不会出现在本地 DuckDB 连接串里，也不会随 SQL 日志泄露。

配置也足够简单：

| 配置项 | 填写内容 |
| --- | --- |
| 数据源类型 | `POLARIS` |
| 连接地址 | `polaris://host:8181/quickstart_catalog` |
| 用户名 | OAuth2 Client ID |
| 密码 | OAuth2 Client Secret |
| 默认 Schema | 目标 Namespace（不存在时自动创建） |

需要连接 MinIO 等 S3 兼容存储时，补充 `s3.endpoint`、`s3.key_id`、`s3.secret` 即可；使用 HTTPS 或反向代理时，可通过 `polaris.catalog_uri` 覆盖 REST Catalog 地址。

---

## 03 四个能力，覆盖数据湖日常

### 1. 元数据浏览：像看普通库表一样看 Iceberg

DataY 的数据源面板支持 Catalog → Namespace → Table 三级树形浏览。

这里踩过一个坑：DuckDB 的 `SHOW ALL TABLES` 对 Iceberg 表只返回占位列（`__` / `UNKNOWN`），拿不到真实结构。DataY 的做法是：

- 表清单：`SHOW ALL TABLES`
- 列结构：`DESCRIBE polaris."namespace"."table"`

这样 `INTEGER`、`VARCHAR`、`DECIMAL(18,2)` 都能原样展示，建表、同步、建模时不会再出现“字段类型未知”。

### 2. SQL 工作台：零学习成本查询数据湖

建好数据源后，直接在 SQL 工作台执行：

```sql
SELECT *
FROM polaris."sales"."orders"
WHERE order_date >= DATE '2026-01-01'
ORDER BY amount DESC
LIMIT 100;
```

DataY 会自动处理 Catalog 限定、OAuth2 认证和 S3 凭证，用户感知到的就是一条普通 SQL。

### 3. 数据集成：复用现有 ETL 链路，不新增组件

这是本次支持的重点。

DataY 没有为 Polaris 单独造一个“数据湖写入节点”，而是复用了已有的 `StreamJdbcOutput` / `SqlSink` / 数据同步流程：

- 目标表不存在？自动建表，并自动创建缺失的 Namespace；
- 批量写入走多值 INSERT，按行数和参数数分块；
- `APPEND`、`OVERWRITE` 与普通数据库保持一致的使用方式。

也就是说，**你原来怎么同步 MySQL、PostgreSQL，现在就怎么同步 Polaris。**

### 4. UPDATE / DELETE：用 CDC 前镜像补齐 Iceberg 的短板

Iceberg 表没有主键，也不支持 `INSERT OR REPLACE` / `ON CONFLICT`。

DataY 的解决方式是：利用 CDC 变更数据里的 `__before` 前镜像，做全列匹配：

```sql
UPDATE polaris."sales"."orders"
SET id = ?, name = ?, amount = ?
WHERE id IS NOT DISTINCT FROM ?
  AND name IS NOT DISTINCT FROM ?
  AND amount IS NOT DISTINCT FROM ?;
```

- `UPDATE` = 用 after 镜像替换 before 镜像；
- `DELETE` = 按 before 镜像精确删除旧行；
- 没有 `__before` 时退化为追加，保证不丢数据。

即使表没有主键，也能得到正确的更新和删除语义。

---

## 04 10 分钟上手：从 Docker 到第一条查询

### 第一步：启动 Polaris

```bash
docker run -d --name polaris \
  -p 8181:8181 -p 8182:8182 \
  -e POLARIS_BOOTSTRAP_CREDENTIALS=POLARIS,root,secret \
  -e polaris.realm-context.realms=POLARIS \
  apache/polaris:latest
```

再准备一个 S3 兼容存储（MinIO 或云上对象存储），创建 Catalog 并指定 `default-base-location`。

### 第二步：在 DataY 新建数据源

- 类型：Apache Polaris
- 地址：`polaris://127.0.0.1:8181/quickstart_catalog`
- 用户名：`root`
- 密码：`secret`
- 扩展参数：按需填写 `s3.endpoint`、`s3.key_id`、`s3.secret`

保存后点击「测试连接」，看到 Catalog 树即接入成功。

### 第三步：查询与写入

在 SQL 工作台执行查询，或在数据集成任务里把 Polaris 配成目标端，选择 `INSERT` / `UPDATE` / `DELETE` / `OVERWRITE` 写入模式即可。

---

## 05 工程细节：四个真实踩坑

一次完整的端到端验证，往往比写代码更能暴露问题。这次我们在本地用 Docker 启动 Polaris 1.9 + S3 兼容存储，跑通了「建表 → 元数据 → INSERT → UPDATE → DELETE → OVERWRITE → DROP」全链路，也顺手填了四个坑：

1. **Iceberg Secret 必须带 `ENDPOINT`**
   只给 Client ID / Secret，DuckDB 会报 `AUTHORIZATION_TYPE is 'oauth2' ... no 'endpoint'`，无法推导 Token 地址。

2. **新建 Namespace 后要重新挂载 Catalog**
   DuckDB 在 `ATTACH` 时缓存 namespace 列表，新建 namespace 后同一连接里的 `CREATE TABLE` 会报 `Schema not found`。DataY 在 `prepareTableCreate` 中检测缺失、创建后 `DETACH` / `ATTACH` 刷新缓存。

3. **`DETACH` 前必须先切回本地库**
   `USE polaris` 之后 Polaris 是默认数据库，直接 `DETACH` 会被拒绝；而单独的 `USE xxx` 会被当成 schema 解析，必须写成 `USE "<scratch>".main`。

4. **S3 `ENDPOINT` 不能带协议前缀**
   写成 `http://host:9100` 会被 DuckDB 拼成 `http://http://host:9100`。DataY 会自动剥离协议并推导 `USE_SSL`，用户填 `http://host:9100` 或 `host:9100` 都可以。

这些细节不写进文档，用户就会在第一次连接时踩一遍。现在，它们都已经沉在 DataY 的实现里。

---

## 06 适用边界

坦白说，Polaris 支持并不是“万能写入”。

- DuckDB 目前对 Iceberg REST Catalog 的写入仅支持 **S3 / S3 Tables / GCS** 作为底层存储，本地文件系统暂不支持；
- Iceberg 表没有主键，也不支持 `TRUNCATE`，`OVERWRITE` 采用 `DELETE` 清空后写入；
- 首次连接需要安装 `httpfs`、`iceberg` 扩展，运行环境需能访问 DuckDB 扩展仓库，或提前预装扩展；
- `UPDATE` / `DELETE` 依赖 CDC 前镜像，非 CDC 场景下会退化为追加写入。

如果你的场景是「已有 Polaris + 对象存储，需要一个轻量工具做查询、同步和调度」，那这套方案正好合适。

---

## 07 写在最后

DataY 的定位一直是：**数据平台可以很小，功能不打折。**

从 DuckDB、DuckLake 到今天的 Apache Polaris，我们想做的不是把重型大数据组件再包一层，而是让一个 JAR、2 核 4 G 的环境，也能完整走通「数据接入 → 数据湖 → 建模 → 查询」的链路。

Polaris 支持已经合入主分支。下一步，我们会继续补齐 Iceberg 的更多写入能力与数据湖治理特性。

如果你正在用 Polaris 或 Iceberg，欢迎在评论区聊聊你的使用场景。

---

## DataY 轻量数据平台

**DataY 是一套开源、轻量的一体化数据平台**，理念很简单：

> 数据平台可以很小，功能不打折。

传统数据平台往往需要 Hadoop、Spark、Flink、ClickHouse 一整套组件，几十台机器起步，运维门槛高。DataY 选择另一条路：

- **一个 JAR**，自研流批一体数据集成引擎，拒绝组件地狱；
- **2 核 4G** 就能跑通「数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用」的完整链路；
- 零依赖启动，10 分钟搭好开发环境；
- 内嵌 DuckDB 作为高性能分析引擎，同时支持 Doris、ClickHouse 等外部分析引擎；
- 调度 master 与执行 worker 可分可合，单机和集群同一套代码。

这里的「轻量」指的是**运行资源门槛低**，不是能力轻量。

DataY 由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行或嵌入 Java 应用 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数、分析看板 |

平台的核心能力包括：

- **数据集成**：MySQL、PostgreSQL、Oracle、SQL Server、Doris、ClickHouse、DuckDB 等二十余种数据源，支持全量、增量与 CDC 实时同步；
- **数据湖**：支持 DuckLake、Apache Polaris（Iceberg REST Catalog），可直接浏览元数据、执行 SQL 与批量写入；
- **维度建模**：星型 / 雪花模型，普通维度、层级维度、时间维度，事实表外键一键关联；
- **任务调度**：Quartz Cron 精确到秒，DAG 工作流编排，失败重试与暂停恢复；
- **高性能数仓**：内嵌 DuckDB，列存 + 向量化，支持 DuckLake / Iceberg 数据湖，也可外挂 Doris / ClickHouse；
- **智能问数**：自然语言 → 指标 → 维度 → 时间过滤 → 取数，一句话拿报表；
- **分析看板**：数据集管理、AI 生成看板、24 栅格设计器、大屏与嵌入分享；
- **AI 原生能力**：AI SQL 查询助手、ETL 任务设计助手、看板设计助手、智能问数助手、Java 脚本助手，以及面向 Agent 的 MCP Harness。

AI 能力兼容 OpenAI Chat Completions 协议，OpenAI、DeepSeek、通义千问、Ollama 等模型服务都可以对接。默认使用 DeepSeek，启动时通过 `DATAY_AI_API_KEY` 配置即可。

### 10 分钟跑起来

```bash
git clone https://gitee.com/yuyuejia/datay.git
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

产品首页：<http://datay.yuyuejia.com.cn/>  
在线体验：<http://datay-demo.yuyuejia.com.cn/>  
项目地址：<https://gitee.com/yuyuejia/datay>  
<https://github.com/yuyuejia/datay>