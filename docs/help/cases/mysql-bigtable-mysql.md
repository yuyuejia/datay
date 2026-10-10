# 基于DataY进行全库大数据量表的全量同步

## 1. DataY 介绍

DataY 是一个轻量、可嵌入、可扩展、高性能、流批一体的数据集成工具，支持通过JSON配置文件的方式定义任务，并执行数据集成任务。内置DuckDB引擎和组件，复用DuckDB强大的数据处理能力和性能。

### 核心特性
- **简单易用**: 通过JSON配置文件定义数据集成任务，无需复杂编码
- **可嵌入**: 可作为库嵌入到其他应用中，提供灵活的数据处理能力
- **可扩展**: 支持组件扩展，满足多样化需求
- **高性能**: 内置DuckDB引擎，支持SQL进行高效数据处理
- **流批一体**: 支持流处理和批处理任务，可根据场景选择合适的处理模式
- **任务编排**: 支持复杂的数据处理流程编排

## 2. 大表同步优化策略

### 2.1 流式查询优化
采用流式查询技术，避免大数据量查询时的内存溢出问题：
- **分批读取**: 每10,000条记录作为一个批次处理
- **游标技术**: 使用数据库游标进行流式读取
- **内存控制**: 避免一次性加载全量数据到内存

### 2.2 分区并行策略
针对大数据量表，DataY提供智能分区并行处理：
- **主键识别**: 自动识别表的主键字段
- **范围分区**: 根据主键值范围进行数据分区
- **并行执行**: 多个分区并行处理，提升同步效率
- **智能适配**: 支持数值类型和字符串类型的分区策略

### 2.3 并发处理优化
- **组件并行度**: 支持配置多个组件的并行度
- **连接池管理**: 优化的数据库连接池管理
- **批量提交**: 批量数据提交减少数据库交互次数

## 3. 优化策略详细说明

### 3.1 分区策略实现原理

#### 3.1.1 数值字符串分区策略
```java
private List<String> generateStringAsNumericPartitionSql(String tableName, String pkName, Object minStr, Object maxStr) {
    List<String> sqlList = new ArrayList<>();
    long min = Long.parseLong(minStr.toString());
    long max = Long.parseLong(maxStr.toString());
    long range = max - min;

    if (range < 0) {
        return generateStringPartitionByChar(tableName, pkName, minStr.toString(), maxStr.toString());
    }

    if (range == 0) {
        sqlList.add("SELECT * FROM " + tableName);
        return sqlList;
    }

    long tablePartitionCount = Math.min(partitionCount, range);
    long partitionSize = Math.max(1, range / tablePartitionCount);

    for (int i = 0; i < tablePartitionCount; i++) {
        long start = min + i * partitionSize;
        long end = start + partitionSize;
        String sql = String.format("SELECT * FROM %s WHERE %s >= '%d' AND %s < '%d'",
                tableName, pkName, start, pkName, end);
        if(i == tablePartitionCount - 1){
            end = max;
            sql = String.format("SELECT * FROM %s WHERE %s >= '%d' AND %s <= '%d'",
                    tableName, pkName, start, pkName, end);
        }
        sqlList.add(sql);
    }
    return sqlList;
}
```

#### 3.1.2 字符串分区策略
对于非数字字符串，采用基于首字符的分区策略
```java
private List<String> generateStringPartitionByChar(String tableName, String pkName, String minStr, String maxStr) {
    List<String> sqlList = new ArrayList<>();

    // 只取第一个字符进行分区
    char minChar = minStr.charAt(0);
    char maxChar = maxStr.charAt(0);

    // 检查字符是否在有效范围内（0-9, a-z）
    if (!(isValidCharRange(minChar)&& isValidCharRange(maxChar))) {
        logInfo("表 " + tableName + " 的主键字符范围超出0-9,a-z，使用普通查询");
        sqlList.add("SELECT * FROM " + tableName);
        return sqlList;
    }

    // 转换为统一的数值范围进行分区计算
    int minVal = charToNumericValue(minChar);
    int maxVal = charToNumericValue(maxChar);
    int charRange = maxVal - minVal;

    int tablePartitionCount = Math.min(partitionCount, charRange);
    int partitionSize = Math.max(1, charRange / tablePartitionCount);

    for (int i = 0; i < tablePartitionCount; i++) {
        int startVal = minVal + i * partitionSize;
        int endVal = startVal + partitionSize;
        String startChar = String.valueOf(numericValueToChar(startVal));
        String endChar = String.valueOf(numericValueToChar(endVal));
        if(i == 0){
            startChar = minStr;
        }
        String sql = String.format("SELECT * FROM %s WHERE %s >= '%s' AND %s < '%s'",
                tableName, pkName, startChar, pkName, endChar);
        if(i == tablePartitionCount - 1){
            endChar = maxStr;
            sql = String.format("SELECT * FROM %s WHERE %s >= '%s' AND %s <= '%s'",
                    tableName, pkName, startChar, pkName, endChar);
        }
        sqlList.add(sql);
    }
    return sqlList;
}
```

### 3.2 流式处理优势
- **内存效率**: 避免一次性加载全量数据
- **处理速度**: 并行处理提升整体吞吐量
- **稳定性**: 分批处理降低单次处理压力

## 4. 同步任务操作步骤

### 4.1 环境准备
- Java 17+ 运行环境
- 目标数据库连接信息
- 源数据库连接信息

### 4.2 构建JAR包

**通过Maven构建获取DataY运行包：**
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

### 4.3 任务配置文件
创建任务配置文件 `big_table_sync.json`：

```json
{
  "units": [
    {
      ".id": "generate_table_sql",
      ".name": "GenerateTableSelectSql",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.jdbc.Driver",
        "username": "root",
        "password": "password",
        "dbschema": "target"
      },
      "schema": "target",
      "partitionCount": 4,
      "enablePartition": true
    },
    {
      ".id": "sql_input_executor",
      ".name": "SqlInput",
      "parallelism": "3",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.jdbc.Driver",
        "username": "root",
        "password": "password",
        "dbschema": "source"
      }
    },
    {
      ".id": "stream_jdbc_output",
      ".name": "StreamJdbcOutput",
      "parallelism": "4",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.jdbc.Driver",
        "username": "root",
        "password": "password",
        "dbschema": "cmm"
      },
      "schema": "cmm",
      "table": "",
      "skipIfTableExists": false,
      "dropIfTableExists": true,
      "model": "overwrite"
    }
  ],
  "connections": [
    {
      "sourceId": "generate_table_sql",
      "targetId": "sql_input_executor"
    },
    {
      "sourceId": "sql_input_executor",
      "targetId": "stream_jdbc_output"
    }
  ]
}
```

### 4.4 任务执行
使用以下命令执行同步任务：
```bash
java -jar datay-1.0.1-jar-with-dependencies.jar big_table_sync.json
```

## 5. 任务文件配置说明

### 5.1 整体任务结构
DataY任务配置文件采用JSON格式，包含以下主要部分：
- `units`: 定义任务中使用的组件单元
- `connections`: 定义组件之间的数据流向关系

### 5.2 组件详细说明

#### 5.2.1 GenerateTableSelectSql组件
**功能**: 自动生成目标schema下所有表的SELECT语句，支持分区优化

**参数说明**:
- `datasource`: 目标数据库连接信息
- `schema`: 要同步的数据库schema名称
- `partitionCount`: 分区数量（默认4）
- `enablePartition`: 是否启用分区优化（默认false）

**分区策略**:
- 自动识别表的主键字段
- 根据主键类型（数值/字符串）采用不同的分区算法
- 生成分区SQL语句，如：`SELECT * FROM table WHERE id >= 1 AND id < 1000`

#### 5.2.2 SqlInput组件
**功能**: 执行上游组件生成的SQL语句，进行数据读取

**参数说明**:
- `datasource`: 源数据库连接信息
- `parallelism`: 并行度，控制并发执行的数量

**特性**:
- 流式读取，避免内存溢出
- 支持多表并行处理

#### 5.2.3 StreamJdbcOutput组件
**功能**: 将数据写入目标数据库

**参数说明**:
- `datasource`: 目标数据库连接信息
- `parallelism`: 并行度，控制写入并发数
- `schema`: 目标schema名称
- `table`: 目标表名（为空时自动从上游获取）
- `model`: 写入模式（overwrite/append/update等）
- `dropIfTableExists`: 是否删除已存在的表
- `skipIfTableExists`: 是否跳过已存在的表

**写入模式**:
- `overwrite`: 先清空目标表，再插入数据（适合全量同步）
- `append`: 在现有数据基础上追加新数据
- `update`: 根据主键更新现有记录


## 6. 性能调优建议

### 6.1 并行度设置
- **SqlInput**: 建议与分区数量匹配
- **StreamJdbcOutput**: 根据目标数据库写入能力设置

## 8. 总结

本案例详细介绍了使用DataY实现大数据量表同步的完整流程。通过智能分区策略和流式处理技术，DataY能够高效处理海量数据同步任务。

**核心优势**:
- 配置简单，学习成本低
- 性能优异，支持智能分区和并行处理
- 扩展性强，支持多种数据源和写入模式
- 部署灵活，可独立运行或嵌入应用

通过本案例的学习，您可以快速掌握DataY在大数据场景下的应用方法，并根据实际需求进行定制化配置，满足企业级数据同步需求。
