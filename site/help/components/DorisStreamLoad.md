# DorisStreamLoad组件文档

## 1. 组件介绍

DorisStreamLoad是DataY数据集成工具中的一个输出组件，专门用于将数据通过Doris的Stream Load协议高效写入到Apache Doris数据库中。该组件支持流式数据写入，能够实现高性能的数据同步和实时数据加载。

### 核心特性
- **高性能写入**: 使用Doris Stream Load协议，支持批量数据写入
- **自动表管理**: 支持自动创建目标表，无需手动建表
- **多模式支持**: 支持INSERT、DELETE等写入模式
- **JSON格式支持**: 使用JSON格式进行数据传输

## 2. 使用场景

### 适用场景
1. **实时数据同步**: 将MySQL、Oracle等关系型数据库的数据实时同步到Doris
2. **数据仓库ETL**: 将处理后的数据批量加载到Doris数据仓库
3. **日志数据收集**: 将应用日志、业务日志实时写入Doris进行分析
4. **流式数据处理**: 与流处理组件配合，实现实时数据处理和存储

### 典型应用架构
```mermaid
graph LR
    A[数据源] --> B[输入组件]
    B --> C[DorisStreamLoad]
    C --> D[Apache Doris]
```

## 3. 配置参数说明

### 3.1 基础配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为"DorisStreamLoad" |
| `datasource` | Object | 是 | - | Doris数据源配置对象 |
| `schema` | String | 否 | - | 目标数据库模式名 |
| `table` | String | 否 | - | 目标表名 |
| `model` | String | 否 | "INSERT" | 写入模式：insert, delete, overwrite |

### 3.2 datasource数据源配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `url` | String | 是 | - | Doris JDBC连接URL，格式：jdbc:mysql://host:port |
| `type` | String | 是 | - | 数据库类型，固定为"Doris" |
| `driver` | String | 是 | - | JDBC驱动类名，固定为"com.mysql.cj.jdbc.Driver" |
| `username` | String | 是 | - | Doris数据库用户名 |
| `password` | String | 是 | - | Doris数据库密码 |
| `dbschema` | String | 是 | - | 目标数据库模式名 |
| `extraParams.fe_endpoint` | String | 是 | - | Doris FE节点HTTP端点，格式：http://host:port |

### 3.3 参数详细说明

#### schema（目标模式）
- **描述**: 指定目标数据所在的数据库模式
- **优先级**: 显式配置 > datasource.dbschema > FlowFile属性 > 默认值
- **自动获取**: 如果未配置，会尝试从FlowFile属性或数据源配置中获取

#### table（目标表名）
- **描述**: 指定要写入的目标表名
- **优先级**: 显式配置 > FlowFile属性 > 抛出异常
- **自动获取**: 如果未配置，会尝试从FlowFile的TABLE属性获取

#### model（写入模式）
- **insert**: 插入模式，将数据追加到目标表
- **delete**: 删除模式，使用DELETE语义删除数据（需要配置主键）
- **overwrite**: 覆盖模式，先清空表再插入数据

## 4. 配置示例

### 4.1 基础配置示例

```json
{
  ".id": "doris_output",
  ".name": "DorisStreamLoad",
  "datasource": {
    "url": "jdbc:mysql://172.20.49.61:19030",
    "type": "Doris",
    "driver": "com.mysql.cj.jdbc.Driver",
    "username": "root",
    "password": "datafusion@123",
    "dbschema": "test_doris",
    "extraParams": {
      "fe_endpoint": "http://172.20.49.61:18030"
    }
  },
  "schema": "test_doris",
  "table": "user_table",
  "model": "insert"
}
```

### 4.2 完整任务配置示例

```json
{
  "units": [
    {
      ".id": "mysql_input",
      ".name": "StreamJdbcInput",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "password",
        "dbschema": "source"
      },
      "schema": "source",
      "table": "users"
    },
    {
      ".id": "doris_output",
      ".name": "DorisStreamLoad",
      "datasource": {
        "url": "jdbc:mysql://172.20.49.61:19030",
        "type": "Doris",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "datafusion@123",
        "dbschema": "test_doris",
        "extraParams": {
          "fe_endpoint": "http://172.20.49.61:18030"
        }
      },
      "schema": "test_doris",
      "table": "users",
      "model": "overwrite"
    }
  ],
  "connections": [
    {
      "sourceId": "mysql_input",
      "targetId": "doris_output"
    }
  ]
}
```

## 5. 技术实现细节

### 5.1 Stream Load协议

DorisStreamLoad组件使用Doris的Stream Load HTTP协议进行数据写入，具有以下特点：
- 使用HTTP PUT方法提交数据
- 支持JSON格式数据传输
- 支持批量写入，提高性能
- 支持事务性写入，确保数据一致性

### 5.2 自动表管理

组件支持自动创建目标表：
- 如果目标表不存在，会根据源表结构自动创建
- 支持跨数据库类型的表结构转换
- 自动处理数据类型映射

---

*本文档基于 DorisStreamLoad 组件 v1.0.1 版本编写*
