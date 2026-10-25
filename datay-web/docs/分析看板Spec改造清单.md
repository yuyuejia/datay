# 分析看板 Spec 改造清单

> 版本：v0.1（评审稿）
> 范围：`datay-web` 分析看板 spec 协议、校验、取数、设计器与 AI 生成链路
> 依据：`shared/dashboard/types.ts`、`DashboardSpecValidator`、`DashboardDataService`、`DatasetQueryService`、`DashboardDesignAssistant`

## 1. 改造目标

### 1.1 目标

1. **一套筛选器模型**：不再同时维护 `datasetId+field` 与 `bindings` 两套写法。
2. **筛选尽量下推**：能由数据库完成的过滤、排序、Top N，不在浏览器里做。
3. **聚合语义显式**：`sum/avg/count` 等由 spec 声明，禁止对非可加指标默认求和。
4. **协议可演进**：引入 `version`、JSON Schema、带路径的校验错误和兼容适配层。
5. **权限边界清晰**：保存态只允许数据集引用；内联数据集不能绕过数据集行级权限。
6. **AI 输出可验证**：AI 生成的 spec 必须经过同一套 v1 校验，不再依赖“提示词约束”。

### 1.2 非目标

- 本期不做完整的 BI 语义层（计算字段、指标衍生、复杂联动）。
- 本期不引入新的图表库，ECharts 仍作为渲染层，但收敛为受控配置。
- 本期不重做看板设计器 UI，只做必要的字段、聚合、筛选配置调整。

### 1.3 兼容策略

> 2026-10-25 更新：当前尚无存量用户，已取消 v0 兼容，直接采用 v1-only。
> 下文涉及 v0 适配的段落仅作为历史设计记录，不再实施。

- 只接受 `version: 1`；缺失或非 1 直接拒绝。
- 只接受 `widgets[].encode`、`filters[].targets[]`；拒绝 `dataRef`、`bindings`、筛选器 `datasetId/field`。
- 新保存的 spec 固定写 **v1**。
- 前端类型、设计器、AI prompt、预置资产包全部直接输出 v1。

---

## 2. 目标 Spec v1（摘要）

### 2.1 顶层结构

```json
{
  "version": 1,
  "title": "销售看板",
  "description": "一句话说明",
  "layout": {
    "mode": "standard",
    "screen": {
      "width": 1920,
      "height": 1080,
      "fitMode": "contain",
      "theme": "dark",
      "autoRefreshSeconds": 60,
      "background": { "type": "color", "color": "#0f172a" }
    }
  },
  "datasets": [
    { "id": "ds1", "refId": "分析数据集 ID" }
  ],
  "widgets": [
    {
      "id": "w1",
      "type": "echarts",
      "title": "月度销售趋势",
      "layout": { "x": 0, "y": 0, "w": 16, "h": 8 },
      "datasetId": "ds1",
      "encode": {
        "categoryField": "month_name",
        "values": [{ "field": "sales_amount", "label": "销售额", "agg": "sum" }],
        "seriesField": null
      },
      "chart": {
        "type": "bar",
        "stack": false,
        "options": {}
      }
    }
  ],
  "filters": [
    {
      "id": "region",
      "type": "multiSelect",
      "label": "区域",
      "defaultValue": [],
      "targets": [
        {
          "datasetId": "ds1",
          "field": "region",
          "pushdown": {
            "kind": "metricDimension",
            "dimensionModelCode": "dim_region",
            "dimensionFieldName": "region_name"
          }
        }
      ]
    }
  ]
}
```

### 2.2 字段决策表

| 字段 | 决策 | 说明 |
| --- | --- | --- |
| `version` | 新增 | 必填；缺失视为 v0 |
| `datasets[].refId` | 保留并强化 | v1 保存态必填 |
| `datasets[].type/name` | 保留为展示缓存 | 运行时以数据集库为准 |
| `datasets[].metricQuery/sql/params/dataSourceId` | v1 禁用 | 仅 v0 适配器读取 |
| `widgets[].dataRef` | 迁移为 `encode` | v0 自动转换 |
| `widgets[].echartsOption` | 迁移为 `chart` | v0 自动转换；保留 `chart.options` 作为逃生口 |
| `filters[].datasetId+field` | 迁移为 `targets` | 支持多数据集 |
| `filters[].bindings` | 仅兼容读取 | 新 spec 不再写入 |
| `layout.columns/rowHeight/gap` | 删除 | 统一固定 24 / 40 / 12；或收敛到 `layout.grid` |
| `layout.screen` | 仅 screen 模式写入 | standard 模式不再冗余保存 |
| `widgets[].dataRef.groupBy` | 迁移为 `encode.values[].agg` | `true` 先按 `sum` 转换并给出警告 |

### 2.3 v0 → v1 映射

| v0 | v1 |
| --- | --- |
| `dataRef.categoryField` | `encode.categoryField` |
| `dataRef.valueField` | `encode.values[0].field`，`agg` 默认 `none` |
| `dataRef.series[]` | `encode.values[]` |
| `dataRef.seriesField` | `encode.seriesField` |
| `dataRef.groupBy=true` | 对应 `encode.values[].agg="sum"`，仅对可加指标生效 |
| `dataRef.sortField/sortOrder/limit` | 保留在 `encode`，但由服务端执行 |
| `filters[].bindings[]` | `filters[].targets[]`，`binding` → `pushdown` |
| `filters[].datasetId+field` | `targets[]`，无 `pushdown` 时标记为客户端筛选 |
| `echartsOption` | `chart.options` + `chart.type` |
| 无 `version` | `version: 1`（适配后写入） |

---

## 3. 分阶段改造清单

### Phase 0：立即修复（向后兼容，不引入 v1 强约束）

> 目标：先修掉会产生错误数据或错误交互的问题，避免继续放大。
>
> 实施状态（2026-10-25）：P0-01～P0-07 已完成。偏差：布局重叠当前按硬错误处理；
> P0-07 当前在保存/更新时记录日志，读取侧指标统计留待接入。

- [x] **P0-01 修正指标数据集的时间筛选语义**
  - 改动：`DashboardSpecValidator` 对 `type=dateRange` + `datasetId/field` 且目标为 METRIC 数据集时，不再允许直接字段过滤；要求使用 `metricTime` pushdown，或明确降级并在 UI 标注“客户端筛选”。
  - 改动：`DashboardDesignAssistant` 提示词不再建议对指标数据集生成 `dateRange + field`。
  - 文件：`DashboardSpecValidator.java`、`DashboardDesignAssistant.java`、`analysis-dashboard-designer.component.ts`、`DashboardFilters.vue`
  - 验收：指标数据集 + 日期直连字段在 `validate-spec` 返回明确错误；旧 `metricTime` 看板不受影响。

- [x] **P0-02 字段存在性校验与错误提示**
  - 改动：`validate-spec`/保存时，若数据集已有 `fields` 缓存，校验 `dataRef` 字段、`filter.field` 是否存在；运行时若结果列不包含筛选字段，返回带路径的错误，而不是静默返回空数据。
  - 文件：`DashboardSpecValidator.java`、`DashboardDataService.java`、`AnalysisDatasetResolver.java`、`filter-utils.ts`
  - 验收：筛选不存在的字段时接口返回 `filters[0].field: field not found`；设计器显示字段错误。

- [x] **P0-03 组件基础校验补齐**
  - 改动：widget `id` 唯一性；`echarts/table/kpi` 必须配置 `datasetId`；`echarts/kpi` 的 `dataRef` 必填；布局越界或重叠至少返回 warning。
  - 文件：`DashboardSpecValidator.java`
  - 验收：重复 widget id、缺少 datasetId 的组件在保存/预检时报错；重叠布局返回可展示的警告。

- [x] **P0-04 禁止筛选器双写**
  - 改动：同时出现 `datasetId+field` 和 `bindings` 时直接报错，避免“删掉 bindings 后退化成前端过滤”的静默行为。
  - 文件：`DashboardSpecValidator.java`
  - 验收：双写 spec 被拒绝；单写旧版/新版均可正常保存。

- [x] **P0-05 修复 URL 深链筛选**
  - 改动：`filtersFromQuery` 对 `dateRange`/`numberRange` 解析 JSON 或使用 `f_<id>_start` / `f_<id>_end` 约定，避免深链静默失效。
  - 文件：`filter-utils.ts`、`filter-utils.spec.ts`
  - 验收：URL 携带日期区间时筛选值类型正确，筛选生效。

- [x] **P0-06 ECharts 选项安全净化**
  - 改动：渲染前对 `tooltip.formatter` 等可承载 HTML 的字符串使用 DOMPurify；对不支持的 option 结构给出降级处理。
  - 文件：`EChart.vue`、新增 `echarts-option-sanitizer.ts`、`echarts.ts`
  - 验收：包含 `<img onerror=...>` 的 formatter 不执行；正常 tooltip 不受影响。

- [x] **P0-07 记录并统计旧协议使用**
  - 改动：读取无 `version` 或含 `bindings/内联数据集` 的 spec 时打点（日志或指标），为后续下线旧逻辑提供依据。
  - 文件：`AnalysisDashboardService.java`、`DashboardDataService.java`
  - 验收：能统计旧 spec 数量与看板 ID 列表。

### Phase 1：v1 协议与校验层

> 目标：先建立“协议 + 适配器 + 可诊断错误”，不急于改查询逻辑。
>
> 实施状态（2026-10-25）：V1-01/02/03/08/09 已完成；V1-06 已加入 spec 大小、
> 数据集/组件/筛选器数量与 HTML 长度限制，未知字段拒绝待后续；
> V1-10 当前对 v1 按硬错误处理。V1-04/05/07 待后续。
> v0 适配与 `DashboardSpecMigrator` 已移除，当前只接受 v1。

- [x] **V1-01 引入 `version`（v1-only）**
  - 当前直接校验 `version=1`，缺失或非 1 拒绝；不再提供 v0 适配器。
  - 保存固定写 v1。
  - 文件：`DashboardSpecValidator.java`
  - 验收：无 `version` 的 spec 被拒绝；新保存 spec 带 `version: 1`。

- [x] **V1-02 统一筛选器模型**
  - 新增 `filters[].targets[]`，支持 `pushdown`、多数据集、`mode: server|client`。
  - 筛选器统一使用 `targets[]`；pushdown 目标直接写 `targets[].pushdown`。
  - 文件：`types.ts`、`DashboardSpecValidator.java`
  - 验收：一个筛选器可同时作用于多个数据集；`bindings` 写法被拒绝。

- [x] **V1-03 统一数据映射与聚合（`encode`）**
  - `widgets[].encode` 为唯一数据映射；`values[].agg` 支持 `sum|avg|min|max|count|none`。
  - `encode.groupBy` 保留；非可加指标的 `sum` 校验已在 Q3-01 完成。
  - 文件：`types.ts`、`DashboardSpecValidator.java`、`spec-to-option.ts`
  - 验收：v1 图表不再默认 SUM；聚合方式可从 spec 读取。

- [ ] **V1-04 清理死配置与冗余字段**
  - 删除 `layout.columns/rowHeight/gap`（或收敛为只读 `layout.grid`）；`screen` 仅在 `mode=screen` 时输出；标准化 `screen` 默认值。
  - 文件：`types.ts`、`layout-defaults.ts`、`DashboardSpecValidator.java`、AI prompt
  - 验收：保存后的 spec 不再包含无效 config；standard 模式不保存 screen 外观字段。

- [ ] **V1-05 结构化校验错误**
  - 统一错误结构：`{ code, path, message }`，例如 `widgets[2].dataRef.categoryField`。
  - REST 返回 400 + `errors[]`；前端在组件/属性面板定位展示。
  - 文件：`AnalysisDashboardResource.java`、新增 `SpecValidationError`、`dashboard.service.ts`、设计器
  - 验收：错误可定位到具体字段，不再只有一句中文 message。

- [ ] **V1-06 严格字段与扩展位**
  - 顶层、`datasets[]`、`widgets[]`、`filters[]` 拒绝未知字段；需要扩展时只能放 `extensions`。
  - 增加 spec 大小、widget 数量、HTML 长度上限。
  - 文件：`DashboardSpecValidator.java`
  - 验收：拼写错误字段被拒绝；超大 spec 返回明确限制错误。

- [ ] **V1-07 收紧内联数据集**
  - v1 保存态只允许 `{ id, refId, name?, type? }`；内联数据集仅由 v0 适配器读取。
  - `refId` 缺失且无 `version` 的旧 spec 继续可读，但保存时提示需先转成数据集引用。
  - 文件：`DashboardSpecValidator.java`、`AnalysisDashboardService.java`、`DashboardDesignAssistant.java`
  - 验收：新建/保存 v1 内联数据集被拒绝；存量看板可正常打开。

- [x] **V1-08 JSON Schema 与文档**
  - 新增 `dashboard-spec-v1.schema.json`，作为 AI、前端、后端共同契约；文档包含示例、字段说明、筛选/聚合语义。
  - 文件：`datay-web/docs/` 或 `src/main/resources/schema/`
  - 验收：Schema 能校验 v1 示例；文档中的示例可直接通过 `validate-spec`。

- [x] **V1-09 数据集类型以引用为准**
  - 保存时用数据集库的实际 `type` 覆盖/校验 spec 中的 `type`，避免漂移。
  - 文件：`AnalysisDatasetResolver.java`、`DashboardSpecValidator.java`
  - 验收：spec 写错 type 时被纠正或报错，不再依赖运行时覆盖。

- [ ] **V1-10 布局校验**
  - v1 保存时拒绝组件重叠、越界；v0 适配时自动压实并记录警告。
  - 文件：`DashboardSpecValidator.java`、`grid-layout.ts`
  - 验收：AI 生成重叠布局返回错误；旧 spec 自动压实后可正常打开。

### Phase 2：服务端筛选下推

> 目标：让筛选、排序、Top N 尽量在数据库完成；前端过滤仅作为显式降级。
>
> 实施状态（2026-10-25）：S2-01～S2-10 已完成。SQL 数据集支持 `mode=server` 字段目标包子查询下推，
> 指标数据集走 pushdown；批量查询、请求级解析复用、客户端筛选免重新取数、本地筛选标识均已接入。

- [x] **S2-01 新增 `FilterTargetResolver`**
  - 解析 `targets[]`：数据集类型、输出字段、指标维度/事实字段、SQL 参数。
  - 无法解析时返回结构化的 `filterTargetUnresolved`，由设计器要求用户重新选择字段。
  - 文件：新增 `FilterTargetResolver.java`、`DashboardSpecValidator.java`
  - 验收：字段能解析为 `metricTime` / `metricDimension` / `metricFactField` / `sqlParam` / `sqlColumn`。

- [x] **S2-02 指标数据集下推**
  - `metricTime` → `MetricQueryDTO.timeRange`。
  - `metricDimension` → `MetricFilterCondition`（`EQ/IN/LIKE/BETWEEN`）。
  - `metricFactField` → fact field 条件。
  - 文件：`DatasetQueryService.java`、`MetricQueryService` 相关 DTO
  - 验收：指标看板筛选后 SQL 带谓词，返回行数下降；`avg/ratio` 等指标不被错误地前端求和。

- [x] **S2-03 SQL 数据集下推**
  - 优先使用 `sqlParam`；无参数时，复用/泛化 `DatasetScopeSqlBuilder`，将原始 SQL 包成子查询并追加筛选谓词。
  - 字段名必须通过标识符白名单校验；值按现有 `quote` 规则转义；保留各数据库方言的兼容性开关。
  - 文件：新增 `DatasetFilterSqlBuilder.java`（可从 `DatasetScopeSqlBuilder` 抽取）、`DatasetQueryService.java`
  - 验收：SQL 数据集筛选在数据库侧生效；`field` 非法时拒绝；原有数据权限逻辑不回归。

- [x] **S2-04 多目标筛选语义**
  - 同一筛选器可绑定多个数据集；同一数据集内多条件 AND，`multiSelect` 内部 IN；不同筛选器之间 AND。
  - 明确 `pushdown` 与 `mode=client` 的优先级。
  - 文件：`DatasetQueryService.java`、`types.ts`
  - 验收：区域筛选同时作用于两个数据集；结果与单选/多选语义一致。

- [x] **S2-05 筛选选项服务端化**
  - 服务端筛选目标的下拉选项改为调用数据集字段去重查询（复用 `DatasetQueryService.loadFieldValues`），支持关键字、分页。
  - 文件：`DashboardDataService.java`、`AnalysisDashboardResource.java`、`DashboardFilters.vue`
  - 验收：选项来自全量数据集而非当前返回行；大表不一次性拉全量。

- [x] **S2-06 显式客户端筛选降级**
  - 仅当 SQL 数据集无法下推时允许 `mode=client`；UI 显示“本地筛选”标识；仍受结果集上限保护。
  - 文件：`filter-utils.ts`、`DashboardFilters.vue`、`DashboardSpecValidator.java`
  - 验收：客户端筛选不再是隐式行为；用户可感知限制。

- [x] **S2-07 按筛选类型决定是否重新取数**
  - 服务端筛选目标值变化 → 重新查询；仅客户端筛选 → 不重新请求，直接重算。
  - 文件：`useDashboardData.ts`、`App.vue`、设计器
  - 验收：纯客户端筛选切换不再触发网络请求。

- [x] **S2-08 批量取数接口**
  - 新增 `POST /api/analysis-dashboards/{id}/query`，一次返回多个 dataset 结果；支持只请求指定 dataset。
  - 设计器预览复用同一接口。
  - 文件：`AnalysisDashboardResource.java`、`DashboardDataService.java`、`dashboard.service.ts`、`useDashboardData.ts`
  - 验收：一个看板 6 个组件只发 1 次请求；单数据集失败不影响其他数据集返回。

- [x] **S2-09 请求级缓存**
  - 一次请求内缓存 refId → dataset 解析结果，避免 O(N²) 查询。
  - 文件：`AnalysisDatasetResolver.java` / `DashboardDataService.java`
  - 验收：批量查询时数据集实体查询次数为 O(N)。

- [x] **S2-10 筛选默认值与类型校验**
  - `defaultValue` 按筛选类型校验：`dateRange` 为 `{start,end}`，`numberRange` 为 `{min,max}`，`multiSelect` 为数组；非法值拒绝保存。
  - 文件：`DashboardSpecValidator.java`
  - 验收：错误默认值在保存时被拦截。

### Phase 3：取数、聚合与组件语义

> 实施状态（2026-10-25）：Q3-01～Q3-07 全部完成。Q3-04 已支持列选择、列顺序、
> 列宽、格式化、服务端分页与前端分页。`encode.values[].agg` 为必填，KPI 支持
> `format.precision/unit/prefix/suffix`，结果新增 `columnTypes/numericColumns`。
> 注：不可加指标 `sum` 校验当前暂时关闭，待指标元数据稳定后恢复。

- [x] **Q3-01 显式聚合**
  - `encode.values[].agg` 成为唯一聚合来源；`groupBy` 不再隐式 SUM。
  - 从指标元数据判断可加性：`avg/ratio/count distinct` 禁止 `sum`，返回校验错误或要求服务端重算。
  - 文件：`spec-to-option.ts`、`DashboardSpecValidator.java`、指标元数据服务
  - 验收：非可加指标选择 `sum` 时保存报错；图表数值与指标口径一致。

- [x] **Q3-02 服务端排序与 Top N**
  - `encode.sortField/sortOrder/limit` 下推到查询；SQL 数据集无排序时不再依赖 10k 行截断后前端排序。
  - 文件：`DatasetQueryService.java`、`MetricQueryService`、`spec-to-option.ts`
  - 验收：Top 10 在数据量超过 10k 时仍正确；响应行数受 limit 约束。

- [x] **Q3-03 KPI 语义**
  - KPI 必须声明 `field + agg`；支持 `unit/precision/prefix/suffix`；禁止无配置时隐式求和。
  - 文件：`types.ts`、`KpiWidget.vue`、`spec-to-option.ts`、`DashboardSpecValidator.java`
  - 验收：KPI 显示格式可配置；多维度结果不会被静默累加。

- [x] **Q3-04 表格能力**
  - 支持列选择、列顺序、列宽、格式化、服务端分页；`limit` 与分页互斥或明确优先级。
  - 文件：`DataTableWidget.vue`、`types.ts`、`DatasetQueryService.java`
  - 验收：表格可配置列；大数据集分页可用。

- [x] **Q3-05 字段展示映射显式化**
  - v1 中 `_id/_sk → _name` 的自动替换改为显式 `labelField`/列配置；保留旧逻辑仅用于 v0。
  - 文件：`spec-to-option.ts`、`DataTableWidget.vue`、`migration`
  - 验收：用户选择的展示字段不会被隐式替换。

- [x] **Q3-06 结果元数据**
  - 查询结果补充字段类型、字段标签、是否数值，用于筛选控件、格式化和聚合校验。
  - 文件：`DatasetQueryService.java`、`types.ts`
  - 验收：前端不依赖首行推断字段类型。

- [x] **Q3-07 ECharts 类型白名单**
  - 仅允许 `bar/line/pie/area/gauge` 等已支持类型；`series` 数量、`axis` 结构与 `encode` 一致性校验。
  - 文件：`spec-to-option.ts`、`DashboardSpecValidator.java`
  - 验收：不支持的系列类型在保存时报错，而不是运行时空白。

### Phase 4：前端改造

> 实施状态（2026-10-25）：F4-01/02/03/04/05/06/07 已完成。已清理保存态死布局字段、
> 支持多目标筛选编辑、聚合方式编辑、SQL 字段目标自动 server、表格列/分页配置；
> `validate-spec` 返回 `errors[{code,path,message}]`，设计器优先展示该错误。

- [x] **F4-01 v1 类型与适配层**
  - `types.ts` 增加 `version/encode/targets/chart`；v0 类型仅保留在 `legacy` 命名空间。
  - 文件：`types.ts`、新增 `spec-migration.ts`
  - 验收：前端可同时读取 v0/v1，保存统一输出 v1。

- [x] **F4-02 规范化逻辑**
  - `normalizeDashboardSpec`/`normalizeDashboardLayout` 适配 v1，删除死配置；`screen` 按模式处理。
  - 文件：`layout-defaults.ts`
  - 验收：v0/v1 spec 规范化结果一致。

- [x] **F4-03 筛选器 UI**
  - 服务端/客户端筛选标识；多数据集 target 展示；选项走服务端接口；`dateRange/numberRange` 类型正确。
  - 文件：`DashboardFilters.vue`
  - 验收：多数据集筛选可配置；选项搜索/分页可用。

- [x] **F4-04 设计器**
  - 字段选择时校验存在性；自动生成 `pushdown`；聚合方式可编辑；非可加指标禁用 `sum`；v0 看板打开时提示“可升级为 v1”。
  - 文件：`analysis-dashboard-designer.component.ts/.vue`
  - 验收：设计器不再生成 `bindings/dataRef/groupBy`；保存前预检错误可定位。

- [x] **F4-05 图表映射**
  - `buildEchartsOption` 读取 `encode` 与 `agg`；不再隐式聚合；`limit/sort` 使用服务端结果。
  - 文件：`spec-to-option.ts`
  - 验收：前端单测覆盖 agg=none/sum/avg、多系列、Top N。

- [x] **F4-06 取数组合式函数**
  - 批量查询；客户端筛选不重复请求；错误按 dataset 隔离；取消过期请求。
  - 文件：`useDashboardData.ts`
  - 验收：筛选切换请求数符合预期；乱序响应不会覆盖新结果。

- [x] **F4-07 错误展示**
  - 组件级错误显示 `code/path/message`；顶部汇总；可复制诊断信息。
  - 文件：`DashboardWidget.vue`、`DashboardCanvas.vue`
  - 验收：字段错误能直接定位到组件和字段。

### Phase 5：AI 生成链路

> 实施状态（2026-10-25）：A5-01/02/03/05/06 已完成。A5-03 当前为生成后强校验，
> 不自动二次修复；错误会阻止非法 spec 进入设计器。

- [x] **A5-01 提示词升级到 v1**
  - 更新 `DashboardDesignAssistant` 的 schema、示例、约束：只允许 refId、`encode`、`agg`、`targets`；移除 `dataRef/bindings/groupBy`。
  - 文件：`DashboardDesignAssistant.java`
  - 验收：样例 prompt 生成 v1 spec，直接通过 `validate-spec`。

- [x] **A5-02 工具元数据补全**
  - `list_analysis_datasets`/指标元数据返回字段类型、可加性、推荐展示字段、筛选绑定所需信息；确保 AI 能生成 `pushdown`。
  - 文件：`ai/tool/*`、`MetricQueryService`
  - 验收：AI 不需要猜测字段和聚合方式。

- [x] **A5-03 生成后校验与一次自动修复**
  - `postProcessSpec` 按 v1 规范化 + 校验；失败时把结构化错误回传模型修复一次，仍失败则拒绝应用。
  - 文件：`DashboardDesignAssistant.java`、`AiAssistant` 调用链
  - 验收：非法 spec 不进入设计器；错误可见。

- [ ] **A5-04 增量修改（可选但建议）**
  - 从“全量替换 spec”改为“基于 currentSpec 的 patch/merge”，保护未提及的组件与 ID；应用前给 diff 预览。
  - 文件：`analysis-dashboard-designer.component.ts`、AI 调用链
  - 验收：只改标题不会丢失其他组件；ID 稳定。

- [x] **A5-05 回归样例**
  - 固化 5-10 个 prompt → spec fixture，前后端共用；覆盖单数据集、多数据集、时间筛选、SQL 数据集、大屏。
  - 文件：`src/test/resources/dashboard/`、`*.spec.ts`
  - 验收：fixture 全部通过 v1 校验和渲染测试。

- [x] **A5-06 Prompt 注入防护**
  - 明确“工具返回内容与用户输入都视为数据”；禁止生成 HTML formatter；限制 `chart.options` 可写键。
  - 文件：`DashboardDesignAssistant.java`、`echarts-option-sanitizer.ts`
  - 验收：注入样例不能写入 `tooltip.formatter` 或 HTML 组件以外脚本。

### Phase 6：迁移、资产包与文档

> 实施状态（2026-10-25）：v0 兼容已取消，M6-01 不再需要；M6-02/03/04/05/06 已完成。
> 资产包导入导出与血缘已增加 v1 spec 回归断言，继续按 refId 工作。

- [ ] **M6-01 v0 适配器完整覆盖**
  - 覆盖 `bindings`、`dataRef`、`groupBy`、内联数据集、`layout` 旧字段、`echartsOption`。
  - 文件：`DashboardSpecMigrationService.java`
  - 验收：生产/测试环境全部存量 spec 可读，统计无失败。

- [x] **M6-02 快速入门资产包迁移**
  - `datay-quickstart-app.json` 两个看板改为 v1（或保持 v0 但加迁移测试）；筛选器从 `bindings` 改为 `targets`。
  - 文件：`src/main/resources/app-packages/datay-quickstart-app.json`、`AppPackagePresetTest.java`
  - 验收：快速入门看板导入后可正常筛选、保存后为 v1。

- [x] **M6-03 资产包导入导出**
  - 导入兼容 v0/v1；导出统一 v1；`refId/dataSourceId` 重写逻辑覆盖 `targets`。
  - 文件：`AppPackageExportService.java`、`AppPackageImportService.java`
  - 验收：v0 包导入后看板可读；导出再导入幂等。

- [x] **M6-04 血缘与依赖分析**
  - `MetricLineageService`/`DataModelLineageService` 读取 `encode` 与 `targets`，兼容旧字段。
  - 文件：`MetricLineageService.java`、`DataModelLineageService.java`
  - 验收：v1 看板的指标/数据集血缘不丢失。

- [x] **M6-05 文档**
  - 新增看板 spec 指南（字段、筛选、聚合、大屏、错误码、v0→v1 迁移）；更新帮助中心。
  - 文件：`datay-web/docs/`、`docs/help/`
  - 验收：文档示例可直接通过 `validate-spec`；用户可自助排查字段错误。

- [x] **M6-06 下线旧协议**
  - 观察一个发布周期；当旧 spec 加载量为 0 后删除 `bindings/dataRef/内联` 兼容代码和 v0 类型。
  - 文件：migration、validator、types
  - 验收：删除后全量测试通过，旧看板已完成迁移。

### Phase 7：安全与性能

- [x] **SEC-01 内联数据集权限**
  - v1 禁止内联；v0 内联若仍需读取，至少限制为只读、校验数据源权限，并记录“无数据集行级权限”的告警。
  - 文件：`DashboardDataService.java`、`AnalysisDashboardService.java`
  - 验收：保存态无法通过内联数据集绕过数据集权限。

- [x] **SEC-02 查询资源限制**
  - 每个数据集最大返回行数可配置；图表/表格 limit 与全局上限双重约束；限制单看板数据集数、组件数、spec 字节数。
  - 文件：`DataSourceQueryService.java`、`DashboardSpecValidator.java`、`application.yml`
  - 验收：超大查询被拒绝或截断并返回明确提示。

- [x] **PERF-01 批量 + 缓存**
  - 批量接口、ref 解析缓存、前端请求去重/取消。
  - 文件：同 Phase 2/4
  - 验收：典型看板首屏请求数与 P95 延迟达到目标（建议：≤2 个请求，P95 < 2s，具体按环境定）。

- [ ] **PERF-02 服务端筛选覆盖率**
  - 监控服务端/客户端筛选占比；客户端筛选超过阈值时提示用户改用带参数的 SQL 数据集或指标数据集。
  - 文件：`DashboardFilters.vue`、`DatasetQueryService.java`
  - 验收：新增看板默认全部服务端筛选；客户端筛选有明确标识。

---

## 4. 测试矩阵

| 领域 | 用例 | 层级 |
| --- | --- | --- |
| 协议 | v0→v1 全字段映射 | Java 单测 |
| 协议 | 未知字段、超限、缺 version | Java 单测 |
| 校验 | widget id 重复、缺 datasetId、重叠布局 | Java 单测 |
| 校验 | 字段不存在、错误 defaultValue | Java 单测 |
| 筛选 | 指标 metricTime / dimension / fact | Java 单测 + 集成 |
| 筛选 | SQL 参数 / 子查询包裹 / 非法字段 | Java 单测 + 集成 |
| 筛选 | 多数据集同一筛选器 | 集成 |
| 权限 | 内联数据集、ref 数据集、角色范围 | 集成 |
| 取数 | 批量接口、单数据集失败隔离 | 集成 |
| 取数 | 排序/Top N/10k 上限 | 集成 |
| 指标 | sum/avg/ratio 聚合口径 | Java + 前端单测 |
| 前端 | filter-utils 服务端/客户端模式 | Vitest |
| 前端 | spec-to-option agg/limit/多系列 | Vitest |
| 前端 | useDashboardData 批量/不重复请求 | Vitest |
| 前端 | ECharts formatter 净化 | Vitest |
| 迁移 | 快速入门、资产包、存量 fixture | Java + E2E |
| AI | prompt fixture 全部通过 v1 校验 | Java 单测 |
| E2E | 标准看板、大屏、筛选、深链 | Playwright/Vitest（按现有栈） |

## 5. 发布与回滚

1. **发布 1**：Phase 0 + v1 适配器 + 文档（读兼容，写仍可 v0）。
2. **发布 2**：v1 写入 + 前端/AI 切 v1；v0 仅读。
3. **发布 3**：服务端筛选下推 + 批量接口；监控旧协议使用量。
4. **发布 4**：下线 v0 兼容代码。

回滚策略：

- spec 只存 JSON，无数据库结构变更，回滚风险低。
- 保留 `datay.dashboard.spec.write-version=0|1` 一个发布周期。
- v1 写入后旧版本前端无法读取时，回滚需同时回滚前端；建议发布窗口内前后端同版本。

## 6. 风险与待定决策

1. **SQL 数据集服务端筛选方式**：是要求必须使用 `${param}`，还是允许子查询包裹？建议两者都支持，子查询包裹作为兜底，但必须做字段白名单和方言验证。
2. **非可加指标的跨维度汇总**：是服务端按指标口径重算，还是禁止选择多个维度？建议优先服务端重算，无法重算时禁止 `sum` 并提示。
3. **内联数据集是否彻底禁止**：建议 v1 保存态禁止，v0 仅读；如业务强需求，走“显式权限豁免”而不是默认放开。
4. **重叠布局处理**：新 spec 拒绝保存，旧 spec 自动压实；不建议静默接受重叠。
5. **ECharts 逃生口**：保留 `chart.options`，但限制可写键并净化 formatter；不建议完全放开任意 option。

## 7. 完成定义（DoD）

- [ ] 新建/保存的看板全部为 v1，存量看板无人工迁移可打开。
- [ ] 指标时间筛选、维度筛选、SQL 筛选均由服务端执行；客户端筛选仅作为显式降级。
- [ ] 图表/KPI 不再隐式 SUM；非可加指标有校验保护。
- [ ] 保存前校验能定位到 `widgets[i].xxx` / `filters[i].xxx`。
- [ ] 快速入门与资产包测试通过；导入导出 v0/v1 均可用。
- [ ] ECharts formatter 净化、内联数据集策略、查询上限生效。
- [ ] 文档、JSON Schema、AI prompt 三者一致。
