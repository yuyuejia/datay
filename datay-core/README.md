# DataY - 轻量、可嵌入、可扩展、高性能、流批一体的数据集成工具

## 项目简介

DataY = DataX + NIFI ,是一个轻量、可嵌入、可扩展、高性能、流批一体的数据集成工具，支持通过JSON配置文件的方式定义任务，支持自动创建目标表。
内置DuckDB引擎和组件,复用DuckDB强大的数据处理能力和性能。 支持DuckLake写入,帮助用户快速构建轻量高性能数据湖。

## 核心特性

- **简单易用**: 通过JSON配置文件定义数据集成任务，无需复杂编码
- **可嵌入**: 可作为库嵌入到其他应用中，提供灵活的数据处理能力
- **可扩展**: 支持组件扩展，满足多样化需求, 支持java脚本组件,自定义数据处理逻辑
- **高性能**: 内置DuckDB引擎，支持SQL进行高效数据处理
- **流批一体**: 支持流处理和批处理任务，可根据场景选择合适的处理模式
- **任务编排**: 支持复杂的数据处理流程编排

## 代码构建

### 环境要求

- Java 17+
- Maven 3.6+

### 构建项目

```bash
mvn clean package
```

## 快速开始

直接下载DataY构建的jar包：[DataY下载地址](https://repo1.maven.org/maven2/io/gitee/yuyuejia/datay/1.0/datay-1.0.1-jar-with-dependencies.jar)

Mysql同步到Mysql任务配置文件示例

```bash
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
      "model": "overwrite",
    }
  ],
  "connections": [
    {
      "sourceId": "c125de36",
      "targetId": "a949c10a"
    }
  ]
}

```
运行任务:
```bash
java -jar datay-1.0.1-jar-with-dependencies.jar taskConfig.json
```

## 嵌入JAVA应用示例
pom.xml 引入依赖:
```maven
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

## 常用场景案例

- [Mysql同步到Mysql](docs/example/mysql-to-mysql.md)
- [Http同步到Mysql](docs/example/http-to-mysql.md)
- [Mysql CDC实时同步到Mysql](docs/example/mysqlCDC-mysql.md)
- [Mysql同步到Apache Doris](docs/example/mysql-doris.md)
- [Mysql同步到DuckLake](docs/example/mysql-ducklake.md)
- [使用DuckDB SQL进行数据处理](docs/example/mysql-sqlunit-mysql.md)

## 支持的数据源

- **关系型数据库**: MySQL, Oracle, PostgreSQL, SQL Server, MariaDB
- **分析数据库**: DuckDB, Doris, ClickHouse, GreenPlum
- **文件系统**: 本地文件、MinIO对象存储
- **CDC**: MySQL Binlog

## 核心组件

### 输入组件
- [StreamJdbcInput](docs/component/StreamJdbcInput.md) : JDBC数据源输入
- [JdbcInput](docs/component/JdbcInput.md) : JDBC数据源输入
- [MySQLBinlogInput](docs/component/MySQLBinlogInput.md) : 读取MySQL Binlog日志,支持增量数据同步

### 处理组件
- [DuckDBSql](docs/component/DuckDBSql.md) : DuckDB SQL处理
- [StreamSqlUnit](docs/component/StreamSqlUnit.md) : 流式SQL处理,使用DuckDB对流式数据进行SQL处理
- [SqlTask](docs/component/SqlTask.md) : 执行对应数据库的SQL任务
- [JavaScriptComponent](docs/component/JavaScriptComponent.md) : Java脚本组件,支持在任务中执行自定义逻辑

### 输出组件
- [StreamJdbcOutput](docs/component/StreamJdbcOutput.md) : JDBC数据源输出
- [DuckDBWrite](docs/component/DuckDBWrite.md) : DuckDB写入
- [DuckLakeWrite](docs/component/DuckLakeWrite.md) : DuckLake写入
- [DorisStreamLoad](docs/component/DorisStreamLoad.md) : Doris流式加载,支持将数据流式加载到Doris数据库

### 路由组件
- [HashRouter](docs/component/hash-router.md) : Hash路由组件,根据指定字段对数据进行Hash路由
- [RandomRouter](docs/component/random-router.md) : 随机路由组件,根据随机数对数据进行路由
- [RandomRouter](docs/component/RandomRouter.md) : 随机路由组件,根据随机数对数据进行路由

### 其他组件
- [HttpListener](docs/component/HttpListener.md) : HTTP监听器,接受HTTP请求并触发数据处理任务
- [GenerateFlowFile](docs/component/GenerateFlowFile.md) : 流程文件生成器,根据配置生成对应的测试数据

## 监控与日志

- 任务增量状态记录,支持增量数据处理
- 支持本地文件状态存储,MinIO分布式状态存储
- 详细的执行日志记录

## 联系方式

插件不断完善中, 如有问题或建议，请通过项目Issue或加微信号进行反馈, 我们会尽快回复您。支持个性化需求,可以加微信联系我们。

<img src="docs/images/datay.jpg" width="350" height="500" alt="DataY">
