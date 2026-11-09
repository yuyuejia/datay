# 不写 Provider、不改代码：DataY 用通用 JDBC 把 TDengine 接进了数据平台

## 内置 20+ 数据源还不够？通用 JDBC 才是数据平台的“最后一公里”

> 内置数据源解决“常用数据库开箱即用”，通用 JDBC 解决“计划外数据库也能快速接入”。

---

### 01 背景：数据源支持，永远追不上数据库的进化

做数据平台的人都有一个共同感受：**数据源支持永远追不上数据库的进化速度。**

MySQL、PostgreSQL、Oracle、SQL Server、Doris、ClickHouse、DuckDB……DataY 已经内置了二十多种常用数据源。但真实项目里，总会遇到一些“计划之外”的数据库：

- 客户现场用的是 TDengine，做物联网时序数据；
- 某个老系统只提供 Hive JDBC；
- 新项目想接 Trino、Phoenix、StarRocks；
- 甚至是一个内部自研、但恰好支持 JDBC 的数据库。

如果每接一种数据库都要开发一个 Provider、改代码、发版本，成本太高，节奏也太慢。

DataY 的答案是：**通用 JDBC 数据源。**

这次我们拿 TDengine 做了一次完整验证——从连接、元数据浏览、SQL 查询，到 ETL 双向同步和增量同步。结论先放在前面：

> **TDengine 可以通过通用 JDBC 数据源直接接入，查询和 ETL 双向同步均已跑通；TDengine 只是案例，通用 JDBC 才是 DataY 适配“计划外数据源”的核心能力。**

---

### 02 通用 JDBC 到底“通用”在哪？

DataY 的通用 JDBC 数据源不是简单填一个 JDBC URL，而是把接入一个陌生数据库需要的能力都做成了配置项：

| 配置项 | 作用 |
|---|---|
| JDBC URL | 任意 JDBC 连接串，例如 `jdbc:TAOS-RS://host:6041` |
| 驱动类名 | 任意 JDBC Driver，例如 `com.taosdata.jdbc.rs.RestfulDriver` |
| 驱动 Jar | 本地 Jar 文件或目录，支持把依赖 Jar 放在同一目录 |
| Maven 坐标 | 也可以填 groupId / artifactId / version，由平台按需下载 |
| 测试查询 | 不同数据库的连通性语句不同，可自定义，默认 `SELECT 1` |

平台内部通过**隔离类加载器**加载驱动 Jar，避免不同数据库驱动之间的类冲突；元数据读取同时兼容两类 JDBC 驱动：

- **schema 型**：PostgreSQL、Hive 等，`getSchemas()` 返回 schema；
- **catalog 型**：MySQL、TDengine 等，用 catalog/数据库承载“库”的概念。

更重要的是，通用 JDBC 接入后，平台上层的能力是复用的：

- 数据源连通性测试
- 库表元数据浏览
- SQL 查询预览
- ETL 输入 / 输出
- 增量同步
- MCP、数据服务等上层入口

**也就是说，通用 JDBC 不是“只能连一下”，而是完整接入了 DataY 的数据集成体系。**

---

### 03 实战：把 TDengine 接进来

#### 3.1 准备驱动

TDengine 官方提供 `taos-jdbcdriver`，支持三种连接方式：

| 方式 | JDBC URL | 驱动类 | 说明 |
|---|---|---|---|
| REST | `jdbc:TAOS-RS://host:6041` | `com.taosdata.jdbc.rs.RestfulDriver` | 走 HTTP，无需原生库，推荐 |
| WebSocket | `jdbc:TAOS-WS://host:6041` | `com.taosdata.jdbc.ws.WebSocketDriver` | 走 WebSocket，同样无需原生库 |
| 原生 | `jdbc:TAOS://host:6030` | `com.taosdata.jdbc.TSDBDriver` | 需要本机安装 TDengine 客户端原生库 |

本次验证使用 `taos-jdbcdriver-3.5.2-dist.jar`。建议使用 `-dist` 完整包，它已经包含 Jackson、Guava、Java-WebSocket 等依赖，省去单独补齐依赖的麻烦。

#### 3.2 新建通用 JDBC 数据源

在 DataY 中新建数据源，类型选择「通用 JDBC」，按下面配置：

```text
类型：通用 JDBC
JDBC URL：jdbc:TAOS-RS://127.0.0.1:6041
用户名：root
密码：taosdata
驱动类名：com.taosdata.jdbc.rs.RestfulDriver
驱动 Jar：/path/to/taos-jdbcdriver-3.5.2-dist.jar
测试查询：SELECT 1
```

保存后点击「测试连接」，返回成功即可。

> TDengine 3.3.6 实测 `SELECT 1` 可以作为连通性测试语句；也可以使用 `SELECT SERVER_VERSION()`。

#### 3.3 元数据浏览

连接成功后，DataY 能正常列出：

- `information_schema`、`performance_schema` 等系统库；
- 业务数据库；
- 普通表、超级表、子表；
- 字段、类型、长度、精度、Tag 字段等表结构信息。

对于 TDengine 的超级表，表结构中的 Tag 字段也会被识别出来；查询预览、字段展示与普通关系型数据库的使用体验基本一致。

#### 3.4 SQL 查询

以下查询均已验证通过：

```sql
SELECT COUNT(*) FROM etl_src.readings_src;

SELECT ts, current, voltage, location
FROM etl_src.readings_src
WHERE voltage >= 230
ORDER BY ts DESC
LIMIT 5;

SELECT location, COUNT(*) AS c, AVG(current) AS avg_current
FROM etl_src.readings_src
GROUP BY location;

SELECT *
FROM etl_src.readings_src
WHERE ts >= '2026-11-09 10:00:10.000'
  AND ts <  '2026-11-09 10:00:20.000';

SELECT FIRST(ts), LAST(ts) FROM etl_src.readings_src;
```

过滤、排序、分页、聚合、分组、时间范围、参数化查询、时序函数 `FIRST/LAST`，以及 `DESCRIBE`、`SHOW DATABASES` 等元数据语句，都可以正常执行。

REST 和 WebSocket 两种方式各做了 12 项查询验证，**全部通过。**

---

### 04 ETL：从“能查”到“能同步”

查询只是第一步。真正体现数据平台价值的，是 ETL。

我们使用 DataY 的 `StreamJdbcInput -> StreamJdbcOutput` 组件，做了双向同步验证。

#### 4.1 TDengine → MySQL

源端配置 TDengine 通用 JDBC 数据源，目标端配置 MySQL，写入模式 `append`：

```json
{
  ".id": "src",
  ".name": "StreamJdbcInput",
  "datasource": {
    "type": "JDBC",
    "url": "jdbc:TAOS-RS://127.0.0.1:6041",
    "username": "root",
    "password": "taosdata",
    "dbschema": "etl_src",
    "extraParams": {
      "jdbc.driverClass": "com.taosdata.jdbc.rs.RestfulDriver",
      "jdbc.driverJar": "/path/to/taos-jdbcdriver-3.5.2-dist.jar",
      "jdbc.testQuery": "SELECT 1"
    }
  },
  "schema": "etl_src",
  "table": "readings_src"
}
```

目标端：

```json
{
  ".id": "dst",
  ".name": "StreamJdbcOutput",
  "datasource": {
    "type": "MYSQL",
    "url": "jdbc:mysql://127.0.0.1:3306/datay_test",
    "username": "root",
    "password": "******",
    "dbschema": "datay_test"
  },
  "schema": "datay_test",
  "table": "td_readings",
  "model": "append"
}
```

验证结果：**50 条 TDengine 数据完整写入 MySQL**，字段值、分组分布一致。

#### 4.2 MySQL → TDengine

反过来，把 MySQL 数据写入 TDengine，同样使用通用 JDBC 作为目标：

```json
{
  ".id": "dst",
  ".name": "StreamJdbcOutput",
  "datasource": {
    "type": "JDBC",
    "url": "jdbc:TAOS-RS://127.0.0.1:6041",
    "username": "root",
    "password": "taosdata",
    "dbschema": "etl_dst",
    "extraParams": {
      "jdbc.driverClass": "com.taosdata.jdbc.rs.RestfulDriver",
      "jdbc.driverJar": "/path/to/taos-jdbcdriver-3.5.2-dist.jar"
    }
  },
  "schema": "etl_dst",
  "table": "td_dst",
  "model": "append"
}
```

验证结果：**40 条 MySQL 数据完整写入 TDengine**，电压、电流、地点等字段一致。

同时验证了 `overwrite` 模式：平台会先 `DELETE` 清空目标表，再批量写入，最终数据量保持 40 条。

#### 4.3 增量同步

TDengine 作为时序库，按时间戳做增量同步是非常典型的场景。

在 `StreamJdbcInput` 中配置：

```json
{
  "incrColumn": "ts"
}
```

第一次同步 50 条，记录最后时间戳 `2026-11-09 10:00:49.0`；向 TDengine 新增 5 条数据后再次运行，平台自动拼接：

```sql
SELECT * FROM etl_src.readings_src
WHERE ts > '2026-11-09 10:00:49.0'
```

第二次只同步了新增的 **5 条**，目标表累计 55 条，增量状态自动更新为 `2026-11-09 10:01:04.0`。

#### 4.4 ETL 验证结果汇总

| 场景 | 模式 | 结果 |
|---|---|---|
| TDengine REST → MySQL | append | 50/50 条 |
| TDengine WebSocket → MySQL | append | 55/55 条 |
| MySQL → TDengine REST | append | 40/40 条 |
| MySQL → TDengine WebSocket | append | 40/40 条 |
| MySQL → TDengine REST | overwrite | 清空后写入 40 条 |
| TDengine → MySQL | 按 `ts` 增量 | 首次 50 条，第二次仅 5 条 |

---

### 05 适配过程中，顺手补上的 6 个通用能力

这次 TDengine 适配并不是“填个 URL 就结束”。真实跑通查询和 ETL 的过程中，我们顺手修复了一组通用问题——**这些问题不只服务 TDengine，所有通过通用 JDBC 接入的数据源都会受益。**

1. **驱动类加载器补充 CodeSource**
   部分驱动会在静态初始化时读取 `getProtectionDomain().getCodeSource()`，原实现会导致空指针；修复后驱动 Jar 的隔离加载更接近标准类加载行为。

2. **元数据 null 容错**
   TDengine 的 `getPrimaryKeys()` 返回 `null`，连接池代理会抛 NPE；现在主键、索引、注释等可选元数据读取失败不会影响表结构浏览。

3. **字符类型 byte[] 自动解码**
   TDengine WebSocket 驱动会把 `VARCHAR` 返回为 `byte[]`，平台现在会根据 JDBC 列类型解码为 UTF-8 字符串，避免页面显示 `[BLOB]`。

4. **通用 JDBC 目标写入 SQL**
   未登记方言的数据源现在可以自动生成标准 `INSERT / UPDATE / DELETE`，让“通用 JDBC 作为 ETL 目标”真正可用。

5. **时间参数归一化**
   `LocalDateTime` 自动转 `java.sql.Timestamp`，字符串使用标准 `setString`，解决 TDengine REST/WebSocket 驱动的绑定差异。

6. **时间字段增量状态解析**
   增量状态持久化后时间会变成字符串，平台现在支持日期时间格式解析，时间字段增量同步不再报 `NumberFormatException`。

这些改动让通用 JDBC 从“能连接”进一步变成“能查询、能写入、能增量同步”。

---

### 06 通用 JDBC 还能接什么？

TDengine 只是这次验证的对象。只要目标数据库提供 JDBC 驱动，理论上都可以用同样的方式接入，例如：

- Hive
- Trino / Presto
- Phoenix
- StarRocks
- 其他内部自研 JDBC 数据库

配置模板基本一致：

```text
类型：通用 JDBC
JDBC URL：jdbc:xxx://host:port/database
驱动类名：com.xxx.Driver
驱动 Jar：/path/to/xxx-jdbc.jar
测试查询：SELECT 1
```

如果驱动不在本地，也可以直接填 Maven 坐标，由平台从 Maven 仓库下载。

> 使用建议：平台已内置的数据源优先使用内置类型，能获得更完整的方言、DDL 和性能优化；通用 JDBC 更适合“平台暂未内置、但提供 JDBC 驱动”的数据库。

---

### 07 几个需要注意的点

1. **优先使用 REST / WebSocket**
   TDengine 原生 `jdbc:TAOS://` 需要本机安装客户端原生库；REST 和 WebSocket 无需原生依赖，部署更简单。

2. **建议使用 `-dist` 完整驱动包**
   普通 Jar 不包含全部依赖，尤其是 WebSocket 方式依赖 Java-WebSocket；`-dist` 包开箱即用。

3. **ETL 写入 TDengine 需要预建目标表**
   通用 JDBC 没有 TDengine 专用 DDL 方言，自动建表暂不支持。建议先建好普通表或子表，再进行同步。

4. **超级表不建议直接作为写入目标**
   通用 INSERT 会把 Tag 字段当普通列处理；写入子表或普通表更稳妥。

5. **注意时区一致性**
   按时间戳增量同步时，DataY JVM 与 TDengine 服务端时区应保持一致；否则增量条件可能出现时间偏移。生产环境建议统一设置为 `Asia/Shanghai`，或根据业务统一使用 UTC。

---

### 08 结语

DataY 的理念是“轻量但不减配”。

内置数据源解决的是“常用数据库开箱即用”；通用 JDBC 解决的是“计划外数据库也能快速接入”。两者结合，才是一个数据平台面对真实世界时应有的弹性。

这次 TDengine 的适配验证说明了一件事：

> **只要数据库提供 JDBC 驱动，DataY 就有机会把它接入数据集成、查询和 ETL 的完整链路。**

TDengine 是一个案例，通用 JDBC 才是能力。

如果你也遇到过“平台不支持某个数据库”的场景，欢迎把数据库类型和 JDBC 驱动信息发给我们，我们一起把“最后一公里”打通。

---

### 09 关于 DataY

DataY 是一套**轻量级、一体化的数据平台**，目标是“用最极致的技术栈精简，交付最完整的数据能力”。

一个 JAR 即可启动，2 核 4G 就能跑通从数据接入、ETL、任务调度、数仓建模到指标问数的完整链路，不依赖 Hadoop、Spark 等重型组件。

**核心能力：**

- **数据集成**：内置 20+ 数据源，支持全量 / 增量同步、MySQL / PostgreSQL / Oracle / MongoDB CDC 实时同步，以及通过通用 JDBC 扩展任意新数据源；
- **任务调度**：Cron 定时调度、DAG 工作流编排、失败重试、调度与执行分离；
- **高性能数仓**：内嵌 DuckDB，支持 Doris、ClickHouse、DuckLake 等分析引擎；
- **维度建模与指标管理**：星型 / 雪花模型、层级维度、时间维度、指标口径统一管理；
- **智能问数**：自然语言 → 指标 → 维度 → 时间过滤 → 取数，一句话拿报表；
- **MCP Harness**：让 AI Agent 直接操作数据源、ETL 任务、数据模型和数据服务；
- **通用 JDBC**：不写 Provider、不改代码，配置 JDBC URL、驱动类和驱动 Jar，即可接入平台未内置的数据库。

**相关链接：**

- 产品首页：<http://datay.yuyuejia.com.cn/>
- 在线演示：<http://datay-demo.yuyuejia.com.cn/>
- 源码仓库：<https://gitee.com/yuyuejia/datay>   <https://github.com/yuyuejia/datay>

欢迎 Star、试用和反馈。如果你正在做数据集成、实时同步、轻量数仓或 AI 问数，DataY 值得一试。

---

*本文基于 TDengine 3.3.6.0、taos-jdbcdriver 3.5.2 与 DataY 通用 JDBC 数据源实测整理。*
