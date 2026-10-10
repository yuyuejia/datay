# 数据集成引擎（DataY Core）

## 项目简介

DataY Core = DataX + NIFI，是一个**轻量、可嵌入、可扩展、高性能、流批一体**的数据集成工具。

- 支持通过 **JSON 配置文件**定义任务，无需复杂编码
- 内置 **DuckDB** 引擎和组件，复用其强大的数据处理能力与性能
- 支持自动创建目标表（DDL 转换）
- 支持 **DuckLake** 写入，帮助用户快速构建轻量高性能数据湖

DataY Web 的任务设计最终会生成 DataY Core 可执行的任务 JSON（`units` + `connections`），
由 Core 引擎执行；Core 也可以脱离 Web 独立运行或嵌入到其他 Java 应用中。

## 核心特性

- **简单易用**：通过 JSON 配置文件定义数据集成任务，无需复杂编码
- **可嵌入**：可作为库嵌入到其他应用中，提供灵活的数据处理能力
- **可扩展**：支持组件扩展，支持 Java 脚本组件自定义数据处理逻辑
- **高性能**：内置 DuckDB 引擎，支持 SQL 进行高效数据处理
- **流批一体**：支持流处理和批处理任务，可根据场景选择合适的处理模式
- **任务编排**：支持复杂的数据处理流程编排

## 快速开始

直接下载 DataY 构建好的 jar 包：
[DataY 下载地址](https://repo1.maven.org/maven2/io/gitee/yuyuejia/datay/1.0/datay-1.0.1-jar-with-dependencies.jar)

MySQL 同步到 MySQL 的任务配置文件示例：

```json
{
  "units": [
    {
      ".id": "c125de36",
      ".name": "StreamJdbcInput",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.jdbc.Driver",
        "username": "root",
        "password": "password",
        "dbschema": "source"
      },
      "table": "table1,table2",
      "incrColumn": "",
      "where": ""
    },
    {
      ".id": "a949c10a",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.jdbc.Driver",
        "username": "root",
        "password": "password",
        "dbschema": "target"
      },
      "schema": "target",
      "table": "",
      "model": "overwrite"
    }
  ],
  "connections": [
    { "sourceId": "c125de36", "targetId": "a949c10a" }
  ]
}
```

运行任务：

```bash
java -jar datay-1.0.1-jar-with-dependencies.jar taskConfig.json
```

> 在平台里使用时，可以在 [任务设计与开发](guide/development.md) 中通过画布生成同样的任务 JSON。

## 嵌入 Java 应用示例

`pom.xml` 引入依赖：

```xml
<dependency>
  <groupId>io.gitee.yuyuejia</groupId>
  <artifactId>datay</artifactId>
  <version>1.0.1</version>
</dependency>
```

```java
public class Example {
    public static void main(String[] args) {
        String job = FileUtil.readUtf8String("/Users/code/datay/src/test/task.json");
        ETLFlowTask task = new ETLFlowTask();
        task.runJob(job);
    }
}
```

## 支持的数据源

- **关系型数据库**：MySQL、Oracle、PostgreSQL、SQL Server、MariaDB
- **分析数据库**：DuckDB、Doris、ClickHouse、GreenPlum
- **文件系统**：本地文件、MinIO 对象存储
- **CDC**：MySQL Binlog、PostgreSQL 逻辑复制（WAL / pgoutput）

## 核心组件

篇幅较长，已单独整理成 [ETL 组件介绍](components/README.md)，按输入、处理、输出、路由、其他分组介绍。

- **输入组件**：`StreamJdbcInput`、`JdbcInput`、`MySQLBinlogInput`、`PostgresCDCInput`
- **处理组件**：`DuckDBSql`、`StreamSqlUnit`、`SqlTask`、`JavaScriptComponent`
- **输出组件**：`StreamJdbcOutput`、`DuckDBWrite`、`DuckLakeWrite`、`DorisStreamLoad`
- **路由组件**：`HashRouter`、`RandomRouter`
- **其他组件**：`HttpListener`、`GenerateFlowFile`

## 监控与日志

- 任务增量状态记录，支持增量数据处理
- 支持本地文件状态存储、MinIO 分布式状态存储
- 详细的执行日志记录

## 下一步

- [ETL 组件介绍](components/README.md)：逐个组件了解配置参数
- [场景案例](cases/README.md)：同步、CDC、DuckLake 等真实案例
- [快速开始](quickstart/README.md)：把平台跑起来

<img src="../images/datay.jpg" width="300" height="400" alt="DataY" />
