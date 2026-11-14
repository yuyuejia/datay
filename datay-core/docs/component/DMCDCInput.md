# DMCDCInput 组件配置文档

## 组件概述

DMCDCInput 是一个基于达梦 **日志挖掘（DBMS_LOGMNR）** 的实时数据输入组件，用于捕获达梦数据库的数据变更。组件通过 JDBC 读取达梦本地归档日志，实时解析 `INSERT`、`UPDATE`、`DELETE` 事件，是构建实时数据同步、CDC（Change Data Capture）和流式 ETL 流程的核心输入组件。

组件无需部署 DMHS、DMDRS 等额外同步软件，仅依赖达梦内置的 `DBMS_LOGMNR` 系统包。

> 达梦 `DBMS_LOGMNR` 只能分析归档日志。为了做到秒级采集，组件默认会周期性执行
> `ALTER SYSTEM ARCHIVE LOG CURRENT` 把在线 redo 切换为归档日志。

## 核心特性

- **实时数据变更捕获**：基于归档日志挖掘，默认秒级捕获数据变更
- **支持多种事件类型**：`INSERT`、`UPDATE`、`DELETE`
- **断点续传**：以已提交事务的最大 `COMMIT_SCN` 作为位点随数据流持久化，任务重启自动续采
- **表级过滤**：支持基于表名的正则表达式过滤（不区分大小写）
- **自动切归档**：可配置切换间隔，也可关闭后仅消费已有归档
- **事件合并优化**：按表与事件类型合并，减少下游写入次数
- **首次全量快照**：支持先全量读取存量数据，再启动增量同步

## 前置要求

### 1. 开启归档与逻辑日志

在源达梦数据库上以 `SYSDBA` 执行：

```sql
-- 开启归档（已有本地归档可跳过）
ALTER DATABASE MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE ADD ARCHIVELOG 'TYPE=LOCAL, DEST=/opt/dmdbms/arch, FILE_SIZE=256, SPACE_LIMIT=1024';
ALTER DATABASE OPEN;

-- 记录逻辑日志：2 = 无论是否有主键，UPDATE/DELETE 都记录所有列（推荐）
SP_SET_PARA_VALUE(1, 'RLOG_APPEND_LOGIC', 2);

-- 记录所有表的逻辑日志；为 0 时只有 ADD LOGIC LOG 的表才会记录
SP_SET_PARA_VALUE(1, 'RLOG_IGNORE_TABLE_SET', 1);

-- 创建日志挖掘系统包
SP_CREATE_SYSTEM_PACKAGES(1, 'DBMS_LOGMNR');
```

> 修改 `RLOG_APPEND_LOGIC` 后需要**重启达梦实例**，新的逻辑日志级别才会生效；只改参数不重启时，已启动实例仍可能按旧级别记录。

校验：

```sql
SELECT ARCH_MODE FROM V$DATABASE;
-- 期望 Y

SELECT PARA_NAME, PARA_VALUE
FROM V$DM_INI
WHERE PARA_NAME IN ('ARCH_INI', 'RLOG_APPEND_LOGIC', 'RLOG_IGNORE_TABLE_SET', 'LOGMNR_DELAY_ANALYZE');
```

### 2. 采集账号授权

```sql
GRANT SELECT ANY TABLE TO <采集账号>;
GRANT SELECT ON V$DM_INI TO <采集账号>;
GRANT SELECT ON V$DATABASE TO <采集账号>;
GRANT SELECT ON V$RLOG TO <采集账号>;
GRANT SELECT ON V$ARCHIVED_LOG TO <采集账号>;
GRANT SELECT ON V$DM_ARCH_INI TO <采集账号>;
GRANT SELECT ON V$LOGMNR_CONTENTS TO <采集账号>;
GRANT SELECT ON V$LOGMNR_LOGS TO <采集账号>;

GRANT EXECUTE ON DBMS_LOGMNR TO <采集账号>;

-- 自动切归档需要归档切换权限；也可直接授予 DBA
GRANT ALTER DATABASE TO <采集账号>;
```

> 达梦只有 **本地归档日志** 可以被 `DBMS_LOGMNR.ADD_LOGFILE` 添加，且必须与数据库实例在同一台机器上。
> 远程实时归档、异步归档、DMDPC/MPP 跨节点日志不在支持范围内。

## 配置参数

### 必需参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `datasource` | DatasourceInfo | ✅ | 达梦数据源连接信息 |
| `schema` / `schemaName` | String | ✅ | 采集的模式名，缺省取数据源的 `dbschema`，再缺省取用户名大写 |

### 可选参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `tableNamePattern` | String | ❌ | 表名过滤正则，为空表示采集 schema 下所有表，不区分大小写 | `"CDC_.*"` |
| `startScn` | Long | ❌ | 指定起始 SCN，为空表示从上次保存位点或当前 SCN 开始 | `2149300` |
| `pollIntervalMs` | Long | ❌ | 日志挖掘轮询间隔（毫秒），默认 `1000` | `500` |
| `archiveSwitch` | Boolean | ❌ | 是否主动切换归档日志，默认 `true` | `false` |
| `archiveSwitchIntervalMs` | Long | ❌ | 两次归档切换之间的最短间隔（毫秒），默认 `5000`，最小 `1000` | `3000` |
| `snapshot` | Boolean | ❌ | 首次运行（无断点）时先读取历史全量数据，再启动增量同步，默认 `false` | `true` |
| `snapshotFetchSize` | Integer | ❌ | 快照分批大小，默认 `10000` | `5000` |
| `includeEventMeta` | Boolean | ❌ | 是否在输出行中追加 `_event_type` 和 `_timestamp`，默认 `false` | `true` |
| `reconnectMaxAttempts` | Integer | ❌ | 连续重连失败的最大次数，超过后任务失败，默认 `5`；`0` 表示不重连 | `5` |
| `reconnectIntervalMs` | Long | ❌ | 两次重连之间的等待时间（毫秒），默认 `3000` | `3000` |
| `ignoreError` | Boolean | ❌ | 非连接类**挖掘错误**是否仅记录日志而不中断任务，默认 `true`；启动校验错误和连接失败始终使任务失败 | `false` |

## 数据源配置 (DatasourceInfo)

```json
{
  "type": "dm",
  "url": "jdbc:dm://127.0.0.1:5236",
  "driver": "dm.jdbc.driver.DmDriver",
  "username": "DATAY",
  "password": "Datay123456",
  "dbschema": "DATAY"
}
```

## 配置示例

### 示例1：采集 schema 下全部表

```json
{
  ".id": "DMCDCInput",
  ".name": "DMCDCInput",
  "datasource": {
    "url": "jdbc:dm://127.0.0.1:5236",
    "username": "DATAY",
    "password": "Datay123456",
    "dbschema": "DATAY"
  },
  "schema": "DATAY"
}
```

### 示例2：按表过滤 + 自动切归档

```json
{
  ".id": "dm-cdc-input",
  ".name": "DMCDCInput",
  "datasource": {
    "url": "jdbc:dm://127.0.0.1:5236",
    "username": "DATAY",
    "password": "Datay123456",
    "dbschema": "DATAY"
  },
  "schema": "DATAY",
  "tableNamePattern": "CDC_DEMO|CUSTOMERS",
  "pollIntervalMs": 1000,
  "archiveSwitch": true,
  "archiveSwitchIntervalMs": 5000
}
```

### 示例3：仅消费已有归档（不切归档）

```json
{
  ".id": "dm-cdc-input",
  ".name": "DMCDCInput",
  "datasource": {
    "url": "jdbc:dm://127.0.0.1:5236",
    "username": "DATAY",
    "password": "Datay123456",
    "dbschema": "DATAY"
  },
  "schema": "DATAY",
  "archiveSwitch": false
}
```

> 关闭自动切归档后，采集延迟取决于达梦自身产生归档日志的频率。建议由 DBA 统一配置归档切换任务，
> 多个 CDC 任务共用同一实例时只保留一个任务负责切归档。

## 工作原理

1. **校验与解析**：校验 `ARCH_MODE=Y`、`RLOG_APPEND_LOGIC`、`RLOG_IGNORE_TABLE_SET`、`LOGMNR_DELAY_ANALYZE`，解析采集表范围。
2. **切换归档**：按 `archiveSwitchIntervalMs` 执行 `ALTER SYSTEM ARCHIVE LOG CURRENT`，生成新的本地归档日志。
3. **发现归档**：查询 `V$ARCHIVED_LOG` 中与当前 SCN 窗口重叠的本地归档文件。
4. **注册并启动**：通过 `DBMS_LOGMNR.ADD_LOGFILE` 注册归档文件，调用
   `DBMS_LOGMNR.START_LOGMNR(STARTSCN, ENDSCN, OPTIONS => COMMITTED_DATA_ONLY + DICT_FROM_ONLINE_CATALOG)`。
5. **挖掘变更**：查询 `V$LOGMNR_CONTENTS`，使用 `DBMS_LOGMNR.COLUMN_PRESENT` 和
   `DBMS_LOGMNR.MINE_VALUE` 提取变更列；达梦 `MINE_VALUE` 对 UPDATE 的未变更列返回 null，
   组件会解析 `SQL_REDO` 的 WHERE/SET 子句补全完整前后镜像。
6. **事件下发**：转换为通用 CDC 事件（`BinlogEvent`）交给下游，事务以已提交的最大 SCN 作为位点。
7. **状态保存**：位点（`binlogPosition` = 已提交 SCN）随 FlowFile 持久化，任务重启自动续采。

## 连接异常与重连

- **初始连接失败**或**日志挖掘过程中断**时，组件按 `reconnectIntervalMs` 的间隔自动重连；
  重连后从已提交的最大 SCN 继续挖掘。
- 连续失败次数超过 `reconnectMaxAttempts`（默认 `5`）后，组件抛出连接异常，ETL 任务状态
  变为 `FAILED`，不会出现「连接已断开但任务显示成功」的情况。
- 连接失败不受 `ignoreError` 影响，始终向上抛出；手动停止任务属于正常停止，不会触发重连。
- 单次连接稳定运行超过 10 秒后，下一次断线会重新累计连续失败次数。

> 任务级重试（Job 的 `retryCount`）与组件级重连是两层机制：组件重连耗尽后任务先变为
> `FAILED`，是否再次整体重试仍由任务的重试配置决定。

## 首次全量快照（snapshot）

开启 `snapshot=true` 后，组件首次运行（无历史断点、无 `snapshotDone` 标记）会：

1. 先获取当前 SCN 作为增量起点（保证快照窗口内的变更不丢）；
2. 对 schema 下匹配 `tableNamePattern` 的表做一次性全量读取，按 `INSERT` 事件分批下发；
3. 再从上述 SCN 启动 LogMiner 增量同步；
4. 全量完成后写入 `snapshotDone` 状态，任务重启有断点或已完成快照时不会重复全量。

> 采用「简单一致性」策略：快照窗口内发生的变更会由 CDC 补放，存在少量重复的可能；对幂等写入或存量基本稳定的场景足够。

## 与其它 CDC 的差异与限制

- **只支持归档日志**：达梦 `DBMS_LOGMNR` 不读在线 redo，实时性依赖归档切换；关闭 `archiveSwitch` 后延迟不可控。
- **不支持 DDL**：仅发送数据变更（`INSERT/UPDATE/DELETE`），不发送 `ALTER TABLE` 等 DDL。
- **LOB/二进制列**：`CLOB/BLOB/TEXT/IMAGE/ROWID/XML/BINARY/RAW/ARRAY` 等类型暂不通过 `MINE_VALUE` 取值（会被忽略并告警）。
- **UPDATE 未变更列 / DELETE 前镜像**：需要 `RLOG_APPEND_LOGIC=2`；`1` 在主键表上只记录主键和变更列。
- **归档清理**：如果位点对应的归档被 `SPACE_LIMIT` 清理，组件会告警；期间发生的变更无法恢复，请合理设置归档保留空间。
- **长事务**：跨越轮询窗口且提交较晚的长事务存在极小概率丢失，建议事务不要长时间持有；存量数据建议先做全量。
- **集群限制**：DMDPC/MPP 不支持跨节点日志同时挖掘；`ADD_LOGFILE` 只能添加数据库实例本机归档。
- **初始数据**：增量默认只捕获组件启动后的变更，存量数据请开启 `snapshot` 或另行全量同步。

## 故障排除

**问题：数据库未开启归档模式**
- 报错提示未开启归档，需以 SYSDBA 执行 `ALTER DATABASE ARCHIVELOG` 并配置本地归档。

**问题：`RLOG_APPEND_LOGIC=0`**
- 达梦不会记录逻辑操作，需执行 `SP_SET_PARA_VALUE(1,'RLOG_APPEND_LOGIC',2)`。

**问题：`DBMS_LOGMNR` 包不存在**
- 执行 `SP_CREATE_SYSTEM_PACKAGES(1,'DBMS_LOGMNR')`。

**问题：归档切换权限不足**
- 为采集账号授予归档切换权限，或设置 `archiveSwitch=false` 仅消费已有归档。

**问题：UPDATE 事件部分列为空**
- 将 `RLOG_APPEND_LOGIC` 设置为 `2`，并确认目标表已开启逻辑日志（`RLOG_IGNORE_TABLE_SET=1` 或 `ADD LOGIC LOG`）。

**问题：任务重启后数据重复或缺失**
- 位点以已提交 SCN 持久化，保持数据源与 schema 配置稳定；检查归档保留空间是否覆盖位点到当前时间。

---

*本文档基于 DMCDCInput 组件 v1.0 版本编写*
