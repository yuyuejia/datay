# 达梦数据库实时同步：不用 DMHS，也能把 CDC 跑起来

> 很多国产化项目做到后期，都会遇到同一个问题：
>
> 业务库换成了达梦，实时同步怎么办？
>
> 上 DMHS？要单独部署、单独授权、单独运维。
> 写定时任务轮询？延迟高、源库压力大，还抓不到中间状态。
>
> 其实达梦自己也提供了日志挖掘能力：`DBMS_LOGMNR`。
> 用好了，它可以把每一次 INSERT、UPDATE、DELETE 变成一条实时数据流。

这篇文章就以 **达梦 CDC → MySQL / DuckDB** 为例，讲清楚三件事：

1. 达梦 CDC 到底是怎么采的；
2. 落地时必须处理哪些坑；
3. 如何用一条统一的数据流，把达梦变更同步到不同目标库。

## 01 为什么达梦 CDC 不能照搬 MySQL

MySQL 的 CDC 很直接：开 Binlog，伪装成一个从库，持续订阅就行。

达梦不一样。

达梦的变更记录在重做日志里，而 `DBMS_LOGMNR` 目前**只能分析归档日志**。这意味着：

- 只开逻辑日志还不够，必须开启归档；
- 在线 redo 不能直接挖，必须先把日志切成归档；
- 想做到秒级同步，就需要周期性执行归档切换；
- 采集账号还要有归档切换、日志挖掘和相关 `V$` 视图权限。

所以，达梦 CDC 的核心不是“查一张表”，而是这条链路：

```text
业务 DML
   ↓
在线 Redo Log
   ↓  ALTER SYSTEM ARCHIVE LOG CURRENT
本地归档日志
   ↓  DBMS_LOGMNR.ADD_LOGFILE
DBMS_LOGMNR 日志挖掘
   ↓  V$LOGMNR_CONTENTS
INSERT / UPDATE / DELETE 事件
   ↓
统一 CDC 数据流
   ↓
MySQL / DuckDB / 其它目标库
```

这条链路里，归档切换是达梦 CDC 和 Oracle CDC 最大的区别，也是实时性的关键。

## 02 前置条件：四步把达梦变成可采集状态

### 第一步：开启归档

以 `SYSDBA` 执行：

```sql
ALTER DATABASE MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE ADD ARCHIVELOG
  'TYPE=LOCAL, DEST=/opt/dmdbms/arch, FILE_SIZE=256, SPACE_LIMIT=1024';
ALTER DATABASE OPEN;
```

检查：

```sql
SELECT ARCH_MODE FROM V$DATABASE;
-- 期望 Y
```

### 第二步：开启逻辑日志

```sql
SP_SET_PARA_VALUE(1, 'RLOG_APPEND_LOGIC', 2);
SP_SET_PARA_VALUE(1, 'RLOG_IGNORE_TABLE_SET', 1);
```

推荐值直接给到 `2`：

- `1`：有主键的表，UPDATE 只记录主键和变更列，DELETE 只记录主键；
- `2`：记录完整行信息，前后镜像更完整；
- `3`：只有 ROWID，无法还原 DML；
- `4`：只记录事务和 DDL，不能挖 DML。

**注意：修改 `RLOG_APPEND_LOGIC` 后需要重启达梦实例，新的日志级别才会真正生效。** 只改参数不重启，是很多现场“参数明明配了却没数据”的原因。

### 第三步：创建日志挖掘系统包

```sql
SP_CREATE_SYSTEM_PACKAGES(1, 'DBMS_LOGMNR');
```

### 第四步：给采集账号授权

```sql
GRANT SELECT ANY TABLE TO DATAY;
GRANT SELECT ON V$DM_INI TO DATAY;
GRANT SELECT ON V$DATABASE TO DATAY;
GRANT SELECT ON V$RLOG TO DATAY;
GRANT SELECT ON V$ARCHIVED_LOG TO DATAY;
GRANT SELECT ON V$DM_ARCH_INI TO DATAY;
GRANT SELECT ON V$LOGMNR_CONTENTS TO DATAY;
GRANT SELECT ON V$LOGMNR_LOGS TO DATAY;

GRANT EXECUTE ON DBMS_LOGMNR TO DATAY;

-- 自动切归档需要
GRANT ALTER DATABASE TO DATAY;
```

生产环境建议按最小权限授权，不要直接给 DBA。

## 03 达梦 CDC 最容易踩的三个坑

### 坑一：只读在线日志

达梦 `DBMS_LOGMNR` 只能挖归档日志。

如果不主动切归档，就只能等达梦自己把 redo 写满、切归档之后才能采到数据，延迟可能非常不可控。

DataY 的 `DMCDCInput` 默认会周期性执行：

```sql
ALTER SYSTEM ARCHIVE LOG CURRENT
```

默认 5 秒一次，也可以通过 `archiveSwitchIntervalMs` 调整。归档切换间隔不能太短，否则会产生大量小归档文件。

### 坑二：UPDATE 拿不到未变更列

达梦 `MINE_VALUE` 对 UPDATE 通常只返回值发生变化的列。

也就是说，一条：

```sql
UPDATE CDC_DEMO SET NAME = 'alice2' WHERE ID = 1;
```

通过 `MINE_VALUE` 可能只能拿到 `NAME`，主键 `ID` 和未变更的 `AMOUNT` 都可能是空。

这会导致下游按主键更新时找不到行，或者把未变更列覆盖成 NULL。

DataY 的处理方式是：

- 先按列读取 `MINE_VALUE`；
- 同时解析 `V$LOGMNR_CONTENTS.SQL_REDO` 的 `SET` 和 `WHERE` 子句；
- `SET` 里是变更后的值，`WHERE` 里是变更前的完整旧行；
- 两者合并，补全 UPDATE 前后镜像；
- 对 `CSF / SSN` 拆分的超长 SQL 片段进行拼接。

这样，下游拿到的 UPDATE 事件就是完整行，而不是“只带变更列的半成品”。

### 坑三：归档被清理，位点追不回来

达梦本地归档有空间上限。

如果任务停得太久，位点对应的归档已经被 `SPACE_LIMIT` 清理，CDC 就无法从断点继续。

DataY 的处理原则是：

- 位点使用已提交事务的最大 `COMMIT_SCN`；
- 位点随数据流持久化，任务重启自动续采；
- 发现最早可用归档 SCN 大于续采位点时明确告警，而不是静默跳过。

上线前一定要确认：**归档保留时间要覆盖任务可能的最长停机时间。**

## 04 一条配置，把达梦变更接进数据流

DataY 的达梦 CDC 输入组件是 `DMCDCInput`。

最小配置：

```json
{
  ".id": "dm_cdc",
  ".name": "DMCDCInput",
  "sourceId": {
    "type": "dm",
    "url": "jdbc:dm://127.0.0.1:5236",
    "driver": "dm.jdbc.driver.DmDriver",
    "username": "DATAY",
    "password": "******",
    "dbschema": "DATAY"
  },
  "schema": "DATAY",
  "tableNamePattern": "CDC_.*",
  "snapshot": true,
  "archiveSwitch": true,
  "archiveSwitchIntervalMs": 5000,
  "pollIntervalMs": 1000
}
```

几个参数的含义：

| 参数 | 说明 |
| --- | --- |
| `tableNamePattern` | 表名过滤正则，不区分大小写；为空采集整个 schema |
| `snapshot` | 首次运行先全量后增量，存量数据不会丢 |
| `archiveSwitch` | 是否主动切归档，默认开启 |
| `archiveSwitchIntervalMs` | 归档切换间隔，默认 5000ms |
| `pollIntervalMs` | 日志挖掘轮询间隔，默认 1000ms |
| `startScn` | 指定起始 SCN，一般留空，从上次位点续采 |
| `snapshotFetchSize` | 全量快照分批大小，默认 10000 |
| `includeEventMeta` | 是否在数据行中追加事件类型和时间戳 |
| `reconnectMaxAttempts` | 连接异常最大重连次数，默认 5 |

## 05 全量 + 增量：snapshot 不是可选项

CDC 默认只捕获任务启动之后的变更。

如果源表已经有几百万条历史数据，直接开 CDC，目标端只会拿到新数据。

开启 `snapshot=true` 后，首次运行会：

1. 先记录当前 SCN 作为增量起点；
2. 对 schema 下匹配表做一次性全量读取；
3. 按 INSERT 事件分批下发；
4. 再从上述 SCN 启动 LogMiner 增量同步；
5. 完成后写入 `snapshotDone` 状态，重启不会重复全量。

它采用的是一致性策略叫“简单一致性”：

> 增量位点取在快照之前，快照期间发生的变更由后续 CDC 再补一遍。

好处是切换快、实现轻；代价是可能出现少量重复。对按主键幂等写入的场景完全够用。

## 06 同步到 MySQL：大写源表，小写目标表

达梦的表名、字段名通常是大小写敏感的，很多现场建表又是大写。

但 MySQL 在 Linux 下默认是大小写敏感的表名，而且团队通常习惯全小写。

如果建表时用了小写、写入时用了大写，就会直接报：

```text
Table 'datay_dm_cdc.CDC_DEMO' doesn't exist
```

DataY 的处理方式不是在下游组件里写死 MySQL 判断，而是把“目标表名规范化”下沉到数据源 Provider：

- `JdbcDataSourceProvider.normalizeTargetTableName()` 默认原样返回；
- `MysqlDataSourceProvider` 覆写为转小写；
- MariaDB、TiDB、PolarDB、Doris、OceanBase、StarRocks 等 MySQL 系 Provider 自动继承；
- `StreamJdbcOutput` 只负责调用 Provider，不再出现具体数据库分支。

这样：

```text
达梦：DATAY.CDC_DEMO
MySQL：datay_dm_cdc.cdc_demo
```

建表 DDL 和后续 INSERT / UPDATE / DELETE 使用同一个表名，不会再出现大小写不一致。

## 07 常用字段类型能同步吗

我们实际跑了一遍达梦 → MySQL 的常用类型同步。

达梦源表：

```sql
CREATE TABLE DATAY.CDC_DM_MYSQL (
  ID INT PRIMARY KEY,
  C_TINYINT TINYINT,
  C_SMALLINT SMALLINT,
  C_INT INT,
  C_BIGINT BIGINT,
  C_DECIMAL DECIMAL(12,4),
  C_NUMBER NUMBER(12,4),
  C_FLOAT FLOAT,
  C_DOUBLE DOUBLE,
  C_CHAR CHAR(10),
  C_VARCHAR VARCHAR(100),
  C_VARCHAR2 VARCHAR2(100),
  C_NVARCHAR2 NVARCHAR2(50),
  C_DATE DATE,
  C_TIME TIME,
  C_DATETIME DATETIME,
  C_TIMESTAMP TIMESTAMP,
  C_BIT BIT
);
```

验证结果：

| 达梦类型 | MySQL 目标类型 | 结果 |
| --- | --- | --- |
| `TINYINT` | `TINYINT` | ✅ |
| `SMALLINT` | `SMALLINT` | ✅ |
| `INT` | `INT` | ✅ |
| `BIGINT` | `BIGINT` | ✅ |
| `DECIMAL` / `NUMBER` | `DECIMAL` | ✅ |
| `FLOAT` / `DOUBLE` | `DOUBLE` | ✅ |
| `CHAR` | `CHAR` | ✅ |
| `VARCHAR` / `VARCHAR2` / `NVARCHAR2` | `VARCHAR` | ✅ |
| `DATE` | `DATE` | ✅ |
| `TIME` | `TIME` | ✅ |
| `DATETIME` / `TIMESTAMP` | `DATETIME` | ✅ |
| `BIT` | `TINYINT` | ✅ |

快照、INSERT、UPDATE、NULL 行、DELETE 全部验证通过。

同样的场景也验证了 **达梦 → DuckDB**、**Oracle → MySQL**。达梦 CDC 不是只能写给一个目标库，它输出的是统一事件流，下游换一个输出组件即可。

## 08 达梦 CDC 的边界

实时同步没有银弹，达梦 CDC 也有明确边界：

- **不支持 DDL**：只发 DML，源表加字段需要单独处理；
- **LOB / 二进制列不支持取值**：`CLOB/BLOB/TEXT/IMAGE/ROWID/XML/BINARY/RAW/ARRAY` 会被忽略并告警；
- **只支持本地归档**：`ADD_LOGFILE` 只能添加数据库实例本机的归档文件；
- **DMDPC/MPP 有限制**：不支持同时分析不同节点的日志；
- **长事务风险**：跨越轮询窗口、提交很晚的长事务存在极小概率丢失；
- **归档空间要规划**：保留空间必须覆盖任务最长停机时间。

上线前建议至少检查：

1. `ARCH_MODE=Y`；
2. `RLOG_APPEND_LOGIC=2` 且已重启；
3. `RLOG_IGNORE_TABLE_SET=1`；
4. `DBMS_LOGMNR` 包已创建；
5. 采集账号有归档切换和日志挖掘权限；
6. 采集范围内所有表都有主键；
7. 归档保留空间足够。

## 09 最后

达梦 CDC 的难点，不在“写一个 JDBC 查询”。

真正的难点在日志链路：

- 在线 redo 怎么变成可挖的归档；
- 归档日志怎么发现、注册、清理；
- UPDATE 的未变更列和主键怎么补全；
- 位点怎么存、连接断了怎么续；
- 目标库表名、字段类型、主键写入怎么对齐。

把这些都收进组件之后，用户看到的就只剩一条普通数据流：

```text
达梦变更 → 统一 CDC 事件 → 清洗 / 路由 → 幂等写入目标库
```

这也是 DataY 想做的事：**把数据库之间的差异留在组件里，把简单留给使用数据的人。**

---

**DataY** 是一个开源、轻量的一体化数据平台：

- 一个 JAR、内嵌 DuckDB，2 核 4G 就能跑；
- 支持 MySQL / PostgreSQL / Oracle / 达梦 / MongoDB 等 CDC 实时同步；
- 支持全量 + 增量、断点续传、自动建表、跨库类型映射；
- 支持可视化拖拽编排，也支持 JSON 定义任务。

项目地址：

- GitHub：<https://github.com/yuyuejia/datay>
- Gitee：<https://gitee.com/yuyuejia/datay>
- 产品首页：<http://datay.yuyuejia.com.cn/>
