# ETL 跨库补数，别再写 N+1 查询了

## JDBCLookup 组件实战：20 条订单，3 条 SQL 搞定客户信息回填

> 事实表在一个库，维度表在另一个库，ETL 里怎么优雅地补字段？
> 今天用一个真实的订单补客户信息场景，看看 DataY 的 JDBCLookup 是怎么做的。

---

### 01 一个很常见的 ETL 难题

做数据集成时，经常会遇到这样的表结构：

- 订单库里有 `orders`，只存了 `customer_id`；
- 客户库里有 `customers`，存着客户名称、等级、区域、额度；
- 两份数据不在同一个数据库，甚至不在同一个实例。

现在需要把订单数据加工成一张宽表：

```text
order_id | customer_id | customer_name | member_level | customer_region | amount
```

最直接的做法是写代码：

```java
for (Order order : orders) {
    Customer customer = customerMapper.selectById(order.getCustomerId());
    order.setCustomerName(customer.getName());
    // ...
}
```

数据量一上来，问题就暴露了：

- **N+1 查询**：1000 条订单就是 1000 次数据库往返；
- **类型不一致**：上游是 `Integer`，关联表是 `BIGINT`，明明是一个值却匹配不上；
- **字段冲突**：两边都有 `region`，回填后把原字段覆盖了；
- **异常处理、空值处理、批次控制**，全都要自己写；
- 换一个库、换一张表，代码还得再改一遍。

这类需求本质上就是一次**跨库 Lookup**，不应该每次都重复造轮子。

---

### 02 JDBCLookup 是什么

`JDBCLookup` 是 DataY 专门为跨库补数场景提供的处理组件：

> 根据上游记录中的某一列作为关联键，批量查询另一个数据源的表，并把查询结果字段回填到原记录中。

它有几个关键设计：

- **批量查询**：把本批数据的关联键去重后，拼成 `IN (?, ?, ...)` 一次查询，而不是逐行查；
- **两种关联方式**：`LEFT` 保留未匹配记录并补空，`INNER` 只保留匹配成功的记录；
- **字段前缀**：回填字段自动加前缀，避免覆盖上游同名字段；
- **条件过滤**：支持附加 `where` 条件，只关联有效数据；
- **结果缓存**：相同关联键不重复查询；
- **元数据合并**：回填字段会合并进表元数据，下游组件能正确识别字段和类型。

下面用一个真实场景跑一遍。

---

### 03 实战：订单跨库补全客户信息

#### 准备两个库

订单库 `datay_it_orders`：

```sql
CREATE TABLE orders (
  order_id BIGINT PRIMARY KEY,
  customer_id BIGINT,
  amount DECIMAL(10,2),
  region VARCHAR(20),
  status VARCHAR(20),
  created_at DATETIME
);
```

客户库 `datay_it_dim`：

```sql
CREATE TABLE customers (
  id BIGINT PRIMARY KEY,
  name VARCHAR(50),
  member_level VARCHAR(10),
  region VARCHAR(20),
  credit_limit DECIMAL(12,2),
  status TINYINT
);
```

造 20 条订单、8 个客户，其中特意埋了几个“坑”：

- 有订单引用了不存在的客户 `9999`；
- 有客户 `status = 0`，不应该被关联；
- 订单里也有一个 `region` 字段，值为“未知区域”；
- 上游关联键会出现 `Integer`、字符串、`BigDecimal` 等不同类型。

这些正是真实环境里最容易出问题的地方。

#### 配置组件

在设计器里拖入 `JDBCLookup`，核心配置如下：

```json
{
  "name": "JDBCLookup",
  "sourceId": "客户库数据源",
  "schema": "datay_it_dim",
  "table": "customers",
  "localField": "customer_id",
  "lookupField": "id",
  "selectColumns": "name, member_level, region, credit_limit, status",
  "outputPrefix": "customer_",
  "where": "status = 1",
  "joinType": "LEFT",
  "batchSize": 500,
  "cache": true
}
```

含义很直观：

| 配置 | 说明 |
| --- | --- |
| `localField` | 上游记录里的关联字段：`customer_id` |
| `lookupField` | 关联表里的字段：`id` |
| `selectColumns` | 需要回填的客户字段 |
| `outputPrefix` | 回填字段统一加 `customer_` 前缀 |
| `where` | 只关联 `status = 1` 的有效客户 |
| `joinType` | `LEFT`：未匹配的订单保留 |
| `batchSize` | 每批最多 500 个关联键 |

#### 执行结果

20 条订单，9 个不同的 `customer_id`。把 `batchSize` 调成 3 后，日志里只出现了 3 条 SQL：

```text
执行关联查询 SQL:
SELECT id, name, member_level, region, credit_limit, status
FROM datay_it_dim.customers
WHERE id IN (?, ?, ?) AND (status = 1)
关联键数量: 3

关联查询完成，输入 20 条，输出 20 条
```

补全后的数据：

| order_id | customer_id | order_region | customer_name | customer_member_level | customer_region |
| --- | --- | --- | --- | --- | --- |
| 2001 | 1001 | 未知区域 | 张伟 | 金卡 | 华东 |
| 2002 | 1002 | 未知区域 | 李娜 | 金卡 | 华北 |
| 2003 | 1003 | 未知区域 | 王强 | 银卡 | 华南 |
| 2009 | 1008 | 未知区域 | NULL | NULL | NULL |
| 2010 | 9999 | 未知区域 | NULL | NULL | NULL |

可以看到：

- 客户信息被正确回填；
- 订单原有的 `region` 字段没有被覆盖；
- `status = 0` 的客户 1008 没有匹配上，LEFT 关联补了 `NULL`；
- 不存在的客户 9999 同样补 `NULL`，订单记录没有丢。

---

### 04 几个值得关注的能力

#### 1. 批量 IN 查询，避免 N+1

组件会先把本批记录的关联键去重，再按 `batchSize` 分批查询。

20 条订单只有 9 个不同客户，`batchSize = 3` 时就是 3 条 SQL，而不是 20 条。

默认每批 500 个键，最大支持 5000。既避免 SQL 过长，也避免逐行查询。

#### 2. LEFT / INNER 两种关联方式

- `LEFT`：未匹配记录保留，回填字段补 `NULL`，适合订单、日志这类不能丢数据的场景；
- `INNER`：未匹配记录直接丢弃，适合只处理有效关联数据的场景。

在测试中，同一个数据集：

- LEFT：20 条进，20 条出；
- INNER：20 条进，19 条出，只丢掉客户不存在的订单。

#### 3. 字段前缀，避免“悄悄覆盖”

这是实际使用中最容易踩的坑。

如果 `outputPrefix` 留空，回填字段会直接使用关联表字段名。以上面的 `region` 为例：

```text
orders.region = "未知区域"
customers.region = "华东"
```

匹配成功后，`customers.region` 会覆盖 `orders.region`，结果变成“华东”。

所以建议：

> **只要关联表和上游可能存在同名字段，就配置一个业务前缀，例如 `customer_`。**

另外注意一个细节：LEFT 关联未匹配时，组件只会在字段不存在时补 `NULL`，不会把上游已有的值覆盖成 `NULL`。

#### 4. 关联键类型自动规范化

真实数据里，同一个客户 ID 可能是：

- `Integer 1001`
- `Long 1001`
- 字符串 `"1001"`
- `BigDecimal("1001.00")`

组件会把这些值统一规范化后再匹配。测试中分别用 `Integer`、字符串、`BigDecimal` 作为关联键，都能正确命中 `BIGINT` 主键。

这一点看起来不起眼，但能省掉大量“明明有数据却关联不上”的排查时间。

#### 5. 结果缓存

组件默认开启 LRU 缓存，最多缓存 10000 个关联键。

测试中验证了这样的场景：

1. 第一次查询，客户 1001 返回“张伟”；
2. 直接修改数据库，把名称改成“张伟-已改名”；
3. 再次执行同一个组件，仍然返回“张伟”，因为命中了缓存；
4. 新建一个关闭缓存的组件，立即读到“张伟-已改名”。

所以：

- 维度数据变化不频繁，保留缓存可以显著减少查询；
- 维度数据实时性要求高，建议设置 `cache = false`。

#### 6. 元数据自动合并

回填字段不只是写进 JSON 记录，还会合并到 FlowFile 的表元数据里。

下游组件看到的输出结构会包含：

```text
customer_name          VARCHAR
customer_member_level  VARCHAR
customer_region        VARCHAR
customer_credit_limit  DECIMAL
customer_status        TINYINT
```

这样后续建表、写库、字段映射都不需要再手工补字段定义。

---

### 05 测试验证结果

这次用真实 MySQL 8.0 跨库跑了一遍，4 个场景全部通过：

| 场景 | 结果 |
| --- | --- |
| LEFT 跨库关联 + 字段回填 | 20 条 → 20 条，字段正确 |
| INNER 关联 | 20 条 → 19 条，未匹配记录被丢弃 |
| 关联键类型规范化 | Integer / String / BigDecimal 均命中 |
| 缓存命中 | 改库后仍返回缓存值，关闭缓存后读到新值 |
| 同名字段覆盖 | 未配置前缀时 `region` 被覆盖 |
| 元数据合并 | 字段及类型正确写入表元数据 |

---

### 06 适用场景与使用建议

JDBCLookup 适合：

- 事实表补维度信息，例如订单补客户、明细补商品；
- 跨库、跨实例的数据关联；
- 维度表数据量中等、关联字段有索引的场景；
- 不想为一次 Lookup 引入额外计算引擎的 ETL 流程。

使用时建议注意：

1. **关联字段一定加索引**。批量 `IN` 查询虽然减少了往返次数，但没有索引仍然会慢；
2. **优先配置 `outputPrefix`**，避免同名字段覆盖；
3. **维度数据变化频繁时关闭缓存**；
4. **`batchSize` 不要盲目调大**，默认 500 已经能覆盖大多数场景；
5. **`INNER` 会丢数据**，订单、日志类主数据建议使用默认的 `LEFT`。

---

### 07 小结

跨库补数看起来只是“查一下、填进去”，但真正落地时会遇到批量性能、类型差异、字段冲突、空值处理、元数据同步等一系列问题。

`JDBCLookup` 把这些细节收敛进一个组件里：

> 选数据源、选表、选字段、配前缀，剩下的交给组件。

20 条订单，9 个客户，3 条 SQL，一次完成。

如果你也在 ETL 里写过类似的 N+1 查询，不妨试试把它换成一个组件。

---

### 关于 DataY

`JDBCLookup` 来自 DataY —— 一套**极致轻量的数据平台**，理念很简单：

> 数据平台可以很小，功能不打折。

传统数据平台往往需要 Hadoop、Spark、Flink、ClickHouse 一整套组件，几十台机器起步，运维门槛高。DataY 选择另一条路：

- **一个 JAR，内嵌 DuckDB**，拒绝组件地狱；
- **2 核 4G** 就能跑通数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用；
- 零依赖启动，10 分钟搭好开发环境；
- 调度 master 与执行 worker 可分可合，单机和集群同一套代码。

DataY 由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行或嵌入 Java 应用 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数 |

**核心能力：**

- 🔌 **数据集成**：MySQL / PostgreSQL / Oracle / SQL Server / SAP HANA / Doris / ClickHouse / DuckDB 等十余种数据源；
- 🔄 **四种 CDC**：MySQL Binlog、PostgreSQL WAL、Oracle LogMiner、MongoDB Change Streams；
- 🧩 **一套组件模型**：Source / Operator / Sink，批流共用；
- 🛡️ **断点续传**：增量水位、Binlog 位点、LSN、SCN、resumeToken 统一持久化；
- 🏗️ **自动建表**：跨库 DDL 转换，表不存在自动创建；
- 🚀 **内嵌 DuckDB**：SQL 加工不需要额外计算集群；
- ⏰ **任务调度**：Quartz Cron 精确到秒，DAG 拓扑编排，master/worker 可分离；
- 📦 **可嵌入**：Core 可以作为一个 Java 库，嵌进你自己的应用。

**快速开始：**

```bash
git clone https://github.com/yuyuejia/datay
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

也可以只用 Core：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

产品首页：<http://datay.yuyuejia.com.cn/>  
在线体验：<http://datay-demo.yuyuejia.com.cn/>  
项目地址：<https://github.com/yuyuejia/datay>

DataY 想做的，不是再堆一套大数据组件，而是把数据集成、建模、调度、数仓和问数，收进一个足够轻的平台上。