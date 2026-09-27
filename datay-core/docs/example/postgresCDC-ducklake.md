# PostgreSQL CDC 实时同步到 DuckLake 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将 PostgreSQL 的数据变更（CDC）实时同步到 **DuckLake** 数据湖（本地文件元数据 + 本地数据目录，无需 S3）。

- **数据源**：PostgreSQL（逻辑复制 / WAL + `pgoutput`）
- **CDC 输入组件**：`PostgresCDCInput`
- **输出组件**：`DuckLakeWrite`（`model=auto`）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> **重要**：DuckLake 不支持主键/唯一约束，UPDATE/DELETE 依赖完整变更前镜像。
> 因此源表必须设置 **`REPLICA IDENTITY FULL`**（等价于 MySQL 的 `binlog_row_image=FULL`）。

> 注意：逻辑复制只捕获组件启动之后的**增量**变更；存量数据请先用 `StreamJdbcInput` 做一次全量同步。
>
> 也可直接在 CDC 组件上开启 `snapshot=true`，首次运行时自动“先全量、后增量”（无断点时才执行）。

## 2. 环境准备

### 2.1 开启 PostgreSQL 逻辑复制

```sql
-- 超级用户执行
ALTER SYSTEM SET wal_level = logical;
-- 修改后重启
SHOW wal_level;  -- 期望 logical

-- 采集账号授权
ALTER ROLE admin WITH REPLICATION;
```

### 2.2 创建源表并设置完整前镜像

```sql
CREATE TABLE public.cdc_demo (
    id          bigint PRIMARY KEY,
    name        varchar(100),
    amount      numeric(10,2),
    updated_at  timestamp
);

-- DuckLake 无主键，UPDATE/DELETE 需要完整变更前镜像
ALTER TABLE public.cdc_demo REPLICA IDENTITY FULL;
```

### 2.3 DuckLake 准备

DuckDB 会自动 `INSTALL/LOAD ducklake` 扩展。本案例使用本地路径：

- 元数据文件：`./target/cdc-pg.ducklake`
- 数据目录：`./target/cdc-pg-ducklake-data`

## 3. 任务配置文件

创建 `postgresCdcToDuckLake.json`：

```json
{
  "units": [
    {
      ".id": "PostgresCDCInput",
      ".name": "PostgresCDCInput",
      "sourceId": {
        "url": "jdbc:postgresql://127.0.0.1:5432/testdb",
        "driver": "org.postgresql.Driver",
        "username": "admin",
        "password": "123456",
        "dbschema": "public"
      },
      "schema": "public",
      "tableNamePattern": "cdc_demo",
      "publicationName": "datay_pub_cdc_demo_lake",
      "slotName": "datay_slot_cdc_demo_lake"
    },
    {
      ".id": "DuckLakeWrite",
      ".name": "DuckLakeWrite",
      "sourceId": {
        "url": "ducklake:./target/cdc-pg.ducklake",
        "driver": "org.duckdb.DuckDBDriver",
        "dbschema": "main",
        "extraParams": {
          "s3.data_path": "./target/cdc-pg-ducklake-data"
        }
      },
      "schema": "main",
      "table": "",
      "model": "auto"
    }
  ],
  "connections": [
    {
      "sourceId": "PostgresCDCInput",
      "targetId": "DuckLakeWrite",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

## 4. 执行 CDC 同步

```bash
mvn clean package -DskipTests

java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar postgresCdcToDuckLake.json
```

## 5. 验证同步效果

在源库执行：

```sql
INSERT INTO public.cdc_demo VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00');
INSERT INTO public.cdc_demo VALUES (2, 'bob',   20.00, '2024-01-02 11:00:00');
UPDATE public.cdc_demo SET name = 'alice2', amount = 11.11 WHERE id = 1;
DELETE FROM public.cdc_demo WHERE id = 2;
```

DuckLake 结果：

```
id | name   | amount | updated_at
1  | alice2 | 11.11  | 2024-01-01 10:00:00
```

### 自动化的端到端测试

```bash
mvn -pl datay-core test -Dtest=PostgresCdcToDuckLakeTest
```

测试覆盖 INSERT / UPDATE / DELETE，并校验 `timestamp` 字段墙上时间保持一致。

## 6. 写入策略说明（DuckLake）

| 事件 | 写入策略 |
|------|----------|
| INSERT | 多值 `INSERT INTO ... VALUES (...), (...)` |
| UPDATE | `UPDATE ... SET ... WHERE <变更前镜像所有列>`（DuckLake 无主键） |
| DELETE | `DELETE ... WHERE <变更前镜像所有列>` |

> 依赖 `REPLICA IDENTITY FULL` 提供完整前镜像；若为默认（仅主键），UPDATE/DELETE 无法按全列匹配。
> 同 DuckDB 目标一样，PG 逻辑复制**不传输 DDL**，表结构变更需手动处理。

## 7. 常见问题

- **UPDATE/DELETE 未生效或产生重复行**：确认源表已设置 `REPLICA IDENTITY FULL`。
- **DuckLake 不支持 PRIMARY KEY/UNIQUE**：建表自动不创建主键，属预期行为。
- **扩展安装失败**：确保 DuckDB 可访问扩展源或已缓存 `ducklake` 扩展。
- **只同步到启动后的变更**：存量数据请另行全量同步。

---

通过本案例，您可以快速搭建 PostgreSQL → DuckLake 的实时 CDC 同步链路。
