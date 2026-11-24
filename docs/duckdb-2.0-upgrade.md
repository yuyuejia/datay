# DuckDB 升级到 2.0.1

## 版本变更

| 项目 | 变更前 | 变更后 |
| --- | --- | --- |
| `org.duckdb:duckdb_jdbc` | `1.5.3.0` | `2.0.1.0`（DuckDB `v2.0.1`） |

版本统一在根 `pom.xml` 的 `duckdb_jdbc.version` 中管理，`datay-core` 与 `datay-web` 共用。
`DBType.POLARIS` 中展示用的推荐驱动 Jar 名同步更新为 `duckdb_jdbc-2.0.1.0.jar`。

## 打包相关调整

DuckDB 2.0 起不再发布 macOS x86_64 原生库，JDBC 包内只保留：

- `libduckdb_java.so_macos_arm64`
- `libduckdb_java.so_linux_amd64`
- `libduckdb_java.so_linux_arm64`
- `libduckdb_java.so_windows_amd64`

`tools/SlimJar.java` 的 `macos` 平台因此改为只保留 `macos_arm64`。使用
`-Dslim.jar.platform=macos` 构建的精简包仅支持 Apple Silicon；Intel Mac 请使用默认的
`all` 平台构建。

## 已验证的 DuckDB 功能

升级后执行了以下回归，全部通过（148 个 core 测试 + 26 个 web 测试）：

- **嵌入式引擎与连接池**：DuckDBEngine、HikariCP、`jdbc_stream_results=true` 流式读取
- **写入链路**：`DuckDBAppender`（含 DECIMAL 精度归一化、HUGEINT、BLOB、UUID、时间类型）、
  `INSERT OR REPLACE`、`INSERT ... ON CONFLICT DO UPDATE`、`DELETE ... USING (VALUES ...)`
- **SQL 组件**：`DuckDBSql`、`DuckDBWrite`、`DuckDBRegister`、`SqlUnit`、`StreamSqlUnit`、
  `DataTransform`、`Join`、`DataQualityCheck`、`SqlSink`
- **元数据**：DECIMAL 精度/小数位、整数类型、VARCHAR 长度回填、`information_schema` 查询
- **DuckLake**：`ATTACH 'ducklake:...'`、DuckLake 写入、`ducklake_*` 运维函数
  （flush / merge / rewrite / expire / cleanup / delete_orphaned / settings / table_info）与表健康分析
- **CDC 同步**：MySQL、PostgreSQL 的 snapshot / 增量 CDC → DuckDB 与 DuckLake
- **外部库挂载**：DuckDB `mysql`、`postgres`、`sqlite`、`httpfs`、`iceberg`、`quack`、
  `ducklake`、`excel` 扩展均可 `INSTALL/LOAD`
- **文件读取**：`read_csv`、`read_parquet`、`read_xlsx`
- **Web 侧**：数据地图 `DuckDbDataMapStore`、指标联邦查询、RAG 指标检索、跨库比对
- **文件兼容性**：1.5.3 创建的数据库文件可被 2.0.1 读取并写入，写回后仍保持旧存储版本，
  1.5.3 仍可读取

## DuckDBRegister 修复

本次同时修复了 `DuckDBRegister` 在 2.0 下暴露的问题：

- `duckdb` 类型不再执行 `INSTALL duckdb` / `LOAD duckdb`（该扩展不存在），直接使用内置引擎
  `ATTACH '<文件>' AS <alias> (TYPE duckdb)`。
- PostgreSQL 不再使用扩展不认的 `database=` 选项；连接信息统一放入 `SECRET`，`ATTACH ''`
  仅引用 `SECRET`，也不再追加使用数据库名作为值的 `SCHEMA` 选项。
- SQLite 改为 `ATTACH '<文件>' AS <alias> (TYPE sqlite)`，不再使用无效的 `file=` 连接串，
  也不再为 SQLite 创建无效的 `TYPE sqlite` SECRET。
- MySQL / PostgreSQL 的连接信息统一写入 `CREATE OR REPLACE SECRET`，避免密码出现在日志中；
  别名、Secret 名使用双引号标识符，字符串值统一转义单引号。
- `sqlserver` / `oracle` 没有对应的 DuckDB 原生扩展，改为直接给出明确错误（建议改用 JDBC
  数据源或 DuckDB `odbc` 扩展），不再执行必然 404 的 `INSTALL`。
- 数据库类型识别改为优先按 URL 前缀匹配（`ducklake:` 先于 `duckdb`），避免路径中出现
  `duckdb` 等关键字时误判。

验证：新增 `DuckDBRegisterTest`（6 个用例）覆盖 DuckDB 文件、MySQL、PostgreSQL、SQLite、
DuckLake 挂载及不支持类型的报错；`ETLRunnerTest#testDuckDBRegister` 端到端流程通过。

## 升级注意事项

1. **存储格式**
   - 2.0 新建的 DuckDB 数据库文件使用存储版本 69，1.5.x 无法打开。
   - 2.0 可以读写 1.5.x 已存在的文件，且写回后保持原存储版本。
   - 如需保留回滚能力，可在创建连接时通过连接属性指定旧存储版本：
     `storage_compatibility_version=v1.5.3`。
   - 回滚到 1.5.x 前，请先确认没有由 2.0 新建的数据库文件（含 DuckLake 元数据库）。

2. **正则 `~` 语义**
   - `'abc' ~ 'b'` 从“整串匹配”变为“部分匹配”（与 PostgreSQL 一致），返回 `true`。
   - Datay 自身生成的 SQL 未使用 `~`，但用户自定义 SQL 需要检查。

3. **Lambda 语法**
   - 旧写法 `x -> x + 1` 默认关闭，需改为 `lambda x: x + 1`。

4. **字面量类型**
   - `X'...'`、`B'...'` 现在分别产生 `BLOB`、`BITSTRING`，与字符串比较时需要显式 CAST。

5. **S3 URL 参数**
   - 不再支持在 S3 URL 的查询参数中携带凭证/配置，请继续使用 `CREATE SECRET`（Datay 已使用 Secret）。

6. **移除/调整的设置**
   - `custom_profiling_settings`、`enable_http_logging`、`http_logging_output` 已移除。
   - `preserve_identifier_case` 从布尔改为文本设置，`true`/`false` 仍兼容。

7. **DuckLake**
   - 2.0 附带 DuckLake 1.1，现有 DuckLake 1.0 元数据可直接读写。
   - 2.0 写入后本地 `.ducklake` 元数据库可能升级到存储版本 69，回滚 1.5.x 前需评估。

## 验证中发现但与本次升级无关的既有问题

- `DuckDBAppender` 不支持 `INTERVAL`、`BIT`、`BITSTRING` 列（`createAppender` 报
  `unsupported C API type: 15 / 29`），1.5.3 与 2.0.1 表现一致。遇到含这些列的表时，
  `DuckDbAppenderWriter` 会返回 `false`（此时尚未写入任何行），`StreamJdbcOutput` 自动回退到
  通用 JDBC 写入，数据写入结果正确，仅失去 Appender 的批量写入性能。
  其余常用类型（整数/浮点/DECIMAL/VARCHAR/BLOB/UUID/日期时间/JSON/ARRAY/STRUCT/MAP/ENUM）
  均可正常使用 Appender。
- 部分依赖本地 MySQL/PostgreSQL 测试数据的用例（如 `DBUtilsTest` 的固定表、`*CdcDataTypesToDuckDBTest` 的 `c_bit` 断言）在 1.5.3 上同样失败，属于测试环境/既有断言问题。
