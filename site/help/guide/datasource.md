# 数据源管理

数据源是 DataY 的数据出入口。数据集成、数据同步、元数据浏览都依赖这里注册的连接。

导航：**数据源 → 数据源管理**。

## 新建数据源

1. 点击「新建数据源」。
2. 填写名称、类型、连接 URL、默认 schema 等信息。
3. 点击「测试连接」确认连通。
4. 保存。

以 DuckDB 为例：

| 字段 | 示例 |
| --- | --- |
| 名称 | `电商数仓(DuckDB)` |
| 类型 | `DUCKDB` |
| 连接 URL | `jdbc:duckdb:./metadata.duckdb` |
| 默认 schema | `main` |
| 用户名 / 密码 | 按数据库要求填写 |

![数据源列表](../images/数据源列表.png)

## 测试连接

- 保存前建议先点「测试连接」，确认驱动、URL、账号密码正确。
- 连接失败时优先检查：网络是否可达、端口是否正确、账号权限、URL 参数（如时区、SSL）。

## 元数据浏览

在数据源详情中可浏览 **schema → table → column** 层级元数据，用于：

- 确认目标表是否已生成、字段是否齐全
- 为 ETL 节点、注册模式导入字段提供参考

## 支持的数据源

| 分类 | 数据源 |
| --- | --- |
| 关系型数据库 | MySQL、Oracle、PostgreSQL、SQL Server、MariaDB |
| 分析数据库 | DuckDB、Doris、ClickHouse、GreenPlum |
| 文件系统 | 本地文件、MinIO 对象存储 |
| CDC | MySQL Binlog、PostgreSQL 逻辑复制（WAL / pgoutput） |

## 下一步

- [数据集成与同步](guide/integration.md)：用注册好的数据源编排同步任务
- [ETL 组件介绍](components/StreamJdbcInput.md)：了解各数据源对应的输入 / 输出组件
