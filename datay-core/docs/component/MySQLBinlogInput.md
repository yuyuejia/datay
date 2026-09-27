# MySQLBinlogInput 组件配置文档

## 组件概述

MySQLBinlogInput 是一个基于MySQL二进制日志（Binlog）的数据输入组件，专门用于实时捕获MySQL数据库的数据变更。该组件通过解析MySQL的Binlog日志，能够实时捕获INSERT、UPDATE、DELETE等数据变更操作，是构建实时数据同步、CDC（Change Data Capture）和流式ETL流程的核心组件。

## 核心特性

-  **实时数据变更捕获**: 实时监控MySQL数据库的数据变更
-  **支持多种事件类型**: 支持INSERT、UPDATE、DELETE、DDL等事件
-  **断点续传**: 支持从指定Binlog位置开始采集，支持状态持久化
-  **表级过滤**: 支持基于数据库名和表名的正则表达式过滤
-  **事件合并优化**: 支持相邻相同类型事件的合并处理，提升性能

## 配置参数

### 必需参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `datasource` | DatasourceInfo | ✅ | MySQL数据源连接信息 | 见数据源配置 |

### 可选参数

| 参数名 | 类型 | 必填 | 描述 | 示例 |
|--------|------|------|------|------|
| `binlogFile` | String | ❌ | 指定从哪个Binlog文件开始采集 | `"mysql-bin.000001"` |
| `binlogPosition` | Long | ❌ | 指定从Binlog文件的哪个位置开始采集 | `12345` |
| `databaseNamePattern` | String | ❌ | 数据库名过滤正则表达式 | `"test_.*"` |
| `tableNamePattern` | String | ❌ | 表名过滤正则表达式 | `"user.*"` |
| `snapshot` | Boolean | ❌ | 首次运行（无断点）时先读取历史全量数据，再启动增量同步，默认 `false` | `true` |
| `snapshotFetchSize` | Integer | ❌ | 快照分批大小，默认 `10000` | `5000` |

> 开启 `snapshot` 后，快照读取范围与增量同步范围一致：库由 `databaseNamePattern` 控制（为空回退数据源 schema），表由 `tableNamePattern` 控制；范围外的表不会被读取。

## 数据源配置 (DatasourceInfo)

MySQLBinlogInput 使用标准的 DatasourceInfo 对象配置MySQL数据源连接：

```json
{
  "type": "mysql",
  "url": "jdbc:mysql://localhost:3306/testdb",
  "username": "root",
  "password": "password",
  "dbschema": "testdb"
}
```

**重要配置要求**:
- MySQL必须开启Binlog功能
- 需要配置`binlog_format=ROW`模式
- 需要`REPLICATION SLAVE`权限

## 配置示例

### 示例1: 基础配置（从当前位置开始）

```json
{
  ".id": "mysql-binlog-input",
  ".name": "MySQLBinlogInput",
  "datasource": {
    "type": "mysql",
    "url": "jdbc:mysql://127.0.0.1:3306/source_db",
    "username": "binlog_user",
    "password": "password",
    "dbschema": "source_db"
  }
}
```

### 示例2: 指定起始位置

```json
{
  ".id": "mysql-binlog-input",
  ".name": "MySQLBinlogInput",
  "datasource": {
    "type": "mysql",
    "url": "jdbc:mysql://localhost:3306/testdb",
    "username": "root",
    "password": "password",
    "dbschema": "testdb"
  },
  "binlogFile": "mysql-bin.000123",
  "binlogPosition": 456789
}
```

### 示例3: 表级过滤配置

```json
{
  ".id": "mysql-binlog-input",
  ".name": "MySQLBinlogInput",
  "datasource": {
    "type": "mysql",
    "url": "jdbc:mysql://localhost:3306/production",
    "username": "binlog_user",
    "password": "password",
    "dbschema": "production"
  },
  "databaseNamePattern": "prod_.*",
  "tableNamePattern": "user_.*|order_.*"
}
```

## 工作原理

### Binlog采集流程

1. **连接MySQL**: 使用配置的数据源信息连接MySQL服务器
2. **Binlog订阅**: 订阅MySQL的Binlog流
3. **事件解析**: 实时解析Binlog事件，转换为标准事件格式
4. **事件过滤**: 根据配置的数据库和表名模式进行过滤
5. **事件处理**: 将事件发送到下游组件或进行合并优化
6. **状态保存**: 记录当前处理的Binlog位置，支持断点续传

### 事件合并机制

为了提高处理效率，组件实现了事件合并机制：

- **合并条件**: 相邻的相同类型事件（INSERT/UPDATE/DELETE）
- **合并范围**: 同一数据库、同一表、相同操作类型
- **最大合并数**: 10,000条记录
- **自动刷新**: 达到最大合并数或事件类型变化时自动刷新

## 性能优化建议

### 1. MySQL服务器配置

```sql
-- 确保开启Binlog
SET GLOBAL log_bin = ON;

-- 设置ROW模式（必需）
SET GLOBAL binlog_format = 'ROW';

-- 设置Binlog保留时间
SET GLOBAL binlog_expire_logs_seconds = 2592000; -- 30天

-- 授予必要的权限
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'binlog_user'@'%';
```

## 使用场景

### 1. 实时数据同步

将MySQL的数据变更实时同步到其他数据存储系统（如Elasticsearch、Redis、数据仓库等）。

### 2. 微服务数据变更通知

在微服务架构中，通过Binlog捕获数据变更，发布到消息队列供其他服务消费。

## 故障排除

### 常见问题及解决方案

**问题1: 连接MySQL失败**
- 检查MySQL服务器是否正常运行
- 验证用户名密码是否正确
- 确认网络连接是否通畅

**问题2: 权限不足**
- 确保用户具有`REPLICATION SLAVE`和`REPLICATION CLIENT`权限
- 确认Binlog功能已开启

---

*本文档基于 MySQLBinlogInput 组件 v1.0 版本编写*
