# 分析看板 Spec v1 指南

> 适用于当前 v1-only 协议。`version` 必须为 `1`；不再兼容 `dataRef`、`bindings`、筛选器 `datasetId/field`。

## 顶层结构

```json
{
  "version": 1,
  "title": "销售看板",
  "description": "可选",
  "layout": { "mode": "standard" },
  "datasets": [],
  "widgets": [],
  "filters": []
}
```

- `layout.mode`：`standard` 或 `screen`
- `screen` 仅在 `screen` 模式保存，字段：
  - `width` / `height`
  - `fitMode`：`contain | stretch | cover`
  - `theme`：`light | dark`
  - `autoRefreshSeconds`：0-86400
  - `background`
- 保存态不再写 `layout.columns/rowHeight/gap`，运行时由前端统一使用 24 栅格、40px 行高、12px 间距。

## 数据集

保存态只允许引用数据集库：

```json
{ "id": "ds1", "refId": "9600", "name": "门店销售", "type": "SQL" }
```

- `refId` 必填
- `type` 以数据集库为准，服务端会覆盖
- v1 保存态不允许内联 `metricQuery` / `sql` / `dataSourceId`

## 组件

### 图表

```json
{
  "id": "w1",
  "title": "月度销售",
  "type": "echarts",
  "layout": { "x": 0, "y": 0, "w": 16, "h": 8 },
  "datasetId": "ds1",
  "encode": {
    "categoryField": "month_name",
    "values": [{ "field": "sales_amount", "name": "销售额", "agg": "sum" }],
    "groupBy": true,
    "sortField": "sales_amount",
    "sortOrder": "desc",
    "limit": 12
  },
  "chart": { "type": "bar" },
  "echartsOption": {
    "tooltip": { "trigger": "axis" },
    "xAxis": { "type": "category" },
    "yAxis": { "type": "value" },
    "series": [{ "type": "bar" }]
  }
}
```

- `encode.values[].field` 必填
- `encode.values[].agg` 必填：`sum | avg | min | max | count | none`
- 不可加指标禁止 `agg=sum`（当前暂时关闭该校验，待指标可加性元数据稳定后恢复）
- `chart.type`：`bar | line | pie | area | gauge`
- `series[].type`：`bar | line | pie | gauge`
- `sortField/sortOrder/limit` 会尽量下推到服务端

### KPI

```json
{
  "id": "k1",
  "type": "kpi",
  "layout": { "x": 0, "y": 0, "w": 6, "h": 4 },
  "datasetId": "ds1",
  "encode": { "values": [{ "field": "sales_amount", "agg": "sum" }] },
  "format": { "prefix": "¥", "precision": 0, "unit": "元", "suffix": "" }
}
```

- 必须声明 `encode.values[0].field + agg`
- 不再自动取首列求和

### 表格

```json
{
  "id": "t1",
  "type": "table",
  "layout": { "x": 0, "y": 8, "w": 24, "h": 8 },
  "datasetId": "ds1",
  "encode": {
    "limit": 100,
    "pageSize": 20,
    "sortField": "sales_amount",
    "sortOrder": "desc",
    "columns": [
      { "field": "month_name", "label": "月份", "width": 160 },
      { "field": "sales_amount", "label": "销售额", "width": 180, "format": { "precision": 2 } }
    ]
  }
}
```

- `columns` 配置后按数组顺序展示
- 不配置时展示数据集全部列
- `pageSize` 当前为前端分页；服务端分页仍受全局最大行数限制

## 筛选器

```json
{
  "id": "region",
  "type": "multiSelect",
  "label": "区域",
  "targets": [
    { "datasetId": "ds1", "field": "region", "mode": "server" }
  ],
  "defaultValue": []
}
```

### 服务端下推

```json
{
  "id": "date_range",
  "type": "dateRange",
  "targets": [
    {
      "datasetId": "ds1",
      "mode": "server",
      "pushdown": { "kind": "metricTime" }
    }
  ]
}
```

- `metricTime`：指标数据集时间范围
- `metricDimension`：指标维度条件
- `metricFactField`：指标事实字段条件
- `sqlParam`：SQL 占位符参数
- SQL `mode=server + field`：服务端在数据集 SQL 外层追加列筛选
- `mode=client + field`：前端本地筛选，界面会显示“本地筛选”标识

## 默认值

- `dateRange`：`{ "start": "...", "end": "..." }`
- `numberRange`：`{ "min": 0, "max": 100 }`
- `multiSelect`：数组
- `dateRange` / `numberRange` 无默认值时请省略 `defaultValue`；空数组、空对象会按无默认值处理

## 校验错误

`POST /api/analysis-dashboards/validate-spec` 返回 400 时包含：

```json
{
  "title": "看板定义校验失败",
  "message": "组件 w1 的字段 xxx 缺少合法 agg",
  "errors": [
    {
      "code": "invalidSpec",
      "path": "widgets",
      "message": "组件 w1 的字段 xxx 缺少合法 agg"
    }
  ]
}
```

## 配额

- spec JSON 最大 1,000,000 字符
- 数据集最多 50 个
- 组件最多 200 个
- 筛选器最多 100 个
- HTML 内容最多 100,000 字符
- 查询返回行数由 `datay.query.max-rows` 控制，默认 10000
