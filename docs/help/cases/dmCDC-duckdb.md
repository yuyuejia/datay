# 达梦 CDC 实时同步到 DuckDB 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将达梦 8 的数据变更（CDC）实时同步到内嵌 DuckDB。

- **数据源**：达梦 8（`DBMS_LOGMNR` 归档日志挖掘）
- **CDC 输入组件**：`DMCDCInput`
- **输出组件**：`StreamJdbcOutput`（`model=auto`，写入 DuckDB）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> 达梦 `DBMS_LOGMNR` 只能分析归档日志。组件默认会周期性执行
> `ALTER SYSTEM ARCHIVE LOG CURRENT`，保证秒级采集；存量数据可开启 `snapshot=true` 先全量后增量。

## 2. 环境准备

### 2.1 一键启动达梦验证实例（Docker）

仓库内提供了达梦 8 容器的启动与配置脚本：

```bash
cd datay-core/src/test/docker/dm
./start.sh
```

脚本会自动开启归档、逻辑日志，创建 `DBMS_LOGMNR` 系统包，并创建测试用户
`DATAY / Datay123456`。详见该目录下的 `README.md`。

### 2.2 手动准备（自建达梦）

```sql
-- 开启归档（SYSDBA）
ALTER DATABASE MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE ADD ARCHIVELOG 'TYPE=LOCAL, DEST=/opt/dmdbms/arch, FILE_SIZE=256, SPACE_LIMIT=1024';
ALTER DATABASE OPEN;

-- 记录完整逻辑日志（2 = 完整前后镜像）
SP_SET_PARA_VALUE(1, 'RLOG_APPEND_LOGIC', 2);
SP_SET_PARA_VALUE(1, 'RLOG_IGNORE_TABLE_SET', 1);

-- 创建日志挖掘系统包
SP_CREATE_SYSTEM_PACKAGES(1, 'DBMS_LOGMNR');

-- 创建采集账号（示例）
CREATE USER DATAY IDENTIFIED BY "Datay123456" DEFAULT TABLESPACE MAIN;
GRANT DBA TO DATAY;
```

> 修改 `RLOG_APPEND_LOGIC` 后需要**重启达梦实例**，新的逻辑日志级别才会生效。

### 2.3 创建测试表

```sql
CREATE TABLE DATAY.CDC_DEMO (
    ID          INT PRIMARY KEY,
    NAME        VARCHAR(100),
    AMOUNT      DECIMAL(10,2),
    UPDATED_AT  TIMESTAMP
);
```

## 3. 任务配置文件

创建 `dmCdcToDuckDB.json`：

```json
{
  "units": [
    {
      ".id": "DMCDCInput",
      ".name": "DMCDCInput",
      "sourceId": {
        "type": "dm",
        "url": "jdbc:dm://127.0.0.1:5236",
        "driver": "dm.jdbc.driver.DmDriver",
        "username": "DATAY",
        "password": "Datay123456",
        "dbschema": "DATAY"
      },
      "schema": "DATAY",
      "tableNamePattern": "CDC_DEMO",
      "snapshot": true,
      "archiveSwitch": true,
      "archiveSwitchIntervalMs": 5000
    },
    {
      ".id": "StreamJdbcOutput",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "url": "jdbc:duckdb:./data/dm_cdc_demo.duckdb",
        "driver": "org.duckdb.DuckDBDriver",
        "dbschema": "main"
      },
      "schema": "main",
      "table": "",
      "model": "auto"
    }
  ],
  "connections": [
    {
      "sourceId": "DMCDCInput",
      "targetId": "StreamJdbcOutput",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

## 4. 执行 CDC 同步

```bash
# 构建（跳过测试）
mvn clean package -DskipTests

# 运行（datay-core 可执行包）
java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar dmCdcToDuckDB.json
```

任务启动后，组件会自动：校验归档与逻辑日志、读取起始 SCN、切换归档并按 SCN 区间挖掘变更并下发。

## 5. 验证同步效果

在源库执行以下操作：

```sql
INSERT INTO DATAY.CDC_DEMO VALUES (1, 'alice', 10.50, TIMESTAMP '2024-01-01 10:00:00');
INSERT INTO DATAY.CDC_DEMO VALUES (2, 'bob',   20.00, TIMESTAMP '2024-01-02 11:00:00');
UPDATE DATAY.CDC_DEMO SET NAME = 'alice2', AMOUNT = 11.11 WHERE ID = 1;
DELETE FROM DATAY.CDC_DEMO WHERE ID = 2;
COMMIT;
```

目标 DuckDB 会自动建表并同步，最终结果：

```
ID | NAME   | AMOUNT | UPDATED_AT
1  | alice2 | 11.11  | 2024-01-01 10:00:00
```

### 自动化的端到端测试

仓库内提供了可直接运行的集成测试，覆盖快照、INSERT / UPDATE / DELETE：

```bash
# 先启动并配置达梦验证实例
cd datay-core/src/test/docker/dm && ./start.sh

cd /path/to/datay
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=DmCdcToDuckDBTest
```

测试会：校验前置条件（不满足自动跳过）→ 重置环境 → 后台启动 CDC 任务 → 执行 DML →
轮询断言 DuckDB 数据 → 清理目标文件。

常用字段类型覆盖测试：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=DmCdcTypesToDuckDBTest
```

覆盖 `TINYINT/SMALLINT/INT/BIGINT`、`DECIMAL/NUMBER`、`FLOAT/DOUBLE`、
`CHAR/VARCHAR/VARCHAR2/NVARCHAR2`、`DATE/TIME/DATETIME/TIMESTAMP`、`BIT` 以及 NULL 行。

## 6. 组件参数速查

`DMCDCInput`：

| 参数 | 必填 | 说明 |
|------|------|------|
| `datasource` | ✅ | 达梦连接信息（url/username/password/dbschema） |
| `schema` | ❌ | 采集模式名，缺省取 `datasource.dbschema`，再缺省取用户名大写 |
| `tableNamePattern` | ❌ | 表名过滤正则（不区分大小写），为空采集全部 |
| `startScn` | ❌ | 起始 SCN，缺省从上次位点或当前 SCN 开始 |
| `pollIntervalMs` | ❌ | 轮询间隔（毫秒），默认 1000 |
| `archiveSwitch` | ❌ | 是否主动切归档，默认 true |
| `archiveSwitchIntervalMs` | ❌ | 归档切换间隔（毫秒），默认 5000 |
| `snapshot` | ❌ | 首次先全量后增量，默认 false |

`StreamJdbcOutput`（`model=auto`）写入策略：

| 事件 | 写入策略 |
|------|----------|
| INSERT | DuckDB Appender 批量写入 |
| UPDATE | `INSERT OR REPLACE`（按主键） |
| DELETE | 按主键 `DELETE` |

## 7. 常见问题

- **未开启归档模式**：`ARCH_MODE` 必须为 `Y`，否则无法使用 `DBMS_LOGMNR`。
- **`RLOG_APPEND_LOGIC=0`**：达梦不会记录逻辑操作，需设置为 `2`。
- **`DBMS_LOGMNR` 包不存在**：执行 `SP_CREATE_SYSTEM_PACKAGES(1,'DBMS_LOGMNR')`。
- **归档切换权限不足**：授予归档切换权限，或关闭 `archiveSwitch` 仅消费已有归档。
- **源表结构变更不同步**：LogMiner 不传输 DDL，需手动同步或重建目标表。
- **只同步到启动后的变更**：存量数据请开启 `snapshot` 或另行全量同步。
- **LOB/二进制列不同步**：`CLOB/BLOB/TEXT/IMAGE/ROWID/XML/BINARY/RAW` 等类型当前暂不支持取值。

---

通过本案例，您可以快速搭建达梦 → DuckDB 的实时 CDC 同步链路。
