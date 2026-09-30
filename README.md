# DataY · 轻量数据平台

> 数据平台可以很小，功能不打折。一套 DataY 跑起来，数据集成、维度建模、任务调度、高性能数仓、智能问数 —— 全有。

🌐 **产品首页**: [http://datay.yuyuejia.com.cn/](http://datay.yuyuejia.com.cn/)

## 为什么是 DataY？

传统数据平台动辄十几台机器、上百 G 内存，部署复杂、运维成本高。DataY 的理念是：**用最极致的技术栈精简，交付最完整的数据能力。**

| 传统方案                             | DataY |
|----------------------------------| --- |
| 需要 Hadoop / Spark / 分布式调度等多套系统组件 | 一个 JAR，内嵌 DuckDB，拒绝组件地狱 |
| 需要多台服务器起步                        | **2 核 4G** 即可跑通数据集成 → 数仓 → 问数的完整链路 |
| 技术栈多，运维门槛高，需要专职大数据工程师            | 零依赖启动，10 分钟搭好开发环境 |

DataY 不是某个单点工具，而是覆盖数据全链路的**一体化数据平台**：

```
数据集成 ──▶ 维度建模 ──▶ 任务调度 ──▶ 高性能数仓 ──▶ 智能问数
  ETL/CDC     星型/雪花     Cron/DAG      DuckDB      自然语言
```

## 项目结构

DataY 分两个子模块，Core 负责引擎，Web 负责平台产品：

```
datay/
├── pom.xml          # Maven 父模块
├── datay-core/      # 轻量、高性能、流批一体的数据集成引擎
├── datay-web/       # 可视化数据平台（任务设计 + 调度 + 数仓 + 问数）
└── site/            # 产品主页 + 帮助文档
```

## 五大能力，一个平台

### 🔌 数据集成（Data Integration）

- 支持 MySQL / PostgreSQL / Oracle / Doris / ClickHouse / DuckDB 等十余种数据源
- 可视化拖拽画布编排 ETL 数据流，也可 JSON 配置文件一键跑任务
- 支持全量同步、增量同步、MySQL CDC / PostgreSQL CDC 实时同步
- 自动建表（DDL 转换），源表改字段自动同步到目标

### 📐 维度建模（Dimensional Modeling）

- 星型 / 雪花模型：可视化定义事实表与维度表的关联，沉淀业务语义层
- 三种维度类型：普通维度、层级维度（多级自动生成 `levelN_id/name`）、时间维度（年/季/月/日粒度 + 预置日期数据）
- 事实表外键字段一键关联维度，指定时间周期字段，指标自动获得业务语义
- 维度 / 事实模型支持物化建表，数据直接落地到内嵌 DuckDB
- 为指标管理与智能问数提供统一的维度体系，指标口径不跑偏

### ⏰ 任务调度（Job Scheduler）

- Cron 定时调度，Quartz 引擎精准到秒
- DAG 工作流编排，父子任务自动拓扑排序
- 支持手动触发、暂停/恢复、失败重试
- 调度 master 与执行 worker 可分离部署，单机就能跑，集群也能扩

### 🚀 高性能数仓（High-Performance Warehouse）

- **内嵌 DuckDB**，列存 + 向量化 + 无锁并发，单机就能跑出分布式数仓的性能
- 与维度建模、指标管理打通，原子指标 / 衍生指标统一口径
- 支持 Doris / ClickHouse 流式加载，需要大规模查询可随时外挂
- 支持 DuckLake（Iceberg 格式），低成本构建数据湖

### 🤖 智能问数（AI Data Agent）

- 自然语言 → 指标 → 维度 → 时间过滤 → 取数，**一句话拿报表**
- RAG 本地向量索引，指标口径不跑偏
- 指标管理模块统一维护业务口径，问数有依据


## 环境要求

- Java 17+
- Maven 3.6+
- Node 22+（构建 DataY Web 前端）

## 一键部署

```bash
# 1. 克隆仓库
git clone https://cnb.cool/yuyuejia/datay

# 2. 构建项目
cd datay
mvn clean package -DskipTests

# 3. 启动（单机模式，调度+执行合一），AI 能力需要提供 AI API Key，默认为 Deepseek 原厂Key
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx

# 4. 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

在线演示地址：[http://datay-demo.yuyuejia.com.cn/](http://datay-demo.yuyuejia.com.cn/)

---

## Core 独立运行（嵌入 / CLI 模式）

```bash
# JSON 定义任务，直接跑
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

```json
{
  "units": [
    { ".name": "StreamJdbcInput", "table": "orders" },
    { ".name": "StreamJdbcOutput", "table": "orders_sync" }
  ],
  "connections": [{ "sourceId": "...", "targetId": "..." }]
}
```

## 常用场景案例

- [Mysql同步到Mysql](datay-core/docs/example/mysql-to-mysql.md)
- [Http同步到Mysql](datay-core/docs/example/http-to-mysql.md)
- [飞书多维表格同步到Mysql](datay-core/docs/example/feishu-bitable-to-mysql.md)
- [Mysql同步到飞书多维表格](datay-core/docs/example/mysql-to-feishu-bitable.md)
- [Mysql CDC实时同步到Mysql](datay-core/docs/example/mysqlCDC-mysql.md)
- [MySQL CDC实时同步到DuckDB](datay-core/docs/example/mysqlCDC-duckdb.md)
- [MySQL CDC实时同步到DuckLake](datay-core/docs/example/mysqlCDC-ducklake.md)
- [PostgreSQL CDC实时同步到DuckDB](datay-core/docs/example/postgresCDC-duckdb.md)
- [PostgreSQL CDC实时同步到DuckLake](datay-core/docs/example/postgresCDC-ducklake.md)
- [Mysql同步到Apache Doris](datay-core/docs/example/mysql-doris.md)
- [Mysql同步到DuckLake](datay-core/docs/example/mysql-ducklake.md)
- [使用DuckDB SQL进行数据处理](datay-core/docs/example/mysql-sqlunit-mysql.md)

## 支持的数据源

- **关系型数据库**: MySQL, Oracle, PostgreSQL, SQL Server, MariaDB
- **分析数据库**: DuckDB, Doris, ClickHouse, GreenPlum
- **文件系统**: 本地文件、MinIO对象存储
- **CDC**: MySQL Binlog, PostgreSQL 逻辑复制（WAL / pgoutput）

## 联系方式

有问题或建议，请通过项目Issue或加微信号进行反馈，我们会尽快回复您。

<img src="datay-core/docs/images/weixin.png" width="300" height="300" alt="DataY">