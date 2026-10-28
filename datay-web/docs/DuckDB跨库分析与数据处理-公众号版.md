# 一个 JAR，打通 MySQL、PostgreSQL 和本地文件：用 DuckDB 做跨库分析

## 一、先说痛点：数据越来越多，分析却越来越麻烦

很多团队的真实情况是：

- 订单在 MySQL；
- 用户和权限在 PostgreSQL；
- 日志、埋点、导出文件是 CSV / Parquet；
- 财务和分析师手里还有一堆 Excel；
- 历史数据可能躺在对象存储里。

想做一个稍微完整一点的分析，通常要经历这样的流程：

1. 从 MySQL 抽订单数据；
2. 从 PostgreSQL 抽用户数据；
3. 把 CSV、Excel 清洗一遍；
4. 统一导入临时库或数仓；
5. 等 ETL 跑完；
6. 最后才能写 SQL 做关联分析。

这条链路的问题很明显：

- **重**：要维护同步任务、调度系统、中间库；
- **慢**：临时分析也要等数据同步，时效性差；
- **贵**：小团队为了几次跨库分析，上一整套大数据组件并不划算；
- **容易错**：数据复制多份后，口径、时间字段、主键映射都可能不一致；
- **不灵活**：业务问题一变，ETL 链路就得改。

更尴尬的是，很多分析根本不需要“先建一个数仓”。

比如：

> 把 MySQL 里的订单，和 PostgreSQL 里的用户画像关联一下，看看不同渠道的高价值用户最近 30 天的复购情况。

这类需求，本质上就是一次跨库 JOIN。

如果数据库本身就能直接完成，为什么还要先搬运一遍？

这正是 DuckDB 擅长的事情。

---

## 二、DuckDB 是什么？

DuckDB 是一个**嵌入式分析型数据库**。

可以把它理解成“分析场景下的 SQLite”：

- 不需要单独部署数据库服务；
- 以库的形式嵌入到应用进程中；
- 支持标准 SQL；
- 列式存储、向量化执行；
- 对 OLAP 聚合、JOIN、扫描非常友好；
- 可以直接读取 CSV、Parquet、JSON 等文件；
- 支持通过扩展连接 MySQL、PostgreSQL、SQLite 等外部数据源。

它特别适合：

- 本地数据分析；
- 数据科学和 Notebook；
- 轻量级数仓；
- 数据集成过程中的计算引擎；
- 跨数据源联合查询；
- 把“临时分析”变成可重复执行的 SQL。

和传统数仓相比，DuckDB 最大的特点是：**你不需要先准备一套集群，打开就能查。**

---

## 三、DuckDB 的跨库分析能力：把外部数据库当表查

DuckDB 可以通过 `ATTACH` 把外部数据库挂载进来。

挂载之后，MySQL、PostgreSQL 里的表，在 SQL 里看起来就像本地表一样，可以直接 JOIN、聚合、导出。

### 1. 挂载 MySQL

```sql
INSTALL mysql;
LOAD mysql;

ATTACH 'mysql://reader:******@10.0.0.12:3306/sales'
AS mysql_sales
(TYPE mysql, READ_ONLY);
```

查看挂载结果：

```sql
SELECT * FROM duckdb_databases();
```

查询 MySQL 表时，使用 `catalog.schema.table` 三段式名称：

```sql
SELECT *
FROM mysql_sales.sales.orders
LIMIT 100;
```

### 2. 挂载 PostgreSQL

```sql
INSTALL postgres;
LOAD postgres;

ATTACH 'host=10.0.0.20 port=5432 dbname=user_center user=reader password=******'
AS pg_user
(TYPE postgres, READ_ONLY);
```

查询 PostgreSQL：

```sql
SELECT *
FROM pg_user.public.users
LIMIT 100;
```

### 3. 跨库 JOIN：MySQL 订单 + PostgreSQL 用户

现在，一次 SQL 就能完成跨库关联：

```sql
SELECT
    date_trunc('day', o.created_at) AS order_date,
    u.channel,
    count(*) AS order_cnt,
    sum(o.amount) AS gmv
FROM mysql_sales.sales.orders o
JOIN pg_user.public.users u
  ON o.user_id = u.id
WHERE o.created_at >= current_date - INTERVAL 30 DAY
  AND u.user_level = 'VIP'
GROUP BY 1, 2
ORDER BY 1, 2;
```

不需要先把订单同步到 PostgreSQL，也不需要在中间库建临时表。

### 4. 跨库 JOIN 本地 Parquet / CSV

DuckDB 还可以直接读取文件：

```sql
SELECT
    o.order_id,
    o.amount,
    p.city,
    p.risk_level
FROM mysql_sales.sales.orders o
JOIN read_parquet('s3://analytics-bucket/user_profile/*.parquet') p
  ON o.user_id = p.user_id
WHERE o.created_at >= current_date - INTERVAL 7 DAY;
```

也可以直接读取 CSV：

```sql
SELECT *
FROM read_csv_auto('exports/channel_mapping.csv');
```

或者 JSON：

```sql
SELECT *
FROM read_json_auto('logs/events-2026-10-*.json');
```

这意味着，**数据库、文件、对象存储可以在同一条 SQL 里协同工作。**

---

## 四、不只是查询：用 DuckDB 做数据处理

DuckDB 不只是“查一下”，也可以承担轻量级的数据加工。

### 1. 把跨库结果落到 Parquet

```sql
COPY (
    SELECT
        o.order_id,
        o.user_id,
        o.amount,
        u.channel,
        u.city
    FROM mysql_sales.sales.orders o
    JOIN pg_user.public.users u
      ON o.user_id = u.id
    WHERE o.created_at >= current_date - INTERVAL 30 DAY
) TO 'result/vip_orders.parquet' (FORMAT PARQUET);
```

Parquet 列式压缩、读取快，适合作为后续分析、归档或数据湖文件。

### 2. 用 CTAS 建本地分析表

```sql
CREATE TABLE vip_order_summary AS
SELECT
    u.channel,
    date_trunc('month', o.created_at) AS month,
    count(*) AS order_cnt,
    sum(o.amount) AS gmv,
    avg(o.amount) AS avg_order_amount
FROM mysql_sales.sales.orders o
JOIN pg_user.public.users u
  ON o.user_id = u.id
WHERE u.user_level = 'VIP'
GROUP BY 1, 2;
```

后续分析直接查本地表，避免重复访问源库。

### 3. 增量加工

```sql
INSERT INTO vip_order_summary
SELECT
    u.channel,
    date_trunc('month', o.created_at) AS month,
    count(*) AS order_cnt,
    sum(o.amount) AS gmv,
    avg(o.amount) AS avg_order_amount
FROM mysql_sales.sales.orders o
JOIN pg_user.public.users u
  ON o.user_id = u.id
WHERE o.created_at >= current_date - INTERVAL 1 DAY
  AND u.user_level = 'VIP'
GROUP BY 1, 2;
```

配合调度，就可以形成轻量级的数据加工链路。

---

## 五、DuckDB 跨库分析，有哪些实践建议？

DuckDB 很强，但也要用对场景。

### 1. 源库账号尽量只读

跨库分析主要是“读”，建议给 DuckDB 配置只读账号，避免误操作生产库。

### 2. 过滤条件尽量下推

虽然 DuckDB 会做优化，但跨库 JOIN 仍然可能产生网络传输。能提前过滤的，尽量提前过滤：

```sql
WHERE o.created_at >= current_date - INTERVAL 7 DAY
```

不要一上来就 `SELECT *` 扫全表。

### 3. 大表 JOIN 要控制数据量

如果两张表都很大，跨库 JOIN 的网络和内存成本会上升。可以考虑：

- 先聚合再关联；
- 把维度表同步到本地；
- 把结果物化成 Parquet 或本地表；
- 对高频分析建立固定加工任务。

### 4. ATTACH 是会话级的

`ATTACH` 挂载通常只在当前连接会话内有效。应用集成时，需要在连接初始化时统一执行挂载，或者使用支持 catalog 管理的平台来托管这些连接。

### 5. DuckDB 不是 OLTP 数据库

它适合分析，不适合高并发事务写入。生产上更合理的定位是：

- 分析计算引擎；
- 轻量数仓；
- 数据加工中间层；
- 跨库联邦查询入口。

---

## 六、DataY：把 DuckDB 的能力产品化

DuckDB 很好用，但如果要让团队一起用，还会遇到新的问题：

- 数据源怎么统一管理？
- ATTACH 挂载怎么配置？
- 跨库查询怎么做权限隔离？
- 加工任务怎么调度？
- 结果怎么建模、怎么问数？
- 业务人员不会写 SQL 怎么办？

**DataY 就是在这个背景下做的一体化轻量数据平台。**

DataY 的定位很明确：

> 数据平台可以很小，功能不打折。

它把数据集成、维度建模、任务调度、高性能数仓和智能问数，放进一套可以单机运行的平台里。

### 1. 内嵌 DuckDB，不搞组件地狱

DataY 内置 DuckDB，不需要额外部署一套分析型数据库。

一个 JAR，2 核 4G，就能跑通：

```text
数据集成 → 维度建模 → 任务调度 → 高性能数仓 → 智能问数
```

对中小团队来说，这比“先上一套大数据平台”现实得多。

### 2. 跨库查询与 catalog 管理

DataY 的数据查询页支持 DuckDB catalog 浏览。

执行：

```sql
ATTACH 'mysql://reader:******@10.0.0.12:3306/sales'
AS mysql_sales
(TYPE mysql);
```

左侧目录会自动展示挂载后的数据库、schema 和表。

这意味着，MySQL、PostgreSQL、DuckDB 本地库和文件数据，可以在同一个查询界面里被统一访问。

### 3. 从“查一次”到“稳定跑”

临时分析用 DuckDB 很舒服，但业务真正需要的是可重复、可调度、可监控。

DataY 提供：

- 可视化 ETL 数据流；
- MySQL / PostgreSQL / Oracle CDC；
- Cron 与 DAG 调度；
- 维度建模与指标管理；
- DuckDB / DuckLake 数仓；
- AI 智能问数；
- MCP Harness，让 Agent 直接操作平台对象。

它把 DuckDB 从一个“好用的分析库”，变成了团队可以日常使用的数据平台。

---

## 七、总结

DuckDB 带来的最大变化，不是“又多了一个数据库”，而是：

**很多跨库分析，不再需要先搬运数据。**

一条 `ATTACH`，加上一条 JOIN，就能把 MySQL、PostgreSQL、Parquet、CSV 放进同一个分析上下文。

对个人和小团队来说，这是效率工具；

对平台来说，这是新的数据集成与分析底座。

而 DataY 做的事情，是把这套能力工程化：

- 用 DuckDB 做计算；
- 用可视化平台做管理；
- 用调度和建模做沉淀；
- 用 AI 降低使用门槛。

如果你也在被“跨库取数、临时分析、轻量数仓”困扰，不妨试试 DuckDB。

如果你希望团队直接拥有一个开箱即用的平台，可以看看 DataY。

**快速开始：**

```bash
git clone https://github.com/yuyuejia/datay
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

也可以只用~~ Core：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

产品首页：<http://datay.yuyuejia.com.cn/>  
在线体验：<http://datay-demo.yuyuejia.com.cn/>  
项目地址：<https://github.com/yuyuejia/datay>


> 数据平台可以很小，功能不打折。
