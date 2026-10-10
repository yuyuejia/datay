# StreamSqlUnit 组件配置文档

## 组件概述

StreamSqlUnit 是一个流式SQL处理组件，用于在数据流管道中执行DuckDB SQL语句对数据进行转换和处理。该组件接收上游数据，将其写入DuckDB临时表，然后执行配置的SQL语句对数据进行转换和处理，最后将处理结果传递给下游组件。

## 核心功能特性

- **SQL数据处理**: 支持在数据流中执行任意SQL查询和转换操作
- **DuckDB集成**: 内置DuckDB引擎，提供高性能的SQL处理能力
- **流式处理**: 支持流式数据转换，适合实时数据处理场景
- **自动临时表管理**: 自动创建和管理DuckDB临时表
- **元数据保留**: 保留上游的事件类型和表元数据信息

## 配置参数说明

### 基础配置参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `.id` | String | 是 | - | 组件唯一标识符 |
| `.name` | String | 是 | - | 组件名称，固定为"StreamSqlUnit" |

### 数据处理参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `sql` | String | 是 | - | 要执行的SQL查询语句 |

## 参数详细说明

### sql（SQL查询语句）
- **类型**: 字符串
- **必填**: 是
- **默认值**: 无
- **描述**: 在DuckDB中执行的SQL查询语句
- **语法要求**: 标准的SQL语法，支持DuckDB特有的函数和特性
- **数据源**: 查询的数据来自上游组件写入的临时表
- **示例**: 
  - `"SELECT * FROM input_table WHERE age > 18"`
  - `"SELECT name, COUNT(*) as count FROM input_table GROUP BY name"`
  - `"SELECT *, UPPER(name) as upper_name FROM input_table"`

## 数据处理流程

### 1. 数据接收阶段
- 接收上游组件传递的FlowFile数据
- 验证数据有效性（JSON数组不为空）
- 获取上游表的元数据信息

### 2. 临时表管理阶段
- 自动创建DuckDB schema（如不存在）
- 自动创建临时表结构（基于上游表元数据）
- 清空临时表数据（TRUNCATE TABLE）
- 使用DuckDB Appender高效写入数据

### 3. SQL执行阶段
- 配置流式读取（FETCH_SIZE = 5000）
- 执行配置的SQL查询语句
- 从ResultSet中提取查询结果
- 自动生成输出表的元数据

### 4. 结果输出阶段
- 将查询结果转换为JSON数组格式
- 保留上游的事件类型属性
- 设置输出表的元数据信息
- 将处理结果传递给下游组件

## 配置示例

### 基础数据过滤示例
```json
{
  ".id": "data_filter",
  ".name": "StreamSqlUnit",
  "sql": "SELECT * FROM input_table WHERE status = 'ACTIVE' AND create_time > '2024-01-01'"
}
```

### 数据聚合计算示例
```json
{
  ".id": "aggregation",
  ".name": "StreamSqlUnit", 
  "sql": "SELECT department, AVG(salary) as avg_salary, COUNT(*) as employee_count FROM input_table GROUP BY department"
}
```

### 数据转换和字段处理示例
```json
{
  ".id": "data_transform",
  ".name": "StreamSqlUnit",
  "sql": "SELECT id, UPPER(name) as name_upper, EXTRACT(YEAR FROM birth_date) as birth_year, CONCAT(address, ', ', city) as full_address FROM input_table"
}
```

### 复杂业务逻辑处理示例
```json
{
  ".id": "business_logic",
  ".name": "StreamSqlUnit",
  "sql": "SELECT *, CASE WHEN age < 18 THEN 'MINOR' WHEN age BETWEEN 18 AND 65 THEN 'ADULT' ELSE 'SENIOR' END as age_group, ROUND(salary * 1.1, 2) as adjusted_salary FROM input_table WHERE department IN ('IT', 'HR', 'Finance')"
}
```

## 完整任务配置示例

### 数据清洗和转换管道
```json
{
  "units": [
    {
      ".id": "jdbc_input",
      ".name": "StreamJdbcInput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/source_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "user",
        "password": "password",
        "dbschema": "source_db"
      },
      "table": "raw_data"
    },
    {
      ".id": "data_cleaner",
      ".name": "StreamSqlUnit",
      "sql": "SELECT id, TRIM(name) as clean_name, COALESCE(email, 'unknown') as email, CAST(age AS INTEGER) as age_int FROM input_table WHERE age IS NOT NULL AND age != ''"
    },
    {
      ".id": "mysql_output",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:mysql://localhost:3306/target_db",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "user",
        "password": "password",
        "dbschema": "target_db"
      },
      "table": "clean_data",
      "model": "append"
    }
  ],
  "connections": [
    {
      "sourceId": "jdbc_input",
      "targetId": "data_cleaner"
    },
    {
      "sourceId": "data_cleaner", 
      "targetId": "mysql_output"
    }
  ]
}
```

### 实时数据分析和告警管道
```json
{
  "units": [
    {
      ".id": "http_listener",
      ".name": "HttpListener",
      "port": "8080",
      "path": "/metrics",
      "dataFormat": "JSON_ARRAY"
    },
    {
      ".id": "anomaly_detector",
      ".name": "StreamSqlUnit",
      "sql": "SELECT metric_name, AVG(value) as avg_value, STDDEV(value) as std_value, COUNT(*) as sample_count, CASE WHEN ABS(value - AVG(value) OVER()) > 2 * STDDEV(value) OVER() THEN 'ANOMALY' ELSE 'NORMAL' END as status FROM input_table GROUP BY metric_name, value"
    },
    {
      ".id": "alert_filter",
      ".name": "StreamSqlUnit", 
      "sql": "SELECT * FROM input_table WHERE status = 'ANOMALY'"
    },
    {
      ".id": "alert_output",
      ".name": "StreamJdbcOutput",
      "datasource": {
        "url": "jdbc:postgresql://localhost:5432/alerts",
        "driver": "org.postgresql.Driver",
        "username": "alert_user",
        "password": "password",
        "dbschema": "public"
      },
      "table": "anomaly_alerts",
      "model": "append"
    }
  ],
  "connections": [
    {
      "sourceId": "http_listener",
      "targetId": "anomaly_detector"
    },
    {
      "sourceId": "anomaly_detector",
      "targetId": "alert_filter"
    },
    {
      "sourceId": "alert_filter",
      "targetId": "alert_output"
    }
  ]
}
```

## 使用场景

### 1. 数据清洗和标准化
- **场景**: 清洗原始数据，处理空值、格式转换等
- **SQL示例**: 
  ```sql
  SELECT id, TRIM(name), COALESCE(email, 'unknown'), 
         CAST(amount AS DECIMAL(10,2)) as clean_amount
  FROM input_table 
  WHERE status IS NOT NULL
  ```

### 2. 数据聚合和统计
- **场景**: 实时计算指标和统计信息
- **SQL示例**:
  ```sql
  SELECT category, 
         COUNT(*) as total_count,
         AVG(price) as avg_price,
         SUM(quantity) as total_quantity
  FROM input_table 
  GROUP BY category
  ```

### 3. 数据关联和丰富
- **场景**: 关联多个数据源，丰富数据内容
- **SQL示例**:
  ```sql
  SELECT a.*, b.category_name, c.region_name
  FROM input_table a
  LEFT JOIN categories b ON a.category_id = b.id
  LEFT JOIN regions c ON a.region_id = c.id
  ```

### 4. 业务规则处理
- **场景**: 应用复杂的业务逻辑和规则
- **SQL示例**:
  ```sql
  SELECT *,
         CASE 
           WHEN score >= 90 THEN 'A'
           WHEN score >= 80 THEN 'B' 
           WHEN score >= 70 THEN 'C'
           ELSE 'D'
         END as grade,
         CASE 
           WHEN days_overdue > 30 THEN 'HIGH_RISK'
           WHEN days_overdue > 15 THEN 'MEDIUM_RISK' 
           ELSE 'LOW_RISK'
         END as risk_level
  FROM input_table
  ```

### 5. 数据格式转换
- **场景**: 转换数据格式，适配不同系统需求
- **SQL示例**:
  ```sql
  SELECT id,
         TO_DATE(create_time, 'YYYY-MM-DD') as create_date,
         EXTRACT(HOUR FROM event_time) as event_hour,
         CONCAT(first_name, ' ', last_name) as full_name
  FROM input_table
  ```

## SQL语法支持

### DuckDB支持的SQL特性
StreamSqlUnit基于DuckDB引擎，支持丰富的SQL功能：

#### 基础SQL功能
- **SELECT查询**: 完整的SELECT语法支持
- **WHERE条件**: 复杂的条件过滤
- **GROUP BY**: 分组聚合操作
- **JOIN操作**: 内连接、外连接等
- **子查询**: 嵌套查询支持

#### 高级功能
- **窗口函数**: ROW_NUMBER(), RANK(), LAG(), LEAD()等
- **字符串函数**: 丰富的字符串处理函数
- **日期时间函数**: 日期时间计算和格式化
- **数学函数**: 数学运算和统计函数
- **类型转换**: 灵活的数据类型转换

#### DuckDB特有功能
- **矢量化执行**: 高性能的查询执行
- **并行处理**: 多核并行查询优化
- **内存优化**: 高效的内存管理


StreamSqlUnit组件为数据流处理提供了强大的SQL处理能力，通过灵活的SQL配置可以实现复杂的数据转换、清洗、聚合等业务逻辑，是构建高效数据管道的重要组件。
