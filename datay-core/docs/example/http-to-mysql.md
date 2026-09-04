# DataY HTTP监听数据同步到MySQL场景案例

## 1. DataY 项目介绍

DataY 是一个轻量、可嵌入、可扩展、高性能、流批一体的数据集成工具，支持通过JSON配置文件的方式定义任务，并执行数据集成任务。内置DuckDB引擎和组件，复用DuckDB强大的数据处理能力和性能。

### 核心特性
- **简单易用**: 通过JSON配置文件定义数据集成任务，无需复杂编码
- **可嵌入**: 可作为库嵌入到其他应用中，提供灵活的数据处理能力
- **可扩展**: 支持组件扩展，满足多样化需求
- **高性能**: 内置DuckDB引擎，支持SQL进行高效数据处理
- **流批一体**: 支持流处理和批处理任务，可根据场景选择合适的处理模式
- **任务编排**: 支持复杂的数据处理流程编排

## 2. 同步场景说明

### 场景概述
本场景演示如何使用DataY实现HTTP监听接收数据并同步到MySQL数据库的完整流程。通过HttpListener组件监听HTTP请求，接收外部系统发送的数据，然后通过StreamJdbcOutput组件将数据写入到MySQL数据库。


### 数据流向
1. 外部系统通过HTTP POST请求向HttpListener发送数据
2. HttpListener接收数据并进行初步处理
3. 数据通过连接传递给StreamJdbcOutput组件
4. StreamJdbcOutput组件将数据写入到MySQL数据库的指定表中

## 3. 同步任务操作步骤

### 步骤1: 构建DataY Jar包

```bash
# 克隆项目
git clone https://cnb.cool/yuyuejia/datay.git

# 进入项目根目录
cd datay

# 构建项目（跳过测试以加快构建速度）
mvn clean package -DskipTests

# 构建完成后，JAR包位于target目录
ls target/datay-*-jar-with-dependencies.jar
```

### 步骤2: 创建任务配置文件

创建 `http-to-mysql-task.json` 文件，内容如下：

```json
{
  "units": [
    {
      ".id": "HttpListener",
      ".name": "HttpListener",
      "inputData": "common_data_types",
      "dataFormat": "AUTO",
      "path": "/test",
      "port": "8085"
    },
    {
      ".id": "StreamJdbcOutput",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "1234qwer",
        "dbschema": "source"
      },
      "table": "user",
      "model": "append"
    }
  ],
  "connections": [
    {
      "sourceId": "HttpListener",
      "targetId": "StreamJdbcOutput"
    }
  ]
}
```

### 步骤3: 准备MySQL数据库

```sql
-- 创建目标数据库和表
CREATE DATABASE IF NOT EXISTS source;
USE source;

CREATE TABLE IF NOT EXISTS user (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100),
    age INT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 步骤4: 启动DataY任务

```bash
java -jar datay-1.0.1-jar-with-dependencies.jar http-to-mysql-task.json
```

### 步骤5: 测试数据同步

使用curl或其他HTTP客户端发送测试数据：

```bash
curl -X POST http://localhost:8085/test \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com",
    "age": 25,
    "created_time": "2023-07-01 12:00:00"
  }'
```

## 4. 任务文件配置说明

### 4.1 整体任务结构

DataY任务配置文件采用JSON格式，包含以下主要部分：
- `units`: 定义任务中使用的组件单元
- `connections`: 定义组件之间的数据流向关系

### 4.2 组件详细说明

#### HttpListener 组件

HttpListener是一个HTTP监听器组件，用于接收HTTP请求并触发数据处理任务。

**参数说明：**

| 参数名 | 类型 | 必填 | 描述 | 示例值 |
|--------|------|------|------|--------|
| `.id` | String | ✅ | 组件唯一标识 | "HttpListener" |
| `.name` | String | ✅ | 组件名称 | "HttpListener" |
| `inputData` | String | ✅ | 输入数据类型标识 | "common_data_types" |
| `dataFormat` | String | ✅ | 数据格式，AUTO表示自动识别 | "AUTO" |
| `path` | String | ✅ | HTTP监听路径 | "/test" |
| `port` | String | ✅ | HTTP监听端口 | "8085" |

**功能特性：**
- 支持HTTP POST请求接收数据
- 自动识别JSON、XML等数据格式
- 支持多线程并发处理
- 内置请求验证和错误处理

#### StreamJdbcOutput 组件

StreamJdbcOutput是一个流式JDBC输出组件，用于将数据写入到关系型数据库。

**参数说明：**

| 参数名 | 类型 | 必填 | 描述 | 示例值 |
|--------|------|------|------|--------|
| `.id` | String | ✅ | 组件唯一标识 | "StreamJdbcOutput" |
| `.name` | String | ✅ | 组件名称 | "StreamJdbcOutput" |
| `datasource` | Object | ✅ | 数据源连接配置 | 见下文 |
| `table` | String | ✅ | 目标表名 | "user" |
| `model` | String | ✅ | 写入模式 | "append" |

**数据源配置 (datasource)：**

| 参数名 | 类型 | 必填 | 描述 | 示例值 |
|--------|------|------|------|--------|
| `url` | String | ✅ | 数据库连接URL | "jdbc:mysql://127.0.0.1:3306" |
| `driver` | String | ✅ | JDBC驱动类 | "com.mysql.cj.jdbc.Driver" |
| `username` | String | ✅ | 数据库用户名 | "root" |
| `password` | String | ✅ | 数据库密码 | "1234qwer" |
| `dbschema` | String | ✅ | 数据库schema | "source" |

**写入模式 (model)：**
- `append`: 追加模式，向表中插入新记录
- `overwrite`: 覆盖模式，先清空表再插入数据
- `update`: 更新模式，根据主键更新记录

### 4.3 连接配置说明

连接配置定义了组件之间的数据流向关系：

```json
{
  "connections": [
    {
      "sourceId": "HttpListener",
      "targetId": "StreamJdbcOutput"
    }
  ]
}
```

**参数说明：**
- `sourceId`: 源组件ID，数据从此组件流出
- `targetId`: 目标组件ID，数据流入此组件

## 5. 组件参数详细说明

### HttpListener 组件详细参数

**inputData 参数：**
- 作用：标识输入数据的类型，用于内部数据路由和处理
- 取值：预定义的数据类型标识符
- 示例："common_data_types" 表示通用数据类型

**dataFormat 参数：**
- 作用：指定输入数据的格式
- 取值：
    - "AUTO": 自动识别数据格式
    - "JSON": 强制JSON格式
    - "XML": 强制XML格式
- 推荐使用"AUTO"以支持多种数据格式

**path 参数：**
- 作用：定义HTTP监听的URL路径
- 格式：必须以"/"开头
- 示例："/api/data" 将监听 http://host:port/api/data

**port 参数：**
- 作用：定义HTTP服务监听的端口号
- 范围：1024-65535（避免使用系统保留端口）
- 注意：确保端口未被其他应用占用

### StreamJdbcOutput 组件详细参数

**table 参数：**
- 作用：指定数据写入的目标表
- 要求：表必须存在，且字段结构与输入数据匹配
- 支持：单表写入，不支持多表同时写入

**model 参数详细说明：**
- `append`：向表中插入新记录，不检查重复
- `overwrite`：先执行`TRUNCATE TABLE`或`DELETE FROM`，再插入数据
- `update`：根据表的主键进行更新插入操作


## 7. 错误处理与监控

### 常见错误及解决方案

**连接错误：**
- MySQL连接失败：检查数据库服务状态和连接参数
- 端口占用：检查8085端口是否被其他应用占用

**数据格式错误：**
- JSON解析失败：确保发送的数据是有效的JSON格式
- 字段不匹配：检查MySQL表结构与输入数据字段的对应关系

### 日志监控
DataY提供详细的执行日志，包括：
- 组件启动状态
- 数据接收和处理的统计信息
- 错误和警告信息

## 8. 扩展应用场景

### 数据转换
可以在HttpListener和StreamJdbcOutput之间添加数据处理组件，进行数据清洗和转换。

---

*本文档基于DataY v1.0.1版本编写，适用于HTTP监听数据同步到MySQL的场景*
