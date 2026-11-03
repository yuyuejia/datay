# 数据集成与同步

**目标**：把源数据（文件 / MySQL / API / CDC 等）同步进数仓物理表。

数据接入有两种方式，按任务复杂度二选一：

| 方式 | 适用场景 | 入口 |
| --- | --- | --- |
| 可视化 ETL 画布 | 复杂数据流、多个组件编排 | 数据集成 |
| 数据同步 | 简单的库表同步 | 数据同步 |

## 方式一：可视化 ETL 画布

导航：**数据集成 → 新建任务**。

1. 从组件面板拖拽节点到画布，例如 `FileInput`（或 `StreamJdbcInput`）与 `ModelWrite`。
2. 用连线连接节点，编排数据流方向。
3. 双击节点打开配置弹窗，填写数据源、表、增量列、写入模式等参数。
4. 保存任务，画布会自动生成 DataY Core 可执行的任务 JSON（`units` + `connections`）。

> **连线设置**：双击连线可设置连线标签与**队列大小**（`queueCapacity`）。
> 队列大小表示该连线最多积压的批次数，默认 **10**；调大可提升吞吐与抖动容忍度，
> 但会按「队列大小 × 批大小 × 并发数」放大内存占用，请结合数据源承载能力设置。

![ETL 画布](../images/ETL画布.png)

![节点配置弹框](../images/节点配置弹框.png)

画布支持的组件继承自 DataY Core，完整清单见 [ETL 组件介绍](components/README.md)：

- **输入组件**：`StreamJdbcInput`、`JdbcInput`、`MySQLBinlogInput`、`PostgresCDCInput`、`MqttConsumer`
- **处理组件**：`DuckDBSql`、`StreamSqlUnit`、`Join`、`DataTransform`、`JavaScriptComponent`
- **输出组件**：`SqlSink`、`StreamJdbcOutput`、`DuckDBWrite`、`DuckLakeWrite`、`DorisStreamLoad`、`ModelWrite`
- **其他组件**：`HttpListener`、`GenerateFlowFile`

## 方式二：数据同步

导航：**数据同步**。

1. 选择源数据源与源表。
2. 选择目标数据源与目标表（可自动建表）。
3. 选择同步模式：**全量** 或 **增量**（需指定增量列）。
4. 执行或保存为同步任务。

数据同步会自动完成目标表建表（DDL 转换），源表改字段也会同步到目标。

### 用 AI 快速生成 ETL 任务

入口：进入 ETL 任务设计页，点击右上角 **AI 设计**。

1. 用一句话描述数据流向，例如「把 MySQL 的 shop.orders 表全量同步到数据仓库的 dw.orders」。
2. AI 会先查询组件目录、组件帮助文档、数据源与表结构，再生成节点、连线和基础配置。
3. 在右侧面板预览生成结果，确认后点击 **应用到画布**。
4. 双击节点检查或补全配置，然后点击 **保存**。

> AI 只负责生成画布草稿，**不会自动保存或执行任务**。
> 有帮助文档的组件会自动生成较完整的配置；暂无详细文档的组件只生成节点骨架，需要手动补全。
> 未配置大模型时，请先在「服务配置 · 大模型」中设置 API Key。

## 从模板市场导入 ETL 模板

如果不想从空白画布开始，可以到 [集成模板市场](../templates.html ':ignore :target=_self') 下载常用模板：

1. 在模板市场选择场景（MySQL 全量 / CDC、PostgreSQL CDC、HTTP、飞书等），点击「下载模板」。
2. 进入 **数据集成 → 导入任务**，选择下载的 `.json` 文件。
3. 导入后任务为**离线**状态。双击使用数据源的节点，按提示选择或修改数据源。
4. 先「执行一次」验证数据，再按需配置 Cron 上线调度。

> 模板里的数据源引用按「名称 + 类型 + 地址」匹配。匹配不到时导入结果会给出警告，
> 重新在节点里选择数据源即可；模板不包含真实密码，请导入后自行填写。
>
> 完整数据应用（如「快速入门」「电商销售分析」）不是单任务模板，请从
> **数据应用 → 应用市场 → 导入资产包文件** 导入。

## 执行与校验

1. 任务「执行一次」，或在任务列表点击「上线」加入调度。
2. 进入 **任务实例** 页查看状态，确认运行成功。

![任务实例运行成功](../images/任务实例运行成功.png)

3. 在 **数据源元数据浏览** 中确认目标表已生成且有数据。

## ETL 任务状态管理

ETL 中的有状态组件会把运行状态持久化，任务下次启动时读取恢复，避免重复同步：

- `StreamJdbcInput` / `JdbcInput`：保存增量列水位（`<组件id>.lastIncrValue.<表>`）
- `MySQLBinlogInput`：保存 binlog 文件与位点（`<组件id>.binlogFile`、`<组件id>.binlogPosition`）
- `PostgresCDCInput`：保存 WAL LSN（`<组件id>.binlogPosition`）

状态以 `jobCode`（即 `ETLTask.jobId`）为维度存储，可在「ETL 任务列表 → 状态」中查询、修改、删除。

### 状态存储后端

状态存储配置位于 `dp_service_config` 的 `status-storage` 分组，可在状态管理弹窗的「状态存储配置」中设置：

| 配置 | 说明 | 默认值 |
| --- | --- | --- |
| `type` | `local` 或 `minio` | `local` |
| `local.base-path` | 本地状态目录（单机部署） | `./log` |
| `minio.endpoint` / `minio.access-key` / `minio.secret-key` | MinIO 连接信息 | 空 |
| `minio.bucket-name` | MinIO 桶名 | `datay-status` |

- **单机部署（standalone）**：使用 `local`，状态写入本地 `./log/status_<jobCode>.json`。
- **master / worker 分离部署**：必须使用 `minio`。worker 执行时把状态写入 MinIO，master 通过共享的
  `dp_service_config` 解析到同一 MinIO，因此可在 master 端查询 / 修改 / 删除 worker 的状态。

> 注意：分离部署时若仍使用 `local`，状态只落在各 worker 本地磁盘，master 无法读取，也无法跨节点恢复增量。

## 下一步

- [任务设计与开发](guide/development.md)：了解任务类型与 DAG 编排
- [任务调度与实例](guide/scheduler.md)：把任务配置成定时 / 依赖调度
