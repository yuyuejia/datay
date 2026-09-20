# DataY - 轻量、可嵌入、可扩展、高性能、流批一体的数据平台

## 项目简介

🌐 **产品首页**: [http://datay.yuyuejia.com.cn/](http://datay.yuyuejia.com.cn/)

DataY 是一个集**数据集成引擎**与**数据平台产品**于一体的多模块项目，包含两个子模块：

- **[DataY Core](datay-core/README.md)**: 轻量、可嵌入、可扩展、高性能、流批一体的数据集成引擎，内置 DuckDB 引擎和组件，通过 JSON 配置文件定义数据集成任务，支持自动创建目标表。
- **[DataY Web](datay-web/README.md)**: 基于 DataY Core 构建的数据平台产品，提供可视化的**任务设计**与**任务调度**能力，通过拖拽画布编排 ETL 数据流，支持定时调度、任务依赖。

DataY Core 负责高性能的数据处理执行，DataY Web 负责任务的可视化设计与统一调度，两者配合即可完成从任务编排到数据落地的完整数据平台闭环。

## 模块结构

```
datay/
├── pom.xml          # Maven 父模块
├── datay-core/      # 集成引擎（可嵌入、可扩展、高性能）
└── datay-web/       # 数据平台产品（任务设计 + 任务调度）
```

## 核心特性

- **简单易用**: 支持 Cli 模式运行任务，支持嵌入应用，同时提供拖拽式任务设计界面
- **流批一体**: 支持流处理和批处理任务，可根据场景选择合适的处理模式
- **可扩展**: 支持组件扩展，满足多样化需求，支持 Java 脚本组件自定义数据处理逻辑
- **高性能**: 内置 DuckDB 引擎，支持 SQL 进行高效数据处理
- **任务调度**: 支持定时调度、任务依赖调度、手动触发执行
- **解耦部署**: 调度 master 与执行 worker 可独立部署或合并部署，横向扩展计算节点


## 环境要求

- Java 17+
- Maven 3.6+
- Node 22+（构建 DataY Web 前端）

## 构建项目

```bash
# 1. 构建并安装 datay-core 到本地仓库
mvn -pl datay-core clean install -DskipTests

# 2. 构建 datay-web
mvn -pl datay-web clean package

# 或一次构建所有模块
mvn clean package -DskipTests
```

## 快速开始

### DataY Core：构建 jar 包运行

先构建项目（参考上方「构建项目」章节），构建完成后在 `datay-core/target/` 目录下可找到带依赖的 jar 包。

Mysql同步到Mysql任务配置文件示例：

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
    {
      "sourceId": "c125de36",
      "targetId": "a949c10a"
    }
  ]
}
```

运行任务：

```bash
java -jar datay-core/target/datay-core-*-jar-with-dependencies.jar taskConfig.json
```

### DataY Web：启动 Web 平台

开发模式默认账号：`admin / admin`

```bash
# 开发模式（两个终端）
./mvnw                    # 后端，http://localhost:8080
./npmw start              # 前端，http://localhost:9000

# 生产模式
./mvnw -Pprod clean verify
java -jar target/*.jar    # http://localhost:8080
```

使用步骤：

1. 进入「数据源」页面，添加源/目标数据源并测试连接
2. 进入「ETL 任务」页面新建任务，拖拽组件、连线编排数据流，配置调度并保存
3. 任务列表点击「上线」，任务即加入调度
4. 进入「任务实例」页面查看任务运行状态

## 常用场景案例

- [Mysql同步到Mysql](datay-core/docs/example/mysql-to-mysql.md)
- [Http同步到Mysql](datay-core/docs/example/http-to-mysql.md)
- [飞书多维表格同步到Mysql](datay-core/docs/example/feishu-bitable-to-mysql.md)
- [Mysql同步到飞书多维表格](datay-core/docs/example/mysql-to-feishu-bitable.md)
- [Mysql CDC实时同步到Mysql](datay-core/docs/example/mysqlCDC-mysql.md)
- [Mysql同步到Apache Doris](datay-core/docs/example/mysql-doris.md)
- [Mysql同步到DuckLake](datay-core/docs/example/mysql-ducklake.md)
- [使用DuckDB SQL进行数据处理](datay-core/docs/example/mysql-sqlunit-mysql.md)

## 支持的数据源

- **关系型数据库**: MySQL, Oracle, PostgreSQL, SQL Server, MariaDB
- **分析数据库**: DuckDB, Doris, ClickHouse, GreenPlum
- **文件系统**: 本地文件、MinIO对象存储
- **CDC**: MySQL Binlog

## 核心组件

### 输入组件
- [StreamJdbcInput](datay-core/docs/component/StreamJdbcInput.md) : JDBC数据源输入
- [MySQLBinlogInput](datay-core/docs/component/MySQLBinlogInput.md) : 读取MySQL Binlog日志，支持增量数据同步

### 处理组件
- [DuckDBSql](datay-core/docs/component/DuckDBSql.md) : DuckDB SQL处理
- [StreamSqlUnit](datay-core/docs/component/StreamSqlUnit.md) : 流式SQL处理，使用DuckDB对流式数据进行SQL处理
- [Join](datay-core/docs/component/Join.md) : 多表关联，配置生成DuckDB SQL并输出结果集
- [JavaScriptComponent](datay-core/docs/component/JavaScriptComponent.md) : Java脚本组件，支持在任务中执行自定义逻辑

### 输出组件
- [StreamJdbcOutput](datay-core/docs/component/StreamJdbcOutput.md) : JDBC数据源输出
- [DuckDBWrite](datay-core/docs/component/DuckDBWrite.md) : DuckDB写入
- [DuckLakeWrite](datay-core/docs/component/DuckLakeWrite.md) : DuckLake写入
- [DorisStreamLoad](datay-core/docs/component/DorisStreamLoad.md) : Doris流式加载
- ModelWrite : DataY Web 模型写入组件，选择数据模型后翻译生成 StreamJdbcOutput 任务定义

### 其他组件
- [HttpListener](datay-core/docs/component/HttpListener.md) : HTTP监听器，接受HTTP请求并触发数据处理任务

## 监控与日志

- 任务增量状态记录，支持增量数据处理
- 支持本地文件状态存储，MinIO分布式状态存储
- 记录执行节点、开始/结束时间、执行消息
- 详细的执行日志记录

## 联系方式

插件不断完善中，如有问题或建议，请通过项目Issue或加微信号进行反馈，我们会尽快回复您。支持个性化需求，可以加微信联系我们。

<img src="datay-core/docs/images/datay.jpg" width="350" height="500" alt="DataY">