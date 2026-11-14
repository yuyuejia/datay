# SQL Server CDC 实时同步到 DuckDB 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将 SQL Server 的数据变更（CDC）实时同步到内嵌 DuckDB。

- **数据源**：SQL Server 2012 及以上（内置 CDC）
- **CDC 输入组件**：`SQLServerCDCInput`
- **输出组件**：`StreamJdbcOutput`（`model=auto`，写入 DuckDB）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> SQL Server CDC 由 SQL Server Agent 的 capture job 驱动，默认约 5 秒捕获一次。
> 存量数据可开启 `snapshot=true` 先全量后增量。

## 2. 环境准备

### 2.1 一键启动 SQL Server 验证实例（Docker）

仓库内提供了 SQL Server 2022 容器的启动与配置脚本：

```bash
cd datay-core/src/test/docker/sqlserver
./start.sh
```

脚本会自动：

1. 启动 SQL Server 2022（启用 SQL Server Agent）；
2. 等待 14333 端口可连接；
3. 创建数据库 `datay_cdc` 与测试账号 `datay / Datay123456!`；
4. 创建测试表并开启 CDC；
5. 校验数据库 CDC、表 CDC 与捕获作业状态。

详见该目录下的 `README.md`。

### 2.2 手动准备（自建 SQL Server）

```sql
-- 1. 开启数据库 CDC（db_owner / sysadmin）
USE [datay_cdc];
EXEC sys.sp_cdc_enable_db;

-- 2. 确保 SQL Server Agent 正在运行，并启动 CDC 捕获作业
EXEC sys.sp_cdc_help_jobs;
EXEC sys.sp_cdc_start_job @job_type = N'capture';

-- 3. 创建测试表
CREATE TABLE dbo.CDC_DEMO (
    ID          INT PRIMARY KEY,
    NAME        NVARCHAR(100),
    AMOUNT      DECIMAL(18,2),
    UPDATED_AT  DATETIME2(3)
);

-- 4. 为表开启 CDC（捕获所有列）
EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name   = N'CDC_DEMO',
    @role_name     = NULL,
    @supports_net_changes = 0;

-- 5. 创建采集账号并授权
CREATE LOGIN datay WITH PASSWORD = 'Datay123456!';
CREATE USER datay FOR LOGIN datay;
GRANT SELECT ON dbo.CDC_DEMO TO datay;
GRANT SELECT ON SCHEMA::cdc TO datay;

-- 仅在使用 autoEnableCdc 时需要
ALTER ROLE db_owner ADD MEMBER datay;
```

> SQL Server Express 没有 SQL Server Agent，不支持 CDC。

## 3. 任务配置文件

创建 `sqlServerCdcToDuckDB.json`：

```json
{
  "units": [
    {
      ".id": "SQLServerCDCInput",
      ".name": "SQLServerCDCInput",
      "sourceId": {
        "type": "sqlserver",
        "url": "jdbc:sqlserver://127.0.0.1:14333;databaseName=datay_cdc",
        "driver": "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "username": "datay",
        "password": "Datay123456!",
        "dbschema": "dbo"
      },
      "schema": "dbo",
      "tableNamePattern": "CDC_DEMO",
      "snapshot": true,
      "pollIntervalMs": 1000
    },
    {
      ".id": "StreamJdbcOutput",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "url": "jdbc:duckdb:./data/sqlserver_cdc_demo.duckdb",
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
      "sourceId": "SQLServerCDCInput",
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
java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar sqlServerCdcToDuckDB.json
```

任务启动后，组件会自动：校验数据库 CDC、解析目标表与 capture instance、读取起始 LSN，
然后按 LSN 窗口读取 change table 并下发。

## 5. 验证同步效果

在源库执行以下操作：

```sql
INSERT INTO dbo.CDC_DEMO VALUES (1, N'alice', 10.50, '2024-01-01T10:00:00');
INSERT INTO dbo.CDC_DEMO VALUES (2, N'bob',   20.00, '2024-01-02T11:00:00');
UPDATE dbo.CDC_DEMO SET NAME = N'alice2', AMOUNT = 11.11 WHERE ID = 1;
DELETE FROM dbo.CDC_DEMO WHERE ID = 2;
```

目标 DuckDB 会自动建表并同步，最终结果：

```
ID | NAME   | AMOUNT | UPDATED_AT
1  | alice2 | 11.11  | 2024-01-01 10:00:00
```

### 自动化的端到端测试

仓库内提供了可直接运行的集成测试，覆盖快照、INSERT / UPDATE / DELETE：

```bash
# 先启动并配置 SQL Server 验证实例
cd datay-core/src/test/docker/sqlserver && ./start.sh

cd /path/to/datay
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=SqlServerCdcToDuckDBTest
```

测试会：校验前置条件（不满足自动跳过）→ 重置环境 → 后台启动 CDC 任务 → 执行 DML →
轮询断言 DuckDB 数据 → 清理目标文件。

常用字段类型覆盖测试：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=SqlServerCdcTypesToDuckDBTest
```

覆盖 `TINYINT/SMALLINT/INT/BIGINT`、`DECIMAL/NUMERIC/MONEY`、`FLOAT/REAL`、
`CHAR/VARCHAR/NCHAR/NVARCHAR`、`DATE/TIME/DATETIME2/DATETIMEOFFSET`、
`UNIQUEIDENTIFIER`、`VARBINARY`、`BIT` 以及 NULL 行。

## 6. 组件参数速查

`SQLServerCDCInput`：

| 参数 | 必填 | 说明 |
|------|------|------|
| `datasource` / `sourceId` | ✅ | SQL Server 连接信息（url/username/password/dbschema） |
| `schema` | ❌ | 采集 schema，缺省取 `datasource.dbschema`，再缺省为 `dbo` |
| `tableNamePattern` | ❌ | 表名过滤正则（不区分大小写），为空采集全部已开启 CDC 的表 |
| `startLsn` | ❌ | 起始 LSN（十六进制），缺省从上次位点或当前位置开始 |
| `pollIntervalMs` | ❌ | 轮询间隔（毫秒），默认 1000 |
| `autoEnableCdc` | ❌ | 未开启 CDC 时自动开启，默认 false，需要 db_owner |
| `snapshot` | ❌ | 首次先全量后增量，默认 false |

`StreamJdbcOutput`（`model=auto`）写入策略：

| 事件 | 写入策略 |
|------|----------|
| INSERT | DuckDB Appender 批量写入 |
| UPDATE | `INSERT OR REPLACE`（按主键） |
| DELETE | 按主键 `DELETE` |

## 7. 常见问题

- **数据库未开启 CDC**：执行 `EXEC sys.sp_cdc_enable_db;`。
- **表未开启 CDC**：执行 `sys.sp_cdc_enable_table`，或打开组件的 `autoEnableCdc`。
- **SQL Server Agent 未运行**：change table 不会产生新数据；检查 Agent 服务和 `sys.sp_cdc_help_jobs`。
- **SQL Server Express**：没有 Agent，不支持 CDC。
- **错误 313 / LSN 区间无效**：位点早于 CDC 清理边界或数据库恢复；组件会告警并从最小 LSN 继续。
- **默认保留 3 天变更**：长时间停机可能丢失变更，请调整 cleanup job 或及时恢复任务。
- **源表结构变更不同步**：CDC 不传输 DDL，需要重建 capture instance 或重跑全量。
- **只同步到启动后的变更**：存量数据请开启 `snapshot` 或另行全量同步。
- **text/ntext/image 前镜像为 NULL**：SQL Server CDC 的限制；删除操作只依赖主键，通常不影响同步。

---

通过本案例，您可以快速搭建 SQL Server → DuckDB 的实时 CDC 同步链路。
