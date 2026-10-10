# 场景案例

本栏目收录 DataY 的真实使用案例，覆盖端到端实战、批量同步、CDC 实时同步以及
文件 / HTTP / 飞书等多样化数据源接入。每个案例都给出了完整的任务配置。

## 端到端实战

| 案例 | 说明 |
| --- | --- |
| [电商指标问数闭环](cases/ecommerce.md) | 2C4G 从数据接入、维度建模、指标定义到智能问数的完整链路 |

## 批量同步

| 案例 | 说明 |
| --- | --- |
| [MySQL 同步到 MySQL](cases/mysql-to-mysql.md) | 最基础的库表同步 |
| [全库大数据量表全量同步](cases/mysql-bigtable-mysql.md) | 大数据量表的流式全量同步 |
| [MySQL 同步到 Apache Doris](cases/mysql-doris.md) | 同步到 Doris 分析库 |
| [MySQL 同步到 DuckLake](cases/mysql-ducklake.md) | 同步到 DuckLake 数据湖 |
| [使用 DuckDB SQL 进行数据处理](cases/mysql-sqlunit-mysql.md) | 用 StreamSqlUnit 在同步链路中做 SQL 转换 |

## CDC 实时同步

| 案例 | 说明 |
| --- | --- |
| [MySQL CDC 实时同步到 MySQL](cases/mysqlCDC-mysql.md) | Binlog 实时捕获同步到 MySQL |
| [MySQL CDC 实时同步到 DuckDB](cases/mysqlCDC-duckdb.md) | Binlog → DuckDB |
| [MySQL CDC 实时同步到 DuckLake](cases/mysqlCDC-ducklake.md) | Binlog → DuckLake |
| [PostgreSQL CDC 实时同步到 DuckDB](cases/postgresCDC-duckdb.md) | 逻辑复制 → DuckDB |
| [PostgreSQL CDC 实时同步到 DuckLake](cases/postgresCDC-ducklake.md) | 逻辑复制 → DuckLake |

## 文件 / HTTP / 飞书

| 案例 | 说明 |
| --- | --- |
| [HTTP 监听数据同步到 MySQL](cases/http-to-mysql.md) | 通过 HttpListener 接收请求写入 MySQL |
| [HTTP 分页循环同步到 MySQL](cases/http-loop-to-mysql.md) | 自动循环调用分页接口写入 MySQL |
| [飞书多维表格同步到 MySQL](cases/feishu-bitable-to-mysql.md) | 飞书 bitable → MySQL |
| [MySQL 同步到飞书多维表格](cases/mysql-to-feishu-bitable.md) | MySQL → 飞书 bitable |

## 下一步

- [ETL 组件介绍](components/README.md)：案例中使用的组件参数
- [使用指南](guide/README.md)：在平台中操作这些任务
