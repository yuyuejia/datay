# DataY 飞书多维表格同步到 MySQL 场景案例

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
本场景演示如何使用 DataY 将飞书（Lark）多维表格（Bitable）中的数据同步到 MySQL 数据库。通过 `FeishuBitableInput` 组件读取飞书多维表格中的记录，然后通过 `StreamJdbcOutput` 组件将数据写入到 MySQL 数据库。

适用于以下典型需求：
- 将飞书多维表格作为业务数据源，定期/一次性同步到业务数据库
- 将表格中的线索、工单、库存等数据沉淀到 MySQL 供后续分析
- 打通飞书办公协作数据与本地数据仓库

### 数据流向
1. `FeishuBitableInput` 组件使用 App ID / App Secret 获取访问凭证（tenant_access_token）
2. 分页读取飞书多维表格指定数据表的全部记录
3. 每条记录被扁平化为「字段名 -> 字段值」的结构，并附带 `record_id`
4. 数据通过连接传递给 `StreamJdbcOutput` 组件
5. `StreamJdbcOutput` 组件将数据写入到 MySQL 数据库的指定表中

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
ls data-core/target/datay-core-*-jar-with-dependencies.jar
```

### 步骤2: 准备飞书侧信息

在创建任务配置前，需要先准备好以下 4 项信息：

**① 应用凭证（App ID / App Secret）**

登录[飞书开放平台](https://open.feishu.cn/)，进入「开发者后台」，创建一个企业自建应用，在「凭证与基础信息」页面获取：
- `appId`: 应用 App ID，形如 `cli_xxxxxxxx`
- `appSecret`: 应用 App Secret

> 注意：需要给该应用开通**多维表格（bitable）**相关权限（如 `bitable:app`），并将应用发布到可用的版本，否则调用接口会提示无权限。

**② 多维表格标识（appToken / tableId / viewId）**

打开飞书多维表格的浏览器地址，URL 形如：

```
https://my.feishu.cn/base/EEmSbfNF8aD2RcsP26CcU3Bvnee?table=tblpjHsRMt8moPj8&view=vew0XXvjv5
```

对应关系如下：

| 参数 | URL 中的位置 | 说明 |
|------|-------------|------|
| `appToken` | `/base/` 后面的部分 | 多维表格（Base）唯一标识 |
| `tableId` | `table=` 后面的值 | 数据表唯一标识 |
| `viewId` | `view=` 后面的值 | 视图唯一标识（可选，用于按视图过滤） |

> 上例中：`appToken = EEmSbfNF8aD2RcsP26CcU3Bvnee`，`tableId = tblpjHsRMt8moPj8`，`viewId = vew0XXvjv5`。

### 步骤3: 创建任务配置文件

创建 `feishu-bitable-to-mysql-task.json` 文件，内容如下：

```json
{
  "units": [
    {
      ".id": "FeishuBitableInput",
      ".name": "FeishuBitableInput",
      "appId": "cli_aa1c93b88bf8dcb0",
      "appSecret": "",
      "appToken": "EEmSbfNF8aD2RcsP26CcU3Bvnee",
      "tableId": "tblpjHsRMt8moPj8",
      "viewId": "vew0XXvjv5",
      "pageSize": "100"
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
      "sourceId": "FeishuBitableInput",
      "targetId": "StreamJdbcOutput"
    }
  ]
}
```

### 步骤4: 准备 MySQL 数据库

目标表字段需要与飞书多维表格的字段一一对应。例如多维表格包含「文本」「单选」「日期」「附件」4 个字段，则目标表结构如下：

```sql
-- 创建目标数据库和表
CREATE DATABASE IF NOT EXISTS source;
USE source;

CREATE TABLE IF NOT EXISTS user (
    record_id VARCHAR(64) PRIMARY KEY,
    `文本` VARCHAR(255),
    `单选` VARCHAR(255),
    `日期` BIGINT,
    `附件` VARCHAR(1024)
);
```

> 说明：
> - `record_id` 列对应飞书每条记录的唯一标识，建议作为主键。
> - 其余列名需与多维表格字段名保持一致（支持中文列名）。
> - 飞书「日期」类型以时间戳（毫秒）返回，可建为 `BIGINT`，也可在后续用 SQL 组件转换为日期格式。

### 步骤5: 启动 DataY 任务

```bash
java -jar datay-core-1.0.1-jar-with-dependencies.jar feishu-bitable-to-mysql-task.json
```

### 步骤6: 验证同步结果

```sql
USE source;

-- 查看同步到的记录数
SELECT COUNT(*) FROM user;

-- 查看同步的数据
SELECT * FROM user;
```

若输出结果中的记录数与飞书多维表格中的记录数一致，则说明同步成功。

## 4. 任务文件配置说明

### 4.1 整体任务结构

DataY 任务配置文件采用 JSON 格式，包含以下主要部分：
- `units`: 定义任务中使用的组件单元
- `connections`: 定义组件之间的数据流向关系

### 4.2 组件详细说明

#### FeishuBitableInput 组件

`FeishuBitableInput` 是一个数据源组件，用于读取飞书多维表格中的记录。

**参数说明：**

| 参数名 | 类型 | 必填 | 描述 | 示例值 |
|--------|------|------|------|--------|
| `.id` | String | ✅ | 组件唯一标识 | "FeishuBitableInput" |
| `.name` | String | ✅ | 组件名称 | "FeishuBitableInput" |
| `appId` | String | ✅ | 飞书应用 App ID | "cli_aa1c93b88bf8dcb0" |
| `appSecret` | String | ✅ | 飞书应用 App Secret | "" |
| `appToken` | String | ✅ | 多维表格（Base）标识 | "EEmSbfNF8aD2RcsP26CcU3Bvnee" |
| `tableId` | String | ✅ | 数据表标识 | "tblpjHsRMt8moPj8" |
| `viewId` | String | ❌ | 视图标识，不填则读取整个表 | "vew0XXvjv5" |
| `pageSize` | String | ❌ | 单页拉取条数，1~500，默认 100 | "100" |
| `recordIdField` | String | ❌ | record_id 对应的字段名，默认 "record_id" | "record_id" |
| `createdTimeField` | String | ❌ | 创建时间对应的字段名，默认 "created_time" | "created_time" |
| `modifiedTimeField` | String | ❌ | 最后更新时间对应的字段名，默认 "last_modified_time" | "last_modified_time" |
| `incrColumn` | String | ❌ | 增量同步字段，取值 `last_modified_time` 或 `created_time`，为空表示全量同步 | "last_modified_time" |
| `fetchFields` | String | ❌ | 是否同时拉取字段元数据，默认 true | "true" |
| `baseUrl` | String | ❌ | 开放平台地址，国际版可设为 open.larksuite.com | "https://open.feishu.cn" |

**功能特性：**
- 自动获取并缓存 tenant_access_token，无需手动维护凭证
- 自动分页拉取全部记录，单表最多 500 条/页
- 自动将复杂字段（人员、附件、链接、多选等）扁平化为简单值
- 每条记录附带 `record_id`，方便作为目标表主键
- 按 `pageSize` 分批向下游传递数据，支持大批量数据

#### StreamJdbcOutput 组件

`StreamJdbcOutput` 是一个流式 JDBC 输出组件，用于将数据写入到关系型数据库。

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
| `url` | String | ✅ | 数据库连接 URL | "jdbc:mysql://127.0.0.1:3306" |
| `driver` | String | ✅ | JDBC 驱动类 | "com.mysql.cj.jdbc.Driver" |
| `username` | String | ✅ | 数据库用户名 | "root" |
| `password` | String | ✅ | 数据库密码 | "1234qwer" |
| `dbschema` | String | ✅ | 数据库 schema | "source" |

**写入模式 (model)：**
- `append`: 追加模式，向表中插入新记录
- `overwrite`: 覆盖模式，先清空表再插入数据

### 4.3 连接配置说明

连接配置定义了组件之间的数据流向关系：

```json
{
  "connections": [
    {
      "sourceId": "FeishuBitableInput",
      "targetId": "StreamJdbcOutput"
    }
  ]
}
```

**参数说明：**
- `sourceId`: 源组件 ID，数据从此组件流出
- `targetId`: 目标组件 ID，数据流入此组件

## 5. 组件参数详细说明

### FeishuBitableInput 组件详细参数

**appId / appSecret 参数：**
- 作用：用于调用飞书开放平台接口的身份凭证
- 获取方式：飞书开放平台 -> 开发者后台 -> 应用 -> 凭证与基础信息
- 注意：应用需开通多维表格权限并发布，否则接口调用会返回无权限

**appToken / tableId 参数：**
- 作用：定位要读取的多维表格和数据表
- 获取方式：打开多维表格页面，从浏览器地址栏 URL 中提取
- `appToken` 位于 `/base/` 之后，`tableId` 位于 `table=` 参数中

**viewId 参数：**
- 作用：按指定视图读取数据（视图中的筛选/排序条件生效）
- 取值：URL 中 `view=` 参数的值
- 不填：读取整个数据表（不受视图过滤影响）

**pageSize 参数：**
- 作用：控制单页拉取的记录数，影响请求次数与单批内存占用
- 范围：1~500
- 建议：数据量大时可设为 500 以减少请求次数

**recordIdField 参数：**
- 作用：飞书记录唯一标识对应的字段名
- 默认：`record_id`
- 用途：每条记录必附带 record_id，便于作为目标表主键实现幂等写入

**createdTimeField / modifiedTimeField 参数：**
- 作用：飞书创建时间 / 最后更新时间对应的字段名
- 默认：`created_time` / `last_modified_time`
- 说明：每条记录必附带这两个时间戳字段（毫秒值），可配合 `incrColumn` 做增量同步

**incrColumn 参数（增量同步）：**
- 作用：启用增量同步，只同步自上次运行以来新增或修改的记录
- 取值：
    - 空（默认）：全量同步，每次拉取全部记录
    - `last_modified_time`：按飞书「最后更新时间」增量，只同步修改过的记录
    - `created_time`：按飞书「创建时间」增量，只同步新建的记录
- 原理：首次运行时同步全部记录并记录最大时间戳，之后每次只拉取时间戳大于该值的记录
- 增量状态由 DataY 引擎统一持久化，重启后自动续接

### StreamJdbcOutput 组件详细参数

**table 参数：**
- 作用：指定数据写入的目标表
- 要求：表必须存在，且字段结构与输入数据字段对应
- 注意：目标表列名需与多维表格字段名（以及 record_id）保持一致

**model 参数详细说明：**
- `append`：向表中插入新记录，不检查重复
- `overwrite`：先执行 `DELETE FROM` 清空表，再插入数据

## 6. 常见问题与注意事项

### 常见错误及解决方案

**凭证错误：**
- 获取 token 失败（code 非 0）：检查 `appId` / `appSecret` 是否正确，应用是否已发布

**权限错误：**
- 接口返回无权限：确认应用已开通多维表格（bitable）相关权限，并将应用添加到多维表格的协作者中

**标识错误：**
- 找不到 app/table：检查 `appToken` / `tableId` 是否从 URL 中正确提取

**字段不匹配：**
- MySQL 写入失败：检查目标表列名是否与多维表格字段名一致，数据类型是否兼容

**中文列名：**
- MySQL 中中文列名需用反引号包裹（建表语句中已示例），同步时 DataY 会自动处理

### 日志监控

DataY 提供详细的执行日志，包括：
- token 获取与缓存状态
- 拉取的字段数、记录总数
- 数据分批发送进度
- 写入 MySQL 的记录数与写入模式
- 错误和警告信息

## 7. 扩展应用场景

### 增量同步（推荐生产使用）

当数据量较大、不希望每次都全量同步时，可将 `FeishuBitableInput` 的 `incrColumn` 设为 `last_modified_time`，实现增量同步：首次运行同步全部记录，之后每次只同步新增或修改的记录。

增量同步配置示例：

```json
{
  ".id": "FeishuBitableInput",
  ".name": "FeishuBitableInput",
  "appId": "cli_aa1c93b88bf8dcb0",
  "appSecret": "",
  "appToken": "EEmSbfNF8aD2RcsP26CcU3Bvnee",
  "tableId": "tblpjHsRMt8moPj8",
  "incrColumn": "last_modified_time"
}
```

配合写入组件时，建议使用 `model: "append"` 并在 MySQL 侧以 `record_id` 建唯一索引（`INSERT ... ON DUPLICATE KEY UPDATE`），实现「已存在则更新、不存在则插入」，从而支持增量更新：

```sql
ALTER TABLE user ADD UNIQUE KEY uk_record_id (record_id);
```

> 说明：
> - 增量状态（上次同步的最大时间戳）由 DataY 引擎自动持久化，重启或重新调度后自动续接。
> - 飞书目前无法直接按「最后更新时间」进行服务端过滤，本组件采用「拉取全部记录 + 本地按时间戳过滤」的方式实现增量，因此每次仍会拉取全量数据，但只向下游发送变更记录，可显著降低数据库写入压力。

### 数据转换
- 可在 `FeishuBitableInput` 与 `StreamJdbcOutput` 之间添加 `DuckDBSql` 组件，对数据进行清洗、过滤、字段转换
- 如需定时同步，可借助 DataY Web 平台的调度能力，配置定时任务周期执行

---

*本文档基于 DataY v1.0.1 版本编写，适用于飞书多维表格同步到 MySQL 的场景*
