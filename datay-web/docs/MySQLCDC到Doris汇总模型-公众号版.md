# MySQL CDC 直接写进 Doris 汇总模型：同步即汇总，明细不用搬两遍

> 很多实时链路是这样做的：先把 MySQL 明细一条条同步到 Doris，再起一个定时任务做 `GROUP BY`，把结果写进汇总表。
>
> 链路长、延迟高、还多一份明细存储。
>
> 这次我们换一种做法：**CDC 进来什么，Doris 在写入时就直接汇总掉。**

---

## 一、为什么要把“汇总”放到写入时做

先看一个很常见的实时大屏需求：

- 按「日期 + 商品」看订单件数和销售金额；
- 数据要准到秒，不能等 T+1；
- 明细表每天几百万行，但看板只关心几十个维度组合；
- 最好别再维护一套定时聚合任务。

传统做法分两步：

```text
MySQL 明细 ──CDC──> Doris 明细表 ──定时 GROUP BY──> Doris 汇总表 ──> 看板
```

问题也很明显：

1. **多存一份明细**：Doris 里既有明细又有汇总，存储翻倍；
2. **多一段延迟**：汇总任务没跑完，看板就是旧数据；
3. **多一套任务**：调度、补偿、口径维护，都是成本；
4. **明细越大越慢**：每次聚合都要扫一遍新增明细。

而 Doris 的**聚合模型（Aggregate Key）**，天生就是为这件事准备的：

```text
MySQL 明细 ──CDC──> Doris 聚合模型
                       ↑
              写入时自动按 Key 合并
```

同一个 Key 的多行数据，在写入阶段就被 `SUM` / `REPLACE` 掉。查询时看到的已经是汇总结果，不需要再扫明细。

这篇文章做一次完整验证：

- 源端：MySQL 8.0，开启 ROW 模式 Binlog；
- 链路：DataY 的 `MySQLBinlogInput` → `DorisStreamLoad`；
- 目标端：Doris 2.1，`AGGREGATE KEY` 汇总模型；
- 验证内容：全量快照、实时增量、批量写入，三条路径是否都能自动汇总。

---

## 二、先看结果：1010 行明细，Doris 里只剩 17 行

先把最终结果放出来。

源表 MySQL `order_detail`：

```text
source_rows   agg_keys   source_qty   source_amount
1010          17         1018         105432.00
```

Doris 聚合表 `order_agg`：

```text
agg_rows   total_qty   total_amount
17         1018        105432.00
```

1010 行明细，按「日期 + 商品」聚合后只剩 17 行；件数和金额与源端**完全一致**。

其中 10 个批量商品，每个商品写了 100 行，Doris 里每个商品只剩 1 行：

```text
stat_date    product_id   product_name   order_cnt   sales_amount
2026-10-27   2001         批量商品2001    100         9990.00
2026-10-27   2002         批量商品2002    100         9990.00
...
2026-10-27   2010         批量商品2010    100         9990.00
```

这就是聚合模型的价值：**汇总不是查出来的，是写进去的。**

---

## 三、Doris 聚合模型：Key 相同就自动合并

先建目标表：

```sql
CREATE DATABASE IF NOT EXISTS cdc_demo;

CREATE TABLE cdc_demo.order_agg (
  stat_date     DATE          NOT NULL COMMENT '统计日期',
  product_id    INT           NOT NULL COMMENT '商品ID',
  product_name  VARCHAR(100)  REPLACE  COMMENT '商品名称',
  category      VARCHAR(50)   REPLACE  COMMENT '品类',
  order_cnt     BIGINT        SUM DEFAULT '0' COMMENT '订单件数',
  sales_amount  DECIMAL(18,2) SUM DEFAULT '0' COMMENT '销售金额',
  last_update   DATETIME      REPLACE  COMMENT '最后更新时间'
)
AGGREGATE KEY(stat_date, product_id)
DISTRIBUTED BY HASH(product_id) BUCKETS 3
PROPERTIES ('replication_num' = '1');
```

三个关键点：

1. `AGGREGATE KEY(stat_date, product_id)`
   这两列是聚合维度。相同「日期 + 商品」的行，会被合并成一行。

2. `SUM`
   指标列用 `SUM`。每来一批数据，就在已有值上累加。

3. `REPLACE`
   维度列用 `REPLACE`。同一个 Key 再来一行时，用最新值覆盖旧值。
   所以 `last_update` 会自动保留最后一次变更时间。

如果用 `DESC` 看表结构，会看到每个非 Key 列都带着聚合方式：

```text
Field          Type            Key    Extra
stat_date      DATE            true   NULL
product_id     INT             true   NULL
product_name   VARCHAR(100)    false  REPLACE
category       VARCHAR(50)     false  REPLACE
order_cnt      BIGINT          false  SUM
sales_amount   DECIMAL(18,2)   false  SUM
last_update    DATETIME        false  REPLACE
```

注意：**聚合模型要求先建表**。

DataY 的 `DorisStreamLoad` 支持目标表不存在时自动建表，但自动建出来的是按源表结构映射的普通模型，不会自动变成 `AGGREGATE KEY`。想做写入时汇总，目标表要提前按业务口径建好。

---

## 四、源表与测试数据

MySQL 源表保持“订单流水”的形态，一行代表一次销售：

```sql
CREATE DATABASE cdc_demo DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE cdc_demo.order_detail (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date     DATE          NOT NULL,
  product_id    INT           NOT NULL,
  product_name  VARCHAR(100),
  category      VARCHAR(50),
  order_cnt     INT,
  sales_amount  DECIMAL(10,2),
  last_update   DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

写入 7 条初始数据：

```sql
INSERT INTO order_detail
  (stat_date, product_id, product_name, category, order_cnt, sales_amount, last_update)
VALUES
('2026-10-25', 1001, '智能音箱 Pro', '数码', 1, 399.00, '2026-10-25 10:00:00'),
('2026-10-25', 1002, '机械键盘',     '数码', 2, 598.00, '2026-10-25 10:05:00'),
('2026-10-25', 1001, '智能音箱 Pro', '数码', 1, 399.00, '2026-10-25 11:00:00'),
('2026-10-25', 1003, '手冲咖啡壶',   '家居', 3, 267.00, '2026-10-25 12:00:00'),
('2026-10-26', 1001, '智能音箱 Pro', '数码', 2, 798.00, '2026-10-26 09:00:00'),
('2026-10-26', 1002, '机械键盘',     '数码', 1, 299.00, '2026-10-26 09:30:00'),
('2026-10-26', 1003, '手冲咖啡壶',   '家居', 2, 178.00, '2026-10-26 10:00:00');
```

这 7 行里，`2026-10-25 + 1001` 出现了两次。源端是两行，Doris 里应该只剩一行，件数变成 2，金额变成 798。

这就是第一个验证点。

---

## 五、DataY 任务：一个 CDC 输入，一个 Doris 输出

任务配置只用了两个组件：

```json
{
  "units": [
    {
      ".id": "mysql_cdc",
      ".name": "MySQLBinlogInput",
      "datasource": {
        "type": "mysql",
        "url": "jdbc:mysql://127.0.0.1:3306/cdc_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "******",
        "dbschema": "cdc_demo"
      },
      "databaseNamePattern": "cdc_demo",
      "tableNamePattern": "order_detail",
      "serverId": 1234,
      "snapshot": true
    },
    {
      ".id": "doris_sink",
      ".name": "DorisStreamLoad",
      "datasource": {
        "type": "Doris",
        "url": "jdbc:mysql://127.0.0.1:9030/cdc_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "******",
        "dbschema": "cdc_demo",
        "extraParams": {
          "fe_endpoint": "http://127.0.0.1:8030"
        }
      },
      "schema": "cdc_demo",
      "table": "order_agg",
      "model": "insert"
    }
  ],
  "connections": [
    { "sourceId": "mysql_cdc", "targetId": "doris_sink" }
  ]
}
```

几个参数值得单独说：

| 参数 | 作用 |
| --- | --- |
| `snapshot: true` | 第一次运行时先读历史全量，再从当前 Binlog 位点接增量 |
| `databaseNamePattern` | 只采集 `cdc_demo` 库 |
| `tableNamePattern` | 只采集 `order_detail` 表 |
| `serverId` | 伪装成从库的 ID，同一 MySQL 实例多个任务要互不相同 |
| `fe_endpoint` | Doris FE 的 HTTP 地址，Stream Load 走这个入口 |
| `model: insert` | 以 INSERT 语义写入；聚合模型会自动完成合并 |

启动方式：

```bash
java -jar datay-core-1.0.4-jar-with-dependencies.jar cdc-task.json
```

日志里会看到两个阶段：

```text
开始读取历史全量数据（snapshot），起始位点 mysql-bin.000003:6464
历史全量数据读取完成，表 cdc_demo.order_detail，共 7 条
历史全量数据读取完成，启动 CDC 增量同步
已成功写入 7 条记录到表 order_agg
```

注意最后一句：DataY 发送的是 **7 条记录**，但 Doris 表里只会出现 6 行。合并发生在 Doris 的写入路径上。

---

## 六、验证一：全量快照也能直接汇总

全量读完，查 Doris：

```sql
SELECT stat_date, product_id, product_name, category,
       order_cnt, sales_amount, last_update
FROM cdc_demo.order_agg
ORDER BY stat_date, product_id;
```

结果：

```text
2026-10-25  1001  智能音箱 Pro  数码  2   798.00   2026-10-25 11:00:00
2026-10-25  1002  机械键盘      数码  2   598.00   2026-10-25 10:05:00
2026-10-25  1003  手冲咖啡壶    家居  3   267.00   2026-10-25 12:00:00
2026-10-26  1001  智能音箱 Pro  数码  2   798.00   2026-10-26 09:00:00
2026-10-26  1002  机械键盘      数码  1   299.00   2026-10-26 09:30:00
2026-10-26  1003  手冲咖啡壶    家居  2   178.00   2026-10-26 10:00:00
```

对比源端 7 行：

- `2026-10-25 + 1001` 的两次记录合并成一行；
- `order_cnt` 从 1 + 1 变成 2；
- `sales_amount` 从 399 + 399 变成 798；
- `last_update` 用 `REPLACE` 保留了更晚的 11:00；
- 总行数从 7 变成 6。

聚合结果：

```text
agg_rows   total_qty   total_amount
6          12          2938.00
```

源端同样按 `stat_date, product_id` 聚合，结果完全一致。

---

## 七、验证二：Binlog 增量进来，自动累加到已有 Key

接下来模拟实时订单，往 MySQL 再插 3 行：

```sql
INSERT INTO order_detail
  (stat_date, product_id, product_name, category, order_cnt, sales_amount, last_update)
VALUES
('2026-10-25', 1001, '智能音箱 Pro', '数码', 3, 1197.00, '2026-10-25 18:00:00'),
('2026-10-26', 1001, '智能音箱 Pro', '数码', 1,  399.00, '2026-10-26 20:00:00'),
('2026-10-27', 1004, '降噪耳机',     '数码', 2,  998.00, '2026-10-27 10:00:00');
```

几秒钟后，DataY 日志出现：

```text
已成功写入 3 条记录到表 order_agg
组件DorisStreamLoad状态保存成功：
{mysql_cdc.binlogFile=mysql-bin.000003, mysql_cdc.binlogPosition=6711}
```

再次查询 Doris：

```text
2026-10-25  1001  智能音箱 Pro  数码  5   1995.00  2026-10-25 18:00:00
2026-10-26  1001  智能音箱 Pro  数码  3   1197.00  2026-10-26 20:00:00
2026-10-27  1004  降噪耳机      数码  2    998.00  2026-10-27 10:00:00
```

发生了三件事：

1. `2026-10-25 + 1001` 从 `2 件 / 798 元` 累加为 `5 件 / 1995 元`；
2. `2026-10-26 + 1001` 从 `2 件 / 798 元` 累加为 `3 件 / 1197 元`；
3. `2026-10-27 + 1004` 是新的 Key，直接新增一行。

源端此时 10 行，Doris 里 7 行：

```text
source: 10 rows, qty 18, amount 5532.00
doris :  7 rows, qty 18, amount 5532.00
```

增量路径同样没有丢数，也没有重复累加。

---

## 八、验证三：1000 行批量写入，仍然只留 10 个 Key

最后做一次批量压力测试：一次事务写入 1000 行，覆盖 10 个商品。

```text
source_rows   source_qty   source_amount
1010          1018         105432.00
```

DataY 侧：

```text
已成功写入 1000 条记录到表 order_agg
```

Doris 侧：

```text
agg_rows   total_qty   total_amount
17         1018        105432.00
```

1000 行明细只产生 10 个聚合 Key，每个商品正好 100 行：

```text
2026-10-27  2001  批量商品2001  100  9990.00
2026-10-27  2002  批量商品2002  100  9990.00
...
2026-10-27  2010  批量商品2010  100  9990.00
```

到这里，三条路径都验证完了：

| 场景 | 源端行数 | Doris 行数 | 件数 | 金额 |
| --- | ---: | ---: | ---: | ---: |
| 全量快照 | 7 | 6 | 12 | 2938.00 |
| 增量 3 行 | 10 | 7 | 18 | 5532.00 |
| 批量 1000 行 | 1010 | 17 | 1018 | 105432.00 |

每一阶段，Doris 的 `SUM` 结果都与源端 `GROUP BY` 完全一致。

---

## 九、这次验证踩到的三个细节

验证过程并不是一次就跑通的，有三个细节值得记录。

### 1. 目标表列名要对齐

第一次建表时，源表叫 `qty`、`amount`、`updated_at`，目标表叫 `order_cnt`、`sales_amount`、`last_update`。

结果 Key 列和维度列都进去了，但指标列全是 0。

原因很简单：Stream Load 按 JSON 字段名找目标列，名字对不上就取默认值。

解决方式有两种：

- 源表和目标表使用一致的列名；
- 或者在中间加一个 `StreamSQL` 做字段改名。

实时链路里，建议把这一步固化成建表规范，不要靠“应该能对上”。


### 2. 聚合模型适合“追加”，不适合“整行更新”

这次验证的是订单流水：每次销售都是新增一行，天然适合 `SUM`。

如果源表会发生 `UPDATE`，比如订单金额被修改，那么再把整行写进聚合模型就会重复累加：

```text
原值 100，UPDATE 后还是 100
聚合模型再 SUM 一次 → 200
```

这类场景应该：

- 用 Doris 的 **Unique Key 模型**做整行覆盖；
- 或者把更新转换成“冲正 + 新增”两条流水，再进入聚合模型。

**模型选型比同步工具更重要。** 聚合模型不是万能的，它适合的是“只追加、按 Key 汇总”的事实流。

---

## 十、什么时候该用这套组合

比较适合：

- 订单、支付、埋点、日志等**追加型事实流**；
- 看板只关心固定维度的 `SUM` / `COUNT` / `MAX` / `MIN`；
- 明细量大，但聚合后的 Key 数量可控；
- 希望省掉定时聚合任务，降低延迟和存储。

不太适合：

- 需要保留每一行明细；
- 源端频繁 `UPDATE` / `DELETE`；
- 聚合 Key 基数接近明细行数（那样聚合没有意义）；
- 需要精确回溯某一条原始记录。

---

## 十一、写在最后

这次验证的核心结论只有一句话：

> **MySQL CDC 负责“把变化送过来”，Doris 聚合模型负责“在落库时把账算好”。**

DataY 在这一链路里做的是三件事：

1. 用 `MySQLBinlogInput` 做全量快照 + Binlog 增量，断点可恢复；
2. 用 `DorisStreamLoad` 走 Stream Load 协议批量写入；
3. 写入前按目标列类型做规范化，保证字符、日期、数值不变形。

最终效果：

- 1010 行明细 → 17 行汇总；
- 件数、金额与源端逐项对齐；
- 查询侧不用再跑 `GROUP BY`，看板直接读汇总表；
- 少一份明细存储，少一套聚合任务，少一段 T+1 延迟。

如果你的实时看板也在“先同步明细、再定时汇总”，可以试试把汇总前移到写入阶段。

很多时候，链路不是不够长，而是**该在入口算的账，被拖到了出口**。

---

## 十二、关于 DataY

上面这条链路里的 `MySQLBinlogInput` 和 `DorisStreamLoad`，都来自 DataY。

DataY 是一套**开源、轻量的一体化数据平台**，理念很简单：

> 数据平台可以很小，功能不打折。

传统数据平台往往需要 Hadoop、Spark、Flink、ClickHouse 一整套组件，几十台机器起步，运维门槛高。DataY 选择另一条路：

- **一个 JAR，自研流批一体处理引擎**，拒绝组件地狱；
- **2 核 4G** 就能跑通数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用；
- 零依赖启动，10 分钟搭好开发环境；
- 调度 master 与执行 worker 可分可合，单机和集群同一套代码；
- 内嵌 DuckDB 作为高性能分析引擎，同时支持 Doris、ClickHouse 等外部分析引擎。

这里的“轻量”指的是**运行资源门槛低**，不是能力轻量。

DataY 由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、流批一体；JSON 定义任务、自动建表、CDC 实时同步；可脱离 Web 独立运行或嵌入 Java 应用 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数 |

和本文这条实时链路相关的能力：

- 🔌 **多数据源**：MySQL / PostgreSQL / Oracle / SQL Server / SAP HANA / Doris / ClickHouse / DuckDB 等二十余种数据源；
- 🔄 **四种 CDC**：MySQL Binlog、PostgreSQL WAL、Oracle LogMiner、MongoDB Change Streams；
- 🧩 **统一组件模型**：Source / Operator / Sink，批流共用；
- 🛡️ **断点续传**：Binlog 位点、LSN、SCN、resumeToken 统一持久化；
- 🏗️ **自动建表**：跨库 DDL 转换，表不存在时自动创建；
- 🚀 **Doris 流式写入**：内置 `DorisStreamLoad`，支持 insert / delete / overwrite；
- 🧮 **流内计算**：StreamSQL、Join、Lookup、DataTransform，同步路上顺手把数据加工好；
- ⏰ **任务调度**：Quartz Cron 精确到秒，DAG 拓扑编排，master/worker 可分离；
- 🤖 **MCP Harness**：把数据源、数据服务、ETL 任务、数据模型暴露给 AI Agent，让 Agent 直接操作平台对象；
- 📦 **可嵌入**：Core 可以作为 Java 库，嵌进你自己的应用。

**快速开始：**

```bash
git clone https://gitee.com/yuyuejia/datay.git
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

也可以只用 Core 跑单个任务：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

产品首页：<http://datay.yuyuejia.com.cn/>  
在线体验：<http://datay-demo.yuyuejia.com.cn/>  
项目地址：<https://gitee.com/yuyuejia/datay>

DataY 想做的，不是再堆一套大数据组件，而是把数据集成、建模、调度、数仓和问数，收进一个足够轻的平台上。

---

*验证环境：DataY 1.0.4 / MySQL 8.0.46 / Apache Doris 2.1.0 / MySQL Connector/J 8.0.33。*
