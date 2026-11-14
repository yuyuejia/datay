# SQLServerCDCInput 组件配置文档

## 组件概述

SQLServerCDCInput 是一个基于 SQL Server **内置变更数据捕获（CDC）** 的实时数据输入组件，用于捕获 SQL Server 数据库的数据变更。组件通过 JDBC 读取 CDC change table 和 `cdc.fn_cdc_get_all_changes_*` 表值函数，实时解析 `INSERT`、`UPDATE`、`DELETE` 事件，是构建实时数据同步、CDC（Change Data Capture）和流式 ETL 流程的核心输入组件。

组件无需部署 Debezium、Kafka、GoldenGate 等额外同步软件，仅依赖 SQL Server 官方 CDC 功能和 SQL Server Agent。

> SQL Server CDC 的捕获过程由 **SQL Server Agent** 的 capture job 完成。Agent 未运行时，
> change table 不会产生新数据，组件将处于等待状态。

## 核心特性

- **实时数据变更捕获**：读取 SQL Server CDC change table，捕获延迟通常约 5 秒（取决于 Agent 捕获作业配置）
- **支持多种事件类型**：`INSERT`、`UPDATE`、`DELETE`
- **完整前后镜像**：使用 `all update old` 行过滤选项，UPDATE 事件包含变更前后完整镜像
- **断点续传**：以十六进制 LSN 作为位点随数据流持久化，任务重启自动续采
- **窗口级位点推进**：一个 LSN 窗口内所有目标表处理完成后才推进位点，避免同事务多表变更漏数
- **表级过滤**：支持基于表名的正则表达式过滤（不区分大小写）
- **自动开启 CDC**：可选配置，自动执行 `sp_cdc_enable_db` / `sp_cdc_enable_table`
- **事件合并优化**：按表与事件类型合并，减少下游写入次数
- **首次全量快照**：支持先全量读取存量数据，再启动增量同步

## 前置要求

> 支持 SQL Server 2012 及以上（建议 2016+），且 SQL Server Agent 正在运行。
> SQL Server Express 版没有 Agent，不支持 CDC。

### 1. 开启数据库 CDC

在源 SQL Server 上以 `db_owner` 或 `sysadmin` 执行：

```sql
USE [your_database];
EXEC sys.sp_cdc_enable_db;

-- 校验
SELECT name, is_cdc_enabled
FROM sys.databases
WHERE name = 'your_database';
```

### 2. 确保 SQL Server Agent 正在运行

SQL Server CDC 的 capture job 依赖 SQL Server Agent：

```sql
-- 查看 CDC 作业
EXEC sys.sp_cdc_help_jobs;

-- 如捕获作业未运行，启动它
EXEC sys.sp_cdc_start_job @job_type = N'capture';
```

> SQL Server **Express 版没有 SQL Server Agent**，因此不支持 CDC。
> 数据库引擎服务或 Agent 服务运行在 `NETWORK SERVICE` 账号下时 CDC 也可能无法正常工作。

### 3. 为表开启 CDC

```sql
EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name   = N'orders',
    @role_name     = NULL,
    @supports_net_changes = 0;

-- 校验
SELECT s.name AS schema_name,
       t.name AS table_name,
       t.is_tracked_by_cdc
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE s.name = N'dbo';
```

默认不指定 `@captured_column_list`，即捕获源表所有列。组件需要完整的捕获列才能正确同步 UPDATE/DELETE；
如果只捕获部分列，未捕获列在目标端可能被写成 `NULL`。

### 4. 采集账号授权

```sql
-- 源表读取权限
GRANT SELECT ON [dbo].[orders] TO [datay];

-- CDC change table、表值函数、lsn_time_mapping 的读取权限
GRANT SELECT ON SCHEMA::cdc TO [datay];

-- 仅在启用 autoEnableCdc 时需要
ALTER ROLE db_owner ADD MEMBER [datay];
```

> 如果为 capture instance 指定了 gating role（`@role_name`），采集账号还需要加入该角色。

### 5. CDC 保留期

SQL Server 默认保留 **3 天**变更，cleanup job 每天凌晨 2 点清理。如果任务停机时间超过保留期，
对应位点的变更会被清理，组件会告警并从当前最小 LSN 继续，期间数据无法恢复。

```sql
-- 查看/调整清理作业
EXEC sys.sp_cdc_help_jobs;
EXEC sys.sp_cdc_change_job @job_type = N'cleanup', @retention = 4320; -- 分钟
```

## 配置参数

### 必需参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `datasource` / `sourceId` | DatasourceInfo | ✅ | SQL Server 数据源连接信息 |
| `schema` / `schemaName` | String | ❌ | 采集的 schema，缺省取数据源 `dbschema`，再缺省为 `dbo` |

### 可选参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `tableNamePattern` | String | ❌ | 表名过滤正则，为空表示采集 schema 下所有已开启 CDC 的表，不区分大小写 | `"CDC_.*"` |
| `startLsn` | String | ❌ | 指定从哪个 LSN 之后开始采集，十六进制字符串，为空表示从上次保存位点或当前位置开始 | `"0x0000002A0000003C0004"` |
| `pollIntervalMs` | Long | ❌ | 查询 change table 的轮询间隔（毫秒），默认 `1000` | `500` |
| `autoEnableCdc` | Boolean | ❌ | 未开启 CDC 时是否自动执行开启语句，默认 `false`，需要 `db_owner` 权限 | `true` |
| `snapshot` | Boolean | ❌ | 首次运行（无断点）时先读取历史全量数据，再启动增量同步，默认 `false` | `true` |
| `snapshotFetchSize` | Integer | ❌ | 快照分批大小，默认 `10000` | `5000` |
| `includeEventMeta` | Boolean | ❌ | 是否在输出行中追加 `_event_type` / `_timestamp`，默认 `false` | `true` |
| `reconnectMaxAttempts` | Integer | ❌ | 连续重连失败的最大次数，超过后任务失败，默认 `5`；`0` 表示不重连 | `5` |
| `reconnectIntervalMs` | Long | ❌ | 两次重连之间的等待时间（毫秒），默认 `3000` | `3000` |
| `ignoreError` | Boolean | ❌ | 非连接类采集错误是否仅记录日志而不中断任务，默认 `true`；连接失败始终使任务失败 | `false` |

## 数据源配置 (DatasourceInfo)

```json
{
  "type": "sqlserver",
  "url": "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_cdc",
  "driver": "com.microsoft.sqlserver.jdbc.SQLServerDriver",
  "username": "datay",
  "password": "Datay123456!",
  "dbschema": "dbo"
}
```

> SQL Server 使用「数据库 + schema」定位表。数据库名在 JDBC URL 的 `databaseName` 中指定，
> 组件中的 `schema` 表示 `dbo` 等数据库内部 schema，二者不能混用。

> 运行目录需要存在 SQL Server JDBC 驱动，例如
> `drivers/sqlserver/default/mssql-jdbc-12.4.2.jre11.jar`。

## 配置示例

### 示例1：采集 schema 下全部已开启 CDC 的表

```json
{
  ".id": "SQLServerCDCInput",
  ".name": "SQLServerCDCInput",
  "sourceId": {
    "type": "sqlserver",
    "url": "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_cdc",
    "username": "datay",
    "password": "Datay123456!",
    "dbschema": "dbo"
  },
  "schema": "dbo"
}
```

### 示例2：按表过滤 + 自动开启 CDC

```json
{
  ".id": "sqlserver-cdc-input",
  ".name": "SQLServerCDCInput",
  "sourceId": {
    "type": "sqlserver",
    "url": "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_cdc",
    "username": "datay",
    "password": "Datay123456!",
    "dbschema": "dbo"
  },
  "schema": "dbo",
  "tableNamePattern": "CDC_.*|ORDERS",
  "autoEnableCdc": true,
  "pollIntervalMs": 1000
}
```

### 示例3：首次全量 + 增量

```json
{
  ".id": "sqlserver-cdc-input",
  ".name": "SQLServerCDCInput",
  "sourceId": {
    "type": "sqlserver",
    "url": "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_cdc",
    "username": "datay",
    "password": "Datay123456!",
    "dbschema": "dbo"
  },
  "schema": "dbo",
  "tableNamePattern": "CDC_DEMO",
  "snapshot": true,
  "snapshotFetchSize": 5000
}
```

### 示例4：从指定 LSN 之后开始

```json
{
  ".id": "sqlserver-cdc-input",
  ".name": "SQLServerCDCInput",
  "sourceId": {
    "type": "sqlserver",
    "url": "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_cdc",
    "username": "datay",
    "password": "Datay123456!",
    "dbschema": "dbo"
  },
  "schema": "dbo",
  "startLsn": "0x0000002A0000003C0004"
}
```

## 工作原理

1. **校验与解析**：校验数据库 `is_cdc_enabled=1`，解析 schema 下匹配 `tableNamePattern` 的表；
   从 `cdc.change_tables` 关联 `sys.tables` / `sys.schemas` 获取每张表的 capture instance。
2. **定位 LSN 窗口**：读取 `sys.fn_cdc_get_max_lsn()` 作为窗口上界；
   使用 `sys.fn_cdc_increment_lsn(上次位点)` 作为下界。
3. **读取变更**：对每个 capture instance 调用
   `cdc.fn_cdc_get_all_changes_<capture_instance>(from_lsn, to_lsn, N'all update old')`，
   并关联 `cdc.lsn_time_mapping` 获取事务提交时间。
4. **解析事件**：
   - `__$operation = 1` → DELETE（前镜像）
   - `2` → INSERT（后镜像）
   - `3` → UPDATE 前镜像（暂存）
   - `4` → UPDATE 后镜像（与 `3` 配对成 UPDATE 事件）
5. **事件下发**：转换为通用 CDC 事件（`BinlogEvent`）交给下游；
   UPDATE 事件会在 `afterData` 中写入 `__before`，供下游处理主键变更。
6. **位点保存**：一个窗口内所有目标表处理完成后，将窗口上界 LSN 持久化为
   `binlogPosition`，任务重启后从 `fn_cdc_increment_lsn(binlogPosition)` 继续。

> 位点以「窗口」为单位推进，语义为**至少一次**。任务异常重启时可能重复投递少量事件，
> 下游使用 `model=auto` 的 upsert/delete 即可幂等处理。

## 连接异常与重连

- **初始连接失败**或**采集过程中断**时，组件按 `reconnectIntervalMs` 的间隔自动重连；
  重连后从最后完整处理到的 LSN 继续采集。
- 连续失败次数超过 `reconnectMaxAttempts`（默认 `5`）后，组件抛出连接异常，ETL 任务状态
  变为 `FAILED`，不会出现「连接已断开但任务显示成功」的情况。
- 连接失败不受 `ignoreError` 影响，始终向上抛出；手动停止任务属于正常停止，不会触发重连。
- 单次连接稳定运行超过 10 秒后，下一次断线会重新累计连续失败次数。

> 任务级重试（Job 的 `retryCount`）与组件级重连是两层机制：组件重连耗尽后任务先变为
> `FAILED`，是否再次整体重试仍由任务的重试配置决定。

## 首次全量快照（snapshot）

开启 `snapshot=true` 后，组件首次运行（无历史断点、无 `snapshotDone` 标记）会：

1. 校验源库并解析目标表；
2. 读取当前最大 LSN 作为增量起点（保证快照窗口内的变更不丢）；
3. 对 schema 下匹配 `tableNamePattern` 的表做一次性全量读取，按 `INSERT` 事件分批下发；
4. 再从上述 LSN 之后启动 CDC 增量同步；
5. 全量完成后写入 `snapshotDone` 状态，任务重启有断点或已完成快照时不会重复全量。

> 如果开启快照时 CDC 尚未产生可用 LSN（例如刚开启捕获作业），组件会从 change table
> 最早可用位点开始增量采集，避免漏掉快照窗口内产生的变更。

> 采用「简单一致性」策略：快照读取使用 `REPEATABLE READ` 事务，快照窗口内已提交的变更
> 可能由 CDC 再次补放，存在少量重复；下游幂等写入即可。

## 与其它 CDC 的差异与限制

- **依赖 SQL Server Agent**：Agent 未运行或使用 Express 版时无法采集。
- **捕获延迟**：默认 capture job 每 5 秒扫描一次事务日志，实际延迟通常为秒级；可通过
  `sp_cdc_change_job` 调整 `@pollinginterval`。
- **不支持 DDL**：仅发送数据变更（`INSERT/UPDATE/DELETE`），不发送 `ALTER TABLE` 等 DDL。
  capture instance 的列结构在开启 CDC 时固定，源表新增列不会被自动捕获；需要重建 capture instance
  或重跑全量。
- **UPDATE 前镜像的 LOB 列**：`text/ntext/image` 在 `__$operation=1/3` 时始终为 `NULL`；
  `varchar(max)/nvarchar(max)/varbinary(max)` 在 UPDATE 前镜像中，只有发生变更时才有值。
- **计算列**：启用 CDC 的计算列在 change table 中始终为 `NULL`。
- **CDC 清理**：默认保留 3 天变更，长时间停机可能丢失变更；组件会在位点早于最小 LSN 时告警。
- **初始数据**：增量默认只捕获组件启动之后的变更，存量数据请开启 `snapshot` 或另行全量同步。
- **主键变更**：依赖 UPDATE 前后镜像识别主键变化；建议 capture instance 捕获全部列。
- **多 capture instance**：同一张表最多允许两个 capture instance，组件按名称顺序选择第一个可用实例。
- **Azure SQL Database**：使用与 SQL Server 不同的 CDC 实现，本组件面向 SQL Server（含 Azure SQL Managed Instance）。

## 常见错误

| 错误信息 | 原因 | 处理方式 |
|----------|------|----------|
| `数据库未开启 CDC` | `sys.databases.is_cdc_enabled = 0` | 执行 `EXEC sys.sp_cdc_enable_db;` |
| `以下表未开启 CDC` | 目标表没有 capture instance | 执行 `sys.sp_cdc_enable_table` 或开启 `autoEnableCdc` |
| `读取 CDC capture instance 失败` | 账号缺少 `cdc` schema 权限 | `GRANT SELECT ON SCHEMA::cdc TO [账号];` |
| `SQL Server CDC 连接异常` | 网络、登录、Agent/数据库不可用 | 检查连接串、账号权限、SQL Server Agent |
| `LSN 区间不在有效范围内` / 错误 313 | 位点早于 CDC 清理边界或数据库恢复 | 组件自动从最小 LSN 继续并告警；必要时重跑全量 |
| `未找到可用的 capture instance` | 表已开启 CDC 但实例正在删除 | 重新执行 `sys.sp_cdc_enable_table` |

---

*本文档基于 SQLServerCDCInput 组件 v1.0 版本编写*
