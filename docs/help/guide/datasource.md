# 数据源管理

数据源是 DataY 的数据出入口。数据集成、数据同步、元数据浏览都依赖这里注册的连接。

导航：**数据源 → 数据源管理**。

## 新建数据源

1. 点击「新建数据源」。
2. 填写名称、类型、连接 URL、默认 schema 等信息。
3. 点击「测试连接」确认连通。
4. 保存。

以 DuckDB 为例：

| 字段 | 示例 |
| --- | --- |
| 名称 | `电商数仓(DuckDB)` |
| 类型 | `DUCKDB` |
| 连接 URL | `jdbc:duckdb:./metadata.duckdb` |
| 默认 schema | `main` |
| 用户名 / 密码 | 按数据库要求填写 |

![数据源列表](../images/数据源列表.png)

## 测试连接

- 保存前建议先点「测试连接」，确认驱动、URL、账号密码正确。
- 连接失败时优先检查：网络是否可达、端口是否正确、账号权限、URL 参数（如时区、SSL）。

## 元数据浏览

在数据源详情中可浏览 **schema → table → column** 层级元数据，用于：

- 确认目标表是否已生成、字段是否齐全
- 为 ETL 节点、注册模式导入字段提供参考

## 数据查询与表血缘

在数据源详情进入「数据查询」后，左侧选择表并打开表详情，可以查看：

- 字段信息
- 索引信息
- 数据预览
- 数据血缘

「数据血缘」会展示当前物理表、绑定到该表的数据模型，以及模型关联的维度、指标、衍生指标和看板；同时包含数据同步、ETL 对该表的读写关系。ETL 任务会继续向上展开其来源物理表，并向下展开输出物理表，继续追踪输出表绑定的模型、指标和看板。血缘图以当前表为高亮根节点，便于从物理表反查业务模型和下游应用。

## 支持的数据源

| 分类 | 数据源 |
| --- | --- |
| 关系型数据库 | MySQL、Oracle、PostgreSQL、SQL Server、MariaDB、SAP HANA |
| 时序数据库 | TDengine |
| 分析数据库 | DuckDB、Doris、ClickHouse、GreenPlum、YMatrix |
| 数据湖 | DuckLake、Apache Polaris（Iceberg REST Catalog） |
| 文件系统 | 本地文件、MinIO 对象存储 |
| 缓存 / KV | Redis |
| CDC | MySQL Binlog、PostgreSQL 逻辑复制（WAL / pgoutput） |

### Redis 连接说明

Redis 作为非 JDBC 缓存 / KV 数据源接入，类型选择 `REDIS`：

| 配置项 | 说明 |
| --- | --- |
| 连接 URL | `redis://host:port`，TLS 使用 `rediss://host:port` |
| 默认端口 | `6379` |
| 用户名 / 密码 | 可选，支持 Redis ACL 与 `requirepass` |
| 默认 Schema | Redis DB 索引，例如 `0` |
| 额外参数 | `redis.timeout_seconds`：连接与命令超时，默认 `5` 秒 |

- 元数据浏览中，Redis key 会作为“表”展示，key 类型（string/hash/list/set/zset/stream）作为表类型。
- ETL 读写使用 `RedisInput` 与 `RedisOutput` 组件，支持键级 JSON 往返、TTL 与批量写入。
- 首版仅支持单机 Redis，暂不支持 Cluster / Sentinel。

### TDengine 连接说明

TDengine 是一种时序数据库，DataY 支持数据源管理、元数据浏览、数据查询和 ETL 读写。新建数据源时类型选择 `TDENGINE`，默认使用 WebSocket 协议：

| 协议 | 连接 URL 示例 | 默认端口 | 驱动类 |
| --- | --- | --- | --- |
| WebSocket（默认） | `jdbc:TAOS-WS://127.0.0.1:6041/power` | `6041` | `com.taosdata.jdbc.ws.WebSocketDriver` |
| REST | `jdbc:TAOS-RS://127.0.0.1:6041/power` | `6041` | `com.taosdata.jdbc.rs.RestfulDriver` |
| 原生 | `jdbc:TAOS://127.0.0.1:6030/power` | `6030` | `com.taosdata.jdbc.TSDBDriver` |

- 前端默认按 WebSocket 拼接 URL；REST 或原生连接通过「自定义 URL」直接填写。
- `taos-jdbcdriver` 不随平台预置，需在新建数据源时通过「JDBC 驱动」上传 `-dist` 完整包（已包含 Jackson、Guava、WebSocket 等依赖）。
- 原生协议依赖本机 TDengine 客户端原生库；容器化部署建议优先使用 WebSocket 或 REST。
- TDengine 没有 schema 概念，**database 会作为 schema 展示**；左侧元数据树按 `database → table → column` 浏览。
- 每个表必须包含一个 `TIMESTAMP` 时间列，ETL 自动建表时会将该列放在第一列。
- TDengine 相同时间戳重复写入会覆盖旧值，因此 ETL 的 `update` 模式在 TDengine 上等价于 `insert/upsert`。
- **超级表（STable）不能直接执行普通 INSERT/UPDATE**。需要写入超级表时，请使用 `SqlSink` 并配置 `INSERT INTO 子表 USING 超级表 TAGS (...) VALUES (...)`；普通 ETL 输出节点会提前拦截并给出提示。

### JDBC 驱动上传

平台不再自动从 Maven 仓库下载 JDBC 驱动。对于驱动未预置的数据源（如达梦、SAP HANA、SQL Server、ClickHouse、TDengine、通用 JDBC），新建或编辑数据源时会出现「JDBC 驱动」区域：

1. 点击「选择并上传」，选择一个或多个 `.jar` 文件（选择后自动开始上传）；
2. 上传成功后驱动会保存到运行目录的 `drivers/<type>/default/`，并自动刷新该类型的连接池，无需重启。

说明：

- 上传会替换该类型已有驱动，避免多版本 jar 冲突；需要依赖 jar 时可一次上传多个文件。
- 请上传与所选数据库版本匹配的驱动。
- 通用 JDBC 数据源的驱动按数据源隔离，保存在 `drivers/jdbc/<owner>/default/`，不同数据源之间不共享；上传成功后会自动把该目录的相对路径填入 `jdbc.driverJar`，便于 Web 与 Docker worker 共用驱动目录。
- 分布式部署时，请确保 worker 与 Web 使用相同的驱动目录（或通过 `driver-dir` 挂载共享）。

### YMatrix 连接说明

YMatrix 是 Greenplum 系超融合时序数据库，兼容 PostgreSQL 协议。新建数据源时类型选择 `YMATRIX`，使用平台内置的 PostgreSQL 驱动即可：

| 配置项 | 说明 |
| --- | --- |
| 连接 URL | `jdbc:postgresql://host:5432/database` |
| 默认端口 | `5432` |
| 驱动类 | `org.postgresql.Driver`（平台内置，无需下载） |
| 用户名 | 初始化 YMatrix 时设置的管理员用户，通常为 `mxadmin` |
| 默认 Schema | 目标 Schema，一般为 `public` |

- 高可用连接可通过「自定义 URL」填写 `jdbc:postgresql://master:5432,standby:5432/database`。
- 元数据浏览、SQL 查询、ETL 读写与自动建表均复用 PostgreSQL 方言；自动建表默认不指定 `DISTRIBUTED BY` 与 MARS3 存储，需要针对性优化时可通过 `SqlSink` 自定义 DDL。
- `ON CONFLICT` UPSERT 需 YMatrix 5.x 及以上版本；4.x 版本请使用 insert/append 写入模式。

### Apache Polaris 连接说明

Apache Polaris 是 Iceberg REST Catalog 的实现，DataY 通过 DuckDB 的 `iceberg` 扩展完成元数据浏览与读写。新建数据源时类型选择 `POLARIS`：

| 配置项 | 说明 |
| --- | --- |
| 连接地址 | `polaris://host:8181/warehouse`，`warehouse` 为 Polaris 的 Catalog 名称 |
| 默认端口 | `8181` |
| 用户名 | OAuth2 Client ID |
| 密码 | OAuth2 Client Secret |
| 默认 Schema | 目标 Namespace（不存在时自动创建） |

常用扩展参数：

- `polaris.catalog_uri`：REST Catalog 完整地址，默认按 `http://host:port/api/catalog` 推导；通过反向代理或 HTTPS 暴露时填写。
- `polaris.scope` / `polaris.oauth2_server_uri`：自定义 OAuth2 授权范围与 Token 地址。
- `polaris.access_delegation_mode`：凭证委派模式，默认 `vended_credentials`；设置为 `none` 时需补充 `s3.key_id` / `s3.secret` 等 S3 凭证。
- `polaris.stage_create_tables` / `polaris.disable_multi_table_commit` / `polaris.skip_create_table_metadata_updates` / `polaris.remove_files_on_delete`：部分 Catalog 版本对 `CREATE TABLE`、多表提交等 REST 接口支持不完整时，用于透传 DuckDB 的兼容开关。
- `s3.endpoint` / `s3.region` / `s3.url_style` / `s3.use_ssl`：对象存储连接参数。`s3.endpoint` 可写 `http://host:port` 或 `host:port`，协议会根据 `http://` / `https://` 自动推导 `USE_SSL`（也可用 `s3.use_ssl` 显式指定）。

注意事项：

- 读写实际由 DuckDB 完成，首次连接需要安装 `httpfs`、`iceberg` 扩展，请确保运行环境可访问 DuckDB 扩展仓库或已预装扩展。
- 每个 Polaris 数据源使用独立的本地 DuckDB 文件挂载一份 Catalog，凭证只写入 DuckDB Secret。
- DuckDB 目前对 Iceberg REST Catalog 的写入仅支持 S3 / S3 Tables / GCS 作为底层存储，暂不支持其他对象存储或本地文件系统。
- Iceberg 表没有主键，也不支持 `TRUNCATE`；ETL 的 `update` / `delete` 通过 CDC 变更前镜像（`__before`）匹配旧行，`overwrite` 使用 `DELETE` 清空后写入。
- 建表语句自动转换为 Iceberg 支持的类型（`TINYINT`/`SMALLINT` 归并为 `INTEGER`，`TEXT`/`JSON` 归并为 `VARCHAR`，`DECIMAL` 精度上限 38），不带主键约束。

## 下一步

- [数据集成与同步](guide/integration.md)：用注册好的数据源编排同步任务
- [ETL 组件介绍](components/StreamJdbcInput.md)：了解各数据源对应的输入 / 输出组件
