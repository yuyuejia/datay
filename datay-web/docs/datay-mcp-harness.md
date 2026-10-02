# DataY MCP Harness（平台管理 + 指标问数 MCP）

DataY Harness 通过 MCP（Model Context Protocol）把两类能力暴露给 AI Agent：

1. **平台对象管理**：**数据源**、**ETL 任务**、**数据模型**的增删改查与运行类动作；
2. **指标智能问数**：指标清单、元数据、维度取值与取数查询（只读）。

接上之后，Claude Desktop / Cursor / DSH 等客户端可以直接
「创建数据源 → 拉取表结构 → 建 ETL 任务并试跑 → 上线调度 → 建维度模型并物化落表」，
并接着「用自然语言问指标 → 拿报表」，整条链路不必再手点页面。

- **端点**：`POST/GET/DELETE /mcp/datay`（Streamable HTTP）
- **鉴权**：`Authorization: Bearer mcp_<userId>_<random>`
- **租户**：`X-Tenant-Id` 或 `X-Tenant-Code`（缺省用令牌所属用户的默认租户）
- **实现**：`com.data.datafusion.mcp.harness`

> 与既有两个 MCP 端点并存、互不影响：
> `/mcp/datasource` 面向「数据源取数与同步任务」，`/mcp/metric` 面向「指标问数（只读）」，
> `/mcp/datay` 面向「平台对象管理 + 指标问数」。
> `/mcp/datay` 的指标工具与 `/mcp/metric` **共用同一份 `AiTool` 实现与提示词**，
> 只是多了一个入口，不存在两套口径。

---

## 1. 客户端配置

先在 **个人设置** 页面生成 MCP Token（`POST /api/account/mcp-token`，明文只展示一次）。

### Claude Desktop / Cursor

```json
{
  "mcpServers": {
    "datay": {
      "type": "http",
      "url": "http://localhost:8080/mcp/datay",
      "headers": {
        "Authorization": "Bearer mcp_1_xxxxxxxxxxxx"
      }
    }
  }
}
```

### DSH

```json
{
  "mcpServers": {
    "datay": {
      "url": "http://localhost:8080/mcp/datay",
      "headers": { "Authorization": "Bearer mcp_1_xxxxxxxxxxxx" }
    }
  }
}
```

多租户场景下附加 `"X-Tenant-Code": "t3"` 即可切换租户；指定的租户必须属于该令牌对应用户，否则调用被拒绝。

---

## 2. 工具清单

工具名以 `对象_动作` 命名，`datay_harness_catalog` 可一次性列出当前全部能力。
下表中 **写** 表示该工具属于写操作，受 `datay.mcp.harness.allow-mutating-tools` 开关控制。

### 数据源（datasource_*）

| 工具 | 写 | 说明 |
| --- | --- | --- |
| `datasource_list` | | 分页列出数据源（不含密码），支持关键字过滤 |
| `datasource_get` | | 按 ID 查看详情（不含密码） |
| `datasource_create` | ✔ | 注册数据源（name / type / url 必填） |
| `datasource_update` | ✔ | 按 ID 部分更新，未提交字段保持原值 |
| `datasource_delete` | ✔ | 删除数据源 |
| `datasource_test_connection` | | 测试连通性，可用已存配置或本次传入的连接参数 |
| `datasource_schemas` | | 列出 schema / 数据库 |
| `datasource_tables` | | 列出某 schema 下的表（支持过滤与上限） |
| `datasource_columns` | | 列出某张表的字段定义 |
| `datasource_query` | ✔ | 在数据源上执行 SQL（可 DDL/DML，故按写操作对待） |
| `datasource_default_warehouse` | | 查询当前租户的默认数仓 |
| `datasource_set_default_warehouse` | ✔ | 设置默认数仓（需 `ROLE_ADMIN` / `ROLE_TENANT_ADMIN`） |

### ETL 任务（etl_task_*）

| 工具 | 写 | 说明 |
| --- | --- | --- |
| `etl_task_list` | | 分页列出任务及状态 |
| `etl_task_get` | | 任务详情，含节点与连线组成的任务设计图 |
| `etl_task_create` | ✔ | 创建任务（默认 OFFLINE），可同时提交 nodes / edges |
| `etl_task_update` | ✔ | 只提交元信息时不动设计图；提交 nodes / edges 时整体替换并重算执行计划 |
| `etl_task_delete` | ✔ | 删除任务及其 Job 与设计图 |
| `etl_task_run` | ✔ | 立即执行一次 |
| `etl_task_online` | ✔ | 上线并按 cron 加入调度 |
| `etl_task_offline` | ✔ | 下线并取消调度 |
| `etl_task_debug` | | 试跑取数：源组件限量采样，sink 只读不写，不污染目标库与增量状态 |
| `etl_task_instances` | | 分页查询执行实例（运行历史） |
| `etl_task_job_preview` | | 预览编译出的执行计划 JSON（units / connections） |

### 数据模型（data_model_*）

| 工具 | 写 | 说明 |
| --- | --- | --- |
| `data_model_list` | | 列出模型，可按 `directoryId` 或 `modelType`（DIMENSION / FACT）过滤 |
| `data_model_get` | | 模型详情 + 字段定义 |
| `data_model_create` | ✔ | 创建维度 / 事实模型 |
| `data_model_update` | ✔ | 按 ID 部分更新 |
| `data_model_delete` | ✔ | 删除模型及其字段 |
| `data_model_fields` | | 查询字段定义 |
| `data_model_save_fields` | ✔ | 整体替换字段集合 |
| `data_model_dimension_values` | | 分页浏览维度取值 |
| `data_model_logical_types` | | 列出支持的逻辑字段类型 |
| `data_model_materialize_types` | | 列出目标数据源支持的物理字段类型 |
| `data_model_materialize_fields` | | 生成物化字段定义预览 |
| `data_model_materialize_check` | | 检查目标表是否已存在 |
| `data_model_materialize_ddl` | | 生成建表 DDL（不执行） |
| `data_model_materialize` | ✔ | 物化落表，回写物理位置，可选生成时间维度预置数据 |
| `data_model_directory_list` | | 列出模型目录 |
| `data_model_directory_create` | ✔ | 创建模型目录 |

### 指标智能问数（metric 分组，只读）

把平台内置问数助手用的 6 个只读 `AiTool` 原样暴露出来，由客户端模型完成「指标识别 → 维度拆解 → 业务限定 → 时间范围」推理，
**服务端无需 LLM Key**。工具名保持原样，因为各工具说明之间互相引用（改名会让说明失效）。

| 工具 | 写 | 说明 |
| --- | --- | --- |
| `metric_query_guide` | | 推荐工作流与硬性约束，建议作为问数第一步 |
| `retrieve_metric_context` | | RAG 语义检索，按用户原话召回指标 / 维度字段 / 维度成员候选 |
| `list_metrics` | | 指标清单，把口语化说法匹配到真实指标编码 |
| `describe_metrics` | | 指标可用的维度、层级、时间字段与周期字段 |
| `sample_dimension_values` | | 抽样维度字段取值，把「北京」映射到正确字段 |
| `query_metric_data` | | 执行指标取数，返回 columns / rows |

典型链路：

```text
metric_query_guide        {}
retrieve_metric_context   {query:"上个月北京各门店销售额"}
list_metrics              {keyword:"销售额"}
describe_metrics          {metricCodes:["sales_amount"]}
sample_dimension_values   {dimensionModelCode:"dim_store", fieldName:"city_name", keyword:"北京"}
query_metric_data         {metricCodes:["sales_amount"],
                           dimensions:[{dimensionModelCode:"dim_date", dimensionFieldNames:["month"]}],
                           conditions:[{dimensionModelCode:"dim_store", dimensionFieldName:"city_name", operator:"EQ", value:"北京"}],
                           timeRange:{start:"2026-08-01", end:"2026-08-31"}}
```

问数取数的目标库由指标所属事实模型的 `dataSourceId` 决定，因此不依赖「默认数仓」设置。

### 指标定义管理（metric 分组，增删改查）

维护**指标定义本身**（口径、公式、事实表、业务限定、状态、目录），对齐 `/api/metrics` 与
`/api/metric-directories`。校验规则完全由 `MetricService` 兜底（编码唯一、原子指标须绑定 DWD 事实表、
衍生指标公式须引用已存在指标且不成环）。

| 工具 | 写 | 说明 |
| --- | --- | --- |
| `metric_list` | | 分页查询指标定义（管理视角，含停用指标与公式） |
| `metric_get` | | 按 ID 查详情，含引用关系 `refMetrics` |
| `metric_create` | ✔ | 新建指标（ATOMIC 需 `factModelId` + `formula`；DERIVED 用 `${编码}` 引用） |
| `metric_update` | ✔ | **部分更新**：未提交字段沿用原值 |
| `metric_delete` | ✔ | 删除；被衍生指标公式引用时平台会拒绝 |
| `metric_preview_sql` | | 预览计算 SQL（不执行），可用已存指标或草稿定义 |
| `metric_directory_list` | | 查询指标目录 |
| `metric_directory_create` | ✔ | 新建目录 |
| `metric_directory_update` | ✔ | 改名 / 调整父目录与排序 |
| `metric_directory_delete` | ✔ | 删除目录；子目录与指标**移动到根目录**，不级联删除 |

> **`list_metrics` 与 `metric_list` 的区别**：前者是问数视角（只返回启用指标，用于把口语匹配到编码），
> 后者是管理视角（分页、含停用、含公式与事实表）。两者说明里都写了互相的指引。

用问数的模型做管理时要注意：`metric_update` / `metric_directory_update` 是**部分更新**（harness 会先取原值再合并），
而平台 REST 的 `PUT /api/metrics/{id}` 是完整覆盖 —— 直接用 REST 改一个字段需要把整份定义回传。

### 元工具

| 工具 | 写 | 说明 |
| --- | --- | --- |
| `datay_harness_catalog` | | 按能力分组列出全部工具，标注是否写操作与所需权限 |

---

## 3. 安全模型

一次工具调用的处理顺序（`DatayHarnessExecutor`）：

1. **传输层鉴权**：`ServerTransportSecurityValidator` 校验 `Bearer` 令牌，缺失或非法直接 `401`。
2. **用户解析**：`McpTokenService` 由令牌反查用户（只存 BCrypt 哈希）。
3. **租户解析**：`X-Tenant-Id` / `X-Tenant-Code` → 校验归属 → 否则用户默认租户；随后写入 `TenantContext`，
   领域服务与 Hibernate 租户过滤器自动生效，**跨租户数据不可见**。
4. **数据权限**：把用户及其角色写入 `SecurityContextHolder`，`RoleDataScopeService` 等数据权限逻辑与页面行为一致。
5. **写开关**：`tool.mutating() && !allowMutatingTools` 时拒绝执行。
6. **权限校验**：`tool.requiredAuthorities()` 与用户权限比对（如默认数仓设置需管理员）。
7. **执行与清理**：结果序列化为 JSON 返回；无论成败都清理 `TenantContext` 与 `SecurityContextHolder`，
   避免线程复用造成串租户。

出参裁剪：`DatayHarnessViews` 统一裁剪响应，数据源**永不返回密码**，任务列表不返回完整设计图。

### 只读部署

```yaml
datay:
  mcp:
    harness:
      allow-mutating-tools: false
```

或启动参数 `--DATAY_MCP_HARNESS_ALLOW_MUTATING_TOOLS=false`。此时全部写工具返回明确错误，
只保留查询、元数据浏览、试跑调试，以及**全部指标问数能力**（`metric` 分组工具均为只读，
不受该开关影响）。

---

## 4. 典型用法

### 4.1 管数据

把这句话交给 Agent：

> 把 MySQL 订单库接进来，建一个每天 2 点同步到 DuckLake 的 ETL 任务，先试跑看看数据，没问题再上线。

Agent 的调用序列大致是：

```text
datasource_create        {name:"订单库", type:"mysql", url:"jdbc:mysql://...", username:"...", password:"..."}
datasource_tables        {id:12, schema:"order_db", search:"order"}
datasource_columns       {id:12, schema:"order_db", table:"t_order"}
etl_task_create          {taskName:"订单同步", cron:"0 0 2 * * ?", nodes:[...], edges:[...]}
etl_task_debug           {id:31, rowLimit:50}
etl_task_run             {id:31}
etl_task_instances       {id:31, size:5}
etl_task_online          {id:31}
```

建模场景：

```text
data_model_directory_create {name:"电商域"}
data_model_create           {name:"日期维度", modelType:"DIMENSION", dimensionKind:"TIME", timeLevels:"YEAR,MONTH,DAY", timeStart:"2024-01-01", timeEnd:"2026-12-31"}
data_model_save_fields      {id:7, fields:[{fieldName:"date_key",fieldType:"STRING",isPrimaryKey:true}, ...]}
data_model_materialize_check{id:7, dataSourceId:1, schemaName:"dw", tableName:"dim_date"}
data_model_materialize_ddl  {id:7, dataSourceId:1, schemaName:"dw", tableName:"dim_date"}
data_model_materialize      {id:7, dataSourceId:1, schemaName:"dw", tableName:"dim_date", overwrite:false, generateData:true, dataStart:"2024-01-01", dataEnd:"2026-12-31"}
```

---

## 5. 扩展一个新工具

1. 任选一个 `*HarnessTools` 配置类，或在 `com.data.datafusion.mcp.harness` 下新建 `@Configuration`；
2. 声明一个 `@Bean DatayHarnessTool`：

```java
@Bean
DatayHarnessTool datasourceRenameTool(DataSourceService service) {
    return DatayHarnessTool.write(
        "datasource_rename",
        "按 ID 重命名数据源",
        object(properties("id", integer("数据源 ID"), "name", string("新名称")), "id", "name"),
        (args, context) -> service.partialUpdate(/* ... */));
}
```

3. 注册中心（`DatayHarnessToolRegistry`）会自动收集，`/mcp/datay` 自动暴露，`datay_harness_catalog` 自动收录，
   **无需改动任何中心化代码**。

约定：

- 只读用 `DatayHarnessTool.read(...)`，写操作必须用 `DatayHarnessTool.write(...)`（否则绕过写开关）；
- 需要管理员权限时用 `write(name, desc, schema, Set.of("ROLE_ADMIN"), handler)`，与 REST 的 `@PreAuthorize` 保持一致；
- 入参读取统一走 `DatayHarnessArgs`（`reqLong` / `reqStr` 等在缺参时抛 `IllegalArgumentException`，
  执行器会转换为可读的「参数错误」结果）；
- 出参优先走 `DatayHarnessViews`，避免把实体敏感字段或超大字段直接抛给模型；
- 工具名不以对象名开头时（如指标问数沿用 `list_metrics`），用
  `DatayHarnessTool.of(name, group, description, schema, ...)` 显式指定分组，否则目录里会散成无意义的分组。

> **Bean 命名坑（务必注意）**：`@Bean` 方法名会决定 bean 名。本仓库开启了
> `spring.main.allow-bean-definition-overriding=true`，如果 `@Bean` 方法与既有组件同名
> （例如把方法命名为 `metricCatalogTool`，正好与 `MetricCatalogTool` 组件同名），
> **不会报错，而是静默顶替掉原组件**——内置问数助手会因此悄悄少掉工具。
> 因此 harness 的 `@Bean` 方法统一加 `...HarnessTool` 后缀，`DatayHarnessMcpWiringTest` 有对应回归断言。

---

## 6. 测试

```bash
# 仅跑 Harness 相关测试
mvn -pl datay-web test -Dtest='com.data.datafusion.mcp.**'
```

| 测试类 | 覆盖点 |
| --- | --- |
| `DatayHarnessToolRegistryTest` | 注册、查找、重名拒绝、目录分组（含显式分组与按名前缀推导） |
| `DatayHarnessExecutorTest` | 令牌/租户/写开关/权限校验、上下文绑定与清理、参数错误映射 |
| `DatayHarnessMcpEndpointTest` | 真实 servlet 上的 `initialize → tools/list` 协议流程与 401 鉴权 |
| `DatayHarnessMcpWiringTest` | 三套 MCP 端点共存无 bean 歧义；56 个工具全部注册且名称唯一；指标问数工具只读且逐字复用 `AiTool` 说明与 schema；**harness 的 `@Bean` 不得顶替 `AiTool` 组件**；写开关读取配置属性 |
| `MetricAdminHarnessToolsTest` | 指标 CRUD：部分更新保留未提交字段、新建字段搬运、删除回执、SQL 预览草稿模式、读写标记与分组 |
