# HttpListener 组件配置文档

## 组件概述

HttpListener 是一个HTTP监听器组件，用于启动HTTP服务器监听HTTP请求，将接收到的请求体数据转换为FlowFile并传递给下游组件。该组件作为数据流的源头，支持多种数据格式的自动识别和处理。

## 核心功能特性

- **HTTP服务器**: 内置轻量级HTTP服务器，支持GET、POST、PUT等HTTP方法
- **多格式支持**: 自动识别JSON、CSV、文本等多种数据格式
- **并发处理**: 支持多线程并发处理HTTP请求
- **灵活配置**: 支持自定义监听地址、端口、路径等参数

## 配置参数说明

### 基础配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为"HttpListener" |

### 网络配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `port` | String | 是 | - | HTTP服务监听端口号 |
| `host` | String | 否 | "0.0.0.0" | 监听地址，0.0.0.0表示监听所有网络接口 |
| `path` | String | 否 | "/" | HTTP请求路径，必须以"/"开头 |

### 数据处理参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `dataFormat` | String | 否 | "AUTO" | 数据格式：AUTO/JSON_ARRAY/JSON_OBJECT/CSV/TEXT |
| `enableHttps` | boolean/String | 否 | false | 是否启用HTTPS（当前版本暂不支持） |

## 参数详细说明

### port（端口配置）
- **类型**: 整数或字符串
- **必填**: 是
- **默认值**: 无
- **描述**: HTTP服务监听的端口号
- **取值范围**: 1024-65535（避免使用系统保留端口）
- **示例**: `8080` 或 `"8080"`

### host（主机地址）
- **类型**: 字符串
- **必填**: 否
- **默认值**: "0.0.0.0"
- **描述**: 监听的主机地址
- **取值说明**:
  - `"0.0.0.0"`: 监听所有网络接口
  - `"127.0.0.1"`: 仅监听本地回环地址
  - `"192.168.1.100"`: 监听特定IP地址

### path（请求路径）
- **类型**: 字符串
- **必填**: 否
- **默认值**: "/"
- **描述**: HTTP请求的路径前缀
- **格式要求**: 必须以"/"开头
- **示例**: `"/api"`、`"/data/ingest"`

### dataFormat（数据格式）
- **类型**: 字符串
- **必填**: 否
- **默认值**: "AUTO"
- **可选值**:
  - `"AUTO"`: 自动检测数据格式
  - `"JSON_ARRAY"`: 强制按JSON数组解析
  - `"JSON_OBJECT"`: 强制按JSON对象解析
  - `"CSV"`: 强制按CSV格式解析
  - `"TEXT"`: 强制按纯文本处理

## 数据格式处理

### 自动检测逻辑（AUTO模式）

当`dataFormat`设置为"AUTO"时，组件按以下优先级检测数据格式：

1. **Content-Type头检测**:
   - `application/json` → JSON格式
   - `text/csv`或`application/csv` → CSV格式
   - `text/plain` → 文本格式

2. **内容特征检测**:
   - 以`[`开头 → JSON数组
   - 以`{`开头 → JSON对象
   - 包含逗号分隔值 → CSV格式（启发式检测）

3. **默认处理**: 文本格式

### 格式转换结果

检测到的数据格式会被转换为相应的FlowFile数据类型：

| 数据格式 | FlowFile数据类型 | 描述 |
|----------|------------------|------|
| JSON_ARRAY | JSON数组 | 使用`setJsonArray()`方法 |
| JSON_OBJECT | JSON对象 | 使用`setJsonObject()`方法 |
| CSV | CSV数据 | 使用`setCsvData()`方法 |
| TEXT | 文本数据 | 使用`setTextData()`方法 |

## HTTP请求处理

### 支持的HTTP方法

- **POST**: 处理请求体数据（主要用途）
- **PUT**: 处理请求体数据
- **GET**: 处理查询参数，生成默认数据流

### 请求处理流程

1. **接收请求**: 监听指定端口的HTTP请求
2. **验证方法**: 检查HTTP方法是否支持
3. **读取请求体**: 读取并验证请求体大小
4. **格式检测**: 根据配置检测数据格式
5. **数据转换**: 将请求体转换为FlowFile
6. **属性设置**: 设置请求相关属性
7. **下游传递**: 将FlowFile传递给下游组件
8. **响应返回**: 向客户端返回处理结果

### 请求属性设置

每个HTTP请求都会在FlowFile中设置以下属性：

| 属性名 | 描述 | 示例值 |
|--------|------|--------|
| `_source` | 数据来源标识 | "HttpListener" |
| `_method` | HTTP方法 | "POST" |
| `_path` | 请求路径 | "/api/data" |
| `_timestamp` | 请求时间戳 | 1640995200000 |
| `_clientAddress` | 客户端IP地址 | "192.168.1.100" |
| `_contentType` | Content-Type头 | "application/json" |
| `_dataFormat` | 检测到的数据格式 | "JSON_ARRAY" |
| `_query` | GET请求的查询参数 | "name=test&age=25" |

## 配置示例

### 基础配置示例
```json
{
  ".id": "http_source",
  ".name": "HttpListener",
  "port": "8080",
  "host": "0.0.0.0",
  "path": "/api/data",
  "dataFormat": "AUTO"
}
```

### 生产环境配置示例
```json
{
  ".id": "production_http_listener",
  ".name": "HttpListener",
  "port": "8085",
  "host": "192.168.1.100",
  "path": "/ingest",
  "dataFormat": "JSON_ARRAY"
}
```

### 特定格式配置示例
```json
{
  ".id": "csv_ingest",
  ".name": "HttpListener",
  "port": "8082",
  "path": "/upload/csv",
  "dataFormat": "CSV"
}
```

## 使用场景

### 1. API数据集成
- **场景**: 集成第三方API数据到数据管道
- **配置**: 指定JSON格式，确保数据一致性
- **特点**: 标准化接口，易于维护

### 2. IoT设备数据接收
- **场景**: 接收物联网设备上报的数据
- **配置**: 简单文本格式，小数据包处理
- **特点**: 高并发，稳定可靠

## 完整任务配置示例

### HTTP到MySQL数据同步
```json
{
  "units": [
    {
      ".id": "http_listener",
      ".name": "HttpListener",
      "port": "8085",
      "path": "/api/users",
      "dataFormat": "JSON_ARRAY",
      "maxBodySize": "10485760"
    },
    {
      ".id": "mysql_output",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/app_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "app_user",
        "password": "password",
        "dbschema": "app_db"
      },
      "table": "user_events",
      "model": "append"
    }
  ],
  "connections": [
    {
      "sourceId": "http_listener",
      "targetId": "mysql_output"
    }
  ]
}
```

### 多格式数据接收
```json
{
  "units": [
    {
      ".id": "json_listener",
      ".name": "HttpListener",
      "port": "8081",
      "path": "/json",
      "dataFormat": "JSON_ARRAY"
    },
    {
      ".id": "csv_listener", 
      ".name": "HttpListener",
      "port": "8082",
      "path": "/csv",
      "dataFormat": "CSV"
    },
    {
      ".id": "data_processor",
      ".name": "StreamSqlUnit",
      "sql": "SELECT * FROM input_table"
    }
  ],
  "connections": [
    {
      "sourceId": "json_listener",
      "targetId": "data_processor"
    },
    {
      "sourceId": "csv_listener",
      "targetId": "data_processor"
    }
  ]
}
```

## 故障排除

### 常见问题及解决方案

**问题1**: 端口被占用
- **症状**: 启动失败，端口绑定错误
- **解决**: 更换端口或停止占用端口的进程

**问题2**: 数据格式解析失败
- **症状**: JSON解析错误或格式检测错误
- **解决**: 检查数据格式，或强制指定dataFormat参数

**问题3**: 请求体过大
- **症状**: 请求被拒绝，返回413错误
- **解决**: 调整maxBodySize参数或减小请求体大小

**问题5**: 下游处理阻塞
- **症状**: HTTP请求响应缓慢
- **解决**: 检查下游组件性能，优化数据处理流程

### 日志分析

组件会输出以下关键日志信息：
- `HTTP服务器已启动，监听地址: 0.0.0.0:8080/api` - 服务启动成功
- `收到HTTP请求: POST /api/data from 192.168.1.100` - 请求接收
- `检测到JSON数组格式，元素数量: 100` - 数据格式检测
- `处理HTTP请求时发生错误: ...` - 错误信息

## 注意事项

1. **HTTPS支持**: 当前版本暂不支持HTTPS，如需安全传输建议使用反向代理
2. **认证授权**: 组件不包含认证机制，需要在前置网关或反向代理中实现
3. **持久化**: HTTP请求处理是瞬时的，需要下游组件确保数据持久化
4. **流量控制**: 高并发场景下需要考虑流量控制和限流措施


通过合理配置HttpListener组件，可以构建灵活、高效的HTTP数据接收管道，满足各种实时数据采集和集成需求。
