# DataY MySQL 同步到飞书多维表格场景案例

## 1. DataY 项目介绍

DataY 是一个轻量、可嵌入、可扩展、高性能、流批一体的数据集成工具，支持通过 JSON 配置文件的方式定义任务，并执行数据集成任务。内置 DuckDB 引擎和组件，复用 DuckDB 强大的数据处理能力和性能。

### 核心特性
- **简单易用**: 通过 JSON 配置文件定义数据集成任务，无需复杂编码
- **可嵌入**: 可作为库嵌入到其他应用中，提供灵活的数据处理能力
- **可扩展**: 支持组件扩展，满足多样化需求
- **高性能**: 内置 DuckDB 引擎，支持 SQL 进行高效数据处理
- **流批一体**: 支持流处理和批处理任务，可根据场景选择合适的处理模式
- **任务编排**: 支持复杂的数据处理流程编排

## 2. 同步场景说明

### 场景概述
本场景演示如何使用 DataY 将 MySQL 数据库中的数据写入到飞书（Lark）多维表格（Bitable）。通过 `StreamJdbcInput` 组件读取 MySQL 数据表，然后通过 `FeishuBitableOutput` 组件将数据写入到飞书多维表格。

适用于以下典型需求：
- 将业务数据库的线索、工单、库存等数据回写到飞书多维表格，供协作与展示
- 打通「飞书多维表格 ↔ 数据库」双向同步闭环
- 将数据库计算结果沉淀到多维表格，便于业务人员查看

### 数据流向
1. `StreamJdbcInput` 组件读取 MySQL 数据表记录
2. 记录以 JSONArray 形式通过连接传递给 `FeishuBitableOutput` 组件
3. `FeishuBitableOutput` 使用 App ID / App Secret 获取 tenant_access_token
4. 根据记录是否包含 `record_id` 决定新增（batch_create）或更新（batch_update）
5. 数据按批（默认 500 条/批）写入飞书多维表格

### 前置权限要求
写入多维表格需要飞书应用具备**编辑权限**，且应用被授权为该表格的协作者：
- 应用权限（Scope）：`bitable:app`（查看、评论、编辑和管理多维表格）
- 文档权限：在表格右上「分享」或「··· → 更多 → 添加文档应用」中，将应用加为协作者并授予「可编辑」权限（若开启高级权限需授予「可管理」）

## 3. 同步任务操作步骤

### 步骤1: 构建 DataY Jar 包

```bash
# 克隆项目
git clone https://cnb.cool/yuyuejia/datay.git

# 进入项目根目录
cd datay

# 构建项目（跳过测试以加快构建速度）
mvn clean package -DskipTests

# 构建完成后，JAR 包位于 target 目录
ls target/datay-*-jar-with-dependencies.jar
```

### 步骤2: 准备飞书侧信息

**① 应用凭证（App ID / App Secret）**

登录[飞书开放平台](https://open.feishu.cn/)，进入「开发者后台」，在「凭证与基础信息」页面获取：
- `appId`: 应用 App ID，形如 `cli_xxxxxxxx`
- `appSecret`: 应用 App Secret

**② 多维表格标识（appToken / tableId）**

打开飞书多维表格的浏览器地址，URL 形如：

```
https://my.feishu.cn/base/EEmSbfNF8aD2RcsP26CcU3Bvnee?table=tblpjHsRMt8moPj8&view=vew0XXvjv5
```

对应关系如下：

| 参数 | URL 中的位置 | 说明 |
|------|-------------|------|
| `appToken` | `/base/` 后面的部分 | 多维表格（Base）唯一标识 |
| `tableId` | `table=` 后面的值 | 数据表唯一标识 |

> 上例中：`appToken = EEmSbfNF8aD2RcsP26CcU3Bvnee`，`tableId = tblpjHsRMt8moPj8`。

### 步骤3: 创建任务配置文件

创建 `mysql-to-feishu-bitable-task.json` 文件，内容如下：

```json
{
  "units": [
    {
      ".id": "StreamJdbcInput",
      ".name": "StreamJdbcInput",
      "datasource": {
        "url": "jdbc:mysql://127.0.0.1:3306",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "1234qwer",
        "dbschema": "source"
      },
      "table": "user"
    },
    {
      ".id": "FeishuBitableOutput",
      ".name": "FeishuBitableOutput",
      "appId": "cli_aa1c93b88bf8dcb0",
      "appSecret": "",
      "appToken": "EEmSbfNF8aD2RcsP26CcU3Bvnee",
      "tableId": "tblpjHsRMt8moPj8",
      "batchSize": "100"
    }
  ],
  "connections": [
    {
      "sourceId": "StreamJdbcInput",
      "targetId": "FeishuBitableOutput"
    }
  ]
}
```

### 步骤4: 准备 MySQL 数据库

源表字段名需与飞书多维表格字段名一一对应。例如多维表格包含「文本」「单选」「日期」3 个字段，则源表结构如下：

```sql
-- 创建源数据库和表
CREATE DATABASE IF NOT EXISTS source;
USE source;

CREATE TABLE IF NOT EXISTS user (
    `文本` VARCHAR(255),
    `单选` VARCHAR(255),
    `日期` BIGINT
);

-- 插入测试数据（日期字段为毫秒时间戳）
INSERT INTO user VALUES ('mysql写入1', '11', 1788192000000);
INSERT INTO user VALUES ('mysql写入2', '11', 1788192000000);
```

> 说明：
> - 源表列名需与多维表格字段名保持一致（支持中文列名）。
> - 飞书「日期」类型需写 13 位毫秒时间戳，建议源表用 `BIGINT` 存储。
> - 如需「多选」字段，源表值需为逗号分隔字符串，并在写入前用 SQL 组件转换为数组（本组件不自动拆分多选）。

### 步骤5: 启动 DataY 任务

```bash
java -jar datay-1.0.1-jar-with-dependencies.jar mysql-to-feishu-bitable-task.json
```

### 步骤6: 验证同步结果

打开飞书多维表格页面，查看数据是否已写入。或调用飞书接口查询记录数：

```bash
# 获取 token
TOKEN=$(curl -s -X POST "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" \
  -H "Content-Type: application/json" \
  -d '{"app_id":"cli_aa1c93b88bf8dcb0","app_secret":""}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['tenant_access_token'])")

# 查询记录
curl -s "https://open.feishu.cn/open-apis/bitable/v1/apps/EEmSbfNF8aD2RcsP26CcU3Bvnee/tables/tblpjHsRMt8moPj8/records?page_size=10" \
  -H "Authorization: Bearer $TOKEN"
```

## 4. 任务文件配置说明

### 4.1 整体任务结构

DataY 任务配置文件采用 JSON 格式，包含以下主要部分：
- `units`: 定义任务中使用的组件单元
- `connections`: 定义组件之间的数据流向关系

### 4.2 组件详细说明

#### StreamJdbcInput 组件

`StreamJdbcInput` 是一个数据源组件，用于读取关系型数据库中的记录。

**参数说明：**

| 参数名 | 类型 | 必填 | 描述 | 示例值 |
|--------|------|------|------|--------|
| `.id` | String | ✅ | 组件唯一标识 | "StreamJdbcInput" |
| `.name` | String | ✅ | 组件名称 | "StreamJdbcInput" |
| `datasource` | Object | ✅ | 数据源连接配置 | 见下文 |
| `table` | String | ✅ | 源表名 | "user" |

**数据源配置 (datasource)：**

| 参数名 | 类型 | 必填 | 描述 | 示例值 |
|--------|------|------|------|--------|
| `url` | String | ✅ | 数据库连接 URL | "jdbc:mysql://127.0.0.1:3306" |
| `driver` | String | ✅ | JDBC 驱动类 | "com.mysql.cj.jdbc.Driver" |
| `username` | String | ✅ | 数据库用户名 | "root" |
| `password` | String | ✅ | 数据库密码 | "1234qwer" |
| `dbschema` | String | ✅ | 数据库 schema | "source" |

#### FeishuBitableOutput 组件

`FeishuBitableOutput` 是一个数据输出组件，用于将数据写入飞书多维表格。

**参数说明：**

| 参数名 | 类型 | 必填 | 描述 | 示例值 |
|--------|------|------|------|--------|
| `.id` | String | ✅ | 组件唯一标识 | "FeishuBitableOutput" |
| `.name` | String | ✅ | 组件名称 | "FeishuBitableOutput" |
| `appId` | String | ✅ | 飞书应用 App ID | "cli_aa1c93b88bf8dcb0" |
| `appSecret` | String | ✅ | 飞书应用 App Secret | "" |
| `appToken` | String | ✅ | 多维表格（Base）标识 | "EEmSbfNF8aD2RcsP26CcU3Bvnee" |
| `tableId` | String | ✅ | 数据表标识 | "tblpjHsRMt8moPj8" |
| `batchSize` | String | ❌ | 单批写入条数，1~500，默认 500 | "100" |
| `recordIdField` | String | ❌ | record_id 对应的字段名，默认 "record_id" | "record_id" |
| `excludeFields` | String | ❌ | 不写入多维表格的字段（逗号分隔） | "created_time,last_modified_time" |
| `baseUrl` | String | ❌ | 开放平台地址，国际版可设为 open.larksuite.com | "https://open.feishu.cn" |

**功能特性：**
- 自动获取并缓存 tenant_access_token，无需手动维护凭证
- 根据记录是否包含 `record_id` 自动判断新增（batch_create）或更新（batch_update）
- 单批最多 500 条，自动分批写入，规避 QPS 限流
- 支持通过 `excludeFields` 排除系统字段（如 created_time、last_modified_time）

### 4.3 连接配置说明

连接配置定义了组件之间的数据流向关系：

```json
{
  "connections": [
    {
      "sourceId": "StreamJdbcInput",
      "targetId": "FeishuBitableOutput"
    }
  ]
}
```

**参数说明：**
- `sourceId`: 源组件 ID，数据从此组件流出
- `targetId`: 目标组件 ID，数据流入此组件

## 5. 组件参数详细说明

### StreamJdbcInput 组件详细参数

**table 参数：**
- 作用：指定读取的源表名
- 注意：源表列名需与多维表格字段名一致

### FeishuBitableOutput 组件详细参数

**batchSize 参数：**
- 作用：控制单批写入的记录数
- 范围：1~500
- 建议：数据量大时设为 500，减少请求次数；受飞书 QPS 限制时适当调小

**recordIdField 参数：**
- 作用：指定记录中用于标识飞书记录 ID 的字段名
- 默认：`record_id`
- 用途：记录中包含该字段且值非空时执行更新（batch_update），否则执行新增（batch_create）
- 场景：实现增量同步或回写时，先从飞书读取 record_id 回填到数据库，再写入即可实现「已存在则更新、不存在则新增」

**excludeFields 参数：**
- 作用：排除不写入多维表格的字段（逗号分隔）
- 示例：从飞书读取出 `created_time`、`last_modified_time` 等系统字段后，回写时应排除这些字段，避免格式校验失败

### 字段写入格式要求

数据库字段写入飞书多维表格时，需遵循以下格式，否则写入失败：

| 多维表格字段类型 | 写入格式 |
|----------------|---------|
| 文本 / 单行 / 多行文本 | 原生字符串 |
| 数字 | 纯数字（int/float），禁止字符串包裹 |
| 单选 | 选项文本，不存在则自动新建选项 |
| 多选 | 数组格式，如 `["标签1","标签2"]` |
| 日期 | 13 位毫秒时间戳 |
| 复选框 | `true` / `false` |
| 人员 | `[{"id":"open_id","name":"姓名"}]` |
| 附件 | 需先上传素材获取 file_token 再传入 |

## 6. 常见问题与注意事项

### 常见错误及解决方案

**权限错误（91403 Forbidden）：**
- 应用缺少编辑权限：确认已开通 `bitable:app`（查看、评论、编辑和管理多维表格）并发布应用
- 文档权限不足：在表格「分享」或「··· → 更多 → 添加文档应用」中将应用加为协作者并授予「可编辑」权限

**字段格式错误：**
- 多选必须传数组、日期必须传 13 位毫秒时间戳、数字禁止字符串格式
- 字段名需与多维表格字段名完全一致（含中文）

**限流问题：**
- 飞书写入接口有 QPS 限制，大批量数据已自动分批（单批 ≤500），无需额外处理

### 日志监控

DataY 提供详细的执行日志，包括：
- token 获取与缓存状态
- 写入模式（新增/更新）、各批写入条数
- 错误和警告信息

## 7. 扩展应用场景

### 增量同步（回写）
先通过 `FeishuBitableInput` 读取多维表格记录，将 `record_id` 回填到 MySQL，之后写入时 `FeishuBitableOutput` 会根据 `record_id` 自动判断新增或更新，实现「已存在则更新、不存在则新增」。

### 数据转换
可在 `StreamJdbcInput` 与 `FeishuBitableOutput` 之间添加 `DuckDBSql` 组件，对数据进行清洗、字段转换（如将日期字符串转为毫秒时间戳、将逗号分隔字符串转为数组）。

### 定时同步
结合 DataY Web 平台的调度能力，配置定时任务周期执行，实现 MySQL → 飞书多维表格的定时同步。

---

*本文档基于 DataY v1.0.1 版本编写，适用于 MySQL 同步到飞书多维表格的场景*
