# DorisArrowFlightInput 组件配置文档

## 组件概述

`DorisArrowFlightInput` 通过 Apache Doris 2.1+ 提供的 **Arrow Flight SQL** 协议流式读取 Doris 数据。

与 `StreamJdbcInput` 使用 MySQL 协议不同，该组件读取数据时直接消费 Arrow RecordBatch，减少 JDBC 行转换开销，适合大批量读取场景。表结构等元数据仍通过 Doris 的 MySQL 查询端口读取，保证下游自动建表、类型转换等逻辑与现有组件一致。

## 核心特性

- ✅ **Arrow Flight SQL 流式读取**：按批消费 Arrow RecordBatch，不一次性加载全量数据
- ✅ **多表同步**：支持逗号分隔多表，也支持同步整个 schema
- ✅ **增量同步**：按增量字段读取，水位随 FlowFile 传递
- ✅ **动态过滤**：`where` 支持 `${attr:属性名}`、`${属性名}`、`#{...}` 等参数
- ✅ **调试采样**：调试模式默认只读取 100 条，并把 `LIMIT` 下推到 Doris
- ✅ **下游兼容**：输出标准 FlowFile，可直接接 `DorisStreamLoad`、`StreamJdbcOutput`、`DuckDBWrite` 等组件

## 前置条件

1. Doris 版本 **2.1 及以上**。
2. Doris FE 和 BE 都开启 Arrow Flight SQL：

   ```properties
   # fe/conf/fe.conf
   arrow_flight_sql_port = 8070

   # be/conf/be.conf
   arrow_flight_sql_port = 8050
   ```

   FE 与 BE 的端口不能相同。修改后重启 FE/BE，并确认日志中出现：

   - FE：`Arrow Flight SQL service is started`
   - BE：`Arrow Flight Service bind to host`

3. DataY 所在机器能访问 **FE 的 Arrow 端口** 和 **BE 的 Arrow 端口**。只放开 FE 端口无法读取数据。
4. Doris 的 MySQL 查询端口（默认 `9030`）可访问，用于读取表元数据。
5. Java 17 启动参数需要增加：

   ```bash
   --add-opens=java.base/java.nio=ALL-UNNAMED
   ```

   否则 Arrow 内存分配器会初始化失败。Docker 镜像的 `entrypoint.sh` 已默认追加该参数；直接 `java -jar` 启动时请手动添加：

   ```bash
   java --add-opens=java.base/java.nio=ALL-UNNAMED -jar datay-web.jar
   ```

## 配置参数

### 基础配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `sourceId` | DatasourceInfo | 是 | - | Doris 数据源 |
| `schema` | String | 否 | 数据源 dbschema | Doris 数据库名 |
| `table` | String | 否 | - | 表名，支持逗号分隔多表；为空时同步 schema 下所有表 |
| `where` | String | 否 | - | 过滤条件，不需要写 `WHERE` |
| `incrColumn` | String | 否 | - | 增量字段。单表填字段名；多表填 JSON |

### Arrow Flight 配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `arrowFlightHost` | String | 否 | 数据源主机 | Doris FE 地址 |
| `arrowFlightPort` | Integer | 否 | `8070` | Doris FE 的 `arrow_flight_sql_port` |
| `useEncryption` | Boolean | 否 | `false` | 是否使用 TLS |

### 高级配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| `batchSize` | Integer | 否 | `10000` | 每个 FlowFile 承载的记录数 |
| `arrowMaxMemoryMb` | Integer | 否 | `512` | Arrow 内存分配器上限（MB） |

## 数据源配置

```json
{
  "type": "DORIS",
  "url": "jdbc:mysql://doris-fe:9030/demo",
  "username": "root",
  "password": "",
  "dbschema": "demo"
}
```

## 配置示例

### 单表读取

```json
{
  ".id": "doris_input",
  ".name": "DorisArrowFlightInput",
  "sourceId": {
    "type": "DORIS",
    "url": "jdbc:mysql://doris-fe:9030/demo",
    "username": "root",
    "password": ""
  },
  "schema": "demo",
  "table": "orders",
  "arrowFlightPort": 8070
}
```

### 多表增量读取

```json
{
  ".id": "doris_input",
  ".name": "DorisArrowFlightInput",
  "schema": "demo",
  "table": "orders,order_items",
  "incrColumn": "{\"orders\":\"update_time\",\"order_items\":\"id\"}",
  "where": "status = 'PAID'",
  "arrowFlightPort": 8070,
  "batchSize": 10000
}
```

### 同步整个 schema

`table` 留空即可：

```json
{
  ".id": "doris_input",
  ".name": "DorisArrowFlightInput",
  "schema": "demo",
  "arrowFlightPort": 8070
}
```

## 输出数据

每批数据下发一个 FlowFile：

- `data`：`JSONArray`，每行一个 `JSONObject`
- `_tableMetadata`：源表 `TableMeta`
- `_table`：表名
- `_database`：schema 名
- status：`<组件id>.lastIncrValue.<表名>`（配置增量字段时）

## 调试模式

调试运行时：

- 不加载历史增量水位；
- 使用调试采样上限（默认 **100**，可由调试配置覆盖）；
- 查询 SQL 自动追加 `LIMIT 100`；
- 读取循环同时做行数截断，达到上限后立即关闭 Arrow Stream。

因此调试大表不会拉取全量数据。

## 增量同步说明

1. 首次运行：读取全量数据。
2. 后续运行：按增量字段追加 `增量字段 > 上次最大值`，并按增量字段升序读取。
3. 水位随 FlowFile 传递，下游 sink 或没有下游的组件会将其保存到任务状态。
4. 调试模式不会保存水位。

## 常见问题

### Arrow 初始化失败

错误包含 `java.nio`、`MemoryUtil`、`InaccessibleObjectException` 等关键字时，请确认启动参数包含：

```bash
--add-opens=java.base/java.nio=ALL-UNNAMED
```

### 连接超时 / 数据读取失败

- 确认 FE 的 Arrow 端口可访问；
- 确认 BE 的 Arrow 端口也可访问，Doris 返回的 endpoint 指向 BE；
- 确认 BE 地址是 DataY 所在网络可达的地址，不是仅 FE 内网可见的地址。

### 表元数据读取失败

- Arrow 端口只负责传输数据，表结构仍通过 MySQL 查询端口读取；
- 检查数据源 JDBC URL、用户名、密码和 `schema` 是否正确。

### 不支持 Doris 1.x / 2.0

Arrow Flight SQL 从 Doris 2.1 开始支持，请升级 Doris 或继续使用 `StreamJdbcInput`。

## 联调测试

仓库内置真实 Doris 联调测试 `DorisArrowFlightInputIntegrationTest`，默认跳过。启动 Doris 并准备 `demo.orders` 表后，可通过环境变量启用：

```bash
DORIS_ARROW_IT=true \
DORIS_FE_HOST=127.0.0.1 \
DORIS_QUERY_PORT=9030 \
DORIS_ARROW_PORT=8070 \
mvn -pl datay-core test -Dtest=DorisArrowFlightInputIntegrationTest
```

测试覆盖全量读取、增量读取、调试采样上限（含默认 100 条）。

### Doris → DuckDB 端到端同步

`DorisArrowFlightToDuckDBIntegrationTest` 使用完整 ETL 流程验证 `DorisArrowFlightInput -> DuckDBWrite`：

```bash
DORIS_ARROW_IT=true \
mvn -pl datay-core test -Dtest=DorisArrowFlightToDuckDBIntegrationTest
```

该测试会在 DuckDB 中自动建表、写入数据，并查询校验行数、字段类型和值。

### Doris → MySQL 端到端同步

`DorisArrowFlightToMysqlIntegrationTest` 使用完整 ETL 流程验证 `DorisArrowFlightInput -> StreamJdbcOutput`：

```bash
DORIS_ARROW_IT=true \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=password \
mvn -pl datay-core test -Dtest=DorisArrowFlightToMysqlIntegrationTest
```

测试会自动创建 `doris_verify` 数据库和 `orders_from_doris` 表，完成后清理目标表。
