# PostgreSQL CDC 实时同步到 DuckDB 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将本地 PostgreSQL 的数据变更（CDC）实时同步到内嵌 DuckDB。

- **数据源**：PostgreSQL（逻辑复制 / WAL）
- **CDC 输入组件**：`PostgresCDCInput`
- **输出组件**：`StreamJdbcOutput`（`model=auto`，写入 DuckDB）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> 注意：逻辑复制只捕获组件启动之后的**增量**变更；存量数据请先用 `StreamJdbcInput` 做一次全量同步。
>
> 也可直接在 CDC 组件上开启 `snapshot=true`，首次运行时自动“先全量、后增量”（无断点时才执行）。

## 2. 环境准备

### 2.1 开启 PostgreSQL 逻辑复制

```sql
-- 需要超级用户执行
ALTER SYSTEM SET wal_level = logical;
-- 修改后重启 PostgreSQL
```

重启后确认：

```sql
SHOW wal_level;  -- 期望：logical
```

### 2.2 授予采集账号复制权限

```sql
ALTER ROLE admin WITH REPLICATION;
```

### 2.3 创建测试表

```sql
CREATE TABLE public.cdc_demo (
    id          bigint PRIMARY KEY,
    name        varchar(100),
    amount      numeric(10,2),
    updated_at  timestamp
);
```

## 3. 任务配置文件

创建 `postgresCdcToDuckDB.json`：

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
      "publicationName": "datay_pub_cdc_demo",
      "slotName": "datay_slot_cdc_demo"
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
      "sourceId": "PostgresCDCInput",
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
java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar postgresCdcToDuckDB.json
```

任务启动后，组件会自动：

1. 创建 publication `datay_pub_cdc_demo`（发布 `public.cdc_demo`）
2. 创建逻辑复制槽 `datay_slot_cdc_demo`（插件 `pgoutput`）
3. 从当前 WAL 位点开始实时读取变更

## 5. 验证同步效果

在源库执行以下操作：

```sql
INSERT INTO public.cdc_demo VALUES (1, 'alice', 10.50, '2024-01-01 10:00:00');
INSERT INTO public.cdc_demo VALUES (2, 'bob',   20.00, '2024-01-02 11:00:00');
UPDATE public.cdc_demo SET name = 'alice2', amount = 11.11 WHERE id = 1;
DELETE FROM public.cdc_demo WHERE id = 2;
```

目标 DuckDB 会自动建表并同步，最终结果：

```
id | name   | amount | updated_at
1  | alice2 | 11.11  | 2024-01-01 10:00:00
```

### 自动化的端到端测试

仓库内提供了可直接运行的集成测试，覆盖 INSERT / UPDATE / DELETE：

```bash
mvn -pl datay-core test -Dtest=PostgresCdcToDuckDBTest
```

测试会：校验前置条件（不满足自动跳过）→ 重置环境 → 后台启动 CDC 任务 → 执行 DML → 轮询断言 DuckDB 数据 → 清理 publication 与槽位。

## 6. 组件参数速查

`PostgresCDCInput`：

| 参数 | 必填 | 说明 |
|------|------|------|
| `datasource` | ✅ | PostgreSQL 连接信息（url/username/password/dbschema） |
| `schema` | ❌ | 采集 schema，缺省取 `datasource.dbschema` |
| `tableNamePattern` | ❌ | 表名过滤正则，为空采集全部 |
| `publicationName` | ❌ | publication 名称，缺省自动生成 |
| `slotName` | ❌ | 逻辑复制槽名称，缺省自动生成 |
| `startLsn` | ❌ | 起始 LSN，缺省从槽位确认位点续传 |

`StreamJdbcOutput`（`model=auto`）写入策略：

| 事件 | 写入策略 |
|------|----------|
| INSERT | DuckDB Appender 批量写入 |
| UPDATE | `INSERT OR REPLACE`（按主键） |
| DELETE | 按主键 `DELETE` |

## 7. 常见问题

- **`wal_level` 未生效**：修改后必须重启数据库。
- **权限不足**：采集账号需 `REPLICATION` 权限，且需拥有待发布的表。
- **源表结构变更不同步**：逻辑复制不传输 DDL，需手动同步或重建目标表。
- **只同步到启动后的变更**：存量数据请另行全量同步。
- **断点续传**：保持 `slotName` 稳定，组件会从上次提交位点续传。

---

通过本案例，您可以快速搭建 PostgreSQL → DuckDB 的实时 CDC 同步链路。
