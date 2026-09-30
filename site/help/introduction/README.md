# 产品介绍

> DataY 是一套**极致轻量、高性能**的数据平台：一个 JAR、内嵌 DuckDB，在 **2 核 4G** 的机器上
> 就能跑通 **数据集成 → 数据建模 → 数据开发 → 任务调度 → 数据应用** 的完整链路。

DataY 由「一个引擎 + 一个平台」组成，两者既能单独使用，也能无缝拼成一套完整产品：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、可扩展、高性能、流批一体；内置 DuckDB，支持 JSON 配置定义任务、自动建表、CDC 实时同步 |
| **DataY Web** | 轻量数据平台 | 基于 DataY Core 构建，提供拖拽式任务设计、Quartz 任务调度、维度建模、指标管理、智能问数 |

- 深入了解引擎：[数据集成引擎（DataY Core）](introduction/core.md)

---

## 为什么选择 DataY

### 极致轻量

传统数据平台动辄十几台机器、上百 G 内存，部署复杂、运维成本高。DataY 反其道而行，
**用最极致的硬件利用，交付最完整的数据能力**。

| 传统方案 | DataY |
| --- | --- |
| 需要 Hadoop / Spark / Flink / ClickHouse / Doris 一整套 | 一个 JAR，内嵌 DuckDB，拒绝组件地狱 |
| 几十上百台机器起步 | **2 核 4G** 即可跑通数据集成 → 数仓 → 问数的完整链路 |
| 运维门槛高，需要专职大数据工程师 | 零依赖启动，10 分钟搭好开发环境 |

- **一个 JAR 启动**：Web 平台直接内嵌 Core 引擎，无需额外部署计算 / 存储组件
- **零依赖运行**：开发默认内嵌 H2，生产可换 MySQL；单机 `standalone` 即可跑通全链路
- **弹性伸缩**：调度 master 与执行 worker 可合并也可分离部署，需要时再扩，单机与集群同一套代码

### 高性能

- **内嵌 DuckDB**：列式存储 + 向量化执行 + 多线程，单机即可跑出分布式数仓的性能
- **流批一体**：同一套组件同时支持批处理与流式处理，按场景选择合适的处理模式
- **实时同步**：支持 MySQL Binlog / PostgreSQL 逻辑复制（WAL）CDC，实时捕获数据变更
- **高性能数仓**：支持 DuckLake（Iceberg 格式）构建低成本数据湖，必要时可外挂 Doris / ClickHouse

---

## 五大能力，一个平台

| 能力 | 说明 | 详细 |
| --- | --- | --- |
| 数据集成 | 十余种数据源，全量 / 增量 / CDC 实时同步，自动建表 | [使用指南](guide/integration.md) · [ETL 组件](components/README.md) |
| 数据建模 | 星型 / 雪花维度建模，时间 / 层级 / 普通维度，事实表关联 | [维度建模](guide/modeling.md) |
| 数据开发 | 拖拽画布编排 ETL，SQL / Shell / DAG 多类型任务 | [任务设计与开发](guide/development.md) |
| 任务调度 | Quartz Cron 调度、依赖调度、master/worker 分离部署 | [任务调度与实例](guide/scheduler.md) |
| 数据应用 | 智能问数、公共指标查询面板 | [智能问数](guide/askdata.md) |

### 平台功能模块

| 模块 | 说明 | 使用指南 |
| --- | --- | --- |
| 数据源 | 数据源连接管理、连接测试、schema / table / column 元数据浏览 | [数据源管理](guide/datasource.md) |
| ETL 任务 | 拖拽画布可视化设计 ETL 数据流，支持 Cron 调度配置、上线 / 下线 | [任务设计与开发](guide/development.md) |
| 数据同步 | 可视化选表完成库表同步，自动建表，支持全量 / 增量同步 | [数据集成与同步](guide/integration.md) |
| 任务定义 | 定义 SQL、Shell、DAG 工作流等类型任务 | [任务设计与开发](guide/development.md) |
| 任务实例 | 任务运行实例监控，支持实例停止 | [任务调度与实例](guide/scheduler.md) |
| 任务依赖 | 配置任务间的父子依赖关系，实现依赖调度 | [任务调度与实例](guide/scheduler.md) |
| 维度建模 | 普通 / 层级 / 时间维度，事实表关联维度 | [维度建模](guide/modeling.md) |
| 指标管理 | 原子指标、衍生指标、公共指标查询 | [指标管理](guide/metric.md) |
| 智能问数 | 自然语言问数，无需编写 SQL | [智能问数](guide/askdata.md) |

---

## 一条链路，覆盖数据全生命周期

```
数据集成 ──▶ 数据建模 ──▶ 数据开发 ──▶ 任务调度 ──▶ 数据应用
 ETL/CDC      维度/指标      画布/任务     Cron/DAG     问数/报表
```

从源数据接入、维度与指标建模、任务编排调度，到最终的自然语言问数，全部在平台内闭环，
无需在多个系统之间来回切换。

---

## 支持的数据源

- **关系型数据库**：MySQL、Oracle、PostgreSQL、SQL Server、MariaDB
- **分析数据库**：DuckDB、Doris、ClickHouse、GreenPlum
- **文件系统**：本地文件、MinIO 对象存储
- **CDC**：MySQL Binlog、PostgreSQL 逻辑复制（WAL / pgoutput）

---

## 技术架构

- **前端**：Vue 3 + Vite + TypeScript + Element Plus + Vue Flow
- **后端**：Spring Boot 3.4 + Java 17 + Spring Security(JWT) + JPA/Hibernate
- **调度引擎**：Quartz
- **数据处理引擎**：DataY Core（内置 DuckDB）
- **数据库**：开发环境默认 H2，生产环境可配置 MySQL
- **项目管理**：基于 JHipster 8.11.0 生成，模块间通过 Maven 管理

### 调度部署模式

调度系统由 **master（调度）** 与 **worker（执行）** 两类角色组成，通过 `development.mode`
声明当前进程承担的角色：`standalone` 单机即可运行，也可拆成 `master` / `worker` 分离部署横向扩展。
详细的角色职责与容器编排见 [任务调度与实例](guide/scheduler.md?id=调度部署模式)。

### 监控与日志

- 任务实例状态全程跟踪：APPENDING / RUNNING / WAITING / SUCCESSFUL / FAILED / TIMEOUT / INTERRUPTED
- 记录执行节点（`IP:端口`）、开始 / 结束时间、执行消息
- Spring Boot Actuator 管理端点：`/management/*`（健康检查、指标、日志）

---

## 下一步

- [快速开始](quickstart/README.md)：10 分钟把平台跑起来
- [使用指南](guide/README.md)：按功能模块逐个上手
- [场景案例](cases/README.md)：跟着真实场景跑通全链路
- [数据集成引擎（DataY Core）](introduction/core.md)：了解底层引擎与嵌入方式

<img src="../images/datay.jpg" width="300" height="400" alt="DataY" />
