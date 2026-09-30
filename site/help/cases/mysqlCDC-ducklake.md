# MySQL CDC 实时同步到 DuckLake 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将 MySQL 的数据变更（CDC）实时同步到 **DuckLake** 数据湖（本地文件元数据 + 本地数据目录，无需 S3）。

- **数据源**：MySQL（Binlog，ROW 模式）
- **CDC 输入组件**：`MySQLBinlogInput`
- **输出组件**：`DuckLakeWrite`（`model=auto`）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> DuckLake **不支持主键 / 唯一约束**，因此 UPDATE 事件通过 binlog 的**变更前镜像**作为 WHERE 条件执行 `UPDATE`，DELETE 事件同样按前镜像条件删除。

> 注意：Binlog 只捕获组件启动之后的**增量**变更；存量数据请先用 `StreamJdbcInput` 做一次全量同步。
>
> 也可直接在 CDC 组件上开启 `snapshot=true`，首次运行时自动“先全量、后增量”（无断点时才执行）。

## 2. 环境准备

### 2.1 确认 MySQL 已开启 Binlog

```sql
SHOW VARIABLES LIKE 'log_bin';       -- 期望 ON
SHOW VARIABLES LIKE 'binlog_format';  -- 期望 ROW
SHOW VARIABLES LIKE 'binlog_row_image'; -- 建议 FULL（UPDATE/DELETE 需要完整前镜像）
```

### 2.2 授予复制权限并建表

```sql
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'root'@'%';

CREATE DATABASE IF NOT EXISTS test DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE test.cdc_demo (
    id          bigint PRIMARY KEY,
    name        varchar(100),
    amount      decimal(10,2),
    updated_at  datetime
);
```

### 2.3 DuckLake 准备

DuckDB 会自动 `INSTALL/LOAD ducklake` 扩展（需可联网或已缓存扩展）。本案例使用本地路径：

- 元数据文件：`./target/cdc-mysql.ducklake`
- 数据目录：`./target/cdc-mysql-ducklake-data`

## 3. 任务配置文件

创建 `mysqlCdcToDuckLake.json`：

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
      ".id": "DuckLakeWrite",
      ".name": "DuckLakeWrite",
      "sourceId": {
        "url": "ducklake:./target/cdc-mysql.ducklake",
        "driver": "org.duckdb.DuckDBDriver",
        "dbschema": "main",
        "extraParams": {
          "s3.data_path": "./target/cdc-mysql-ducklake-data"
        }
      },
      "schema": "main",
      "table": "",
      "model": "auto"
    }
  ],
  "connections": [
    {
      "sourceId": "MySQLBinlogInput",
      "targetId": "DuckLakeWrite",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

> `s3.data_path` 支持 `s3://` 路径（需同时配置 `s3.key_id`/`s3.secret`/`s3.endpoint` 等）或本地目录。

## 4. 执行 CDC 同步

```bash
# 克隆仓库
git clone https://cnb.cool/yuyuejia/datay

# 构建项目
cd datay
mvn clean package -DskipTests

# 执行任务
java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar mysqlCdcToDuckLake.json
```

## 5. 验证同步效果

在源库执行：

```sql
INSERT INTO test.cdc_demo VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00');
INSERT INTO test.cdc_demo VALUES (2, 'bob',   20.00, '2024-01-02 11:00:00');
UPDATE test.cdc_demo SET name = 'alice2', amount = 11.11 WHERE id = 1;
DELETE FROM test.cdc_demo WHERE id = 2;
```

DuckLake 结果：

```
id | name   | amount | updated_at
1  | alice2 | 11.11  | 2024-01-01 10:00:00
```

### 自动化的端到端测试

```bash
mvn -pl datay-core test -Dtest=MysqlCdcToDuckLakeTest
```

测试覆盖 INSERT / UPDATE / DELETE，并校验 `datetime` 字段墙上时间保持一致。

## 6. 写入策略说明（DuckLake）

| 事件 | 写入策略 |
|------|----------|
| INSERT | 多值 `INSERT INTO ... VALUES (...), (...)` |
| UPDATE | `UPDATE ... SET ... WHERE <变更前镜像所有列>`（DuckLake 无主键） |
| DELETE | `DELETE ... WHERE <变更前镜像所有列>` |

> 依赖 `binlog_row_image=FULL` 提供完整前镜像；若为非 FULL，UPDATE/DELETE 可能无法精确匹配。

## 7. 常见问题

- **DuckLake 不支持 PRIMARY KEY/UNIQUE**：建表自动不创建主键，属预期行为。
- **`INSERT OR REPLACE` 报错**：DuckLake 不支持；本组件已改为基于前镜像的 `UPDATE`。
- **扩展安装失败**：确保 DuckDB 可访问扩展源或已缓存 `ducklake` 扩展。
- **只同步到启动后的变更**：存量数据请另行全量同步。

---

通过本案例，您可以快速搭建 MySQL → DuckLake 的实时 CDC 同步链路。
