# OracleCDCInput 组件配置文档

## 组件概述

OracleCDCInput 是一个基于 Oracle **日志挖掘（LogMiner / `DBMS_LOGMNR`）** 的实时数据输入组件，用于捕获 Oracle 数据库的数据变更。组件通过 JDBC 读取在线与归档重做日志，实时解析 `INSERT`、`UPDATE`、`DELETE` 事件，是构建实时数据同步、CDC（Change Data Capture）和流式 ETL 流程的核心输入组件。

组件无需部署 Debezium、GoldenGate、XStream 等额外组件，仅依赖数据库内置的 `DBMS_LOGMNR`。

## 核心特性

- **实时数据变更捕获**：基于 LogMiner 挖掘重做日志，秒级捕获数据变更
- **支持多种事件类型**：`INSERT`、`UPDATE`、`DELETE`
- **断点续传**：以已提交事务的最大 SCN 作为位点随数据流持久化，任务重启自动续采
- **表级过滤**：支持基于表名的正则表达式过滤（不区分大小写）
- **自动定位日志**：CDB/PDB 环境下自动发现所需日志，非 PDB 环境自动注册在线与归档日志
- **事件合并优化**：按表与事件类型合并，减少下游写入次数

## 前置要求

在源 Oracle 上需要满足：

```sql
-- 1. 开启归档模式（需要 SYSDBA，且需要重启数据库到 MOUNT 状态）
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;

-- 2. 开启补充日志（最小 + 全列，全列用于补全 UPDATE 的未变更列）
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 3. 采集账号授权（在目标 PDB / 非 CDB 库中执行）
GRANT LOGMINING TO <采集账号>;
GRANT SELECT ANY TRANSACTION TO <采集账号>;
GRANT EXECUTE ON SYS.DBMS_LOGMNR TO <采集账号>;
GRANT EXECUTE ON SYS.DBMS_LOGMNR_D TO <采集账号>;
GRANT SELECT ON SYS.V_$LOGMNR_CONTENTS TO <采集账号>;
GRANT SELECT ON SYS.V_$LOGMNR_LOGS TO <采集账号>;
GRANT SELECT ON SYS.V_$ARCHIVED_LOG TO <采集账号>;
GRANT SELECT ON SYS.V_$LOGFILE TO <采集账号>;
GRANT SELECT ON SYS.V_$LOG TO <采集账号>;
GRANT SELECT ON SYS.V_$DATABASE TO <采集账号>;
```

> CDB 环境下需在 `CDB$ROOT` 与目标 PDB 中分别开启补充日志，并在访问表的 PDB 中授权。

## 配置参数

### 必需参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `datasource` | DatasourceInfo | ✅ | Oracle 数据源连接信息 |
| `schema` / `schemaName` | String | ✅ | 采集的 schema（Oracle 中即用户），缺省取数据源的 `dbschema`，再缺省取用户名大写 |

### 可选参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `tableNamePattern` | String | ❌ | 表名过滤正则，为空表示采集 schema 下所有表，不区分大小写 | `"CDC_.*"` |
| `startScn` | Long | ❌ | 指定起始 SCN，为空表示从上次保存位点或当前 SCN 开始 | `2149300` |
| `pollIntervalMs` | Long | ❌ | 日志挖掘轮询间隔（毫秒），默认 `1000` | `500` |
| `snapshot` | Boolean | ❌ | 首次运行（无断点）时先读取历史全量数据，再启动增量同步，默认 `false` | `true` |
| `snapshotFetchSize` | Integer | ❌ | 快照分批大小，默认 `10000` | `5000` |
| `ignoreError` | Boolean | ❌ | 采集出错时是否仅记录日志而不中断任务，默认 `true` | `false` |

## 数据源配置 (DatasourceInfo)

```json
{
  "type": "oracle",
  "url": "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
  "driver": "oracle.jdbc.OracleDriver",
  "username": "datay",
  "password": "datay123",
  "dbschema": "DATAY"
}
```

## 配置示例

### 示例1：采集 schema 下全部表

```json
{
  ".id": "OracleCDCInput",
  ".name": "OracleCDCInput",
  "datasource": {
    "url": "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
    "username": "datay",
    "password": "datay123",
    "dbschema": "DATAY"
  },
  "schema": "DATAY"
}
```

### 示例2：按表过滤 + 指定轮询间隔

```json
{
  ".id": "oracle-cdc-input",
  ".name": "OracleCDCInput",
  "datasource": {
    "url": "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
    "username": "datay",
    "password": "datay123",
    "dbschema": "DATAY"
  },
  "schema": "DATAY",
  "tableNamePattern": "CDC_DEMO|CUSTOMERS",
  "pollIntervalMs": 1000
}
```

### 示例3：从指定 SCN 开始

```json
{
  ".id": "oracle-cdc-input",
  ".name": "OracleCDCInput",
  "datasource": {
    "url": "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
    "username": "datay",
    "password": "datay123"
  },
  "startScn": 2149300
}
```

## 工作原理

1. **校验与解析**：校验数据库处于 `ARCHIVELOG` 模式并开启补充日志，解析采集表范围。
2. **建立挖掘会话**：在同一 JDBC 连接（同一 Oracle 会话）上完成 LogMiner 的开始、查询与结束；并设置 NLS 时间格式便于取值。
3. **注册日志并启动**：非 PDB 环境下按 SCN 区间注册在线与归档日志；PDB 环境下（不允许 `ADD_LOGFILE`）由 `START_LOGMNR` 自动定位所需日志。
4. **挖掘变更**：调用 `DBMS_LOGMNR.START_LOGMNR(STARTSCN, ENDSCN, OPTIONS => DICT_FROM_ONLINE_CATALOG + COMMITTED_DATA_ONLY)`，查询 `V$LOGMNR_CONTENTS`。
5. **取值与转换**：按表列定义使用 `DBMS_LOGMNR.MINE_VALUE` 提取前后镜像，数值按列 scale 规范化，时间转为 ISO-8601。
6. **事件下发**：转换为通用 CDC 事件（`BinlogEvent`）交给下游，事务以已提交的最大 SCN 作为位点。
7. **状态保存**：位点（`binlogPosition` = 已提交 SCN）随 FlowFile 持久化，任务重启自动续采。

## 首次全量快照（snapshot）

开启 `snapshot=true` 后，组件首次运行（无历史断点、无 `snapshotDone` 标记）会：

1. 先获取当前 SCN 作为增量起点（保证快照窗口内的变更不丢）；
2. 对 schema 下匹配 `tableNamePattern` 的表做一次性全量读取，按 `INSERT` 事件分批下发；
3. 再从上述 SCN 启动 LogMiner 增量同步；
4. 全量完成后写入 `snapshotDone` 状态，任务重启有断点或已完成快照时不会重复全量。

> 采用「简单一致性」策略：快照窗口内发生的变更会由 CDC 补放，存在少量重复的可能；对幂等写入或存量基本稳定的场景足够。

## 与其它 CDC 的差异与限制

- **不支持 DDL**：LogMiner 数据挖掘仅发送数据变更（`INSERT/UPDATE/DELETE`），不发送 `ALTER TABLE` 等 DDL。
- **LOB 列**：`CLOB/BLOB/LONG/ROWID` 等类型暂不通过 `MINE_VALUE` 取值（会被忽略并告警），含此类列的表建议仅同步普通列或改用其它方式。
- **UPDATE 未变更列**：需开启 `SUPPLEMENTAL LOG DATA (ALL) COLUMNS`，否则未变更列可能缺失。
- **长事务**：跨越轮询窗口且提交较晚的长事务存在极小概率丢失，建议事务不要长时间持有；存量数据建议先做全量。
- **初始数据**：增量默认只捕获组件启动后的变更，存量数据请开启 `snapshot` 或另行全量同步。

## 故障排除

**问题：数据库未开启归档模式**
- 报错提示未开启 `ARCHIVELOG`，需以 SYSDBA 重启到 MOUNT 后执行 `ALTER DATABASE ARCHIVELOG`。

**问题：权限不足 / `DBMS_LOGMNR` 执行失败**
- 确认采集账号具备 `LOGMINING` 角色与 `SELECT ANY TRANSACTION` 权限，并在访问表所在容器（PDB）中授权。

**问题：PDB 中 `ORA-65040 不允许从可插入数据库内部执行此操作`**
- 新版组件已自动识别 PDB 并跳过 `ADD_LOGFILE`，由 `START_LOGMNR` 自动定位日志；请升级到最新版本。

**问题：UPDATE 事件部分列为空**
- 开启全列补充日志：`ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;`。

**问题：任务重启后数据重复或缺失**
- 位点以已提交 SCN 持久化，保持数据源与 schema 配置稳定；长事务场景注意上述限制。

---

*本文档基于 OracleCDCInput 组件 v1.0 版本编写*
