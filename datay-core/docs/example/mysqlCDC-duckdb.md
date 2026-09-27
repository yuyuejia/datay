# MySQL CDC 实时同步到 DuckDB 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将本地 MySQL 的数据变更（CDC）实时同步到内嵌 DuckDB。

- **数据源**：MySQL（Binlog，ROW 模式）
- **CDC 输入组件**：`MySQLBinlogInput`
- **输出组件**：`StreamJdbcOutput`（`model=auto`，写入 DuckDB）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> 注意：Binlog 只捕获组件启动之后的**增量**变更；存量数据请先用 `StreamJdbcInput` 做一次全量同步。
>
> 也可直接在 CDC 组件上开启 `snapshot=true`，首次运行时自动“先全量、后增量”（无断点时才执行）。

## 2. 环境准备

### 2.1 确认 MySQL 已开启 Binlog

```sql
SHOW VARIABLES LIKE 'log_bin';       -- 期望 ON
SHOW VARIABLES LIKE 'binlog_format';  -- 期望 ROW
SHOW VARIABLES LIKE 'server_id';      -- 需要非 0
```

若未开启，在 `my.cnf` 中配置后重启：

```ini
[mysqld]
server-id=1
log-bin=mysql-bin
binlog-format=ROW
binlog-row-image=FULL
```

### 2.2 授予复制权限

```sql
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'root'@'%';
FLUSH PRIVILEGES;
```

### 2.3 创建测试表

```sql
CREATE DATABASE IF NOT EXISTS test DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE test.cdc_demo (
    id          bigint PRIMARY KEY,
    name        varchar(100),
    amount      decimal(10,2),
    updated_at  datetime
);
```

## 3. 任务配置文件

创建 `mysqlCdcToDuckDB.json`：

```json
{
  "units": [
    {
      ".id": "MySQLBinlogInput",
      ".name": "MySQLBinlogInput",
      "sourceId": {
        "url": "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "1234qwer",
        "dbschema": "test"
      },
      "databaseNamePattern": "test",
      "tableNamePattern": "cdc_demo"
    },
    {
      ".id": "StreamJdbcOutput",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "url": "jdbc:duckdb:./data/cdc_demo.duckdb",
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
      "sourceId": "MySQLBinlogInput",
      "targetId": "StreamJdbcOutput",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

## 4. 执行 CDC 同步

```bash
mvn clean package -DskipTests

java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar mysqlCdcToDuckDB.json
```

## 5. 验证同步效果

在源库执行：

```sql
INSERT INTO test.cdc_demo VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00');
INSERT INTO test.cdc_demo VALUES (2, 'bob',   20.00, '2024-01-02 11:00:00');
UPDATE test.cdc_demo SET name = 'alice2', amount = 11.11 WHERE id = 1;
DELETE FROM test.cdc_demo WHERE id = 2;
```

目标 DuckDB 会自动建表并同步，最终结果：

```
id | name   | amount | updated_at
1  | alice2 | 11.11  | 2024-01-01 10:00:00
```

### 自动化的端到端测试

```bash
mvn -pl datay-core test -Dtest=MysqlCdcToDuckDBTest
```

测试覆盖 INSERT / UPDATE / DELETE，并校验 `datetime` 字段的墙上时间保持一致（不受 JVM 时区影响）。

## 6. 组件参数速查

`MySQLBinlogInput`：

| 参数 | 必填 | 说明 |
|------|------|------|
| `datasource` | ✅ | MySQL 连接信息（url/username/password/dbschema） |
| `databaseNamePattern` | ❌ | 数据库（schema）过滤正则，为空采集全部 |
| `tableNamePattern` | ❌ | 表名过滤正则，为空采集全部 |
| `binlogFile` | ❌ | 指定起始 Binlog 文件 |
| `binlogPosition` | ❌ | 指定起始位点 |

`StreamJdbcOutput`（`model=auto`）写入策略：

| 事件 | 写入策略 |
|------|----------|
| INSERT | DuckDB Appender 批量写入 |
| UPDATE | `INSERT OR REPLACE`（按主键） |
| DELETE | 按主键 `DELETE` |

## 7. 常见问题

- **MySQL 8.4+/9.x 连接报 `SHOW MASTER STATUS` 语法错误**：需使用 `mysql-binlog-connector-java 0.30.1+`（本项目已升级）。
- **权限不足**：采集账号需 `REPLICATION SLAVE`、`REPLICATION CLIENT`。
- **只同步到启动后的变更**：存量数据请另行全量同步。
- **DDL 跨库同步**：MySQL CDC 的 DDL 事件按数据库/表过滤；跨方言（如 MySQL→DuckDB）的 DDL 语法不支持自动转换。

---

通过本案例，您可以快速搭建 MySQL → DuckDB 的实时 CDC 同步链路。
