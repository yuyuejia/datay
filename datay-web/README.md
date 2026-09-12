# DataY Web - 基于 DataY Core 的数据平台

## 项目简介

DataY Web 是基于 [DataY Core](../datay-core/README.md) 数据集成引擎构建的数据平台产品，提供可视化的**任务设计**和**任务调度**能力。

- **任务设计**: 通过拖拽画布可视化编排 ETL 数据流，支持数据同步任务的可视化配置，同时提供 SQL、Shell、DAG 等多类型任务定义
- **任务调度**: 基于 Quartz 提供 Cron 定时调度、任务依赖调度、手动触发执行，并对任务实例进行全生命周期监控
- **底层引擎**: 直接嵌入 DataY Core 执行引擎，复用其 DuckDB 引擎、丰富的 ETL 组件以及高性能的数据处理能力

## 核心特性

- **可视化任务设计**: 基于 Vue Flow 的拖拽式 ETL 任务画布，节点连线编排数据流，双击节点即可配置组件参数
- **丰富的组件库**: 集成 DataY Core 的输入、处理、输出、路由组件，支持 MySQL Binlog CDC、DuckDB SQL 处理、Doris 流式加载等
- **多类型任务**: 支持 ETL、SQL、Shell、DAG 工作流、Demo 等多种任务类型
- **DAG 工作流编排**: 支持任务间依赖关系，执行时自动拓扑排序，按序调度子任务
- **定时调度**: Quartz Cron 表达式定时调度，支持任务上线/下线动态管理调度
- **依赖调度**: 配置任务依赖后，父任务成功后自动触发子任务，依赖未满足时任务自动进入等待状态并轮询恢复
- **数据同步**: 可视化选择同步表，自动完成目标表建表(DDL 转换)，支持全量/增量同步
- **任务实例监控**: 实例状态、执行节点、开始/结束时间、执行消息全流程跟踪，支持运行中任务终止
- **多数据源管理**: 统一管理数据源连接，支持连接测试与 schema/table/column 元数据浏览
- **调度解耦部署**: 调度 master 与执行 worker 可独立部署，也可合并部署，基于 Redis 队列进行分布式任务分发与 Leader 选举
- **AI SQL 助手**: 在数据查询页用自然语言生成 SQL，支持工具注册（function calling）与 Agent 循环，自动查询库表元数据并校验 SQL

## 系统架构

- **前端**: Vue 3 + Vite + TypeScript + Element Plus + Vue Flow
- **后端**: Spring Boot 3.4 + Java 17 + Spring Security(JWT) + JPA/Hibernate
- **调度引擎**: Quartz
- **数据处理引擎**: DataY Core（内置 DuckDB）
- **数据库**: 开发环境默认 H2，生产环境可配置 MySQL（参见 `src/main/docker/`）
- **项目管理**: 基于 JHipster 8.11.0 生成，模块间通过 Maven 管理

## 代码构建

### 环境要求

- Java 17+
- Maven 3.6+
- Node 22+（前端构建）

### 构建项目

DataY Web 依赖 datay-core，需先安装到本地仓库：

```bash
# 在项目根目录执行，先构建 datay-core
mvn -pl datay-core clean install -DskipTests
# 构建 datay-web
mvn -pl datay-web clean package
```

### 开发模式

后端与前端分别启动（两个终端）：

```bash
./mvnw
./npmw start
```

后端运行在 `http://localhost:8080`，前端 Vite 开发服务器运行在 `http://localhost:9000`（已代理到后端），修改代码浏览器自动刷新。

### 生产构建

```bash
./mvnw -Pprod clean verify
java -jar target/*.jar
```

访问 `http://localhost:8080`。

## 快速开始

开发模式默认账号：`admin / admin`

1. **配置数据源**: 进入「数据源」页面，添加源/目标数据源并测试连接
2. **设计任务**: 进入「ETL 任务」页面新建任务，从组件面板拖拽节点、连线编排数据流，双击节点配置参数
3. **配置调度**: 点击画布上方「调度」按钮，通过 Cron 表达式选择器设置定时规则并保存任务
4. **上线任务**: 任务列表点击「上线」，任务即加入调度
5. **查看实例**: 进入「任务实例」页面查看任务运行状态与执行信息

## 功能模块

| 模块 | 说明 |
| --- | --- |
| 数据源 | 数据源连接管理、连接测试、schema/table/column 元数据浏览 |
| ETL 任务 | 拖拽画布可视化设计 ETL 数据流，支持 Cron 调度配置、上线/下线 |
| 数据同步 | 可视化选表完成库表同步，自动建表，支持全量/增量同步 |
| 任务定义 | 定义 SQL、Shell、DAG 工作流等类型任务 |
| 任务实例 | 任务运行实例监控，支持实例停止 |
| 任务依赖 | 配置任务间的父子依赖关系，实现依赖调度 |
| AI SQL 助手 | 数据查询页内置，自然语言转 SQL，支持工具注册与多轮 Agent 循环 |

## 任务设计

### ETL 可视化设计

- 从组件面板拖拽组件节点到画布，通过连线编排数据流
- 双击节点打开配置弹窗，配置组件参数
- 通过 Cron 表达式选择器配置调度规则
- 保存后自动生成 DataY Core 可执行的任务 JSON（units + connections），由 DataY Core 引擎执行

支持的 ETL 组件（继承自 DataY Core，详见 [datay-core 组件文档](../datay-core/docs/component/)）：

- **输入组件**: `StreamJdbcInput`、`JdbcInput`、`MySQLBinlogInput`
- **处理组件**: `DuckDBSql`、`StreamSqlUnit`、`JavaScriptComponent`
- **输出组件**: `StreamJdbcOutput`、`DuckDBWrite`、`DuckLakeWrite`、`DorisStreamLoad`、`ModelWrite`
- **其他组件**: `GenerateFlowFile`、`LogFlowFile`

### 任务类型

| 类型 | 说明 |
| --- | --- |
| ETL | 基于 DataY Core 引擎的数据集成任务 |
| SQL | 执行 SQL 脚本任务 |
| Shell | 执行 Shell 脚本任务 |
| DAG | DAG 工作流编排任务，按依赖关系拓扑排序执行 |
| DEMO | 演示任务 |

## 任务调度

- **Cron 定时调度**: 基于 Quartz，任务上线后自动注册调度，支持动态上下线
- **依赖调度**: 通过任务依赖(JobDepend)配置父子关系，父任务成功后触发子任务；依赖未满足时实例进入 WAITING 状态，由 WaitingJobQuartzTask 定时轮询恢复
- **手动触发**: 支持任务立即执行一次（RUN）

### 调度部署模式

调度系统由 **master（调度）** 与 **worker（执行）** 两类角色组成，通过 `development.mode` 配置项控制当前进程承担的角色：

| 模式 | 说明 | 调度能力 | 执行能力 | 依赖 Redis |
| --- | --- | --- | --- | --- |
| `standalone` | 单机部署，调度与执行合一（默认） | 有 | 有 | 否 |
| `master` | master 独立部署，只负责调度，不执行任务 | 有 | 无 | 是 |
| `worker` | worker 独立部署，只负责执行任务，不参与调度 | 无 | 有 | 是 |
| `cluster` | master 与 worker 合并部署（集群能力单进程） | 有 | 有 | 是 |

角色职责划分：

- **master**：初始化 Quartz 调度、维护任务上线/下线、接收 worker 上报的任务状态并落库，通过 Redis 队列下发待执行任务
- **worker**：从 Redis 队列消费待执行任务并执行，上报执行状态与日志，不初始化 Quartz 调度
- 多 master 部署时通过 Redis 分布式锁进行 Leader 选举，只有 Leader 负责调度，保证调度一致性

启动方式：

```bash
# 单机部署（默认，无需 Redis）
java -jar target/*.jar --spring.profiles.active=prod,standalone

# master 独立部署（仅调度，可多副本由 Leader 选举保证唯一性）
java -jar target/*.jar --spring.profiles.active=prod,master

# worker 独立部署（仅执行，可水平扩展提升吞吐）
java -jar target/*.jar --spring.profiles.active=prod,worker

# master + worker 合并部署（集群能力，单进程同时具备调度与执行）
java -jar target/*.jar --spring.profiles.active=prod,cluster
```

集群模式（`master` / `worker` / `cluster`）需要配置 Redis：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ''
      database: 0
```

也可以通过环境变量配置：`SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` / `SPRING_DATA_REDIS_PASSWORD`，
以及部署模式 `DEVELOPMENT_MODE`。

master / worker 分离部署的容器编排参考示例见 [`src/main/docker/scheduler-cluster.yml`](src/main/docker/scheduler-cluster.yml)：

```bash
# 启动 master 与 worker
docker compose -f src/main/docker/scheduler-cluster.yml up -d
# worker 横向扩展
docker compose -f src/main/docker/scheduler-cluster.yml up -d --scale worker=3
```

## AI SQL 助手

数据查询页提供 AI 助手入口，用户用自然语言描述需求，助手自动生成可直接执行的 SQL。

### 工作方式：工具注册 + Agent 循环

助手不是「一次性提问」，而是走标准的 **function calling loop**：

1. 把用户需求与**已注册工具清单**一起下发给模型；
2. 模型判断信息不足时，会请求调用工具，例如先查有哪些表、哪些字段；
3. 平台在本进程真实执行工具（连到目标数据源查元数据 / 校验 SQL），把结果回填给模型；
4. 模型基于真实结果继续推理，**循环重复上述过程**，直到不再请求工具、直接给出最终 SQL；
5. 若循环次数达到 `max-tool-rounds` 上限仍未收敛，则关闭工具并追加收敛指令，**强制模型输出最终 SQL**。

好处是生成的 SQL 表名、字段名全部来自真实元数据，避免模型凭空编造；同时按需查询 schema，比一次性把整库结构塞进提示词更省 token。

### 内置工具

| 工具名 | 说明 | 属性 |
| --- | --- | --- |
| `list_database_objects` | 按 `schemas` → `tables` → `columns` 逐级查询数据库结构元数据 | 只读 |
| `validate_sql` | 用数据库真实解析器预校验 SQL，失败返回报错信息供模型自修 | 只读 |
| `preview_sql_result` | 小样本试跑 SELECT（外层强制 LIMIT），确认查询口径 | 只读 |

工具默认只读，具备副作用的写工具需要显式开启 `allow-mutating-tools` 才会暴露给模型。

### 扩展新工具

实现 `com.data.datafusion.ai.tool.AiTool` 接口，打上 `@Component` 即可，注册中心会自动收集，无需改动任何中心化代码：

```java
@Component
public class MyTool implements AiTool {
    @Override public String name() { return "my_tool"; }
    @Override public String description() { return "工具用途说明，供模型判断何时调用"; }
    @Override public Map<String, Object> parametersSchema() { /* JSON Schema */ }
    @Override public Object execute(Map<String, Object> args, AiToolContext ctx) { /* ... */ }
}
```

### 配置

```yaml
datay:
  ai:
    enabled: true
    base-url: https://api.deepseek.com/v1   # 兼容 OpenAI Chat Completions 协议
    api-key: sk-xxxx
    model: deepseek-chat
    max-tool-rounds: 6            # Agent 循环最大轮次
    max-tool-calls-per-round: 8   # 单轮工具调用上限
    allow-mutating-tools: false   # 是否允许写工具
```

也支持环境变量覆盖：`DATAY_AI_BASE_URL` / `DATAY_AI_API_KEY` / `DATAY_AI_MODEL`。
未配置 `api-key` 时助手自动降级为不可用，不影响原有查询功能。

OpenAI / DeepSeek / 通义千问 / Ollama 等只要兼容该协议，替换 `base-url` 与 `model` 即可接入。

### 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ai/sql/status` | 查询助手可用状态与已注册工具数 |
| GET | `/api/ai/sql/tools` | 列出已注册的工具定义 |
| POST | `/api/ai/sql/generate` | 传入自然语言需求，返回最终 SQL、说明及工具调用轨迹 |

## 支持的数据源

- **关系型数据库**: MySQL, Oracle, PostgreSQL, SQL Server, MariaDB
- **分析数据库**: DuckDB, Doris, ClickHouse, GreenPlum
- **文件系统**: 本地文件、MinIO 对象存储
- **CDC**: MySQL Binlog

## 监控与日志

- 任务实例状态全程跟踪：APPENDING / RUNNING / WAITING / SUCCESSFUL / FAILED / TIMEOUT / INTERRUPTED
- 记录执行节点（`IP:端口`）、开始/结束时间、执行消息
- Spring Boot Actuator 管理端点：`/management/*`（健康检查、指标、日志）
- 详细的后端执行日志

## 测试

```bash
# 后端测试
./mvnw verify

# 前端单元测试 (Vitest)
./npmw test

# 代码质量
npm run lint
```

## 联系方式

产品不断完善中，如有问题或建议，请通过项目 Issue 反馈，我们会尽快回复您。支持个性化需求，可以加微信联系我们。

<img src="../datay-core/docs/images/datay.jpg" width="350" height="500" alt="DataY">
