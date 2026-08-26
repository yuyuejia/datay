# StreamJdbcOutput 组件配置文档

## 组件概述

StreamJdbcOutput 是一个JDBC输出组件，用于将数据写入到关系型数据库中。该组件支持多种写入模式，能够自动处理表创建、数据转换和批量写入等操作。

## 核心功能特性

- **多模式支持**: 支持append、update、replace、overwrite、auto等多种写入模式
- **自动表创建**: 当目标表不存在时，自动根据源表结构创建目标表
- **智能事件处理**: 支持自动识别和处理INSERT、UPDATE、DELETE等事件类型
- **主键识别**: 自动识别主键列，优化UPDATE和DELETE操作

## 配置参数说明

### 基础配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为"StreamJdbcOutput" |
| `parallelism` | String | 否 | "1" | 并行度，控制数据写入的并发数 |

### 数据源配置

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `datasource.url` | String | 是 | - | 目标数据库连接URL |
| `datasource.driver` | String | 是 | - | JDBC驱动类名 |
| `datasource.username` | String | 是 | - | 数据库用户名 |
| `datasource.password` | String | 是 | - | 数据库密码 |
| `datasource.dbschema` | String | 是 | - | 数据库模式名 |

### 表配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `schema` | String | 否 | - | 目标表所在的schema，未配置时从数据源或flowfile属性获取 |
| `table` | String | 否 | - | 目标表名，未配置时从flowfile属性获取 |
| `model` | String | 是 | - | 写入模式：append/update/replace/overwrite/auto |

## 写入模式详解

### append（追加模式）
- **描述**: 在目标表现有数据基础上追加新数据
- **适用场景**: 增量数据同步
- **SQL操作**: INSERT语句

### update（更新模式）
- **描述**: 根据主键更新现有记录
- **适用场景**: 数据更新同步
- **SQL操作**: UPDATE语句（基于主键）

### replace（替换模式）
- **描述**: 替换现有记录（INSERT OR REPLACE）
- **适用场景**: 需要覆盖重复记录的场景
- **SQL操作**: REPLACE语句

### overwrite（覆盖模式）
- **描述**: 先清空目标表，再插入数据
- **适用场景**: 全量数据同步
- **SQL操作**: TRUNCATE + INSERT

### auto（自动模式）
- **描述**: 根据flowfile中的事件类型自动选择写入模式
- **支持的事件类型**: 
  - `INSERT`: 执行INSERT操作
  - `UPDATE`: 执行UPDATE操作
  - `DELETE`: 执行DELETE操作
  - `QUERY/DDL`: 执行DDL语句
- **适用场景**: CDC（变更数据捕获）场景

## 事件类型处理

当使用`auto`模式时，组件会根据flowfile中的事件类型属性自动选择相应的操作：

### 事件类型属性
- **属性名**: `_event_type`
- **取值**: INSERT/UPDATE/DELETE/QUERY/DDL

### 事件处理逻辑
- **INSERT事件**: 执行INSERT语句插入新记录
- **UPDATE事件**: 执行UPDATE语句更新记录（基于主键）
- **DELETE事件**: 执行DELETE语句删除记录（基于主键）
- **QUERY/DDL事件**: 直接执行SQL语句（用于表结构变更等）

## 表名和Schema获取策略

### 表名获取优先级
1. 配置的`table`参数
2. flowfile的`_table`属性
3. flowfile的`_table_metadata`属性中的表名
4. 抛出异常（必须至少有一个来源）

### Schema获取优先级
1. 配置的`schema`参数
2. 数据源的`dbschema`属性（当配置了table参数时）
3. flowfile的`_database`属性
4. 数据源的`dbschema`属性
5. flowfile的`_table_metadata`属性中的schema
6. 数据源的`dbschema`属性（默认值）

## 自动表创建功能

当目标表不存在时，组件会自动创建表

### 创建策略
1. 检查目标表是否存在
2. 如果不存在，尝试从flowfile的`_table_metadata`属性获取源表结构
3. 使用`DatabaseConverter`转换表结构到目标数据库类型
4. 执行生成的DDL语句创建表
5. 如果`model`为`overwrite`，清空表数据

### 表结构转换
- 支持不同数据库类型之间的表结构转换
- 自动处理数据类型映射
- 保留主键、索引等约束信息

## 配置示例

### 基础配置示例
```json
{
  ".id": "output_component",
  ".name": "StreamJdbcOutput",
  "parallelism": "4",
  "datasource": {
    "url": "jdbc:mysql://localhost:3306/testdb",
    "driver": "com.mysql.jdbc.Driver",
    "username": "root",
    "password": "password",
    "dbschema": "target_schema"
  },
  "table": "target_table",
  "model": "append"
}
```

### 自动模式配置示例
```json
{
  ".id": "cdc_output",
  ".name": "StreamJdbcOutput",
  "parallelism": "2",
  "datasource": {
    "url": "jdbc:postgresql://localhost:5432/cdc_db",
    "driver": "org.postgresql.Driver",
    "username": "postgres",
    "password": "password",
    "dbschema": "public"
  },
  "model": "auto"
}
```

### 覆盖模式配置示例
```json
{
  ".id": "full_sync_output",
  ".name": "StreamJdbcOutput",
  "datasource": {
    "url": "jdbc:oracle:thin:@localhost:1521:xe",
    "driver": "oracle.jdbc.OracleDriver",
    "username": "system",
    "password": "password",
    "dbschema": "SYSTEM"
  },
  "table": "employees",
  "model": "overwrite"
}
```

## 使用场景

### 1. 全量数据同步
- **模式**: overwrite
- **特点**: 每次执行前清空目标表，适合定期全量同步

### 2. 增量数据同步
- **模式**: append
- **特点**: 保留历史数据，只追加新数据

### 3. CDC（变更数据捕获）
- **模式**: auto
- **特点**: 自动识别和处理INSERT/UPDATE/DELETE操作

### 4. 数据更新同步
- **模式**: update
- **特点**: 基于主键更新现有记录

### 5. 表结构同步
- **模式**: auto + DDL事件
- **特点**: 自动执行表结构变更语句

## 最佳实践

### 性能优化
1. **合理设置并行度**: 根据目标数据库的并发能力调整parallelism参数
2. **批量提交**: 组件自动使用批量提交，减少数据库交互次数
3. **连接池配置**: 确保数据源配置了合适的连接池参数

## 注意事项

1. **权限要求**: 确保数据库用户具有相应的读写权限和表创建权限
2. **数据类型兼容性**: 注意不同数据库类型之间的数据类型映射
3. **主键要求**: UPDATE和DELETE模式需要表有主键或唯一约束
4. **事件类型依赖**: auto模式需要flowfile提供正确的事件类型属性

## 故障排除

### 常见问题及解决方案

**问题1**: 表创建失败
- **原因**: 权限不足或表已存在
- **解决**: 检查用户权限，确认表名唯一性

**问题2**: 数据类型转换错误
- **原因**: 源和目标数据库类型不兼容
- **解决**: 检查数据类型映射，必要时手动创建表结构

**问题3**: UPDATE/DELETE操作失败
- **原因**: 表缺少主键或唯一约束
- **解决**: 为目标表添加主键或使用append模式

**问题4**: 性能问题
- **原因**: 并行度过高或批量大小不合适
- **解决**: 调整parallelism参数，监控数据库性能指标
---

*本文档基于 StreamJdbcOutput 组件 v1.0 版本编写*
