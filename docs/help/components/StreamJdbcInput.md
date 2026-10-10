# StreamJdbcInput 组件配置文档

## 组件概述

StreamJdbcInput 是一个数据输入组件，该组件特别适合需要处理大数据量、要求全量或增量同步的数据迁移和ETL场景。支持常用的关系型数据库Mysql,Postgres,Oracle等数据源. 该组件支持多表同步、增量数据同步和批量数据处理，是数据集成和ETL流程中的重要源头组件。

## 核心特性

- ✅ **多表同步支持**: 支持同时同步多个数据库表
- ✅ **增量数据同步**: 基于增量字段实现增量数据同步
- ✅ **流式读取**: 使用流式读取技术，避免内存溢出
- ✅ **批量处理**: 每10,000条记录批量发送到下游
- ✅ **状态持久化**: 自动保存增量同步状态
- ✅ **条件过滤**: 支持自定义WHERE条件过滤数据

## 配置参数

### 必需参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `table` | String | ✅ | 要同步的表名，支持多表（逗号分隔） | `"user,order,product"` |
| `datasource` | DatasourceInfo | ✅ | 数据源连接信息 | 见数据源配置 |

### 可选参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `incrColumn` | String | ❌ | 增量字段配置，支持JSON格式多表配置 | `"{\"user\": \"id\", \"order\": \"update_time\"}"` |
| `where` | String | ❌ | 自定义WHERE条件，支持多表通用条件 | `"status = 'active'"` |

## 数据源配置 (DatasourceInfo)

StreamJdbcInput 使用标准的 DatasourceInfo 对象配置数据源连接：

```json
{
  "type": "mysql",
  "url": "jdbc:mysql://localhost:3306/testdb",
  "username": "root",
  "password": "password",
  "dbschema": "testdb"
}
```

## 配置示例

### 示例1: 单表全量同步

```json
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
  "table": "common_data_types",
  "where": ""
}
```

### 示例2: 多表增量同步

```json
{
  ".id": "c125de36",
  ".name": "StreamJdbcInput",
  "table": "user,order,product",
  "incrColumn": "{\"user\": \"update_time\", \"order\": \"id\", \"product\": \"modify_time\"}",
  "datasource": {
    "type": "mysql",
    "url": "jdbc:mysql://localhost:3306/testdb",
    "username": "root",
    "password": "password",
    "dbschema": "testdb"
  }
}
```

### 示例3: 带条件过滤的同步

```json
{
  ".id": "c125de36",
  ".name": "StreamJdbcInput",
  "table": "user",
  "where": "status = 'active' AND create_time > '2024-01-01'",
  "datasource": {
    "type": "mysql",
    "url": "jdbc:mysql://localhost:3306/testdb",
    "username": "root",
    "password": "password",
    "dbschema": "testdb"
  }
}
```

## 增量同步机制

### 增量字段配置格式

**单表模式** (传统格式):
```
"incrColumn": "id"
```
**多表模式** (JSON格式):
```
"incrColumn": "{"table1": "field1", "table2": "field2"}"
```

### 数据读取流程
1. **连接数据库**: 建立与源数据库的连接
2. **解析配置**: 解析多表配置和增量字段配置
3. **加载状态**: 加载上次同步的增量状态
4. **构建查询**: 根据配置构建SQL查询语句
5. **流式读取**: 分批读取数据（每批10000条）
6. **状态更新**: 记录当前批次的增量最大值
7. **数据发送**: 将数据发送到下游组件

### 增量同步逻辑

1. **首次执行**: 查询表中所有记录
2. **后续执行**: 只查询增量字段值大于上次最大值的记录
3. **状态保存**: 自动保存每个表的最后增量值
4. **断点续传**: 支持从上次中断的位置继续同步

## 性能配置

- **批量大小**: 每10,000条记录作为一个批次
- **流式读取**: 使用数据库游标避免内存溢出

## 注意事项

1. **权限要求**: 需要源数据库的SELECT权限
2. **网络连接**: 确保到源数据库的网络连接稳定
3. **增量字段**: 增量字段应为有序字段（如ID、时间戳）
4. **内存使用**: 大数据量同步时注意调整JVM内存参数
5. **事务隔离**: 建议使用读已提交的事务隔离级别

## 版本兼容性

- 支持 MySQL、PostgreSQL、Oracle 等主流数据库
- 需要JDBC驱动支持

---

*本文档基于 StreamJdbcInput 组件 v1.0 版本编写*
