# Oracle CDC 实时同步到 DuckDB 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将 Oracle 的数据变更（CDC）实时同步到内嵌 DuckDB。

- **数据源**：Oracle（LogMiner 日志挖掘）
- **CDC 输入组件**：`OracleCDCInput`
- **输出组件**：`StreamJdbcOutput`（`model=auto`，写入 DuckDB）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> 注意：LogMiner 增量默认只捕获组件启动之后的**增量**变更；存量数据可开启 `snapshot=true` 先全量后增量。

## 2. 环境准备

### 2.1 一键启动 Oracle 验证实例（Docker）

仓库内提供了 Oracle Free 容器的启动与配置脚本：

```bash
cd datay-core/src/test/docker/oracle
./setup.sh
```

脚本会自动开启归档模式、补充日志，并给 `datay` 账号授予 LogMiner 所需权限。详见该目录下的 `README.md`。

> 本地验证已预置数据源 **`Oracle CDC(本地)`**（`jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1`，schema `DATAY`，账号 `datay/datay123`），
> 启动并配置好上述容器后，可直接在「任务设计」中选用该数据源。

### 2.2 手动准备（自建 Oracle）

```sql
-- 开启归档模式（SYSDBA）
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;

-- 补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 授权（在目标 PDB / 非 CDB 库中）
GRANT LOGMINING TO datay;
GRANT SELECT ANY TRANSACTION TO datay;
GRANT EXECUTE ON SYS.DBMS_LOGMNR TO datay;
GRANT EXECUTE ON SYS.DBMS_LOGMNR_D TO datay;
GRANT SELECT ON SYS.V_$LOGMNR_CONTENTS TO datay;
GRANT SELECT ON SYS.V_$LOGFILE TO datay;
```

### 2.3 创建测试表

```sql
CREATE TABLE DATAY.CDC_DEMO (
    ID          NUMBER(10) PRIMARY KEY,
    NAME        VARCHAR2(100),
    AMOUNT      NUMBER(10,2),
    UPDATED_AT  TIMESTAMP
);
```

## 3. 任务配置文件

创建 `oracleCdcToDuckDB.json`：

```json
{
  "units": [
    {
      ".id": "OracleCDCInput",
      ".name": "OracleCDCInput",
      "sourceId": {
        "url": "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
        "driver": "oracle.jdbc.OracleDriver",
        "username": "datay",
        "password": "datay123",
        "dbschema": "DATAY"
      },
      "schema": "DATAY",
      "tableNamePattern": "CDC_DEMO",
      "snapshot": true
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
      "sourceId": "OracleCDCInput",
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
java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar oracleCdcToDuckDB.json
```

任务启动后，组件会自动：校验归档与补充日志、读取起始 SCN、按 SCN 区间挖掘变更并下发。

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

仓库内提供了可直接运行的集成测试，覆盖 INSERT / UPDATE / DELETE：

```bash
# 先启动并配置 Oracle 验证实例
cd datay-core/src/test/docker/oracle && ./setup.sh

cd /path/to/datay
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=OracleCdcToDuckDBTest
```

针对多字段类型的覆盖，另有 `OracleCdcTypesToDuckDBTest`（NUMBER/DECIMAL、VARCHAR2/CHAR/NVARCHAR2、BINARY_FLOAT/BINARY_DOUBLE、DATE/TIMESTAMP、RAW、NULL 等）：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=OracleCdcTypesToDuckDBTest
```

测试会：校验前置条件（不满足自动跳过）→ 重置环境 → 后台启动 CDC 任务 → 执行 DML → 轮询断言 DuckDB 数据 → 清理目标文件。

## 6. 组件参数速查

`OracleCDCInput`：

| 参数 | 必填 | 说明 |
|------|------|------|
| `datasource` | ✅ | Oracle 连接信息（url/username/password/dbschema） |
| `schema` | ❌ | 采集 schema，缺省取 `datasource.dbschema`，再缺省取用户名大写 |
| `tableNamePattern` | ❌ | 表名过滤正则（不区分大小写），为空采集全部 |
| `startScn` | ❌ | 起始 SCN，缺省从上次位点或当前 SCN 开始 |
| `pollIntervalMs` | ❌ | 轮询间隔（毫秒），默认 1000 |
| `snapshot` | ❌ | 首次先全量后增量，默认 false |

`StreamJdbcOutput`（`model=auto`）写入策略：

| 事件 | 写入策略 |
|------|----------|
| INSERT | DuckDB Appender 批量写入 |
| UPDATE | `INSERT OR REPLACE`（按主键） |
| DELETE | 按主键 `DELETE` |

## 7. 常见问题

- **未开启归档模式**：需以 SYSDBA 重启到 MOUNT 后执行 `ALTER DATABASE ARCHIVELOG`。
- **权限不足**：采集账号需 `LOGMINING` 角色与 `SELECT ANY TRANSACTION` 权限。
- **PDB 中 ORA-65040**：组件已自动识别 PDB 并跳过 `ADD_LOGFILE`。
- **源表结构变更不同步**：LogMiner 不传输 DDL，需手动同步或重建目标表。
- **只同步到启动后的变更**：存量数据请开启 `snapshot` 或另行全量同步。
- **LOB 列不同步**：`CLOB/BLOB/LONG/ROWID` 等类型当前暂不支持取值。

---

通过本案例，您可以快速搭建 Oracle → DuckDB 的实时 CDC 同步链路。
